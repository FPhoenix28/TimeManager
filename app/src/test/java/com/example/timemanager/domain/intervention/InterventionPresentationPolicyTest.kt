package com.example.timemanager.domain.intervention

import com.example.timemanager.data.model.*
import com.example.timemanager.domain.limit.LimitInterventionTriggerState
import com.example.timemanager.monitor.SessionFrictionManager
import com.example.timemanager.permissions.AccessibilityRuntimeState
import com.example.timemanager.permissions.PermissionHealthState
import com.example.timemanager.service.selectInterventionType
import org.junit.Assert.*
import org.junit.Test

class InterventionPresentationPolicyTest {
    private val fallback = PermissionHealthState(true, true, true, false, false)
    private val full = fallback.copy(accessibilityGranted = true, accessibilityConnected = true)
    private fun session(presentation: InterventionPresentation, phase: SessionGatePhase = SessionGatePhase.REMINDER) =
        LimitInterventionRequest.sessionReminder("one", "One", 600L, 600L, 600L,
            SessionGate(1L, phase, 6_000L), presentation)
    private fun daily() = LimitInterventionRequest(packageName = "one", appName = "One",
        todayUsageSeconds = 600L, baseLimitSeconds = 600L, extensionSeconds = 0L,
        effectiveLimitSeconds = 600L, exceededSeconds = 0L, currentSessionSeconds = 600L,
        extensionDurationSeconds = 900L, presentation = InterventionPresentation.OVERLAY_IN_PLACE)

    @Test fun fallbackCountdownEnablesChoiceWithoutAutoClosing() {
        val request = session(InterventionPresentation.OVERLAY_IN_PLACE)
        assertFalse(InterventionPresentationPolicy.sessionActionsAvailable(request, 1L))
        assertTrue(InterventionPresentationPolicy.sessionActionsAvailable(request, 0L))
    }

    @Test fun homeFirstReminderAndReentryBothOfferChoiceAtDeadline() {
        val request = session(InterventionPresentation.HOME_FIRST)
        assertFalse(InterventionPresentationPolicy.sessionActionsAvailable(request, 1L))
        assertTrue(InterventionPresentationPolicy.sessionActionsAvailable(request, 0L))
        val reentry = session(InterventionPresentation.HOME_FIRST, SessionGatePhase.REENTRY)
        assertFalse(InterventionPresentationPolicy.sessionActionsAvailable(reentry, 1L))
        assertTrue(InterventionPresentationPolicy.sessionActionsAvailable(reentry, 0L))
    }

    @Test fun homeFirstReminderContinueWaitsForDeadlineThenReusesAdmission() {
        val request = session(InterventionPresentation.HOME_FIRST)
        assertEquals(SessionCompletionAction.WAIT, InterventionPresentationPolicy.sessionCompletion(request, true, 5_999L))
        assertEquals(SessionCompletionAction.ADMIT_AND_LAUNCH, InterventionPresentationPolicy.sessionCompletion(request, true, 6_000L))
        assertEquals(SessionCompletionAction.EXIT_TO_HOME, InterventionPresentationPolicy.sessionCompletion(request, false, 6_000L))
    }

    @Test fun fallbackActionsWaitThenContinueWithoutLaunchOrAdmissionOrExitByIntent() {
        val request = session(InterventionPresentation.OVERLAY_IN_PLACE)
        assertEquals(SessionCompletionAction.WAIT, InterventionPresentationPolicy.sessionCompletion(request, true, 5_999L))
        assertEquals(SessionCompletionAction.WAIT, InterventionPresentationPolicy.sessionCompletion(request, false, 5_999L))
        assertEquals(SessionCompletionAction.CONTINUE_IN_PLACE, InterventionPresentationPolicy.sessionCompletion(request, true, 6_000L))
        assertEquals(SessionCompletionAction.EXIT_TO_HOME, InterventionPresentationPolicy.sessionCompletion(request, false, 6_000L))
        val reentry = session(InterventionPresentation.HOME_FIRST, SessionGatePhase.REENTRY)
        assertEquals(SessionCompletionAction.ADMIT_AND_LAUNCH, InterventionPresentationPolicy.sessionCompletion(reentry, true, 6_000L))
        assertEquals(SessionCompletionAction.EXIT_TO_HOME, InterventionPresentationPolicy.sessionCompletion(reentry, false, 6_000L))
    }

    @Test fun fallbackDailyExtensionUsesSameFifteenMinutesWithoutChangingBase() {
        val before = EffectiveAppLimit("one", 3_600L, 0L)
        val after = before.copy(todayExtensionSeconds = before.todayExtensionSeconds + daily().extensionDurationSeconds)
        assertEquals(3_600L, after.baseLimitSeconds)
        assertEquals(4_500L, after.effectiveLimitSeconds)
        assertEquals(900L, after.todayExtensionSeconds)
    }

