package com.example.timemanager.ui.intervention

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionGateDeadlineTest {
    @Test fun countdownUsesFixedDeadlineAndRoundsUpUntilItReallyExpires() {
        assertEquals(5L, gateRemainingSeconds(6_000L, 1_000L))
        assertEquals(1L, gateRemainingSeconds(6_000L, 5_999L))
        assertEquals(0L, gateRemainingSeconds(6_000L, 6_000L))
    }

    @Test fun recreatingCountdownDoesNotRestartGate() {
        assertEquals(3L, gateRemainingSeconds(6_000L, 3_500L))
        assertEquals(3L, gateRemainingSeconds(6_000L, 3_500L))
        assertEquals(0L, gateRemainingSeconds(6_000L, 9_000L))
    }
}
