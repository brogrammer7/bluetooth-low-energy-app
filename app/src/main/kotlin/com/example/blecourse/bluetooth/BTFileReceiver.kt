package com.example.blecourse.bluetooth

import android.content.Context
import android.annotation.SuppressLint
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.os.ParcelUuid
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.blecourse.extensions.BTAdvertiseError
import com.example.blecourse.bluetooth.profiles.BLEProfile
import java.io.IOException
import kotlin.math.min

/**
 * Receives a file over a BLE L2CAP connection and persists it to `MediaStore`.
 *
 * This handler publishes an insecure L2CAP server socket, advertises the L2CAP service UUID,
 * accepts incoming connections, decodes the transfer header (file size and file name), streams
 * the payload into memory, and writes the completed file to the user's Documents collection.
 *
 * UI-friendly state is exposed via Compose state properties for publishing/advertising/receiving
 * status, transfer progress, published PSM, and the resulting `receivedFileUri`.
 */
class BTFileReceiver(context: Context) : BTBaseHandler(context) {
    private val TAG = BTFileReceiver::class.java.simpleName
    private val applicationContext = context.applicationContext

    /**
     * The chunk size for reading data from the Bluetooth socket. This value can be adjusted based
     * on the expected file sizes and the performance of the Bluetooth connection.
     */
    private val streamChunkSize = 8192

    private var advertiseCallback: AdvertiseCallback? = null
    private var serverSocket: BluetoothServerSocket? = null

    /**
     * Buffers for storing incoming data. The inputChunkBuffer is used to accumulate data read from the socket,
     * while the inputBuffer is used to store the complete file data once it has been fully received.
     */
    private var inputChunkBuffer = ByteArray(0)
    private var inputBuffer = ByteArray(0)
    private var inputFileName: String? = null
    private var inputFileSize: Int? = null

    /**
     * The PSM (Protocol/Service Multiplexer) value published by the L2CAP server socket. This value
     * is used by clients to connect to the correct service on the server.
     */
    var publishedPsm: Int? by mutableStateOf(null)
        private set

    /**
     * State variables to track the status of the receiver. These include whether the receiver is ready,
     * whether it is currently publishing, advertising, or receiving data, and the progress of any ongoing
     * file transfer.
     */
    var isReady by mutableStateOf(false)
        private set

    var isPublishing by mutableStateOf(false)
        private set

    var isAdvertising by mutableStateOf(false)
        private set

    var isReceiving by mutableStateOf(false)
        private set

    var transferProgress by mutableFloatStateOf(0.0f)
        private set

    /**
     * The URI of the file that has been received and saved to the device's storage. This can be
     * used to access the file after it has been successfully transferred.
     */
    var receivedFileUri: Uri? by mutableStateOf(null)
        private set

