package com.example.timemanager.notifications

import com.example.timemanager.data.model.SessionReminder

/** Non-overlapping notification ID spaces for the foreground service and user alerts. */
object NotificationIds {
    const val FOREGROUND_SERVICE = 1_001
    const val LIMIT_WARNING_BASE = 10_000
    const val LIMIT_REACHED_BASE = 20_000
    const val SESSION_REMINDER_BASE = 30_000

    fun limitWarning(packageName: String): Int =
        LIMIT_WARNING_BASE + packageBucket(packageName, CATEGORY_RANGE)

    fun limitReached(packageName: String): Int =
        LIMIT_REACHED_BASE + packageBucket(packageName, CATEGORY_RANGE)

    fun sessionReminder(packageName: String, reminder: SessionReminder): Int {
        val reminderOffset = when (reminder) {
            SessionReminder.FIFTEEN_MINUTES -> 0
            SessionReminder.THIRTY_MINUTES -> 1
        }
        return SESSION_REMINDER_BASE +
            packageBucket(packageName, SESSION_PACKAGE_BUCKETS) * SESSION_IDS_PER_PACKAGE +
            reminderOffset
    }

    private fun packageBucket(packageName: String, bucketCount: Int): Int =
        Math.floorMod(packageName.hashCode(), bucketCount)

    private const val CATEGORY_RANGE = 10_000
    private const val SESSION_IDS_PER_PACKAGE = 2
    private const val SESSION_PACKAGE_BUCKETS = CATEGORY_RANGE / SESSION_IDS_PER_PACKAGE
}
