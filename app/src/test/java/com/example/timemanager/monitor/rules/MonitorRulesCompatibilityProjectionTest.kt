package com.example.timemanager.monitor.rules

import com.example.timemanager.domain.rules.*
import com.example.timemanager.monitor.shadow.ShadowFixtures
import org.junit.Assert.*
import org.junit.Test

class MonitorRulesCompatibilityProjectionTest {
    private val open = MonitorExecutionAvailability(true, false)
    private fun project(f: ShadowFixtures, availability: MonitorExecutionAvailability = open) =
        MonitorRulesCompatibilityProjection.project(f.capture().evaluation!!, availability)

    @Test fun belowLimitAllowsSelectedSession() {
        assertEquals(MonitorArbitrationDecision.ALLOW_SESSION, project(ShadowFixtures().also { it.usage = 899L }))
    }

    @Test fun actionableDailySuppressesSessionWithoutRequestingDisplay() {
        val f = ShadowFixtures()
        val evaluation = f.capture().evaluation!!
        assertEquals(RuleKind.DAILY, evaluation.selected!!.kind)
        assertEquals(MonitorArbitrationDecision.SUPPRESS_SESSION_DAILY,
            MonitorRulesCompatibilityProjection.project(evaluation, open))
    }

    @Test fun stableDailySuppressesWithoutInventingCandidate() {
        val f = ShadowFixtures().also { it.stableOver() }
        val evaluation = f.capture().evaluation!!
        assertNull(evaluation.selected)
        assertEquals(MonitorArbitrationDecision.SUPPRESS_SESSION_DAILY,
            MonitorRulesCompatibilityProjection.project(evaluation, open))
        assertTrue(evaluation.suppressed.any { it.candidate is RuleCandidate.Session })
    }

    @Test fun dailyActiveWithoutSessionStillExpressesConstraint() {
        val f = ShadowFixtures().also { it.stableOver(); it.friction.clear() }
        assertEquals(MonitorArbitrationDecision.SUPPRESS_SESSION_DAILY, project(f))
    }

    @Test fun noRulesDueDoesNotRequestSession() {
        val f = ShadowFixtures().also { it.policy = it.policy.copy(dailyLimitMinutes = null); it.friction.clear() }
        assertEquals(MonitorArbitrationDecision.NO_SESSION, project(f))
    }

    @Test fun incompleteDailyUsageCannotBeInterpretedAsUnderLimit() {
        val f = ShadowFixtures().also { it.usage = null }
        assertEquals(MonitorArbitrationDecision.FALLBACK, project(f))
    }

    @Test fun missingOverlayDefersButDoesNotEraseSessionCandidate() {
        val f = ShadowFixtures().also { it.usage = 899L; it.health = it.health.copy(overlayGranted = false) }
        val evaluation = f.capture().evaluation!!
        assertEquals(RuleKind.SESSION, evaluation.selected!!.kind)
        assertEquals(MonitorArbitrationDecision.DEFER,
            MonitorRulesCompatibilityProjection.project(evaluation, open))
    }

    @Test fun occupiedWindowDefersSession() {
        val f = ShadowFixtures().also { it.usage = 899L }
        assertEquals(MonitorArbitrationDecision.DEFER, project(f, open.copy(overlayOccupied = true)))
    }

    @Test fun currentCoordinatorGateDefersSession() {
        val f = ShadowFixtures().also { it.usage = 899L }
        assertEquals(MonitorArbitrationDecision.DEFER, project(f, open.copy(sessionAvailable = false)))
    }

    @Test fun executionDeferralDoesNotClearDailyActive() {
        val f = ShadowFixtures().also { it.stableOver() }
        assertEquals(MonitorArbitrationDecision.SUPPRESS_SESSION_DAILY,
            project(f, MonitorExecutionAvailability(false, true)))
    }

    @Test fun notificationOrAccessibilityAbsenceDoesNotRemoveSoftThresholdEligibility() {
        val f = ShadowFixtures().also {
            it.usage = 899L
            it.health = it.health.copy(notificationGranted = false, accessibilityGranted = false, accessibilityConnected = false)
        }
        assertEquals(MonitorArbitrationDecision.ALLOW_SESSION, project(f))
    }

    @Test fun newEffectiveLimitReleasesDailyConstraintWithoutCacheMutation() {
        val f = ShadowFixtures().also { it.stableOver() }
        assertEquals(MonitorArbitrationDecision.SUPPRESS_SESSION_DAILY, project(f))
        f.extension = 900L
        assertEquals(MonitorArbitrationDecision.ALLOW_SESSION, project(f))
    }
}
