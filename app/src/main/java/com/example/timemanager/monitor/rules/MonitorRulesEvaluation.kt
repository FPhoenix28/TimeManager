package com.example.timemanager.monitor.rules

import com.example.timemanager.data.model.EffectiveAppLimit
import com.example.timemanager.data.model.SessionFrictionState
import com.example.timemanager.data.model.UsageLimitEvaluation
import com.example.timemanager.domain.rules.*
import com.example.timemanager.monitor.shadow.*

/** All-variant, read-only evaluation. Call capture and consume under Monitor's timingMutex.
 * Revision is Monitor-local, not an atomic revision of all repositories. Historical shadow DTOs
 * and adapter are reused so diagnostics and execution observe exactly the same evaluation.
 */
class MonitorRulesEvaluation(
    private val source: LegacyMonitorReadSource,
    private val evaluate: (RuleEvaluationContext) -> RuleEvaluation = RulesEvaluator()::evaluate,
) {
    private val adapter = LegacyRulesSnapshotAdapter()
    var currentRevision = 0L
        private set
    private var sequence = 0L

    fun onPolicyChanged() { currentRevision++ }

    fun capture(packageName: String, physicalSeconds: Long?, usageSeconds: Long?, effectiveLimit: EffectiveAppLimit?,
        legacyEvaluation: UsageLimitEvaluation?, episode: SessionFrictionState?, sessionDue: Boolean): ShadowObservation {
        val revision = currentRevision
        val capturedSequence = ++sequence
        return try {
            val day = source.usageDay()
            val time = source.time()
            val foreground = source.foreground()
            val exactGeneration = foreground?.takeIf { it.packageName == packageName }?.generation
            val health = source.health()
            val daily = if (effectiveLimit != null) source.daily(packageName, day, legacyEvaluation?.isLimitReached == true, time.wallNowMillis) else null
            val issues = buildSet {
                if (source.episode(packageName) != episode) add(ShadowFact.SESSION_CHANGED_DURING_READ)
                if (foreground != null && foreground.packageName != packageName) add(ShadowFact.FOREGROUND_CHANGED_DURING_READ)
            }
            val input = LegacyMonitorSnapshot(
                EvaluationIdentity(packageName, ForegroundGeneration(exactGeneration ?: 0L), PolicyRevision(revision), UsageDayKey(day), capturedSequence),
                if (exactGeneration == null) ShadowGenerationSource.MONITOR_OBSERVATION else ShadowGenerationSource.COORDINATOR,
                source.ownPackage, time, source.policy(packageName), usageSeconds, effectiveLimit, daily, episode, physicalSeconds,
                health, legacyEvaluation?.isLimitReached, sessionDue,
                ShadowAvailability(health.canShowSessionReminder, source.interventionsAllowed(packageName), source.overlayOccupied(), daily?.displayOccupied == true), issues,
            )
            val adapted = adapter.adapt(input)
            ShadowObservation(revision, capturedSequence, input, adapted.context,
                adapted.context?.let(evaluate), adapted.missing)
        } catch (error: Exception) {
            ShadowObservation(revision, capturedSequence, errorType = error.javaClass.simpleName)
        }
    }
}
