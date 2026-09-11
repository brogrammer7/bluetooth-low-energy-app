package com.example.blecourse.extensions

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import com.example.blecourse.bluetooth.profiles.BLEProfile
import com.example.blecourse.bluetooth.profiles.BLEProfile.CCC_DESCRIPTOR_UUID
import com.example.blecourse.bluetooth.profiles.BLEProfile.CUD_DESCRIPTOR_UUID
import java.util.UUID

/*
 * A mutable map to store human-readable descriptions for BluetoothGattCharacteristics that have been received through
 * the Characteristic User Description (CUD) descriptor for instance.
 */
internal val characteristicUserDescriptions = mutableMapOf<BluetoothGattCharacteristic, String>()

/**
 * Provides a human-readable description of a BluetoothGattService based on its UUID.
 */
val BluetoothGattService.displayName: String
    get() = BLEProfile.standardServiceDisplayNameFor(this.uuid) ?: "Unknown service"

/**
 * Returns a BluetoothGattCharacteristic within a BluetoothGattService by its UUID, or null if not found.
 */
fun BluetoothGattService.findCharacteristicByUuid(uuid: UUID): BluetoothGattCharacteristic? =
    this.characteristics.firstOrNull { it.uuid == uuid }

/**
 * Returns a BluetoothGattCharacteristic across a list of BluetoothGattServices by its UUID, or null if not found.
 */
fun List<BluetoothGattService>.findCharacteristicByUuid(uuid: UUID): BluetoothGattCharacteristic? =
    this.firstNotNullOfOrNull { it.findCharacteristicByUuid(uuid) }

/**
 * Provides a human-readable description of a BluetoothGattCharacteristic based on its UUID.
 */
var BluetoothGattCharacteristic.displayName: String
    get() = characteristicUserDescriptions[this] ?: BLEProfile.standardCharacteristicDisplayNameFor(this.uuid) ?: "Unknown characteristic"
    set(value) {
        characteristicUserDescriptions[this] = value
    }

/**
 * Returns true if the characteristic supports reading its value.
 */
val BluetoothGattCharacteristic.hasReadProperty: Boolean
    get() = this.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0

/**
 * Returns true if the characteristic supports writing its value.
 */
val BluetoothGattCharacteristic.hasWriteProperty: Boolean
    get() = this.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0

/**
 * Returns true if the characteristic supports writing its value without response.
 */
val BluetoothGattCharacteristic.hasWriteWithoutResponseProperty: Boolean
    get() = this.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0

/**
 * Returns true if the characteristic supports indications.
 */
val BluetoothGattCharacteristic.hasIndicateProperty: Boolean
    get() = this.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0

/**
 * Returns true if the characteristic supports notifications.
 */
val BluetoothGattCharacteristic.hasNotifyProperty: Boolean
    get() = this.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0

/**
 * Returns the characteristic property descriptions as a comma-separated string.
 */
val BluetoothGattCharacteristic.propertyNames : String
    get() {
        val desc = mutableListOf<String>()

        if (this.properties and BluetoothGattCharacteristic.PROPERTY_BROADCAST != 0) {
            desc.add("BROADCAST")
        }

        if (this.properties and BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS != 0) {
            desc.add("EXTENDED")
        }

        if (this.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
            desc.add("READ")
        }

        if (this.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
            desc.add("WRITE")
        }

        if (this.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
            desc.add("WRITE_NO_RESPONSE")
        }

        if (this.properties and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE != 0) {
            desc.add("SIGNED_WRITE")
        }

        if (this.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
            desc.add("NOTIFY")
        }

        if (this.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
            desc.add("INDICATE")
        }

        return desc.joinToString(",")
    }

/**
 * Returns the Client Characteristic Configuration (CCC) descriptor of the BluetoothGattCharacteristic, if it exists.
 * The CCC descriptor is used to enable or disable notifications and indications for the characteristic.
 */
val BluetoothGattCharacteristic.cccDescriptor: BluetoothGattDescriptor?
    get() = this.getDescriptor(CCC_DESCRIPTOR_UUID)

/**
 * Returns the appropriate value to write to the CCC descriptor based on the properties of the BluetoothGattCharacteristic.
 */
val BluetoothGattCharacteristic.cccValue: ByteArray
    get() = when {
        this.hasNotifyProperty -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        this.hasIndicateProperty -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        else -> BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
    }

/**
 * Writes the appropriate value to the CCC descriptor of the BluetoothGattCharacteristic to enable or disable
 * notifications/indications.
 *
 * Returns BluetoothGatt.GATT_SUCCESS if the write operation was initiated successfully,
 * or BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED if the characteristic does not support notifications/indications or
 * if the CCC descriptor is not found.
 */
@SuppressLint("MissingPermission")
fun BluetoothGattCharacteristic.writeCCCDescriptor(gatt: BluetoothGatt, enable: Boolean) : Int {
    if (enable && !this.hasNotifyProperty && !this.hasIndicateProperty) {
        return BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
    }

    val cccValue = if (enable) this.cccValue else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE

    return this.cccDescriptor?.let { descriptor ->
        gatt.writeDescriptor(descriptor, cccValue)
    } ?: BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
}

/**
 * Provides a human-readable description of a BluetoothGattDescriptor based on its UUID.
 */
val BluetoothGattDescriptor.displayName: String
    get() = when (this.uuid) {
        CCC_DESCRIPTOR_UUID -> "Client Characteristic Configuration"
        CUD_DESCRIPTOR_UUID -> "Characteristic User Description"
        else -> "Unknown descriptor"
    }

/**
 * Checks if the descriptor is a Client Characteristic Configuration Descriptor (CCCD)
 */
val BluetoothGattDescriptor.isCCCDescriptor: Boolean
    get() = this.uuid.equals(CCC_DESCRIPTOR_UUID)

/**
 * Checks if the descriptor is a Characteristic User Description (CUD)
 */
val BluetoothGattDescriptor.isCUDDescriptor: Boolean
    get() = this.uuid.equals(CUD_DESCRIPTOR_UUID)

/**
 * Validates the value being written to a CCCD by checking the correct length (2 bytes) and if it corresponds to
 * enabling or disabling notifications/indications.
 */
fun BluetoothGattDescriptor.validateCCCDescriptorValue(value: ByteArray?): Int {
    if (value?.size != 2) {
        return BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH
    }

    if (value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) {
        return if (characteristic.hasIndicateProperty) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
    }

    if (value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
        return if (characteristic.hasNotifyProperty) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
    }

    if (!value.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
        return BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
    }

    return BluetoothGatt.GATT_SUCCESS
}