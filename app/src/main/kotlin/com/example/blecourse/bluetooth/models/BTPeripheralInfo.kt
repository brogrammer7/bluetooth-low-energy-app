package com.example.blecourse.bluetooth.models

import androidx.lifecycle.ViewModel
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@SuppressLint("MissingPermission")
class BTPeripheralInfo(private val deviceAddress: String, private val deviceName: String) : ViewModel() {
    constructor(device: BluetoothDevice) : this(device.address, device.name ?: "Unknown")

    val address: String get() = deviceAddress
    val name: String get() = deviceName
    var isConnectable: Boolean by mutableStateOf(false)
    var rssi: Int by mutableIntStateOf(0)
}