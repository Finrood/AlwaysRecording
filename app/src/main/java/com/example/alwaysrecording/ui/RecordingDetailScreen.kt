package com.example.alwaysrecording.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.layout.width // Added
import java.util.Locale

@Composable
fun RecordingDetailScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
    filename: String?,
    detailViewModel: RecordingDetailViewModel = viewModel(),
    fileViewModel: FileViewModel = viewModel() // Added
) {
    val isPlaying by detailViewModel.isPlaying.collectAsState()
    val currentPosition by detailViewModel.currentPosition.collectAsState()
    val totalDuration by detailViewModel.totalDuration.collectAsState()
    val playbackSpeed by detailViewModel.playbackSpeed.collectAsState()
    val recordings by fileViewModel.recordings.collectAsState() // To get the current recording with tags
    val currentRecording = recordings.find { it.filename == filename }

    var newTagInput by remember { mutableStateOf("") }

    LaunchedEffect(filename) {
        if (filename != null) {
            detailViewModel.initPlayer(filename)
        }
    }

    Column(modifier = modifier.padding(16.dp)) {
        Text(text = "Playback", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = filename ?: "No file selected", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))

        Slider(
            value = currentPosition.toFloat(),
            onValueChange = { detailViewModel.seekTo(it.toInt()) },
            valueRange = 0f..totalDuration.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = formatMillis(currentPosition.toLong()))
            Text(text = formatMillis(totalDuration.toLong()))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (isPlaying) detailViewModel.pause() else detailViewModel.play() }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = String.format(Locale.getDefault(), "Playback Speed: %.1fx", playbackSpeed)) // Fixed string formatting
        Slider(
            value = playbackSpeed,
            onValueChange = { newSpeed ->
                detailViewModel.setPlaybackSpeed(newSpeed)
            },
            valueRange = 0.5f..2.0f, // Example range: 0.5x to 2.0x
            steps = 14 // For 0.5, 0.6, ..., 2.0 (15 values, 14 steps)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Tags", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            currentRecording?.tags?.forEach { tag ->
                AssistChip(
                    onClick = {
                        currentRecording.id?.let { id ->
                            fileViewModel.removeTagFromRecording(id, tag)
                        }
                    },
                    label = { Text(tag) },
                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove tag") }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newTagInput,
                onValueChange = { newTagInput = it },
                label = { Text("Add new tag") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp)) // Fixed width modifier
            Button(
                onClick = {
                    if (newTagInput.isNotBlank() && currentRecording != null) {
                        currentRecording.id?.let { id ->
                            fileViewModel.addTagToRecording(id, newTagInput.trim())
                            newTagInput = ""
                        }
                    }
                }
            ) {
                Text("Add")
            }
        }
    }
}

private fun formatMillis(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}