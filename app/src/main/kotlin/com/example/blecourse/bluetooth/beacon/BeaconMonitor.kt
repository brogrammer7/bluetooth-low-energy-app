package com.example.blecourse.bluetooth.beacon

import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.blecourse.bluetooth.models.BeaconInfo
import com.example.blecourse.bluetooth.models.BeaconRegion
import com.example.blecourse.bluetooth.BTBaseHandler
import com.example.blecourse.extensions.BTScanError
import com.example.blecourse.bluetooth.profiles.BeaconProfile
import java.util.UUID
import kotlin.collections.set

/**
 * Monitors iBeacon advertisements for configured regions and exposes nearby beacons as state.
 *
 * The monitor starts a BLE scan with iBeacon-specific filters, decodes advertisement data,
 * keeps the latest [BeaconInfo] for matching regions in [rangedBeacons], and periodically
 * removes entries that have not been seen within the timeout window.
 */
class BeaconMonitor(context: Context) : BTBaseHandler(context) {
    private val TAG = BeaconMonitor::class.java.simpleName

    // The interval in milliseconds at which the presence of beacons is checked.
    private val presenceTrackingInterval = 1000L

    // The timeout in milliseconds after which a beacon is considered out of range if not seen.
    private val beaconTimeout = 10000L

    private var scanCallback: ScanCallback? = null

    private var monitoredRegions = listOf<BeaconRegion>()

    /**
     * Map of discovered beacons, keyed by their unique identifier (UUID:major:minor)
     */
    var rangedBeacons = mutableStateMapOf<String, BeaconInfo>()
        private set

    /**
     * Indicates if the Bluetooth adapter is ready
     */
    var isReady by mutableStateOf(false)
        private set

    /**
     * Indicates if beacon monitoring is active
     */
    var isMonitoring by mutableStateOf(false)
        private set

    init {
        bluetoothManager?.adapter?.let { _ ->
            isReady = true

            scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    result?.let { scanResult ->
                        BeaconDecoder.decodeManufacturerData(scanResult.scanRecord?.manufacturerSpecificData)?.let { beaconInfo ->
                            beaconInfo.updateRssi(scanResult.rssi)

                            // Track only beacons that match one of the configured monitoring regions.
                            if (monitoredRegions.any { region -> region.matches(beaconInfo) }) {
                                rangedBeacons[beaconInfo.identifier] = beaconInfo
                            }
                        }
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e(TAG, "onScanFailed -> ${BTScanError(errorCode)}")
                    stopMonitoring()
                }
            }
        }
    }

    /**
     * Initializes the beacon monitor with a list of target UUIDs to monitor.
     */
    fun initialize(targets: List<UUID>) {
        Log.i(TAG, "initialize")

        monitoredRegions = targets.map { uuid ->
            BeaconRegion("com.blecourse.$uuid", uuid)
        }
    }

    /**
     * Shuts down the beacon monitor, stopping any ongoing monitoring and clearing tracked beacons.
     */
    fun shutDown() {
        Log.i(TAG, "shutDown")

        stopMonitoring()

        rangedBeacons.clear()
    }

    /**
     * Starts monitoring for beacons.
     */
    @SuppressLint("MissingPermission")
    fun startMonitoring() {
        if (!isReady || isMonitoring) {
            return
        }

        val bluetoothLeScanner = bluetoothManager?.adapter?.bluetoothLeScanner ?: run {
            Log.e(TAG, "startMonitoring -> BluetoothLeScanner is not available")
            return
        }

        Log.i(TAG, "startMonitoring")

        rangedBeacons.clear()
        isMonitoring = true

        val filters = listOf(
            iBeaconFilter(BeaconProfile.CP27_BEACON_COMPANY_ID),
            iBeaconFilter(BeaconProfile.DEMO_BEACON_COMPANY_ID)
        )

        val settings: ScanSettings = ScanSettings.Builder()
            // SCAN_MODE_LOW_LATENCY enables continuous scanning for best results but uses most power.
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            // CALLBACK_TYPE_ALL_MATCHES, we'll get a callback whenever an advertisement packet is received.
            // We use this because we want to monitor the signal strength of nearby peripherals.
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            // Use MATCH_MODE_AGGRESSIVE for more frequent callbacks on Android 13
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            // MATCH_NUM_MAX_ADVERTISEMENT ensures we get a callback for every advertisement packet
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .build()

        bluetoothLeScanner.startScan(filters, settings, scanCallback)

        // Start the presence monitoring loop to track beacons that go out of range
        startPresenceMonitoring()
    }

    /**
     * Creates a ScanFilter for iBeacon advertisements based on the specified company ID.
     */
    private fun iBeaconFilter(companyId: Int): ScanFilter =
        ScanFilter.Builder()
            .setManufacturerData(
                companyId,
                byteArrayOf(BeaconProfile.IBEACON_TYPE.toByte(), BeaconProfile.IBEACON_LENGTH.toByte()),
                byteArrayOf(0xFF.toByte(), 0xFF.toByte())
            )
            .build()

    /**
     * Stops monitoring for beacons and clears the list of tracked beacons.
     */
    @SuppressLint("MissingPermission")
    fun stopMonitoring() {
        if (!isMonitoring) {
            return
        }

        Log.i(TAG, "stopMonitoring")

        val bluetoothLeScanner = bluetoothManager?.adapter?.bluetoothLeScanner ?: run {
            Log.e(TAG, "stopMonitoring -> BluetoothLeScanner is not available")
            return
        }

        bluetoothLeScanner.stopScan(scanCallback)

        rangedBeacons.clear()
        isMonitoring = false
    }

    /**
     * Starts the presence monitoring loop to track beacons that go out of range.
     */
    private fun startPresenceMonitoring() {
        if (isMonitoring) {
            Handler(Looper.getMainLooper()).postDelayed(trackPresenceRunnable, presenceTrackingInterval)
        }
    }

    /**
     * Tracks the presence of beacons and removes those that have gone out of range.
     */
    val trackPresenceRunnable = Runnable {
        val minLastSeenTime = System.currentTimeMillis() - beaconTimeout
        val expiredBeacons = rangedBeacons.values.filter {
                beacon -> beacon.lastSeen < minLastSeenTime
        }

        expiredBeacons.forEach {
                beacon -> rangedBeacons.remove(beacon.identifier)
        }

        startPresenceMonitoring()
    }
}