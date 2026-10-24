package com.example.timemanager.monitor.rules

import com.example.timemanager.data.model.*
import com.example.timemanager.domain.rules.*
import com.example.timemanager.monitor.shadow.*
import com.example.timemanager.service.selectInterventionType
import org.junit.Assert.*
import org.junit.Test

/** Application seam used by Monitor, with real read adapter, evaluator, Daily edge owner and
 * Friction owner. Android Daily/Overlay execution sinks are simulated, not device-tested here.
 */
class MonitorArbitrationBoundaryTest {
    private class Iteration(val f: ShadowFixtures = ShadowFixtures(),
        evaluate: (RuleEvaluationContext) -> RuleEvaluation = RulesEvaluator()::evaluate) {
        val engine = MonitorRulesEvaluation(f.source, evaluate)
        val logs = mutableListOf<String>()
        val arbitrator = MonitorRulesArbitrator(logs::add)
        val reverse = MonitorRulesShadow(engine)
        var edgeEvaluations = 0
        var dailyShows = 0
        var sessionRequests = 0
        var sessionCommits = 0
        var selectorCalls = 0
        var comparisons = 0

        fun capture() = engine.capture(f.pkg, 600L, f.usage, f.limit(), f.evaluation(),
            f.friction.snapshot(f.pkg), f.friction.isReminderDue(f.pkg))

        fun tick(debugComparison: Boolean = true, showSuccess: Boolean = true,
            afterCapture: () -> Unit = {}): MonitorArbitrationResult {
            val due = f.friction.isReminderDue(f.pkg)
            val before = f.friction.snapshot(f.pkg)
            val beforeEdge = f.trigger.readSnapshot(f.pkg, f.day)
            val observation = capture()
            assertEquals(before, f.friction.snapshot(f.pkg))
            assertEquals(beforeEdge, f.trigger.readSnapshot(f.pkg, f.day))
            afterCapture()
            val evaluation = f.evaluation()
            val available = f.health.canShowSessionReminder && f.allowed
            // Existing Daily execution remains before final Session arbitration, once only.
            val decision = if (evaluation != null && available) {
                edgeEvaluations++
                val edge = f.trigger.evaluate(f.pkg, f.day, evaluation.isLimitReached)
                if (evaluation.isLimitReached && !f.dailyOccupied && (edge.limitCrossed || edge.enteredWhileOverLimit || f.retry)) {
                    dailyShows++
                    LimitInterventionDecision.Show(LimitInterventionRequest(packageName = f.pkg, appName = "Example",
                        todayUsageSeconds = f.usage!!, baseLimitSeconds = f.limit()!!.baseLimitSeconds,
                        extensionSeconds = f.extension, effectiveLimitSeconds = evaluation.limitSeconds,
                        exceededSeconds = 0L, currentSessionSeconds = 600L, extensionDurationSeconds = 900L))
                } else LimitInterventionDecision.None
            } else null
            val extensionBefore = f.extension
            val result = arbitrator.decide(observation, engine.currentRevision,
                MonitorExecutionAvailability(available, f.occupied), legacySelection = {
                    selectorCalls++
                    selectInterventionType(decision is LimitInterventionDecision.Show ||
                        (evaluation?.isLimitReached == true && available), due && available, f.occupied)
                }, reverseShadow = debugComparison)
            if (debugComparison) {
                val compared = reverse.complete(observation, decision, result.legacyPriority, available, due && available, f.occupied)
                if (result.fallbackReason == null) {
                    assertEquals(ShadowComparisonStatus.MATCH, compared.status)
                    assertFalse(result.reverseMismatch)
                    comparisons++
                }
            }
            assertEquals(before, f.friction.snapshot(f.pkg)) // Neither arbitration nor reverse shadow commits stages.
            assertEquals(extensionBefore, f.extension)
            if (result.allowsSession) {
                sessionRequests++
                val gate = f.friction.beginGate(f.pkg, SessionGatePhase.REMINDER)!!
                if (showSuccess) {
                    f.friction.onReminderShown(f.pkg, gate.id)
                    sessionCommits++
                } else f.friction.failOpen(f.pkg) // Existing show failure path, not a stage commit.
            }
            return result
        }
    }

