package com.example.blecourse.bluetooth.models

import java.util.HashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.collections.remove
import kotlin.collections.set

/**
 * A simple buffer for storing byte arrays associated with a reference of a generic type. This can be used to store data
 * that is being read from or written to a Bluetooth characteristic, allowing you to accumulate data across multiple
 * read/write operations if needed.
 */
class BTReferencedByteBuffer<T> {
    private val buffer = HashMap<T, ByteArray>()
    private val lock = ReentrantReadWriteLock()

    /**
     * Returns a list of references of the generic type T that are currently stored in the buffer.
     */
    val references: List<T>
        get() {
            lock.readLock().lock()

            try {
                return buffer.keys.toList()
            } finally {
                lock.readLock().unlock()
            }
        }

    /**
     * Returns the byte array data associated with the given reference, or null if no data is found.
     */
    fun dataForReference(reference: T): ByteArray? {
        lock.readLock().lock()

        try {
            return buffer[reference]
        } finally {
            lock.readLock().unlock()
        }
    }

    /**
     * Returns a list of chunks for the given reference and size, or null if no data is found.
     */
    fun chunkedDataForReference(reference: T, chunkSize: Int): List<ByteArray>? {
        return dataForReference(reference)?.let { data ->
            if((data.size > chunkSize))
                data.toList().chunked(chunkSize).map { it.toByteArray() }
            else
                listOf(data)
        }
    }

    /**
     * Adds the given byte array data to the buffer, associating it with the given reference. If there is already
     * data associated with the reference, the new data is appended to the existing data. Returns the size of the
     * data (in bytes) that was already present for the reference.
     */
    fun add(data: ByteArray, reference: T) : Int {
        lock.writeLock().lock()

        try {
            val prevData = buffer[reference] ?: ByteArray(0)
            buffer[reference] = prevData + data
            return prevData.size
        } finally {
            lock.writeLock().unlock()
        }
    }

    /**
     * Updates the buffer by first clearing any existing data for the given reference, and then adding the new
     * byte array data. Returns the size of the data (in bytes) that was added.
     * This operation is atomic and executed under a single write lock.
     */
    fun update(data: ByteArray, reference: T) : Int {
        lock.writeLock().lock()

        try {
            buffer.remove(reference)
            buffer[reference] = data
            return data.size
        } finally {
            lock.writeLock().unlock()
        }
    }

    /**
     * Clears the data associated with the given reference from the buffer. If no reference is provided, only
     * the data for the currently active reference is cleared.
     */
    fun clearReference(reference: T? = null) {
        lock.writeLock().lock()

        try {
            this.buffer.remove(reference)
        } finally {
            lock.writeLock().unlock()
        }
    }

    /**
     * Clears all data from the buffer, removing all references and associated byte array data.
     */
    fun clearAll() {
        lock.writeLock().lock()

        try {
            this.buffer.clear()
        } finally {
            lock.writeLock().unlock()
        }
    }
}