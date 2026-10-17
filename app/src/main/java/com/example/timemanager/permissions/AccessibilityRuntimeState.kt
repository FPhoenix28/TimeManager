package com.example.timemanager.permissions

enum class AccessibilityRuntimeState {
    DISABLED, ENABLED_BUT_DISCONNECTED, CONNECTED;

    companion object {
        fun resolve(enabled: Boolean, connected: Boolean): AccessibilityRuntimeState = when {
            !enabled -> DISABLED
            connected -> CONNECTED
            else -> ENABLED_BUT_DISCONNECTED
        }
    }
}
