package com.example.timemanager.monitor.shadow

import com.example.timemanager.domain.rules.RuleKind
import com.example.timemanager.domain.rules.SuppressionReason
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class MonitorShadowParityTest(private val scenario: String) {
    @Test fun monitorInputsMatchLegacyBusinessFactsSeparatelyFromExecutionDeferral() {
        val f = ShadowFixtures()
        when (scenario) {
            "below" -> f.usage = 899L
            "crossing" -> f.usage = 900L
            "entered over" -> { f.stableOver(); f.trigger.onManagedAppEntered(f.pkg) }
            "retry" -> { f.stableOver(); f.retry = true }
            "stable over" -> f.stableOver()
            "extension release" -> { f.stableOver(); f.extension = 900L }
            "daily window occupied" -> f.dailyOccupied = true
            "session window occupied" -> { f.usage = 899L; f.occupied = true }
            "capability missing" -> f.health = f.health.copy(overlayGranted = false)
            "coordinator gate" -> { f.usage = 899L; f.allowed = false }
            "no quota" -> f.policy = f.policy.copy(dailyLimitMinutes = null)
            "explicit zero no use" -> { f.policy = f.policy.copy(dailyLimitMinutes = 0); f.usage = 0L }
            "explicit zero positive use" -> { f.policy = f.policy.copy(dailyLimitMinutes = 0); f.stableOver() }
        }
        val beforeEpisode = f.friction.snapshot(f.pkg)
        val beforeEdge = f.trigger.readSnapshot(f.pkg, f.day)
        val beforeExtension = f.extension
        val observation = f.capture()
        assertEquals(beforeEpisode, f.friction.snapshot(f.pkg))
        assertEquals(beforeExtension, f.extension)
        assertEquals(beforeEdge, f.trigger.readSnapshot(f.pkg, f.day))
        val result = f.completeLegacy(observation)
        assertEquals(scenario, ShadowComparisonStatus.MATCH, result.status)
        assertTrue(result.differences.isEmpty())
        assertEquals(0, f.diagnosticCount)
        if (scenario == "stable over") {
            assertTrue(RuleKind.DAILY in observation.evaluation!!.activeConstraints)
            assertNull(observation.evaluation.selected)
            assertEquals(SuppressionReason.DAILY_ACTIVE, observation.evaluation.suppressed.single().reason)
        }
        if (scenario == "extension release") {
            assertFalse(RuleKind.DAILY in observation.evaluation!!.activeConstraints)
            assertEquals(RuleKind.SESSION, observation.evaluation.selected?.kind)
        }
        if (scenario.contains("occupied") || scenario == "capability missing" || scenario == "coordinator gate") {
            assertTrue(result.executionDeferredBy.isNotEmpty())
        }
        assertEquals(beforeEpisode, f.friction.snapshot(f.pkg))
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<String>> = listOf("below", "crossing", "entered over", "retry", "stable over", "extension release",
            "daily window occupied", "session window occupied", "capability missing", "coordinator gate", "no quota",
            "explicit zero no use", "explicit zero positive use").map { arrayOf(it) }
    }
}
