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
 * This implementation aims for careful concurrent access but uses a lock for snapshotting
 * to ensure data consistency during reads, which might be frequent when saving.
 * For extreme low-latency scenarios, a fully lock-free version would be more complex.
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

    private val lock = ReentrantLock() // To protect snapshot operation from concurrent writes

    init {
        if (capacityBytes % bytesPerFrame != 0) {
            Log.w(TAG, "Buffer capacity is not a multiple of bytesPerFrame. This might lead to partial frames.")
        }
    }

    /**
     * Writes audio data to the buffer. Overwrites the oldest data if the buffer is full.
     * This method should ideally be called from a single audio processing thread.
     *
     * @param data The audio data to write.
     * @param offset The offset in the data array from which to start writing.
     * @param length The number of bytes to write.
     */
    fun write(data: ByteArray, offset: Int, length: Int) {
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

    private fun _write(data: ByteArray, offset: Int, length: Int) {
        // No lock needed here if we assume a single writer thread.
        // If multiple writers, a lock or atomic operations would be needed for writePosition and currentSize.

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
            // If buffer was not full and now we're writing, readPosition doesn't change
            // unless currentSize was 0, in which case readPosition should be the old writePosition
            if (currentSize.get() == length && length < capacityBytes) { // First write, less than full
                readPosition = prevWritePos // Or simply 0 if always starting from 0
            }
        }
        // Log.d(TAG, "Write: len=$length, writePos=$writePosition, readPos=$readPosition, size=${currentSize.get()}")
    }


    /**
     * Creates a snapshot of the current buffer content and writes it to the given OutputStream.
     * This method is designed to be efficient by writing directly to the stream
     * and avoiding large intermediate byte array allocations on the heap.
     * This method is thread-safe using a ReentrantLock.
     *
     * @param outputStream The stream to write the audio data to.
     * @return The number of bytes written.
     * @throws IOException If an I/O error occurs.
     */
    @Throws(IOException::class)
    fun snapshot(outputStream: OutputStream): Int {
        lock.withLock {
            val size = currentSize.get()
            if (size == 0) {
                return 0
            }

            // If buffer hasn't wrapped around (writePosition is ahead of readPosition)
            // or if the buffer is exactly full and readPosition is 0 (first fill)
            if (readPosition < writePosition || (size == capacityBytes && readPosition == 0 && writePosition == 0) ) {
                // The case (size == capacityBytes && readPosition == 0 && writePosition == 0) means it's full and just wrapped
                // to the beginning. So, the entire buffer from 0 to capacityBytes is valid.
                if (size == capacityBytes && readPosition == 0 && writePosition == 0) {
                    outputStream.write(buffer, 0, capacityBytes)
                } else {
                    outputStream.write(buffer, readPosition, size)
                }
            } else {
                // Buffer has wrapped around (data is in two segments)
                // Segment 1: from readPosition to end of buffer
                val firstChunkSize = capacityBytes - readPosition
                outputStream.write(buffer, readPosition, firstChunkSize)
                // Segment 2: from start of buffer to writePosition
                val secondChunkSize = writePosition
                outputStream.write(buffer, 0, secondChunkSize)
            }
            // Log.d(TAG, "Snapshot: readPos=$readPosition, writePos=$writePosition, size=$size")
            return size
        }
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