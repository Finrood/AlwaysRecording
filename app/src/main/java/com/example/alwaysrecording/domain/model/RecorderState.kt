package com.example.alwaysrecording.domain.model

/**
 * Represents the possible states of the audio recorder.
 */
sealed class RecorderState {
    object Idle : RecorderState() {
        override fun toString(): String = "Idle"
    }
    object Recording : RecorderState() {
        override fun toString(): String = "Recording"
    }
    object SavingReplay : RecorderState() {
        override fun toString(): String = "SavingReplay"
    }
    data class Error(val message: String, val cause: Throwable? = null) : RecorderState()
    object MicBusy : RecorderState() {
        override fun toString(): String = "MicBusy"
    }
    object PermissionNeeded : RecorderState() {
        override fun toString(): String = "PermissionNeeded"
    }
}