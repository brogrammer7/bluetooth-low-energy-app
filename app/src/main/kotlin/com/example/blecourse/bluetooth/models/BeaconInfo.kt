package com.example.blecourse.bluetooth.models

import java.util.UUID
import kotlin.math.pow

/**
 * Proximity zones for a beacon based on estimated distance
 */
enum class BeaconProximity {
    UNKNOWN,    // Distance cannot be determined
    IMMEDIATE,  // Within a few centimeters
    NEAR,       // Within a couple of meters
    FAR;        // Greater than a couple of meters away

    /**
     * Returns a human-readable name for the proximity zone
     */
    val displayName: String
        get() = when (this) {
            UNKNOWN -> "Unknown"
            IMMEDIATE -> "Immediate"
            NEAR -> "Near"
            FAR -> "Far"
        }

    companion object {

        /**
         * Determines the proximity zone based on the estimated distance
         */
        fun fromDistance(distance: Double): BeaconProximity {
            return when {
                distance < 0 -> UNKNOWN
                distance < 0.5 -> IMMEDIATE
                distance < 3.0 -> NEAR
                else -> FAR
            }
        }
    }
}

/**
 * Represents an iBeacon with all its identifying information and current state
 */
data class BeaconInfo(
    val uuid: UUID,
    val major: Int,
    val minor: Int,
    val measuredPower: Int,
    var rssi: Int = 0,
    var distance: Double = -1.0,
    var proximity: BeaconProximity = BeaconProximity.UNKNOWN,
    var lastSeen: Long = System.currentTimeMillis()
) {
    /**
     * Unique identifier for this beacon combining UUID, major and minor
     */
    val identifier: String
        get() = "$uuid:$major:$minor"

    /**
     * Updates the beacon's RSSI and recalculates distance and proximity
     */
    fun updateRssi(newRssi: Int) {
        rssi = newRssi
        distance = calculateDistance(rssi, measuredPower)
        proximity = BeaconProximity.fromDistance(distance)
        lastSeen = System.currentTimeMillis()
    }

    companion object {

        // Typical indoor default; tune with real measurements for better accuracy.
        private const val ENVIRONMENTAL_FACTOR = 2.0

        /**
         * Calculates the estimated distance from RSSI and measured power using the log-distance path loss model.
         * d = 10 ^ ((measuredPower - rssi) / (10 * n))
         */
        fun calculateDistance(rssi: Int, measuredPower: Int): Double {
            if (rssi == 0 || measuredPower == 0) {
                return -1.0 // Unknown distance / invalid calibration
            }

            val exp = (measuredPower - rssi) / (10.0 * ENVIRONMENTAL_FACTOR)
            return 10.0.pow(exp)
        }
    }
}
