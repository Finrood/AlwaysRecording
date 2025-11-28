package com.example.alwaysrecording.ui

import android.app.Application
import android.media.MediaRecorder
import android.os.Build
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.alwaysrecording.data.recorder.MediaRecorderFactory
import com.example.alwaysrecording.data.settings.SettingsRepository
import com.example.alwaysrecording.data.storage.StorageChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class RecordingViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Mock
    private lateinit var mockApplication: Application

    @Mock
    private lateinit var mockSettingsRepository: SettingsRepository

    @Mock
    private lateinit var mockMediaRecorderFactory: MediaRecorderFactory

    @Mock
    private lateinit var mockStorageChecker: StorageChecker

    @Mock
    private lateinit var mockMediaRecorder: MediaRecorder

    private lateinit var viewModel: RecordingViewModel

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        // Mock Application context behavior
        whenever(mockApplication.applicationContext).thenReturn(mockApplication)
        whenever(mockApplication.externalCacheDir).thenReturn(File("mock_cache_dir"))
        whenever(mockApplication.getExternalFilesDir(any())).thenReturn(File("mock_music_dir"))

        // Mock SettingsRepository behavior
        whenever(mockSettingsRepository.saveDirectoryUri).thenReturn(MutableStateFlow(null))

        // Mock MediaRecorderFactory and StorageChecker
        whenever(mockMediaRecorderFactory.create(any(), any(), any())).thenReturn(mockMediaRecorder)
        whenever(mockStorageChecker.isStorageAvailable(any(), any())).thenReturn(true)

        viewModel = RecordingViewModel(mockApplication, mockMediaRecorderFactory, mockStorageChecker)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun startRecording_startsRecordingAndTimer() = runTest {
        viewModel.startRecording()
        testDispatcher.scheduler.runCurrent() // Execute initial coroutine start

        assertTrue(viewModel.isRecording.first())
        verify(mockMediaRecorder).prepare()
        verify(mockMediaRecorder).start()
        
        testDispatcher.scheduler.advanceTimeBy(1001) // Advance time to trigger timer update
        assertTrue(viewModel.elapsedTime.first() > 0)
        
        viewModel.stopRecording() // Cleanup to stop timer
    }

    @Test
    fun stopRecording_stopsRecordingAndTimer() = runTest {
        viewModel.startRecording()
        testDispatcher.scheduler.runCurrent()

        viewModel.stopRecording()
        testDispatcher.scheduler.runCurrent()

        assertFalse(viewModel.isRecording.first())
        verify(mockMediaRecorder).stop()
        verify(mockMediaRecorder).release()
        assertEquals(0L, viewModel.elapsedTime.first())
    }

    @Test
    fun pauseRecording_pausesRecordingAndTimer() = runTest {
        viewModel.startRecording()
        testDispatcher.scheduler.runCurrent()

        doNothing().`when`(mockMediaRecorder).pause()
        viewModel.pauseRecording()
        testDispatcher.scheduler.runCurrent()

        assertTrue(viewModel.isPaused.first())
        verify(mockMediaRecorder).pause()
        val initialElapsedTime = viewModel.elapsedTime.first()
        testDispatcher.scheduler.advanceTimeBy(5000) // Advance time by 5 seconds
        assertEquals(initialElapsedTime, viewModel.elapsedTime.first())

        viewModel.stopRecording() // Cleanup
    }

    @Test
    fun resumeRecording_resumesRecordingAndTimer() = runTest(testDispatcher) {
        try {
            viewModel.startRecording()
            testDispatcher.scheduler.runCurrent()

            doNothing().`when`(mockMediaRecorder).pause()
            doNothing().`when`(mockMediaRecorder).resume()

            viewModel.pauseRecording()
            testDispatcher.scheduler.runCurrent()

            viewModel.resumeRecording()
            testDispatcher.scheduler.runCurrent()

            assertFalse(viewModel.isPaused.first())
            verify(mockMediaRecorder).resume()
            val initialElapsedTime = viewModel.elapsedTime.first()
            testDispatcher.scheduler.advanceTimeBy(5000) // Advance time by 5 seconds
            assertTrue(viewModel.elapsedTime.first() > initialElapsedTime)
        } finally {
            viewModel.stopRecording() // Cleanup
        }
    }

    @Test
    fun startRecording_notEnoughStorage_emitsSnackbarError() = runTest {
        whenever(mockStorageChecker.isStorageAvailable(any(), any())).thenReturn(false)

        viewModel.startRecording()
        testDispatcher.scheduler.runCurrent()

        assertEquals(UiError.Snackbar("Not enough storage available."), viewModel.error.first())
    }

    @Test
    fun setSelectedFormat_updatesSelectedFormat() = runTest {
        viewModel.setSelectedFormat(RecordingFormat.THREE_GPP)
        assertEquals(RecordingFormat.THREE_GPP, viewModel.selectedFormat.first())
    }

    @Test
    fun setUserDefinedFilename_updatesFilename() = runTest {
        val filename = "MyCustomRecording"
        viewModel.setUserDefinedFilename(filename)
        assertEquals(filename, viewModel.userDefinedFilename.first())
    }

    // Additional tests for SAF saving logic would require instrumentation tests
    // as they involve Android's ContentResolver and DocumentFile APIs.
}