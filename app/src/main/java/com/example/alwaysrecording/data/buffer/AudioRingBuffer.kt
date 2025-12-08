package com.example.alwaysrecording.data.buffer

import android.util.Log
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private const val TAG = "AudioRingBuffer"

/**
 * A circular buffer for storing audio data (PCM 16-bit).
 * This implementation uses a ReentrantLock to ensure thread safety between
 * the writer (AudioRecord thread) and the reader (Snapshot/Save thread).
 * 
 * Optimization: Snapshots copy data to a temporary buffer under lock, 
 * then perform disk I/O outside the lock to minimize blocking the audio thread.
 *
 * @param capacityBytes The total capacity of the buffer in bytes.
 * @param bytesPerFrame Number of bytes per audio frame (e.g., 2 for 16-bit mono).
 */
class AudioRingBuffer(
    private val capacityBytes: Int,
    private val bytesPerFrame: Int = 2 // Default for 16-bit mono
) {
    private val buffer: ByteArray = ByteArray(capacityBytes)
    private var writePosition: Int = 0 // Next position to write to
    private var readPosition: Int = 0  // Oldest data position (start of the current valid segment)
    private var currentSize: AtomicInteger = AtomicInteger(0) // Current number of bytes stored

    private val lock = ReentrantLock()

    init {
        if (capacityBytes % bytesPerFrame != 0) {
            Log.w(TAG, "Buffer capacity is not a multiple of bytesPerFrame. This might lead to partial frames.")
        }
    }

    /**
     * Writes audio data to the buffer. Overwrites the oldest data if the buffer is full.
     * This method is thread-safe.
     *
     * @param data The audio data to write.
     * @param offset The offset in the data array from which to start writing.
     * @param length The number of bytes to write.
     */
    fun write(data: ByteArray, offset: Int, length: Int) {
        lock.withLock {
            if (length == 0) return
            if (length > capacityBytes) {
                Log.w(TAG, "Write length ($length) exceeds buffer capacity ($capacityBytes). Writing last part.")
                // Write only the last part that fits, effectively what a continuous stream would do
                val newOffset = offset + length - capacityBytes
                _write(data, newOffset, capacityBytes)
                return
            }
            _write(data, offset, length)
        }
    }

    // Internal write logic, assumed to be called under lock
    private fun _write(data: ByteArray, offset: Int, length: Int) {
        if (writePosition + length <= capacityBytes) {
            // Fits in one segment
            System.arraycopy(data, offset, buffer, writePosition, length)
        } else {
            // Wraps around
            val firstChunkSize = capacityBytes - writePosition
            System.arraycopy(data, offset, buffer, writePosition, firstChunkSize)
            val secondChunkSize = length - firstChunkSize
            System.arraycopy(data, offset + firstChunkSize, buffer, 0, secondChunkSize)
        }

        val prevWritePos = writePosition
        writePosition = (writePosition + length) % capacityBytes

        val newSize = currentSize.get() + length
        if (newSize > capacityBytes) {
            currentSize.set(capacityBytes)
            // Adjust readPosition because we've overwritten old data
            readPosition = writePosition // The new start of data is where we just finished writing
        } else {
            currentSize.set(newSize)
            // If it's the first write and it didn't fill the buffer, readPosition remains 0
            if (currentSize.get() == length && length < capacityBytes) { // First write, less than full
                readPosition = prevWritePos 
            }
        }
    }


    /**
     * Creates a snapshot of the current buffer content and writes it to the given OutputStream.
     * This method copies the valid data to a temporary buffer while holding the lock,
     * then writes to the stream after releasing the lock to avoid blocking the writer thread.
     *
     * @param outputStream The stream to write the audio data to.
     * @return The number of bytes written.
     * @throws IOException If an I/O error occurs.
     */
    @Throws(IOException::class)
    fun snapshot(outputStream: OutputStream): Int {
        val dataToWrite: ByteArray
        val size: Int

        lock.withLock {
            size = currentSize.get()
            if (size == 0) {
                return 0
            }
            try {
                dataToWrite = ByteArray(size)
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "OOM during snapshot allocation", e)
                throw IOException("Not enough memory to create snapshot", e)
            }

            // Copy logic adapted to write to dataToWrite
            // If buffer hasn't wrapped around (writePosition is ahead of readPosition)
            // or if the buffer is exactly full and readPosition is 0 (first fill)
            if (readPosition < writePosition || (size == capacityBytes && readPosition == 0 && writePosition == 0) ) {
                 System.arraycopy(buffer, readPosition, dataToWrite, 0, size)
            } else {
                // Buffer has wrapped around
                val firstChunkSize = capacityBytes - readPosition
                System.arraycopy(buffer, readPosition, dataToWrite, 0, firstChunkSize)
                val secondChunkSize = writePosition
                System.arraycopy(buffer, 0, dataToWrite, firstChunkSize, secondChunkSize)
            }
        }

        // Perform blocking I/O outside the lock
        outputStream.write(dataToWrite)
        return size
    }

    /**
     * Clears the buffer.
     */
    fun clear() {
        lock.withLock {
            writePosition = 0
            readPosition = 0
            currentSize.set(0)
        }
    }

    /**
     * Gets the current number of valid bytes in the buffer.
     */
    fun getCurrentSize(): Int = currentSize.get()

    /**
     * Gets the total capacity of the buffer in bytes.
     */
    fun getCapacity(): Int = capacityBytes
}