package com.example.alwaysrecording.ui

import android.app.Application
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import android.os.Build // Added

class RecordingDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition

    private val _totalDuration = MutableStateFlow(0)
    val totalDuration: StateFlow<Int> = _totalDuration

    private val _playbackSpeed = MutableStateFlow(1.0f) // Default to normal speed
    val playbackSpeed: StateFlow<Float> = _playbackSpeed

    internal var mediaPlayer: MediaPlayer? = null
    private var timerJob: Job? = null

    fun initPlayer(filename: String) {
        val file = File(getApplication<Application>().getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC), filename)
        if (file.exists()) {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer()
            }
            mediaPlayer?.apply {
                setDataSource(file.absolutePath)
                prepare()
                _totalDuration.value = duration
            }
        }
    }

    fun play() {
        mediaPlayer?.apply {
            playbackParams = playbackParams.setSpeed(_playbackSpeed.value)
            start()
        }
        _isPlaying.value = true
        startTimer()
    }

    fun pause() {
        mediaPlayer?.pause()
        _isPlaying.value = false
        timerJob?.cancel()
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        _currentPosition.value = position
    }

    private fun startTimer() {
        timerJob = viewModelScope.launch {
            while (_isPlaying.value) {
                _currentPosition.value = mediaPlayer?.currentPosition ?: 0
                delay(100) // Update every 100ms instead of 1000ms
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        mediaPlayer?.apply {
            playbackParams = playbackParams.setSpeed(speed)
        }
    }

    public override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        mediaPlayer = null
        timerJob?.cancel()
        _isPlaying.value = false // Explicitly set isPlaying to false
    }
}
