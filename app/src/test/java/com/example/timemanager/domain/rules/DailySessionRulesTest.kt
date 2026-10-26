package com.example.timemanager.domain.rules

import com.example.timemanager.data.model.SessionGatePhase
import com.example.timemanager.domain.profile.DailyRuleConfig
import org.junit.Assert.*
import org.junit.Test

class DailySessionRulesTest {
    private val f = V090CompatibilityFixtures
    private val evaluator = RulesEvaluator()
    private fun daily(base: Long, used: Long, extension: Long = 0L) = f.context().let {
        it.copy(effectivePolicy = it.effectivePolicy.copy(appPolicy = it.effectivePolicy.appPolicy.copy(
            daily = DailyRuleConfig.Explicit(base))), daily = f.daily(used, extension), session = null)
    }
    private fun session() = f.context().let { it.copy(effectivePolicy = it.effectivePolicy.copy(
        appPolicy = it.effectivePolicy.appPolicy.copy(daily = DailyRuleConfig.Unset))) }

    @Test fun unsetQuotaDoesNotInventDailyLimitOrMissingUsage() {
        val result = evaluator.evaluate(session().copy(daily = null, session = null))
        assertNull(result.selected)
        assertTrue(result.insufficientFacts.isEmpty())
    }

    @Test fun explicitZeroWithZeroUseIsNotReachedJustAsLegacyEnforcement() {
        assertNull(evaluator.evaluate(daily(0L, 0L)).selected)
    }

    @Test fun explicitZeroWithPositiveUseIsReached() {
        assertEquals(RuleKind.DAILY, evaluator.evaluate(daily(0L, 1L)).selected?.kind)
    }

    @Test fun dailyBoundaryIsTheExactEffectiveSecond() {
        for (extension in listOf(0L, 900L, 1_800L)) {
            val limit = 3_600L + extension
            for (used in listOf(limit - 1, limit, limit + 1)) {
                val result = evaluator.evaluate(daily(3_600L, used, extension))
                assertEquals("extension=$extension used=$used", used >= limit, result.selected is RuleCandidate.Daily)
            }
        }
    }

    @Test fun overLimitWithoutCrossEntryOrRetryDoesNotInventAnEdge() {
        val context = daily(600L, 700L)
        assertNull(evaluator.evaluate(context.copy(daily = context.daily!!.copy(edge = DailyEdgeSnapshot(true)))).selected)
    }

    @Test fun firstObservationUsesLegacyFalsePreviousBaseline() {
        val selected = evaluator.evaluate(daily(600L, 600L)).selected as RuleCandidate.Daily
        assertEquals(DailyCandidateReason.LIMIT_CROSSED, selected.reason)
    }

    @Test fun firstOverLimitReentryWorksWithPreviousOverTrue() {
        val context = daily(600L, 700L)
        val result = evaluator.evaluate(context.copy(daily = context.daily!!.copy(edge = DailyEdgeSnapshot(true, true))))
        assertEquals(DailyCandidateReason.ENTERED_WHILE_OVER_LIMIT, result.selected?.reason)
    }

    @Test fun suppliedDisplayRetryIsRecognizedWithoutSchedulingOne() {
        val context = daily(600L, 700L)
        val result = evaluator.evaluate(context.copy(daily = context.daily!!.copy(edge = DailyEdgeSnapshot(true, displayRetryReady = true))))
        assertEquals(DailyCandidateReason.DISPLAY_RETRY, result.selected?.reason)
    }

    @Test fun unknownDailyUsageIsNotZeroAndDoesNotBlockSessionEvaluation() {
        val unknown = evaluator.evaluate(f.context().copy(daily = null))
        val zero = evaluator.evaluate(f.context().copy(daily = f.daily(0L)))
        assertEquals(RuleKind.SESSION, unknown.selected?.kind)
        assertEquals(setOf(RuleFactGap(RuleKind.DAILY, MissingRuleFact.DAILY_USAGE)), unknown.insufficientFacts)
        assertTrue(zero.insufficientFacts.isEmpty())
    }

    @Test fun unavailableDailyEdgeDoesNotBecomeACrossing() {
        val context = daily(600L, 700L)
        val result = evaluator.evaluate(context.copy(daily = context.daily!!.copy(edge = DailyEdgeSnapshot(observationAvailable = false))))
        assertNull(result.selected)
        assertEquals(setOf(RuleFactGap(RuleKind.DAILY, MissingRuleFact.DAILY_EDGE)), result.insufficientFacts)
    }

