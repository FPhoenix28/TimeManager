package com.example.timemanager.accessibility.rules

import com.example.timemanager.data.model.FocusModeType
import com.example.timemanager.domain.focus.*

/** Hypothetical legacy business choice only. It cannot execute or consume runtime state. */
object AccessibilityLegacyIntent {
    fun forEntry(input: AccessibilityRulesSnapshot, essentials: DeepFocusEssentialApps): AccessibilityIntent {
        val higher = window(input, essentials)
        // The old Deep branch globally returned even for an essential target. Debug comparison retains that history.
        if (input.focus.isDeepFocusActive(input.time.wallNowMillis) || higher != AccessibilityIntent.ALLOW_ENTRY) return higher
        return entry(input)
    }

    fun window(input: AccessibilityRulesSnapshot, essentials: DeepFocusEssentialApps): AccessibilityIntent {
        val state = input.focus
        val target = input.target
        val now = input.time.wallNowMillis
        if (!input.health.canForceHome && (state.isActive(now) || state.isDeepFocusActive(now))) return AccessibilityIntent.FAIL_OPEN
        if (state.mode == FocusModeType.DEEP_FOCUS) return when (DeepFocusEnforcementPolicy.evaluate(state,
            target.packageName, target.className, essentials,
            target.disposition == com.example.timemanager.domain.rules.TargetDisposition.LOCK_SCREEN,
            input.health.overlayGranted, now)) {
            DeepFocusDisposition.BLOCK -> AccessibilityIntent.BLOCK_DEEP
            DeepFocusDisposition.ALLOW -> AccessibilityIntent.ALLOW_ENTRY
            DeepFocusDisposition.ALLOW_AND_SUSPEND_OVERLAY ->
                if (!input.health.overlayGranted) AccessibilityIntent.FAIL_OPEN else AccessibilityIntent.ALLOW_ENTRY
        }
        return if (FocusEnforcementPolicy.shouldBlock(state, target.packageName, essentials.ownPackage, now))
            AccessibilityIntent.BLOCK_FOCUS else AccessibilityIntent.ALLOW_ENTRY
    }

    fun entry(input: AccessibilityRulesSnapshot): AccessibilityIntent {
        if (input.daily?.isOverLimit == true) return AccessibilityIntent.SUPPRESS_SESSION_DAILY
        val e = input.episode
        val bypass = e?.bypassExpiresAtMillis?.let { input.time.elapsedNowMillis < it } == true
        return if (bypass || e?.allowedForeground == true || e?.reentryGateArmed != true || e.pendingGate != null)
            AccessibilityIntent.ALLOW_ENTRY else AccessibilityIntent.CHECK_SESSION_REENTRY
    }
}
