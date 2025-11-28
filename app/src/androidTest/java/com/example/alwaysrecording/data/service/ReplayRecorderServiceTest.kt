package com.example.alwaysrecording.data.service

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.ServiceTestRule
import com.example.alwaysrecording.data.buffer.AudioRingBuffer
import com.example.alwaysrecording.data.settings.DataStoreSettingsRepository
import com.example.alwaysrecording.domain.model.RecorderState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import android.app.ActivityManager

@LargeTest
@RunWith(AndroidJUnit4::class)
class ReplayRecorderServiceTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    private lateinit var context: Context
    private lateinit var settingsRepository: DataStoreSettingsRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        settingsRepository = DataStoreSettingsRepository(context)
        // Clear DataStore before each test to ensure a clean state
        runBlocking {
            settingsRepository.setBufferLengthMinutes(DataStoreSettingsRepository.DEFAULT_BUFFER_LENGTH_MINUTES)
            settingsRepository.setSampleRateHz(DataStoreSettingsRepository.DEFAULT_SAMPLE_RATE_HZ)
            settingsRepository.setAutoStartEnabled(DataStoreSettingsRepository.DEFAULT_AUTO_START_ENABLED)
            settingsRepository.setStorageQuotaMb(DataStoreSettingsRepository.DEFAULT_STORAGE_QUOTA_MB)
            settingsRepository.setChannels(DataStoreSettingsRepository.DEFAULT_CHANNELS)
            settingsRepository.setBitDepth(DataStoreSettingsRepository.DEFAULT_BIT_DEPTH)
            settingsRepository.setSaveDirectoryUri(DataStoreSettingsRepository.DEFAULT_SAVE_DIRECTORY_URI)
        }
    }

    @After
    fun tearDown() {
        // Ensure service is stopped after each test
        val stopIntent = ReplayRecorderService.newStopIntent(context)
        context.startService(stopIntent)
        // Clean up any created files
        val storageDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
        storageDir?.listFiles()?.forEach { it.delete() }
    }

    @Test
    fun serviceStartsAndStopsRecording() = runBlocking {
        val startIntent = ReplayRecorderService.newStartIntent(context)
        serviceRule.startService(startIntent)

        // Wait for service to transition to Recording state
        serviceRule.waitForServiceToStart(10, TimeUnit.SECONDS)
        assertTrue(ReplayRecorderService.publicServiceState.first() is RecorderState.Recording)

        val stopIntent = ReplayRecorderService.newStopIntent(context)
        context.startService(stopIntent)

        // Wait for service to transition to Idle state
        serviceRule.waitForServiceToStop(10, TimeUnit.SECONDS)
        assertTrue(ReplayRecorderService.publicServiceState.first() is RecorderState.Idle)
    }

    @Test
    fun serviceSavesReplay() = runBlocking {
        // Set a short buffer length for faster testing
        settingsRepository.setBufferLengthMinutes(1)
        settingsRepository.setSampleRateHz(8000) // Lower sample rate for smaller data

        val startIntent = ReplayRecorderService.newStartIntent(context)
        serviceRule.startService(startIntent)
        serviceRule.waitForServiceToStart(10, TimeUnit.SECONDS)

        // Let it record for a few seconds to fill buffer
        kotlinx.coroutines.delay(5000) // Record for 5 seconds

        val saveIntent = ReplayRecorderService.newSaveReplayIntent(context)
        context.startService(saveIntent)

        // Wait for saving to complete
        serviceRule.waitForServiceToReachState(RecorderState.SavingReplay::class.java, 10, TimeUnit.SECONDS)
        serviceRule.waitForServiceToReachState(RecorderState.Recording::class.java, 10, TimeUnit.SECONDS) // Should return to recording

        val storageDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
        val savedFiles = storageDir?.listFiles { file -> file.name.endsWith(".wav") }
        assertTrue(savedFiles?.isNotEmpty() == true)
        // Basic check for file size (should be non-zero)
        assertTrue(savedFiles?.first()?.length() ?: 0 > 0)
    }

    @Test
    fun serviceHandlesLowMemory() = runBlocking {
        // This test is difficult to simulate accurately without root or specific Android APIs
        // that allow triggering onTrimMemory with specific levels.
        // We can only test the logic if onTrimMemory is called.

        // For now, this test will primarily verify that the service doesn't crash
        // and attempts to save/adjust settings if onTrimMemory is called with critical levels.

        // Start service
        val startIntent = ReplayRecorderService.newStartIntent(context)
        serviceRule.startService(startIntent)
        serviceRule.waitForServiceToStart(10, TimeUnit.SECONDS)

        // Simulate onTrimMemory call (this is a conceptual simulation, not actual system call)
        val service = serviceRule.bindService(startIntent) as ReplayRecorderService
        service.onTrimMemory(ActivityManager.TRIM_MEMORY_RUNNING_CRITICAL)

        // Verify state transition and settings adjustment
        serviceRule.waitForServiceToReachState(RecorderState.Error::class.java, 10, TimeUnit.SECONDS)
        assertTrue(ReplayRecorderService.publicServiceState.first() is RecorderState.Error)
        // Verify buffer length was halved (if it was > 1)
        val currentBufferLength = settingsRepository.bufferLengthMinutes.first()
        assertTrue(currentBufferLength < DataStoreSettingsRepository.DEFAULT_BUFFER_LENGTH_MINUTES)
    }

    // Helper function to wait for service state (requires access to internal state or public flow)
    private suspend fun ServiceTestRule.waitForServiceToReachState(stateClass: Class<out RecorderState>, timeout: Long, unit: TimeUnit) {
        val endTime = System.currentTimeMillis() + unit.toMillis(timeout)
        while (System.currentTimeMillis() < endTime) {
            if (stateClass.isInstance(ReplayRecorderService.publicServiceState.first())) {
                return
            }
            kotlinx.coroutines.delay(100) // Check every 100ms
        }
        throw AssertionError("Service did not reach state ${stateClass.simpleName} within timeout")
    }

    private suspend fun ServiceTestRule.waitForServiceToStart(timeout: Long, unit: TimeUnit) {
        val endTime = System.currentTimeMillis() + unit.toMillis(timeout)
        while (System.currentTimeMillis() < endTime) {
            if (ReplayRecorderService.publicServiceState.first() is RecorderState.Recording) {
                return
            }
            kotlinx.coroutines.delay(100) // Check every 100ms
        }
        throw AssertionError("Service did not start within timeout")
    }

    private suspend fun ServiceTestRule.waitForServiceToStop(timeout: Long, unit: TimeUnit) {
        val endTime = System.currentTimeMillis() + unit.toMillis(timeout)
        while (System.currentTimeMillis() < endTime) {
            if (ReplayRecorderService.publicServiceState.first() is RecorderState.Idle) {
                return
            }
            kotlinx.coroutines.delay(100) // Check every 100ms
        }
        throw AssertionError("Service did not stop within timeout")
    }
}
