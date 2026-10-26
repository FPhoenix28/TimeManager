package com.example.timemanager.config

import android.content.Context

/** v2 deliberately does not import the superseded global first-reminder/mode preferences. */
internal class DebugTimingPreferences(context: Context) : DebugTimingStorage {
    private val preferences = context.applicationContext.getSharedPreferences(
        "debug_app_detail_timing_v2", Context.MODE_PRIVATE,
    )

    override fun read(): TimeConfig = try {
        val defaults = TimeConfig.production()
        val loaded = defaults.copy(
            sessionFirstIncrementSeconds = preferences.getLong("increment_first", defaults.sessionFirstIncrementSeconds),
            sessionSecondIncrementSeconds = preferences.getLong("increment_second", defaults.sessionSecondIncrementSeconds),
            sessionRepeatingIncrementSeconds = preferences.getLong("increment_repeat", defaults.sessionRepeatingIncrementSeconds),
            frictionResetIdleSeconds = preferences.getLong("idle_reset", defaults.frictionResetIdleSeconds),
            frictionGateSeconds = preferences.getLong("gate", defaults.frictionGateSeconds),
            extensionDurationSeconds = preferences.getLong("extension", defaults.extensionDurationSeconds),
            firstSessionReminderSeconds = preferences.getLong("notification_first", defaults.firstSessionReminderSeconds),
            secondSessionReminderSeconds = preferences.getLong("notification_second", defaults.secondSessionReminderSeconds),
        )
        require(DebugTimingParameter.entries.all { it.seconds(loaded) in 1L..DurationInput.MAX_SECONDS })
        loaded.copy(debugTimeOverridesEnabled = loaded != defaults)
    } catch (_: IllegalArgumentException) {
        TimeConfig.production()
    }

    override fun write(config: TimeConfig) {
        preferences.edit()
            .putLong("increment_first", config.sessionFirstIncrementSeconds)
            .putLong("increment_second", config.sessionSecondIncrementSeconds)
            .putLong("increment_repeat", config.sessionRepeatingIncrementSeconds)
            .putLong("idle_reset", config.frictionResetIdleSeconds)
            .putLong("gate", config.frictionGateSeconds)
            .putLong("extension", config.extensionDurationSeconds)
            .putLong("notification_first", config.firstSessionReminderSeconds)
            .putLong("notification_second", config.secondSessionReminderSeconds)
            .apply()
    }

    override fun clear() { preferences.edit().clear().apply() }
}
