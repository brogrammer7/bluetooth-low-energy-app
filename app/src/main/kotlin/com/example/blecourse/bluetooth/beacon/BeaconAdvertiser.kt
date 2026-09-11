package com.example.blecourse.bluetooth.beacon

import android.annotation.SuppressLint
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.blecourse.bluetooth.BTBaseHandler
import com.example.blecourse.extensions.BTAdvertiseError
import com.example.blecourse.bluetooth.profiles.BeaconIdentity
import com.example.blecourse.bluetooth.profiles.BeaconProfile

/**
 * Broadcasts a configurable iBeacon identity over BLE using Android's advertising set APIs.
 *
 * The advertiser manages the advertising lifecycle, tracks readiness/active state, and
 * periodically updates manufacturer-specific payload data for the current [BeaconIdentity].
 */
class BeaconAdvertiser(context: Context) : BTBaseHandler(context) {
    private val TAG = BeaconAdvertiser::class.java.simpleName

    /**
     * The interval in milliseconds at which the manufacturer data is updated for advertising.
     */
    private val updateInterval = 1000L

    /**
     * The measured power (TX power at 1 meter) used for calculating distance. This value depends on
     * the device's transmission power and should be calibrated for accurate distance estimation.
     * In this example, we use a value of -60 dBm, which looks appropriate for a Motorola moto e13 device.
     */
    private val measuredPower = -60

    private var advertisingSetCallback: AdvertisingSetCallback? = null
    private var currentAdvertisingSet: AdvertisingSet? = null

    /**
     * The current beacon identity being advertised. This includes the UUID, major, and minor values.
     */
    var currentIdentity by mutableStateOf<BeaconIdentity?>(null)
        private set

    /**
     * Indicates if the Bluetooth adapter is ready for advertising
     */
    var isReady by mutableStateOf(false)
        private set

    /**
     * Indicates if the advertiser is currently advertising
     */
    var isAdvertising by mutableStateOf(false)
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

                    // We also start the manufacturer data update process here
                    startAdvertisingManufacturerData()
                }

                override fun onAdvertisingSetStopped(advertisingSet: AdvertisingSet?) {
                    /*
                     * The advertising set has stopped.
                     */
                    isAdvertising = false
                    currentAdvertisingSet = null

                    Log.i(TAG, "onAdvertisingSetStopped")
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
     * Initializes the advertiser with a specific beacon identity. This identity will be used to generate
     * the manufacturer data for advertising.
     */
    fun initialize(identity: BeaconIdentity) {
        Log.i(TAG, "initialize")
        currentIdentity = identity
    }

    /**
     * Shuts down the advertiser, stopping any ongoing advertising and clearing the current identity.
     */
    fun shutDown() {
        Log.i(TAG, "shutDown")

        stopAdvertising()

        currentIdentity = null
    }

    /**
     * Starts advertising the current beacon identity.
     */
    @SuppressLint("MissingPermission")
    fun startAdvertising() {
        if (!isReady || isAdvertising) {
            return
        }

        val bluetoothLeAdvertiser = bluetoothManager?.adapter?.bluetoothLeAdvertiser ?: run {
            Log.e(TAG, "startAdvertising -> BluetoothLeAdvertiser is not available")
            return
        }

        Log.i(TAG, "startAdvertising")

        isAdvertising = true

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
            .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
            .build()

        bluetoothLeAdvertiser.startAdvertisingSet(
            advertisingSetParameters,
            null,
            null,
            null,
            null,
            advertisingSetCallback
        )
    }

    /**
     * Stops advertising the current beacon identity.
     */
    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        if (!isAdvertising) {
            return
        }

        val bluetoothLeAdvertiser = bluetoothManager?.adapter?.bluetoothLeAdvertiser ?: run {
            Log.e(TAG, "stopAdvertising -> BluetoothLeAdvertiser is not available")
            return
        }

        Log.i(TAG, "stopAdvertising")

        bluetoothLeAdvertiser.stopAdvertisingSet(advertisingSetCallback)

        isAdvertising = false
    }

    /**
     * Starts the periodic update of the manufacturer data for advertising.
     */
    private fun startAdvertisingManufacturerData() {
        if (isAdvertising) {
            Handler(Looper.getMainLooper()).postDelayed(advertiseManufacturerDataRunnable, updateInterval)
        }
    }

    /**
     * Updates the manufacturer data for advertising. This function retrieves the current
     * advertising set and identity, encodes the manufacturer data, and sets it for the advertising set.
     * It then schedules itself to run again after the specified interval.
     */
    @SuppressLint("MissingPermission")
    val advertiseManufacturerDataRunnable = Runnable {
        val advertisingSet = currentAdvertisingSet ?: return@Runnable
        val identity = currentIdentity ?: return@Runnable

        val manufacturerData = BeaconEncoder.encodeManufacturerData(identity, measuredPower)

        val advertisingData = AdvertiseData.Builder()
            .addManufacturerData(
                BeaconProfile.DEMO_BEACON_COMPANY_ID,
                manufacturerData
            )
            .build()

        advertisingSet.setAdvertisingData(advertisingData)

        startAdvertisingManufacturerData()
    }
}