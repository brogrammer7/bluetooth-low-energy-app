package com.example.blecourse.bluetooth

import android.content.Context
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.BOND_BONDED
import android.bluetooth.BluetoothDevice.BOND_BONDING
import android.bluetooth.BluetoothDevice.BOND_NONE
import android.bluetooth.BluetoothDevice.TRANSPORT_LE
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattConnectionSettings
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.blecourse.extensions.BTGattStatus
import com.example.blecourse.extensions.BTProfileState
import com.example.blecourse.extensions.BTScanError
import com.example.blecourse.bluetooth.profiles.BLEProfile
import java.util.concurrent.Executors
import kotlin.collections.set

/**
 * The BTFileSender class is responsible for sending files to nearby Bluetooth receivers using L2CAP sockets.
 * It manages the scanning, connecting, and data transfer processes, and provides state information about the
 * current operation.
 */
class BTFileSender(context: Context) : BTBaseHandler(context) {
    private val TAG = BTFileSender::class.java.simpleName
    private val applicationContext = context.applicationContext

    // Maximum file payload accepted by this in-memory workshop implementation.
    private val maximumFileSize = 100 * 1024 * 1024

    /**
     * The size of the chunks to send over the L2CAP socket.
     *
     * This is set to 2048 bytes, because the Android stack fails to dynamically negotiate and
     * manage Credit-Based Flow Control buffers at the application level when larger chunk sizes are used.
     *
     * This is a known issue in the Android Bluetooth stack, and it can lead to data loss or
     * connection drops when sending large files over L2CAP sockets.
     */
    private val streamChunkSize = 2048

    private var scanCallback: ScanCallback? = null
    private var gattCallback: BluetoothGattCallback? = null
    private var connectedGatt: BluetoothGatt? = null
    private var connectedReceiver: BluetoothDevice? = null

    /**
     * The active Bluetooth socket used for sending data. This is set when a connection is established
     * and cleared when the connection is closed.
     */
    private var activeSocket: BluetoothSocket? = null

    // Contains the framed transfer header and complete file payload waiting to be written.
    private var outputBuffer = ByteArray(0)

    /**
     * A map of discovered receivers, where the key is the device address and the value is the BluetoothDevice object.
     * This is used to keep track of the devices that have been discovered during scanning.
     */
    var discoveredReceivers = mutableStateMapOf<String, BluetoothDevice>()
        private set

    /**
     * We use isReady to indicate that the Bluetooth adapter is available and the central handler is ready to be used.
     */
    var isReady by mutableStateOf(false)
        private set

    /**
     * Indicates whether the central handler is currently scanning for receivers.
     */
    var isScanning by mutableStateOf(false)
        private set

    /**
     * Indicates whether the central handler is currently connected to a receiver.
     */
    var isConnected by mutableStateOf(false)
        private set

    /**
     * Indicates whether the central handler is currently in the process of connecting to a receiver.
     */
    var isConnecting by mutableStateOf(false)
        private set

    /**
     * Indicates whether the central handler is currently sending data to a receiver.
     */
    var isSending by mutableStateOf(false)
        private set

    /**
     * Indicates the progress of the current file transfer, ranging from 0.0 to 1.0.
     */
    var transferProgress by mutableFloatStateOf(0.0f)
        private set

