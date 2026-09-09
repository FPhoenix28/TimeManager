package com.example.timemanager.service

import com.example.timemanager.data.model.InterventionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageMonitorServiceInterventionPriorityTest {
    @Test
    fun dailyLimitWinsWhenDailyAndSessionBecomeDueTogether() {
        assertEquals(
            InterventionType.DAILY_LIMIT_REACHED,
            selectInterventionType(
                dailyLimitTriggered = true,
                sessionDue = true,
                overlayShowing = false,
            ),
        )
    }

    @Test
    fun sessionInterventionWaitsWhileAnotherOverlayIsVisible() {
        assertNull(
            selectInterventionType(
                dailyLimitTriggered = false,
                sessionDue = true,
                overlayShowing = true,
            ),
        )
    }
}
