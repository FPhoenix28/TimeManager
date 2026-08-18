package com.example.timemanager.data.model

/** Current uninterrupted foreground-use session for one application. */
data class TimerState(
    val packageName: String,
    val appName: String,
    val currentSessionSeconds: Long,
)
