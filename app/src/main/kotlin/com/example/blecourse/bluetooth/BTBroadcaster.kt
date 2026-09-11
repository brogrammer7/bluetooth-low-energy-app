package com.example.blecourse.bluetooth

import android.content.Context
import android.annotation.SuppressLint
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.AdvertisingSetCallback
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.blecourse.extensions.BTAdvertiseError

/**
 * Implements a BLE Broadcaster that uses the BLE advertising API to broadcast manufacturer data to nearby devices.
 * This component is designed to be used in scenarios where you want to broadcast information to nearby devices without
 * allowing them to connect.
 *
 * It uses the AdvertisingSet API, which allows more control and larger advertising payloads.
 */
class BTBroadcaster(context: Context) : BTBaseHandler(context) {
    private val TAG = BTBroadcaster::class.java.simpleName

    private var advertisingSetCallback: AdvertisingSetCallback? = null
    private var currentAdvertisingSet: AdvertisingSet? = null

    /**
     * isReady indicates whether the Bluetooth adapter is available and ready to use.
     * This is used to determine whether we can start broadcasting or not.
     */
    var isReady by mutableStateOf(false)
        private set

    /**
     * This indicates whether the device is currently broadcasting its presence and manufacturer data to nearby devices.
     */
    var isBroadcasting by mutableStateOf(false)
        private set

    init {
        bluetoothManager?.adapter?.let { _ ->
            isReady = true

            advertisingSetCallback = object : AdvertisingSetCallback() {
                override fun onAdvertisingSetStarted(advertisingSet: AdvertisingSet?, txPower: Int, status: Int) {
                    /*
                     * The advertising set has started. We check the status to ensure it started successfully.
                     */
                    if (status != ADVERTISE_SUCCESS) {
                        Log.e(TAG, "onAdvertisingSetStarted -> ${BTAdvertiseError(status)}")
                        return
                    }

                    Log.i(TAG, "onAdvertisingSetStarted")

                    currentAdvertisingSet = advertisingSet
                }

                override fun onAdvertisingSetStopped(advertisingSet: AdvertisingSet?) {
                    /*
                     * The advertising set has stopped.
                     */
                    Log.i(TAG, "onAdvertisingSetStopped")

                    isBroadcasting = false
                    currentAdvertisingSet = null
                }

                override fun onAdvertisingDataSet(advertisingSet: AdvertisingSet?, status: Int) {
                    /*
                     * The advertising data has been updated. We check the status to ensure it was set successfully.
                     */
                    if (status != ADVERTISE_SUCCESS) {
                        Log.e(TAG, "onAdvertisingDataSet -> ${BTAdvertiseError(status)}")
                        return
                    }

                    Log.i(TAG, "onAdvertisingDataSet -> Data has been updated")
                }
            }
        }
    }

    /**
     * Shuts down the peripheral handler by stopping the BLE advertiser
     */
    fun shutDown() {
        Log.i(TAG, "shutDown")
        stopBroadcasting()
    }

    /**
     * Starts broadcasting the device's presence and manufacturer data to nearby devices. This will start the BLE
     * advertising process, allowing nearby devices to discover this device and read the advertised manufacturer data.
     */
    @SuppressLint("MissingPermission")
    fun startBroadcasting() {
        if (!isReady || isBroadcasting) {
            return
        }

        val bluetoothLeAdvertiser = bluetoothManager?.adapter?.bluetoothLeAdvertiser ?: run {
            Log.e(TAG, "startBroadcasting -> BluetoothLeAdvertiser is not available")
            return
        }

        Log.i(TAG, "startBroadcasting")

        isBroadcasting = true

        /*
         * We define the advertising set parameters to specify how the advertising should be performed.
         * We also enable legacy mode to ensure compatibility with BLE 4.x devices. In legacy mode, the advertising
         * payload is limited to 31 bytes, so we can only include small manufacturer specific data packets.
         * For more data, we would need to use extended advertising, which requires BLE 5.0 and later.
         */
        val advertisingSetParameters: AdvertisingSetParameters = AdvertisingSetParameters.Builder()
            // TX_POWER_MEDIUM provides a good balance between range and power consumption.
            .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MEDIUM)
            // INTERVAL_HIGH provides a good balance between visibility and power consumption.
            .setInterval(AdvertisingSetParameters.INTERVAL_HIGH)
            // We don't allow connections from centrals, since we only want to broadcast data.
            .setConnectable(false)
            // Enable legacy mode to ensure BLE 4.x compatibility.
            .setLegacyMode(true)
            .build()

        /*
         * We define the advertising data to include the device name. This will allow nearby devices to see the name
         * of this device in their scan results. We can also include manufacturer specific data here, but we will set
         * it later using the updateManufacturerSpecificData function which is called perodically by the UI.
         */
        val advertiseData: AdvertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        /*
         * We also define a scan response to include the device name that will be sent in response to active
         * scan requests from centrals.
         */
        val scanResponse: AdvertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        bluetoothLeAdvertiser.startAdvertisingSet(
            advertisingSetParameters,
            advertiseData,
            scanResponse,
            null,
            null,
            advertisingSetCallback
        )
    }

    /**
     * Stops broadcasting the device's presence and manufacturer data to nearby devices. This will stop the BLE
     * advertising process, making this device no longer discoverable to nearby devices.
     */
    @SuppressLint("MissingPermission")
    fun stopBroadcasting() {
        if (!isBroadcasting) {
            return
        }

        val bluetoothLeAdvertiser = bluetoothManager?.adapter?.bluetoothLeAdvertiser ?: run {
            Log.e(TAG, "stopBroadcasting -> BluetoothLeAdvertiser is not available")
            return
        }

        Log.i(TAG, "stopBroadcasting")

        bluetoothLeAdvertiser.stopAdvertisingSet(advertisingSetCallback)

        isBroadcasting = false
    }

    /**
     * Updates the manufacturer specific data that is being broadcasted to nearby devices.
     * This will update the advertising data of the current advertising set with the new manufacturer data, allowing
     * nearby devices to receive the updated data in their scan results.
     */
    @SuppressLint("MissingPermission")
    fun updateManufacturerSpecificData(data: ByteArray, manufacturerId: Int) {
        val advertisingSet = currentAdvertisingSet ?: return

        val advertisingData = AdvertiseData.Builder()
            .addManufacturerData(manufacturerId, data)
            .build()

        advertisingSet.setAdvertisingData(advertisingData)
    }
}