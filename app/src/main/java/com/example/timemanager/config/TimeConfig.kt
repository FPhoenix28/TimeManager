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
    val sessionFirstIncrementSeconds: Long = 8L * 60L,
    val sessionSecondIncrementSeconds: Long = 8L * 60L,
    val sessionRepeatingIncrementSeconds: Long = 5L * 60L,
) {
    init {
        require(firstSessionReminderSeconds > 0L)
        require(secondSessionReminderSeconds > firstSessionReminderSeconds)
        require(extensionDurationSeconds > 0L)
        require(sessionFirstIncrementSeconds > 0L && sessionSecondIncrementSeconds > 0L &&
            sessionRepeatingIncrementSeconds > 0L)
        require(frictionGateSeconds > 0L && frictionResetIdleSeconds > 0L && frictionBypassSeconds > 0L)
        require(sessionInterventionOverrideSeconds == null ||
            debugTimeOverridesEnabled && sessionInterventionOverrideSeconds > 0L)
    }

    companion object {
        private val productionConfig = TimeConfig(
            debugTimeOverridesEnabled = false,
            firstSessionReminderSeconds = 15L * 60L,
            secondSessionReminderSeconds = 30L * 60L,
            extensionDurationSeconds = LIMIT_EXTENSION_SECONDS,
        )

        fun production(): TimeConfig = productionConfig

        const val FRICTION_GATE_SECONDS = 5L
        const val FRICTION_RESET_IDLE_SECONDS = 5L * 60L
        const val FRICTION_BYPASS_SECONDS = 3L
        const val LIMIT_EXTENSION_MINUTES = 15L
        const val LIMIT_EXTENSION_SECONDS = LIMIT_EXTENSION_MINUTES * 60L
    }
}
