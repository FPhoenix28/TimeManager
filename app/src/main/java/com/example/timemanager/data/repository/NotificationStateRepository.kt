package com.example.timemanager.data.repository

import com.example.timemanager.data.preferences.NotificationStatePreferences
import kotlinx.coroutines.flow.first

/** Prevents the same threshold notification from being sent twice in one day. */
class NotificationStateRepository(
    private val preferences: NotificationStatePreferences,
) {
    suspend fun wasSentToday(
        packageName: String,
        thresholdPercent: Int,
        today: String,
    ): Boolean = preferences
        .observeLastSentDate(packageName, thresholdPercent)
        .first() == today

    suspend fun markSentToday(
        packageName: String,
        thresholdPercent: Int,
        today: String,
    ) {
        preferences.setLastSentDate(packageName, thresholdPercent, today)
    }
}
