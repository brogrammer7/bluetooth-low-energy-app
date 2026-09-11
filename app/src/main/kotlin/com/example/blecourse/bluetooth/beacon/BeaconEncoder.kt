package com.example.blecourse.bluetooth.beacon

import com.example.blecourse.bluetooth.profiles.BeaconIdentity
import com.example.blecourse.bluetooth.profiles.BeaconProfile
import java.nio.ByteBuffer

/**
 * BeaconEncoder is responsible for encoding beacon identity information into the manufacturer data format
 * required for iBeacon advertising. It constructs the byte array according to the iBeacon specification.
 */
object BeaconEncoder {
    /**
     * Encodes a BeaconIdentity into a byte array suitable for iBeacon advertising.
     */
    fun encodeManufacturerData(identity: BeaconIdentity, measuredPower: Int): ByteArray {
        val prefix = byteArrayOf(BeaconProfile.IBEACON_TYPE.toByte(), BeaconProfile.IBEACON_LENGTH.toByte())

        val uuidByteBuffer = ByteBuffer.wrap(ByteArray(16))
        uuidByteBuffer.putLong(identity.uuid.mostSignificantBits)
        uuidByteBuffer.putLong(identity.uuid.leastSignificantBits)

        val suffix = byteArrayOf(
            (identity.major shr 8).toByte(),
            (identity.major and 0xff).toByte(),
            (identity.minor shr 8).toByte(),
            (identity.minor and 0xff).toByte(),
            measuredPower.toByte()
        )

        return prefix + uuidByteBuffer.array() + suffix
    }
}