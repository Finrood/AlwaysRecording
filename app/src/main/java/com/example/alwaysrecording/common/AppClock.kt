package com.example.alwaysrecording.common

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Interface for providing the current time. Useful for testing.
 */
interface AppClock {
    fun now(): Long // Milliseconds since epoch
    fun formattedNow(pattern: String = "yyyyMMdd_HHmmss"): String
}

class SystemClock : AppClock {
    override fun now(): Long = System.currentTimeMillis()

    override fun formattedNow(pattern: String): String {
        return LocalDateTime.now(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern(pattern))
    }
}