package com.example.timemanager.notifications

import com.example.timemanager.data.model.SessionReminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationIdsTest {
    @Test
    fun `notification categories stay in separate ranges`() {
        val packageNames = listOf(
            "com.example.video",
            "com.example.social",
            "com.example.game",
            "com.tencent.mobileqq",
        )

        packageNames.forEach { packageName ->
            val warningId = NotificationIds.limitWarning(packageName)
            val reachedId = NotificationIds.limitReached(packageName)
            val session15Id = NotificationIds.sessionReminder(
                packageName,
                SessionReminder.FIFTEEN_MINUTES,
            )
            val session30Id = NotificationIds.sessionReminder(
                packageName,
                SessionReminder.THIRTY_MINUTES,
            )

            assertTrue(warningId in 10_000 until 20_000)
            assertTrue(reachedId in 20_000 until 30_000)
            assertTrue(session15Id in 30_000 until 40_000)
            assertTrue(session30Id in 30_000 until 40_000)
            assertNotEquals(NotificationIds.FOREGROUND_SERVICE, warningId)
            assertNotEquals(NotificationIds.FOREGROUND_SERVICE, reachedId)
            assertNotEquals(NotificationIds.FOREGROUND_SERVICE, session15Id)
            assertNotEquals(NotificationIds.FOREGROUND_SERVICE, session30Id)
            assertNotEquals(session15Id, session30Id)
            assertEquals(warningId, NotificationIds.limitWarning(packageName))
        }
    }
}
