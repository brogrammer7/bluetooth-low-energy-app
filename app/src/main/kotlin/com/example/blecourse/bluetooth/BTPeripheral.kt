package com.example.blecourse.bluetooth

import android.content.Context
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.blecourse.extensions.cccValue
import com.example.blecourse.extensions.displayName
import com.example.blecourse.extensions.findCharacteristicByUuid
import com.example.blecourse.extensions.hasIndicateProperty
import com.example.blecourse.extensions.isCCCDescriptor
import com.example.blecourse.extensions.isCUDDescriptor
import com.example.blecourse.extensions.validateCCCDescriptorValue
import com.example.blecourse.extensions.BTAdvertiseError
import com.example.blecourse.bluetooth.models.BTReferencedByteBuffer
import com.example.blecourse.bluetooth.models.BTCommandQueue
import com.example.blecourse.extensions.BTGattStatus
import com.example.blecourse.extensions.BTProfileState
import com.example.blecourse.bluetooth.profiles.BLEProfile
import java.util.UUID
import kotlin.collections.forEach

/**
 * Implements a BLE Peripheral that can advertise services and characteristics, accept connections from centrals,
 * and handle read/write requests from connected centrals.
 * It uses the BluetoothGattServer API to manage GATT services and characteristics, and the BluetoothLeAdvertiser API
 * to manage advertising. The class maintains a command queue to serialize GATT operations and a read buffer to handle
 * prepared write requests.
 * It also keeps track of connected centrals and the connection state of the peripheral, which can be observed by the
 * UI to update the interface accordingly.
 */
class BTPeripheral(context: Context) : BTBaseHandler(context) {
    private val TAG = BTPeripheral::class.java.simpleName
    private val applicationContext = context.applicationContext

    private var advertiseCallback: AdvertiseCallback? = null
    private var gattServerCallback: BluetoothGattServerCallback? = null
    private var gattServer: BluetoothGattServer? = null

    /**
     * A list of services we will offer. We keep track of these services to include their UUIDs in the advertising,
     * allowing centrals to discover the peripheral based on the services it offers.
     */
    private var offeredServices = listOf<BluetoothGattService>()

    /**
     * We use a handler with the main looper to ensure that these operations are executed on the main thread, which
     * is required by the BluetoothGattServer API.
     */
    private val commandHandler = Handler(Looper.getMainLooper())

    /**
     * The commandQueue is used to serialize GATT operations that require a response from the system, such as sending
     * notifications or responding to read/write requests. This ensures that we don't send multiple requests at
     * the same time, which could lead to unexpected behavior or errors.
     */
    private val commandQueue = BTCommandQueue()

    /**
     * The incomingCharacteristicData is used to buffer the data of prepared write requests for characteristics.
     * When a central sends a prepared write request, we store the data in this buffer until we receive an execute
     * write request, at which point we process the buffered data and update the characteristic values accordingly.
     */
    private val incomingCharacteristicData = BTReferencedByteBuffer<BluetoothGattCharacteristic>()

    /**
     * The outgoingCharacteristicData holds the current value of the characteristics that we want to return in read
     * requests and use in notifications/indications. Renamed from characteristicValueBuffer to make intent clearer.
     */
    private val outgoingCharacteristicData = BTReferencedByteBuffer<BluetoothGattCharacteristic>()

    /**
     * Maximum Transmission Unit (MTU)
     * This is the maximum size in bytes for BLE that can be transmitted in a single packet. Will be updated later
     * if the peripheral supports a higher MTU.
     */
    private var mtuSize = 23

    /**
     * subscribedCentrals holds the device info of the subscribed centrals, indexed by their address. We use a mutable
     * state map to allow observing changes in the subscribed centrals over time as centrals subscribe and unsubscribe.
     */
    var connectedCentrals = mutableStateMapOf<String, BluetoothDevice>()
        private set

    /**
     * isReady indicates whether the peripheral is ready to start advertising and accept connections.
     * The UI can observe this state to enable or disable the option to start advertising.
     */
    var isReady by mutableStateOf(false)
        private set

    /**
     * isAdvertising indicates whether the peripheral is currently advertising. The UI can observe this state to update
     * the interface accordingly.
     */
    var isAdvertising by mutableStateOf(false)
        private set

    /**
     * Callback that is used to deliver the received data to the calling instance.
     */
    var onDataReceived: ((BluetoothDevice, BluetoothGattCharacteristic, ByteArray) -> Unit)? = null

    init {
        bluetoothManager?.adapter?.let { _ ->
            isReady = true

            advertiseCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    // Advertising successfully started
                    Log.i(TAG, "onStartSuccess")
                }

                override fun onStartFailure(errorCode: Int) {
                    // Something went wrong during advertising
                    Log.e(TAG, "onStartFailure -> ${BTAdvertiseError(errorCode)}")
                    stopAdvertising()
                }
            }

