package com.example.timemanager.ui.permissions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionUiStateTest {
    @Test
    fun allRequiredGranted_requiresEveryPermission() {
        assertTrue(
            PermissionUiState(
                usageAccessGranted = true,
                notificationGranted = true,
                overlayGranted = true,
            ).allRequiredGranted,
        )
        assertFalse(
            PermissionUiState(
                usageAccessGranted = false,
                notificationGranted = true,
                overlayGranted = true,
            ).allRequiredGranted,
        )
        assertFalse(
            PermissionUiState(
                usageAccessGranted = true,
                notificationGranted = false,
                overlayGranted = true,
            ).allRequiredGranted,
        )
        assertFalse(
            PermissionUiState(
                usageAccessGranted = true,
                notificationGranted = true,
                overlayGranted = false,
            ).allRequiredGranted,
        )
    }
}
