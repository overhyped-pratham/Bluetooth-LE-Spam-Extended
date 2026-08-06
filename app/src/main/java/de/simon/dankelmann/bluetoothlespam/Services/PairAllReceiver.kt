
package de.simon.dankelmann.bluetoothlespam.Services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class PairAllReceiver : BroadcastReceiver() {

    private val _logTag = "PairAllReceiver"

    companion object {
        const val ACTION_PAIR_ALL = "de.simon.dankelmann.bluetoothlespam.ACTION_PAIR_ALL"
        const val ACTION_STOP_PAIR_ALL = "de.simon.dankelmann.bluetoothlespam.ACTION_STOP_PAIR_ALL"
        const val EXTRA_INTERVAL_MS = "interval_ms"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(_logTag, "Received pair-all broadcast: $action")

        try {
            val pairingManager = PairingManager(context)

            when (action) {
                ACTION_PAIR_ALL -> {
                    val interval = intent.getLongExtra(EXTRA_INTERVAL_MS, 3000L)
                    val targetStr = intent.getStringExtra("target") ?: "all"
                    val target = when (targetStr.lowercase()) {
                        "apple" -> PairingManager.Target.APPLE
                        "android" -> PairingManager.Target.ANDROID
                        "both" -> PairingManager.Target.BOTH
                        "all" -> PairingManager.Target.ALL
                        "" -> PairingManager.Target.ALL
                        null -> PairingManager.Target.ALL
                        else -> PairingManager.Target.ALL
                    }
                    pairingManager.startContinuousPairing(interval, target)
                    Log.i(_logTag, "Started continuous pairing (interval=${interval}ms, target=${target})")
                }
                ACTION_STOP_PAIR_ALL -> {
                    pairingManager.stopContinuousPairing()
                    Log.i(_logTag, "Stopped continuous pairing")
                }
                else -> {
                    // For backwards compatibility: treat any other action as a single-shot start
                    val interval = intent.getLongExtra(EXTRA_INTERVAL_MS, 3000L)
                    pairingManager.startContinuousPairing(interval)
                    Log.i(_logTag, "Started continuous pairing (legacy path, interval=${interval}ms)")
                }
            }
        } catch (ex: Exception) {
            Log.e(_logTag, "Error while attempting to pair devices", ex)
        }
    }
}

