package com.example.timemanager.accessibility.rules

import com.example.timemanager.domain.focus.DeepFocusEnforcementPolicy
import com.example.timemanager.domain.focus.DeepFocusEssentialApps
import com.example.timemanager.domain.rules.TargetDisposition
import com.example.timemanager.domain.rules.TargetFacts

/** Android boundary: the existing resolver supplies identities, existing predicates identify UI. */
object AccessibilityTargetClassifier {
    fun classify(packageName: String, className: String?, essentials: DeepFocusEssentialApps,
        keyguardLocked: Boolean): TargetFacts = TargetFacts(packageName, className, when {
        packageName == essentials.ownPackage -> TargetDisposition.OWN_APP
        keyguardLocked -> TargetDisposition.LOCK_SCREEN
        DeepFocusEnforcementPolicy.isNecessaryCallOrEmergencyUi(packageName, className, essentials) -> TargetDisposition.NECESSARY_CALL_UI
        DeepFocusEnforcementPolicy.isNecessaryRecoverySettings(packageName, className, essentials) -> TargetDisposition.RECOVERY_SETTINGS_UI
        packageName == essentials.launcherPackage -> TargetDisposition.LAUNCHER
        packageName == essentials.inputMethodPackage -> TargetDisposition.INPUT_METHOD
        packageName in essentials.systemUiPackages -> TargetDisposition.SYSTEM_UI
        className == null -> TargetDisposition.UNCLASSIFIED
        else -> TargetDisposition.ORDINARY_APP
    })
}
