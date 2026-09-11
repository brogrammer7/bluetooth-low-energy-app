package com.example.blecourse.bluetooth

import android.content.Context
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice.BOND_BONDED
import android.bluetooth.BluetoothDevice.BOND_BONDING
import android.bluetooth.BluetoothDevice.BOND_NONE
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
import android.bluetooth.BluetoothDevice.TRANSPORT_LE
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattConnectionSettings
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.example.blecourse.bluetooth.models.BTCommandQueue
import com.example.blecourse.bluetooth.models.BTCharacteristicData
import com.example.blecourse.extensions.BTBluetoothStatus
import com.example.blecourse.extensions.BTGattStatus
import com.example.blecourse.extensions.BTProfileState
import com.example.blecourse.extensions.BTScanError
import com.example.blecourse.extensions.displayName
import com.example.blecourse.extensions.findCharacteristicByUuid
import com.example.blecourse.extensions.hasIndicateProperty
import com.example.blecourse.extensions.hasNotifyProperty
import com.example.blecourse.extensions.hasReadProperty
import com.example.blecourse.extensions.hasWriteProperty
import com.example.blecourse.extensions.hasWriteWithoutResponseProperty
import com.example.blecourse.extensions.isCUDDescriptor
import com.example.blecourse.extensions.propertyNames
import com.example.blecourse.extensions.writeCCCDescriptor
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.collections.set

/**
 * Implements a BLE Central handler providing the following capabilities:
 * - Scanning for peripherals and their services/characteristics
 * - Connecting to peripherals
 * - Reading/writing characteristics data
 */
class BTCentral(context: Context) : BTBaseHandler(context) {
    private val TAG = BTCentral::class.java.simpleName
    private val applicationContext = context.applicationContext

    // Minimum signal strength (in dBm) to consider a peripheral for discovery
    private val minRSSI = -80

    private var scanCallback: ScanCallback? = null
    private var gattCallback: BluetoothGattCallback? = null
    private var connectedGatt: BluetoothGatt? = null

    /**
     * We can specify target service UUIDs and their related characteristic UUIDs to filter the discovered services
     * and characteristics after connecting to a peripheral.
     */
    private var targetServiceUuids: Map<UUID, List<UUID>>? = null

    /**
     * We use a Handler to post delayed tasks for certain operations like starting service discovery after connecting
     * to ensure that the operations are executed sequentially and to prevent potential deadlocks.
     */
    private val commandHandler = Handler(Looper.getMainLooper())
    /**
     * We use a command queue to manage the asynchronous BLE operations sequentially. This ensures that we don't
     * execute multiple operations at the same time that could interfere with each other and cause unexpected behavior.
     */
    private val commandQueue = BTCommandQueue()

    /**
     * Maximum Transmission Unit (MTU)
     * This is the maximum size in bytes for BLE that can be transmitted in a single packet. Will be updated later
     * if the peripheral supports a higher MTU.
     */
    private var mtuSize = 23

    /**
     * We store the discovered peripherals in a mutable state map to automatically update the UI when new
     * peripherals have been discovered.
     */
    var discoveredPeripherals = mutableStateMapOf<String, BTPeripheralInfo>()
        private set

    /**
     * Mutable state that hold information about the connected peripheral. It is null when no peripheral is connected.
     */
    var connectedPeripheralInfo by mutableStateOf<BTPeripheralInfo?>(null)
        private set

    /**
     * Mutable state containing characteristic data of the connected peripheral to easily update the UI with changes.
     */
    var connectedPeripheralData by mutableStateOf(BTCharacteristicData())
        private set

    /**
     * We use isReady to indicate that the Bluetooth adapter is available and the central handler is ready to be used.
     */
    var isReady by mutableStateOf(false)
        private set

    /**
     * We use isScanning to indicate that we are currently scanning for peripherals. This is useful to update the
     * UI accordingly and to prevent multiple scanning attempts at the same time.
     */
    var isScanning by mutableStateOf(false)
        private set

