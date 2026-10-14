package com.example.timemanager.monitor

import com.example.timemanager.data.model.SessionGatePhase
import org.junit.Assert.*
import org.junit.Test

class SessionFrictionManagerTest {
    private var now = 1_000L
    private val manager = SessionFrictionManager(clock = { now })

    @Test fun progressionUsesActualUsageAcrossShortDesktopBreaks() {
        manager.beginPhysicalSession("one", 600L)
        manager.onActualUsage("one", 600L)
        val gate = manager.beginGate("one", SessionGatePhase.REMINDER)!!
        manager.onReminderShown("one", gate.id)
        assertEquals(1_080L, manager.snapshot("one")!!.nextReminderAtSeconds)
        manager.endPhysicalSession("one", 600L)
        now += 120_000L
        manager.closeGate("one", gate.id)
        manager.beginPhysicalSession("one", 600L)
        manager.onActualUsage("one", 479L)
        assertFalse(manager.isReminderDue("one"))
        manager.onActualUsage("one", 480L)
        assertTrue(manager.isReminderDue("one"))
        assertEquals(1_080L, manager.snapshot("one")!!.episodeUsageSeconds)
    }

    @Test fun tenEightEightThenFiveAndCustomTwentySchedules() {
        for (first in listOf(600L, 1_200L)) {
            val tracker = SessionFrictionManager(clock = { now })
            tracker.beginPhysicalSession("one", first)
            for (at in listOf(first, first + 480L, first + 960L, first + 1_260L)) {
                tracker.onActualUsage("one", at)
                assertTrue(tracker.isReminderDue("one"))
                val gate = tracker.beginGate("one", SessionGatePhase.REMINDER)!!
                tracker.onReminderShown("one", gate.id)
                tracker.closeGate("one", gate.id)
            }
            assertEquals(first + 1_560L, tracker.snapshot("one")!!.nextReminderAtSeconds)
            assertEquals(4, tracker.snapshot("one")!!.reminderCount)
        }
    }

    @Test fun attemptAtFourthMinuteRestartsIdleObservation() {
        manager.beginPhysicalSession("one", 600L)
        manager.endPhysicalSession("one", 600L)
        now += 240_000L
        manager.onLaunchAttempt("one")
        now += 60_000L
        manager.expireIdleEpisodes()
        assertNotNull(manager.snapshot("one"))
        now += 240_000L
        manager.expireIdleEpisodes()
        assertNull(manager.snapshot("one"))
    }

    @Test fun actuallyForegroundNeverExpiresEvenAfterFiveMinutes() {
        manager.beginPhysicalSession("one", 600L)
        now += 600_000L
        manager.expireIdleEpisodes()
        assertNotNull(manager.snapshot("one"))
        manager.onActualUsage("one", 600L)
        assertEquals(now, manager.snapshot("one")!!.lastEngagementTimeMillis)
    }

    @Test fun voluntaryExitRetainsEpisodeUntilFullIdle() {
        manager.beginPhysicalSession("one", 600L)
        manager.endPhysicalSession("one", 300L)
        now += 299_999L
        assertTrue(manager.expireIdleEpisodes().isEmpty())
        now++
        assertEquals(setOf("one"), manager.expireIdleEpisodes())
        manager.beginPhysicalSession("one", 600L)
        assertEquals(0L, manager.snapshot("one")!!.episodeUsageSeconds)
    }

    @Test fun gateDeadlineIsStableAndCannotCreateEarlyBypass() {
        activate()
        val gate = manager.beginGate("one", SessionGatePhase.REENTRY)!!
        now += 4_999L
        assertEquals(gate, manager.beginGate("one", SessionGatePhase.REENTRY))
        assertFalse(manager.allowNextEntry("one", gate.id))
        now++
        assertTrue(manager.allowNextEntry("one", gate.id))
        assertEquals(now + 3_000L, manager.snapshot("one")!!.bypassExpiresAtMillis)
    }

    @Test fun tokenIsPackageSpecificSingleUseAndVisitAdmissionEndsOnExit() {
        activate()
        val gate = manager.beginGate("one", SessionGatePhase.REENTRY)!!
        now += 5_000L
        assertTrue(manager.allowNextEntry("one", gate.id))
        assertFalse(manager.consumeBypass("other"))
        assertTrue(manager.consumeBypass("one"))
        assertFalse(manager.consumeBypass("one"))
        assertTrue(manager.isEntryAdmitted("one"))
        manager.onForegroundLeft("one")
        assertFalse(manager.isEntryAdmitted("one"))
        assertTrue(manager.snapshot("one")!!.isActive)
    }

    @Test fun expiredTokenDoesNotAdmitEntry() {
        activate()
        val gate = manager.beginGate("one", SessionGatePhase.REENTRY)!!
        now += 5_000L
        manager.allowNextEntry("one", gate.id)
        now += 3_000L
        assertFalse(manager.consumeBypass("one"))
        assertFalse(manager.isEntryAdmitted("one"))
    }

    @Test fun displayFailureDoesNotCommitOrTrapUser() {
        manager.beginPhysicalSession("one", 600L)
        manager.onActualUsage("one", 600L)
        manager.beginGate("one", SessionGatePhase.REMINDER)
        assertEquals(0, manager.snapshot("one")!!.reminderCount)
        manager.failOpen("one")
        assertNull(manager.snapshot("one"))
    }