    init {
        bluetoothManager?.adapter?.let { _ ->
            isReady = true

            scanCallback = object : ScanCallback() {
                @SuppressLint("MissingPermission")
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    val device = result?.device ?: return
                    val deviceKey = device.address.uppercase()

                    if (!discoveredReceivers.containsKey(deviceKey)) {
                        // The peripheral appears the first time. Add it to the discoveredReceivers map.
                        Log.i(TAG, "onScanResult -> address=${device.address}, name=${device.name}")

                        discoveredReceivers[deviceKey] = device
                    }
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
                        return
                    }

                    val gatt = gatt ?: return
                    val device = gatt.device

                    Log.d(
                        TAG, "onConnectionStateChange -> address=${device.address}, " +
                                "newState=${BTProfileState(newState)}"
                    )

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
                                    Log.i(
                                        TAG,
                                        "onConnectionStateChange -> Connected to: ${device.address}"
                                    )

                                    connectedGatt = gatt
                                    connectedReceiver = device

                                    isConnecting = false
                                    isConnected = true
                                }

                                BOND_BONDING -> {
                                    // Bonding is still in progress
                                    Log.i(
                                        TAG,
                                        "onConnectionStateChange -> Waiting for bonding to complete"
                                    )
                                }
                            }
                        }

                        BluetoothProfile.STATE_DISCONNECTED -> {
                            /*
                             * Receiver is disconnected.
                             * We need to close the GATT connection to free up resources to prevent resource leaks and
                             * unexpected behavior in future connections.
                             */
                            Log.i(
                                TAG,
                                "onConnectionStateChange -> Disconnected from: ${device.address}"
                            )

                            activeSocket?.close()
                            gatt.close()

                            connectedGatt = null
                            connectedReceiver = null
                            activeSocket = null
                            isConnected = false
                            isSending = false
                        }
                    }
                }
            }
        }
    }

    /**
     * Shuts down the central handler by stopping scanning and disconnecting from any connected receiver.
     */
    fun shutDown() {
        Log.i(TAG, "shutDown")

        stopScanning()
        disconnect()

        discoveredReceivers.clear()
    }

    /**
     * Starts scanning for nearby BLE receivers.
     */
    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (!isReady || isScanning) {
            return
        }

        this.discoveredReceivers.clear()

        val bluetoothLeScanner = bluetoothManager?.adapter?.bluetoothLeScanner ?: run {
            Log.e(TAG, "startScanning -> BluetoothLeScanner is not available")
            return
        }

        isScanning = true

        /*
         * We define a filter to only discover peripherals that advertise the L2CAP service UUID.
         */
        val filters: List<ScanFilter> = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BLEProfile.L2CAP_SERVICE_UUID))
                .build()
        )

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
            // We use MATCH_MODE_STICKY because we don't want to discover peripherals with low signal strength.
            .setMatchMode(ScanSettings.MATCH_MODE_STICKY)
            // MATCH_NUM_FEW_ADVERTISEMENT ensures that you will get a callback for the first few advertisement packets
            // of a peripheral. This is useful to get some initial data of a peripheral right after it is discovered, but also allows you to get updates of the data in the next callbacks if the data changes while scanning.
            .setNumOfMatches(ScanSettings.MATCH_NUM_FEW_ADVERTISEMENT)
            .build()

        bluetoothLeScanner.startScan(filters, settings, scanCallback)
    }

    /**
     * Stops scanning for nearby BLE receivers.
     */
    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (!isScanning) {
            return
        }

        Log.i(TAG, "stopScanning -> Stop scanning")

        val bluetoothLeScanner = bluetoothManager?.adapter?.bluetoothLeScanner ?: run {
            Log.e(TAG, "stopScanning -> BluetoothLeScanner is not available")
            return
        }

        bluetoothLeScanner.stopScan(scanCallback)

        isScanning = false
    }

    /**
     * Connects to a nearby receiver with the given address. This method initiates a GATT connection to the receiver.
     * The connection process is asynchronous, and the result will be reported through the BluetoothGattCallback.
     */
    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        if (!isReady || isConnected || isConnecting) {
            return
        }

        val receiver = bluetoothManager?.adapter?.getRemoteDevice(address) ?: run {
            Log.e(TAG, "connect -> Unknown receiver: $address")
            return
        }

        Log.i(TAG, "connect -> Connecting to: ${receiver.address}")

        isConnecting = true

        /*
         * We set autoConnect=false to connect immediately. Otherwise, Android manages the connection in the background
         * and connects whenever the peripheral is visible.
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
             * interfere with each other.
             */
            val executor = Executors.newSingleThreadExecutor()

            receiver.connectGatt(settings, executor, gattCallback!!)
        } else {
            /*
             * For Android versions below 17, we use the deprecated connectGatt method.
             */
            @Suppress("DEPRECATION")
            receiver.connectGatt(applicationContext, false, gattCallback, TRANSPORT_LE)
        }
    }

    /**
     * Disconnects from the currently connected receiver.
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
     * Sends a file to a nearby receiver. The file is sent in chunks over an L2CAP socket, and the
     * transfer progress is tracked.
     */
    @SuppressLint("MissingPermission")
    fun sendFile(fileData: ByteArray, fileName: String, psm: Int) {
        if (!isConnected) {
            return
        }

        if (fileData.size > maximumFileSize) {
            Log.e(TAG, "sendFile -> File size exceeds the supported size")
            return
        }

        val header = BTDataEncoder.encodeUInt(fileData.size.toUInt()) + fileName.encodeToByteArray()

        outputBuffer = BTDataEncoder.encodeUInt(header.size.toUInt()) + header + fileData

        transferProgress = 0.0f
        isSending = true

        Log.i(TAG, "sendFile -> fileName=$fileName, fileSize=${fileData.size}, totalSize=${outputBuffer.size}")

        /*
         * Create an L2CAP socket to send the file data.
         * The PSM (Protocol/Service Multiplexer) is used to identify the service to connect to.
         * The PSM is provided by the receiver and is used to establish a connection over L2CAP.
         */
        activeSocket = connectedReceiver?.createInsecureL2capChannel(192)

        /*
         * Connect and send on a background thread so the caller is not blocked while the Bluetooth
         * stack establishes the L2CAP channel.
         *
         * A short delay gives the receiver time to finish preparing the server-side socket after it
         * shares the PSM, which makes the subsequent connect call more reliable.
         */
        Thread {
            Thread.sleep(100)

            activeSocket?.connect()

            sendDataToSocket()
        }.start()
    }

    /**
     * Sends data to the connected L2CAP socket.
     */
    private fun sendDataToSocket() {
        if (outputBuffer.isEmpty()) {
            Log.e(TAG, "sendDataToSocket -> Output buffer is empty")
            return
        }

        val socket = activeSocket ?: run {
            Log.e(TAG, "sendDataToSocket -> Active socket is null")
            return
        }

        if (!socket.isConnected) {
            Log.e(TAG, "sendDataToSocket -> Socket is not connected")
            return
        }

        isSending = true

        try {
            val outputStream = socket.outputStream

            var writeOffset = 0

            while (writeOffset < outputBuffer.size) {
                val maxPaketSize = socket.maxTransmitPacketSize
                val writeSize = minOf(streamChunkSize, outputBuffer.size - writeOffset, maxPaketSize)

                outputStream.write(outputBuffer, writeOffset, writeSize)

                writeOffset += writeSize

                transferProgress = writeOffset.toFloat() / outputBuffer.size.toFloat()

                Thread.sleep(10)
            }

            outputStream.flush()
            transferProgress = 1.0f

            Log.i(TAG, "sendDataToSocket -> Transfer complete")
        } catch (e: Exception) {
            Log.e(TAG, "sendDataToSocket -> ${e.message}")
        }

        isSending = false
    }
}