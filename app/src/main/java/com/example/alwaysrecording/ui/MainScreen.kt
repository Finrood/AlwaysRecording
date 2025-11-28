package com.example.alwaysrecording.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.alwaysrecording.domain.model.RecorderState
import androidx.compose.runtime.mutableStateOf // Added
import androidx.compose.runtime.remember // Added
import androidx.compose.runtime.setValue // Added
import androidx.compose.material3.AlertDialog // Added
import androidx.compose.material3.OutlinedTextField // Added
import androidx.compose.material3.ExperimentalMaterial3Api // Added
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
    recordingViewModel: RecordingViewModel = viewModel(),
    replayViewModel: ReplayViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    var showFilenameDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            showFilenameDialog = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StandardRecordingCard(recordingViewModel, onStartClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                showFilenameDialog = true
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        })
        Spacer(modifier = Modifier.height(16.dp))
        ReplayRecordingCard(replayViewModel, settingsViewModel, onRequestPermission = {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        })
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { navController.navigate("files") }) {
            Text("View Recordings")
        }
    }

    if (showFilenameDialog) {
        FilenameInputDialog(
            onDismiss = { showFilenameDialog = false },
            onConfirm = {
                recordingViewModel.setUserDefinedFilename(it)
                recordingViewModel.startRecording()
                showFilenameDialog = false
            }
        )
    }
}

@Composable
fun FilenameInputDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var filename by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Recording Name (Optional)") },
        text = {
            OutlinedTextField(
                value = filename,
                onValueChange = { filename = it },
                label = { Text("Filename") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(filename) }) {
                Text("Start Recording")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandardRecordingCard(recordingViewModel: RecordingViewModel, onStartClick: () -> Unit) {
    val isRecording by recordingViewModel.isRecording.collectAsState()
    val isPaused by recordingViewModel.isPaused.collectAsState()
    val elapsedTime by recordingViewModel.elapsedTime.collectAsState()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Standard Recording", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            AnimatedVisibility(visible = isRecording) {
                Text(text = "Time: $elapsedTime s", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(modifier = Modifier.height(16.dp))

            

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onStartClick, enabled = !isRecording) {
                    Icon(Icons.Filled.Mic, contentDescription = "Start Recording")
                }
                IconButton(onClick = { recordingViewModel.stopRecording() }, enabled = isRecording) {
                    Icon(Icons.Filled.Stop, contentDescription = "Stop Recording")
                }
                IconButton(onClick = { recordingViewModel.pauseRecording() }, enabled = isRecording && !isPaused) {
                    Icon(Icons.Filled.Pause, contentDescription = "Pause Recording")
                }
                IconButton(onClick = { recordingViewModel.resumeRecording() }, enabled = isPaused) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Resume Recording")
                }
            }
        }
    }
}

@Composable
fun ReplayRecordingCard(
    replayViewModel: ReplayViewModel,
    settingsViewModel: SettingsViewModel = viewModel(),
    onRequestPermission: () -> Unit
) {
    val replayState by replayViewModel.recorderState.collectAsState()
    val bufferLength by settingsViewModel.bufferLength.collectAsState()
    val context = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Always-On Buffered Recording", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Service Enabled")
                Switch(
                    checked = replayState is RecorderState.Recording,
                    onCheckedChange = {
                        if (it) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                replayViewModel.startReplayService()
                            } else {
                                onRequestPermission()
                            }
                        } else {
                            replayViewModel.stopReplayService()
                        }
                    },
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { replayViewModel.saveReplay() }, enabled = replayState is RecorderState.Recording) {
                Text("Save Last $bufferLength Minutes")
            }
            Text(text = "Status: ${replayState::class.java.simpleName}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}