package com.example.timemanager.data.model

data class AppSessionSettings(
    val packageName: String,
    val reminderMinutes: Int,
    val reminderSeconds: Long = reminderMinutes * 60L,
)
