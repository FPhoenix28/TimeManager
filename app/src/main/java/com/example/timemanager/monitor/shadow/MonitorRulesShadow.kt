package com.example.timemanager.monitor.shadow

import com.example.timemanager.data.model.EffectiveAppLimit
import com.example.timemanager.data.model.InterventionType
import com.example.timemanager.data.model.LimitInterventionDecision
import com.example.timemanager.data.model.SessionFrictionState
import com.example.timemanager.data.model.UsageLimitEvaluation
import com.example.timemanager.domain.intervention.ForegroundEntry
import com.example.timemanager.domain.limit.LimitInterventionManager
import com.example.timemanager.domain.rules.*
import com.example.timemanager.permissions.PermissionHealthState
import com.example.timemanager.monitor.rules.MonitorRulesEvaluation

/** Only read callbacks are accepted; there is no HOME, overlay or transition command handle. */
class LegacyMonitorReadSource(
    val ownPackage: String,
    val policy: (String) -> LegacyPolicyInput,
    val usageDay: () -> String,
    val time: () -> EvaluationTime,
    val health: () -> PermissionHealthState,
    val daily: (String, String, Boolean, Long) -> LimitInterventionManager.RulesReadSnapshot?,
    val episode: (String) -> SessionFrictionState?,
    val foreground: () -> ForegroundEntry?,
    val interventionsAllowed: (String) -> Boolean,
    val overlayOccupied: () -> Boolean,
)

data class ShadowObservation(
    val revision: Long,
    val sequence: Long,
    val input: LegacyMonitorSnapshot? = null,
    val context: RuleEvaluationContext? = null,
    val evaluation: RuleEvaluation? = null,
    val missing: Set<ShadowFact> = emptySet(),
    val errorType: String? = null,
)

/** Debug comparison facade. Evaluation can be shared with the authoritative all-variant path. */
class MonitorRulesShadow(
    private val captureSource: MonitorRulesEvaluation,
    private val diagnostic: (String) -> Unit = {},
) {
    constructor(source: LegacyMonitorReadSource, diagnostic: (String) -> Unit = {},
        evaluate: (RuleEvaluationContext) -> RuleEvaluation = RulesEvaluator()::evaluate) :
        this(MonitorRulesEvaluation(source, evaluate), diagnostic)

    private var lastDiagnostic: String? = null

    fun onPolicyChanged() = captureSource.onPolicyChanged()

    fun capture(packageName: String, physicalSeconds: Long?, usageSeconds: Long?, effectiveLimit: EffectiveAppLimit?,
        legacyEvaluation: UsageLimitEvaluation?, episode: SessionFrictionState?, sessionDue: Boolean): ShadowObservation {
        return captureSource.capture(packageName, physicalSeconds, usageSeconds, effectiveLimit, legacyEvaluation, episode, sessionDue)
    }

    fun complete(observation: ShadowObservation, dailyDecision: LimitInterventionDecision?,
        selectedPriority: InterventionType?, dailyAvailable: Boolean, sessionEligible: Boolean, overlayOccupied: Boolean): ShadowRuleComparison {
        val result = try {
            val input = observation.input
            val evaluation = observation.evaluation
            when {
                observation.errorType != null -> ShadowRuleComparison(ShadowComparisonStatus.ERROR)
                observation.revision != captureSource.currentRevision -> ShadowRuleComparison(ShadowComparisonStatus.STALE)
                input == null || evaluation == null -> ShadowRuleComparison(ShadowComparisonStatus.INSUFFICIENT_SHADOW_FACTS, missingFacts = observation.missing)
                else -> ShadowRulesComparator.compare(input, evaluation, observation.missing,
                    LegacyMonitorOutcome(dailyDecision, selectedPriority, dailyAvailable, sessionEligible, overlayOccupied))
            }
        } catch (_: Exception) {
            ShadowRuleComparison(ShadowComparisonStatus.ERROR)
        }
        // Only abnormal transitions are logged. A diagnostic sink failure cannot reach monitoring.
        val key = if (result.status == ShadowComparisonStatus.MATCH) null else
            "${result.status} differences=${result.differences} missing=${result.missingFacts} error=${observation.errorType}"
        if (key != null && key != lastDiagnostic) {
            try {
                diagnostic("$key revision=${observation.revision} sequence=${observation.sequence} generationSource=${observation.input?.generationSource}")
            } catch (_: Exception) { /* Diagnostics never affect legacy execution. */ }
        }
        lastDiagnostic = key
        return result
    }
}
