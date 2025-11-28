package com.example.alwaysrecording.ui

sealed class UiError {
    data class Toast(val message: String) : UiError()
    data class Snackbar(val message: String) : UiError()
    data class Dialog(val title: String, val message: String) : UiError()
}
