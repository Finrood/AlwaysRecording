package com.example.alwaysrecording.domain.model

data class Recording(
    val id: Long,
    val filename: String,
    val timestamp: Long,
    val duration: Long,
    val size: Long,
    val tags: List<String> = emptyList() // New field
)
