package com.example.alwaysrecording.data.storage

import android.util.Log
import com.example.alwaysrecording.data.buffer.AudioRingBuffer
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "WavWriter"

/**
 * Utility class to write PCM audio data to a .wav file.
 */
object WavWriter {

    private const val RECORDER_BPP: Short = 16 // Bits per sample
    private const val FILE_SIZE_UNKNOWN: Int = 0 // Placeholder for actual size

    /**
     * Writes the given PCM audio data from an AudioRingBuffer snapshot to a .wav file.
     *
     * @param outputFile The file to write to.
     * @param audioRingBuffer The buffer containing the audio data.
     * @param sampleRate The sample rate of the audio (e.g., 16000).
     * @param channels The number of channels (e.g., 1 for mono, 2 for stereo).
     * @param bitDepth The bit depth of the audio (e.g., 16 for 16-bit PCM).
     * @return True if writing was successful, false otherwise.
     */
    fun writeWavFile(
        outputFile: File,
        audioRingBuffer: AudioRingBuffer,
        sampleRate: Int,
        channels: Short,
        bitDepth: Short = RECORDER_BPP
    ): Boolean {
        val byteRate = sampleRate * channels * bitDepth / 8
        val blockAlign = (channels * bitDepth / 8).toShort()

        try {
            FileOutputStream(outputFile).use { fileOutputStream ->
                // Write a temporary header (sizes will be updated later)
                writeWavHeader(
                    fileOutputStream,
                    0,
                    channels,
                    sampleRate.toLong(),
                    byteRate.toLong(),
                    blockAlign,
                    bitDepth,
                    0L
                )

                // Write audio data by taking a snapshot from the ring buffer
                val audioDataLength = audioRingBuffer.snapshot(fileOutputStream)

                if (audioDataLength == 0) {
                    Log.w(TAG, "No audio data in buffer to write for ${outputFile.name}")
                    outputFile.delete() // Clean up empty file
                    return false
                }

                // Re-open the file to update the header with correct sizes
                RandomAccessFile(outputFile, "rw").use { randomAccessFile ->
                    randomAccessFile.seek(0) // Go to the beginning of the file

                    // Total file size - 8 (for "RIFF" and size field itself)
                    val overallFileSize = outputFile.length() - 8
                    // Size of audio data chunk
                    val audioChunkSize = audioDataLength

                    // Check for WAV size limit (approx 4GB max for unsigned 32-bit int)
                    // Standard WAV uses 32-bit int for size. Java's randomAccessFile.write(header) writes bytes.
                    // If size > Int.MAX_VALUE, toInt() returns negative, which when written as bytes is correct for unsigned interpretation up to 4GB.
                    // However, > 4GB will definitely fail/wrap.
                    if (overallFileSize > 0xFFFFFFFFL) {
                        Log.e(TAG, "File size exceeds WAV format limit (4GB). File may be corrupt or unplayable.")
                        // Ideally, we should use RF64 format for > 4GB, but that's a larger change.
                        // For now, we warn and proceed, but the header will contain wrapped values.
                    }

                    writeWavHeader(
                        randomAccessFile,
                        overallFileSize,
                        channels,
                        sampleRate.toLong(),
                        byteRate.toLong(),
                        blockAlign,
                        bitDepth,
                        audioChunkSize.toLong()
                    )
                }

                Log.d(
                    TAG,
                    "Successfully wrote WAV file: ${outputFile.absolutePath}, Data Size: $audioDataLength bytes"
                )
                return true
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error writing WAV file", e)
            outputFile.delete() // Clean up partially written file on error
            return false
        }
    }

    /**
     * Writes the WAV file header to a RandomAccessFile (for updating).
     */
    @Throws(IOException::class)
    private fun writeWavHeader(
        randomAccessFile: RandomAccessFile,
        totalAudioLenBytesActuallyWrittenPlusHeader: Long, // This is chunk RIFF size: FileSize - 8
        channels: Short,
        longSampleRate: Long,
        byteRate: Long, // SampleRate * NumChannels * BitsPerSample/8
        blockAlign: Short, // NumChannels * BitsPerSample/8
        bitsPerSample: Short,
        totalAudioLenBytesActuallyWritten: Long // This is SubChunk2Size (just audio data)
    ) {
        val header = createHeader(
            totalAudioLenBytesActuallyWrittenPlusHeader,
            channels,
            longSampleRate,
            byteRate,
            blockAlign,
            bitsPerSample,
            totalAudioLenBytesActuallyWritten
        )
        randomAccessFile.write(header.array())
    }

    /**
     * Writes the WAV file header to an OutputStream (for initial writing).
     */
    @Throws(IOException::class)
    private fun writeWavHeader(
        outStream: OutputStream,
        totalAudioLenBytesActuallyWrittenPlusHeader: Long, // This is chunk RIFF size: FileSize - 8
        channels: Short,
        longSampleRate: Long,
        byteRate: Long, // SampleRate * NumChannels * BitsPerSample/8
        blockAlign: Short, // NumChannels * BitsPerSample/8
        bitsPerSample: Short,
        totalAudioLenBytesActuallyWritten: Long // This is SubChunk2Size (just audio data)
    ) {
        val header = createHeader(
            totalAudioLenBytesActuallyWrittenPlusHeader,
            channels,
            longSampleRate,
            byteRate,
            blockAlign,
            bitsPerSample,
            totalAudioLenBytesActuallyWritten
        )
        outStream.write(header.array())
    }

    private fun createHeader(
        totalAudioLenBytesActuallyWrittenPlusHeader: Long, // This is chunk RIFF size: FileSize - 8
        channels: Short,
        longSampleRate: Long,
        byteRate: Long, // SampleRate * NumChannels * BitsPerSample/8
        blockAlign: Short, // NumChannels * BitsPerSample/8
        bitsPerSample: Short,
        totalAudioLenBytesActuallyWritten: Long // This is SubChunk2Size (just audio data)
    ): ByteBuffer {
        Log.d(TAG, "createHeader: totalAudioLenBytesActuallyWrittenPlusHeader=$totalAudioLenBytesActuallyWrittenPlusHeader, channels=$channels, longSampleRate=$longSampleRate, byteRate=$byteRate, blockAlign=$blockAlign, bitsPerSample=$bitsPerSample, totalAudioLenBytesActuallyWritten=$totalAudioLenBytesActuallyWritten")
        val header = ByteBuffer.allocate(44)
        header.order(ByteOrder.LITTLE_ENDIAN)

        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt((totalAudioLenBytesActuallyWrittenPlusHeader).toInt())
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1)
        header.putShort(channels)
        header.putInt(longSampleRate.toInt())
        header.putInt(byteRate.toInt())
        header.putShort(blockAlign)
        header.putShort(bitsPerSample)
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(totalAudioLenBytesActuallyWritten.toInt())
        return header
    }
}