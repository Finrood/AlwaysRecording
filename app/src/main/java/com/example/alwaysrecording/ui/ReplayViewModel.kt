package com.example.alwaysrecording.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import com.example.alwaysrecording.data.service.ReplayRecorderService
import com.example.alwaysrecording.domain.model.RecorderState
import kotlinx.coroutines.flow.StateFlow

class ReplayViewModel(application: Application) : AndroidViewModel(application) {

    val recorderState: StateFlow<RecorderState> = ReplayRecorderService.publicServiceState

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
