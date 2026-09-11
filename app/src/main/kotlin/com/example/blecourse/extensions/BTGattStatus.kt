package com.example.blecourse.extensions

import android.bluetooth.BluetoothGatt

/**
 * Provides a human-readable description of the status code returned by Bluetooth GATT operations.
 */
class BTGattStatus(val status: Int) {
    private val GATT_SUCCESS = BluetoothGatt.GATT_SUCCESS
    private val GATT_INVALID_HANDLE = 0x01
    private val GATT_READ_NOT_PERMITTED = BluetoothGatt.GATT_READ_NOT_PERMITTED
    private val GATT_WRITE_NOT_PERMITTED = BluetoothGatt.GATT_WRITE_NOT_PERMITTED
    private val GATT_INVALID_PDU = 0x04
    private val GATT_INSUFFICIENT_AUTHENTICATION = BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION
    private val GATT_REQUEST_NOT_SUPPORTED = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
    private val GATT_INVALID_OFFSET = BluetoothGatt.GATT_INVALID_OFFSET
    private val GATT_INSUFFICIENT_AUTHORIZATION = BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION
    private val GATT_PREPARE_QUEUE_FULL = 0x09
    private val GATT_ATTRIBUTE_NOT_FOUND = 0x0a
    private val GATT_ATTRIBUTE_NOT_LONG = 0x0b
    private val GATT_INSUFFICIENT_KEY_SIZE = 0x0c
    private val GATT_INVALID_ATTRIBUTE_LENGTH = BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH
    private val GATT_ERROR_UNLIKELY = 0x0e
    private val GATT_INSUFFICIENT_ENCRYPTION = BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION
    private val GATT_UNSUPPORTED_GROUP_TYPE = 0x10
    private val GATT_INSUFFICIENT_RESOURCES = 0x11
    private val GATT_DATABASE_OUT_OF_SYNC = 0x12
    private val GATT_VALUE_NOT_ALLOWED = 0x13
    private val GATT_TOO_SHORT = 0x7f
    private val GATT_NO_RESOURCES = 0x80
    private val GATT_INTERNAL_ERROR = 0x81
    private val GATT_WRONG_STATE = 0x82
    private val GATT_DATABASE_FULL = 0x83
    private val GATT_BUSY = 0x84
    private val GATT_ERROR = 0x85
    private val GATT_CMD_STARTED = 0x86
    private val GATT_ILLEGAL_PARAMETER = 0x87
    private val GATT_PENDING = 0x88
    private val GATT_AUTHORIZATION_FAILED = 0x89
    private val GATT_MORE = 0x8a
    private val GATT_INVALID_CONFIGURATION = 0x8b
    private val GATT_SERVICE_STARTED = 0x8c
    private val GATT_ENCRYPTION_NO_MITM = 0x8d
    private val GATT_NOT_ENCRYPTED = 0x8e
    private val GATT_CONNECTION_CONGESTED = BluetoothGatt.GATT_CONNECTION_CONGESTED
    private val GATT_DUPLICATE_REGISTRATION = 0x90
    private val GATT_ALREADY_OPEN = 0x91
    private val GATT_CANCEL = 0x92
    private val GATT_CONNECTION_TIMEOUT = 0x93
    private val GATT_CCC_CONFIGURATION_ERROR = 0xfd
    private val GATT_PROCEDURE_IN_PROGRESS = 0xfe
    private val GATT_VALUE_OUT_OF_RANGE = 0xff
    private val GATT_FAILURE = BluetoothGatt.GATT_FAILURE

    override fun toString(): String = when (status) {
        GATT_SUCCESS -> "Success"
        GATT_INVALID_HANDLE -> "Invalid attribute handle ($status)"
        GATT_READ_NOT_PERMITTED -> "Reading is not permitted ($status) "
        GATT_WRITE_NOT_PERMITTED -> "Writing is not permitted ($status)"
        GATT_INVALID_PDU -> "Invalid attribute PDU ($status)"
        GATT_INSUFFICIENT_AUTHENTICATION -> "Insufficient authentication ($status)"
        GATT_REQUEST_NOT_SUPPORTED -> "Request is not supported ($status)"
        GATT_INVALID_OFFSET -> "Invalid offset ($status)"
        GATT_INSUFFICIENT_AUTHORIZATION -> "Insufficient authorization ($status)"
        GATT_PREPARE_QUEUE_FULL -> "Prepare queue is full ($status)"
        GATT_ATTRIBUTE_NOT_FOUND -> "Attribute not found ($status)"
        GATT_ATTRIBUTE_NOT_LONG -> "The attribute cannot be read ($status)"
        GATT_INSUFFICIENT_KEY_SIZE -> "Insufficient encryption key size ($status)"
        GATT_INVALID_ATTRIBUTE_LENGTH -> "Invalid attribute length ($status)"
        GATT_ERROR_UNLIKELY -> "Unlikely error ($status)"
        GATT_INSUFFICIENT_ENCRYPTION -> "Insufficient encryption ($status)"
        GATT_UNSUPPORTED_GROUP_TYPE -> "Unsupported group type ($status)"
        GATT_INSUFFICIENT_RESOURCES -> "Insufficient resources ($status)"
        GATT_DATABASE_OUT_OF_SYNC -> "Database out of sync ($status)"
        GATT_VALUE_NOT_ALLOWED -> "The value is not allowed ($status)"
        GATT_TOO_SHORT -> "Too short ($status)"
        GATT_NO_RESOURCES -> "No resources ($status)"
        GATT_INTERNAL_ERROR -> "Internal error ($status)"
        GATT_WRONG_STATE -> "Wrong state ($status)"
        GATT_DATABASE_FULL -> "Database is full ($status)"
        GATT_BUSY -> "GATT is busy ($status)"
        GATT_ERROR -> "Undefined GATT error ($status)"
        GATT_CMD_STARTED -> "Operation has been queued ($status)"
        GATT_ILLEGAL_PARAMETER -> "Illegal parameter ($status)"
        GATT_PENDING -> "Operation is pending ($status)"
        GATT_AUTHORIZATION_FAILED -> "Authorization failed ($status)"
        GATT_MORE -> "More ($status)"
        GATT_INVALID_CONFIGURATION -> "Invalid configuration ($status)"
        GATT_SERVICE_STARTED -> "Service started ($status)"
        GATT_ENCRYPTION_NO_MITM -> "No Man-In-The-Middle encryption ($status)"
        GATT_NOT_ENCRYPTED -> "Not encrypted ($status)"
        GATT_CONNECTION_CONGESTED -> "Connection is congested ($status)"
        GATT_DUPLICATE_REGISTRATION -> "Duplicate registration ($status)"
        GATT_ALREADY_OPEN -> "Already open ($status)"
        GATT_CANCEL -> "Cancel ($status)"
        GATT_CONNECTION_TIMEOUT -> "Connection timeout ($status)"
        GATT_CCC_CONFIGURATION_ERROR -> "Client Characteristic Configuration Descriptor error ($status)"
        GATT_PROCEDURE_IN_PROGRESS -> "Procedure in progress ($status)"
        GATT_VALUE_OUT_OF_RANGE -> "Value out of range ($status)"
        GATT_FAILURE -> "Operation failed ($status)"
        else -> "Unknown status ($status)"
    }
}