    @Test fun belowQuotaUsesEngineAndCommitsOnlyAfterExistingShowSuccess() {
        val r = Iteration().also { it.f.usage = 899L }
        assertTrue(r.tick().allowsSession)
        assertEquals(1, r.edgeEvaluations)
        assertEquals(0, r.dailyShows)
        assertEquals(1, r.sessionRequests)
        assertEquals(1, r.sessionCommits)
        assertEquals(1, r.f.friction.snapshot(r.f.pkg)!!.reminderCount)
        assertEquals(1_080L, r.f.friction.snapshot(r.f.pkg)!!.nextReminderAtSeconds)
        assertEquals(1, r.comparisons)
    }

    @Test fun crossingExecutesOnlyOldDailySinkAndSuppressesSession() {
        val r = Iteration().also { it.f.usage = 900L }
        assertEquals(MonitorArbitrationDecision.SUPPRESS_SESSION_DAILY, r.tick().decision)
        assertEquals(1, r.dailyShows)
        assertEquals(1, r.edgeEvaluations)
        assertEquals(0, r.sessionRequests)
        assertEquals(0, r.f.friction.snapshot(r.f.pkg)!!.reminderCount)
    }

    @Test fun stableOverLimitWithoutDisplayEdgeDoesNotShowAnything() {
        val r = Iteration().also { it.f.stableOver() }
        repeat(2) { assertEquals(MonitorArbitrationDecision.SUPPRESS_SESSION_DAILY, r.tick().decision) }
        assertEquals(2, r.edgeEvaluations)
        assertEquals(0, r.dailyShows)
        assertEquals(0, r.sessionRequests)
        assertEquals(2, r.comparisons)
    }

    @Test fun actualExtensionInputReleasesLimitOnNextIteration() {
        val r = Iteration().also { it.f.stableOver() }
        assertFalse(r.tick().allowsSession)
        r.f.extension = 900L // Represents an already completed legacy +15 action, not an engine write.
        assertTrue(r.tick().allowsSession)
        assertEquals(900L, r.f.extension)
        assertEquals(0, r.dailyShows)
        assertEquals(2, r.edgeEvaluations)
    }

    @Test fun initialConfigNotLoadedFallsBackForOnlyThisIteration() {
        val r = Iteration().also { it.f.usage = 899L; it.f.policy = it.f.policy.copy(loaded = false) }
        val result = r.tick()
        assertEquals(MonitorFallbackReason.INSUFFICIENT_FACTS, result.fallbackReason)
        assertTrue(result.allowsSession)
        assertEquals(1, r.selectorCalls)
        assertEquals(1, r.edgeEvaluations)
        assertTrue(r.logs.single().contains("ENGINE_FALLBACK_LEGACY"))
    }

    @Test fun missingUsageFallsBackWithoutFabricatingDailyUsage() {
        val r = Iteration().also { it.f.usage = null }
        assertEquals(MonitorFallbackReason.INSUFFICIENT_FACTS, r.tick().fallbackReason)
        assertEquals(0, r.edgeEvaluations)
        assertEquals(1, r.selectorCalls)
    }

    @Test fun missingDailyEdgeFallsBackWithoutSecondEdgeEvaluation() {
        val r = Iteration().also { it.f.edgeAvailable = false }
        assertEquals(MonitorFallbackReason.INSUFFICIENT_FACTS, r.tick().fallbackReason)
        assertEquals(1, r.edgeEvaluations)
        assertEquals(1, r.dailyShows)
        assertEquals(0, r.sessionRequests)
    }

    @Test fun evaluatorExceptionFallsBackOnceWithoutDuplicateMutation() {
        var calls = 0
        val r = Iteration(evaluate = { calls++; error("injected evaluator failure") }).also { it.f.usage = 899L }
        val result = r.tick()
        assertEquals(MonitorFallbackReason.EVALUATION_ERROR, result.fallbackReason)
        assertEquals(1, calls)
        assertEquals(1, r.edgeEvaluations)
        assertEquals(1, r.selectorCalls)
        assertEquals(1, r.sessionCommits)
        assertEquals(0L, r.f.extension)
    }

    @Test fun staleRevisionUsesLegacyWithoutRerunningDailyOwner() {
        val r = Iteration().also { it.f.usage = 899L }
        val result = r.tick(afterCapture = r.engine::onPolicyChanged)
        assertEquals(MonitorFallbackReason.STALE, result.fallbackReason)
        assertEquals(1, r.edgeEvaluations)
        assertEquals(1, r.selectorCalls)
    }

    @Test fun occupiedWindowKeepsBusinessSessionButDefersExecution() {
        val r = Iteration().also { it.f.usage = 899L; it.f.occupied = true }
        assertEquals(MonitorArbitrationDecision.DEFER, r.tick().decision)
        assertEquals(0, r.sessionRequests)
        assertEquals(0, r.sessionCommits)
    }

