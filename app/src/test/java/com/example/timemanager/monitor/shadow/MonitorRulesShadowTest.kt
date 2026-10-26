package com.example.timemanager.monitor.shadow

import com.example.timemanager.data.model.InterventionType
import com.example.timemanager.data.model.SessionGatePhase
import com.example.timemanager.domain.intervention.ForegroundEntry
import org.junit.Assert.*
import org.junit.Test

class MonitorRulesShadowTest {
    @Test fun policyRevisionChangesWithoutChangingLedgerOrEpisode() {
        val f = ShadowFixtures()
        val before = f.capture()
        f.shadow.onPolicyChanged()
        val after = f.capture()
        assertEquals(before.revision + 1L, after.revision)
        assertEquals(before.sequence + 1L, after.sequence)
        assertEquals(before.context!!.daily!!.identity, after.context!!.daily!!.identity)
        assertEquals(before.context.session, after.context.session)
    }

    @Test fun generationReusesCoordinatorOrExplicitlyMarksMonitorObservationFallback() {
        val f = ShadowFixtures()
        val observed = f.capture()
        assertEquals(ShadowGenerationSource.COORDINATOR, observed.input!!.generationSource)
        assertEquals(7L, observed.context!!.identity.foregroundGeneration.value)
        f.foreground = null
        val fallback = f.capture()
        assertEquals(ShadowGenerationSource.MONITOR_OBSERVATION, fallback.input!!.generationSource)
        assertEquals(0L, fallback.context!!.identity.foregroundGeneration.value)
        assertTrue(fallback.sequence > observed.sequence)
    }

    @Test fun staleRevisionIsDiagnosticOnly() {
        val f = ShadowFixtures()
        val observation = f.capture()
        f.shadow.onPolicyChanged()
        assertEquals(ShadowComparisonStatus.STALE, f.completeLegacy(observation).status)
        assertEquals(1, f.legacyRunCount)
    }

    @Test fun changedForegroundAndChangedEpisodeYieldInsufficientFactsNotFalseMismatch() {
        val f = ShadowFixtures()
        f.foreground = ForegroundEntry("example.other", 8L)
        val foreground = f.capture()
        assertEquals(ShadowComparisonStatus.INSUFFICIENT_SHADOW_FACTS, f.completeLegacy(foreground).status)
        f.foreground = null
        val before = f.friction.snapshot(f.pkg)
        f.friction.onActualUsage(f.pkg, 601L)
        val episode = f.shadow.capture(f.pkg, 600L, f.usage, f.limit(), f.evaluation(), before, true)
        assertTrue(ShadowFact.SESSION_CHANGED_DURING_READ in episode.missing)
        assertEquals(ShadowComparisonStatus.INSUFFICIENT_SHADOW_FACTS, f.completeLegacy(episode).status)
    }

    @Test fun bypassAndAdmissionAreNeverConsumedByCaptureOrComparison() {
        val f = ShadowFixtures()
        f.policy = f.policy.copy(dailyLimitMinutes = null)
        val gate = f.friction.beginGate(f.pkg, SessionGatePhase.REMINDER)!!
        f.friction.onReminderShown(f.pkg, gate.id)
        f.now = 6_000L
        assertTrue(f.friction.allowNextEntry(f.pkg, gate.id))
        val before = f.friction.snapshot(f.pkg)
        val observation = f.capture()
        assertEquals(ShadowComparisonStatus.MATCH, f.completeLegacy(observation).status)
        assertEquals(before, f.friction.snapshot(f.pkg))
        assertTrue(f.friction.isReentryGateArmed(f.pkg))
        assertEquals(9_000L, f.friction.snapshot(f.pkg)!!.bypassExpiresAtMillis)
        // Only the real owner consumes it; the following shadow leaves admission intact.
        assertTrue(f.friction.consumeBypass(f.pkg))
        val admitted = f.friction.snapshot(f.pkg)
        assertEquals(ShadowComparisonStatus.MATCH, f.completeLegacy(f.capture()).status)
        assertEquals(admitted, f.friction.snapshot(f.pkg))
        assertTrue(f.friction.isEntryAdmitted(f.pkg))
    }

    @Test fun evaluatorFailureAndDiagnosticSinkFailureDoNotPreventLegacyRun() {
        val f = ShadowFixtures()
        val observer = MonitorRulesShadow(f.source, diagnostic = { error("diagnostic failure") }, evaluate = { error("injected evaluator failure") })
        val before = f.friction.snapshot(f.pkg)
        val result = f.completeLegacy(f.capture(observer), observer)
        assertEquals(ShadowComparisonStatus.ERROR, result.status)
        assertEquals(1, f.legacyRunCount)
        assertEquals(before, f.friction.snapshot(f.pkg))
    }

    @Test fun readFailureIsIsolatedAndMismatchLogsAreDeduplicated() {
        val f = ShadowFixtures()
        val source = LegacyMonitorReadSource("owner", { error("injected read failure") }, { f.day }, f.source.time, f.source.health,
            f.source.daily, f.source.episode, f.source.foreground, f.source.interventionsAllowed, f.source.overlayOccupied)
        var logs = 0
        val observer = MonitorRulesShadow(source, diagnostic = { logs++ })
        repeat(3) { assertEquals(ShadowComparisonStatus.ERROR, f.completeLegacy(f.capture(observer), observer).status) }
        assertEquals(3, f.legacyRunCount)
        assertEquals(1, logs)
    }

    @Test fun comparatorDetectsInjectedBusinessMismatchWithoutMutatingInput() {
        val f = ShadowFixtures()
        val observation = f.capture()
        val input = observation.input!!.copy(legacySessionDue = false)
        val result = ShadowRulesComparator.compare(input, observation.evaluation!!, emptySet(),
            LegacyMonitorOutcome(null, InterventionType.DAILY_LIMIT_REACHED, false, false, false))
        assertEquals(ShadowComparisonStatus.MISMATCH, result.status)
        assertTrue(ShadowDifference.SESSION_DUE in result.differences)
        assertEquals(observation.input!!.episode, f.friction.snapshot(f.pkg))
    }

    @Test fun selectorGatesChangingAfterCaptureAreAnExecutionDeferralNotABusinessMismatch() {
        val f = ShadowFixtures().also { it.usage = 899L }
        val observation = f.capture()
        val result = f.shadow.complete(observation, null, null, dailyAvailable = false, sessionEligible = false, overlayOccupied = false)
        assertEquals(ShadowComparisonStatus.MATCH, result.status)
        assertTrue(ShadowExecutionDeferral.LEGACY_GATE_CHANGED in result.executionDeferredBy)
    }
}
