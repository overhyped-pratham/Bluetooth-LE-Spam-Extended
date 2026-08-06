package de.simon.dankelmann.bluetoothlespam.Services

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import de.simon.dankelmann.bluetoothlespam.BleSpamApplication
import de.simon.dankelmann.bluetoothlespam.Constants.Constants
import de.simon.dankelmann.bluetoothlespam.Helpers.BluetoothHelpers.Companion.bluetoothAdapter
import de.simon.dankelmann.bluetoothlespam.Helpers.BluetoothLeDeviceClassificationHelper
import de.simon.dankelmann.bluetoothlespam.PermissionCheck.PermissionCheck

class PairingManager(private val context: Context) {

    private val _logTag = "PairingManager"
    private val _bluetoothAdapter: BluetoothAdapter? = context.bluetoothAdapter()

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var intervalMillis: Long = 3000
    private var receiverRegistered = false

    private val bondStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent == null) return
            val action = intent.action
            if (action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val newState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                val prevState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1)
                Log.i(_logTag, "Bond state changed for ${device?.address} $prevState->$newState")
                checkStopIfAllBonded()
            }
        }
    }

    private val pairingRunnable = object : Runnable {
        override fun run() {
            attemptPairingOnce()
            if (running) handler.postDelayed(this, intervalMillis)
        }
    }

    enum class Target {
        ALL, APPLE, ANDROID, BOTH
    }

    fun startContinuousPairing(intervalMillis: Long = 3000, target: Target = Target.ALL) {
        if (_bluetoothAdapter == null) {
            Log.w(_logTag, "Bluetooth adapter unavailable")
            return
        }

        if (!PermissionCheck.checkPermission(Manifest.permission.BLUETOOTH_CONNECT, context)) {
            Log.w(_logTag, "Missing BLUETOOTH_CONNECT permission")
            return
        }

        this.intervalMillis = intervalMillis
        if (!receiverRegistered) {
            val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            context.registerReceiver(bondStateReceiver, filter)
            receiverRegistered = true
        }

        if (!running) {
            running = true
            // store selected target in runnable by wrapping
            currentTarget = target
            handler.post(pairingRunnable)
            Log.i(_logTag, "Started continuous pairing every ${this.intervalMillis}ms")
        }
    }

    fun stopContinuousPairing() {
        running = false
        handler.removeCallbacks(pairingRunnable)
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(bondStateReceiver)
            } catch (ex: Exception) {
                Log.w(_logTag, "Error unregistering receiver", ex)
            }
            receiverRegistered = false
        }
        Log.i(_logTag, "Stopped continuous pairing")
    }

    private fun attemptPairingOnce(): List<String> {
        val initiated = mutableListOf<String>()

        try {
            val app = context.applicationContext as BleSpamApplication
            val scanService = app.scanService

            val target = currentTarget ?: Target.BOTH

            // Build list of candidates with their scan data
            val candidates = mutableListOf<Pair<String, de.simon.dankelmann.bluetoothlespam.Models.BluetoothLeScanResult>>()
            scanService.getFlipperDevicesList().forEach { candidates.add(Pair(it.address, it)) }
            scanService.getSpamPackageScanResultList().forEach { candidates.add(Pair(it.address, it)) }

            candidates.forEach { (addr, scanResult) ->
                try {
                    if (!matchesTarget(scanResult, target)) return@forEach

                    val device = _bluetoothAdapter!!.getRemoteDevice(addr)
                    if (device.bondState != BluetoothDevice.BOND_BONDED) {
                        val started = device.createBond()
                        Log.i(_logTag, "Bonding requested for $addr (started=$started)")
                        if (started) initiated.add(addr)
                    }
                } catch (ex: Exception) {
                    Log.e(_logTag, "Failed to request bond for $addr", ex)
                }
            }
        } catch (ex: Exception) {
            Log.e(_logTag, "Error during pairing attempt", ex)
        }

        return initiated
    }

    private fun checkStopIfAllBonded() {
        try {
            val app = context.applicationContext as BleSpamApplication
            val scanService = app.scanService


            val target = currentTarget ?: Target.BOTH

            val candidates = mutableListOf<Pair<String, de.simon.dankelmann.bluetoothlespam.Models.BluetoothLeScanResult>>()
            scanService.getFlipperDevicesList().forEach { candidates.add(Pair(it.address, it)) }
            scanService.getSpamPackageScanResultList().forEach { candidates.add(Pair(it.address, it)) }

            val filtered = candidates.filter { (_, scanResult) -> matchesTarget(scanResult, target) }

            val allBonded = filtered.all { (addr, _) ->
                try {
                    val device = _bluetoothAdapter!!.getRemoteDevice(addr)
                    device.bondState == BluetoothDevice.BOND_BONDED
                } catch (ex: Exception) {
                    false
                }
            }

            if (allBonded) {
                Log.i(_logTag, "All discovered devices are bonded; stopping continuous pairing")
                stopContinuousPairing()
            }
        } catch (ex: Exception) {
            Log.e(_logTag, "Error checking bond states", ex)
        }
    }

    private var currentTarget: Target? = null

    private fun matchesTarget(scanResult: de.simon.dankelmann.bluetoothlespam.Models.BluetoothLeScanResult, target: Target): Boolean {
        if (target == Target.ALL) return true
        if (target == Target.BOTH) return true

        // Detect Apple via manufacturer id
        val isApple = scanResult.manufacturerSpecificData.keys.contains(Constants.MANUFACTURER_ID_APPLE)

        // Detect Android via Google Fast Pair service UUID, Samsung manufacturer id, or name heuristics
        val isFastPair = scanResult.serviceUuids.any { it.toString().equals(Constants.UUID_GOOGLE_FAST_PAIRING, ignoreCase = true) }
        val isSamsung = scanResult.manufacturerSpecificData.keys.contains(Constants.MANUFACTURER_ID_SAMSUNG)

        val name = scanResult.deviceName.lowercase().trim()
        val androidKeywords = listOf("pixel", "buds", "galaxy", "watch", "wear", "oneplus", "buds", "earbuds", "xiaomi", "mi", "huawei", "oppo", "realme", "vivo", "motorola", "lenovo")
        val nameMatches = androidKeywords.any { kw -> name.contains(kw) }

        val isAndroid = isFastPair || isSamsung || nameMatches

        return when (target) {
            Target.APPLE -> isApple
            Target.ANDROID -> isAndroid
            Target.BOTH -> isApple || isAndroid
            else -> true
        }
    }
}
