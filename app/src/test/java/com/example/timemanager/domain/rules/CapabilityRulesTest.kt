package com.example.timemanager.domain.rules

import com.example.timemanager.data.model.FocusModeStatus
import com.example.timemanager.permissions.AccessibilityRuntimeState
import org.junit.Assert.*
import org.junit.Test

class CapabilityRulesTest {
    private val f = V090CompatibilityFixtures
    private val evaluator = RulesEvaluator()
    private fun context() = f.context().copy(daily = f.daily(3_600L), focus = FocusRuntimeSnapshot.Concurrent(
        FocusRuntimeSnapshot.Focus(1L, 1_900_000_000_000L, setOf(f.PACKAGE)),
        FocusRuntimeSnapshot.DeepFocus(1L, 1_900_000_000_000L, FocusModeStatus.ACTIVE)))

    @Test fun allAccessibilityStatesRetainMatchedFactsButFocusAndDeepRequireActualConnection() {
        for (state in AccessibilityRuntimeState.entries) {
            val base = context()
            val result = evaluator.evaluate(base.copy(capabilities = base.capabilities.copy(accessibility = state)))
            assertEquals(RuleKind.DEEP_FOCUS, result.selected?.kind)
            assertEquals(setOf(RuleKind.FOCUS, RuleKind.DAILY, RuleKind.SESSION), result.suppressed.map { it.candidate.kind }.toSet())
            val missing = result.requiredCapabilities.filter { it.capability == RequiredCapability.ACCESSIBILITY_CONNECTION }
            assertEquals(if (state == AccessibilityRuntimeState.CONNECTED) emptySet() else setOf(RuleKind.DEEP_FOCUS, RuleKind.FOCUS),
                missing.map { it.candidate.kind }.toSet())
        }
    }

    @Test fun usageAndOverlayGapsAreAttachedToEachAffectedCandidateIncludingSuppressedOnes() {
        val base = context()
        val result = evaluator.evaluate(base.copy(capabilities = base.capabilities.copy(usageAccessGranted = false, overlayGranted = false)))
        assertEquals(setOf(RuleKind.DEEP_FOCUS, RuleKind.DAILY, RuleKind.SESSION),
            result.requiredCapabilities.filter { it.capability == RequiredCapability.OVERLAY }.map { it.candidate.kind }.toSet())
        assertEquals(setOf(RuleKind.DAILY, RuleKind.SESSION),
            result.requiredCapabilities.filter { it.capability == RequiredCapability.USAGE_ACCESS }.map { it.candidate.kind }.toSet())
        assertEquals(5, result.requiredCapabilities.size)
    }

    @Test fun notificationDenialDoesNotDisableOverlayBusinessCandidates() {
        val base = context()
        val result = evaluator.evaluate(base.copy(capabilities = base.capabilities.copy(notificationsGranted = false)))
        assertNotNull(result.selected)
        assertTrue(result.requiredCapabilities.isEmpty())
    }

    @Test fun reentryRequiresConnectionButOrdinarySessionReminderDoesNot() {
        val base = f.context()
        val snapshot = base.session!!.copy(admission = SessionAdmissionSnapshot(reentryGateArmed = true))
        for (state in AccessibilityRuntimeState.entries) {
            val context = base.copy(session = snapshot, capabilities = base.capabilities.copy(accessibility = state))
            val entry = evaluator.evaluate(context.copy(event = EvaluationEvent.APP_ENTERED))
            assertEquals(SessionCandidateReason.REENTRY_GATE_ELIGIBLE, entry.selected?.reason)
            assertEquals(state != AccessibilityRuntimeState.CONNECTED, entry.requiredCapabilities.isNotEmpty())
            val tick = evaluator.evaluate(context)
            assertEquals(SessionCandidateReason.EPISODE_THRESHOLD_REACHED, tick.selected?.reason)
            assertTrue(tick.requiredCapabilities.isEmpty())
        }
    }
}
