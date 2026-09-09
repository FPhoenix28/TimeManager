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
    fun defaultProgressiveInterventionsFollowTenEightEightThenFiveMinuteSchedule() {
        assertFalse(evaluate(599L, 600L).isDue)

        assertTrue(evaluate(600L, 600L).isDue)
        assertEquals(1_080L, tracker.onSessionInterventionShown("app.one").nextReminderAtSeconds)
        assertFalse(evaluate(1_079L, 600L).isDue)

        assertTrue(evaluate(1_080L, 600L).isDue)
        assertEquals(1_560L, tracker.onSessionInterventionShown("app.one").nextReminderAtSeconds)

        assertTrue(evaluate(1_560L, 600L).isDue)
        assertEquals(1_860L, tracker.onSessionInterventionShown("app.one").nextReminderAtSeconds)

        assertTrue(evaluate(1_860L, 600L).isDue)
        assertEquals(2_160L, tracker.onSessionInterventionShown("app.one").nextReminderAtSeconds)

        assertTrue(evaluate(2_160L, 600L).isDue)
        assertEquals(2_460L, tracker.onSessionInterventionShown("app.one").nextReminderAtSeconds)
    }

    @Test
    fun customTwentyMinuteFirstReminderUsesTheSameProgression() {
        assertTrue(evaluate(1_200L, 1_200L).isDue)
        assertEquals(1_680L, tracker.onSessionInterventionShown("app.one").nextReminderAtSeconds)
        assertTrue(evaluate(1_680L, 1_200L).isDue)
        assertEquals(2_160L, tracker.onSessionInterventionShown("app.one").nextReminderAtSeconds)
        assertTrue(evaluate(2_160L, 1_200L).isDue)
        assertEquals(2_460L, tracker.onSessionInterventionShown("app.one").nextReminderAtSeconds)
        assertTrue(evaluate(2_460L, 1_200L).isDue)
        assertEquals(2_760L, tracker.onSessionInterventionShown("app.one").nextReminderAtSeconds)
    }

    @Test
    fun endingSessionRestartsProgressionFromFirstReminder() {
        assertTrue(evaluate(600L, 600L).isDue)
        tracker.onSessionInterventionShown("app.one")

        tracker.endSession()

        assertFalse(evaluate(599L, 600L).isDue)
        assertTrue(evaluate(600L, 600L).isDue)
        assertEquals(0, evaluate(600L, 600L).reminderCount)
    }

    @Test
    fun continuingAfterFirstReminderTargetsEighteenMinutesNotTwenty() {
        assertTrue(evaluate(600L, 600L).isDue)
        tracker.onSessionInterventionShown("app.one")

        assertFalse(evaluate(1_079L, 600L).isDue)
        assertTrue(evaluate(1_080L, 600L).isDue)
    }

    @Test
    fun dueReminderDoesNotAdvanceUntilOverlayDisplayIsConfirmed() {
        val firstAttempt = evaluate(600L, 600L)
        val secondAttempt = evaluate(601L, 600L)

        assertTrue(firstAttempt.isDue)
        assertTrue(secondAttempt.isDue)
        assertEquals(0, secondAttempt.reminderCount)
        assertEquals(600L, secondAttempt.nextReminderAtSeconds)
    }

    @Test
    fun loweringThresholdBeforeFirstReminderTriggersOnNextTick() {
        assertFalse(evaluate(900L, 1_200L).isDue)

        val lowered = evaluate(901L, 600L)

        assertTrue(lowered.isDue)
        assertEquals(600L, lowered.firstReminderSeconds)
        assertEquals(0, lowered.reminderCount)
    }

    @Test
    fun changingFirstReminderAfterProgressionStartedDoesNotMoveCurrentStage() {
        assertTrue(evaluate(600L, 600L).isDue)
        tracker.onSessionInterventionShown("app.one")
        assertTrue(evaluate(1_080L, 600L).isDue)
        tracker.onSessionInterventionShown("app.one")

        val changed = evaluate(1_100L, 1_200L)

        assertFalse(changed.isDue)
        assertEquals(600L, changed.firstReminderSeconds)
        assertEquals(2, changed.reminderCount)
        assertEquals(1_560L, changed.nextReminderAtSeconds)
    }

    private fun evaluate(currentSeconds: Long, firstReminderSeconds: Long) =
        tracker.evaluateSessionIntervention(
            timerState = state("app.one", currentSeconds),
            configuredReminderSeconds = firstReminderSeconds,
        )

    private fun state(packageName: String, seconds: Long) = TimerState(
        packageName = packageName,
        appName = packageName,
        currentSessionSeconds = seconds,
    )
}
