package com.example.alwaysrecording.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingTest {

    @Test
    fun recording_propertiesAreCorrect() {
        val recording = Recording(
            id = 1L,
            filename = "test.wav",
            timestamp = System.currentTimeMillis(),
            duration = 120000L, // 2 minutes
            size = 500000L, // 0.5 MB
            tags = listOf("meeting", "important")
        )

        assertEquals(1L, recording.id)
        assertEquals("test.wav", recording.filename)
        assertTrue(recording.timestamp > 0)
        assertEquals(120000L, recording.duration)
        assertEquals(500000L, recording.size)
        
        assertEquals(listOf("meeting", "important"), recording.tags)
    }

    @Test
    fun recording_defaultValuesAreCorrect() {
        val recording = Recording(
            id = 2L,
            filename = "default.wav",
            timestamp = System.currentTimeMillis(),
            duration = 0L,
            size = 0L
        )

        
        assertTrue(recording.tags.isEmpty())
    }

    @Test
    fun recording_equalityIsCorrect() {
        val timestamp = System.currentTimeMillis()
        val recording1 = Recording(1L, "file1.wav", timestamp, 1000L, 100L, listOf("tag1"))
        val recording2 = Recording(1L, "file1.wav", timestamp, 1000L, 100L, listOf("tag1"))
        val recording3 = Recording(2L, "file2.wav", timestamp, 2000L, 200L, listOf("tag2"))

        assertEquals(recording1, recording2)
        assertFalse(recording1 == recording3)
    }
}
