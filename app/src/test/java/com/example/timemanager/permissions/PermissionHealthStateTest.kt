package com.example.timemanager.permissions

import org.junit.Assert.*
import org.junit.Test

class PermissionHealthStateTest {
    private val healthy = PermissionHealthState(true, true, true, true)

    @Test fun allFourPermissionsDefineHealthWithoutChangingOnboardingMinimum() {
        assertTrue(healthy.isHealthy)
        assertTrue(healthy.missingPermissions.isEmpty())
        val noAccessibility = healthy.copy(accessibilityGranted = false)
        assertEquals(setOf(AppPermission.ACCESSIBILITY), noAccessibility.missingPermissions)
        assertFalse(noAccessibility.isHealthy)
        assertTrue(noAccessibility.allRequiredGranted)
        assertTrue(noAccessibility.canShowSessionReminder)
        assertFalse(healthy.copy(usageAccessGranted = false).canShowSessionReminder)
        assertFalse(healthy.copy(overlayGranted = false).canShowSessionReminder)
        assertTrue(healthy.copy(notificationGranted = false).canShowSessionReminder)
    }

    @Test fun missingPermissionsAreListedIndividually() {
        assertEquals(AppPermission.entries.toSet(), PermissionHealthState(false, false, false).missingPermissions)
    }

    @Test fun identicalRefreshesAndFewerMissingPermissionsNeverRepeatNotice() {
        val tracker = PermissionHealthNoticeTracker()
        val missing = healthy.copy(accessibilityGranted = false)
        tracker.update(missing)
        assertTrue(tracker.consumePending())
        repeat(10) { tracker.update(missing); assertFalse(tracker.consumePending()) }
        tracker.update(missing.copy(overlayGranted = false))
        assertTrue(tracker.consumePending())
        tracker.update(missing)
        assertFalse(tracker.consumePending())
        tracker.update(healthy)
        tracker.update(missing)
        assertTrue(tracker.consumePending())
    }

    @Test fun queuedNoticeSurvivesAwayFromHomeButRecoveryCancelsIt() {
        val tracker = PermissionHealthNoticeTracker()
        val missing = healthy.copy(accessibilityGranted = false)
        tracker.update(missing)
        tracker.update(missing)
        assertTrue(tracker.consumePending())
        assertFalse(tracker.consumePending())
        tracker.update(healthy)
        tracker.update(missing)
        tracker.update(healthy)
        assertFalse(tracker.consumePending())
    }
}
