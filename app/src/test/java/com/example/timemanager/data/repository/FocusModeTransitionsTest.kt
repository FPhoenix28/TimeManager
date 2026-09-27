package com.example.timemanager.data.repository

import com.example.timemanager.data.model.FocusModeState
import com.example.timemanager.data.model.FocusModeStatus
import com.example.timemanager.data.model.FocusModeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusModeTransitionsTest {
    @Test fun activeDeepFocusBecomesCompletedAtEndAndImmediatelyStopsRestricting() {
        val active = FocusModeState(
            mode = FocusModeType.DEEP_FOCUS,
            status = FocusModeStatus.ACTIVE,
            startTimeMillis = 1_000L,
            endTimeMillis = 61_000L,
        )

        val completed = FocusModeTransitions.advanceIfExpired(active, 61_000L)

        assertEquals(FocusModeType.DEEP_FOCUS, completed.mode)
        assertEquals(FocusModeStatus.COMPLETED, completed.status)
        assertFalse(completed.isDeepFocusActive(61_000L))
        assertTrue(completed.isDeepFocusCompleted())
    }

    @Test fun ordinaryFocusStillReturnsToOffAtEnd() {
        val focus = FocusModeState(
            mode = FocusModeType.FOCUS,
            startTimeMillis = 1_000L,
            endTimeMillis = 61_000L,
            blockedPackages = setOf("app.blocked"),
        )

        assertEquals(
            FocusModeState(),
            FocusModeTransitions.advanceIfExpired(focus, 61_000L),
        )
    }

    @Test fun invalidPersistedWindowFailsOpen() {
        val invalid = FocusModeState(
            mode = FocusModeType.DEEP_FOCUS,
            startTimeMillis = 5_000L,
            endTimeMillis = 5_000L,
        )

        assertEquals(
            FocusModeState(),
            FocusModeTransitions.advanceIfExpired(invalid, 5_000L),
        )
    }
}
