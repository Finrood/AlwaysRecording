package com.example.alwaysrecording.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AppClockTest {

    @Test
    fun systemClock_now_returnsCurrentTimeMillis() {
        val clock = SystemClock()
        val currentTime = System.currentTimeMillis()
        val clockTime = clock.now()
        // Allow for a small difference due to execution time
        assertTrue("Time should be close to current system time", Math.abs(currentTime - clockTime) < 100)
    }

    @Test
    fun systemClock_formattedNow_returnsCorrectFormat() {
        val clock = SystemClock()
        val pattern = "yyyy-MM-dd HH:mm:ss"
        val expectedFormat = LocalDateTime.now(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern(pattern))
        val actualFormat = clock.formattedNow(pattern)
        // Due to potential time difference between test execution and LocalDateTime.now(),
        // we'll check if the format is correct and the date part matches.
        // A more robust test would involve mocking the clock.
        assertEquals("Formatted date should match pattern", expectedFormat.length, actualFormat.length)
        assertEquals("Formatted date part should match", expectedFormat.substring(0, 10), actualFormat.substring(0, 10))
    }
}
