package com.example.blecourse.extensions

import android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED
import android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE
import android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED
import android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR
import android.bluetooth.le.AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS

/**
 * Provides a human-readable description of the error based on the error code returned by the BLE advertising API.
 */
class BTAdvertiseError(val error: Int) {
    override fun toString(): String = when (error) {
        ADVERTISE_FAILED_DATA_TOO_LARGE -> "Advertise data is too large"
        ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "No advertising instance available"
        ADVERTISE_FAILED_ALREADY_STARTED -> "Advertising is already started"
        ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal error"
        ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Feature is not supported"
        else -> "Unknown error ($error)"
    }
}