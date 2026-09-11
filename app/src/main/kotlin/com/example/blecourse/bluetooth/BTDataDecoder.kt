package com.example.blecourse.bluetooth

import com.example.blecourse.bluetooth.profiles.BLEProfile
import java.nio.charset.StandardCharsets
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID
import kotlin.collections.isEmpty
import kotlin.math.roundToInt
import android.util.SparseArray
import androidx.core.util.isEmpty
import kotlin.collections.isEmpty
import kotlin.math.roundToInt

/**
 * Data decoder for decoding manufacturer specific data and characteristics values to human-readable formats.
 */
object BTDataDecoder {
    private val dateTimeFormatter: DateTimeFormatter? = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)

    /**
     * Check if the manufacturer data is supported.
     */
    fun isValidManufacturerData(data: SparseArray<ByteArray>?) : Boolean {
        val bytes = this.extractManufacturerSpecificData(data) ?: return false

        val companyId = decodeUShortAt(bytes, 0).toInt()

        // Check if this is a ThermoBeacon
        if (BLEProfile.THERMO_BEACON_COMPANY_IDS.contains(companyId)) {
            return bytes.size == BLEProfile.THERMO_BEACON_DATA_LENGTH
        }

        // Check if this is our Random Number Generator
        if (companyId == BLEProfile.RANDOM_NUMBER_COMPANY_ID) {
            return bytes.size == BLEProfile.RANDOM_NUMBER_DATA_LENGTH
        }

        return false
    }

    /**
     * Converts the bytes of the manufacturer specific data to a map with labels and values
     * In this example, we only support the ThermoBeacon and the Random Number Generator we'll build in this course.
     */
    fun decodeManufacturerData(data: SparseArray<ByteArray>?) : Map<String, String>? {
        val bytes = this.extractManufacturerSpecificData(data) ?: return null

        val companyId = decodeUShortAt(bytes, 0).toInt()

        if (BLEProfile.THERMO_BEACON_COMPANY_IDS.contains(companyId) && bytes.size == BLEProfile.THERMO_BEACON_DATA_LENGTH) {
            /*
             * ThermoBeacon (Different Brands)
             * See https://github.com/theengs/decoder/blob/development/src/devices/ThermoBeacon_json.h
             *
             * Total number of bytes: 20
             * - Company ID (Bytes 0..1)
             * - MAC address (Bytes 2..9)
             * - Voltage (Bytes 10..11)
             * - Temperature in Celsius (Bytes 12..13)
             * - Humidity in Percent (Bytes 14..15)
             * - Uptime in seconds (Bytes 16..19)
             */
            val voltage = decodeUShortAt(bytes, 10).toDouble()  / 1000.0
            val tempCelsius = decodeUShortAt(bytes, 12).toDouble() / 16.0
            val humidityPercent = decodeUShortAt(bytes, 14).toDouble() / 16.0

            return mapOf(
                "Voltage" to "%.1f V".format(voltage),
                "Temperature" to "%.1f °C".format(tempCelsius),
                "Humidity" to "%.1f%%".format(humidityPercent)
            )
        }

        if (companyId == BLEProfile.RANDOM_NUMBER_COMPANY_ID && bytes.size == BLEProfile.RANDOM_NUMBER_DATA_LENGTH) {
            /*
             * Custom data providing a random number and a timestamp
             * - Company ID (Bytes 0..1)
             * - Random number  (Bytes 2..3)
             * - TimeStamp (Bytes 4..11)
             */
            val randomNumber = decodeUShortAt(bytes, 2).toInt()
            val timestamp = decodeTimestampAt(bytes, 4)

            return mapOf(
                "Random Number" to "$randomNumber",
                "Timestamp" to (timestamp?.format(dateTimeFormatter) ?: "ERROR!")
            )
        }

        return null
    }

    /**
     * Converts the bytes of the characteristic value to a map with labels and values
     */
    fun decodeDataForCharacteristic(bytes: ByteArray, uuid: UUID): Map<String, String>? {
        if (bytes.isEmpty()) {
            return null
        }

        when (uuid) {
            BLEProfile.BATTERY_LEVEL_UUID -> {
                // Battery Level in percent (1 Byte)
                return mapOf("Battery Level" to "${bytes[0].toInt()}%")
            }

            BLEProfile.DEVICE_INFO_MODEL_NUMBER_UUID -> {
                // UTF-8 string representing of the device's model number
                return mapOf("Model Number" to String(bytes, StandardCharsets.UTF_8))
            }

            BLEProfile.DEVICE_INFO_SERIAL_NUMBER_UUID -> {
                // UTF-8 string representing of the device's serial number
                return mapOf("Serial Number" to String(bytes, StandardCharsets.UTF_8))
            }

            BLEProfile.DEVICE_INFO_FIRMWARE_REVISION_UUID -> {
                // UTF-8 string representing of the device's firmware revision
                return mapOf("Firmware Revision" to String(bytes, StandardCharsets.UTF_8))
            }

            BLEProfile.DEVICE_INFO_HARDWARE_REVISION_UUID -> {
                // UTF-8 string representing of the device's hardware revision
                return mapOf("Hardware Revision" to String(bytes, StandardCharsets.UTF_8))
            }

            BLEProfile.DEVICE_INFO_SOFTWARE_REVISION_UUID -> {
                // UTF-8 string representing of the device's software revision
                return mapOf("Software Revision" to String(bytes, StandardCharsets.UTF_8))
            }

            BLEProfile.DEVICE_INFO_MANUFACTURER_UUID -> {
                // UTF-8 string representing of the device's manufacturer name
                return mapOf("Manufacturer" to String(bytes, StandardCharsets.UTF_8))
            }

            BLEProfile.BODY_SENSOR_LOCATION_UUID -> {
                return decodeBodySensorLocation(bytes)
            }

            BLEProfile.HEART_RATE_MEASUREMENT_UUID -> {
                return decodeHeartRateMeasurement(bytes)
            }

            BLEProfile.RANDOM_NUMBER_CHARACTERISTIC_UUID -> {
                return decodeRandomNumber(bytes)
            }

            else -> {
                // For unknown characteristics, return the raw data as a hex string
                return mapOf("Raw Data" to bytes.joinToString(separator = " ") { String.format("%02x", it) })
            }
        }
    }

    /*
     * Decode Body Sensor Location (1 Byte)
     */
    private fun decodeBodySensorLocation(bytes: ByteArray): Map<String, String> {
        val location = bytes[0].toInt()
        if (location in 1..6) {
            return mapOf("Device Location" to listOf("Chest", "Wrist", "Finger", "Hand", "Earlobe", "Foot")[location])
        }

        return mapOf("Device Location" to "Unknown")
    }

    /*
     * Decode Heart Rate Measurement
     * - Flags (1 Byte)
     * - Heart Rate (1 or 2 Bytes)
     * - Energy Expended information (2 Bytes)
     * - R-R Intervals (Variable length)
     */
    private fun decodeHeartRateMeasurement(bytes: ByteArray): Map<String, String>? {
        if (bytes.size < 2) {
            return null
        }

        val resultMap = mutableMapOf<String, String>()

        var offset = 1
        val flags = bytes[0].toInt() // First byte represents flags

        if (flags and 0x01 == 0) {
            // Heart rate format is 1 Byte
            resultMap["Heart Rate"] = "${decodeUByteAt(bytes, 1).toInt()} bpm"
            offset++
        } else {
            // Heart rate format is 2 Bytes
            resultMap["Heart Rate"] = "${decodeUShortAt(bytes, 1).toInt()} bpm"
            offset += 2
        }

        if (flags and 0x04 != 0) {
            // Sensor Contact information available
            resultMap["Sensor Contact"] = if (flags and 0x02 != 0) "Detected" else "No Contact"
        }

        if (flags and 0x08 != 0) {
            // Energy Expended information available
            resultMap["Energy Expended"] = "${decodeUShortAt(bytes, offset).toInt()} Joules"
            offset += 2
        }

        if (flags and 0x10 != 0) {
            // RR-Interval values are present
            val rrCount = (bytes.size - offset) / 2

            if (rrCount > 0) {
                val rr = mutableListOf<Int>()

                (0 until rrCount).forEach { _ ->
                    val value = decodeUShortAt(bytes, offset).toInt().toDouble()
                    rr.add(((value / 1024.0) * 1000.0).roundToInt())
                    offset += 2
                }

                // Calculate average (RRNN)
                resultMap["RRNN"] = "${rr.average().roundToInt()} ms"
            }
        }

        return resultMap
    }

    /*
     * Decodes the random number and time stamp (Custom)
     * - Random number (Bytes 0..1)
     * - Time stamp (Bytes 2..9)
     */
    private fun decodeRandomNumber(bytes: ByteArray): Map<String, String>? {
        // Custom characteristic providing a random number and a timestamp
        if (bytes.size < 9) {
            return null
        }

        // Random number (Bytes 0..1)
        val randomNumber = decodeUShortAt(bytes, 0)

        // Timestamp (Bytes 2..8)
        val timestamp = decodeTimestampAt(bytes, 2)

        return mapOf(
            "Random Number" to "$randomNumber",
            "Timestamp" to (timestamp?.format(dateTimeFormatter) ?: "ERROR!")
        )
    }

    /*
     * Decodes the byte array to an UByte (1 byte) starting at the given offset
     */
    fun decodeUByteAt(bytes: ByteArray, offset: Int) : UByte =
        bytes[offset].toUByte()

    /*
     * Decodes the byte array to an UShort (2 bytes) starting at the given offset
     */
    fun decodeUShortAt(bytes: ByteArray, offset: Int) : UShort =
        (((bytes[offset + 1].toUInt() and 0xFFu) shl 8) or (bytes[offset].toUInt() and 0xFFu)).toUShort()

    /*
     * Decodes the byte array to an UInt (4 bytes) starting at the given offset
     */
    fun decodeUIntAt(bytes: ByteArray, offset: Int) =
        ((bytes[offset + 3].toUInt() and 0xFFu) shl 24) or ((bytes[offset + 2].toUInt() and 0xFFu) shl 16) or
                ((bytes[offset + 1].toUInt() and 0xFFu) shl 8) or (bytes[offset].toUInt() and 0xFFu)

    /*
     * Decodes the byte array to a ZonedDateTime (7 bytes) starting at the given offset
     */
    private fun decodeTimestampAt(bytes: ByteArray, offset: Int) : ZonedDateTime? {
        if (bytes.size < offset + 7) {
            return null
        }

        return ZonedDateTime.of(
            decodeUShortAt(bytes, offset).toInt(),
            decodeUByteAt(bytes, offset + 2).toInt(),
            decodeUByteAt(bytes, offset + 3).toInt(),
            decodeUByteAt(bytes, offset + 4).toInt(),
            decodeUByteAt(bytes, offset + 5).toInt(),
            decodeUByteAt(bytes, offset + 6).toInt(),
            0,
            ZoneId.systemDefault()
        )
    }

    /**
     * Extracts the relevant manufacturer data from the given sparse array and converts it to a
     * regular byte array.
     */
    private fun extractManufacturerSpecificData(data: SparseArray<ByteArray>?): ByteArray? {
        val data = data ?: return null

        if (data.isEmpty() || data.valueAt(0).isEmpty()) {
            return null
        }

        val manufacturerId = data.keyAt(0).toUShort()
        return byteArrayOf(manufacturerId.toByte(), (manufacturerId.toInt() shr 8).toByte()) + data.valueAt(0)
    }
}