    /**
     * We use isConnecting to indicate that we are in the process of connecting to a peripheral. This is useful to
     * prevent multiple connection attempts at the same time and to show a loading state in the UI while connecting.
     */
    var isConnecting by mutableStateOf(false)
        private set

    /**
     * We use isConnected to indicate that we are currently connected to a peripheral. This is useful to update the
     * UI accordingly and to prevent certain operations that require a connection when we are not connected.
     */
    var isConnected by mutableStateOf(false)
        private set

    init {
        bluetoothManager?.adapter?.let { _ ->
            isReady = true

            scanCallback = object : ScanCallback() {
                @SuppressLint("MissingPermission")
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    val rssi = result?.rssi ?: return

                    if (rssi < minRSSI) {
                        // We ignore peripherals with low signal strength to optimize the scanning results.
                        return
                    }

                    val isObservable = BTDataDecoder.isValidManufacturerData(result.scanRecord?.manufacturerSpecificData)

                    val device = result.device
                    val deviceKey = device.address.uppercase()

                    if (!discoveredPeripherals.containsKey(deviceKey)) {
                        // The peripheral appears the first time. Add it to the discoveredPeripherals map.
                        Log.i(TAG, "onScanResult -> address=${device.address}, name=${device.name}")

                        val peripheralInfo = BTPeripheralInfo(device)
                        peripheralInfo.isConnectable = result.isConnectable
                        peripheralInfo.isObservable = isObservable

                        discoveredPeripherals[deviceKey] = peripheralInfo
                    }

                    discoveredPeripherals[deviceKey]?.rssi = result.rssi
                }

                override fun onScanFailed(errorCode: Int) {
                    // Something went wrong while scanning
                    Log.e(TAG, "onScanFailed -> ${BTScanError(errorCode)}")
                    stopScanning()
                }
            }

            gattCallback = object : BluetoothGattCallback() {
                @SuppressLint("MissingPermission")
                override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.e(TAG, "onConnectionStateChange -> GATT failure: ${BTGattStatus(status)}")
                        commandQueue.complete()
                        return
                    }

                    val gatt = gatt ?: run {
                        Log.w(TAG, "onConnectionStateChange -> gatt is null")
                        commandQueue.complete()
                        return
                    }

                    val device = gatt.device

                    Log.d(TAG, "onConnectionStateChange -> address=${device.address}, newState=${BTProfileState(newState)}")

                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            /*
                             * Peripheral is connected, but we need to check if it is bonded yet.
                             * Otherwise, we need to wait for the bonding process to complete before we can access
                             * certain services/characteristics.
                             */
                            when (device.bondState) {
                                BOND_NONE, BOND_BONDED -> {
                                    // Bonding is complete, we can start service discovery now.
                                    Log.i(TAG, "onConnectionStateChange -> Connected to: ${device.address}")

                                    connectedGatt = gatt

                                    commandHandler.postDelayed({
                                        gatt.discoverServices()
                                    }, 50) // We add a small delay here to prevent deadlocks
                                }

                                BOND_BONDING -> {
                                    // Bonding is still in progress
                                    Log.i(TAG, "onConnectionStateChange -> Waiting for bonding to complete")
                                }
                            }
                        }

