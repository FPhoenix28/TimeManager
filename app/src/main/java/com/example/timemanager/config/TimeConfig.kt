package com.example.timemanager.config

/** Build-variant time parameters consumed by the shared production business logic. */
data class TimeConfig(
    val debugTimeOverridesEnabled: Boolean,
    val firstSessionReminderSeconds: Long,
    val secondSessionReminderSeconds: Long,
    val extensionDurationSeconds: Long,
    val frictionGateSeconds: Long = FRICTION_GATE_SECONDS,
    val frictionResetIdleSeconds: Long = FRICTION_RESET_IDLE_SECONDS,
    val frictionBypassSeconds: Long = FRICTION_BYPASS_SECONDS,
    val sessionInterventionOverrideSeconds: Long? = null,
) {
    init {
        require(firstSessionReminderSeconds > 0L)
        require(secondSessionReminderSeconds > firstSessionReminderSeconds)
        require(extensionDurationSeconds > 0L)
        require(frictionGateSeconds > 0L && frictionResetIdleSeconds > 0L && frictionBypassSeconds > 0L)
        require(sessionInterventionOverrideSeconds == null ||
            debugTimeOverridesEnabled && sessionInterventionOverrideSeconds > 0L)
    }

    companion object {
        const val FRICTION_GATE_SECONDS = 5L
        const val FRICTION_RESET_IDLE_SECONDS = 5L * 60L
        const val FRICTION_BYPASS_SECONDS = 3L
        const val LIMIT_EXTENSION_MINUTES = 15L
        const val LIMIT_EXTENSION_SECONDS = LIMIT_EXTENSION_MINUTES * 60L
    }
}
