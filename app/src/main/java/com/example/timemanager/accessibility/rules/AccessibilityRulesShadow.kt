package com.example.timemanager.accessibility.rules

import com.example.timemanager.domain.rules.*

enum class AccessibilityShadowStatus { MATCH, MISMATCH, INSUFFICIENT_FACTS, STALE, EXPECTED_EXECUTION_DIFFERENCE, ERROR }

data class AccessibilityShadowResult(
    val status: AccessibilityShadowStatus,
    val projection: AccessibilityProjection? = null,
    val evaluation: RuleEvaluation? = null,
    val detail: String = "",
)

/** Diagnostic state only. No coordinator, Friction writer, window or command references. */
class AccessibilityRulesShadow(
    private val evaluate: (RuleEvaluationContext) -> RuleEvaluation = RulesEvaluator()::evaluate,
    private val diagnostic: (String) -> Unit = {},
) {
    private val adapter = AccessibilityRulesSnapshotAdapter()
    private var lastDiagnostic: String? = null

    fun compare(input: AccessibilityRulesSnapshot, legacy: AccessibilityIntent,
        currentRevision: PolicyRevision = input.identity.policyRevision,
        currentGeneration: ForegroundGeneration = input.identity.foregroundGeneration): AccessibilityShadowResult {
        val result = try {
            val adapted = adapter.adapt(input)
            val evaluation = evaluate(adapted.context)
            val projection = AccessibilityRulesCompatibilityProjection.project(evaluation, input.overlayOccupied)
            val knownHandledDaily = input.dailyAlreadyHandled && input.dailyFreshForEntry &&
                projection.intent == AccessibilityIntent.SUPPRESS_SESSION_DAILY
            val scopeMissing = if (input.scope == AccessibilityObservationScope.REENTRY) adapted.missing else
                adapted.missing.filter { it == AccessibilityMissingFact.CAPABILITY_INCONSISTENCY }.toSet()
            when {
                currentRevision != input.identity.policyRevision || evaluation.identity != input.identity ||
                    currentGeneration != input.identity.foregroundGeneration ->
                    AccessibilityShadowResult(AccessibilityShadowStatus.STALE, projection, evaluation, "identity/revision changed")
                scopeMissing.isNotEmpty() || evaluation.insufficientFacts.any {
                    input.scope == AccessibilityObservationScope.REENTRY || it.rule in setOf(RuleKind.FOCUS, RuleKind.DEEP_FOCUS)
                } -> AccessibilityShadowResult(AccessibilityShadowStatus.INSUFFICIENT_FACTS, projection, evaluation,
                    "missing=$scopeMissing engine=${evaluation.insufficientFacts}")
                input.scope == AccessibilityObservationScope.FOCUS_DEEP_WINDOW -> {
                    val higherIntent = if (evaluation.activeConstraints.any { it == RuleKind.FOCUS || it == RuleKind.DEEP_FOCUS })
                        projection.intent else AccessibilityIntent.ALLOW_ENTRY
                    when {
                        legacy == AccessibilityIntent.FAIL_OPEN && evaluation.explicitExemptions.any { it.rule == RuleKind.DEEP_FOCUS } &&
                            !input.health.overlayGranted -> AccessibilityShadowResult(AccessibilityShadowStatus.EXPECTED_EXECUTION_DIFFERENCE,
                                projection, evaluation, "legacy suspends overlay before checking an exempt Deep target")
                        higherIntent != legacy -> AccessibilityShadowResult(AccessibilityShadowStatus.MISMATCH, projection, evaluation, "Focus/Deep intent differs")
                        evaluation.explicitExemptions.any { it.rule == RuleKind.DEEP_FOCUS } &&
                            evaluation.activeConstraints.any { it == RuleKind.DAILY || it == RuleKind.SESSION } ->
                            AccessibilityShadowResult(AccessibilityShadowStatus.EXPECTED_EXECUTION_DIFFERENCE, projection, evaluation,
                                "Deep exemption is rule-scoped; legacy Deep branch short-circuits lower execution")
                        else -> AccessibilityShadowResult(AccessibilityShadowStatus.MATCH, projection, evaluation)
                    }
                }
                legacy == AccessibilityIntent.CHECK_SESSION_REENTRY && projection.intent == AccessibilityIntent.FAIL_OPEN &&
                    projection.missingCapabilities.isNotEmpty() -> AccessibilityShadowResult(AccessibilityShadowStatus.EXPECTED_EXECUTION_DIFFERENCE,
                        projection, evaluation, "legacy request reaches existing Monitor capability gate; execution remains fail-open")
                projection.intent != legacy -> AccessibilityShadowResult(AccessibilityShadowStatus.MISMATCH, projection, evaluation, "re-entry intent differs")
                knownHandledDaily -> AccessibilityShadowResult(AccessibilityShadowStatus.EXPECTED_EXECUTION_DIFFERENCE, projection, evaluation,
                    "fresh Daily already executed by legacy Monitor; no second execution")
                projection.deferredToMonitor -> AccessibilityShadowResult(AccessibilityShadowStatus.EXPECTED_EXECUTION_DIFFERENCE, projection, evaluation,
                    "normal threshold remains Monitor-owned; not a re-entry gate")
                else -> AccessibilityShadowResult(AccessibilityShadowStatus.MATCH, projection, evaluation)
            }
        } catch (error: Exception) {
            AccessibilityShadowResult(AccessibilityShadowStatus.ERROR, detail = error.javaClass.simpleName)
        }
        if (result.status in setOf(AccessibilityShadowStatus.MISMATCH, AccessibilityShadowStatus.INSUFFICIENT_FACTS,
                AccessibilityShadowStatus.STALE, AccessibilityShadowStatus.ERROR)) {
            val key = "${input.scope} ${result.status} legacy=$legacy engine=${result.projection?.intent} ${result.detail}"
            if (key != lastDiagnostic) {
                try { diagnostic(key) } catch (_: Exception) { /* Optional sink cannot enter execution. */ }
                lastDiagnostic = key
            }
        }
        return result
    }
}