    @Test fun accessibilityLossDoesNotDismissOrdinarySessionOrDaily() {
        assertFalse(InterventionPresentationPolicy.shouldDismiss(daily(), fallback))
        assertFalse(InterventionPresentationPolicy.shouldDismiss(session(InterventionPresentation.OVERLAY_IN_PLACE), fallback))
        assertFalse(InterventionPresentationPolicy.shouldDismiss(daily().copy(presentation = InterventionPresentation.HOME_FIRST), fallback))
        assertTrue(InterventionPresentationPolicy.shouldDismiss(session(InterventionPresentation.HOME_FIRST, SessionGatePhase.REENTRY), fallback))
    }

    @Test fun usageAndOverlayRevocationDismissEitherOrdinaryIntervention() {
        for (health in listOf(fallback.copy(usageAccessGranted = false), fallback.copy(overlayGranted = false))) {
            assertFalse(InterventionPresentationPolicy.canShow(health, true))
            assertTrue(InterventionPresentationPolicy.shouldDismiss(daily(), health))
            assertTrue(InterventionPresentationPolicy.shouldDismiss(session(InterventionPresentation.OVERLAY_IN_PLACE), health))
        }
        assertTrue(InterventionPresentationPolicy.canShow(fallback.copy(notificationGranted = false), true))
    }

    @Test fun enabledAndActuallyConnectedAreSeparateCapabilities() {
        assertEquals(AccessibilityRuntimeState.DISABLED, fallback.accessibilityRuntimeState)
        val disconnected = fallback.copy(accessibilityGranted = true)
        assertEquals(AccessibilityRuntimeState.ENABLED_BUT_DISCONNECTED, disconnected.accessibilityRuntimeState)
        assertFalse(disconnected.canForceHome)
        assertTrue(disconnected.allRequiredGranted)
        assertFalse(disconnected.isHealthy)
        assertEquals(AccessibilityRuntimeState.CONNECTED, full.accessibilityRuntimeState)
        assertTrue(full.canForceHome)
        assertEquals(InterventionPresentation.HOME_FIRST, InterventionPresentationPolicy.mode(full.canForceHome))
        assertEquals(InterventionPresentation.OVERLAY_IN_PLACE, InterventionPresentationPolicy.mode(disconnected.canForceHome))
    }

    @Test fun dailyCrossingAndAlreadyOverLimitEntrySelectDailyWithoutAccessibility() {
        val friction = SessionFrictionManager(clock = { 1_000L })
        val coordinator = InterventionCoordinator(friction, "own")
        val trigger = LimitInterventionTriggerState()
        val available = InterventionPresentationPolicy.canShow(fallback, coordinator.interventionsAllowed("one"))
        assertTrue(available)
        trigger.evaluate("one", "day", false)
        val crossed = trigger.evaluate("one", "day", true)
        assertTrue(crossed.limitCrossed)
        assertEquals(InterventionType.DAILY_LIMIT_REACHED, selectInterventionType(available && crossed.limitCrossed, true, false))
        trigger.onAppLeft("one")
        trigger.onManagedAppEntered("one")
        val entered = trigger.evaluate("one", "day", true)
        assertTrue(entered.enteredWhileOverLimit)
        assertEquals(InterventionType.DAILY_LIMIT_REACHED, selectInterventionType(available && entered.enteredWhileOverLimit, false, false))
        assertFalse(trigger.evaluate("one", "day", true).enteredWhileOverLimit)
    }

    @Test fun shownOverlayPausesTimingUntilReleaseAcrossDisconnectAndReconnect() {
        val coordinator = InterventionCoordinator(SessionFrictionManager(clock = { 0L }), "own")
        assertTrue(coordinator.claimOverlay(InterventionPriority.DAILY, "one"))
        coordinator.pauseTimingForOverlay(InterventionPriority.DAILY, "one")
        assertFalse(coordinator.canTime("one"))
        coordinator.setAccessibilityConnected(true)
        coordinator.setAccessibilityConnected(false)
        assertFalse(coordinator.canTime("one"))
        coordinator.releaseOverlay(InterventionPriority.DAILY, "one")
        assertTrue(coordinator.canTime("one"))
    }

    @Test fun failedOverlayClaimCannotPauseTiming() {
        val coordinator = InterventionCoordinator(SessionFrictionManager(clock = { 0L }), "own")
        coordinator.pauseTimingForOverlay(InterventionPriority.SESSION, "one")
        assertTrue(coordinator.canTime("one"))
        assertFalse(InterventionPresentationPolicy.canShow(fallback, interventionsAllowed = false))
    }
}
