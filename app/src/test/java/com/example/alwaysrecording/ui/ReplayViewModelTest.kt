package com.example.alwaysrecording.ui

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.alwaysrecording.data.service.ReplayRecorderService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.whenever
import org.junit.Assert.assertEquals
import org.robolectric.RobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class ReplayViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Mock
    private lateinit var mockApplication: Application

    private lateinit var viewModel: ReplayViewModel

    private val ACTION_START_RECORDING_LOCAL = "com.example.alwaysrecording.action.START_RECORDING"
    private val ACTION_STOP_RECORDING_LOCAL = "com.example.alwaysrecording.action.STOP_RECORDING"
    private val ACTION_SAVE_REPLAY_LOCAL = "com.example.alwaysrecording.action.SAVE_REPLAY"

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        // Mock application context behavior for startService
        whenever(mockApplication.startService(any())).thenReturn(ComponentName("com.example.alwaysrecording", "ReplayRecorderService"))

        viewModel = ReplayViewModel(mockApplication)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun startReplayService_sendsStartIntent() {
        viewModel.startReplayService()

        val intentCaptor = argumentCaptor<Intent>()
        verify(mockApplication).startService(intentCaptor.capture())

        assertEquals(ACTION_START_RECORDING_LOCAL, intentCaptor.firstValue.action)
        assertEquals(ReplayRecorderService::class.java.name, intentCaptor.firstValue.component?.className)
    }

    @Test
    fun stopReplayService_sendsStopIntent() {
        viewModel.stopReplayService()

        val intentCaptor = argumentCaptor<Intent>()
        verify(mockApplication).startService(intentCaptor.capture())

        assertEquals(ACTION_STOP_RECORDING_LOCAL, intentCaptor.firstValue.action)
        assertEquals(ReplayRecorderService::class.java.name, intentCaptor.firstValue.component?.className)
    }

    @Test
    fun saveReplay_sendsSaveReplayIntent() {
        viewModel.saveReplay()

        val intentCaptor = argumentCaptor<Intent>()
        verify(mockApplication).startService(intentCaptor.capture())

        assertEquals(ACTION_SAVE_REPLAY_LOCAL, intentCaptor.firstValue.action)
        assertEquals(ReplayRecorderService::class.java.name, intentCaptor.firstValue.component?.className)
    }

    // Testing recorderState flow directly is difficult due to it being a static member
    // of ReplayRecorderService. This would typically require PowerMock or refactoring
    // ReplayRecorderService to allow injecting a mockable state flow.
    // For now, we focus on verifying the ViewModel's interaction with the Android system (Intents).
}