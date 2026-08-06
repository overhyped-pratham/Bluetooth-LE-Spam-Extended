package de.simon.dankelmann.bluetoothlespam

import android.app.Application
import de.simon.dankelmann.bluetoothlespam.Handlers.AdvertisementSetQueueHandler
import de.simon.dankelmann.bluetoothlespam.Helpers.BluetoothHelpers
import de.simon.dankelmann.bluetoothlespam.Helpers.ThemeManager
import de.simon.dankelmann.bluetoothlespam.Interfaces.Services.IAdvertisementService
import de.simon.dankelmann.bluetoothlespam.Interfaces.Services.IBluetoothLeScanService
import de.simon.dankelmann.bluetoothlespam.Services.BluetoothLeScanService


class BleSpamApplication : Application() {

    lateinit var advertisementService: IAdvertisementService
        private set

    lateinit var queueHandler: AdvertisementSetQueueHandler
        private set

    lateinit var scanService: IBluetoothLeScanService
        private set

    private var httpPairingServer: de.simon.dankelmann.bluetoothlespam.Services.HttpPairingServer? = null

    override fun onCreate() {
        // Apply the user's theme preference before calling super.onCreate()
        // to ensure the theme is set before any UI is created
        ThemeManager.getInstance().applyTheme(this)

        super.onCreate()

        setupAdvertisementService()
        scanService = BluetoothLeScanService(this)
        httpPairingServer = de.simon.dankelmann.bluetoothlespam.Services.HttpPairingServer(this)
        httpPairingServer?.start()
    }

    override fun onTerminate() {
        httpPairingServer?.stop()
        super.onTerminate()
    }

    fun setupAdvertisementService() {
        advertisementService = BluetoothHelpers.getAdvertisementService(this)
        queueHandler = AdvertisementSetQueueHandler(this, advertisementService)
    }
}
