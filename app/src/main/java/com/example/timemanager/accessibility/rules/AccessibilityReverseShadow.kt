package com.example.timemanager.accessibility.rules

import com.example.timemanager.domain.rules.RuleKind

/** Compare the actual pre-execution Engine plan, not a second evaluator result or execution outcome. */
object AccessibilityReverseShadow {
    fun compare(plan: AccessibilityAuthorityDecision.Engine, legacy: AccessibilityIntent): AccessibilityShadowResult {
        val input = plan.input
        val projection = plan.projection
        val evaluation = plan.evaluation
        val expected = when {
            input.scope == AccessibilityObservationScope.REENTRY && legacy == AccessibilityIntent.ALLOW_ENTRY &&
                evaluation.explicitExemptions.any { it.rule == RuleKind.DEEP_FOCUS } &&
                evaluation.activeConstraints.any { it == RuleKind.DAILY || it == RuleKind.SESSION } ->
                "Deep exemption now hands off fresh lower entry; legacy globally returned"
            input.scope == AccessibilityObservationScope.FOCUS_DEEP_WINDOW && legacy == AccessibilityIntent.FAIL_OPEN &&
                evaluation.explicitExemptions.any { it.rule == RuleKind.DEEP_FOCUS } && !input.health.overlayGranted ->
                "legacy overlay suspension precedes exemption"
            input.scope == AccessibilityObservationScope.REENTRY && projection.intent == AccessibilityIntent.FAIL_OPEN &&
                projection.missingCapabilities.isNotEmpty() -> "Engine capability projection precedes execution"
            projection.deferredToMonitor && input.scope == AccessibilityObservationScope.REENTRY ->
                "normal Session threshold remains Monitor-owned"
            else -> null
        }
        return AccessibilityShadowResult(when {
            expected != null -> AccessibilityShadowStatus.EXPECTED_EXECUTION_DIFFERENCE
            projection.intent != legacy -> AccessibilityShadowStatus.MISMATCH
            else -> AccessibilityShadowStatus.MATCH
        }, projection, evaluation, expected ?: "")
    }
}
