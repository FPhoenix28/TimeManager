package com.example.timemanager.accessibility.rules

import com.example.timemanager.domain.rules.*

enum class AccessibilityIntent { ALLOW_ENTRY, BLOCK_FOCUS, BLOCK_DEEP, CHECK_SESSION_REENTRY, SUPPRESS_SESSION_DAILY, FAIL_OPEN }

data class AccessibilityProjection(
    val intent: AccessibilityIntent,
    val deferredToMonitor: Boolean = false,
    val deferredByWindow: Boolean = false,
    val missingCapabilities: Set<RequiredCapability> = emptySet(),
)

/** Maps the already-resolved winner; no independent priority ranking or Android commands. */
object AccessibilityRulesCompatibilityProjection {
    /** Window events may select higher rules only. Lower rules need a fresh real-entry pipeline. */
    fun authoritative(evaluation: RuleEvaluation, scope: AccessibilityObservationScope,
        overlayOccupied: Boolean = false): AccessibilityProjection {
        val projected = project(evaluation, overlayOccupied)
        if (scope == AccessibilityObservationScope.FOCUS_DEEP_WINDOW &&
            evaluation.selected?.kind !in setOf(RuleKind.FOCUS, RuleKind.DEEP_FOCUS)) {
            return AccessibilityProjection(AccessibilityIntent.ALLOW_ENTRY, deferredToMonitor = true,
                deferredByWindow = overlayOccupied)
        }
        return if (projected.intent == AccessibilityIntent.SUPPRESS_SESSION_DAILY &&
            projected.missingCapabilities.isNotEmpty()) projected.copy(intent = AccessibilityIntent.FAIL_OPEN)
        else projected
    }

    fun project(evaluation: RuleEvaluation, overlayOccupied: Boolean = false): AccessibilityProjection {
        val selected = evaluation.selected
        val capabilities = evaluation.requiredCapabilities.filter { it.candidate == selected }.map { it.capability }.toSet()
        val intent = when {
            selected is RuleCandidate.DeepFocus -> if (capabilities.isEmpty()) AccessibilityIntent.BLOCK_DEEP else AccessibilityIntent.FAIL_OPEN
            selected is RuleCandidate.Focus -> if (capabilities.isEmpty()) AccessibilityIntent.BLOCK_FOCUS else AccessibilityIntent.FAIL_OPEN
            evaluation.insufficientFacts.isNotEmpty() -> AccessibilityIntent.FAIL_OPEN
            RuleKind.DAILY in evaluation.activeConstraints -> AccessibilityIntent.SUPPRESS_SESSION_DAILY
            selected is RuleCandidate.Session && selected.reason == SessionCandidateReason.REENTRY_GATE_ELIGIBLE ->
                if (capabilities.isEmpty()) AccessibilityIntent.CHECK_SESSION_REENTRY else AccessibilityIntent.FAIL_OPEN
            else -> AccessibilityIntent.ALLOW_ENTRY
        }
        return AccessibilityProjection(intent,
            deferredToMonitor = selected is RuleCandidate.Session && selected.reason == SessionCandidateReason.EPISODE_THRESHOLD_REACHED,
            deferredByWindow = overlayOccupied, missingCapabilities = capabilities)
    }
}