            gattServerCallback = object : BluetoothGattServerCallback() {
                override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
                    val device = device ?: return
                    val deviceKey = device.address.uppercase()

                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.e(TAG, "onConnectionStateChange -> ${BTGattStatus(status)}, newState=${BTProfileState(newState)}")
                    }

                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            /*
                             * A central connected to the peripheral. We save the central's device info.
                             */
                            if (!connectedCentrals.containsKey(deviceKey)) {
                                Log.i(TAG, "onConnectionStateChange -> Connected to ${device.address}")

                                connectedCentrals[deviceKey] = device
                            }
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            /*
                             * A central disconnected from the peripheral. We remove the central's device info
                             * and clear the command
                             */
                            Log.i(TAG, "onConnectionStateChange -> Disconnected from ${device.address}")

                            connectedCentrals.remove(deviceKey)

                            if (connectedCentrals.isEmpty()) {
                                commandQueue.clear()
                            }
                        }
                    }
                }

                override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
                    /*
                     * A service was added to the GATT server. We complete the command queue to allow
                     * new commands to be processed.
                     */
                    Log.i(TAG, "onServiceAdded -> status=${BTGattStatus(status)}, service=${service?.displayName ?: "N/A"}")

                    commandQueue.complete()
                }

                override fun onNotificationSent(device: BluetoothDevice?, status: Int) {
                    /*
                     * A notification or indication has been sent to a central. We log the result and complete the command
                     * queue to allow new commands to be processed.
                     */
                    val device = device ?: return

                    Log.i(TAG, "onNotificationSent -> address=${device.address}, status=${BTGattStatus(status)}")

                    commandQueue.complete()
                }

                @SuppressLint("MissingPermission")
                override fun onCharacteristicReadRequest(device: BluetoothDevice?, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic?) {
                    /*
                     * A central is requesting to read a characteristic value. We check the validity of the request and
                     * send an appropriate response.
                     */
                    val device = device ?: return
                    val characteristic = characteristic ?: return

                    Log.i(
                        TAG,
                        "onCharacteristicReadRequest -> address=${device.address}, requestId=$requestId, " +
                                "offset=$offset, characteristic=${characteristic.displayName}"
                    )

                    val responseValue = outgoingCharacteristicData.dataForReference(characteristic) ?: ByteArray(0)

                    if (offset > responseValue.size) {
                        Log.e(TAG, "onCharacteristicReadRequest -> Invalid offset: $offset")

                        commandHandler.post {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
                        }

                        return
                    }

                    commandHandler.post {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, responseValue.copyOfRange(offset, responseValue.size)
                        )
                    }
                }

                @SuppressLint("MissingPermission")
                override fun onCharacteristicWriteRequest( device: BluetoothDevice?, requestId: Int, characteristic: BluetoothGattCharacteristic?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
                    /*
                     * A central is requesting to write a characteristic value. We check the validity of
                     * the request, handle prepared writes, and send an appropriate response.
                     */
                    val device = device ?: return
                    val characteristic = characteristic ?: return
                    var status = BluetoothGatt.GATT_SUCCESS

                    Log.i(TAG, "onCharacteristicWriteRequest -> address=${device.address}, requestId=$requestId, " +
                            "characteristic=${characteristic.displayName}, preparedWrite=$preparedWrite, " +
                            "responseNeeded=$responseNeeded, offset=$offset, value=[${value?.size ?: "N/A"} bytes]")

                    if (preparedWrite) {
                        // The value is just a chunk that must be buffered. Completion is indicated by the onExecuteWrite callback
                        if (value != null && incomingCharacteristicData.add(value, characteristic) != offset) {
                            Log.e(TAG, "onCharacteristicWriteRequest -> Invalid offset: $offset")
                            incomingCharacteristicData.clearReference(characteristic)
                            status = BluetoothGatt.GATT_INVALID_OFFSET
                        }
                    } else {
                        // The value is treated as complete and is forwarded to the calling instance
                        commandHandler.post {
                            onDataReceived?.invoke(device, characteristic, value ?: ByteArray(0))
                        }
                    }

                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.e(TAG, "onCharacteristicWriteRequest -> ${BTGattStatus(status)}")
                    }

                    if (responseNeeded) {
                        commandHandler.post {
                            gattServer?.sendResponse(device, requestId, status, offset, null)
                        }
                    }
                }

                @SuppressLint("MissingPermission")
                override fun onDescriptorReadRequest( device: BluetoothDevice?, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor?) {
                    /*
                     * A central is requesting to read a descriptor value. We check the validity of the request and
                     * send an appropriate response.
                     */
                    val device = device ?: return
                    val descriptor = descriptor ?: return
                    val characteristic = descriptor.characteristic ?: return

                    var responseValue: ByteArray? = when {
                        /*
                         * The central is requesting to read a Client Characteristic Configuration (CCC) descriptor.
                         * We return the current CCC value for the characteristic.
                         */
                        descriptor.isCCCDescriptor -> characteristic.cccValue

                        /*
                         * The central is requesting to read a Characteristic User Description (CUD) descriptor.
                         * We return the predefined user description for the characteristic.
                         */
                        descriptor.isCUDDescriptor -> BLEProfile.RANDOM_NUMBER_USER_DESCRIPTION.toByteArray()

                        else -> null
                    }

                    Log.i(
                        TAG,
                        "onDescriptorReadRequest -> address=${device.address}, requestId=$requestId, " +
                                "offset=$offset, descriptor=${descriptor.uuid}"
                    )

                    commandHandler.post {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, responseValue)
                    }
                }

                @SuppressLint("MissingPermission")
                override fun onDescriptorWriteRequest( device: BluetoothDevice?, requestId: Int, descriptor: BluetoothGattDescriptor?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
                    /*
                     * A central is requesting to write a descriptor value. We check the validity of the request, handle
                     * prepared writes, and send an appropriate response.
                     * If the descriptor is a CCCD, we also validate the value to ensure it's a valid notification/indication
                     * configuration and that the characteristic supports the requested configuration.
                     */
                    val device = device ?: return
                    val descriptor = descriptor ?: return

                    if (descriptor.isCCCDescriptor) {
                        /*
                         * The central is requesting to write a Client Characteristic Configuration (CCC) descriptor.
                         * We validate the value to ensure it's a valid notification/indication configuration and that the
                         * characteristic supports the requested configuration.
                         */
                        val status = descriptor.validateCCCDescriptorValue(value)

                        Log.i(TAG, "onDescriptorWriteRequest -> address=${device.address}, requestId=$requestId, " +
                                "descriptor=${descriptor.displayName}, preparedWrite=$preparedWrite, responseNeeded=$responseNeeded, " +
                                "offset=$offset, value=[${value?.size ?: "N/A"} bytes]")

                        if (responseNeeded) {
                            commandHandler.post {
                                gattServer?.sendResponse(device, requestId, status, offset, null)
                            }
                        }

                        return
                    }

                    Log.i(TAG, "onDescriptorWriteRequest -> address=${device.address}, requestId=$requestId, " +
                            "descriptor=${descriptor.displayName}, preparedWrite=$preparedWrite, " +
                            "responseNeeded=$responseNeeded, offset=$offset, value=[${value?.size ?: "N/A"} bytes]")

                    if (responseNeeded) {
                        commandHandler.post {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                        }
                    }
                }

                @SuppressLint("MissingPermission")
                override fun onExecuteWrite(device: BluetoothDevice?, requestId: Int, execute: Boolean) {
                    /*
                     * A central is requesting to execute prepared writes. We process the buffered writes, complete the
                     * command queue and send a success response.
                     */
                    val device = device ?: return

                    Log.i(TAG, "onExecuteWrite -> address=${device.address}, requestId=$requestId, execute=$execute")

                    if (execute) {
                        incomingCharacteristicData.references.forEach { characteristic ->
                            incomingCharacteristicData.dataForReference(characteristic)?.let { value ->
                                commandHandler.post {
                                    onDataReceived?.invoke(device, characteristic, value)
                                }
                            }
                        }
                    }

                    incomingCharacteristicData.clearAll()

                    commandHandler.post {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                    }
                }

                override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
                    val device = device ?: return

                    Log.i(TAG, "onMtuChanged -> address=${device.address}, mtu=$mtu")
                    mtuSize = mtu
                }
            }
        }
    }

    /**
     * Initializes the peripheral with the specified services. These services will be added to the GATT server and their
     * UUIDs will be included in the advertising data, allowing centrals to discover the peripheral based on the services.
     */
    fun initialize(services: List<BluetoothGattService>) {
        Log.i(TAG, "initialize")
        offeredServices = services
    }

    /**
     * Shuts down the peripheral by stopping advertising, disconnecting from all connected centrals, clearing the
     * GATT server, and updating the connection state.
     */
    @SuppressLint("MissingPermission")
    fun shutDown() {
        Log.i(TAG, "shutDown")

        stopAdvertising()

        connectedCentrals.values.forEach { central ->
            gattServer?.cancelConnection(central)
        }

        connectedCentrals.clear()

        gattServer?.close()
        gattServer = null
    }

    /**
     * Starts advertising the peripheral's presence and available services. The peripheral will be discoverable by
     * centrals and can accept connection requests. The advertising data will include the UUIDs of the services added
     * to the GATT server, allowing centrals to discover the peripheral based on the services it offers.
     */
    @SuppressLint("MissingPermission")
    fun startAdvertising() {
        if (!isReady || isAdvertising) {
            return
        }

        val bluetoothLeAdvertiser = bluetoothManager?.adapter?.bluetoothLeAdvertiser ?: run {
            Log.e(TAG, "BluetoothLeAdvertiser is not available")
            return
        }

        if (offeredServices.isEmpty()) {
            Log.e(TAG, "No services to advertise")
            return
        }

        Log.i(TAG, "startAdvertising")

        isAdvertising = true

        /*
         * We open the GATT server if neccessary to manage the services and characteristics offered by the peripheral
         * and update the advertised services.
         */
        if (gattServer == null) {
            gattServer = bluetoothManager?.openGattServer(applicationContext, gattServerCallback)
        }

        /*
         * We define some advertise settings the advertising performance and power consumption.
         * The settings depend on the use case.
         */
        val settings: AdvertiseSettings = AdvertiseSettings.Builder()
            // ADVERTISE_MODE_LOW_LATENCY enables continuous advertising for best results but uses most power.
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            // ADVERTISE_TX_POWER_MEDIUM is a good balance between range and power consumption.
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            // Allow centrals to connect to this peripheral.
            .setConnectable(true)
            .build()

        /*
         * We define the advertising data to include the UUIDs of the services offered by the peripheral.
         */
        val advertiseDataBuilder = AdvertiseData.Builder()

        offeredServices.forEach { service ->
            gattServer?.addService(service)
            advertiseDataBuilder.addServiceUuid(ParcelUuid(service.uuid))
        }

        val advertiseData = advertiseDataBuilder.build()

        /*
         * We also define a scan response to include the device name that will be sent in response to active
         * scan requests from centrals.
         */
        val scanResponse: AdvertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        bluetoothLeAdvertiser.startAdvertising(settings, advertiseData, scanResponse, advertiseCallback)
    }

    /**
     * Stops advertising the peripheral. The peripheral will no longer be discoverable by centrals and will not accept
     * new connection requests.
     * Existing connections with centrals will remain active until they disconnect or the GATT server is closed.
     */
    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        if (!isAdvertising) {
            return
        }

        val bluetoothLeAdvertiser = bluetoothManager?.adapter?.bluetoothLeAdvertiser ?: run {
            Log.e(TAG, "BluetoothLeAdvertiser is not available")
            return
        }

        Log.i(TAG, "stopAdvertising")

        bluetoothLeAdvertiser.stopAdvertising(advertiseCallback)

        isAdvertising = false
    }

    /**
     * Updates the value of a characteristic in the GATT server. This value will be returned in read requests from
     * connected centrals and can be used in notifications/indications sent to centrals.
     */
    fun updateCharacteristicData(data: ByteArray, characteristicUuid: UUID) {
        if (!isReady) {
            return
        }

        val characteristic = gattServer?.services?.findCharacteristicByUuid(characteristicUuid) ?: run {
            return
        }

        outgoingCharacteristicData.update(data, characteristic)
    }

    @SuppressLint("MissingPermission")
    fun writeCharacteristicData(characteristicUuid: UUID, address: String? = null) {
        if (!isReady) {
            return
        }

        val gattServer = gattServer ?: run {
            return
        }

        val centrals = address?.let {
            connectedCentrals.values.filter { central -> central.address == it }
        } ?: connectedCentrals.values

        if (centrals.isEmpty()) {
            return
        }

        val characteristic = offeredServices.findCharacteristicByUuid(characteristicUuid) ?: run {
            Log.e(TAG, "writeCharacteristicData -> Invalid characteristic $characteristicUuid")
            return
        }

        /*
         * We iterate over the connected centrals and the characteristics with updated data to send notifications/indications
         */
        centrals.forEach { central ->
            outgoingCharacteristicData.chunkedDataForReference(characteristic, mtuSize)?.let { chunks ->
                chunks.forEach { data ->
                    Log.i(TAG, "writeCharacteristicData -> Writing ${data.size} bytes to: address=${central.address}")

                    commandQueue.enqueue {
                        val status = gattServer.notifyCharacteristicChanged(central, characteristic, characteristic.hasIndicateProperty, data)

                        if (status != BluetoothStatusCodes.SUCCESS) {
                            Log.e(TAG, "sendValueToCentral -> notifyCharacteristicChanged failed: ${BTGattStatus(status)}")
                            commandQueue.complete()
                        }
                    }
                }
            }
        }
    }

}
