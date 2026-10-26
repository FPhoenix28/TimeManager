package com.example.timemanager.accessibility.rules

import android.content.Context

/** Compile-time API only. Release never constructs the Debug observer and contains no file sink. */
class AccessibilityRulesObserver(@Suppress("UNUSED_PARAMETER") context: Context) {
    fun observeAuthority(input: AccessibilityRulesSnapshot, decision: AccessibilityAuthorityDecision, legacy: AccessibilityIntent) = Unit
    fun fallback(reason: String) = Unit
    fun close() = Unit
}
