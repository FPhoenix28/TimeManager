package com.example.timemanager.monitor.rules

import com.example.timemanager.data.model.InterventionType
import com.example.timemanager.domain.rules.*
import com.example.timemanager.monitor.shadow.ShadowObservation

/** SUPPRESS_SESSION_DAILY is an arbitration sentinel, NEVER a Daily display request. */
enum class MonitorArbitrationDecision { ALLOW_SESSION, SUPPRESS_SESSION_DAILY, NO_SESSION, DEFER, FALLBACK }
enum class MonitorFallbackReason { EVALUATION_ERROR, INSUFFICIENT_FACTS, STALE, INVARIANT }

/** Execution gates are sampled after the existing Daily execution, without changing business facts. */
data class MonitorExecutionAvailability(val sessionAvailable: Boolean, val overlayOccupied: Boolean)

object MonitorRulesCompatibilityProjection {
    fun project(evaluation: RuleEvaluation, availability: MonitorExecutionAvailability): MonitorArbitrationDecision = when {
        evaluation.insufficientFacts.isNotEmpty() -> MonitorArbitrationDecision.FALLBACK
        RuleKind.DAILY in evaluation.activeConstraints -> MonitorArbitrationDecision.SUPPRESS_SESSION_DAILY
        evaluation.selected !is RuleCandidate.Session -> MonitorArbitrationDecision.NO_SESSION
        !availability.sessionAvailable || availability.overlayOccupied ||
            evaluation.requiredCapabilities.any { it.candidate == evaluation.selected } -> MonitorArbitrationDecision.DEFER
        else -> MonitorArbitrationDecision.ALLOW_SESSION
    }
}

data class MonitorArbitrationResult(
    val decision: MonitorArbitrationDecision,
    val fallbackReason: MonitorFallbackReason? = null,
    val legacyPriority: InterventionType? = null,
    val reverseMismatch: Boolean = false,
) {
    val allowsSession: Boolean get() = decision == MonitorArbitrationDecision.ALLOW_SESSION
}

/** One-iteration rollout policy. Owns no Daily edges, episode stages, extension or actions.
 * The legacy callback must be the pure selector over already sampled iteration facts.
 * It is invoked only for safety fallback or optional Debug reverse comparison.
 */
class MonitorRulesArbitrator(private val diagnostic: (String) -> Unit = {}) {
    private var lastDiagnostic: String? = null

    fun decide(observation: ShadowObservation, currentRevision: Long,
        availability: MonitorExecutionAvailability, legacySelection: () -> InterventionType?,
        reverseShadow: Boolean = false): MonitorArbitrationResult {
        val evaluation = observation.evaluation
        val context = observation.context
        val fallback = when {
            observation.errorType != null -> MonitorFallbackReason.EVALUATION_ERROR
            observation.revision != currentRevision -> MonitorFallbackReason.STALE
            context == null || evaluation == null || observation.input == null ||
                observation.missing.isNotEmpty() || evaluation.insufficientFacts.isNotEmpty() -> MonitorFallbackReason.INSUFFICIENT_FACTS
            evaluation.identity != context.identity || evaluation.identity != observation.input.identity ||
                context.event != EvaluationEvent.TICK || context.focus != FocusRuntimeSnapshot.Off ||
                evaluation.activeConstraints.any { it == RuleKind.FOCUS || it == RuleKind.DEEP_FOCUS } ||
                evaluation.selected is RuleCandidate.Focus || evaluation.selected is RuleCandidate.DeepFocus ||
                evaluation.selected is RuleCandidate.Daily && RuleKind.DAILY !in evaluation.activeConstraints ||
                evaluation.selected is RuleCandidate.Session && RuleKind.DAILY in evaluation.activeConstraints -> MonitorFallbackReason.INVARIANT
            else -> null
        }
        // No stateful owner/evaluator is called again, even on fallback.
        val legacy = if (fallback != null || reverseShadow) legacySelection() else null
        val decision = if (fallback != null) when (legacy) {
            InterventionType.SESSION_REMINDER -> MonitorArbitrationDecision.ALLOW_SESSION
            InterventionType.DAILY_LIMIT_REACHED -> MonitorArbitrationDecision.SUPPRESS_SESSION_DAILY
            else -> MonitorArbitrationDecision.NO_SESSION
        } else MonitorRulesCompatibilityProjection.project(checkNotNull(evaluation), availability)
        // Compare executable Session admission, not Daily sentinel vs display request.
        val mismatch = fallback == null && reverseShadow &&
            (decision == MonitorArbitrationDecision.ALLOW_SESSION) != (legacy == InterventionType.SESSION_REMINDER)
        val key = when {
            fallback != null -> "ENGINE_FALLBACK_LEGACY reason=$fallback error=${observation.errorType} missing=${observation.missing}"
            mismatch -> "ENGINE_REVERSE_SHADOW_MISMATCH engine=$decision legacy=$legacy"
            else -> null
        }
        if (key != null && key != lastDiagnostic) {
            try { diagnostic("$key revision=${observation.revision} sequence=${observation.sequence}") }
            catch (_: Exception) { /* Optional diagnostics cannot interrupt monitoring. */ }
        }
        lastDiagnostic = key
        return MonitorArbitrationResult(decision, fallback, legacy, mismatch)
    }
}
