package com.example.alwaysrecording.ui

import android.app.Application
import android.media.MediaRecorder
import android.os.Build
import com.example.alwaysrecording.data.recorder.MediaRecorderFactory
import com.example.alwaysrecording.data.recorder.DefaultMediaRecorderFactory
import com.example.alwaysrecording.data.storage.StorageChecker
import com.example.alwaysrecording.data.storage.DefaultStorageChecker
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import android.net.Uri
import com.example.alwaysrecording.data.settings.SettingsRepository
import com.example.alwaysrecording.data.settings.DataStoreSettingsRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.isActive

enum class RecordingFormat(val extension: String, val outputFormat: Int, val audioEncoder: Int) {
    M4A(
        "m4a",
        MediaRecorder.OutputFormat.MPEG_4,
        MediaRecorder.AudioEncoder.AAC
    ),
    THREE_GPP(
        "3gp",
        MediaRecorder.OutputFormat.THREE_GPP,
        MediaRecorder.AudioEncoder.AMR_NB
    )
}

class RecordingViewModel @JvmOverloads constructor(
    application: Application,
    private val mediaRecorderFactory: MediaRecorderFactory = DefaultMediaRecorderFactory(),
    private val storageChecker: StorageChecker = DefaultStorageChecker()
) : AndroidViewModel(application) {

    private val settingsRepository: SettingsRepository = DataStoreSettingsRepository(application)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused

    private val _elapsedTime = MutableStateFlow(0L)
    val elapsedTime: StateFlow<Long> = _elapsedTime

    private val _fileSize = MutableStateFlow(0L)
    val fileSize: StateFlow<Long> = _fileSize

    internal var mediaRecorder: MediaRecorder? = null
    private var timerJob: Job? = null
    internal var outputFile: File? = null

    private val _error = MutableStateFlow<UiError?>(null)
    val error: StateFlow<UiError?> = _error

    private val _selectedFormat = MutableStateFlow(RecordingFormat.M4A) // Default to M4A
    val selectedFormat: StateFlow<RecordingFormat> = _selectedFormat

    private val _userDefinedFilename = MutableStateFlow<String?>(null)
    val userDefinedFilename: StateFlow<String?> = _userDefinedFilename

    fun startRecording() {
        val storageDir = getApplication<Application>().getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
        if (!storageChecker.isStorageAvailable(getApplication(), storageDir)) {
            _error.value = UiError.Snackbar("Not enough storage available.")
            return
        }
        val baseFileName = if (!userDefinedFilename.value.isNullOrEmpty()) {
            userDefinedFilename.value!!
        } else {
            "standard_recording_${System.currentTimeMillis()}"
        }
        val fileName = "$baseFileName.${_selectedFormat.value.extension}"
        outputFile = File(storageDir, fileName)

        mediaRecorder = mediaRecorderFactory.create(getApplication(), _selectedFormat.value, outputFile!!).apply {
            try {
                prepare()
            } catch (e: IOException) {
                _error.value = UiError.Dialog("Error preparing recorder", e.message ?: "")
                return
            }
            start()
        }
        _isRecording.value = true
        startTimer()
    }

    fun stopRecording() {
        mediaRecorder?.apply {
            stop()
            release()
        }
        mediaRecorder = null
        _isRecording.value = false
        _isPaused.value = false
        stopTimer()
        _elapsedTime.value = 0

        // Handle saving to SAF if a URI is set
        viewModelScope.launch(Dispatchers.IO) {
            val saveUriString = settingsRepository.saveDirectoryUri.firstOrNull()
            if (!saveUriString.isNullOrEmpty() && outputFile != null && outputFile!!.exists()) {
                val contentResolver = getApplication<Application>().contentResolver
                val treeUri = Uri.parse(saveUriString)
                val pickedDir = DocumentFile.fromTreeUri(getApplication<Application>(), treeUri)

                if (pickedDir != null && pickedDir.exists() && pickedDir.isDirectory && pickedDir.canWrite()) {
                    val newFile = pickedDir.createFile("audio/${_selectedFormat.value.extension}", outputFile!!.name)
                    if (newFile != null) {
                        try {
                            contentResolver.openOutputStream(newFile.uri)?.use { outputStream ->
                                outputFile!!.inputStream().use { inputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                            Log.d("RecordingViewModel", "Successfully copied to SAF: ${newFile.uri}")
                            // Delete the temporary file after successful copy
                            outputFile!!.delete()
                        } catch (e: Exception) {
                            Log.e("RecordingViewModel", "Error copying to SAF: ${e.message}", e)
                            _error.value = UiError.Dialog("Save Error", "Failed to save to selected location: ${e.message}")
                        }
                    } else {
                        _error.value = UiError.Dialog("Save Error", "Failed to create file in selected SAF location.")
                    }
                } else {
                    _error.value = UiError.Dialog("Save Error", "Selected SAF location is not valid or writable.")
                }
            } else if (outputFile != null && outputFile!!.exists()) {
                // If no SAF URI, but the file exists, it means it was saved to the Music directory.
                Log.d("RecordingViewModel", "Recording saved to storage: ${outputFile!!.absolutePath}")
            }
            outputFile = null // Clear the reference to the temporary file
        }
    }

    fun pauseRecording() {
        mediaRecorder?.pause()
        _isPaused.value = true
        timerJob?.cancel()
    }

    fun resumeRecording() {
        mediaRecorder?.resume()
        _isPaused.value = false
        startTimer()
    }

    private fun startTimer() {
        timerJob = viewModelScope.launch {
            while (isActive) { // Use isActive to ensure the loop is cancellable
                delay(1000)
                _elapsedTime.value += 1
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null // Reset the job to null
    }

    fun setUserDefinedFilename(filename: String?) {
        _userDefinedFilename.value = filename
    }

    fun setSelectedFormat(format: RecordingFormat) {
        _selectedFormat.value = format
    }
    
    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        mediaRecorder?.release()
    }
}
