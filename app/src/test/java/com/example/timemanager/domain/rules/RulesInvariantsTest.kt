package com.example.timemanager.domain.rules

import com.example.timemanager.data.model.FocusModeStatus
import com.example.timemanager.domain.profile.DailyRuleConfig
import com.example.timemanager.domain.profile.ProfileId
import com.example.timemanager.domain.profile.FirstSessionReminder
import com.example.timemanager.domain.profile.SessionRuleConfig
import com.example.timemanager.permissions.AccessibilityRuntimeState
import org.junit.Assert.*
import org.junit.Test

class RulesInvariantsTest {
    private val f = V090CompatibilityFixtures
    private val evaluator = RulesEvaluator()
    private fun focus() = FocusRuntimeSnapshot.Focus(1L, 1_900_000_000_000L, setOf(f.PACKAGE))
    private fun deep() = FocusRuntimeSnapshot.DeepFocus(1L, 1_900_000_000_000L, FocusModeStatus.ACTIVE)

    @Test fun deepEssentialExemptionRecompetesAllRemainingCombinations() {
        val base = f.context()
        for (disposition in TargetDisposition.entries.filter { it != TargetDisposition.ORDINARY_APP && it != TargetDisposition.UNCLASSIFIED }) {
            for (mask in 0..7) {
                val daily = mask and 2 != 0
                val session = mask and 1 != 0
                val ordinaryFocus = mask and 4 != 0
                val context = base.copy(
                    target = base.target.copy(disposition = disposition),
                    effectivePolicy = base.effectivePolicy.copy(appPolicy = base.effectivePolicy.appPolicy.copy(
                        daily = if (daily) DailyRuleConfig.Explicit(3_600L) else DailyRuleConfig.Unset)),
                    daily = if (daily) f.daily(3_600L) else null,
                    session = if (session) f.session() else null,
                    focus = if (ordinaryFocus) FocusRuntimeSnapshot.Concurrent(focus(), deep()) else deep(),
                )
                val result = evaluator.evaluate(context)
                val expected = when {
                    ordinaryFocus && disposition != TargetDisposition.OWN_APP -> RuleKind.FOCUS
                    daily -> RuleKind.DAILY
                    session -> RuleKind.SESSION
                    else -> null
                }
                assertEquals("$disposition/$mask", expected, result.selected?.kind)
                assertEquals(listOf(RuleExemption(RuleKind.DEEP_FOCUS, disposition)), result.explicitExemptions)
                assertFalse(RuleKind.DEEP_FOCUS in result.activeConstraints)
                val candidates = listOfNotNull(result.selected) + result.suppressed.map { it.candidate }
                assertEquals(daily, candidates.any { it.kind == RuleKind.DAILY })
                assertEquals(session, candidates.any { it.kind == RuleKind.SESSION })
            }
        }
    }

    @Test fun unknownDeepClassificationAndMissingDailyUsageDoNotEraseIndependentFocusAndSession() {
        val base = f.context()
        val result = evaluator.evaluate(base.copy(daily = null, focus = FocusRuntimeSnapshot.Concurrent(focus(), deep()),
            target = base.target.copy(disposition = TargetDisposition.UNCLASSIFIED)))
        assertEquals(RuleKind.FOCUS, result.selected?.kind)
        assertEquals(RuleKind.SESSION, result.suppressed.single().candidate.kind)
        assertEquals(setOf(RuleFactGap(RuleKind.DEEP_FOCUS, MissingRuleFact.TARGET_CLASSIFICATION),
            RuleFactGap(RuleKind.DAILY, MissingRuleFact.DAILY_USAGE)), result.insufficientFacts)
    }

    @Test fun usageDayChangeResetsNeitherEpisodeNorItsStage() {
        val base = f.context().let { it.copy(session = it.session!!.copy(episode = it.session.episode.copy(
            reminderCount = 2, episodeUsageSeconds = 1_560L, nextReminderAtSeconds = 1_560L))) }
        val nextDay = UsageDayKey("2026-10-23")
        val next = base.copy(identity = base.identity.copy(usageDayKey = nextDay),
            daily = f.daily(0L).copy(identity = UsageDayRuntimeIdentity(f.PACKAGE, nextDay)),
            event = EvaluationEvent.USAGE_DAY_CHANGED)
        val before = evaluator.evaluate(base).selected as RuleCandidate.Session
        val after = evaluator.evaluate(next).selected as RuleCandidate.Session
        assertSame(before.runtime, after.runtime)
        assertEquals(2, after.runtime.episode.reminderCount)
        assertEquals(1_560L, after.runtime.episode.nextReminderAtSeconds)
        assertEquals(nextDay, evaluator.evaluate(next).identity.usageDayKey)
    }

