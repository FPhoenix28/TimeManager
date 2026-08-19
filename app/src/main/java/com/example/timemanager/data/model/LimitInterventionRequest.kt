package com.example.timemanager.data.model

data class LimitInterventionRequest(
    val packageName: String,
    val appName: String,
    val todayUsageSeconds: Long,
    val baseLimitSeconds: Long,
    val extensionSeconds: Long,
    val effectiveLimitSeconds: Long,
    val exceededSeconds: Long,
    val currentSessionSeconds: Long,
    val extensionsRemaining: Int,
)

sealed interface LimitInterventionDecision {
    data object None : LimitInterventionDecision
    data object Hide : LimitInterventionDecision
    data class Show(val request: LimitInterventionRequest) : LimitInterventionDecision
}