    @Test fun duplicateCommitAndUsageSamplesAreIdempotent() {
        manager.beginPhysicalSession("one", 600L)
        manager.onActualUsage("one", 600L)
        manager.onActualUsage("one", 600L)
        val gate = manager.beginGate("one", SessionGatePhase.REMINDER)!!
        manager.onReminderShown("one", gate.id)
        manager.onReminderShown("one", gate.id)
        manager.endPhysicalSession("one", 600L)
        manager.endPhysicalSession("one", 600L)
        assertEquals(600L, manager.snapshot("one")!!.episodeUsageSeconds)
        assertEquals(1, manager.snapshot("one")!!.reminderCount)
    }

    @Test fun settingChangeOnlyAffectsEpisodeBeforeFirstReminder() {
        manager.beginPhysicalSession("one", 1_200L)
        manager.onActualUsage("one", 900L)
        manager.updateFirstReminder("one", 600L)
        assertTrue(manager.isReminderDue("one"))
        val gate = manager.beginGate("one", SessionGatePhase.REMINDER)!!
        manager.onReminderShown("one", gate.id)
        manager.updateFirstReminder("one", 1_200L)
        assertEquals(1_080L, manager.snapshot("one")!!.nextReminderAtSeconds)
    }

    @Test fun stayHomeDoesNotCreateAdmissionOrAlterProgression() {
        activate()
        val before = manager.snapshot("one")!!
        val gate = manager.beginGate("one", SessionGatePhase.REENTRY)!!
        manager.closeGate("one", gate.id)
        val after = manager.snapshot("one")!!
        assertNull(after.bypassExpiresAtMillis)
        assertEquals(before.reminderCount, after.reminderCount)
        assertEquals(before.nextReminderAtSeconds, after.nextReminderAtSeconds)
    }

    @Test fun reentryGateIsConsumedForStageEvenWhenUserStaysHome() {
        activate()
        val gate = manager.beginGate("one", SessionGatePhase.REENTRY)!!
        manager.closeGate("one", gate.id)
        now += 120_000L
        manager.onLaunchAttempt("one")
        assertNull("Same reminder stage must not gate again", manager.beginGate("one", SessionGatePhase.REENTRY))
    }

    @Test fun consumedGateCannotReturnAfterBypassVisitEndsOrTokenExpires() {
        activate()
        val gate = manager.beginGate("one", SessionGatePhase.REENTRY)!!
        now += 5_000L
        assertTrue(manager.allowNextEntry("one", gate.id))
        assertTrue(manager.consumeBypass("one"))
        manager.onForegroundLeft("one")
        now += 3_000L
        manager.onLaunchAttempt("one")
        assertNull("Admission ending must not rearm a consumed stage", manager.beginGate("one", SessionGatePhase.REENTRY))
    }

    @Test fun nextCommittedStageRearmsExactlyOneGate() {
        activate()
        val first = manager.beginGate("one", SessionGatePhase.REENTRY)!!
        assertTrue(manager.snapshot("one")!!.reentryGateConsumed)
        manager.closeGate("one", first.id)
        manager.beginPhysicalSession("one", 600L)
        manager.onActualUsage("one", 480L)
        val next = manager.beginGate("one", SessionGatePhase.REMINDER)!!
        assertFalse(manager.snapshot("one")!!.reentryGateArmed)
        manager.onReminderShown("one", next.id)
        assertEquals(2, manager.snapshot("one")!!.reminderCount)
        assertTrue(manager.snapshot("one")!!.reentryGateArmed)
        manager.endPhysicalSession("one", 480L)
        manager.closeGate("one", next.id)
        val second = manager.beginGate("one", SessionGatePhase.REENTRY)!!
        assertNotEquals(first.id, second.id)
        manager.closeGate("one", second.id)
        assertNull(manager.beginGate("one", SessionGatePhase.REENTRY))
    }

    @Test fun expiredUnconsumedBypassStillCannotRearmConsumedStage() {
        activate()
        val gate = manager.beginGate("one", SessionGatePhase.REENTRY)!!
        now += 5_000L
        manager.allowNextEntry("one", gate.id)
        now += 3_000L
        assertFalse(manager.consumeBypass("one"))
        assertFalse(manager.isReentryGateArmed("one"))
        assertNull(manager.beginGate("one", SessionGatePhase.REENTRY))
    }

    @Test fun fullIdleResetsConsumedStageAndNextEpisodeBeginsAtFirstReminder() {
        activate()
        val gate = manager.beginGate("one", SessionGatePhase.REENTRY)!!
        manager.closeGate("one", gate.id)
        now += 300_000L
        assertEquals(setOf("one"), manager.expireIdleEpisodes())
        manager.beginPhysicalSession("one", 600L)
        val state = manager.snapshot("one")!!
        assertEquals(0, state.reminderCount)
        assertEquals(600L, state.nextReminderAtSeconds)
        assertFalse(state.reentryGateArmed)
        assertFalse(state.reentryGateConsumed)
        assertNull(state.bypassExpiresAtMillis)
    }

    private fun activate() {
        manager.beginPhysicalSession("one", 600L)
        manager.onActualUsage("one", 600L)
        val gate = manager.beginGate("one", SessionGatePhase.REMINDER)!!
        manager.onReminderShown("one", gate.id)
        manager.endPhysicalSession("one", 600L)
        manager.closeGate("one", gate.id)
    }
}
