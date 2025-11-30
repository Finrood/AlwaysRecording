package com.example.alwaysrecording.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.alwaysrecording.ui.theme.AlwaysRecordingTheme

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AlwaysRecordingTheme {
                val navController = rememberNavController()
                val snackbarHostState = remember { SnackbarHostState() }
                val recordingViewModel: RecordingViewModel = viewModel()
                val error by recordingViewModel.error.collectAsState()
                var showErrorDialog by remember { mutableStateOf<UiError.Dialog?>(null) }

                LaunchedEffect(error) {
                    error?.let {
                        when (it) {
                            is UiError.Snackbar -> {
                                snackbarHostState.showSnackbar(it.message)
                                recordingViewModel.clearError()
                            }
                            is UiError.Toast -> {
                                android.widget.Toast.makeText(this@MainActivity, it.message, android.widget.Toast.LENGTH_SHORT).show()
                                recordingViewModel.clearError()
                            }
                            is UiError.Dialog -> showErrorDialog = it
                        }
                    }
                }

                if (showErrorDialog != null) {
                    AlertDialog(
                        onDismissRequest = { 
                            showErrorDialog = null
                            recordingViewModel.clearError()
                        },
                        title = { Text(showErrorDialog!!.title) },
                        text = { Text(showErrorDialog!!.message) },
                        confirmButton = {
                            TextButton(onClick = { 
                                showErrorDialog = null
                                recordingViewModel.clearError()
                            }) {
                                Text("OK")
                            }
                        }
                    )
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Always Recording") },
                            actions = {
                                IconButton(onClick = { navController.navigate("settings") }) {
                                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                                }
                            }
                        )
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = "main",
                        modifier = Modifier.padding(it)
                    ) {
                        composable("main") { MainScreen(navController = navController, recordingViewModel = recordingViewModel) }
                        composable("settings") { SettingsScreen() }
                        composable("files") { FileListScreen(navController = navController) }
                        composable(
                            "recording_detail/{filename}",
                            arguments = listOf(navArgument("filename") { type = NavType.StringType })
                        ) {
                            RecordingDetailScreen(
                                navController = navController,
                                filename = it.arguments?.getString("filename")
                            )
                        }
                    }
                }
            }
        }
    }
}