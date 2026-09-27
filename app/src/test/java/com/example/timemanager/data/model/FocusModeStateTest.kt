package com.example.timemanager.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusModeStateTest {
    private val state = FocusModeState(
        mode = FocusModeType.FOCUS,
        startTimeMillis = 1_000L,
        endTimeMillis = 61_000L,
        blockedPackages = setOf("app.blocked"),
    )

    @Test fun activeOnlyWithinPersistedTimeWindow() {
        assertFalse(state.isActive(999L))
        assertTrue(state.isActive(1_000L))
        assertTrue(state.isActive(60_999L))
        assertFalse(state.isActive(61_000L))
    }

    @Test fun remainingTimeIsCalculatedFromEndTime() {
        assertEquals(51_000L, state.remainingMillis(10_000L))
        assertEquals(0L, state.remainingMillis(70_000L))
    }

    @Test fun deepFocusStopsBeingActiveAtEndEvenBeforeStateIsPersistedAsCompleted() {
        val deepFocus = state.copy(
            mode = FocusModeType.DEEP_FOCUS,
            blockedPackages = emptySet(),
        )

        assertTrue(deepFocus.isDeepFocusActive(60_999L))
        assertFalse(deepFocus.isDeepFocusActive(61_000L))
    }
}
