package com.example.timemanager.config

import com.example.timemanager.BuildConfig
import android.content.Context

/** Production parameters compiled only into release builds. */
object BuildTimeConfig {
    init {
        check(!BuildConfig.DEBUG) { "Release time configuration cannot run in a debug build." }
    }

    val value = TimeConfig.production()
    const val supportsPreciseAppDurations = false
    val provider = TimeConfigProvider { value }
    fun initialize(@Suppress("UNUSED_PARAMETER") context: Context) = Unit
    fun logIfChanged() = Unit
}
