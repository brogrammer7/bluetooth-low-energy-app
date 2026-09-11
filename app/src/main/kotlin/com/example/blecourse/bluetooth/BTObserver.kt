package com.example.blecourse.bluetooth

import android.content.Context
import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.blecourse.bluetooth.models.BTPeripheralInfo
import com.example.blecourse.extensions.BTScanError

/**
 * Implements a BLE Observer that uses the BLE scanning API to discover peripherals and decode their manufacturer data.
 */
class BTObserver(context: Context) : BTBaseHandler(context) {
    private val TAG = BTObserver::class.java.simpleName

    private var scanCallback: ScanCallback? = null

    /**
     * observedPeripheralInfo holds the peripheral info of the observed peripheral.
     */
    var observedPeripheralInfo by mutableStateOf<BTPeripheralInfo?>(null)
        private set

    /**
     * The decoded manufacturer data of the observed peripheral. We use a mutable state map to allow observing changes
     * in the manufacturer data over time as we receive new scan results.
     */
    var observedPeripheralData = mutableStateMapOf<String, String>()
        private set

    /*
     * isReady indicates whether the Bluetooth adapter is available and ready to use. This allows us to conditionally
     * enable the observing functionality in the UI based on whether the Bluetooth adapter is ready or not.
     */
    var isReady by mutableStateOf(false)
        private set

    /*
     * isObserving indicates whether we are currently observing a peripheral. This allows us to conditionally show the
     * observing state in the UI and prevent starting multiple scans at the same time.
     */
    var isObserving by mutableStateOf(false)
        private set

    init {
        bluetoothManager?.adapter?.let { _ ->
            isReady = true

            scanCallback = object : ScanCallback() {
                @SuppressLint("MissingPermission")
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    val device = result?.device ?: return

                    if (observedPeripheralInfo == null) {
                        // Device of interest appears for the first time. So, we save its peripheral info.
                        observedPeripheralInfo = BTPeripheralInfo(device)
                    }

                    /*
                     * We decode the manufacturer specific data from the scan record and save it in the data map.
                     * This allows us to observe changes in the manufacturer data of the peripheral over time,
                     * as we receive new scan results.
                     */
                    BTDataDecoder.decodeManufacturerData(result.scanRecord?.manufacturerSpecificData)?.let { values ->
                        values.forEach { (key, value) ->
                            observedPeripheralData[key] = value
                        }
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    // Something went wrong while scanning
                    Log.e(TAG, "onScanFailed -> ${BTScanError(errorCode)}")
                    stopObserving()
                }
            }
        }
    }

    /**
     * Shuts down the central handler by stopping the BLE scanner
     */
    fun shutDown() {
        Log.i(TAG, "shutDown")
        stopObserving()
    }

    /**
     * Starts observing the peripheral with the given address.
     * This will start the BLE scanning process and listen for scan results that match the given address. When a
     * matching scan result is received, the peripheral info and manufacturer data will be updated accordingly.
     */
    @SuppressLint("MissingPermission")
    fun startObserving(address: String) {
        if (!isReady || isObserving) {
            return
        }

        val bluetoothLeScanner = bluetoothManager?.adapter?.bluetoothLeScanner ?: run {
            Log.i(TAG, "startObserving -> BluetoothLeScanner is not available")
            return
        }

        Log.i(TAG, "startObserving -> Observing: address=$address")

        isObserving = true

        observedPeripheralInfo = null
        observedPeripheralData.clear()

        /*
         * Use a device address filter to only receive advertisement packets from this peripheral.
         */
        val filters = listOf(ScanFilter.Builder().setDeviceAddress(address).build())

        /*
         * We define the same scan settings here as we used for device discovery of the BLE central.
         */
        val settings: ScanSettings = ScanSettings.Builder()
            // SCAN_MODE_LOW_LATENCY enables continuous scanning for best results but uses most power.
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            // CALLBACK_TYPE_ALL_MATCHES, we'll get a callback whenever an advertisement packet is received.
            // We use this because we want to monitor the signal strength of nearby peripherals.
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            // We use MATCH_MODE_STICKY because we don't want to discover peripherals with low signal strength.
            .setMatchMode(ScanSettings.MATCH_MODE_STICKY)
            // MATCH_NUM_FEW_ADVERTISEMENT ensures that we will get a callback for a few advertisement packets
            .setNumOfMatches(ScanSettings.MATCH_NUM_FEW_ADVERTISEMENT)
            .build()

        bluetoothLeScanner.startScan(filters, settings, scanCallback)
    }

    /**
     * Stops observing the peripheral.
     */
    @SuppressLint("MissingPermission")
    fun stopObserving() {
        if (!isObserving) {
            return
        }

        Log.i(TAG, "stopObserving -> Stop observing")

        val bluetoothLeScanner = bluetoothManager?.adapter?.bluetoothLeScanner ?: run {
            Log.i(javaClass.simpleName, "stopObserving -> BluetoothLeScanner is not available")
            return
        }

        bluetoothLeScanner.stopScan(scanCallback)

        isObserving = false
    }

}