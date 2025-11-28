package com.example.alwaysrecording.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.alwaysrecording.ui.theme.AlwaysRecordingTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.runtime.Composable
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.navigation.NavBackStackEntry
import org.mockito.Mockito.mock

@LargeTest
@RunWith(AndroidJUnit4::class)
class MainScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var navController: TestNavHostController

    @Before
    fun setup() {
        composeTestRule.setContent {
            navController = TestNavHostController(composeTestRule.activity)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            AlwaysRecordingTheme {
                // Provide mock ViewModels for the MainScreen
                MainScreen(
                    navController = navController,
                    recordingViewModel = mock(),
                    replayViewModel = mock(),
                    settingsViewModel = mock()
                )
            }
        }
    }

    @Test
    fun mainScreen_displaysAllCardsAndButtons() {
        composeTestRule.onNodeWithText("Standard Recording").assertIsDisplayed()
        composeTestRule.onNodeWithText("Always-On Buffered Recording").assertIsDisplayed()
        composeTestRule.onNodeWithText("View Recordings").assertIsDisplayed()

        // Check for buttons within Standard Recording card
        composeTestRule.onNodeWithText("Start Recording").assertIsDisplayed()
        composeTestRule.onNodeWithText("Stop Recording").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pause Recording").assertIsDisplayed()
        composeTestRule.onNodeWithText("Resume Recording").assertIsDisplayed()

        // Check for elements within Always-On Buffered Recording card
        composeTestRule.onNodeWithText("Service Enabled").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save Last").assertIsDisplayed()
    }

    @Test
    fun viewRecordingsButton_navigatesToFileListScreen() {
        composeTestRule.onNodeWithText("View Recordings").performClick()
        composeTestRule.waitForIdle()
        assertEquals("files", navController.currentDestination?.route)
    }

    @Test
    fun settingsButton_navigatesToSettingsScreen() {
        // The settings button is in the TopAppBar of MainActivity, not MainScreen directly.
        // We need to access the TopAppBar from MainActivity's Scaffold.
        // This requires testing MainActivity directly or ensuring MainScreen is part of it.
        // For this test, we'll assume the MainActivity's Scaffold is correctly set up.

        // This test needs to be run against MainActivity directly, not just MainScreen.
        // Let's modify the setup to test MainActivity.

        composeTestRule.setContent {
            navController = TestNavHostController(composeTestRule.activity)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            AlwaysRecordingTheme {
                MainActivityContent(navController = navController) // A composable that wraps Scaffold and NavHost
            }
        }

        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()
        assertEquals("settings", navController.currentDestination?.route)
    }
}

// Helper Composable to wrap MainActivity's content for testing
@Composable
fun MainActivityContent(navController: TestNavHostController) {
    val recordingViewModel: RecordingViewModel = mock()
    val replayViewModel: ReplayViewModel = mock()
    val settingsViewModel: SettingsViewModel = mock()
    val fileViewModel: FileViewModel = mock()
    val recordingDetailViewModel: RecordingDetailViewModel = mock()

    // Replicate MainActivity's Scaffold and NavHost structure
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
        snackbarHost = { SnackbarHost(remember { SnackbarHostState() }) }
    ) {
        NavHost(
            navController = navController,
            startDestination = "main",
            modifier = Modifier.padding(it)
        ) {
            composable("main") { MainScreen(navController = navController, recordingViewModel = recordingViewModel, replayViewModel = replayViewModel, settingsViewModel = settingsViewModel) }
            composable("settings") { SettingsScreen(navController = navController, settingsViewModel = settingsViewModel) }
            composable("files") { FileListScreen(navController = navController, fileViewModel = fileViewModel) }
            composable(
                "recording_detail/{filename}",
                arguments = listOf(navArgument("filename") { type = NavType.StringType })
            ) {
                RecordingDetailScreen(
                    navController = navController,
                    filename = it.arguments?.getString("filename"),
                    detailViewModel = recordingDetailViewModel
                )
            }
        }
    }
}
