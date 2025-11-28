package com.example.alwaysrecording.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultTest {

    @Test
    fun success_data_isCorrect() {
        val successResult = Result.Success("Test Data")
        assertEquals("Test Data", successResult.data)
    }

    @Test
    fun error_exception_isCorrect() {
        val exception = RuntimeException("Test Exception")
        val errorResult = Result.Error(exception)
        assertEquals(exception, errorResult.exception)
    }

    @Test
    fun loading_isCorrect() {
        val loadingResult = Result.Loading
        assertTrue(loadingResult is Result.Loading)
    }

    @Test
    fun succeeded_returnsTrueForSuccessWithData() {
        val successResult = Result.Success("Data")
        assertTrue(successResult.succeeded)
    }

    @Test
    fun succeeded_returnsFalseForError() {
        val errorResult = Result.Error(Exception())
        assertFalse(errorResult.succeeded)
    }

    @Test
    fun succeeded_returnsFalseForLoading() {
        val loadingResult = Result.Loading
        assertFalse(loadingResult.succeeded)
    }

    //@Test
    //fun toString_returnsCorrectStringForSuccess() {
    //    val successResult = Result.Success(123)
    //    assertEquals("Success[data=123]", successResult.toString())
    //}

    //@Test
    //fun toString_returnsCorrectStringForError() {
    //    val exception = IllegalArgumentException("Invalid argument")
    //    val errorResult = Result.Error(exception)
    //    assertEquals("Error[exception=java.lang.IllegalArgumentException: Invalid argument]", errorResult.toString())
    //}

    //@Test
    //fun toString_returnsCorrectStringForLoading() {
    //    val loadingResult = Result.Loading
    //    assertEquals("Loading", loadingResult.toString())
    //}
}