    @Test fun disconnectedAccessibilityKeepsSoftThresholdPath() {
        val r = Iteration().also {
            it.f.usage = 899L
            it.f.health = it.f.health.copy(accessibilityConnected = false)
            it.f.foreground = null
        }
        assertTrue(r.tick().allowsSession)
        assertEquals(1, r.sessionRequests)
        assertEquals(1, r.comparisons)
    }

    @Test fun missingOverlayDefersAndNeverCommitsEpisode() {
        val r = Iteration().also { it.f.usage = 899L; it.f.health = it.f.health.copy(overlayGranted = false) }
        assertEquals(MonitorArbitrationDecision.DEFER, r.tick().decision)
        assertEquals(0, r.edgeEvaluations)
        assertEquals(0, r.sessionCommits)
    }

    @Test fun unsuccessfulExistingShowDoesNotAdvanceStage() {
        val r = Iteration().also { it.f.usage = 899L }
        assertTrue(r.tick(showSuccess = false).allowsSession)
        assertEquals(0, r.sessionCommits)
        assertNull(r.f.friction.snapshot(r.f.pkg))
    }

    @Test fun repeatedDueReadsAndReverseShadowNeverConsumeStageOrPendingEntry() {
        val r = Iteration().also { it.f.usage = 899L; it.f.trigger.onManagedAppEntered(it.f.pkg) }
        val before = r.f.friction.snapshot(r.f.pkg)
        repeat(3) {
            assertTrue(r.f.friction.isReminderDue(r.f.pkg))
            val observation = r.capture()
            r.reverse.complete(observation, null, InterventionType.SESSION_REMINDER, false, true, false)
            assertTrue(r.f.trigger.readSnapshot(r.f.pkg, r.f.day).enteredThisVisit)
            assertEquals(before, r.f.friction.snapshot(r.f.pkg))
        }
        assertEquals(0, r.edgeEvaluations)
    }

    @Test fun allVariantPathWithoutDebugDiagnosticsNeverCallsLegacyOnSuccess() {
        var evaluations = 0
        val r = Iteration(evaluate = { evaluations++; RulesEvaluator().evaluate(it) }).also { it.f.usage = 899L }
        val result = r.tick(debugComparison = false)
        assertTrue(result.allowsSession)
        assertNull(result.fallbackReason)
        assertEquals(1, evaluations)
        assertEquals(0, r.selectorCalls)
        assertEquals(0, r.comparisons)
        assertTrue(r.logs.isEmpty())
    }

    @Test fun allVariantFallbackWorksWithoutDebugComparison() {
        val r = Iteration(evaluate = { error("injected failure") }).also { it.f.usage = 899L }
        assertTrue(r.tick(debugComparison = false).allowsSession)
        assertEquals(1, r.selectorCalls)
        assertEquals(1, r.edgeEvaluations)
    }

    @Test fun ordinaryMismatchKeepsEngineDecisionAndLogsInsteadOfFallingBack() {
        val f = ShadowFixtures().also { it.usage = 899L }
        // Inject a valid engine result with no candidate, not an impossible priority invariant.
        val engine = MonitorRulesEvaluation(f.source, evaluate = { RuleEvaluation(it.identity, null) })
        val observation = engine.capture(f.pkg, 600L, f.usage, f.limit(), f.evaluation(), f.friction.snapshot(f.pkg), true)
        val logs = mutableListOf<String>()
        val result = MonitorRulesArbitrator(logs::add).decide(observation, engine.currentRevision,
            MonitorExecutionAvailability(true, false), { InterventionType.SESSION_REMINDER }, reverseShadow = true)
        assertEquals(MonitorArbitrationDecision.NO_SESSION, result.decision)
        assertTrue(result.reverseMismatch)
        assertNull(result.fallbackReason)
        assertTrue(logs.single().contains("ENGINE_REVERSE_SHADOW_MISMATCH"))
        assertEquals(0, f.friction.snapshot(f.pkg)!!.reminderCount)
    }

    @Test fun impossibleDailyActiveWithSelectedSessionFallsBackCentrally() {
        val f = ShadowFixtures().also { it.usage = 899L }
        val engine = MonitorRulesEvaluation(f.source, evaluate = {
            RuleEvaluation(it.identity, RuleCandidate.Session(it.session!!, SessionCandidateReason.EPISODE_THRESHOLD_REACHED),
                activeConstraints = setOf(RuleKind.DAILY, RuleKind.SESSION))
        })
        val observation = engine.capture(f.pkg, 600L, f.usage, f.limit(), f.evaluation(), f.friction.snapshot(f.pkg), true)
        val result = MonitorRulesArbitrator().decide(observation, engine.currentRevision,
            MonitorExecutionAvailability(true, false), { InterventionType.SESSION_REMINDER })
        assertEquals(MonitorFallbackReason.INVARIANT, result.fallbackReason)
        assertTrue(result.allowsSession)
    }

