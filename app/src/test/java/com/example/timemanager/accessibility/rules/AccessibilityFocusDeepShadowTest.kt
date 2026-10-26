package com.example.timemanager.accessibility.rules

import com.example.timemanager.data.model.*
import com.example.timemanager.domain.focus.*
import com.example.timemanager.domain.intervention.DailyLimitSnapshot
import com.example.timemanager.domain.rules.*
import com.example.timemanager.permissions.*
import org.junit.Assert.*
import org.junit.Test

class AccessibilityFocusDeepShadowTest {
    private val shadow = AccessibilityRulesShadow()
    private val essentials = DeepFocusEssentialApps("own", "launcher", "ime", "phone", setOf("phone"), "settings", setOf("systemui"))
    private fun compare(state: FocusModeState, expected: AccessibilityIntent,
        disposition: TargetDisposition = TargetDisposition.ORDINARY_APP,
        health: PermissionHealthState = PermissionHealthState(true, true, true, true, true)): AccessibilityShadowResult {
        val input = accessibilitySnapshot(state, disposition, health = health,
            scope = AccessibilityObservationScope.FOCUS_DEEP_WINDOW)
        return shadow.compare(input, expected)
    }

    @Test fun focusSelectedMatchesActualLegacyPolicy() {
        val state = activeFocus()
        assertTrue(FocusEnforcementPolicy.shouldBlock(state, "app", "own", 2_000L))
        assertEquals(AccessibilityShadowStatus.MATCH, compare(state, AccessibilityIntent.BLOCK_FOCUS).status)
    }
    @Test fun focusUnselectedDoesNotBlock() {
        assertEquals(AccessibilityShadowStatus.MATCH,
            compare(activeFocus().copy(blockedPackages = setOf("other")), AccessibilityIntent.ALLOW_ENTRY).status)
    }
    @Test fun focusOwnAppDoesNotBlockEvenIfSelected() {
        assertEquals(AccessibilityShadowStatus.MATCH,
            compare(activeFocus(), AccessibilityIntent.ALLOW_ENTRY, TargetDisposition.OWN_APP).status)
    }
    @Test fun focusCompletedDoesNotBlock() {
        assertEquals(AccessibilityShadowStatus.MATCH,
            compare(activeFocus().copy(status = FocusModeStatus.COMPLETED), AccessibilityIntent.ALLOW_ENTRY).status)
    }
    @Test fun focusBeforeStartDoesNotBlock() {
        assertEquals(AccessibilityShadowStatus.MATCH,
            compare(activeFocus().copy(startTimeMillis = 3_000L), AccessibilityIntent.ALLOW_ENTRY).status)
    }
    @Test fun focusExactEndAndAfterEndDoNotBlock() {
        for (end in listOf(2_000L, 1_999L)) assertEquals(AccessibilityShadowStatus.MATCH,
            compare(activeFocus().copy(endTimeMillis = end), AccessibilityIntent.ALLOW_ENTRY).status)
    }
    @Test fun focusConnectionStatesRemainDistinctAndApplicationOverlayIsNotRequired() {
        for ((enabled, connected, expected) in listOf(Triple(false, false, AccessibilityRuntimeState.DISABLED),
            Triple(true, false, AccessibilityRuntimeState.ENABLED_BUT_DISCONNECTED), Triple(true, true, AccessibilityRuntimeState.CONNECTED))) {
            val health = PermissionHealthState(true, true, false, enabled, connected)
            assertEquals(expected, health.accessibilityRuntimeState)
            val result = compare(activeFocus(), if (connected) AccessibilityIntent.BLOCK_FOCUS else AccessibilityIntent.FAIL_OPEN, health = health)
            assertEquals(AccessibilityShadowStatus.MATCH, result.status)
            assertFalse(result.evaluation!!.requiredCapabilities.any { it.capability == RequiredCapability.OVERLAY })
        }
    }
    @Test fun deepOrdinaryTargetMatchesLegacyAndSuppressesLowerCandidates() {
        val result = compare(activeFocus(FocusModeType.DEEP_FOCUS), AccessibilityIntent.BLOCK_DEEP)
        assertEquals(AccessibilityShadowStatus.MATCH, result.status)
        assertEquals(RuleKind.DEEP_FOCUS, result.evaluation!!.selected!!.kind)
    }
    @Test fun everyEssentialDispositionIsOnlyADeepExemption() {
        val deep = activeFocus(FocusModeType.DEEP_FOCUS)
        for (disposition in TargetDisposition.entries.filter { it !in setOf(TargetDisposition.ORDINARY_APP, TargetDisposition.UNCLASSIFIED) }) {
            val input = accessibilitySnapshot(deep, disposition, managed = false, scope = AccessibilityObservationScope.FOCUS_DEEP_WINDOW)
            val result = shadow.compare(input, AccessibilityIntent.ALLOW_ENTRY)
            assertEquals(disposition.toString(), AccessibilityShadowStatus.MATCH, result.status)
            assertEquals(listOf(RuleExemption(RuleKind.DEEP_FOCUS, disposition)), result.evaluation!!.explicitExemptions)
        }
    }
    @Test fun deepEssentialDailyPreservesDailySelectionNotGlobalAllow() {
        val result = shadow.compare(accessibilitySnapshot(activeFocus(FocusModeType.DEEP_FOCUS), TargetDisposition.NECESSARY_CALL_UI,
            daily = DailyLimitSnapshot("day", 60L, 60L, true), scope = AccessibilityObservationScope.FOCUS_DEEP_WINDOW), AccessibilityIntent.ALLOW_ENTRY)
        assertEquals(AccessibilityShadowStatus.EXPECTED_EXECUTION_DIFFERENCE, result.status)
        assertEquals(AccessibilityIntent.SUPPRESS_SESSION_DAILY, result.projection!!.intent)
    }
    @Test fun deepEssentialSessionPreservesReentryCandidate() {
        val source = accessibilitySnapshot(activeFocus(FocusModeType.DEEP_FOCUS), TargetDisposition.RECOVERY_SETTINGS_UI,
            scope = AccessibilityObservationScope.FOCUS_DEEP_WINDOW)
        val result = shadow.compare(source.copy(episode = source.episode!!.copy(episodeUsageSeconds = 35L)), AccessibilityIntent.ALLOW_ENTRY)
        assertEquals(RuleKind.SESSION, result.evaluation!!.selected!!.kind)
        assertEquals(AccessibilityShadowStatus.EXPECTED_EXECUTION_DIFFERENCE, result.status)
    }
    @Test fun deepMissingOverlayIsMatchedButCannotClaimProtection() {
        val result = compare(activeFocus(FocusModeType.DEEP_FOCUS), AccessibilityIntent.FAIL_OPEN,
            health = PermissionHealthState(true, true, false, true, true))
        assertEquals(AccessibilityShadowStatus.MATCH, result.status)
        assertTrue(result.evaluation!!.activeConstraints.contains(RuleKind.DEEP_FOCUS))
        assertTrue(result.projection!!.missingCapabilities.contains(RequiredCapability.OVERLAY))
    }
    @Test fun deepExemptMissingOverlayReportsLegacySuspensionRatherThanMismatch() {
        val result = compare(activeFocus(FocusModeType.DEEP_FOCUS), AccessibilityIntent.FAIL_OPEN,
            TargetDisposition.NECESSARY_CALL_UI, PermissionHealthState(true, true, false, true, true))
        assertEquals(AccessibilityShadowStatus.EXPECTED_EXECUTION_DIFFERENCE, result.status)
        assertTrue(result.evaluation!!.explicitExemptions.isNotEmpty())
    }
    @Test fun deepDisconnectedStillHasBusinessConstraintButFailsOpenExecution() {
        val result = compare(activeFocus(FocusModeType.DEEP_FOCUS), AccessibilityIntent.FAIL_OPEN,
            health = PermissionHealthState(true, true, true, true, false))
        assertEquals(AccessibilityShadowStatus.MATCH, result.status)
        assertTrue(result.projection!!.missingCapabilities.contains(RequiredCapability.ACCESSIBILITY_CONNECTION))
    }
    @Test fun deepCompletedExactEndAndBeforeStartImmediatelyStopBlocking() {
        val deep = activeFocus(FocusModeType.DEEP_FOCUS)
        for (state in listOf(deep.copy(status = FocusModeStatus.COMPLETED), deep.copy(endTimeMillis = 2_000L), deep.copy(startTimeMillis = 3_000L))) {
            val result = compare(state, AccessibilityIntent.ALLOW_ENTRY)
            assertEquals(AccessibilityShadowStatus.MATCH, result.status)
            assertFalse(result.evaluation!!.activeConstraints.contains(RuleKind.DEEP_FOCUS))
        }
    }
    @Test fun unknownDeepTargetIsInsufficientNotAssumedExempt() {
        val result = compare(activeFocus(FocusModeType.DEEP_FOCUS), AccessibilityIntent.BLOCK_DEEP, TargetDisposition.UNCLASSIFIED)
        assertEquals(AccessibilityShadowStatus.INSUFFICIENT_FACTS, result.status)
        assertTrue(result.evaluation!!.explicitExemptions.isEmpty())
    }
    @Test fun resolverIdentitiesAndExistingCallRecoveryPredicatesAreReused() {
        val cases = listOf(Triple("own", "Activity", TargetDisposition.OWN_APP),
            Triple("launcher", "Activity", TargetDisposition.LAUNCHER), Triple("ime", "Activity", TargetDisposition.INPUT_METHOD),
            Triple("systemui", "Activity", TargetDisposition.SYSTEM_UI), Triple("phone", "IncomingCallActivity", TargetDisposition.NECESSARY_CALL_UI),
            Triple("phone", "EmergencyActivity", TargetDisposition.NECESSARY_CALL_UI), Triple("settings", "AccessibilitySettings", TargetDisposition.RECOVERY_SETTINGS_UI),
            Triple("settings", "DrawOverlaySettings", TargetDisposition.RECOVERY_SETTINGS_UI), Triple("phone", "DialerMainActivity", TargetDisposition.ORDINARY_APP))
        for ((pkg, cls, expected) in cases) {
            assertEquals(expected, AccessibilityTargetClassifier.classify(pkg, cls, essentials, false).disposition)
        }
        assertEquals(TargetDisposition.LOCK_SCREEN, AccessibilityTargetClassifier.classify("systemui", "Keyguard", essentials, true).disposition)
        assertEquals(TargetDisposition.UNCLASSIFIED, AccessibilityTargetClassifier.classify("phone", null, essentials, false).disposition)
    }
    @Test fun actualPersistedStateNeverCreatesConcurrentFocusSnapshot() {
        for (state in listOf(activeFocus(), activeFocus(FocusModeType.DEEP_FOCUS), FocusModeState())) {
            assertFalse(AccessibilityRulesSnapshotAdapter().adapt(accessibilitySnapshot(state)).context.focus is FocusRuntimeSnapshot.Concurrent)
        }
    }
}
