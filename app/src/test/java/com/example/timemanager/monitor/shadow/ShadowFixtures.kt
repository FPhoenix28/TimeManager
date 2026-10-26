package com.example.timemanager.monitor.shadow

import com.example.timemanager.data.model.*
import com.example.timemanager.domain.intervention.ForegroundEntry
import com.example.timemanager.domain.limit.LimitInterventionManager
import com.example.timemanager.domain.limit.LimitInterventionTriggerState
import com.example.timemanager.domain.limit.UsageLimitEvaluator
import com.example.timemanager.domain.rules.EvaluationTime
import com.example.timemanager.monitor.SessionFrictionManager
import com.example.timemanager.permissions.PermissionHealthState
import com.example.timemanager.service.selectInterventionType

/** In-memory legacy owners only. No context, repositories, settings writes or device commands. */
internal class ShadowFixtures {
    val pkg = "example.managed"
    var day = "2026-10-22"
    var policy = LegacyPolicyInput(true, true, 15, null)
    var usage: Long? = 1_000L
    var extension = 0L
    var health = PermissionHealthState(true, true, true, true, true)
    var allowed = true
    var occupied = false
    var dailyOccupied = false
    var retry = false
    var edgeAvailable = true
    var foreground: ForegroundEntry? = ForegroundEntry(pkg, 7L)
    var diagnosticCount = 0
    var legacyRunCount = 0
    var now = 1_000L
    val trigger = LimitInterventionTriggerState()
    val friction = SessionFrictionManager(clock = { now })
    val source = LegacyMonitorReadSource(
        "example.owner", { policy }, { day }, { EvaluationTime(10_000L, now) }, { health },
        { target, key, over, _ -> if (!edgeAvailable) null else trigger.readSnapshot(target, key).let {
            LimitInterventionManager.RulesReadSnapshot(it.previousOverLimit, it.enteredThisVisit, retry,
                over && (!it.previousOverLimit || it.enteredThisVisit || retry), dailyOccupied)
        } },
        friction::snapshot, { foreground }, { allowed }, { occupied },
    )
    val shadow = MonitorRulesShadow(source, diagnostic = { diagnosticCount++ })

    init {
        friction.beginPhysicalSession(pkg, 600L)
        friction.onActualUsage(pkg, 600L)
    }

    fun limit() = policy.dailyLimitMinutes?.let { EffectiveAppLimit(pkg, it * 60L, extension) }
    fun evaluation() = limit()?.let { limit -> usage?.let { UsageLimitEvaluator().evaluateSeconds(pkg, it, limit.effectiveLimitSeconds) } }
    fun capture(observer: MonitorRulesShadow = shadow) = observer.capture(pkg, 600L, usage, limit(), evaluation(),
        friction.snapshot(pkg), friction.isReminderDue(pkg))

    /** Executes the existing edge owner once, then calls the real legacy priority helper. */
    fun completeLegacy(observation: ShadowObservation, observer: MonitorRulesShadow = shadow): ShadowRuleComparison {
        legacyRunCount++
        val evaluation = evaluation()
        val available = health.canShowSessionReminder && allowed
        val decision = if (evaluation != null && available) {
            val edge = trigger.evaluate(pkg, day, evaluation.isLimitReached)
            if (evaluation.isLimitReached && !dailyOccupied && (edge.limitCrossed || edge.enteredWhileOverLimit || retry)) {
                LimitInterventionDecision.Show(LimitInterventionRequest(packageName = pkg, appName = "Example",
                    todayUsageSeconds = checkNotNull(usage), baseLimitSeconds = checkNotNull(limit()).baseLimitSeconds,
                    extensionSeconds = extension, effectiveLimitSeconds = evaluation.limitSeconds,
                    exceededSeconds = 0L, currentSessionSeconds = 600L, extensionDurationSeconds = 900L))
            } else LimitInterventionDecision.None
        } else null
        val sessionEligible = friction.isReminderDue(pkg) && available
        val selected = selectInterventionType(decision is LimitInterventionDecision.Show ||
            (evaluation?.isLimitReached == true && available), sessionEligible, occupied)
        return observer.complete(observation, decision, selected, available, sessionEligible, occupied)
    }

    fun stableOver() { trigger.evaluate(pkg, day, currentOverLimit = true) }
}
