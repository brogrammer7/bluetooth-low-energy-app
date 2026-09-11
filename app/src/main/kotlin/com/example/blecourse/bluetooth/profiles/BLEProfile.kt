package com.example.blecourse.bluetooth.profiles

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import java.util.UUID

/**
 * Bluetooth GATT service and characteristic configuration
 */
object BLEProfile {
    /*
     * Battery service and Battery Level characteristic UUIDs
     */
    val BATTERY_SERVICE_UUID                    = "180f".toUUID
    val BATTERY_LEVEL_UUID                      = "2a19".toUUID

    /*
     * Device Information service UUID and related characteristic UUIDs for Model Number, Serial Number,
     * Firmware Version, Hardware Version, Software Version and Manufacturer
     */
    val DEVICE_INFO_SERVICE_UUID              = "180a".toUUID
    val DEVICE_INFO_MODEL_NUMBER_UUID         = "2a24".toUUID
    val DEVICE_INFO_SERIAL_NUMBER_UUID        = "2a25".toUUID
    val DEVICE_INFO_FIRMWARE_REVISION_UUID    = "2a26".toUUID
    val DEVICE_INFO_HARDWARE_REVISION_UUID    = "2a27".toUUID
    val DEVICE_INFO_SOFTWARE_REVISION_UUID    = "2a28".toUUID
    val DEVICE_INFO_MANUFACTURER_UUID         = "2a29".toUUID

    /*
     * Heart Rate service and related characteristic UUIDs for Heart Rate measurement and Body Sensor Location
     */
    val HEART_RATE_SERVICE_UUID               = "180d".toUUID
    val HEART_RATE_MEASUREMENT_UUID           = "2a37".toUUID
    val BODY_SENSOR_LOCATION_UUID             = "2a38".toUUID

    /*
     * Custom service and characteristic UUIDs for the Random Number Generator example
     */
    val RANDOM_NUMBER_SERVICE_UUID            = "a1de94cb-4b7b-4a75-bfcd-321bc9a2b24b".toUUID
    val RANDOM_NUMBER_CHARACTERISTIC_UUID     = "a1de94cb-4b7b-4a75-bfcd-321bc9a2b24c".toUUID
    const val RANDOM_NUMBER_USER_DESCRIPTION  = "Random Number Generator"

    /*
     * Standard descriptor UUIDs for Client Characteristic Configuration (CCCD) and Characteristic User Description (CUD)
     */
    val CCC_DESCRIPTOR_UUID                   = "2902".toUUID
    val CUD_DESCRIPTOR_UUID                   = "2901".toUUID

    /*
     * GATT service/characteristic mapping for validation purposes
     */
    val serviceConfigurationsByUuid = mapOf(
        BATTERY_SERVICE_UUID to listOf(
            BATTERY_LEVEL_UUID
        ),
        DEVICE_INFO_SERVICE_UUID to listOf(
            DEVICE_INFO_MODEL_NUMBER_UUID,
            DEVICE_INFO_SERIAL_NUMBER_UUID,
            DEVICE_INFO_FIRMWARE_REVISION_UUID,
            DEVICE_INFO_HARDWARE_REVISION_UUID,
            DEVICE_INFO_SOFTWARE_REVISION_UUID,
            DEVICE_INFO_MANUFACTURER_UUID
        ),
        HEART_RATE_SERVICE_UUID to listOf(
            HEART_RATE_MEASUREMENT_UUID,
            BODY_SENSOR_LOCATION_UUID
        ),
        RANDOM_NUMBER_SERVICE_UUID to listOf(
            RANDOM_NUMBER_CHARACTERISTIC_UUID
        )
    )

    /*
     * Returns a human-readable name for a standard service used in this app
     */
    fun standardServiceDisplayNameFor(uuid: UUID) : String? = when (uuid) {
        BATTERY_SERVICE_UUID -> "Battery Service"
        DEVICE_INFO_SERVICE_UUID -> "Device Information Service"
        HEART_RATE_SERVICE_UUID -> "Heart Rate Service"
        else -> null
    }

    /*
     * Returns a human-readable name for a standard characteristic used in this app
     */
    fun standardCharacteristicDisplayNameFor(uuid: UUID) : String? = when (uuid) {
        BATTERY_SERVICE_UUID -> "Battery Service"
        BATTERY_LEVEL_UUID -> "Battery Level"
        DEVICE_INFO_MODEL_NUMBER_UUID -> "Model Number String"
        DEVICE_INFO_SERIAL_NUMBER_UUID -> "Serial Number String"
        DEVICE_INFO_FIRMWARE_REVISION_UUID -> "Firmware Revision String"
        DEVICE_INFO_HARDWARE_REVISION_UUID -> "Hardware Revision String"
        DEVICE_INFO_SOFTWARE_REVISION_UUID -> "Software Revision String"
        DEVICE_INFO_MANUFACTURER_UUID -> "Manufacturer Name String"
        HEART_RATE_MEASUREMENT_UUID -> "Heart Rate Measurement"
        BODY_SENSOR_LOCATION_UUID -> "Body Sensor Location"
        else -> null
    }

    /*
     * BluetoothGattService definition which is offered by the random number generator peripheral.
     */
    val randomNumberService: BluetoothGattService get() {
        // Add a Characteristic User Description (CUD) to provide a human-readable description of the characteristic
        val cudDescriptor = BluetoothGattDescriptor(
            CUD_DESCRIPTOR_UUID,
            BluetoothGattDescriptor.PERMISSION_READ
        )

        // Add a Client Characteristic Configuration Descriptor (CCCD) - REQUIRED for characteristics with NOTIFY property
        val cccDescriptor = BluetoothGattDescriptor(
            CCC_DESCRIPTOR_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )

        /*
         * This characteristic exposes the current random number as a read-only value. Centrals can fetch the
         * latest value on demand with PROPERTY_READ and subscribe with PROPERTY_NOTIFY to receive future updates.
         */
        val characteristic = BluetoothGattCharacteristic(
            RANDOM_NUMBER_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        characteristic.addDescriptor(cudDescriptor)
        characteristic.addDescriptor(cccDescriptor)

        // Create the service with the characteristic
        val service = BluetoothGattService(RANDOM_NUMBER_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(characteristic)

        return service
    }
}

/**
 * String extension to generate UUID from 16 and 128 bit UUIDs
 */
val String.toUUID : UUID get() {
    val uuidString = if (this.length == 4) "0000$this-0000-1000-8000-00805F9B34FB" else this
    return UUID.fromString(uuidString)
}
