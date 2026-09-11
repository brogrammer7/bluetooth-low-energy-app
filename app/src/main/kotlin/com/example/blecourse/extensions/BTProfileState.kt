package com.example.blecourse.extensions

import android.bluetooth.BluetoothProfile

/**
 * Provides a human-readable description of the Bluetooth profile connection state.
 */
class BTProfileState(val state: Int) {
    override fun toString(): String = when (state) {
        BluetoothProfile.STATE_DISCONNECTED -> "Disconnected"
        BluetoothProfile.STATE_CONNECTED -> "Connected"
        BluetoothProfile.STATE_CONNECTING -> "Connecting"
        BluetoothProfile.STATE_DISCONNECTING -> "Disconnecting"
        else -> "Unknown state ($state)"
    }
}
