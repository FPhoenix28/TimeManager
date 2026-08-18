package com.example.timemanager.data.model

/** One-time reminder reached during the current uninterrupted app session. */
enum class SessionReminder(val thresholdMinutes: Int) {
    FIFTEEN_MINUTES(15),
    THIRTY_MINUTES(30),
}
