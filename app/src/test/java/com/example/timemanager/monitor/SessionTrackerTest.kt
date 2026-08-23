package com.example.timemanager.monitor

import com.example.timemanager.data.model.SessionReminder
import com.example.timemanager.data.model.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun customDebugThresholdsUseTheSameSessionLogic() {
        val debugTracker = SessionTracker(
            firstReminderSeconds = 15L,
            secondReminderSeconds = 30L,
        )

        assertNull(debugTracker.update(state("app.one", 14L)))
        assertEquals(SessionReminder.FIFTEEN_MINUTES, debugTracker.update(state("app.one", 15L)))
        assertNull(debugTracker.update(state("app.one", 29L)))
        assertEquals(SessionReminder.THIRTY_MINUTES, debugTracker.update(state("app.one", 30L)))
    }

    @Test
    fun thirtyMinuteInterventionIsConsumedOncePerSession() {
        assertFalse(tracker.consumeSessionIntervention(state("app.one", 1_799L), 1_800L))
        assertTrue(tracker.consumeSessionIntervention(state("app.one", 1_800L), 1_800L))
        assertFalse(tracker.consumeSessionIntervention(state("app.one", 1_801L), 1_800L))
    }

    @Test
    fun endingSessionAllowsThirtyMinuteInterventionAgain() {
        assertTrue(tracker.consumeSessionIntervention(state("app.one", 1_800L), 1_800L))

        tracker.endSession()

        assertTrue(tracker.consumeSessionIntervention(state("app.one", 1_800L), 1_800L))
    }

    @Test
    fun reminderUpdatesDoNotResetInterventionWithinTheSameSession() {
        assertTrue(tracker.consumeSessionIntervention(state("app.one", 1_800L), 1_800L))
        tracker.update(state("app.one", 1_801L))

        assertFalse(tracker.consumeSessionIntervention(state("app.one", 2_400L), 1_200L))
    }

    @Test
    fun loweringThresholdTriggersOnNextUpdateWithoutRepeating() {
        assertFalse(tracker.consumeSessionIntervention(state("app.one", 1_500L), 1_800L))
        assertTrue(tracker.consumeSessionIntervention(state("app.one", 1_501L), 1_200L))
        assertFalse(tracker.consumeSessionIntervention(state("app.one", 1_502L), 1_200L))
    }

    private fun state(packageName: String, seconds: Long) = TimerState(
        packageName = packageName,
        appName = packageName,
        currentSessionSeconds = seconds,
    )
}
