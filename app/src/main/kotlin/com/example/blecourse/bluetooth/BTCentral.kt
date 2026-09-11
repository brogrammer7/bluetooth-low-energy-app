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
import android.bluetooth.BluetoothDevice.TRANSPORT_LE
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattConnectionSettings
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.example.blecourse.bluetooth.models.BTCommandQueue
import com.example.blecourse.bluetooth.models.BTCharacteristicData
import com.example.blecourse.extensions.displayName
import com.example.blecourse.extensions.hasIndicateProperty
import com.example.blecourse.extensions.hasNotifyProperty
import com.example.blecourse.extensions.hasReadProperty
import com.example.blecourse.extensions.isCUDDescriptor
import com.example.blecourse.extensions.writeCCCDescriptor
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.collections.set

class BTCentral(context: Context) : BTBaseHandler(context) {
    private val TAG = BTCentral::class.java.simpleName
    private val applicationContext = context.applicationContext

    private var scanCallback: ScanCallback? = null
    private var gattCallback: BluetoothGattCallback? = null
    private var connectedGatt: BluetoothGatt? = null

    private var targetServiceUuids: Map<UUID, List<UUID>>? = null

    private val commandHandler = Handler(Looper.getMainLooper())
    private val commandQueue = BTCommandQueue()

    var discoveredPeripherals = mutableStateMapOf<String, BTPeripheralInfo>()
        private set

    var connectedPeripheralInfo by mutableStateOf<BTPeripheralInfo?>(null)
        private set

    var connectedPeripheralData by mutableStateOf(BTCharacteristicData())
        private set

    var isReady by mutableStateOf(false)
        private set

    var isScanning by mutableStateOf(false)
        private set

    var isConnecting by mutableStateOf(false)
        private set

    var isConnected by mutableStateOf(false)
        private set

    init {
        bluetoothManager?.adapter?.let { _ ->
            isReady = true

            scanCallback = object : ScanCallback() {
                @SuppressLint("MissingPermission")
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

            gattCallback = object : BluetoothGattCallback() {
                @SuppressLint("MissingPermission")
                override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        return
                    }

                    val gatt = gatt ?: return
                    val device = gatt.device

                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            Log.i(TAG, "onConnectionStateChange -> Connected to: ${device.address}")

                            connectedGatt = gatt

                            commandHandler.postDelayed({
                                gatt.discoverServices()
                            }, 50)
                        }

                        BluetoothProfile.STATE_DISCONNECTED -> {
                            Log.i(TAG, "onConnectionStateChange -> Disconnected from: ${device.address}")

                            gatt.close()

                            connectedGatt = null
                            connectedPeripheralInfo = null
                            isConnected = false
                        }
                    }
                }

                @SuppressLint("MissingPermission")
                override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        return
                    }

                    val gatt = gatt ?: return
                    val device = gatt.device
                    val targets = targetServiceUuids ?: emptyMap()

                    gatt.services.forEach { service ->
                        if (targets.contains(service.uuid)) {
                            service.characteristics.forEach { characteristic ->
                                if (targets[service.uuid]?.contains(characteristic.uuid) == true) {
                                    if (characteristic.hasIndicateProperty || characteristic.hasNotifyProperty) {
                                        commandQueue.enqueue {
                                            gatt.setCharacteristicNotification(characteristic, true)
                                            characteristic.writeCCCDescriptor(gatt, true)
                                        }
                                    }

                                    if (characteristic.hasReadProperty) {
                                        commandQueue.enqueue {
                                            gatt.readCharacteristic(characteristic)
                                        }
                                    }

                                    characteristic.descriptors.forEach { descriptor ->
                                        commandQueue.enqueue {
                                            gatt.readDescriptor(descriptor)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    isConnecting = false
                    isConnected = true
                }

                override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                    BTDataDecoder.decodeDataForCharacteristic(value, characteristic.uuid)?.let { data ->
                        connectedPeripheralData.update(data, characteristic)
                    }

                    commandQueue.complete()
                }

                override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        commandQueue.complete()
                        return
                    }

                    BTDataDecoder.decodeDataForCharacteristic(value, characteristic.uuid)?.let { data ->
                        connectedPeripheralData.update(data, characteristic)
                    }

                    commandQueue.complete()
                }

                override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int, value: ByteArray) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        commandQueue.complete()
                        return
                    }

                    if (!descriptor.isCUDDescriptor) {
                        commandQueue.complete()
                        return
                    }

                    descriptor.characteristic.displayName = String(value, StandardCharsets.UTF_8)

                    commandQueue.complete()
                }
            }
        }
    }

    fun initialize(targets: Map<UUID, List<UUID>>? = null) {
        Log.i(TAG, "initialize")

        this.targetServiceUuids = targets
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

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        if (!isReady || isConnected || isConnecting) {
            return
        }

        val peripheral = bluetoothManager?.adapter?.getRemoteDevice(address) ?: run {
            Log.e(TAG, "connect -> Unknown peripheral: $address")
            return
        }

        isConnecting = true

        connectedPeripheralInfo = BTPeripheralInfo(peripheral)
        connectedPeripheralData.clear()

        if (Build.VERSION.SDK_INT >= 37) {
            val settings = BluetoothGattConnectionSettings.Builder()
                .setAutoConnectEnabled(false)
                .setTransport(TRANSPORT_LE)
                .build()

            val executor = Executors.newSingleThreadExecutor()

            peripheral.connectGatt(settings, executor, gattCallback!!)
        } else {
            @Suppress("DEPRECATION")
            peripheral.connectGatt(applicationContext, false, gattCallback, TRANSPORT_LE)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        if (!isConnected) {
            return
        }

        val gatt = connectedGatt ?: run {
            Log.w(TAG, "disconnect -> Not connected")
            return
        }

        gatt.disconnect()
    }

    @SuppressLint("MissingPermission")
    fun readCharacteristic(uuid: UUID) {
        val gatt = connectedGatt ?: return

        val characteristic = gatt.services.flatMap { it.characteristics }.firstOrNull { it.uuid == uuid } ?: run {
            Log.e(TAG, "readCharacteristic -> Characteristic $uuid is not available")
            return
        }

        if (!characteristic.hasReadProperty) {
            Log.e(TAG, "readCharacteristic -> Characteristic is not readable")
            return
        }

        commandQueue.enqueue {
            gatt.readCharacteristic(characteristic)
        }
    }
}