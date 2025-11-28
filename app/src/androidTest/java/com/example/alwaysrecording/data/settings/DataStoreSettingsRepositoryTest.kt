package com.example.alwaysrecording.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreSettingsRepositoryTest {

    private lateinit var settingsRepository: DataStoreSettingsRepository
    private lateinit var dataStore: DataStore<Preferences>
    private val testContext: Context = ApplicationProvider.getApplicationContext()
    private val testCoroutineDispatcher = UnconfinedTestDispatcher()
    private val testCoroutineScope = TestScope(testCoroutineDispatcher + Job())

    @Before
    fun setup() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = testCoroutineScope,
            produceFile = { testContext.preferencesDataStoreFile("test_settings") }
        )
        // Manually inject the test DataStore instance
        // This requires modifying the DataStoreSettingsRepository to accept a DataStore in its constructor
        // For now, we'll rely on the extension property, but for proper testing, DI is better.
        // For this test, we'll clear the file before each test.
        File(testContext.filesDir, "datastore/test_settings.preferences_pb").deleteRecursively()

        settingsRepository = DataStoreSettingsRepository(testContext)
    }

    @After
    fun teardown() {
        File(testContext.filesDir, "datastore/test_settings.preferences_pb").deleteRecursively()
    }

    @Test
    fun bufferLengthMinutes_defaultIsCorrect() = testCoroutineScope.runTest {
        assertEquals(DataStoreSettingsRepository.DEFAULT_BUFFER_LENGTH_MINUTES, settingsRepository.bufferLengthMinutes.first())
    }

    @Test
    fun setBufferLengthMinutes_updatesValue() = testCoroutineScope.runTest {
        val newLength = 10
        settingsRepository.setBufferLengthMinutes(newLength)
        assertEquals(newLength, settingsRepository.bufferLengthMinutes.first())
    }

    @Test
    fun sampleRateHz_defaultIsCorrect() = testCoroutineScope.runTest {
        assertEquals(DataStoreSettingsRepository.DEFAULT_SAMPLE_RATE_HZ, settingsRepository.sampleRateHz.first())
    }

    @Test
    fun setSampleRateHz_updatesValue() = testCoroutineScope.runTest {
        val newRate = 44100
        settingsRepository.setSampleRateHz(newRate)
        assertEquals(newRate, settingsRepository.sampleRateHz.first())
    }

    @Test
    fun autoStartEnabled_defaultIsCorrect() = testCoroutineScope.runTest {
        assertEquals(DataStoreSettingsRepository.DEFAULT_AUTO_START_ENABLED, settingsRepository.autoStartEnabled.first())
    }

    @Test
    fun setAutoStartEnabled_updatesValue() = testCoroutineScope.runTest {
        val newValue = true
        settingsRepository.setAutoStartEnabled(newValue)
        assertEquals(newValue, settingsRepository.autoStartEnabled.first())
    }

    @Test
    fun storageQuotaMb_defaultIsCorrect() = testCoroutineScope.runTest {
        assertEquals(DataStoreSettingsRepository.DEFAULT_STORAGE_QUOTA_MB, settingsRepository.storageQuotaMb.first())
    }

    @Test
    fun setStorageQuotaMb_updatesValue() = testCoroutineScope.runTest {
        val newQuota = 500
        settingsRepository.setStorageQuotaMb(newQuota)
        assertEquals(newQuota, settingsRepository.storageQuotaMb.first())
    }

    

    @Test
    fun channels_defaultIsCorrect() = testCoroutineScope.runTest {
        assertEquals(DataStoreSettingsRepository.DEFAULT_CHANNELS, settingsRepository.channels.first())
    }

    @Test
    fun setChannels_updatesValue() = testCoroutineScope.runTest {
        val newChannels = 2
        settingsRepository.setChannels(newChannels)
        assertEquals(newChannels, settingsRepository.channels.first())
    }

    @Test
    fun bitDepth_defaultIsCorrect() = testCoroutineScope.runTest {
        assertEquals(DataStoreSettingsRepository.DEFAULT_BIT_DEPTH, settingsRepository.bitDepth.first())
    }

    @Test
    fun setBitDepth_updatesValue() = testCoroutineScope.runTest {
        val newBitDepth = 24
        settingsRepository.setBitDepth(newBitDepth)
        assertEquals(newBitDepth, settingsRepository.bitDepth.first())
    }

    @Test
    fun saveDirectoryUri_defaultIsNull() = testCoroutineScope.runTest {
        assertEquals(DataStoreSettingsRepository.DEFAULT_SAVE_DIRECTORY_URI, settingsRepository.saveDirectoryUri.first())
    }

    @Test
    fun setSaveDirectoryUri_updatesValue() = testCoroutineScope.runTest {
        val newUri = "content://com.android.externalstorage.documents/tree/primary%3ADCIM"
        settingsRepository.setSaveDirectoryUri(newUri)
        assertEquals(newUri, settingsRepository.saveDirectoryUri.first())
    }

    @Test
    fun setSaveDirectoryUri_clearsValueWithNull() = testCoroutineScope.runTest {
        val initialUri = "content://some/uri"
        settingsRepository.setSaveDirectoryUri(initialUri)
        assertEquals(initialUri, settingsRepository.saveDirectoryUri.first())

        settingsRepository.setSaveDirectoryUri(null)
        assertEquals(null, settingsRepository.saveDirectoryUri.first())
    }

    

    
}
