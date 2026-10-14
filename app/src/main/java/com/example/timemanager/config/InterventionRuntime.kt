package com.example.timemanager.config

import android.os.SystemClock
import com.example.timemanager.BuildConfig
import com.example.timemanager.domain.intervention.InterventionCoordinator
import com.example.timemanager.monitor.SessionFrictionManager

/** One lightweight runtime per application process; no Android component references. */
object InterventionRuntime {
    val coordinator by lazy {
        val config = BuildTimeConfig.value
        InterventionCoordinator(
            friction = SessionFrictionManager(
                clock = SystemClock::elapsedRealtime,
                gateDurationMillis = config.frictionGateSeconds * 1_000L,
                idleResetMillis = config.frictionResetIdleSeconds * 1_000L,
                bypassDurationMillis = config.frictionBypassSeconds * 1_000L,
            ),
            ownPackage = BuildConfig.APPLICATION_ID,
        )
    }
}
