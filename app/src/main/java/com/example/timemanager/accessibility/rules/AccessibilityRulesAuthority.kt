package com.example.timemanager.accessibility.rules

import com.example.timemanager.domain.rules.*

sealed interface AccessibilityAuthorityDecision {
    data class Engine(val input: AccessibilityRulesSnapshot, val evaluation: RuleEvaluation,
        val projection: AccessibilityProjection) : AccessibilityAuthorityDecision
    data class LegacyFallback(val reason: String) : AccessibilityAuthorityDecision
    data object Stale : AccessibilityAuthorityDecision
}

/** Pure pre-execution boundary. Neither evaluation nor disagreement can execute legacy. */
class AccessibilityRulesAuthority(
    private val adapt: (AccessibilityRulesSnapshot) -> AdaptedAccessibilitySnapshot = AccessibilityRulesSnapshotAdapter()::adapt,
    private val evaluate: (RuleEvaluationContext) -> RuleEvaluation = RulesEvaluator()::evaluate,
) {
    fun decide(input: AccessibilityRulesSnapshot): AccessibilityAuthorityDecision = try {
        val adapted = adapt(input)
        val critical = adapted.missing.filter {
            input.scope == AccessibilityObservationScope.REENTRY || it == AccessibilityMissingFact.CAPABILITY_INCONSISTENCY
        }
        when {
            AccessibilityMissingFact.DAILY_NOT_FRESH in critical -> AccessibilityAuthorityDecision.Stale
            critical.isNotEmpty() -> AccessibilityAuthorityDecision.LegacyFallback("missing=$critical")
            else -> {
                val evaluation = evaluate(adapted.context)
                when {
                    evaluation.identity != input.identity -> AccessibilityAuthorityDecision.Stale
                    evaluation.insufficientFacts.any { input.scope == AccessibilityObservationScope.REENTRY ||
                        it.rule == RuleKind.FOCUS || it.rule == RuleKind.DEEP_FOCUS } ->
                        AccessibilityAuthorityDecision.LegacyFallback("engine missing=${evaluation.insufficientFacts}")
                    else -> AccessibilityAuthorityDecision.Engine(input, evaluation,
                        AccessibilityRulesCompatibilityProjection.authoritative(evaluation, input.scope, input.overlayOccupied))
                }
            }
        }
    } catch (error: Exception) {
        AccessibilityAuthorityDecision.LegacyFallback(error.javaClass.simpleName)
    }
}

/** One branch is chosen before entering a sink. Never catch execution and run the other sink. */
object AccessibilityAuthorityDispatch {
    suspend fun execute(decision: AccessibilityAuthorityDecision, isCurrent: () -> Boolean,
        engine: suspend (AccessibilityAuthorityDecision.Engine) -> Unit, legacy: suspend (String) -> Unit) {
        if (!isCurrent()) return
        when (decision) {
            is AccessibilityAuthorityDecision.Engine -> engine(decision)
            is AccessibilityAuthorityDecision.LegacyFallback -> legacy(decision.reason)
            AccessibilityAuthorityDecision.Stale -> Unit
        }
    }
}
