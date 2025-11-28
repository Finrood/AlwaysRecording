package com.example.alwaysrecording.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey // Added
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "replay_settings")

class DataStoreSettingsRepository(private val context: Context) : SettingsRepository {

    private object PreferencesKeys {
        val BUFFER_LENGTH_MINUTES = intPreferencesKey("buffer_length_minutes")
        val SAMPLE_RATE_HZ = intPreferencesKey("sample_rate_hz")
        val AUTO_START_ENABLED = booleanPreferencesKey("auto_start_enabled")
        val STORAGE_QUOTA_MB = intPreferencesKey("storage_quota_mb")
        val CHANNELS = intPreferencesKey("channels")
        val BIT_DEPTH = intPreferencesKey("bit_depth")
        val SAVE_DIRECTORY_URI = stringPreferencesKey("save_directory_uri")
    }

    companion object {
        const val DEFAULT_BUFFER_LENGTH_MINUTES = 5
        const val DEFAULT_SAMPLE_RATE_HZ = 16000 // 16 kHz
        const val DEFAULT_AUTO_START_ENABLED = false
        const val DEFAULT_STORAGE_QUOTA_MB = 100
        const val DEFAULT_CHANNELS = 1 // Mono
        const val DEFAULT_BIT_DEPTH = 16 // 16-bit PCM
        val DEFAULT_SAVE_DIRECTORY_URI: String? = null // Changed to val
    }

    override val bufferLengthMinutes: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.BUFFER_LENGTH_MINUTES] ?: DEFAULT_BUFFER_LENGTH_MINUTES
        }

    override suspend fun setBufferLengthMinutes(minutes: Int) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.BUFFER_LENGTH_MINUTES] = minutes
        }
    }

    override val sampleRateHz: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SAMPLE_RATE_HZ] ?: DEFAULT_SAMPLE_RATE_HZ
        }

    override suspend fun setSampleRateHz(sampleRate: Int) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.SAMPLE_RATE_HZ] = sampleRate
        }
    }

    override val autoStartEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.AUTO_START_ENABLED] ?: DEFAULT_AUTO_START_ENABLED
        }

    override suspend fun setAutoStartEnabled(enabled: Boolean) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.AUTO_START_ENABLED] = enabled
        }
    }

    override val storageQuotaMb: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.STORAGE_QUOTA_MB] ?: DEFAULT_STORAGE_QUOTA_MB
        }

    override suspend fun setStorageQuotaMb(mb: Int) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.STORAGE_QUOTA_MB] = mb
        }
    }

    override val saveDirectoryUri: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SAVE_DIRECTORY_URI] ?: DEFAULT_SAVE_DIRECTORY_URI
        }

    override suspend fun setSaveDirectoryUri(uri: String?) {
        context.dataStore.edit { settings ->
            if (uri != null) {
                settings[PreferencesKeys.SAVE_DIRECTORY_URI] = uri
            } else {
                settings.remove(PreferencesKeys.SAVE_DIRECTORY_URI)
            }
        }
    }

    override val channels: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.CHANNELS] ?: DEFAULT_CHANNELS
        }

    override suspend fun setChannels(channels: Int) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.CHANNELS] = channels
        }
    }

    override val bitDepth: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.BIT_DEPTH] ?: DEFAULT_BIT_DEPTH
        }

    override suspend fun setBitDepth(bitDepth: Int) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.BIT_DEPTH] = bitDepth
        }
    }
}