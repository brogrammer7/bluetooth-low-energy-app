package com.example.blecourse.bluetooth

import java.time.ZonedDateTime

/**
 * Data encoder for encoding random number and time stamp data into ByteArray.
 */
object BTDataEncoder {
    /**
     * Encodes a number and a time stamp to a ByteArray (9 bytes)
     */
    fun encodeRandomNumber(number: Int) : ByteArray {
        val numberArray: ByteArray = encodeUShort(number.toUShort())
        val timestampArray = encodeTimestamp(ZonedDateTime.now())

        return numberArray + timestampArray
    }

    /*
     * Encodes an UByte to a ByteArray (1 byte)
     */
    fun encodeUByte(value: UByte) : ByteArray =
        byteArrayOf(value.toByte())

    /*
     * Encodes an UShort to a ByteArray (2 bytes)
     */
    fun encodeUShort(value: UShort) : ByteArray =
        byteArrayOf(value.toByte(), (value.toInt() shr 8).toByte())

    /*
     * Encodes an UInt to a ByteArray (4 bytes)
     */
    fun encodeUInt(value: UInt) : ByteArray =
        byteArrayOf(value.toByte(), (value shr 8).toByte(), (value shr 16).toByte(), (value shr 24).toByte())

    /*
     * Encodes a ZonedDateTime to a ByteArray (7 bytes)
     */
    private fun encodeTimestamp(timestamp: ZonedDateTime) : ByteArray {
        val year = timestamp.year
        return byteArrayOf(
            year.toByte(),
            (year shr 8).toByte(),
            timestamp.monthValue.toByte(),
            timestamp.dayOfMonth.toByte(),
            timestamp.hour.toByte(),
            timestamp.minute.toByte(),
            timestamp.second.toByte()
        )
    }
}
