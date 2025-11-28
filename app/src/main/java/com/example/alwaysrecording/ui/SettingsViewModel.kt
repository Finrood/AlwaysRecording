package com.example.alwaysrecording.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.alwaysrecording.data.settings.DataStoreSettingsRepository
import com.example.alwaysrecording.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import android.os.Environment
import kotlinx.coroutines.flow.firstOrNull

class SettingsViewModel @JvmOverloads constructor(
    application: Application,
    private val settingsRepository: SettingsRepository = DataStoreSettingsRepository(application)
) : AndroidViewModel(application) {

    val bufferLength = settingsRepository.bufferLengthMinutes
        .stateIn(viewModelScope, SharingStarted.Eagerly, DataStoreSettingsRepository.DEFAULT_BUFFER_LENGTH_MINUTES)

    val sampleRate = settingsRepository.sampleRateHz
        .stateIn(viewModelScope, SharingStarted.Eagerly, DataStoreSettingsRepository.DEFAULT_SAMPLE_RATE_HZ)

    val autoStart = settingsRepository.autoStartEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, DataStoreSettingsRepository.DEFAULT_AUTO_START_ENABLED)

    val storageQuota = settingsRepository.storageQuotaMb
        .stateIn(viewModelScope, SharingStarted.Eagerly, DataStoreSettingsRepository.DEFAULT_STORAGE_QUOTA_MB)

    val channels = settingsRepository.channels
        .stateIn(viewModelScope, SharingStarted.Eagerly, DataStoreSettingsRepository.DEFAULT_CHANNELS)

    val bitDepth = settingsRepository.bitDepth
        .stateIn(viewModelScope, SharingStarted.Eagerly, DataStoreSettingsRepository.DEFAULT_BIT_DEPTH)

    private val _currentStorageUsageMb = MutableStateFlow(0)
    val currentStorageUsageMb: StateFlow<Int> = _currentStorageUsageMb

    val saveDirectoryUri = settingsRepository.saveDirectoryUri
        .stateIn(viewModelScope, SharingStarted.Eagerly, DataStoreSettingsRepository.DEFAULT_SAVE_DIRECTORY_URI)

    init {
        loadStorageUsage() // Initial load
    }

    fun setBufferLength(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setBufferLengthMinutes(minutes)
        }
    }

    fun setSampleRate(hz: Int) {
        viewModelScope.launch {
            settingsRepository.setSampleRateHz(hz)
        }
    }

    fun setAutoStart(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoStartEnabled(enabled)
        }
    }

    fun setStorageQuota(mb: Int) {
        viewModelScope.launch {
            settingsRepository.setStorageQuotaMb(mb)
        }
    }

    fun setChannels(channels: Int) {
        viewModelScope.launch {
            settingsRepository.setChannels(channels)
        }
    }

    fun setBitDepth(bitDepth: Int) {
        viewModelScope.launch {
            settingsRepository.setBitDepth(bitDepth)
        }
    }

    fun setSaveDirectoryUri(uri: String?) {
        viewModelScope.launch {
            settingsRepository.setSaveDirectoryUri(uri)
        }
    }

    private fun loadStorageUsage() {
        viewModelScope.launch {
            val storageDir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            if (storageDir != null && storageDir.exists()) {
                val files = storageDir.listFiles { file -> file.name.endsWith(".wav") } ?: emptyArray()
                val totalSize = files.sumOf { it.length() }
                _currentStorageUsageMb.value = (totalSize / (1024 * 1024)).toInt()
            }
        }
    }
}
