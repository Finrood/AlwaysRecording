package com.example.alwaysrecording.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.alwaysrecording.data.service.ReplayRecorderService
import com.example.alwaysrecording.data.settings.DataStoreSettingsRepository
import com.example.alwaysrecording.data.settings.SettingsRepository
import com.example.alwaysrecording.domain.model.RecorderState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReplayViewModel @JvmOverloads constructor(
    application: Application,
    private val settingsRepository: SettingsRepository = DataStoreSettingsRepository(application)
) : AndroidViewModel(application) {

    val recorderState: StateFlow<RecorderState> = ReplayRecorderService.publicServiceState

    init {
        // Restore state if app is opened and service isn't running but should be
        viewModelScope.launch {
            val shouldBeRecording = settingsRepository.isRecording.first()
            if (shouldBeRecording && recorderState.value !is RecorderState.Recording) {
                startReplayService()
            }
        }
    }

    fun startReplayService() {
        val intent = ReplayRecorderService.newStartIntent(getApplication())
        getApplication<Application>().startService(intent)
    }

    fun stopReplayService() {
        val intent = ReplayRecorderService.newStopIntent(getApplication())
        getApplication<Application>().startService(intent)
    }

    fun saveReplay() {
        val intent = ReplayRecorderService.newSaveReplayIntent(getApplication())
        getApplication<Application>().startService(intent)
    }
}
