package com.example.timemanager.config

import com.example.timemanager.BuildConfig

/** Production parameters compiled only into release builds. */
object BuildTimeConfig {
    init {
        check(!BuildConfig.DEBUG) { "Release time configuration cannot run in a debug build." }
    }

    val value = TimeConfig(
        debugTimeOverridesEnabled = false,
        firstSessionReminderSeconds = 15L * 60L,
        secondSessionReminderSeconds = 30L * 60L,
        extensionDurationSeconds = 5L * 60L,
    )
}
