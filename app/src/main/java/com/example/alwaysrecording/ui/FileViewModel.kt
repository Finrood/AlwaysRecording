package com.example.alwaysrecording.ui

import android.app.Application
import android.content.Intent
import android.media.MediaMetadataRetriever
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.alwaysrecording.domain.model.Recording
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import com.example.alwaysrecording.data.settings.SettingsRepository
import com.example.alwaysrecording.data.settings.DataStoreSettingsRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.InputStreamReader
import java.io.OutputStreamWriter


val Json = Json { ignoreUnknownKeys = true }

// Data class to represent the structure of tags.json
@Serializable
data class RecordingTags(
    val tags: Map<String, List<String>> = emptyMap() // Map of recordingId (String) to List of tags
)

class FileViewModel @JvmOverloads constructor(
    application: Application,
    private val settingsRepository: SettingsRepository = DataStoreSettingsRepository(application)
) : AndroidViewModel(application) {

    internal val _recordings = MutableStateFlow<List<Recording>>(emptyList())
    val recordings: StateFlow<List<Recording>> = _recordings

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val filteredRecordings: StateFlow<List<Recording>> =
        combine(_recordings, _searchQuery) { recordings, query ->
            if (query.isBlank()) {
                recordings
            } else {
                recordings.filter { recording -> // Explicitly name the lambda parameter
                    recording.filename.contains(query, ignoreCase = true)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _allTags = MutableStateFlow<Map<String, List<String>>>(emptyMap()) // Map of recordingId to tags

    fun loadRecordings() {
        viewModelScope.launch {
            val storageDir = getApplication<Application>().getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
            val files = storageDir?.listFiles { file -> file.name.endsWith(".wav") || file.name.endsWith(".mp3") } ?: emptyArray()

            // Load tags from tags.json
            val tagsFile = File(storageDir, "tags.json")
            if (tagsFile.exists()) {
                try {
                    tagsFile.inputStream().use { inputStream ->
                        val jsonString = InputStreamReader(inputStream).readText()
                        val loadedTags = Json.decodeFromString(RecordingTags.serializer(), jsonString)
                        _allTags.value = loadedTags.tags
                    }
                } catch (e: Exception) {
                    _allTags.value = emptyMap() // Clear tags on error
                }
            } else {
                _allTags.value = emptyMap()
            }

            _recordings.value = files.mapNotNull { file ->
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(file.absolutePath)
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
                    retriever.release()

                    val recordingId = file.lastModified() // Use lastModified as ID for now
                    val fileTags = _allTags.value[recordingId.toString()] ?: emptyList()

                    Recording(
                        id = file.lastModified(),
                        filename = file.name,
                        timestamp = file.lastModified(),
                        duration = duration,
                        size = file.length(),
                        tags = fileTags // Populate tags
                    )
                } catch (e: Exception) {
                    null
                }
            }.sortedByDescending { it.timestamp }
        }
    }

    private suspend fun saveTagsToJson() {
        val storageDir = getApplication<Application>().getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
        val tagsFile = File(storageDir, "tags.json")
        try {
            tagsFile.outputStream().use { outputStream ->
                val jsonString = Json.encodeToString(RecordingTags.serializer(), RecordingTags(_allTags.value))
                OutputStreamWriter(outputStream).write(jsonString)
            }
        } catch (e: Exception) {
        }
    }

    fun addTagToRecording(recordingId: Long, tag: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentTags = _allTags.value.toMutableMap()
            val fileTags = currentTags[recordingId.toString()]?.toMutableList() ?: mutableListOf()
            if (!fileTags.contains(tag)) {
                fileTags.add(tag)
                currentTags[recordingId.toString()] = fileTags
                _allTags.value = currentTags
                saveTagsToJson()
                loadRecordings() // Reload recordings to update UI
            }
        }
    }

    fun removeTagFromRecording(recordingId: Long, tag: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentTags = _allTags.value.toMutableMap()
            val fileTags = currentTags[recordingId.toString()]?.toMutableList()
            if (fileTags != null && fileTags.remove(tag)) {
                currentTags[recordingId.toString()] = fileTags
                _allTags.value = currentTags
                saveTagsToJson()
                loadRecordings() // Reload recordings to update UI
            }
        }
    }

    fun renameRecording(recording: Recording, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val oldFile = File(getApplication<Application>().getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC), recording.filename)
            val newFile = File(oldFile.parent, newName)

            if (oldFile.exists() && oldFile.renameTo(newFile)) {
                // Update tags.json if the file was renamed
                val currentTags = _allTags.value.toMutableMap()
                val tagsForRenamedFile = currentTags.remove(recording.id.toString())
                if (tagsForRenamedFile != null) {
                    // Use the new file's lastModified as the new ID for tags
                    currentTags[newFile.lastModified().toString()] = tagsForRenamedFile
                }
                _allTags.value = currentTags
                saveTagsToJson()
                loadRecordings() // Reload recordings to update UI
            } else {
                // Optionally, emit an error state to the UI
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun shareRecording(recording: Recording) {
        val context = getApplication<Application>()
        val file = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC), recording.filename)
        if (file.exists()) {
            val uri = FileProvider.getUriForFile(context, "com.example.alwaysrecording.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/wav"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Share Recording")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    }

    fun openFile(recording: Recording) {
        val context = getApplication<Application>()
        val file = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC), recording.filename)
        if (file.exists()) {
            val uri = FileProvider.getUriForFile(context, "com.example.alwaysrecording.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "audio/wav")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (e: android.content.ActivityNotFoundException) {
                // No app found to open the file
            }
        }
    }

    fun deleteRecording(recording: Recording) {
        val file = File(getApplication<Application>().getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC), recording.filename)
        if (file.exists()) {
            file.delete()
            loadRecordings()
        }
    }
}