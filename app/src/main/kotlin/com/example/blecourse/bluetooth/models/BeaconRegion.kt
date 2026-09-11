package com.example.blecourse.bluetooth.models

import java.util.UUID

/**
 * Defines a region for beacon monitoring. A region can be defined by:
 * - UUID only (matches all beacons with this UUID)
 * - UUID + major (matches all beacons with this UUID and major value)
 * - UUID + major + minor (matches a specific beacon)
 */
data class BeaconRegion(
    val identifier: String,
    val uuid: UUID,
    val major: Int? = null,
    val minor: Int? = null
) {
    /**
     * Checks if the given beacon matches this region
     */
    fun matches(beaconInfo: BeaconInfo): Boolean {
        if (beaconInfo.uuid != uuid) {
            return false
        }

        if (major != null && beaconInfo.major != major) {
            return false
        }

        if (minor != null && beaconInfo.minor != minor) {
            return false
        }

        return true
    }
}