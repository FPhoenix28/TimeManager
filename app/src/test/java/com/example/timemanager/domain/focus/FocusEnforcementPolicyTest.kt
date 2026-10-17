package com.example.timemanager.domain.focus

import com.example.timemanager.data.model.FocusModeState
import com.example.timemanager.data.model.FocusModeType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusEnforcementPolicyTest {
    private val focus = FocusModeState(
        mode = FocusModeType.FOCUS,
        startTimeMillis = 1_000L,
        endTimeMillis = 61_000L,
        blockedPackages = setOf("app.blocked", "com.example.timemanager"),
    )

    @Test fun selectedAppIsBlockedDuringFocus() {
        assertTrue(FocusEnforcementPolicy.shouldBlock(focus, "app.blocked", OWN_APP, 10_000L))
    }

    @Test fun unrelatedAndOwnAppsAreNotBlocked() {
        assertFalse(FocusEnforcementPolicy.shouldBlock(focus, "app.allowed", OWN_APP, 10_000L))
        assertFalse(FocusEnforcementPolicy.shouldBlock(focus, OWN_APP, OWN_APP, 10_000L))
    }

    @Test fun ownPackageExclusionUsesEachVariantsRuntimeIdentity() {
        val dev = "$OWN_APP.debug"
        val bothSelected = focus.copy(blockedPackages = setOf(OWN_APP, dev))

        assertFalse(FocusEnforcementPolicy.shouldBlock(bothSelected, dev, dev, 10_000L))
        assertFalse(FocusEnforcementPolicy.shouldBlock(bothSelected, OWN_APP, OWN_APP, 10_000L))
    }

    @Test fun debugDoesNotMistakeReleasePackageForItself() {
        val dev = "$OWN_APP.debug"
        val bothSelected = focus.copy(blockedPackages = setOf(OWN_APP, dev))

        assertTrue(FocusEnforcementPolicy.shouldBlock(bothSelected, OWN_APP, dev, 10_000L))
        assertTrue(FocusEnforcementPolicy.shouldBlock(bothSelected, dev, OWN_APP, 10_000L))
    }

    @Test fun expiredOffAndReservedModesNeverBlock() {
        assertFalse(FocusEnforcementPolicy.shouldBlock(focus, "app.blocked", OWN_APP, 61_000L))
        assertFalse(
            FocusEnforcementPolicy.shouldBlock(
                focus.copy(mode = FocusModeType.OFF),
                "app.blocked",
                OWN_APP,
                10_000L,
            ),
        )
        assertFalse(
            FocusEnforcementPolicy.shouldBlock(
                focus.copy(mode = FocusModeType.DEEP_FOCUS),
                "app.blocked",
                OWN_APP,
                10_000L,
            ),
        )
    }

    @Test fun visibleOverlayStillRequiresHomeButIsNotAddedAgain() {
        val action = FocusEnforcementActionPolicy.decide(
            shouldBlock = true,
            overlayShowing = true,
            debounced = false,
        )

        assertTrue(action.performHome)
        assertFalse(action.showBlockedOverlay)
    }

    @Test fun duplicateEventInsideDebounceWindowDoesNothing() {
        val action = FocusEnforcementActionPolicy.decide(
            shouldBlock = true,
            overlayShowing = false,
            debounced = true,
        )

        assertFalse(action.performHome)
        assertFalse(action.showBlockedOverlay)
    }

    private companion object {
        const val OWN_APP = "com.example.timemanager"
    }
}
