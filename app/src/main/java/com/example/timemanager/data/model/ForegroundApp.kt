package com.example.timemanager.data.model

/** Display information for the application most recently moved to the foreground. */
data class ForegroundApp(
    val packageName: String,
    val appName: String,
)
