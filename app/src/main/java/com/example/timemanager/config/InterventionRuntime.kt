package com.example.timemanager.config

import android.os.SystemClock
import com.example.timemanager.BuildConfig
import com.example.timemanager.domain.intervention.InterventionCoordinator
import com.example.timemanager.monitor.SessionFrictionManager

/** One lightweight runtime per application process; no Android component references. */
object InterventionRuntime {
    val coordinator by lazy {
        InterventionCoordinator(
            friction = SessionFrictionManager(
                clock = SystemClock::elapsedRealtime,
                timeConfigProvider = BuildTimeConfig.provider,
            ),
            ownPackage = BuildConfig.APPLICATION_ID,
        )
    }
}
