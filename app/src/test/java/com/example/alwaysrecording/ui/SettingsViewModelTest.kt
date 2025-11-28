package com.example.alwaysrecording.ui

import android.app.Application
import com.example.alwaysrecording.data.settings.DataStoreSettingsRepository
import com.example.alwaysrecording.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.MockitoAnnotations.openMocks
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.doSuspendableAnswer
import java.io.File

@ExperimentalCoroutinesApi
class SettingsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Mock
    private lateinit var mockApplication: Application

    @Mock
    private lateinit var mockSettingsRepository: SettingsRepository

    @Mock
    private lateinit var mockFile: File

    private lateinit var viewModel: SettingsViewModel

    // Mock flows for settings repository
    private val bufferLengthFlow = MutableStateFlow(DataStoreSettingsRepository.DEFAULT_BUFFER_LENGTH_MINUTES)
    private val sampleRateFlow = MutableStateFlow(DataStoreSettingsRepository.DEFAULT_SAMPLE_RATE_HZ)
    private val autoStartFlow = MutableStateFlow(DataStoreSettingsRepository.DEFAULT_AUTO_START_ENABLED)
    private val storageQuotaFlow = MutableStateFlow(DataStoreSettingsRepository.DEFAULT_STORAGE_QUOTA_MB)
    private val channelsFlow = MutableStateFlow(DataStoreSettingsRepository.DEFAULT_CHANNELS)
    private val bitDepthFlow = MutableStateFlow(DataStoreSettingsRepository.DEFAULT_BIT_DEPTH)
    private val saveDirectoryUriFlow = MutableStateFlow(DataStoreSettingsRepository.DEFAULT_SAVE_DIRECTORY_URI)

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        // Configure mock SettingsRepository to return our mock flows
        whenever(mockSettingsRepository.bufferLengthMinutes).thenReturn(bufferLengthFlow)
        whenever(mockSettingsRepository.sampleRateHz).thenReturn(sampleRateFlow)
        whenever(mockSettingsRepository.autoStartEnabled).thenReturn(autoStartFlow)
        whenever(mockSettingsRepository.storageQuotaMb).thenReturn(storageQuotaFlow)
        whenever(mockSettingsRepository.channels).thenReturn(channelsFlow)
        whenever(mockSettingsRepository.bitDepth).thenReturn(bitDepthFlow)
        whenever(mockSettingsRepository.saveDirectoryUri).thenReturn(saveDirectoryUriFlow)

        // Configure mock SettingsRepository setters to update the corresponding flows
        runTest {
            whenever(mockSettingsRepository.setBufferLengthMinutes(any())).doSuspendableAnswer { invocation -> bufferLengthFlow.value = invocation.getArgument(0) as Int; Unit }
            whenever(mockSettingsRepository.setSampleRateHz(any())).doSuspendableAnswer { invocation -> sampleRateFlow.value = invocation.getArgument(0) as Int; Unit }
            whenever(mockSettingsRepository.setAutoStartEnabled(any())).doSuspendableAnswer { invocation -> autoStartFlow.value = invocation.getArgument(0) as Boolean; Unit }
            whenever(mockSettingsRepository.setStorageQuotaMb(any())).doSuspendableAnswer { invocation -> storageQuotaFlow.value = invocation.getArgument(0) as Int; Unit }
            whenever(mockSettingsRepository.setChannels(any())).doSuspendableAnswer { invocation -> channelsFlow.value = invocation.getArgument(0) as Int; Unit }
            whenever(mockSettingsRepository.setBitDepth(any())).doSuspendableAnswer { invocation -> bitDepthFlow.value = invocation.getArgument(0) as Int; Unit }
            whenever(mockSettingsRepository.setSaveDirectoryUri(any())).doSuspendableAnswer { invocation -> saveDirectoryUriFlow.value = invocation.getArgument(0) as String?; Unit }
        }

        // Mock the application context for getExternalFilesDir
        whenever(mockApplication.getExternalFilesDir(any())).thenReturn(mockFile)
        whenever(mockFile.exists()).thenReturn(true)

        viewModel = SettingsViewModel(mockApplication, mockSettingsRepository)
        // Manually inject the mock settings repository
        // This is a workaround as DataStoreSettingsRepository is directly instantiated in ViewModel
        // For proper testing, SettingsRepository should be injected via constructor.
        // For now, we'll use reflection or a test-specific setter if needed, or assume the mock is used.
        // A better approach would be to refactor SettingsViewModel to accept SettingsRepository as a constructor parameter.
        // For this test, we'll assume the mock is correctly used by the ViewModel.
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setBufferLength_updatesRepository() = runTest {
        val newLength = 15
        viewModel.setBufferLength(newLength)
        advanceUntilIdle()
        verify(mockSettingsRepository).setBufferLengthMinutes(newLength)
        assertEquals(newLength, bufferLengthFlow.first()) // Verify flow also updated
    }

    @Test
    fun setSampleRate_updatesRepository() = runTest {
        val newRate = 44100
        viewModel.setSampleRate(newRate)
        advanceUntilIdle()
        verify(mockSettingsRepository).setSampleRateHz(newRate)
        assertEquals(newRate, sampleRateFlow.first()) // Verify flow also updated
    }

    @Test
    fun setAutoStart_updatesRepository() = runTest {
        val enabled = true
        viewModel.setAutoStart(enabled)
        advanceUntilIdle()
        verify(mockSettingsRepository).setAutoStartEnabled(enabled)
        assertEquals(enabled, autoStartFlow.first()) // Verify flow also updated
    }

    @Test
    fun setStorageQuota_updatesRepository() = runTest {
        val newQuota = 750
        viewModel.setStorageQuota(newQuota)
        advanceUntilIdle()
        verify(mockSettingsRepository).setStorageQuotaMb(newQuota)
        assertEquals(newQuota, storageQuotaFlow.first()) // Verify flow also updated
    }

    

    @Test
    fun setChannels_updatesRepository() = runTest {
        val newChannels = 2
        viewModel.setChannels(newChannels)
        advanceUntilIdle()
        verify(mockSettingsRepository).setChannels(newChannels)
        assertEquals(newChannels, channelsFlow.first()) // Verify flow also updated
    }

    @Test
    fun setBitDepth_updatesRepository() = runTest {
        val newBitDepth = 24
        viewModel.setBitDepth(newBitDepth)
        advanceUntilIdle()
        verify(mockSettingsRepository).setBitDepth(newBitDepth)
        assertEquals(newBitDepth, bitDepthFlow.first()) // Verify flow also updated
    }

    @Test
    fun setSaveDirectoryUri_updatesRepository() = runTest {
        val newUri = "content://test/uri"
        viewModel.setSaveDirectoryUri(newUri)
        advanceUntilIdle()
        verify(mockSettingsRepository).setSaveDirectoryUri(newUri)
        assertEquals(newUri, saveDirectoryUriFlow.first()) // Verify flow also updated
    }

    @Test
    fun setSaveDirectoryUri_clearsRepositoryWithNull() = runTest {
        viewModel.setSaveDirectoryUri(null)
        advanceUntilIdle()
        verify(mockSettingsRepository).setSaveDirectoryUri(null)
        assertEquals(null, saveDirectoryUriFlow.first()) // Verify flow also updated
    }

    

    
}
