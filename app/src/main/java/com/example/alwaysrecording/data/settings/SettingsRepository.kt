package com.example.alwaysrecording.data.settings

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val bufferLengthMinutes: Flow<Int>
    suspend fun setBufferLengthMinutes(minutes: Int)

    val sampleRateHz: Flow<Int>
    suspend fun setSampleRateHz(sampleRate: Int)

    val autoStartEnabled: Flow<Boolean>
    suspend fun setAutoStartEnabled(enabled: Boolean)

    val storageQuotaMb: Flow<Int>
    suspend fun setStorageQuotaMb(mb: Int)

    val saveDirectoryUri: Flow<String?>
    suspend fun setSaveDirectoryUri(uri: String?)

    val channels: Flow<Int>
    suspend fun setChannels(channels: Int)

    val bitDepth: Flow<Int>
    suspend fun setBitDepth(bitDepth: Int)
}