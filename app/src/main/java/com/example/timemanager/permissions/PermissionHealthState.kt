package com.example.timemanager.permissions

enum class AppPermission { USAGE_ACCESS, OVERLAY, NOTIFICATIONS, ACCESSIBILITY }

data class PermissionHealthState(
    val usageAccessGranted: Boolean,
    val notificationGranted: Boolean,
    val overlayGranted: Boolean,
    val accessibilityGranted: Boolean = false,
) {
    val missingPermissions: Set<AppPermission> get() = buildSet {
        if (!usageAccessGranted) add(AppPermission.USAGE_ACCESS)
        if (!overlayGranted) add(AppPermission.OVERLAY)
        if (!notificationGranted) add(AppPermission.NOTIFICATIONS)
        if (!accessibilityGranted) add(AppPermission.ACCESSIBILITY)
    }
    val isHealthy: Boolean get() = missingPermissions.isEmpty()
    // Keep the existing onboarding minimum: Accessibility is an optional enhancement.
    val allRequiredGranted: Boolean get() = usageAccessGranted && notificationGranted && overlayGranted
    val canShowSessionReminder: Boolean get() = usageAccessGranted && overlayGranted
}
