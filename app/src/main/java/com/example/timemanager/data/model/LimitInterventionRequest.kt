package com.example.timemanager.data.model

import com.example.timemanager.domain.intervention.InterventionPresentation

data class LimitInterventionRequest(
    val type: InterventionType = InterventionType.DAILY_LIMIT_REACHED,
    val packageName: String,
    val appName: String,
    val todayUsageSeconds: Long,
    val baseLimitSeconds: Long,
    val extensionSeconds: Long,
    val effectiveLimitSeconds: Long,
    val exceededSeconds: Long,
    val currentSessionSeconds: Long,
    val extensionDurationSeconds: Long,
    val configuredSessionReminderSeconds: Long = 10L * 60L,
    val sessionGate: SessionGate? = null,
    val presentation: InterventionPresentation = InterventionPresentation.HOME_FIRST,
    val actionDeadlineMillis: Long? = null,
) {
    companion object {
        fun sessionReminder(
            packageName: String,
            appName: String,
            todayUsageSeconds: Long,
            currentSessionSeconds: Long,
            configuredReminderSeconds: Long,
            gate: SessionGate,
            presentation: InterventionPresentation = InterventionPresentation.HOME_FIRST,
        ) = LimitInterventionRequest(
            type = InterventionType.SESSION_REMINDER,
            packageName = packageName,
            appName = appName,
            todayUsageSeconds = todayUsageSeconds,
            baseLimitSeconds = 0L,
            extensionSeconds = 0L,
            effectiveLimitSeconds = 0L,
            exceededSeconds = 0L,
            currentSessionSeconds = currentSessionSeconds,
            extensionDurationSeconds = 0L,
            configuredSessionReminderSeconds = configuredReminderSeconds,
            sessionGate = gate,
            presentation = presentation,
        )
    }
}

sealed interface LimitInterventionDecision {
    data object None : LimitInterventionDecision
    data object Hide : LimitInterventionDecision
    data class Show(val request: LimitInterventionRequest) : LimitInterventionDecision
}
