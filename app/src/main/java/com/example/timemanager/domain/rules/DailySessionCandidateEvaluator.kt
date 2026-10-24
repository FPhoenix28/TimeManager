package com.example.timemanager.domain.rules

import com.example.timemanager.domain.limit.UsageLimitEvaluator
import com.example.timemanager.domain.profile.DailyRuleConfig

internal object DailySessionCandidateEvaluator {
    fun evaluate(context: RuleEvaluationContext): CandidateFacts {
        if (!context.effectivePolicy.appPolicy.managed) return CandidateFacts()
        val daily = daily(context)
        val session = session(context)
        return CandidateFacts(daily.candidates + session.candidates, gaps = daily.gaps + session.gaps,
            activeConstraints = daily.activeConstraints + session.activeConstraints)
    }

    private fun daily(context: RuleEvaluationContext): CandidateFacts {
        val config = context.effectivePolicy.appPolicy.daily as? DailyRuleConfig.Explicit
            ?: return CandidateFacts()
        val runtime = context.daily ?: return CandidateFacts(gaps = listOf(
            RuleFactGap(RuleKind.DAILY, MissingRuleFact.DAILY_USAGE)))
        val reached = UsageLimitEvaluator().evaluateSeconds(context.identity.packageName,
            runtime.usageSeconds, config.baseLimitSeconds + runtime.todayExtensionSeconds).isLimitReached
        if (!reached) return CandidateFacts()
        if (!runtime.edge.observationAvailable) return CandidateFacts(gaps = listOf(
            RuleFactGap(RuleKind.DAILY, MissingRuleFact.DAILY_EDGE)), activeConstraints = setOf(RuleKind.DAILY))
        // Read the owner's edge facts; do not track a previous state or consume entry here.
        val reason = when {
            runtime.edge.previousOverLimit != true -> DailyCandidateReason.LIMIT_CROSSED
            runtime.edge.enteredThisVisit -> DailyCandidateReason.ENTERED_WHILE_OVER_LIMIT
            runtime.edge.displayRetryReady -> DailyCandidateReason.DISPLAY_RETRY
            else -> return CandidateFacts(activeConstraints = setOf(RuleKind.DAILY))
        }
        return CandidateFacts(candidates = listOf(RuleCandidate.Daily(config.baseLimitSeconds, runtime, reason)))
    }

    private fun session(context: RuleEvaluationContext): CandidateFacts {
        val runtime = context.session ?: return CandidateFacts() // Known absence, not unknown usage.
        val episode = runtime.episode
        if (episode.pendingGate != null) return CandidateFacts() // Includes elapsed/ready-but-open gates.
        val entering = context.event == EvaluationEvent.APP_ENTERED
        val admission = runtime.admission
        val validBypass = admission.bypassExpiresAtElapsedMillis?.let { context.time.elapsedNowMillis < it } == true
        if (entering && (validBypass || admission.foregroundAdmitted)) return CandidateFacts()
        val reason = when {
            entering && admission.reentryGateArmed -> SessionCandidateReason.REENTRY_GATE_ELIGIBLE
            episode.episodeUsageSeconds >= episode.nextReminderAtSeconds -> SessionCandidateReason.EPISODE_THRESHOLD_REACHED
            else -> return CandidateFacts()
        }
        return CandidateFacts(candidates = listOf(RuleCandidate.Session(runtime, reason)))
    }
}
