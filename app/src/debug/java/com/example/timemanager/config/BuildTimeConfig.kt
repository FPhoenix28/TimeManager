package com.example.timemanager.config

import com.example.timemanager.BuildConfig

/** Debug builds use production timing unless a test explicitly injects [fastRegression]. */
object BuildTimeConfig {
    init {
        check(BuildConfig.DEBUG) { "Debug time configuration cannot run in a release build." }
    }

    val value = TimeConfig(
        debugTimeOverridesEnabled = false,
        firstSessionReminderSeconds = 15L * 60L,
        secondSessionReminderSeconds = 30L * 60L,
        extensionDurationSeconds = TimeConfig.LIMIT_EXTENSION_SECONDS,
    )

    /** Explicit opt-in fixture for tests; production quota DataStore keys are never changed. */
    val fastRegression = TimeConfig(
        debugTimeOverridesEnabled = true,
        firstSessionReminderSeconds = 15L,
        secondSessionReminderSeconds = 30L,
        extensionDurationSeconds = 30L,
        frictionResetIdleSeconds = 30L,
        sessionInterventionOverrideSeconds = 20L,
    )
}
