package com.example.timemanager.data.model

enum class SessionGatePhase { REMINDER, REENTRY }

/** Deadline is an absolute elapsedRealtime value, not a Compose-owned countdown. */
data class SessionGate(
    val id: Long,
    val phase: SessionGatePhase,
    val endTimeMillis: Long,
    val reminderCommitted: Boolean = false,
)

/** Short-lived, process-only snapshot. SessionFrictionManager is its only writer. */
data class SessionFrictionState(
    val packageName: String,
    val firstReminderSeconds: Long,
    val nextReminderAtSeconds: Long,
    val episodeUsageSeconds: Long = 0L,
    val reminderCount: Int = 0,
    val isActive: Boolean = false,
    val lastEngagementTimeMillis: Long,
    val pendingGate: SessionGate? = null,
    val bypassExpiresAtMillis: Long? = null,
    val allowedForeground: Boolean = false,
    val reentryGateArmed: Boolean = false,
) {
    // reminderCount is the stage ID; only a new committed reminder can rearm it.
    val reentryGateConsumed: Boolean get() = reminderCount > 0 && !reentryGateArmed
}