    init {
        bluetoothManager?.adapter?.let { _ ->
            isReady = true

            advertiseCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    Log.i(TAG, "onStartSuccess")
                }

                override fun onStartFailure(errorCode: Int) {
                    Log.e(TAG, "onStartFailure -> ${BTAdvertiseError(errorCode)}")
                    isAdvertising = false
                }
            }
        }
    }


    /**
     * Shuts down the file receiver by stopping any ongoing publishing and advertising activities.
     */
    fun shutDown() {
        Log.i(TAG, "shutDown")

        stopPublishing()
    }

    /**
     * Starts publishing the L2CAP service and begins advertising it over Bluetooth LE. This method
     * sets up the necessary sockets and threads to accept incoming connections from clients.
     */
    @SuppressLint("MissingPermission")
    fun startPublishing() {
        if (!isReady || isPublishing) {
            return
        }

        Log.i(TAG, "startPublishing")

        isPublishing = true
        inputChunkBuffer = ByteArray(0)
        inputBuffer = ByteArray(0)

        try {
            // Create an L2CAP listening socket
            serverSocket = bluetoothManager?.adapter?.listenUsingInsecureL2capChannel()
            publishedPsm = serverSocket?.psm ?: -1

            Log.i(TAG, "startPublishing -> PSM=$publishedPsm")

            startAdvertising()

            // Accept L2CAP connections in background thread
            Thread {
                try {
                    acceptConnections()
                } catch (e: Exception) {
                    Log.e(TAG, "startPublishing - acceptConnections thread -> ${e.message}")
                }
            }.start()
        } catch (e: IOException) {
            Log.e(TAG, "startPublishing -> ${e.message}")
            isPublishing = false
        }
    }
    /**
     * Stops publishing the L2CAP service and advertising it over Bluetooth LE. This method closes
     * any open sockets and resets the state of the receiver.
     */
    @SuppressLint("MissingPermission")
    fun stopPublishing() {
        if (!isPublishing) {
            return
        }

        Log.i(TAG, "stopPublishing")

        stopAdvertising()

        serverSocket?.close()

        publishedPsm = null
        isPublishing = false
    }

    /**
     * Starts advertising the L2CAP service over Bluetooth LE. This method sets up the necessary
     * advertising settings and data, and begins advertising the service to nearby devices.
     */
    private fun startAdvertising() {
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
         * We define some advertise settings the advertising performance and power consumption.
         * The settings depend on the use case.
         */
        val settings: AdvertiseSettings = AdvertiseSettings.Builder()
            // ADVERTISE_MODE_LOW_LATENCY enables continuous advertising for best results but uses most power.
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            // ADVERTISE_TX_POWER_MEDIUM is a good balance between range and power consumption.
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            // Allow centrals to connect to this peripheral.
            .setConnectable(true)
            .build()

        // Advertise the GATT service UUID so centrals can discover it
        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(BLEProfile.L2CAP_SERVICE_UUID))
            .build()

        try {
            bluetoothLeAdvertiser.startAdvertising(settings, advertiseData, advertiseCallback)
        } catch (e: Exception) {
            Log.e(TAG, "startAdvertising -> Error: ${e.message}")
            isAdvertising = false
        }
    }


    /**
     * Stops advertising the L2CAP service over Bluetooth LE. This method stops any ongoing
     * advertising and releases the resources associated with it.
     */
    @SuppressLint("MissingPermission")
    private fun stopAdvertising() {
        if (!isAdvertising) {
            return
        }

        val bluetoothLeAdvertiser = bluetoothManager?.adapter?.bluetoothLeAdvertiser ?: run {
            Log.e(TAG, "stopAdvertising -> BluetoothLeAdvertiser is not available")
            isAdvertising = false
            return
        }

        Log.i(TAG, "stopAdvertising")

        try {
            isAdvertising = false
            bluetoothLeAdvertiser.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {
            Log.e(TAG, "stopAdvertising -> ${e.message}")
        }
    }

    /**
     * Accepts incoming connections from clients. This method runs in a loop, accepting connections
     * and reading data from the connected sockets.
     */
    private fun acceptConnections() {
        var isConnected = true

        while (isConnected) {
            Log.i(TAG, "acceptConnections")

            val connectedSocket: BluetoothSocket? = try {
                serverSocket?.accept()
            } catch (e: IOException) {
                Log.e(TAG, "acceptConnections -> ${e.message}")
                isConnected = false
                null
            }

            Log.i(TAG, "acceptConnections: -> address=${connectedSocket?.remoteDevice?.address}")

            connectedSocket?.let {
                readDataFromSocket(it)
            }
        }
    }

    /**
     * Reads data from the connected Bluetooth socket. This method handles the reception of file
     * metadata and file chunks, updating the transfer progress accordingly.
     */
    private fun readDataFromSocket(socket: BluetoothSocket) {
        inputFileName = null
        inputFileSize = null
        transferProgress = 0.0f
        receivedFileUri = null
        isReceiving = true

        try {
            val inputStream = socket.inputStream
            val buffer = ByteArray(streamChunkSize)

            while (socket.isConnected) {
                val count = inputStream.read(buffer, 0, buffer.size)
                if (count <= 0) {
                    Log.e(TAG, "readDataFromSocket -> count=$count")
                    break
                }

                inputChunkBuffer += buffer.copyOf(count)

                if (inputFileSize == null && inputChunkBuffer.size >= 4) {
                    val headerSize = BTDataDecoder.decodeUIntAt(inputChunkBuffer, 0).toInt()
                    if (headerSize >= 4) {
                        val totalHeaderSize = headerSize + 4
                        if (inputChunkBuffer.size >= totalHeaderSize) {
                            val header = inputChunkBuffer.copyOfRange(4, totalHeaderSize)
                            val fileSize = BTDataDecoder.decodeUIntAt(header, 0).toInt()
                            val fileName = String(header.drop(4).toByteArray())

                            if (fileSize <= 0 || fileName.isEmpty()) {
                                Log.e(TAG, "readDataFromSocket -> Invalid file metadata")
                                break
                            }

                            inputFileSize = fileSize
                            inputFileName = fileName

                            inputChunkBuffer = inputChunkBuffer.copyOfRange(totalHeaderSize, inputChunkBuffer.size)

                            Log.i(TAG, "readDataFromSocket -> Receiving: fileName=$inputFileName, size=$inputFileSize")
                        }
                    }
                }

                if (inputFileSize == null) {
                    continue
                }

                val remainingSize = inputFileSize!! - inputBuffer.size
                val readCount = min(remainingSize, inputChunkBuffer.size)

                inputBuffer += inputChunkBuffer.copyOf(readCount)
                inputChunkBuffer = inputChunkBuffer.copyOfRange(readCount, inputChunkBuffer.size)

                transferProgress = inputBuffer.size.toFloat() / inputFileSize!!.toFloat()

                if (inputBuffer.size >= inputFileSize!!) {
                    saveReceivedFile()
                    transferProgress = 1.0f
                    break
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "readDataFromSocket -> ${e.message}")
        } finally {
            socket.close()
            isReceiving = false
        }
    }

    /**
     * Saves the received file to the device's storage. This method creates a new file in the
     * MediaStore and writes the received data to it.
     */
    private fun saveReceivedFile() {
        try {
            val safeFileName = inputFileName ?: "Received File"

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
            }

            val resolver = applicationContext.contentResolver

            resolver.insert(
                MediaStore.Files.getContentUri("external"),
                values
            )?.let { uri ->
                resolver.openOutputStream(uri)?.use { stream ->
                    stream.write(inputBuffer)
                }

                Log.i(TAG, "saveReceivedFile -> Saved to MediaStore: $uri")
                receivedFileUri = uri
            } ?: run {
                Log.e(TAG, "saveReceivedFile -> Failed to insert into MediaStore")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "saveReceivedFile -> ${t.message}")
        }
    }
}
