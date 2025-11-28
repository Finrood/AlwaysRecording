package com.example.alwaysrecording.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.alwaysrecording.domain.model.Recording
import java.util.concurrent.TimeUnit
import java.util.Locale

@Composable
fun FileListScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
    fileViewModel: FileViewModel = viewModel()
) {
    val recordings by fileViewModel.filteredRecordings.collectAsState()
    val searchQuery by fileViewModel.searchQuery.collectAsState()
    var showRenameDialog by remember { mutableStateOf<Recording?>(null) }

    LaunchedEffect(Unit) {
        fileViewModel.loadRecordings()
    }

    if (showRenameDialog != null) {
        RenameDialog(
            recording = showRenameDialog!!,
            onDismiss = { showRenameDialog = null },
            onRename = { recording, newName ->
                fileViewModel.renameRecording(recording, newName)
                showRenameDialog = null
            }
        )
    }

    Column(modifier = modifier.padding(16.dp)) {
        Text(text = "Recorded Files", style = MaterialTheme.typography.headlineMedium)

        // Search input field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { fileViewModel.setSearchQuery(it) },
            label = { Text("Search recordings") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { fileViewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            }
        )

        LazyColumn {
            items(recordings) { recording ->
                RecordingListItem(recording, fileViewModel, navController) {
                    showRenameDialog = it
                }
            }
        }
    }
}

@Composable
fun RecordingListItem(
    recording: Recording,
    fileViewModel: FileViewModel,
    navController: NavController,
    onRenameClick: (Recording) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                navController.navigate("recording_detail/${recording.filename}")
            }
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = recording.filename, style = MaterialTheme.typography.bodyLarge)
            val durationMillis = recording.duration
            val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) % 60
            Text(
                text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Row {
            IconButton(onClick = { fileViewModel.shareRecording(recording) }) {
                Icon(Icons.Default.Share, contentDescription = "Share")
            }
            IconButton(onClick = { onRenameClick(recording) }) {
                Icon(Icons.Default.Edit, contentDescription = "Rename")
            }
            IconButton(onClick = { fileViewModel.deleteRecording(recording) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

@Composable
fun RenameDialog(
    recording: Recording,
    onDismiss: () -> Unit,
    onRename: (Recording, String) -> Unit
) {
    var newName by remember { mutableStateOf(recording.filename) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Recording") },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("New filename") }
            )
        },
        confirmButton = {
            Button(onClick = { onRename(recording, newName) }) {
                Text("Rename")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}