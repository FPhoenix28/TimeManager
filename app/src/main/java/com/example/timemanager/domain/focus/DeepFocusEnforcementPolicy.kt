package com.example.timemanager.domain.focus

import com.example.timemanager.data.model.FocusModeState

enum class DeepFocusDisposition {
    ALLOW,
    ALLOW_AND_SUSPEND_OVERLAY,
    BLOCK,
}

/** Fast, allocation-light decision used directly from AccessibilityService events. */
object DeepFocusEnforcementPolicy {
    fun evaluate(
        state: FocusModeState,
        foregroundPackage: String,
        foregroundClass: String?,
        essentials: DeepFocusEssentialApps,
        keyguardLocked: Boolean,
        overlayPermissionGranted: Boolean,
        nowMillis: Long,
    ): DeepFocusDisposition {
        if (!state.isDeepFocusActive(nowMillis)) return DeepFocusDisposition.ALLOW
        if (!overlayPermissionGranted) return DeepFocusDisposition.ALLOW_AND_SUSPEND_OVERLAY
        if (keyguardLocked) return DeepFocusDisposition.ALLOW_AND_SUSPEND_OVERLAY
        if (isNecessaryCallOrEmergencyUi(foregroundPackage, foregroundClass, essentials)) {
            return DeepFocusDisposition.ALLOW_AND_SUSPEND_OVERLAY
        }
        if (isNecessaryRecoverySettings(foregroundPackage, foregroundClass, essentials)) {
            return DeepFocusDisposition.ALLOW_AND_SUSPEND_OVERLAY
        }
        if (
            foregroundPackage == essentials.ownPackage ||
            foregroundPackage == essentials.launcherPackage ||
            foregroundPackage == essentials.inputMethodPackage ||
            foregroundPackage in essentials.systemUiPackages
        ) {
            return DeepFocusDisposition.ALLOW
        }

        return DeepFocusDisposition.BLOCK
    }

    fun shouldResumeOverlayAfterSuspension(
        foregroundPackage: String,
        essentials: DeepFocusEssentialApps,
    ): Boolean =
        foregroundPackage != essentials.ownPackage &&
            foregroundPackage != essentials.inputMethodPackage &&
            foregroundPackage !in essentials.systemUiPackages

    internal fun isNecessaryCallOrEmergencyUi(
        packageName: String,
        className: String?,
        essentials: DeepFocusEssentialApps,
    ): Boolean {
        if (packageName !in essentials.callUiPackages || className == null) return false
        val simpleClassName = className.substringAfterLast('.').lowercase()
        return simpleClassName.contains("incall") ||
            simpleClassName.contains("in_call") ||
            simpleClassName.contains("incomingcall") ||
            simpleClassName.contains("incoming_call") ||
            simpleClassName.contains("emergency") ||
            simpleClassName.contains("callactivity")
    }

    internal fun isNecessaryRecoverySettings(
        packageName: String,
        className: String?,
        essentials: DeepFocusEssentialApps,
    ): Boolean {
        if (packageName != essentials.settingsPackage || className == null) return false
        val normalized = className.lowercase()
        return normalized.contains("accessibility") ||
            normalized.contains("drawoverlay") ||
            normalized.contains("manageexternal")
    }
}
