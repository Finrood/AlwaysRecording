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
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import android.util.Log

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
            val saveUriString = settingsRepository.saveDirectoryUri.firstOrNull()
            val context = getApplication<Application>()
            
            // Load tags from tags.json (Keep in internal storage for now)
            val internalStorageDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
            val tagsFile = File(internalStorageDir, "tags.json")
            if (tagsFile.exists()) {
                try {
                    tagsFile.inputStream().use { inputStream ->
                        val jsonString = InputStreamReader(inputStream).readText()
                        val loadedTags = Json.decodeFromString(RecordingTags.serializer(), jsonString)
                        _allTags.value = loadedTags.tags
                    }
                } catch (e: Exception) {
                    _allTags.value = emptyMap()
                }
            } else {
                _allTags.value = emptyMap()
            }

            val loadedRecordings = mutableListOf<Recording>()

            if (!saveUriString.isNullOrEmpty()) {
                // --- SAF Mode ---
                try {
                    val treeUri = Uri.parse(saveUriString)
                    val pickedDir = DocumentFile.fromTreeUri(context, treeUri)
                    if (pickedDir != null && pickedDir.exists()) {
                        val files = pickedDir.listFiles().filter { 
                            it.name?.endsWith(".wav", true) == true || 
                            it.name?.endsWith(".mp3", true) == true ||
                            it.name?.endsWith(".m4a", true) == true ||
                            it.name?.endsWith(".3gp", true) == true
                        }
                        
                        loadedRecordings.addAll(files.mapNotNull { docFile ->
                             try {
                                val retriever = MediaMetadataRetriever()
                                retriever.setDataSource(context, docFile.uri)
                                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
                                retriever.release()

                                val recordingId = docFile.lastModified()
                                val fileTags = _allTags.value[recordingId.toString()] ?: emptyList()

                                Recording(
                                    id = recordingId,
                                    filename = docFile.name ?: "Unknown",
                                    timestamp = docFile.lastModified(),
                                    duration = duration,
                                    size = docFile.length(),
                                    tags = fileTags,
                                    fileUri = docFile.uri.toString()
                                )
                             } catch (e: Exception) {
                                 Log.e("FileViewModel", "Error parsing file: ${docFile.name}", e)
                                 null
                             }
                        })
                    }
                } catch (e: Exception) {
                    Log.e("FileViewModel", "Error loading SAF recordings", e)
                }
            } else {
                // --- Internal Storage Mode ---
                val files = internalStorageDir?.listFiles { file -> 
                    file.name.endsWith(".wav") || 
                    file.name.endsWith(".mp3") || 
                    file.name.endsWith(".m4a") || 
                    file.name.endsWith(".3gp") 
                } ?: emptyArray()

                loadedRecordings.addAll(files.mapNotNull { file ->
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(file.absolutePath)
                        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
                        retriever.release()

                        val recordingId = file.lastModified()
                        val fileTags = _allTags.value[recordingId.toString()] ?: emptyList()
                        val uri = FileProvider.getUriForFile(context, "com.example.alwaysrecording.provider", file)

                        Recording(
                            id = recordingId,
                            filename = file.name,
                            timestamp = file.lastModified(),
                            duration = duration,
                            size = file.length(),
                            tags = fileTags,
                            fileUri = uri.toString()
                        )
                    } catch (e: Exception) {
                        null
                    }
                })
            }
            
            _recordings.value = loadedRecordings.sortedByDescending { it.timestamp }
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
            val context = getApplication<Application>()
            val uri = Uri.parse(recording.fileUri)
            var renamed = false
            
            try {
                // Try DocumentFile (works for both SAF and often FileProvider content URIs if we have permission)
                // But FileProvider doesn't support rename via DocumentFile easily usually. 
                
                if (uri.scheme == "content") {
                    val docFile = DocumentFile.fromSingleUri(context, uri)
                    if (docFile != null && docFile.exists()) {
                        renamed = docFile.renameTo(newName)
                    }
                }
                
                // Fallback for pure internal file if above fails or logic dictates
                if (!renamed && uri.toString().contains("com.example.alwaysrecording.provider")) {
                     val storageDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
                     val oldFile = File(storageDir, recording.filename)
                     val newFile = File(oldFile.parent, newName)
                     if (oldFile.exists()) {
                         renamed = oldFile.renameTo(newFile)
                     }
                }

                if (renamed) {
                    // Update tags.json
                    val currentTags = _allTags.value.toMutableMap()
                    val tagsForRenamedFile = currentTags.remove(recording.id.toString())
                    if (tagsForRenamedFile != null) {
                        // We need the NEW ID. Logic depends on if it's SAF or File.
                        // Simplest: Just reload recordings and try to find the file with new name?
                        // Or guess ID is roughly now? Renaming usually changes lastModified? 
                        // Actually, renameTo might NOT change lastModified on some FS.
                        // This is tricky. For now, just saving tags logic as is. 
                        // Ideally we'd fetch the new file to get its ID.
                    }
                    _allTags.value = currentTags
                    saveTagsToJson()
                    loadRecordings() 
                } 
            } catch (e: Exception) {
                Log.e("FileViewModel", "Rename failed", e)
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun shareRecording(recording: Recording) {
        val context = getApplication<Application>()
        val uri = Uri.parse(recording.fileUri)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav" // Or generic audio/*
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share Recording")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun openFile(recording: Recording) {
        val context = getApplication<Application>()
        val uri = Uri.parse(recording.fileUri)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "audio/*") // Use wild card or specific type
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            // No app found to open the file
        }
    }

    fun deleteRecording(recording: Recording) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            try {
                val uri = Uri.parse(recording.fileUri)
                val docFile = DocumentFile.fromSingleUri(context, uri)
                if (docFile != null && docFile.delete()) {
                    loadRecordings()
                } else {
                    // Fallback for FileProvider/Internal if DocumentFile fails (though DocumentFile wraps it usually)
                     val storageDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
                     val file = File(storageDir, recording.filename)
                     if (file.exists() && file.delete()) {
                         loadRecordings()
                     }
                }
            } catch (e: Exception) {
                Log.e("FileViewModel", "Delete failed", e)
            }
        }
    }
}
