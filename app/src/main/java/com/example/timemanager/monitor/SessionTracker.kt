package com.example.timemanager.monitor

import com.example.timemanager.data.model.SessionReminder
import com.example.timemanager.data.model.TimerState

/** Tracks notification and progressive intervention state for one continuous app session. */
class SessionTracker(
    private val firstReminderSeconds: Long = 15L * 60L,
    private val secondReminderSeconds: Long = 30L * 60L,
) {
    private var activePackageName: String? = null
    private val sentReminders = mutableSetOf<SessionReminder>()
    private var interventionFirstReminderSeconds = 0L
    private var interventionReminderCount = 0
    private var nextInterventionReminderAtSeconds = 0L

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
        resetInterventionProgress()
    }

    /** Checks whether the next overlay is due without consuming or advancing it. */
    fun evaluateSessionIntervention(
        timerState: TimerState,
        configuredReminderSeconds: Long,
    ): SessionInterventionProgress {
        require(configuredReminderSeconds > 0L)
        if (activePackageName != timerState.packageName) {
            startSession(timerState.packageName)
        }

        // A changed setting affects the active session only before its first intervention.
        if (interventionReminderCount == 0) {
            interventionFirstReminderSeconds = configuredReminderSeconds
            nextInterventionReminderAtSeconds = configuredReminderSeconds
        }

        return SessionInterventionProgress(
            packageName = timerState.packageName,
            firstReminderSeconds = interventionFirstReminderSeconds,
            reminderCount = interventionReminderCount,
            nextReminderAtSeconds = nextInterventionReminderAtSeconds,
            isDue = timerState.currentSessionSeconds >= nextInterventionReminderAtSeconds,
        )
    }

    /** Advances the schedule only after the session overlay was actually displayed. */
    fun onSessionInterventionShown(packageName: String): SessionInterventionProgress {
        require(activePackageName == packageName) { "No active session for $packageName" }
        require(nextInterventionReminderAtSeconds > 0L) {
            "Session intervention has not been configured"
        }

        interventionReminderCount += 1
        nextInterventionReminderAtSeconds += when (interventionReminderCount) {
            1, 2 -> FIRST_FOLLOW_UP_INTERVAL_SECONDS
            else -> LATER_FOLLOW_UP_INTERVAL_SECONDS
        }
        return currentInterventionProgress(packageName)
    }

    fun currentInterventionProgress(packageName: String): SessionInterventionProgress =
        SessionInterventionProgress(
            packageName = packageName,
            firstReminderSeconds = interventionFirstReminderSeconds,
            reminderCount = interventionReminderCount,
            nextReminderAtSeconds = nextInterventionReminderAtSeconds,
            isDue = false,
        )

    private fun startSession(packageName: String) {
        activePackageName = packageName
        sentReminders.clear()
        resetInterventionProgress()
    }

    private fun resetInterventionProgress() {
        interventionFirstReminderSeconds = 0L
        interventionReminderCount = 0
        nextInterventionReminderAtSeconds = 0L
    }

    private companion object {
        const val FIRST_FOLLOW_UP_INTERVAL_SECONDS = 8L * 60L
        const val LATER_FOLLOW_UP_INTERVAL_SECONDS = 5L * 60L
    }
}

data class SessionInterventionProgress(
    val packageName: String,
    val firstReminderSeconds: Long,
    val reminderCount: Int,
    val nextReminderAtSeconds: Long,
    val isDue: Boolean,
)
