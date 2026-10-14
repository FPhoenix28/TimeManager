package com.example.timemanager.monitor

import com.example.timemanager.data.model.SessionReminder
import com.example.timemanager.data.model.TimerState

/** Physical-session notification reminders only. Episode progression lives in SessionFrictionManager. */
class SessionTracker(
    private val firstReminderSeconds: Long = 15L * 60L,
    private val secondReminderSeconds: Long = 30L * 60L,
) {
    private var activePackageName: String? = null
    private val sentReminders = mutableSetOf<SessionReminder>()

    fun update(timerState: TimerState): SessionReminder? {
        if (activePackageName != timerState.packageName) {
            startSession(timerState.packageName)
        }

        return when {
            timerState.currentSessionSeconds >= secondReminderSeconds &&
                SessionReminder.THIRTY_MINUTES !in sentReminders -> {
                sentReminders += SessionReminder.FIFTEEN_MINUTES
                sentReminders += SessionReminder.THIRTY_MINUTES
                SessionReminder.THIRTY_MINUTES
            }
            timerState.currentSessionSeconds >= firstReminderSeconds &&
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
}
