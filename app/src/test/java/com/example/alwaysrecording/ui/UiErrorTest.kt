package com.example.alwaysrecording.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiErrorTest {

    @Test
    fun snackbarError_hasCorrectMessage() {
        val error = UiError.Snackbar("Snackbar message")
        assertTrue(error is UiError.Snackbar)
        assertEquals("Snackbar message", error.message)
    }

    @Test
    fun toastError_hasCorrectMessage() {
        val error = UiError.Toast("Toast message")
        assertTrue(error is UiError.Toast)
        assertEquals("Toast message", error.message)
    }

    @Test
    fun dialogError_hasCorrectTitleAndMessage() {
        val error = UiError.Dialog("Dialog Title", "Dialog message")
        assertTrue(error is UiError.Dialog)
        assertEquals("Dialog Title", error.title)
        assertEquals("Dialog message", error.message)
    }
}
