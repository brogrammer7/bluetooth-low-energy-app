package com.example.blecourse.bluetooth.models

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Implements a command queue for Bluetooth operations to ensure they are executed sequentially to avoid conflicts.
 * Bluetooth commands are enqueued as Runnable objects and will be executed one at a time. The next command in the queue
 * is automatically executed as soon as a command is completed.
 *
 * This class uses a Handler to post commands to the main thread, which is necessary for most Bluetooth operations that
 * interact with the Android Bluetooth API.
 */
class BTCommandQueue {
    private val handler = Handler(Looper.getMainLooper())
    private val queue = ConcurrentLinkedQueue<Runnable>()
    private var current: Runnable? = null

    /**
     * Enqueues a Bluetooth command to be executed.
     */
    fun enqueue(command: Runnable) {
        this.queue.offer(command)

        if (this.current == null) {
            this.execute()
        }
    }

    /**
     * Marks the current command as completed and triggers the execution of the next command in the queue, if any.
     */
    fun complete() {
        this.current = null

        if (this.queue.isNotEmpty()) {
            this.execute()
        }
    }

    /**
     * Executes the next command in the queue or wait for the current command to complete before executing the next one.
     */
    fun execute() {
        if (this.current != null) {
            return
        }

        val next = this.queue.poll() ?: return

        this.execute(next)
    }

    private fun execute(command: Runnable) {
        this.current = command

        try {
            this.handler.post(command)
        } catch (ex: Exception) {
            Log.e(BTCommandQueue::class.simpleName, ex.message, ex)
            this.complete()
        }
    }

    /**
     * Clears the command queue and resets the current command.
     */
    fun clear() {
        this.queue.clear()
        this.current = null
    }
}