                        BluetoothProfile.STATE_DISCONNECTED -> {
                            /*
                             * Peripheral is disconnected.
                             * We need to close the GATT connection to free up resources to prevent resource leaks and
                             * unexpected behavior in future connections.
                             */
                            Log.i(TAG, "onConnectionStateChange -> Disconnected from: ${device.address}")

                            gatt.close()

                            connectedGatt = null
                            connectedPeripheralInfo = null

                            commandQueue.clear()

                            isConnected = false
                        }
                    }
                }

                @SuppressLint("MissingPermission")
                override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.e(TAG, "onServicesDiscovered -> GATT failure: ${BTGattStatus(status)}")
                        commandQueue.complete()
                        return
                    }

                    val gatt = gatt ?: run {
                        Log.w(TAG, "onServicesDiscovered -> gatt is null")
                        commandQueue.complete()
                        return
                    }

                    val device = gatt.device
                    val targets = targetServiceUuids ?: emptyMap()

                    /*
                     * Services/characteristics have been discovered. We need to check which ones are supported.
                     */
                    gatt.services.forEach { service ->
                        if (targets.contains(service.uuid)) {
                            Log.i(TAG, "onServicesDiscovered -> address=${device.address}, service=${service.displayName}")

                            /*
                             * We are interested in this service. Now check which characteristics are supported and
                             * read the initial values if possible. We also enable notifications/indications for
                             * supported characteristics to receive live updates.
                             */
                            service.characteristics.forEach { characteristic ->
                                if (targets[service.uuid]?.contains(characteristic.uuid) == true) {
                                    Log.i(TAG, "onServicesDiscovered -> address=${device.address}, " +
                                            "characteristic=${characteristic.displayName} (${characteristic.propertyNames})")

                                    connectedPeripheralData.update(emptyMap(), characteristic)

                                    if (characteristic.hasIndicateProperty || characteristic.hasNotifyProperty) {
                                        /*
                                         * We enable notifications/indications for supported characteristics to
                                         * receive live updates when the value changes.
                                         */
                                        commandQueue.enqueue {
                                            val status = if (gatt.setCharacteristicNotification(characteristic, true)) {
                                                /*
                                                 * We also need to write to the Client Characteristic Configuration
                                                 * descriptor to specify whether we want notifications or indications.
                                                 */
                                                characteristic.writeCCCDescriptor(gatt, true)
                                            } else {
                                                BluetoothGatt.GATT_FAILURE
                                            }

                                            if (status != BluetoothGatt.GATT_SUCCESS) {
                                                Log.e(TAG, "onServicesDiscovered -> ${BTGattStatus(status)}")
                                                commandQueue.complete()
                                            }
                                        }
                                    }

                                    if (characteristic.hasReadProperty) {
                                        /*
                                         * We read the initial value of the characteristic to populate the UI with
                                         * data right after connecting.
                                         */
                                        commandQueue.enqueue {
                                            if (!gatt.readCharacteristic(characteristic)) {
                                                Log.e(TAG, "onServicesDiscovered -> readCharacteristic() failed")
                                                commandQueue.complete()
                                            }
                                        }
                                    }

                                    /*
                                     * We also read the related descriptors if there are any. Especially the
                                     * Characteristic User Description (CUD) descriptor would be interesting to get
                                     * a human-readable description of the characteristic to show in the UI.
                                     */
                                    characteristic.descriptors.forEach { descriptor ->
                                        commandQueue.enqueue {
                                            if (!gatt.readDescriptor(descriptor)) {
                                                Log.e(TAG, "onServicesDiscovered -> readDescriptor() failed")
                                                commandQueue.complete()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    isConnecting = false
                    isConnected = true
                }

                @SuppressLint("MissingPermission")
                override fun onServiceChanged(gatt: BluetoothGatt) {
                    /*
                     * Indicates that the services of the connected peripheral have changed. In this case, we need to
                     * restart the service discovery process to get the updated services and characteristics and clear
                     * the command queue to prevent any pending commands from being executed on outdated services.
                     */
                    Log.i(TAG, "onServiceChanged -> Service changed. Restarting discovery.")

                    commandQueue.clear()

                    commandHandler.postDelayed({
                        gatt.discoverServices()
                    }, 100)
                }

                override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                    /*
                     * A characteristic has been updated by the peripheral. This happens for characteristics that have
                     * notifications/indications enabled. We decode the new data and set the command queue to be complete.
                     */
                    Log.i(TAG, "onCharacteristicChange -> address=${gatt.device.address}, " +
                            "characteristic=${characteristic.displayName}, value=[${value.size} bytes]")

                    /*
                     * We decode the characteristic value and update the connectedPeripheralData accordingly. This will
                     * also update the UI with the new data.
                     */
                    BTDataDecoder.decodeDataForCharacteristic(value, characteristic.uuid)?.let { data ->
                        connectedPeripheralData.update(data, characteristic)
                    } ?: {
                        Log.w(TAG, "onCharacteristicChange -> Invalid characteristic data")
                    }
                }

                override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                    /*
                     * A characteristic has been read. We check the status to ensure it was read successfully, decode
                     * the data and set the command queue to be complete.
                     */
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.e(TAG, "onCharacteristicRead -> GATT failure: ${BTGattStatus(status)}")
                        commandQueue.complete()
                        return
                    }

                    Log.i(TAG, "onCharacteristicRead -> address=${gatt.device.address}, " +
                            "characteristic=${characteristic.displayName}, value=[${value.size} bytes]")

                    /*
                     * We decode the characteristic value and update the connectedPeripheralData accordingly.
                     * This will also update the UI with the new data.
                     */
                    BTDataDecoder.decodeDataForCharacteristic(value, characteristic.uuid)?.let { data ->
                        connectedPeripheralData.update(data, characteristic)
                    } ?: {
                        Log.w(TAG, "onCharacteristicRead -> Invalid characteristic data")
                    }

                    commandQueue.complete ()
                }

                override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
                    /*
                     * A characteristic has been written. We check the status to ensure it was written successfully
                     * and set the command queue to be complete.
                     */
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.e(TAG, "onCharacteristicWrite -> GATT failure: ${BTGattStatus(status)}")
                        commandQueue.complete()
                        return
                    }

                    val gatt = gatt ?: run {
                        Log.w(TAG, "onCharacteristicWrite -> gatt is null")
                        commandQueue.complete()
                        return
                    }

                    val characteristic = characteristic ?: run {
                        Log.w(TAG, "onCharacteristicWrite -> characteristic is null")
                        commandQueue.complete()
                        return
                    }

                    Log.i(TAG, "onCharacteristicWrite -> address=${gatt.device.address}, " +
                            "characteristic=${characteristic.displayName}")

                    commandQueue.complete()
                }

                override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int, value: ByteArray) {
                    /*
                     * A descriptor has been read. We check the status to ensure it was read successfully and set
                     * the command queue to be complete.
                     */
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.e(TAG, "onDescriptorRead -> GATT failure: ${BTGattStatus(status)}")
                        commandQueue.complete()
                        return
                    }

                    if (!descriptor.isCUDDescriptor) {
                        commandQueue.complete()
                        return
                    }

                    /*
                     * The Characteristic User Description (CUD) descriptor provides a human-readable name for the
                     * characteristic. We decode the value and set it as the description of the characteristic.
                     */
                    val userDescription = value.toString(Charsets.UTF_8)

                    Log.i(TAG, "onDescriptorRead -> address=${gatt.device.address}, descriptor=${descriptor.displayName}, value=$userDescription")

                    descriptor.characteristic.displayName = userDescription

                    commandQueue.complete()
                }

                override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
                    /*
                     * A descriptor has been written. We check the status to ensure it was written successfully
                     * and set the command queue to be complete.
                     */
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.e(TAG, "onDescriptorWrite -> GATT failure: ${BTGattStatus(status)}")
                        commandQueue.complete()
                        return
                    }

                    val gatt = gatt ?: run {
                        Log.w(TAG, "onDescriptorWrite -> gatt is null")
                        commandQueue.complete()
                        return
                    }

                    val descriptor = descriptor ?: run {
                        Log.w(TAG, "onDescriptorWrite -> descriptor is null")
                        commandQueue.complete()
                        return
                    }

                    Log.i(TAG, "onDescriptorWrite -> address=${gatt.device.address}, descriptor=${descriptor.displayName}")

                    commandQueue.complete()
                }

                override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                    /*
                     * The MTU has been changed. We check the status to ensure it was changed successfully and
                     * update the mtuSize accordingly.
                     */
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.e(TAG, "onMtuChanged -> ${BTGattStatus(status)}")
                        return
                    }

                    Log.i(TAG, "onMtuChanged -> MTU changed to $mtu")
                    mtuSize = mtu
                }
            }
        }
    }

    /**
     * Initializes the central handler with the given target service UUIDs and their related characteristic UUIDs.
     * This allows us to filter the discovered services and characteristics after connecting to a peripheral.
     */
    fun initialize(targets: Map<UUID, List<UUID>>? = null) {
        Log.i(TAG, "initialize")

        this.targetServiceUuids = targets
    }

    /**
     * Shuts down the central handler by stopping scanning, disconnecting from the connected peripheral and clearing
     * the command queue. This ensures that we have a clean state when switching between different Bluetooth handlers (central, peripheral, observer, broadcaster) in the app.
     */
    fun shutDown() {
        Log.i(TAG, "shutDown")

        stopScanning()
        disconnect()

        commandQueue.clear()
    }

    /**
     * Starts scanning for peripherals. We use a ScanCallback to receive scan results asynchronously.
     * The discovered peripherals are stored in the discoveredPeripherals map. We also apply some filters to only discover relevant peripherals
     */
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
        discoveredPeripherals.clear()

        /*
         * We can apply filters to only discover relevant peripherals. This is especially useful in crowded environments
         * with many peripherals around to optimize the scanning results and power consumption.
         * At the moment, we are interested in all kinds of peripherals. We will check the provided services/characteristics
         * later during service discovery. So, we don't use any filters for now.
         */
        val filters: List<ScanFilter> = emptyList()

        /*
         * Use the following to filter on the service UUIDs to only discover peripherals that have the relevant services
         * for your use case. Please note that this filter only works for services that are advertised by the peripheral.
         */
        // val filters = targetServiceUuids?.keys?.map { ScanFilter.Builder().setServiceUuid(ParcelUuid(it)).build() } ?: emptyList()

        /*
         * We define some settings for scanning to optimize the scanning results and power consumption.
         * The settings depend pretty much on the use case. It is a trade-off between performance and power consumption.
         */
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
    }

    /**
     * Stops scanning for peripherals.
     */
    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (!isScanning) {
            return
        }

        val bluetoothLeScanner = bluetoothManager?.adapter?.bluetoothLeScanner ?: run {
            Log.e(TAG, "stopScanning -> BluetoothLeScanner is not available")
            return
        }

        Log.i(TAG, "stopScanning")

        bluetoothLeScanner.stopScan(scanCallback)

        isScanning = false
    }

    /**
     * Connects to a peripheral with the given address.
     * We use a BluetoothGattCallback to receive connection updates and data asynchronously.
     */
    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        if (!isReady || isConnected || isConnecting) {
            return
        }

        val peripheral = bluetoothManager?.adapter?.getRemoteDevice(address) ?: run {
            Log.e(TAG, "connect -> Unknown peripheral: $address")
            return
        }

        Log.i(TAG, "connect -> Connecting to: ${peripheral.address}")

        isConnecting = true

        /*
         * We set the connected peripheral info and clear the connected peripheral data before starting
         * the connection process to update the UI accordingly and prevent any old data from being shown.
         */
        connectedPeripheralInfo = BTPeripheralInfo(peripheral)
        connectedPeripheralData.clear()

        /*
         * We set autoConnect=false to connect immediately. Otherwise, Android manages the connection
         * in the background and connects whenever the peripheral is visible.
         * We explicitly set transport to TRANSPORT_LE to ensure that we are using Bluetooth Low Energy (BLE).
         * Otherwise, TRANSPORT_AUTO is used which often defaults to Bluetooth Classic.
         */
        if (Build.VERSION.SDK_INT >= 37) {
            /*
             * Starting from Android 17 (API level 37), we use the new BluetoothGattConnectionSettings
             * to configure the connection settings more explicitly.
             */
            val settings = BluetoothGattConnectionSettings.Builder()
                .setAutoConnectEnabled(false)
                .setTransport(TRANSPORT_LE)
                .build()

            /*
             * We use a single-threaded executor to ensure that the GATT operations are executed
             * sequentially and do not interfere with each other.
             */
            val executor = Executors.newSingleThreadExecutor()

            peripheral.connectGatt(settings, executor, gattCallback!!)
        } else {
            /*
             * For Android versions below 17, we use the deprecated connectGatt method.
             */
            @Suppress("DEPRECATION")
            peripheral.connectGatt(applicationContext, false, gattCallback, TRANSPORT_LE)
        }
    }

    /**
     * Disconnects from the connected peripheral.
     */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        if (!isConnected) {
            return
        }

        val gatt = connectedGatt ?: run {
            Log.w(TAG, "disconnect -> Not connected")
            return
        }

        Log.i(TAG, "disconnect -> Disconnecting from: ${gatt.device.address}")

        gatt.disconnect()
    }

    /**
     * Sends the given data to the connected peripheral. The write operation is enqueued in the command queue to ensure
     * that it is executed sequentially and does not interfere with other ongoing operations.
     */
    @SuppressLint("MissingPermission")
    fun writeCharacteristic(data: ByteArray, uuid: UUID, withoutResponse: Boolean = false) {
        val gatt = connectedGatt ?: run {
            Log.e(TAG, "writeCharacteristic -> Not connected")
            return
        }

        val characteristic = gatt.services.findCharacteristicByUuid(uuid) ?: run {
            Log.e(TAG, "writeCharacteristic -> Characteristic $uuid is not available")
            return
        }

        val writeType = if (withoutResponse && characteristic.hasWriteWithoutResponseProperty) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else if (characteristic.hasWriteProperty) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            Log.e(TAG, "writeCharacteristic -> Characteristic does not support writing")
            return
        }

        /*
         * We need to chunk the data into smaller packets if it exceeds the MTU size. The maximum payload size for a
         * BLE packet is (MTU - 3) bytes, because 3 bytes are used for the ATT protocol overhead. If the data exceeds
         * this size, we split it into multiple chunks and add them sequentially to the write queue.
         */
        val chunkedData = if((data.size > mtuSize - 3))
            data.toList().chunked(mtuSize - 3).map { it.toByteArray() }
        else
            listOf(data)

        chunkedData.forEach { chunk ->
            commandQueue.enqueue {
                Log.i(TAG, "writeCharacteristic -> Writing ${chunk.size} bytes " +
                        "to: characteristic=${characteristic.displayName}")

                val status = gatt.writeCharacteristic(characteristic, chunk, writeType)
                if (status != BluetoothStatusCodes.SUCCESS) {
                    Log.e(TAG, "writeCharacteristic -> ${BTBluetoothStatus(status)}")
                    commandQueue.complete()
                }
            }
        }
    }

    /**
     * Triggers a read operation for the given characteristic from the connected peripheral. The read operation is
     * enqueued to ensure that it is executed sequentially and does not interfere with other ongoing operations.
     */
    @SuppressLint("MissingPermission")
    fun readCharacteristic(uuid: UUID) {
        val gatt = connectedGatt ?: run {
            Log.e(TAG, "readCharacteristic -> Not connected")
            return
        }

        val characteristic = gatt.services.flatMap { it.characteristics }.firstOrNull { it.uuid == uuid } ?: run {
            Log.e(TAG, "readCharacteristic -> Characteristic $uuid is not available")
            return
        }

        if (!characteristic.hasReadProperty) {
            Log.e(TAG, "readCharacteristic -> Characteristic is not readable")
            return
        }

        Log.i(TAG, "readCharacteristic -> address=${gatt.device.address}, characteristic=$uuid")

        commandQueue.enqueue {
            if (!gatt.readCharacteristic(characteristic)) {
                Log.e(TAG, "readCharacteristic -> readCharacteristic failed")
                commandQueue.complete()
            }
        }
    }
}