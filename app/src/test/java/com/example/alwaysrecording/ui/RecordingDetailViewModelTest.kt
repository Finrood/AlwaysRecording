package com.example.alwaysrecording.ui

import android.app.Application
import android.media.MediaPlayer
import android.os.Build
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.MockitoAnnotations.openMocks
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

@ExperimentalCoroutinesApi
class RecordingDetailViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Mock
    private lateinit var mockApplication: Application

    @Mock
    private lateinit var mockMediaPlayer: MediaPlayer

    @Mock
    private lateinit var mockPlaybackParams: android.media.PlaybackParams

    private lateinit var viewModel: RecordingDetailViewModel

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        // Mock Application context behavior
        whenever(mockApplication.applicationContext).thenReturn(mockApplication)
        val mockExternalFilesDir = File("mock_external_files_dir")
        mockExternalFilesDir.mkdirs()
                                        whenever(mockApplication.getExternalFilesDir(any())).thenReturn(mockExternalFilesDir)

        // Mock MediaPlayer behavior
        doNothing().whenever(mockMediaPlayer).setDataSource(any<String>())
        doNothing().whenever(mockMediaPlayer).prepare()
        doNothing().whenever(mockMediaPlayer).start()
        doNothing().whenever(mockMediaPlayer).pause()
        doNothing().whenever(mockMediaPlayer).seekTo(any<Int>())
        doNothing().whenever(mockMediaPlayer).release()
        whenever(mockMediaPlayer.duration).thenReturn(120000) // 2 minutes
        whenever(mockMediaPlayer.currentPosition).thenReturn(0)
        whenever(mockMediaPlayer.playbackParams).thenReturn(mockPlaybackParams)
        whenever(mockPlaybackParams.setSpeed(any())).thenReturn(mockPlaybackParams)

        viewModel = RecordingDetailViewModel(mockApplication)
        // Manually inject the mock MediaPlayer for testing purposes
        // This requires a slight modification to RecordingDetailViewModel to allow setting MediaPlayer for tests.
        // For now, we'll simulate the internal creation.
        // This is a limitation of direct mocking without PowerMock or a factory pattern.
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        File("mock_external_files_dir").deleteRecursively()
    }

    @Test
    fun initPlayer_initializesMediaPlayerAndDuration() = runTest {
        val filename = "test_recording.wav"
        val testFile = File(mockApplication.getExternalFilesDir(null), filename)
        testFile.createNewFile()

        // Simulate MediaPlayer creation and injection
        viewModel.mediaPlayer = mockMediaPlayer

        viewModel.initPlayer(filename)
        testDispatcher.scheduler.runCurrent() // Execute immediately available coroutines

        verify(mockMediaPlayer).setDataSource(testFile.absolutePath)
        verify(mockMediaPlayer).prepare()
        assertEquals(120000, viewModel.totalDuration.first())

        viewModel.onCleared() // Cleanup
    }

    @Test
    fun play_startsPlaybackAndTimer() = runTest {
        val filename = "test_recording.wav"
        val testFile = File(mockApplication.getExternalFilesDir(null), filename)
        testFile.createNewFile()
        viewModel.mediaPlayer = mockMediaPlayer // Inject mock BEFORE initPlayer
        viewModel.initPlayer(filename)

        viewModel.play()
        testDispatcher.scheduler.advanceTimeBy(150) // Advance time to trigger timer update

        assertTrue(viewModel.isPlaying.first())
        verify(mockMediaPlayer).start()
        assertTrue(viewModel.currentPosition.first() >= 0)

        viewModel.onCleared() // Ensure timer is stopped
        testDispatcher.scheduler.runCurrent() // Clean up any remaining coroutines
    }

    @Test
    fun pause_pausesPlaybackAndTimer() = runTest {
        val filename = "test_recording.wav"
        val testFile = File(mockApplication.getExternalFilesDir(null), filename)
        testFile.createNewFile()
        viewModel.mediaPlayer = mockMediaPlayer
        viewModel.initPlayer(filename)
        viewModel.play()
        testDispatcher.scheduler.advanceTimeBy(150) // Simulate some playback

        viewModel.pause()
        testDispatcher.scheduler.runCurrent()

        assertFalse(viewModel.isPlaying.first())
        verify(mockMediaPlayer).pause()

        viewModel.onCleared() // Cleanup
    }

    @Test
    fun seekTo_seeksMediaPlayerAndUpdatesPosition() = runTest {
        val filename = "test_recording.wav"
        val testFile = File(mockApplication.getExternalFilesDir(null), filename)
        testFile.createNewFile()
        viewModel.mediaPlayer = mockMediaPlayer
        viewModel.initPlayer(filename)

        val seekPosition = 30000 // 30 seconds
        viewModel.seekTo(seekPosition)
        testDispatcher.scheduler.runCurrent()

        verify(mockMediaPlayer).seekTo(seekPosition)
        assertEquals(seekPosition, viewModel.currentPosition.first())

        viewModel.onCleared() // Cleanup
    }

    @Test
    fun setPlaybackSpeed_updatesSpeedAndMediaPlayer() = runTest {
        val filename = "test_recording.wav"
        val testFile = File(mockApplication.getExternalFilesDir(null), filename)
        testFile.createNewFile()
        viewModel.mediaPlayer = mockMediaPlayer
        viewModel.initPlayer(filename)

        val newSpeed = 1.5f
        viewModel.setPlaybackSpeed(newSpeed)
        testDispatcher.scheduler.runCurrent()

        assertEquals(newSpeed, viewModel.playbackSpeed.first())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            verify(mockMediaPlayer).playbackParams = any() // Verify playbackParams is set
        }

        viewModel.onCleared() // Cleanup
    }

    @Test
    fun onCleared_releasesMediaPlayerAndCancelsTimer() = runTest {
        val filename = "test_recording.wav"
        val testFile = File(mockApplication.getExternalFilesDir(null), filename)
        testFile.createNewFile()
        viewModel.mediaPlayer = mockMediaPlayer
        viewModel.initPlayer(filename)
        viewModel.play()
        testDispatcher.scheduler.advanceTimeBy(150) // Simulate some playback

        viewModel.onCleared()
        testDispatcher.scheduler.runCurrent()

        verify(mockMediaPlayer).release()
        assertFalse(viewModel.isPlaying.first()) // Should be false after clear
    }
}
