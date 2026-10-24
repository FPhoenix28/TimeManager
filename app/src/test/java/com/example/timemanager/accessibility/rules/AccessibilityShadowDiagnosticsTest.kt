package com.example.timemanager.accessibility.rules

import com.example.timemanager.domain.rules.*
import org.junit.Assert.*
import org.junit.Test

class AccessibilityShadowDiagnosticsTest {
    @Test fun wrongLegacyIntentIsAnUnexpectedBusinessMismatch() {
        assertEquals(AccessibilityShadowStatus.MISMATCH,
            AccessibilityRulesShadow().compare(accessibilitySnapshot(), AccessibilityIntent.ALLOW_ENTRY).status)
    }
    @Test fun revisionAndGenerationChangesAreStaleNotBusinessMismatch() {
        val input = accessibilitySnapshot()
        val shadow = AccessibilityRulesShadow()
        assertEquals(AccessibilityShadowStatus.STALE, shadow.compare(input, AccessibilityIntent.CHECK_SESSION_REENTRY,
            currentRevision = PolicyRevision(2L)).status)
        assertEquals(AccessibilityShadowStatus.STALE, shadow.compare(input, AccessibilityIntent.CHECK_SESSION_REENTRY,
            currentGeneration = ForegroundGeneration(8L)).status)
    }
    @Test fun evaluatorIdentityMustMatchCapturedContext() {
        val shadow = AccessibilityRulesShadow(evaluate = { RulesEvaluator().evaluate(it.copy(identity = it.identity.copy(evaluationSequence = 2L))) })
        assertEquals(AccessibilityShadowStatus.STALE, shadow.compare(accessibilitySnapshot(), AccessibilityIntent.CHECK_SESSION_REENTRY).status)
    }
    @Test fun evaluatorExceptionIsIsolatedBeforeAnyExecution() {
        val shadow = AccessibilityRulesShadow(evaluate = { error("injected evaluator failure") })
        assertEquals(AccessibilityShadowStatus.ERROR, shadow.compare(accessibilitySnapshot(), AccessibilityIntent.CHECK_SESSION_REENTRY).status)
    }
    @Test fun missingConfigurationAndIncoherentQuotaAreInsufficient() {
        val source = accessibilitySnapshot()
        for (input in listOf(source.copy(policy = source.policy.copy(loaded = false)),
            source.copy(policy = source.policy.copy(baseLimitSeconds = 900L)), source.copy(capabilityInconsistent = true))) {
            assertEquals(AccessibilityShadowStatus.INSUFFICIENT_FACTS,
                AccessibilityRulesShadow().compare(input, AccessibilityIntent.CHECK_SESSION_REENTRY).status)
        }
    }
    @Test fun recurringDiagnosticIsDeduplicatedAndMatchIsSilent() {
        val messages = mutableListOf<String>()
        val shadow = AccessibilityRulesShadow(diagnostic = messages::add)
        repeat(100) { shadow.compare(accessibilitySnapshot(), AccessibilityIntent.ALLOW_ENTRY) }
        assertEquals(1, messages.size)
        repeat(100) { shadow.compare(accessibilitySnapshot(), AccessibilityIntent.CHECK_SESSION_REENTRY) }
        assertEquals(1, messages.size)
    }
    @Test fun knownExecutionDifferencesAreNotLoggedForEveryEvent() {
        val messages = mutableListOf<String>()
        val source = accessibilitySnapshot()
        val input = source.copy(episode = source.episode!!.copy(reentryGateArmed = false, episodeUsageSeconds = 35L))
        val shadow = AccessibilityRulesShadow(diagnostic = messages::add)
        repeat(100) { assertEquals(AccessibilityShadowStatus.EXPECTED_EXECUTION_DIFFERENCE,
            shadow.compare(input, AccessibilityIntent.ALLOW_ENTRY).status) }
        assertTrue(messages.isEmpty())
    }
    @Test fun diagnosticSinkFailureCannotInterruptLegacyPath() {
        val shadow = AccessibilityRulesShadow(diagnostic = { error("sink") })
        assertEquals(AccessibilityShadowStatus.MISMATCH, shadow.compare(accessibilitySnapshot(), AccessibilityIntent.ALLOW_ENTRY).status)
    }
}
