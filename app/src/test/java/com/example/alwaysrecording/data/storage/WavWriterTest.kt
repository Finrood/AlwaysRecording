package com.example.alwaysrecording.data.storage

import com.example.alwaysrecording.data.buffer.AudioRingBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
class WavWriterTest {

    @Rule
    @JvmField
    val folder = TemporaryFolder()

    private val sampleRate = 16000
    private val channels: Short = 1
    private val bitDepth: Short = 16
    private val bytesPerFrame = (bitDepth / 8) * channels

    @Test
    fun writeWavFile_createsValidWavFile() {
        val outputFile = folder.newFile("test_output.wav")
        val audioData = ByteArray(sampleRate * bytesPerFrame) // 1 second of audio
        for (i in audioData.indices) {
            audioData[i] = i.toByte() // Dummy audio data
        }
        val buffer = AudioRingBuffer(audioData.size, bytesPerFrame.toInt())
        buffer.write(audioData, 0, audioData.size)

        WavWriter.writeWavFile(outputFile, buffer, sampleRate, channels, bitDepth)

        assertTrue(outputFile.exists())
        assertTrue(outputFile.length() > 44) // WAV file must be larger than header
    }

    @Test
    fun writeWavFile_emptyBuffer_returnsFalseAndDeletesFile() {
        val outputFile = folder.newFile("empty_output.wav")
        val buffer = AudioRingBuffer(100, bytesPerFrame.toInt())

        val success = WavWriter.writeWavFile(outputFile, buffer, sampleRate, channels, bitDepth)

        assertFalse(success)
        assertFalse(outputFile.exists()) // File should be deleted
    }

    @Test
    fun writeWavFile_bufferWithWrapAround_createsValidWavFile() {
        val outputFile = folder.newFile("wrapped_output.wav")
        val bufferCapacity = sampleRate * bytesPerFrame // 1 second capacity
        val buffer = AudioRingBuffer(bufferCapacity, bytesPerFrame.toInt())

        // Write 1.5 seconds of audio to force wrap-around
        val audioData1 = ByteArray(sampleRate * bytesPerFrame) // 1 second
        val audioData2 = ByteArray(sampleRate / 2 * bytesPerFrame) // 0.5 second
        buffer.write(audioData1, 0, audioData1.size)
        buffer.write(audioData2, 0, audioData2.size)

        WavWriter.writeWavFile(outputFile, buffer, sampleRate, channels, bitDepth)

        assertTrue(outputFile.exists())
        assertTrue(outputFile.length() > 0)
    }

    @Test
    fun writeWavFile_headerIsCorrect() {
        val outputFile = folder.newFile("header_test.wav")
        val audioData = ByteArray(sampleRate * bytesPerFrame) // 1 second
        // Fill with known data
        for (i in audioData.indices) {
            audioData[i] = 0x55 // Arbitrary pattern
        }
        val buffer = AudioRingBuffer(audioData.size, bytesPerFrame.toInt())
        buffer.write(audioData, 0, audioData.size)

        WavWriter.writeWavFile(outputFile, buffer, sampleRate, channels, bitDepth)

        val bytes = outputFile.readBytes()
        
        // Check RIFF header
        assertEquals('R'.code.toByte(), bytes[0])
        assertEquals('I'.code.toByte(), bytes[1])
        assertEquals('F'.code.toByte(), bytes[2])
        assertEquals('F'.code.toByte(), bytes[3])

        // Check File Size (Total - 8)
        val fileSize = bytes.size - 8
        val fileSizeFromHeader = (bytes[4].toInt() and 0xFF) or
                ((bytes[5].toInt() and 0xFF) shl 8) or
                ((bytes[6].toInt() and 0xFF) shl 16) or
                ((bytes[7].toInt() and 0xFF) shl 24)
        assertEquals(fileSize, fileSizeFromHeader)

        // Check WAVE fmt
        assertEquals('W'.code.toByte(), bytes[8])
        assertEquals('A'.code.toByte(), bytes[9])
        assertEquals('V'.code.toByte(), bytes[10])
        assertEquals('E'.code.toByte(), bytes[11])
        assertEquals('f'.code.toByte(), bytes[12])
        assertEquals('m'.code.toByte(), bytes[13])
        assertEquals('t'.code.toByte(), bytes[14])
        assertEquals(' '.code.toByte(), bytes[15])

        // Check Audio Format (1 = PCM)
        assertEquals(1, bytes[20].toInt())
        assertEquals(0, bytes[21].toInt())

        // Check Channels (1)
        assertEquals(channels.toLong(), bytes[22].toLong())

        // Check Sample Rate (16000)
        val sampleRateFromHeader = (bytes[24].toInt() and 0xFF) or
                ((bytes[25].toInt() and 0xFF) shl 8) or
                ((bytes[26].toInt() and 0xFF) shl 16) or
                ((bytes[27].toInt() and 0xFF) shl 24)
        assertEquals(sampleRate, sampleRateFromHeader)

        // Check Data Header
        assertEquals('d'.code.toByte(), bytes[36])
        assertEquals('a'.code.toByte(), bytes[37])
        assertEquals('t'.code.toByte(), bytes[38])
        assertEquals('a'.code.toByte(), bytes[39])

        // Check Data Size
        val dataSizeFromHeader = (bytes[40].toInt() and 0xFF) or
                ((bytes[41].toInt() and 0xFF) shl 8) or
                ((bytes[42].toInt() and 0xFF) shl 16) or
                ((bytes[43].toInt() and 0xFF) shl 24)
        assertEquals(audioData.size, dataSizeFromHeader)
    }
}
