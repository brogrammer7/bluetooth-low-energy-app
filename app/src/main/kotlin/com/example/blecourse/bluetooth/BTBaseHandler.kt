package com.example.blecourse.bluetooth

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel

/**
 * Base handler for Bluetooth operations. It checks if the device supports Bluetooth LE and if Bluetooth is enabled and
 * initializes the BluetoothManager, which can be used by subclasses to perform Bluetooth operations.
 */
open class BTBaseHandler(context: Context) : ViewModel() {
    protected var bluetoothManager: BluetoothManager? = null

    init {
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            if (bluetoothManager.adapter?.isEnabled ?: false) {
                this.bluetoothManager = bluetoothManager
            }
        }
    }
}