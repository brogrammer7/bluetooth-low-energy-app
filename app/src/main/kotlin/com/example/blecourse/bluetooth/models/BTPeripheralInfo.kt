package com.example.blecourse.bluetooth.models

import androidx.lifecycle.ViewModel
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A simple data class that holds information about a BLE peripheral, such as its address, name, RSSI, and whether
 * it is connectable or observable.
 */
@SuppressLint("MissingPermission")
class BTPeripheralInfo(private val deviceAddress: String, private val deviceName: String) : ViewModel() {
    constructor(device: BluetoothDevice) : this(device.address, device.name ?: "Unknown")

    /**
     * The address of the peripheral, obtained from the BluetoothDevice. It is a unique identifier for the device.
     */
    val address: String get() = deviceAddress

    /**
     * The name of the peripheral, obtained from the BluetoothDevice, but it can be null, so we provide a default value of "Unknown".
     */
    val name: String get() = deviceName

    /**
     * Whether the peripheral is connectable (i.e., it has the connectable flag in the advertisement data).
     */
    var isConnectable: Boolean by mutableStateOf(false)

    /**
     * Whether the peripheral is observable (i.e., it has manufacturer specific data that we are interested in).
     */
    var isObservable: Boolean by mutableStateOf(false)

    /**
     * Signal strength of the BluetoothDevice. It is updated when the device is scanned.
     */
    var rssi: Int by mutableIntStateOf(0)
}