package com.example.timemanager.monitor

import com.example.timemanager.data.model.SessionReminder
import com.example.timemanager.data.model.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionTrackerTest {
    private val tracker = SessionTracker()

    @Test
    fun emitsFifteenAndThirtyMinuteRemindersOncePerSession() {
        assertNull(tracker.update(state("app.one", 899L)))
        assertEquals(SessionReminder.FIFTEEN_MINUTES, tracker.update(state("app.one", 900L)))
        assertNull(tracker.update(state("app.one", 901L)))
        assertEquals(SessionReminder.THIRTY_MINUTES, tracker.update(state("app.one", 1_800L)))
        assertNull(tracker.update(state("app.one", 1_801L)))
    }

    @Test
    fun leavingAppResetsReminderState() {
        assertEquals(SessionReminder.FIFTEEN_MINUTES, tracker.update(state("app.one", 900L)))

        tracker.endSession()

        assertEquals(SessionReminder.FIFTEEN_MINUTES, tracker.update(state("app.one", 900L)))
    }

    @Test
    fun changingPackageStartsNewSession() {
        assertEquals(SessionReminder.FIFTEEN_MINUTES, tracker.update(state("app.one", 900L)))
        assertEquals(SessionReminder.FIFTEEN_MINUTES, tracker.update(state("app.two", 900L)))
    }

    private fun state(packageName: String, seconds: Long) = TimerState(
        packageName = packageName,
        appName = packageName,
        currentSessionSeconds = seconds,
    )
}
