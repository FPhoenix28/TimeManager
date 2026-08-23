package com.example.timemanager.timer

import android.os.SystemClock
import android.util.Log
import com.example.timemanager.data.model.ForegroundApp
import com.example.timemanager.data.model.TimerState

/** Tracks uninterrupted foreground sessions in memory using a monotonic clock. */
class AppTimer(
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {
    private var currentApp: ForegroundApp? = null
    private var sessionStartedAtMillis = 0L
    private val completedSessions = mutableListOf<TimerState>()

    fun update(foregroundApp: ForegroundApp): TimerState {
        val now = elapsedRealtime()
        val activeApp = currentApp

        if (activeApp == null) {
            startSession(foregroundApp, now)
            Log.d(TAG, "session started packageName=${foregroundApp.packageName}")
        } else if (activeApp.packageName != foregroundApp.packageName) {
            saveCurrentSession(now)
            startSession(foregroundApp, now)
            Log.d(
                TAG,
                "session switched from=${activeApp.packageName} to=${foregroundApp.packageName}",
            )
        } else {
            currentApp = foregroundApp
        }

        return createCurrentState(now)
    }

    fun finishCurrentSession(): TimerState? {
        if (currentApp == null) return null
        return saveCurrentSession(elapsedRealtime()).also { state ->
            Log.d(TAG, "session finished packageName=${state.packageName} seconds=${state.currentSessionSeconds}")
        }
    }

    fun getCompletedSessions(): List<TimerState> = completedSessions.toList()

    private fun startSession(app: ForegroundApp, now: Long) {
        currentApp = app
        sessionStartedAtMillis = now
    }

    private fun saveCurrentSession(now: Long): TimerState {
        val finalState = createCurrentState(now)
        completedSessions += finalState
        currentApp = null
        sessionStartedAtMillis = 0L
        return finalState
    }

    private fun createCurrentState(now: Long): TimerState {
        val app = checkNotNull(currentApp)
        val elapsedSeconds = ((now - sessionStartedAtMillis) / MILLIS_PER_SECOND)
            .coerceAtLeast(0L)
        return TimerState(
            packageName = app.packageName,
            appName = app.appName,
            currentSessionSeconds = elapsedSeconds,
        )
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val TAG = "AppTimer"
    }
}