    @Test fun equivalentInputsProduceFieldEqualResultsWithoutMutatingAnySnapshot() {
        val base = f.context().copy(daily = f.daily(3_600L), focus = FocusRuntimeSnapshot.Concurrent(focus(), deep()))
        val equivalent = base.copy(focus = FocusRuntimeSnapshot.Concurrent(focus(), deep()),
            daily = base.daily!!.copy(), session = base.session!!.copy())
        val results = listOf(evaluator.evaluate(base), evaluator.evaluate(base), evaluator.evaluate(equivalent))
        for (result in results.drop(1)) {
            assertEquals(results.first().identity, result.identity)
            assertEquals(results.first().selected, result.selected)
            assertEquals(results.first().suppressed, result.suppressed)
            assertEquals(results.first().explicitExemptions, result.explicitExemptions)
            assertEquals(results.first().insufficientFacts, result.insufficientFacts)
            assertEquals(results.first().requiredCapabilities, result.requiredCapabilities)
            assertEquals(results.first().activeConstraints, result.activeConstraints)
        }
        assertEquals(base.daily, equivalent.daily)
        assertEquals(base.session, equivalent.session)
        assertEquals(0, base.session!!.episode.reminderCount)
        assertFalse(base.daily!!.notificationReceipts.warningSent)
    }

    @Test fun repeatedEntryEvaluationDoesNotConsumeTheGateOrToken() {
        val base = f.context().let { it.copy(session = it.session!!.copy(
            admission = SessionAdmissionSnapshot(reentryGateArmed = true)), event = EvaluationEvent.APP_ENTERED) }
        repeat(2) {
            assertEquals(SessionCandidateReason.REENTRY_GATE_ELIGIBLE, evaluator.evaluate(base).selected?.reason)
        }
        assertTrue(base.session!!.admission.reentryGateArmed)
        assertEquals(0, base.session.episode.reminderCount)
    }

    @Test fun profileRevisionChangeDoesNotForkTheSharedLedgerOrEpisode() {
        val base = f.context()
        val next = base.copy(effectivePolicy = base.effectivePolicy.copy(sourceProfileId = ProfileId("work")),
            identity = base.identity.copy(policyRevision = PolicyRevision(3L)))
        assertEquals(base.daily!!.identity, next.daily!!.identity)
        assertSame(base.session, next.session)
        assertEquals(3L, evaluator.evaluate(next).identity.policyRevision.value)
        assertEquals(2L, evaluator.evaluate(base).identity.policyRevision.value)
        assertEquals(base.identity.evaluationSequence, evaluator.evaluate(next).identity.evaluationSequence)
        assertEquals(base.identity.foregroundGeneration, evaluator.evaluate(next).identity.foregroundGeneration)
    }

    @Test fun notificationReceiptsDoNotDisableDailyOverlayEdgesOrGetWritten() {
        val base = f.context().copy(daily = f.daily(3_600L).copy(notificationReceipts = DailyNotificationReceipts(true, true)))
        val result = evaluator.evaluate(base)
        assertEquals(RuleKind.DAILY, result.selected?.kind)
        assertEquals(DailyNotificationReceipts(true, true), base.daily!!.notificationReceipts)
    }

    @Test fun accessibilityLossKeepsSoftCandidatesSelectedWhenTheyAreTheOnlyRules() {
        for (state in AccessibilityRuntimeState.entries) {
            val base = f.context().let { it.copy(capabilities = it.capabilities.copy(accessibility = state)) }
            assertEquals(RuleKind.SESSION, evaluator.evaluate(base).selected?.kind)
            val daily = evaluator.evaluate(base.copy(daily = f.daily(3_600L)))
            assertEquals(RuleKind.DAILY, daily.selected?.kind)
            assertTrue(daily.requiredCapabilities.isEmpty())
        }
    }

    @Test fun suppliedStageWinsOverNewProfileFirstReminderSetting() {
        val base = f.context()
        val pendingLaterStage = base.session!!.copy(episode = base.session.episode.copy(
            reminderCount = 2, episodeUsageSeconds = 1_559L, nextReminderAtSeconds = 1_560L))
        val newPolicy = base.effectivePolicy.copy(appPolicy = base.effectivePolicy.appPolicy.copy(
            session = SessionRuleConfig(FirstSessionReminder.Explicit(1_200L))))
        assertNull(evaluator.evaluate(base.copy(session = pendingLaterStage, effectivePolicy = newPolicy)).selected)
        assertEquals(2, pendingLaterStage.episode.reminderCount)
    }
}
