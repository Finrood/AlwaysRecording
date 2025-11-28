package com.example.alwaysrecording.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current

    val bufferLength by settingsViewModel.bufferLength.collectAsState()
    val sampleRate by settingsViewModel.sampleRate.collectAsState()
    val autoStart by settingsViewModel.autoStart.collectAsState()
    val storageQuota by settingsViewModel.storageQuota.collectAsState()
    val channels by settingsViewModel.channels.collectAsState()
    val bitDepth by settingsViewModel.bitDepth.collectAsState()
    val currentStorageUsageMb by settingsViewModel.currentStorageUsageMb.collectAsState()
    val saveDirectoryUri by settingsViewModel.saveDirectoryUri.collectAsState()

    val openDirectoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val contentResolver = context.contentResolver
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(it, takeFlags)
            settingsViewModel.setSaveDirectoryUri(it.toString())
        }
    }

    Column(modifier = modifier.padding(16.dp)) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Buffer Length: $bufferLength minutes")
        Slider(
            value = bufferLength.toFloat(),
            onValueChange = { settingsViewModel.setBufferLength(it.toInt()) },
            valueRange = 1f..60f,
            steps = 59
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Sample Rate: $sampleRate Hz")
            Button(onClick = {
                val rates = listOf(8000, 16000, 44100, 48000)
                val nextIndex = rates.indexOf(sampleRate) + 1
                val nextRate = if (nextIndex < rates.size) rates[nextIndex] else rates[0]
                settingsViewModel.setSampleRate(nextRate)
            }) {
                Text("Change")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Channels")
            Switch(
                checked = channels == 2, // True for Stereo, False for Mono
                onCheckedChange = { isStereo: Boolean ->
                    settingsViewModel.setChannels(if (isStereo) 2 else 1)
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Bit Depth: $bitDepth-bit")
        Slider(
            value = bitDepth.toFloat(),
            onValueChange = { settingsViewModel.setBitDepth(it.toInt()) },
            valueRange = 16f..24f, // Assuming 16-bit and 24-bit as options
            steps = 1 // Only 16 and 24
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Auto-start service on boot")
            Switch(
                checked = autoStart,
                onCheckedChange = { settingsViewModel.setAutoStart(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Storage Quota: $storageQuota MB")
        Slider(
            value = storageQuota.toFloat(),
            onValueChange = { settingsViewModel.setStorageQuota(it.toInt()) },
            valueRange = 100f..1000f,
            steps = 9
        )

        Spacer(modifier = Modifier.height(8.dp)) // Smaller spacer

        // Storage usage indicator
        Text(text = "Used: $currentStorageUsageMb MB / $storageQuota MB")

        LinearProgressIndicator(
            progress = { currentStorageUsageMb.toFloat() / storageQuota.toFloat() },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Standard Recording Save Location", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = saveDirectoryUri?.let {
                    DocumentFile.fromTreeUri(LocalContext.current, Uri.parse(it))?.name
                } ?: "Default (App Cache)",
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { openDirectoryLauncher.launch(null) }) {
                Text("Select Folder")
            }
        }
        if (saveDirectoryUri != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { settingsViewModel.setSaveDirectoryUri(null) }) {
                Text("Reset to Default")
            }
        }
    }
}