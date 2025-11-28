package com.example.alwaysrecording.data.buffer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
class AudioRingBufferTest {

    private val bytesPerFrame = 2 // 16-bit PCM

    @Test
    fun constructor_initializesCorrectly() {
        val capacity = 100
        val buffer = AudioRingBuffer(capacity, bytesPerFrame)
        assertEquals(capacity, buffer.getCapacity())
        assertEquals(0, buffer.getCurrentSize())
    }

    @Test
    fun write_addsDataAndIncreasesSize() {
        val capacity = 10
        val buffer = AudioRingBuffer(capacity, bytesPerFrame)
        val data = byteArrayOf(1, 2, 3, 4)
        buffer.write(data, 0, data.size)
        assertEquals(data.size, buffer.getCurrentSize())
    }

    @Test
    fun write_overwritesOldestDataWhenFull() {
        val capacity = 10
        val buffer = AudioRingBuffer(capacity, bytesPerFrame)
        val data1 = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10) // Fills buffer
        buffer.write(data1, 0, data1.size)
        assertEquals(capacity, buffer.getCurrentSize())

        val data2 = byteArrayOf(11, 12)
        buffer.write(data2, 0, data2.size)
        assertEquals(capacity, buffer.getCurrentSize())

        val outputStream = ByteArrayOutputStream()
        buffer.snapshot(outputStream)
        val expected = byteArrayOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 12) // 1,2 overwritten by 11,12
        assertArrayEquals(expected, outputStream.toByteArray())
    }

    @Test
    fun write_handlesWrapAroundCorrectly() {
        val capacity = 10
        val buffer = AudioRingBuffer(capacity, bytesPerFrame)
        val data1 = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8) // Fill partially
        buffer.write(data1, 0, data1.size)

        val data2 = byteArrayOf(9, 10, 11, 12) // Wraps around
        buffer.write(data2, 0, data2.size)
        assertEquals(capacity, buffer.getCurrentSize())

        val outputStream = ByteArrayOutputStream()
        buffer.snapshot(outputStream)
        val expected = byteArrayOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 12) // 1,2 overwritten by 11,12
        assertArrayEquals(expected, outputStream.toByteArray())
    }

    @Test
    fun snapshot_returnsZeroForEmptyBuffer() {
        val capacity = 10
        val buffer = AudioRingBuffer(capacity, bytesPerFrame)
        val outputStream = ByteArrayOutputStream()
        val bytesWritten = buffer.snapshot(outputStream)
        assertEquals(0, bytesWritten)
        assertTrue(outputStream.toByteArray().isEmpty())
    }

    @Test
    fun snapshot_writesAllDataWhenNotWrapped() {
        val capacity = 10
        val buffer = AudioRingBuffer(capacity, bytesPerFrame)
        val data = byteArrayOf(1, 2, 3, 4, 5, 6)
        buffer.write(data, 0, data.size)

        val outputStream = ByteArrayOutputStream()
        val bytesWritten = buffer.snapshot(outputStream)
        assertEquals(data.size, bytesWritten)
        assertArrayEquals(data, outputStream.toByteArray())
    }

    @Test
    fun snapshot_writesCorrectDataWhenWrapped() {
        val capacity = 10
        val buffer = AudioRingBuffer(capacity, bytesPerFrame)
        val data1 = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8) // Fill partially
        buffer.write(data1, 0, data1.size)

        val data2 = byteArrayOf(9, 10, 11, 12) // Wraps around, overwrites 1,2
        buffer.write(data2, 0, data2.size)

        val outputStream = ByteArrayOutputStream()
        val bytesWritten = buffer.snapshot(outputStream)
        assertEquals(capacity, bytesWritten)
        val expected = byteArrayOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
        assertArrayEquals(expected, outputStream.toByteArray())
    }

    @Test
    fun clear_resetsBufferState() {
        val capacity = 10
        val buffer = AudioRingBuffer(capacity, bytesPerFrame)
        val data = byteArrayOf(1, 2, 3, 4, 5, 6)
        buffer.write(data, 0, data.size)
        assertEquals(data.size, buffer.getCurrentSize())

        buffer.clear()
        assertEquals(0, buffer.getCurrentSize())
        val outputStream = ByteArrayOutputStream()
        buffer.snapshot(outputStream)
        assertTrue(outputStream.toByteArray().isEmpty())
    }

    @Test
    fun write_lengthGreaterThanCapacity_writesLastPart() {
        val capacity = 10
        val buffer = AudioRingBuffer(capacity, bytesPerFrame)
        val data = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15) // Length > capacity
        buffer.write(data, 0, data.size)
        assertEquals(capacity, buffer.getCurrentSize())

        val outputStream = ByteArrayOutputStream()
        buffer.snapshot(outputStream)
        val expected = byteArrayOf(6, 7, 8, 9, 10, 11, 12, 13, 14, 15) // Last 10 bytes
        assertArrayEquals(expected, outputStream.toByteArray())
    }

    @Test
    fun snapshot_fullBuffer_readPositionZero_writesEntireBuffer() {
        val capacity = 10
        val buffer = AudioRingBuffer(capacity, bytesPerFrame)
        val data = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        buffer.write(data, 0, data.size)

        val outputStream = ByteArrayOutputStream()
        buffer.snapshot(outputStream)
        assertArrayEquals(data, outputStream.toByteArray())
    }
}
