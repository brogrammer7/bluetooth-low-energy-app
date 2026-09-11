package com.example.blecourse.bluetooth

import android.content.Context
import android.annotation.SuppressLint
import android.util.Log
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.blecourse.bluetooth.models.BTPeripheralInfo

class BTCentral(context: Context) : BTBaseHandler(context) {
    private val TAG = BTCentral::class.java.simpleName

    private var scanCallback: ScanCallback? = null

    var discoveredPeripherals = mutableStateMapOf<String, BTPeripheralInfo>()
        private set

    var isReady by mutableStateOf(false)
        private set

    var isScanning by mutableStateOf(false)
        private set

    init {
        bluetoothManager?.adapter?.let { _ ->
            isReady = true

            scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    val result = result ?: return
                    val device = result.device
                    val deviceKey = device.address.uppercase()

                    if (!discoveredPeripherals.containsKey(deviceKey)) {
                        Log.i(TAG, "onScanResult -> address=${device.address}, name=${device.name}")

                        val peripheralInfo = BTPeripheralInfo(device)
                        peripheralInfo.isConnectable = result.isConnectable

                        discoveredPeripherals[deviceKey] = peripheralInfo
                    }

                    discoveredPeripherals[deviceKey]?.rssi = result.rssi
                }

                override fun onScanFailed(errorCode: Int) {
                }
            }
        }
    }

    fun startScanning() {
        if (!isReady || isScanning) {
            return
        }

        val bluetoothLeScanner = bluetoothManager?.adapter?.bluetoothLeScanner ?: run {
            Log.e(TAG, "startScanning -> BluetoothLeScanner is not available")
            return
        }

        Log.i(TAG, "startScanning")

        isScanning = true
        this.discoveredPeripherals.clear()

        val filters: List<ScanFilter> = emptyList()

        val settings: ScanSettings = ScanSettings.Builder()
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .build()

        bluetoothLeScanner.startScan(filters, settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (!isScanning) {
            return
        }

        Log.i(TAG, "stopScanning")

        val bluetoothLeScanner = bluetoothManager?.adapter?.bluetoothLeScanner ?: run {
            Log.e(TAG, "stopScanning -> BluetoothLeScanner is not available")
            return
        }

        bluetoothLeScanner.stopScan(scanCallback)

        isScanning = false
    }
}