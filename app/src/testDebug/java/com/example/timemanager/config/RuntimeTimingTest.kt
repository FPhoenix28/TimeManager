package com.example.timemanager.config

import com.example.timemanager.data.model.SessionGatePhase
import com.example.timemanager.data.model.SessionReminder
import com.example.timemanager.data.model.TimerState
import com.example.timemanager.monitor.SessionFrictionManager
import com.example.timemanager.monitor.SessionTracker
import org.junit.Assert.*
import org.junit.Test

class RuntimeTimingTest {
    private var now = 0L
    private var config = DebugTimingPresets.fast
    private val provider = TimeConfigProvider { config }
    private fun manager() = SessionFrictionManager(clock = { now }, timeConfigProvider = provider)

    @Test fun fastProgressionUsesActualManagerAndSuccessfulShowCommits() {
        val m = manager()
        m.beginPhysicalSession("app", 20L)
        for ((count, at) in listOf(20L, 35L, 50L, 60L, 70L).withIndex()) {
            m.onActualUsage("app", at - 1L)
            assertFalse(m.isReminderDue("app"))
            m.onActualUsage("app", at)
            assertTrue(m.isReminderDue("app"))
            val gate = m.beginGate("app", SessionGatePhase.REMINDER)!!
            assertEquals(count, m.snapshot("app")!!.reminderCount)
            m.onReminderShown("app", gate.id)
            m.onReminderShown("app", gate.id)
            assertEquals(count + 1, m.snapshot("app")!!.reminderCount)
            m.closeGate("app", gate.id)
        }
        assertEquals(5, m.snapshot("app")!!.reminderCount)
        assertEquals(80L, m.snapshot("app")!!.nextReminderAtSeconds)
    }

    @Test fun firstThresholdEditAppliesBeforeFirstReminderWithoutReset() {
        val m = manager()
        m.beginPhysicalSession("app", 300L)
        m.onActualUsage("app", 25L)
        m.updateFirstReminder("app", 20L)
        assertTrue(m.isReminderDue("app"))
        assertEquals(25L, m.snapshot("app")!!.episodeUsageSeconds)
    }

    @Test fun committedStageDoesNotRewindAndFutureIncrementUsesNewConfig() {
        val m = manager()
        m.beginPhysicalSession("app", 20L)
        m.onActualUsage("app", 20L)
        var gate = m.beginGate("app", SessionGatePhase.REMINDER)!!
        m.onReminderShown("app", gate.id)
        m.closeGate("app", gate.id)
        config = TimeConfig.production()
        m.updateFirstReminder("app", 600L)
        assertEquals(35L, m.snapshot("app")!!.nextReminderAtSeconds)
        assertEquals(1, m.snapshot("app")!!.reminderCount)
        m.onActualUsage("app", 35L)
        gate = m.beginGate("app", SessionGatePhase.REMINDER)!!
        m.onReminderShown("app", gate.id)
        assertEquals(515L, m.snapshot("app")!!.nextReminderAtSeconds)
    }

    @Test fun shownGateDeadlineIsStableWhenConfigChanges() {
        val m = manager()
        m.beginPhysicalSession("app", 20L)
        m.onActualUsage("app", 20L)
        val gate = m.beginGate("app", SessionGatePhase.REMINDER)!!
        assertEquals(2_000L, gate.endTimeMillis)
        config = TimeConfig.production()
        assertEquals(gate, m.snapshot("app")!!.pendingGate)
        now = 1_999L
        assertFalse(m.allowNextEntry("app", gate.id))
        now = 2_000L
        assertTrue(m.allowNextEntry("app", gate.id))
    }

    @Test fun shortenedResetStillRequiresIdleAndTracksLastEngagement() {
        val m = manager()
        m.beginPhysicalSession("app", 20L)
        now = 31_000L
        assertTrue(m.expireIdleEpisodes().isEmpty())
        m.endPhysicalSession("app", 10L)
        now += 29_000L
        assertTrue(m.expireIdleEpisodes().isEmpty())
        m.onLaunchAttempt("app")
        now += 29_000L
        assertTrue(m.expireIdleEpisodes().isEmpty())
        now += 1_000L
        assertEquals(setOf("app"), m.expireIdleEpisodes())
    }

    @Test fun notificationThresholdsAreLiveAndReceiptsAreNotReset() {
        val tracker = SessionTracker(timeConfigProvider = provider)
        assertNull(tracker.update(TimerState("app", "App", 14L)))
        assertEquals(SessionReminder.FIFTEEN_MINUTES, tracker.update(TimerState("app", "App", 15L)))
        assertNull(tracker.update(TimerState("app", "App", 16L)))
        config = TimeConfig.production()
        assertNull(tracker.update(TimerState("app", "App", 30L)))
    }

    @Test fun resettingProviderRestoresAllProductionFields() {
        val m = manager()
        config = TimeConfig.production()
        m.beginPhysicalSession("app", 600L)
        m.onActualUsage("app", 600L)
        val gate = m.beginGate("app", SessionGatePhase.REMINDER)!!
        assertEquals(5_000L, gate.endTimeMillis)
        m.onReminderShown("app", gate.id)
        assertEquals(1_080L, m.snapshot("app")!!.nextReminderAtSeconds)
        assertEquals(900L, provider.current().extensionDurationSeconds)
    }
}
