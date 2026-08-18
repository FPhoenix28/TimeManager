package com.example.timemanager.monitor

import com.example.timemanager.data.model.SessionReminder
import com.example.timemanager.data.model.TimerState

/** Emits each continuous-use threshold at most once in one app session. */
class SessionTracker {
    private var activePackageName: String? = null
    private val sentReminders = mutableSetOf<SessionReminder>()

    fun update(timerState: TimerState): SessionReminder? {
        if (activePackageName != timerState.packageName) {
            startSession(timerState.packageName)
        }

        return when {
            timerState.currentSessionSeconds >= THIRTY_MINUTES_SECONDS &&
                SessionReminder.THIRTY_MINUTES !in sentReminders -> {
                sentReminders += SessionReminder.FIFTEEN_MINUTES
                sentReminders += SessionReminder.THIRTY_MINUTES
                SessionReminder.THIRTY_MINUTES
            }
            timerState.currentSessionSeconds >= FIFTEEN_MINUTES_SECONDS &&
                SessionReminder.FIFTEEN_MINUTES !in sentReminders -> {
                sentReminders += SessionReminder.FIFTEEN_MINUTES
                SessionReminder.FIFTEEN_MINUTES
            }
            else -> null
        }
    }

    fun endSession() {
        activePackageName = null
        sentReminders.clear()
    }

    private fun startSession(packageName: String) {
        activePackageName = packageName
        sentReminders.clear()
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60L
        const val FIFTEEN_MINUTES_SECONDS = 15L * SECONDS_PER_MINUTE
        const val THIRTY_MINUTES_SECONDS = 30L * SECONDS_PER_MINUTE
    }
}
