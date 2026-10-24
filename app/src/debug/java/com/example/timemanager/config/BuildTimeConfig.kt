package com.example.timemanager.config

import com.example.timemanager.BuildConfig
import android.content.Context
import android.util.Log

/** Dev starts with production globals; per-app precise settings stay in their repositories. */
object BuildTimeConfig {
    init {
        check(BuildConfig.DEBUG) { "Debug time configuration cannot run in a release build." }
    }

    @Volatile private var runtime: DebugTimingController? = null
    private var lastLogged: TimeConfig? = null
    val value: TimeConfig get() = runtime?.current() ?: TimeConfig.production()
    const val supportsPreciseAppDurations = true
    val provider = object : TimeConfigProvider {
        override fun current() = value
        override fun observe() = runtime?.observe() ?: kotlinx.coroutines.flow.flowOf(value)
    }
    val controller: DebugTimingController get() = checkNotNull(runtime)

    @Synchronized fun initialize(context: Context) {
        if (runtime == null) runtime = DebugTimingController(DebugTimingPreferences(context))
    }

    /** Called on monitor start/ticks, logs only when the effective configuration changes. */
    @Synchronized fun logIfChanged() {
        val current = value
        if (current == lastLogged) return
        lastLogged = current
        Log.d("DebugTestTiming", "${if (current.debugTimeOverridesEnabled) "TEST_TIMING_ACTIVE" else "PRODUCTION_TIMING"} $current")
    }

    /** Explicit opt-in fixture for tests; production quota DataStore keys are never changed. */
    val fastRegression = DebugTimingPresets.legacyFastRegression
}
