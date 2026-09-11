package com.example.blecourse.bluetooth.models

import androidx.lifecycle.ViewModel
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.collections.iterator

class BTCharacteristicData : ViewModel() {
    private val lock = ReentrantReadWriteLock()

    var serviceData = mutableStateMapOf<BluetoothGattService, SnapshotStateMap<BluetoothGattCharacteristic, SnapshotStateMap<String, String>>>()
        private set

    fun update(data: Map<String, String>, characteristic: BluetoothGattCharacteristic) {
        lock.writeLock().lock()

        try {
            val characteristicMap = serviceData.getOrPut(characteristic.service) {
                mutableStateMapOf()
            }

            val valuesMap = characteristicMap.getOrPut(characteristic) {
                mutableStateMapOf()
            }

            for ((key, value) in data) {
                valuesMap[key] = value
            }
        } finally {
            lock.writeLock().unlock()
        }
    }

    fun valuesForService(service: BluetoothGattService) : Map<BluetoothGattCharacteristic, Map<String, String>> {
        lock.readLock().lock()

        try {
            return this.serviceData[service]?.toMap() ?: emptyMap()
        } finally {
            lock.readLock().unlock()
        }
    }

    fun clear() {
        lock.writeLock().lock()

        try {
            serviceData.clear()
        } finally {
            lock.writeLock().unlock()
        }
    }
}