package com.example.blecourse.extensions

import android.bluetooth.BluetoothStatusCodes

/**
 * A simple wrapper class for Bluetooth status codes to provide more readable output when logging or displaying status
 * messages. It takes an integer status code and converts it to a human-readable string based on the known Bluetooth
 * status codes defined in BluetoothStatusCodes.
 */
class BTBluetoothStatus(val status: Int) {
    override fun toString(): String = when (status) {
        BluetoothStatusCodes.SUCCESS -> "Success"
        BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED -> "Bluetooth is not enabled"
        BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ALLOWED -> "Bluetooth is not allowed"
        BluetoothStatusCodes.ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION -> "Missing connect permission"
        BluetoothStatusCodes.ERROR_DEVICE_NOT_BONDED -> "Device is not bonded"
        BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY -> "The remote device is busy"
        BluetoothStatusCodes.ERROR_PROFILE_SERVICE_NOT_BOUND -> "Profile service is not bound"
        BluetoothStatusCodes.ERROR_GATT_WRITE_NOT_ALLOWED -> "Writing is not allowed"
        else -> "Unknown status ($status)"
    }
}