package com.example.blecourse.bluetooth.profiles

import java.util.UUID

/**
 * Declares the identifying values programmed into an iBeacon.
 */
data class BeaconIdentity(
    /**
     * Proximity UUID shared by one beacon deployment or beacon family.
     */
    val uuid: UUID,

    /**
     * Human-readable region identifier used when registering beacon monitoring.
     */
    val identifier: String,

    /**
     * Major grouping value carried by the beacon advertisement.
     */
    val major: Int,

    /**
     * Minor value used to distinguish individual beacons within a major group.
     */
    val minor: Int
)

/**
 * Centralized demo beacon constants shared by the beacon advertiser and monitor.
 */
object BeaconProfile {
    /**
     * iBeacon advertisement type and data length
     */
    const val IBEACON_TYPE = 0x02
    const val IBEACON_LENGTH = 0x15

    /**
     * Expected total length of iBeacon manufacturer data
     */
    const val IBEACON_DATA_LENGTH = 23

    /**
     * Proximity UUID and company ID of the CP27 Beacon
     */
    val CP27_BEACON_UUID = UUID.fromString("E2C56DB5-DFFB-48D2-B060-D0F5A71096E0")!!
    const val CP27_BEACON_COMPANY_ID = 0x4458

    /**
     * Demo proximity UUID and company ID used by the sample iBeacon flow.
     */
    val DEMO_BEACON_UUID = UUID.fromString("39ED98FF-2900-441A-802F-9C398FC199D2")!!
    const val DEMO_BEACON_COMPANY_ID = 0x004C // Apple's iBeacon manufacturer ID

    /**
     * Region identifier used when registering the demo beacon region.
     */
    const val DEMO_BEACON_IDENTIFIER = "com.blecourse.demoBeacon"

    /**
     * Demo major and minor values used for the sample beacon identity.
     */
    const val DEMO_BEACON_MAJOR = 100
    const val DEMO_BEACON_MINOR = 1

    /**
     * Complete demo beacon identity assembled from the configured UUID, identifier, major, and minor values.
     */
    val demoBeaconIdentity = BeaconIdentity(
        uuid = DEMO_BEACON_UUID,
        identifier = DEMO_BEACON_IDENTIFIER,
        major = DEMO_BEACON_MAJOR,
        minor = DEMO_BEACON_MINOR
    )
}