    @Test fun sessionThresholdReadsSnapshotNotConfiguredDefaultOrPhysicalSeconds() {
        val base = session()
        for (used in listOf(1_079L, 1_080L, 1_081L)) {
            val snapshot = base.session!!.copy(episode = base.session.episode.copy(
                episodeUsageSeconds = used, reminderCount = 1, nextReminderAtSeconds = 1_080L),
                physicalSession = PhysicalSessionSnapshot(2L, true))
            assertEquals(used >= 1_080L, evaluator.evaluate(base.copy(session = snapshot)).selected != null)
        }
    }

    @Test fun pendingReminderAndReentrySuppressDuplicatesBeforeAndAfterCooldown() {
        val base = session()
        for (phase in SessionGatePhase.entries) for (deadline in listOf(999L, 6_000L)) {
            val snapshot = base.session!!.copy(episode = base.session.episode.copy(
                pendingGate = PendingSessionGateSnapshot(1L, phase, deadline, false)))
            assertNull(evaluator.evaluate(base.copy(session = snapshot, event = EvaluationEvent.APP_ENTERED)).selected)
            assertEquals(1L, snapshot.episode.pendingGate!!.gateId)
        }
    }

    @Test fun absentSessionIsKnownAbsenceNotAnInsufficientFact() {
        val result = evaluator.evaluate(session().copy(session = null))
        assertNull(result.selected)
        assertTrue(result.insufficientFacts.isEmpty())
    }

    @Test fun armedEntryProducesReentryCandidateEvenBelowNextReminder() {
        val base = session()
        val snapshot = base.session!!.copy(episode = base.session.episode.copy(nextReminderAtSeconds = 1_080L),
            admission = SessionAdmissionSnapshot(reentryGateArmed = true))
        assertEquals(SessionCandidateReason.REENTRY_GATE_ELIGIBLE,
            evaluator.evaluate(base.copy(session = snapshot, event = EvaluationEvent.APP_ENTERED)).selected?.reason)
        assertNull(evaluator.evaluate(base.copy(session = snapshot)).selected)
    }

    @Test fun liveBypassAndVisitAdmissionSuppressEntryWithoutConsumption() {
        val base = session()
        for (admission in listOf(SessionAdmissionSnapshot(true, 2_000L), SessionAdmissionSnapshot(true, foregroundAdmitted = true))) {
            val snapshot = base.session!!.copy(admission = admission)
            assertNull(evaluator.evaluate(base.copy(session = snapshot, event = EvaluationEvent.APP_ENTERED)).selected)
            assertEquals(admission, snapshot.admission)
        }
    }

    @Test fun expiryIsExclusiveAndDoesNotClearToken() {
        val base = session()
        val snapshot = base.session!!.copy(admission = SessionAdmissionSnapshot(true, 1_000L))
        assertEquals(SessionCandidateReason.REENTRY_GATE_ELIGIBLE,
            evaluator.evaluate(base.copy(session = snapshot, event = EvaluationEvent.APP_ENTERED)).selected?.reason)
        assertEquals(1_000L, snapshot.admission.bypassExpiresAtElapsedMillis)
    }

    @Test fun admissionDoesNotSuppressNextReminderDuringActualContinuedUse() {
        val base = session()
        val snapshot = base.session!!.copy(admission = SessionAdmissionSnapshot(foregroundAdmitted = true))
        assertEquals(SessionCandidateReason.EPISODE_THRESHOLD_REACHED, evaluator.evaluate(base.copy(session = snapshot)).selected?.reason)
    }

    @Test fun dailySelectionDoesNotAdvanceOrConsumeSuppressedEpisode() {
        val base = daily(600L, 600L).copy(session = f.session())
        val before = base.copy()
        val result = evaluator.evaluate(base)
        assertEquals(RuleKind.DAILY, result.selected?.kind)
        assertEquals(RuleKind.SESSION, result.suppressed.single().candidate.kind)
        assertEquals(before, base)
        assertEquals(0, base.session!!.episode.reminderCount)
        assertEquals(600L, base.session.episode.nextReminderAtSeconds)
        assertNull(base.session.episode.pendingGate)
        assertFalse(base.daily!!.notificationReceipts.reachedSent)
        assertEquals(0L, base.daily.todayExtensionSeconds)
    }

    @Test fun unmanagedSkipsDailyAndSessionWithoutTouchingSnapshots() {
        val base = daily(600L, 600L).copy(session = f.session())
        val context = base.copy(effectivePolicy = base.effectivePolicy.copy(appPolicy = base.effectivePolicy.appPolicy.copy(managed = false)))
        assertNull(evaluator.evaluate(context).selected)
        assertSame(base.session, context.session)
    }
}
