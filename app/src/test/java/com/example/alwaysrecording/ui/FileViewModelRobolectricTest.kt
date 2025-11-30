package com.example.alwaysrecording.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.alwaysrecording.data.settings.SettingsRepository
import com.example.alwaysrecording.domain.model.Recording
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.robolectric.Shadows
import java.io.File

import androidx.core.content.FileProvider

@RunWith(AndroidJUnit4::class)
class FileViewModelRobolectricTest {

    private lateinit var context: Context
    private lateinit var viewModel: FileViewModel

    @Mock
    private lateinit var mockSettingsRepository: SettingsRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        context = ApplicationProvider.getApplicationContext()
        viewModel = FileViewModel(context as Application, mockSettingsRepository)
    }

    @Test
    fun `openFile launches correct intent`() {
        // 1. Arrange
        // Create a dummy file in the expected directory
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        // Ensure directory exists
        storageDir?.mkdirs()
        
        val testFile = File(storageDir, "test_recording.wav")
        testFile.createNewFile()

        // Generate a valid URI for the test file
        val fileUri = FileProvider.getUriForFile(
            context,
            "com.example.alwaysrecording.provider",
            testFile
        )

        val recording = Recording(
            id = 1L,
            filename = "test_recording.wav",
            timestamp = System.currentTimeMillis(),
            duration = 1000L,
            size = 1024L,
            fileUri = fileUri.toString()
        )

        // 2. Act
        viewModel.openFile(recording)

        // 3. Assert
        val expectedIntent = Shadows.shadowOf(context as Application).nextStartedActivity
        assertNotNull("Intent should not be null", expectedIntent)
        assertEquals(Intent.ACTION_VIEW, expectedIntent.action)
        // The type might be audio/wav or audio/* depending on implementation. 
        // New impl uses audio/*, test expected audio/wav. Let's check implementation.
        // Implementation: setDataAndType(uri, "audio/*")
        assertEquals("audio/*", expectedIntent.type)
        assertEquals(fileUri, expectedIntent.data)
        
        // Verify flags
        // Note: We check if the required flags are set. The intent might have other flags.
        val flags = expectedIntent.flags
        val expectedFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        assertEquals(expectedFlags, flags and expectedFlags)
    }
}
