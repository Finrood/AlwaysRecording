package com.example.alwaysrecording.ui

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.alwaysrecording.data.settings.SettingsRepository
import com.example.alwaysrecording.domain.model.Recording
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
@ExperimentalCoroutinesApi
class FileViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Mock
    private lateinit var mockApplication: Application

    @Mock
    private lateinit var mockSettingsRepository: SettingsRepository

    private lateinit var viewModel: FileViewModel

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        // Pass the mock factory to the constructor
        viewModel = FileViewModel(mockApplication, mockSettingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setSearchQuery filters recordings`() = runTest {
        // 1. Arrange: Directly set the data in the ViewModel
        val allRecordings = listOf(
            Recording(1L, "meeting-notes.wav", 1000L, 120000L, 1L),
            Recording(2L, "lecture-audio.mp3", 2000L, 180000L, 2L),
            Recording(3L, "important-meeting.wav", 3000L, 60000L, 3L)
        )
        viewModel._recordings.value = allRecordings
        advanceUntilIdle()

        // 2. Act: Set the search query
        viewModel.setSearchQuery("meeting")
        advanceUntilIdle()

        // 3. Assert: Check if the filtered list is correct
        val filtered = viewModel.filteredRecordings.first()
        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.filename == "meeting-notes.wav" })
        assertTrue(filtered.any { it.filename == "important-meeting.wav" })
        assertFalse(filtered.any { it.filename == "lecture-audio.mp3" })
    }

    @Test
    fun `setSearchQuery with empty query shows all`() = runTest {
        // 1. Arrange
        val allRecordings = listOf(
            Recording(1L, "meeting.wav", 1000L, 120000L, 1L),
            Recording(2L, "lecture.mp3", 2000L, 180000L, 2L)
        )
        viewModel._recordings.value = allRecordings
        advanceUntilIdle()

        // 2. Act
        viewModel.setSearchQuery("")
        advanceUntilIdle()

        // 3. Assert
        val filtered = viewModel.filteredRecordings.first()
        assertEquals(2, filtered.size)
    }

    @Test
    fun `setSearchQuery with no matches returns empty list`() = runTest {
        // 1. Arrange
        val allRecordings = listOf(
            Recording(1L, "meeting.wav", 1000L, 120000L, 1L),
            Recording(2L, "lecture.mp3", 2000L, 180000L, 2L)
        )
        viewModel._recordings.value = allRecordings
        advanceUntilIdle()

        // 2. Act
        viewModel.setSearchQuery("project-x")
        advanceUntilIdle()

        // 3. Assert
        val filtered = viewModel.filteredRecordings.first()
        assertEquals(0, filtered.size)
    }
}
