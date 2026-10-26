package com.example.timemanager.domain.rules

import com.example.timemanager.data.model.InterventionType
import com.example.timemanager.domain.limit.LimitInterventionTriggerState
import com.example.timemanager.domain.limit.UsageLimitEvaluator
import com.example.timemanager.domain.profile.DailyRuleConfig
import com.example.timemanager.service.selectInterventionType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Parity for processForegroundApp's reached guard as well as new display eligibility. */
@RunWith(Parameterized::class)
class DailyContinuousSuppressionParityTest(private val case: Case) {
    data class Case(val name: String, val usage: Long, val previousOver: Boolean,
        val base: Long = 600L, val extension: Long = 0L, val entered: Boolean = false,
        val retryReady: Boolean = false, val sessionDue: Boolean = true, val nextDay: Boolean = false) {
        override fun toString(): String = name
    }

    @Test fun domainSeparatesTheLegacyActiveGuardFromActualDailyDisplay() {
        val f = V090CompatibilityFixtures
        val base = f.context()
        val day = if (case.nextDay) UsageDayKey("2026-10-23") else f.usageDay
        val oldLimit = UsageLimitEvaluator().evaluateSeconds(f.PACKAGE, case.usage, case.base + case.extension)
        val oldTrigger = LimitInterventionTriggerState()
        oldTrigger.evaluate(f.PACKAGE, day.value, case.previousOver)
        if (case.entered) oldTrigger.onManagedAppEntered(f.PACKAGE)
        val oldEdge = oldTrigger.evaluate(f.PACKAGE, day.value, oldLimit.isLimitReached)
        // LimitInterventionManager.evaluate: reached && !overlayShowing && (crossed || entered || retry).
        val oldDisplayEligible = oldLimit.isLimitReached &&
            (oldEdge.limitCrossed || oldEdge.enteredWhileOverLimit || case.retryReady)
        // UsageMonitorService.processForegroundApp passes this OR to selectInterventionType.
        // Here grants/interventionsAllowed are true and no overlay is occupied.
        val oldPriority = selectInterventionType(
            dailyLimitTriggered = oldDisplayEligible || oldLimit.isLimitReached,
            sessionDue = case.sessionDue,
            overlayShowing = false,
        )
        // The old DAILY return can mean "suppress Session", NOT "show another Daily overlay";
        // handleInterventionDecision had already handled the actual display before this helper.
        val expectedAction = when {
            oldDisplayEligible -> RuleKind.DAILY
            oldPriority == InterventionType.SESSION_REMINDER -> RuleKind.SESSION
            else -> null
        }
        val context = base.copy(identity = base.identity.copy(usageDayKey = day),
            effectivePolicy = base.effectivePolicy.copy(appPolicy = base.effectivePolicy.appPolicy.copy(daily = DailyRuleConfig.Explicit(case.base))),
            daily = f.daily(case.usage, case.extension).copy(identity = UsageDayRuntimeIdentity(f.PACKAGE, day),
                edge = DailyEdgeSnapshot(oldEdge.previousOverLimit, case.entered, case.retryReady)),
            session = if (case.sessionDue) f.session() else null)
        val before = context.copy()
        val result = RulesEvaluator().evaluate(context)
        assertEquals(expectedAction, result.selected?.kind)
        assertEquals(oldLimit.isLimitReached, RuleKind.DAILY in result.activeConstraints)
        val allCandidates = listOfNotNull(result.selected) + result.suppressed.map { it.candidate }
        assertEquals(oldDisplayEligible, allCandidates.any { it.kind == RuleKind.DAILY })
        if (case.sessionDue && oldPriority == InterventionType.DAILY_LIMIT_REACHED) {
            assertEquals(SuppressionReason.DAILY_ACTIVE, result.suppressed.single { it.candidate.kind == RuleKind.SESSION }.reason)
        }
        assertEquals(before, context)
        assertEquals(context.identity, result.identity)
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Case>> = listOf(
            Case("below", 599L, false),
            Case("crossing", 600L, false),
            Case("above crossing", 601L, false),
            Case("stable exact", 600L, true),
            Case("stable above", 601L, true),
            Case("over entry", 601L, true, entered = true),
            Case("over retry", 601L, true, retryReady = true),
            Case("extension releases", 600L, true, extension = 900L),
            Case("extended boundary", 1_500L, false, extension = 900L),
            Case("new usage day", 0L, false, nextDay = true),
            Case("zero quota zero use", 0L, true, base = 0L),
            Case("zero quota positive use", 1L, true, base = 0L),
            Case("stable without Session", 601L, true, sessionDue = false),
        ).map { arrayOf(it) }
    }
}
