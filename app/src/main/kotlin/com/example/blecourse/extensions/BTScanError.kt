package com.example.blecourse.extensions

import android.bluetooth.le.ScanCallback.SCAN_FAILED_ALREADY_STARTED
import android.bluetooth.le.ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED
import android.bluetooth.le.ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED
import android.bluetooth.le.ScanCallback.SCAN_FAILED_INTERNAL_ERROR
import android.bluetooth.le.ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES
import android.bluetooth.le.ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY

/**
 * Provides a human-readable description of the error based on the error code returned during BLE scanning.
 */
class BTScanError(val error: Int) {
    override fun toString(): String = when (error) {
        SCAN_FAILED_ALREADY_STARTED -> "Scanning is already started"
        SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Application cannot be registered"
        SCAN_FAILED_INTERNAL_ERROR -> "Internal error"
        SCAN_FAILED_FEATURE_UNSUPPORTED -> "Feature is not supported"
        SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "No more hardware resources"
        SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> "Scanning too frequently"
        else -> "Unknown error: $error"
    }
}