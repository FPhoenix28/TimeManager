package com.example.timemanager.domain.rules

import com.example.timemanager.data.model.FocusModeStatus
import com.example.timemanager.data.model.SessionGatePhase
import com.example.timemanager.domain.profile.ProfileId
import com.example.timemanager.permissions.AccessibilityRuntimeState
import org.junit.Assert.*
import org.junit.Test

class RuntimeSnapshotsTest {
    private val fixture = V090CompatibilityFixtures

    @Test fun switchingProfileKeepsPackageDayLedgerAndEpisodeIdentity() {
        val before = fixture.context()
        val after = before.copy(effectivePolicy = before.effectivePolicy.copy(sourceProfileId = ProfileId("work")))
        assertNotEquals(before.effectivePolicy.sourceProfileId, after.effectivePolicy.sourceProfileId)
        assertEquals(before.daily!!.identity, after.daily!!.identity)
        assertSame(before.daily, after.daily)
        assertSame(before.session, after.session)
    }

    @Test fun newUsageDayDoesNotForceANewSessionEpisode() {
        val before = fixture.context()
        val nextDay = UsageDayKey("2026-10-23")
        val after = before.copy(
            identity = before.identity.copy(usageDayKey = nextDay),
            daily = DailyRuntimeSnapshot(UsageDayRuntimeIdentity(fixture.PACKAGE, nextDay), 0L, 0L),
            event = EvaluationEvent.USAGE_DAY_CHANGED,
        )
        assertNotEquals(before.daily!!.identity, after.daily!!.identity)
        assertSame(before.session, after.session)
    }

    @Test fun snapshotCopiesDoNotMutateTheOriginalStageOrAdmission() {
        val before = fixture.session()
        val gate = PendingSessionGateSnapshot(1L, SessionGatePhase.REENTRY, 6_000L, false)
        val observation = before.copy(
            episode = before.episode.copy(pendingGate = gate),
            admission = SessionAdmissionSnapshot(reentryGateArmed = false, foregroundAdmitted = true),
        )
        assertNull(before.episode.pendingGate)
        assertFalse(before.admission.foregroundAdmitted)
        assertEquals(0, observation.episode.reminderCount)
        assertEquals(600L, observation.episode.nextReminderAtSeconds)
    }

    @Test fun invalidRuntimeQuantitiesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { fixture.daily(usageSeconds = -1L) }
        assertThrows(IllegalArgumentException::class.java) { fixture.daily(extensionSeconds = -1L) }
        assertThrows(IllegalArgumentException::class.java) { fixture.session().episode.copy(reminderCount = -1) }
    }

    @Test fun focusBlockedSetIsAnIndependentRuntimeFactNotManagedEligibility() {
        val selected = mutableSetOf("example.notManaged")
        val focus = FocusRuntimeSnapshot.Focus(1_000L, 61_000L, selected)
        selected.clear()
        assertEquals(setOf("example.notManaged"), focus.blockedPackages)
        assertFalse(fixture.PACKAGE in focus.blockedPackages)
        assertTrue(fixture.context().effectivePolicy.appPolicy.managed)
        assertThrows(UnsupportedOperationException::class.java) {
            (focus.blockedPackages as MutableSet<String>).clear()
        }
    }

    @Test fun deepFocusCompletionIsRuntimeDataIndependentOfProfileConfiguration() {
        val active = FocusRuntimeSnapshot.DeepFocus(1_000L, 61_000L, FocusModeStatus.ACTIVE)
        val completed = active.copy(status = FocusModeStatus.COMPLETED)
        assertEquals(active.endWallMillis, completed.endWallMillis)
        assertEquals(FocusModeStatus.ACTIVE, active.status)
        assertEquals(FocusModeStatus.COMPLETED, completed.status)
    }

    @Test fun ordinaryCompletionAndConcurrentFactsPreserveFrozenBlockedPackages() {
        val selected = mutableSetOf(fixture.PACKAGE)
        val ordinary = FocusRuntimeSnapshot.Focus(1_000L, 61_000L, selected, FocusModeStatus.COMPLETED)
        val deep = FocusRuntimeSnapshot.DeepFocus(1_000L, 61_000L, FocusModeStatus.ACTIVE)
        val combined = FocusRuntimeSnapshot.Concurrent(ordinary, deep)
        selected.clear()
        assertEquals(FocusModeStatus.COMPLETED, combined.focus.status)
        assertEquals(setOf(fixture.PACKAGE), combined.focus.blockedPackages)
        assertSame(deep, combined.deepFocus)
    }

    @Test fun firstObservationUnknownEdgeAndReadyRetryAreDistinctFacts() {
        val first = DailyEdgeSnapshot()
        val unknown = first.copy(observationAvailable = false)
        val retry = first.copy(previousOverLimit = true, displayRetryReady = true)
        assertTrue(first.observationAvailable)
        assertNull(first.previousOverLimit)
        assertFalse(unknown.observationAvailable)
        assertTrue(retry.displayRetryReady)
        assertEquals(true, retry.previousOverLimit)
        assertFalse(first.displayRetryReady)
    }

    @Test fun capabilitySnapshotReusesAllThreeExistingAccessibilityStatesAndSeparateGrants() {
        for (state in AccessibilityRuntimeState.entries) {
            val capabilities = CapabilitySnapshot(true, true, false, state)
            assertEquals(state, capabilities.accessibility)
            assertTrue(capabilities.usageAccessGranted && capabilities.overlayGranted)
            assertFalse(capabilities.notificationsGranted)
        }
    }

    @Test fun generationPolicyRevisionAndEvaluationSequenceAreDistinctIdentityDimensions() {
        val original = fixture.identity
        assertNotEquals(original, original.copy(foregroundGeneration = ForegroundGeneration(8L)))
        assertNotEquals(original, original.copy(policyRevision = PolicyRevision(3L)))
        assertNotEquals(original, original.copy(evaluationSequence = 2L))
        assertThrows(IllegalArgumentException::class.java) {
            fixture.context().copy(daily = fixture.daily().copy(identity =
                UsageDayRuntimeIdentity("example.other", fixture.usageDay)))
        }
    }
}