    @Test fun legitimateDailyExecutionWindowChangeDoesNotBecomeStaleEvaluation() {
        val r = Iteration().also { it.f.usage = 900L }
        val result = r.tick(afterCapture = { r.f.occupied = true })
        assertNull(result.fallbackReason)
        assertEquals(MonitorArbitrationDecision.SUPPRESS_SESSION_DAILY, result.decision)
        assertEquals(1, r.dailyShows)
    }

    @Test fun readAdapterFailureFallsBackWithoutBreakingOldDailyExecution() {
        val f = ShadowFixtures().also { it.usage = 899L; it.policy = it.policy.copy(storedSessionReminderMinutes = 0) }
        val r = Iteration(f)
        assertEquals(MonitorFallbackReason.EVALUATION_ERROR, r.tick().fallbackReason)
        assertEquals(1, r.edgeEvaluations)
        assertEquals(1, r.selectorCalls)
    }

    @Test fun explicitDailyConfigWithMissingEffectiveLimitFallsBack() {
        val f = ShadowFixtures().also { it.usage = 899L }
        val engine = MonitorRulesEvaluation(f.source)
        val observation = engine.capture(f.pkg, 600L, f.usage, null, null, f.friction.snapshot(f.pkg), true)
        var selectorCalls = 0
        val result = MonitorRulesArbitrator().decide(observation, engine.currentRevision,
            MonitorExecutionAvailability(true, false), { selectorCalls++; InterventionType.SESSION_REMINDER })
        assertEquals(MonitorFallbackReason.INSUFFICIENT_FACTS, result.fallbackReason)
        assertTrue(ShadowFact.DAILY_LIMIT_UNAVAILABLE in observation.missing)
        assertEquals(1, selectorCalls)
    }

    @Test fun knownAbsentDailyConfigIsNotMistakenForUnknownLimit() {
        val r = Iteration().also { it.f.policy = it.f.policy.copy(dailyLimitMinutes = null) }
        val result = r.tick()
        assertNull(result.fallbackReason)
        assertTrue(result.allowsSession)
        assertEquals(0, r.edgeEvaluations)
    }

    @Test fun entryAndRetryKeepExistingDailyExecutionAuthority() {
        for (entry in listOf(false, true)) {
            val r = Iteration().also { it.f.stableOver() }
            if (entry) r.f.trigger.onManagedAppEntered(r.f.pkg) else r.f.retry = true
            assertEquals(MonitorArbitrationDecision.SUPPRESS_SESSION_DAILY, r.tick().decision)
            assertEquals(1, r.dailyShows)
            assertEquals(1, r.edgeEvaluations)
            assertEquals(0, r.sessionCommits)
        }
    }

    @Test fun currentExecutionGateCanCloseAfterCaptureWithoutChangingBusinessPriority() {
        val r = Iteration().also { it.f.usage = 899L }
        val result = r.tick(afterCapture = { r.f.allowed = false })
        assertNull(result.fallbackReason)
        assertEquals(MonitorArbitrationDecision.DEFER, result.decision)
        assertEquals(0, r.sessionRequests)
    }

    @Test fun configurationRevisionChangesApplyNextCaptureWithoutEngineCache() {
        val f = ShadowFixtures()
        val engine = MonitorRulesEvaluation(f.source)
        val arbitrator = MonitorRulesArbitrator()
        fun decide(): MonitorArbitrationResult {
            val observation = engine.capture(f.pkg, 600L, f.usage, f.limit(), f.evaluation(), f.friction.snapshot(f.pkg), true)
            return arbitrator.decide(observation, engine.currentRevision, MonitorExecutionAvailability(true, false),
                { error("complete normal path must not call legacy selector") })
        }
        assertEquals(MonitorArbitrationDecision.SUPPRESS_SESSION_DAILY, decide().decision)
        f.policy = f.policy.copy(dailyLimitMinutes = 30)
        engine.onPolicyChanged()
        assertTrue(decide().allowsSession)
        assertEquals(0, f.friction.snapshot(f.pkg)!!.reminderCount)
        assertEquals(0L, f.extension)
    }
}
