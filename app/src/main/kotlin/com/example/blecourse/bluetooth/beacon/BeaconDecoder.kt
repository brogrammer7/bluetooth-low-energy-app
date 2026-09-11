package com.example.blecourse.bluetooth.beacon

import android.util.SparseArray
import com.example.blecourse.bluetooth.models.BeaconInfo
import com.example.blecourse.bluetooth.profiles.BeaconProfile
import java.nio.ByteBuffer
import java.util.UUID

/**
 * BeaconDecoder is responsible for decoding manufacturer data from BLE scan results into BeaconInfo objects.
 * It currently supports decoding iBeacon formatted data.
 */
object BeaconDecoder {
    /**
     * Decodes manufacturer data from a BLE scan result into a BeaconInfo object.
     */
    fun decodeManufacturerData(data: SparseArray<ByteArray>?): BeaconInfo? {
        val data = data ?: return null

        val manufacturerData = data.valueAt(0)
        val type = manufacturerData[0].toInt() and 0xff
        val length = manufacturerData[1].toInt() and 0xff

        if (manufacturerData.size >= BeaconProfile.IBEACON_DATA_LENGTH && type == BeaconProfile.IBEACON_TYPE && length == BeaconProfile.IBEACON_LENGTH) {
            /*
             * iBeacon format:
             * Byte 0-1: Type and Length
             * Byte 2-17: UUID (16 bytes)
             * Byte 18-19: Major (2 bytes)
             * Byte 20-21: Minor (2 bytes)
             * Byte 22: Measured Power (1 byte)
             */
            val uuidBuffer = ByteBuffer.wrap(manufacturerData.copyOfRange(2, 18))
            val uuid = UUID(uuidBuffer.long, uuidBuffer.long)
            val major = ((manufacturerData[18].toInt() and 0xff) shl 8) or (manufacturerData[19].toInt() and 0xff)
            val minor = ((manufacturerData[20].toInt() and 0xff) shl 8) or (manufacturerData[21].toInt() and 0xff)
            val measuredPower = manufacturerData[22].toInt()

            return BeaconInfo(
                uuid = uuid,
                major = major,
                minor = minor,
                measuredPower = measuredPower
            )
        }

        return null
    }
}