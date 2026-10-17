package com.example.timemanager.domain.focus

import com.example.timemanager.data.model.FocusModeState
import com.example.timemanager.data.model.FocusModeStatus
import com.example.timemanager.data.model.FocusModeType
import org.junit.Assert.assertEquals
import org.junit.Test

class DeepFocusEnforcementPolicyTest {
    private val state = FocusModeState(
        mode = FocusModeType.DEEP_FOCUS,
        status = FocusModeStatus.ACTIVE,
        startTimeMillis = 1_000L,
        endTimeMillis = 61_000L,
    )
    private val essentials = DeepFocusEssentialApps(
        ownPackage = "app.time",
        launcherPackage = "launcher",
        inputMethodPackage = "ime",
        defaultDialerPackage = "dialer",
        callUiPackages = setOf("dialer", "system.incallui"),
        settingsPackage = "settings",
    )

    @Test fun ordinaryAppsAreBlockedOnlyWhileActive() {
        assertEquals(
            DeepFocusDisposition.BLOCK,
            decide("social", "social.Main", now = 10_000L),
        )
        assertEquals(
            DeepFocusDisposition.ALLOW,
            decide("social", "social.Main", now = 61_000L),
        )
    }

    @Test fun launcherOwnAppImeAndSystemUiAreAllowed() {
        listOf("app.time", "launcher", "ime", "com.android.systemui").forEach { packageName ->
            assertEquals(DeepFocusDisposition.ALLOW, decide(packageName, "Any"))
        }
    }

    @Test fun newPublicVariantsOnlyExemptTheirOwnRuntimePackage() {
        val release = "com.fphoenix28.timemanager"
        val dev = "$release.debug"
        for ((own, other) in listOf(release to dev, dev to release)) {
            val runtimeEssentials = essentials.copy(ownPackage = own)
            assertEquals(DeepFocusDisposition.ALLOW, DeepFocusEnforcementPolicy.evaluate(
                state, own, "com.example.timemanager.MainActivity", runtimeEssentials,
                keyguardLocked = false, overlayPermissionGranted = true, nowMillis = 10_000L,
            ))
            assertEquals(DeepFocusDisposition.BLOCK, DeepFocusEnforcementPolicy.evaluate(
                state, other, "com.example.timemanager.MainActivity", runtimeEssentials,
                keyguardLocked = false, overlayPermissionGranted = true, nowMillis = 10_000L,
            ))
        }
    }

    @Test fun defaultDialerPackageIsNotBlanketAllowed() {
        assertEquals(
            DeepFocusDisposition.BLOCK,
            decide("dialer", "com.android.dialer.main.impl.MainActivity"),
        )
        assertEquals(
            DeepFocusDisposition.ALLOW_AND_SUSPEND_OVERLAY,
            decide("dialer", "com.android.incallui.InCallActivity"),
        )
        assertEquals(
            DeepFocusDisposition.ALLOW_AND_SUSPEND_OVERLAY,
            decide("dialer", "com.android.dialer.emergency.EmergencyDialer"),
        )
        assertEquals(
            DeepFocusDisposition.ALLOW_AND_SUSPEND_OVERLAY,
            decide("system.incallui", "com.vivo.call.InCallActivity"),
        )
        assertEquals(
            DeepFocusDisposition.BLOCK,
            decide("system.incallui", "com.android.incallui.record.CallRecordSetting"),
        )
    }

    @Test fun onlyRecoverySettingsActivitiesAreAllowed() {
        assertEquals(
            DeepFocusDisposition.ALLOW_AND_SUSPEND_OVERLAY,
            decide("settings", "com.android.settings.Settings.AccessibilitySettingsActivity"),
        )
        assertEquals(
            DeepFocusDisposition.BLOCK,
            decide("settings", "com.android.settings.Settings.MainSettings"),
        )
    }

    @Test fun keyguardAlwaysSuspendsOverlay() {
        assertEquals(
            DeepFocusDisposition.ALLOW_AND_SUSPEND_OVERLAY,
            DeepFocusEnforcementPolicy.evaluate(
                state = state,
                foregroundPackage = "social",
                foregroundClass = "social.Main",
                essentials = essentials,
                keyguardLocked = true,
                overlayPermissionGranted = true,
                nowMillis = 10_000L,
            ),
        )
    }

    @Test fun completedStateNeverBlocks() {
        assertEquals(
            DeepFocusDisposition.ALLOW,
            DeepFocusEnforcementPolicy.evaluate(
                state = state.copy(status = FocusModeStatus.COMPLETED),
                foregroundPackage = "social",
                foregroundClass = "social.Main",
                essentials = essentials,
                keyguardLocked = false,
                overlayPermissionGranted = true,
                nowMillis = 10_000L,
            ),
        )
    }

    @Test fun missingOverlayPermissionDoesNotBlockApps() {
        assertEquals(
            DeepFocusDisposition.ALLOW_AND_SUSPEND_OVERLAY,
            DeepFocusEnforcementPolicy.evaluate(
                state = state,
                foregroundPackage = "social",
                foregroundClass = "social.Main",
                essentials = essentials,
                keyguardLocked = false,
                overlayPermissionGranted = false,
                nowMillis = 10_000L,
            ),
        )
    }

    @Test fun overlayRemovalAndSystemUiEventsDoNotCancelAnEssentialUiSuspension() {
        listOf("app.time", "ime", "com.android.systemui").forEach { packageName ->
            assertEquals(
                false,
                DeepFocusEnforcementPolicy.shouldResumeOverlayAfterSuspension(
                    packageName,
                    essentials,
                ),
            )
        }
        assertEquals(
            true,
            DeepFocusEnforcementPolicy.shouldResumeOverlayAfterSuspension("launcher", essentials),
        )
    }

    private fun decide(
        packageName: String,
        className: String?,
        now: Long = 10_000L,
    ) = DeepFocusEnforcementPolicy.evaluate(
        state = state,
        foregroundPackage = packageName,
        foregroundClass = className,
        essentials = essentials,
        keyguardLocked = false,
        overlayPermissionGranted = true,
        nowMillis = now,
    )
}
