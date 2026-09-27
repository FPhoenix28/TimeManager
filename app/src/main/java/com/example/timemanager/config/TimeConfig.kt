package com.example.timemanager.config

/** Build-variant time parameters consumed by the shared production business logic. */
data class TimeConfig(
    val debugTimeOverridesEnabled: Boolean,
    val firstSessionReminderSeconds: Long,
    val secondSessionReminderSeconds: Long,
    val extensionDurationSeconds: Long,
) {
    init {
        require(firstSessionReminderSeconds > 0L)
        require(secondSessionReminderSeconds > firstSessionReminderSeconds)
        require(extensionDurationSeconds > 0L)
    }

    companion object {
        const val LIMIT_EXTENSION_MINUTES = 15L
        const val LIMIT_EXTENSION_SECONDS = LIMIT_EXTENSION_MINUTES * 60L
    }
}
