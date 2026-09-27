package com.example.timemanager.domain.focus

import com.example.timemanager.data.model.FocusModeState
import com.example.timemanager.data.model.FocusModeType

/** Pure Focus decision; daily-limit and session state never enter this path. */
object FocusEnforcementPolicy {
    fun shouldBlock(
        state: FocusModeState,
        foregroundPackage: String,
        ownPackage: String,
        nowMillis: Long,
    ): Boolean = when (state.mode) {
        FocusModeType.OFF -> false
        FocusModeType.FOCUS -> state.isActive(nowMillis) &&
            foregroundPackage != ownPackage &&
            foregroundPackage in state.blockedPackages
        FocusModeType.DEEP_FOCUS -> false // Reserved for a separate future policy.
    }
}

data class FocusEnforcementAction(
    val performHome: Boolean,
    val showBlockedOverlay: Boolean,
)

/**
 * Keeps enforcement independent from the informational overlay lifecycle.
 * An already-visible overlay must never let a blocked app remain in foreground.
 */
object FocusEnforcementActionPolicy {
    fun decide(
        shouldBlock: Boolean,
        overlayShowing: Boolean,
        debounced: Boolean,
    ): FocusEnforcementAction = when {
        !shouldBlock || debounced -> FocusEnforcementAction(
            performHome = false,
            showBlockedOverlay = false,
        )

        else -> FocusEnforcementAction(
            performHome = true,
            showBlockedOverlay = !overlayShowing,
        )
    }
}
