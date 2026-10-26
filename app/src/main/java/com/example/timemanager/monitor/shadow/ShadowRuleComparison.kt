package com.example.timemanager.monitor.shadow

import com.example.timemanager.data.model.InterventionType
import com.example.timemanager.data.model.LimitInterventionDecision
import com.example.timemanager.domain.rules.*

enum class ShadowComparisonStatus { MATCH, MISMATCH, INSUFFICIENT_SHADOW_FACTS, STALE, ERROR }
enum class ShadowDifference { DAILY_ACTIVE, DAILY_DISPLAY_ELIGIBILITY, SESSION_DUE, BUSINESS_ARBITRATION, LEGACY_DISPLAY_OUTCOME, LEGACY_PRIORITY }
enum class ShadowExecutionDeferral { CAPABILITY, COORDINATOR_GATE, OVERLAY_OCCUPIED, DAILY_DISPLAY_OCCUPIED, LEGACY_GATE_CHANGED }

data class LegacyMonitorOutcome(
    val dailyDecision: LimitInterventionDecision?,
    val selectedPriority: InterventionType?,
    val dailyAvailable: Boolean,
    val sessionEligible: Boolean,
    val overlayOccupied: Boolean,
)

data class ShadowRuleComparison(
    val status: ShadowComparisonStatus,
    val differences: Set<ShadowDifference> = emptySet(),
    val missingFacts: Set<ShadowFact> = emptySet(),
    val executionDeferredBy: Set<ShadowExecutionDeferral> = emptySet(),
)

/** Compares business facts separately from the legacy selector's execution gates. Never executes. */
object ShadowRulesComparator {
    fun compare(input: LegacyMonitorSnapshot, evaluation: RuleEvaluation,
        missing: Set<ShadowFact>, outcome: LegacyMonitorOutcome): ShadowRuleComparison {
        val deferred = buildSet {
            if (!input.availability.canShowSoft) add(ShadowExecutionDeferral.CAPABILITY)
            if (!input.availability.interventionsAllowed) add(ShadowExecutionDeferral.COORDINATOR_GATE)
            if (input.availability.overlayOccupied || outcome.overlayOccupied) add(ShadowExecutionDeferral.OVERLAY_OCCUPIED)
            if (input.availability.dailyDisplayOccupied) add(ShadowExecutionDeferral.DAILY_DISPLAY_OCCUPIED)
            if (input.availability.canShowSoft && input.availability.interventionsAllowed &&
                (!outcome.dailyAvailable || input.legacySessionDue && !outcome.sessionEligible)) add(ShadowExecutionDeferral.LEGACY_GATE_CHANGED)
        }
        if (missing.isNotEmpty() || evaluation.insufficientFacts.isNotEmpty()) {
            return ShadowRuleComparison(ShadowComparisonStatus.INSUFFICIENT_SHADOW_FACTS,
                missingFacts = missing, executionDeferredBy = deferred)
        }
        val candidates = listOfNotNull(evaluation.selected) + evaluation.suppressed.map { it.candidate }
        val reached = input.legacyReached == true
        val displayEligible = input.dailyOwner?.displayEligible == true
        val expectedAction = when {
            displayEligible -> RuleKind.DAILY
            reached -> null // Legacy Daily marker suppresses Session without a new display.
            input.legacySessionDue -> RuleKind.SESSION
            else -> null
        }
        val actualDailyShow = outcome.dailyDecision is LimitInterventionDecision.Show
        val expectedLegacyPriority = when {
            actualDailyShow || reached && outcome.dailyAvailable -> InterventionType.DAILY_LIMIT_REACHED
            outcome.sessionEligible && !outcome.overlayOccupied -> InterventionType.SESSION_REMINDER
            else -> null
        }
        val differences = buildSet {
            if ((RuleKind.DAILY in evaluation.activeConstraints) != reached) add(ShadowDifference.DAILY_ACTIVE)
            if (candidates.any { it.kind == RuleKind.DAILY } != displayEligible) add(ShadowDifference.DAILY_DISPLAY_ELIGIBILITY)
            if (candidates.any { it.kind == RuleKind.SESSION } != input.legacySessionDue) add(ShadowDifference.SESSION_DUE)
            if (evaluation.selected?.kind != expectedAction) add(ShadowDifference.BUSINESS_ARBITRATION)
            // Compare the actual manager result only when the legacy path evaluated Daily.
            if (outcome.dailyAvailable && input.effectiveLimit != null &&
                actualDailyShow != (displayEligible && !input.availability.dailyDisplayOccupied)) {
                add(ShadowDifference.LEGACY_DISPLAY_OUTCOME)
            }
            if (outcome.selectedPriority != expectedLegacyPriority) add(ShadowDifference.LEGACY_PRIORITY)
        }
        return ShadowRuleComparison(if (differences.isEmpty()) ShadowComparisonStatus.MATCH else ShadowComparisonStatus.MISMATCH,
            differences = differences, executionDeferredBy = deferred)
    }
}
