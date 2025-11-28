package com.example.alwaysrecording.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RecorderStateTest {

    @Test
    fun idleState_isCorrect() {
        val state = RecorderState.Idle
        assertTrue(state is RecorderState.Idle)
        assertEquals("Idle", state.toString())
    }

    @Test
    fun recordingState_isCorrect() {
        val state = RecorderState.Recording
        assertTrue(state is RecorderState.Recording)
        assertEquals("Recording", state.toString())
    }

    @Test
    fun savingReplayState_isCorrect() {
        val state = RecorderState.SavingReplay
        assertTrue(state is RecorderState.SavingReplay)
        assertEquals("SavingReplay", state.toString())
    }

    @Test
    fun errorState_isCorrectWithMessage() {
        val message = "Microphone not found"
        val state = RecorderState.Error(message)
        assertTrue(state is RecorderState.Error)
        assertEquals(message, state.message)
        assertEquals(null, state.cause)
        assertEquals("Error(message=Microphone not found, cause=null)", state.toString())
    }

    @Test
    fun errorState_isCorrectWithMessageAndCause() {
        val message = "Storage full"
        val cause = IOException("No space left on device")
        val state = RecorderState.Error(message, cause)
        assertTrue(state is RecorderState.Error)
        assertEquals(message, state.message)
        assertEquals(cause, state.cause)
        assertEquals("Error(message=Storage full, cause=java.io.IOException: No space left on device)", state.toString())
    }

    @Test
    fun micBusyState_isCorrect() {
        val state = RecorderState.MicBusy
        assertTrue(state is RecorderState.MicBusy)
        assertEquals("MicBusy", state.toString())
    }

    @Test
    fun permissionNeededState_isCorrect() {
        val state = RecorderState.PermissionNeeded
        assertTrue(state is RecorderState.PermissionNeeded)
        assertEquals("PermissionNeeded", state.toString())
    }
}
