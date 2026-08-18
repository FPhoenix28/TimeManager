package com.example.timemanager.data.model

/** Result of evaluating today's real-time usage against one daily limit. */
data class UsageLimitEvaluation(
    val packageName: String,
    val usedSeconds: Long,
    val limitSeconds: Long,
    val remainingSeconds: Long,
    val progress: Float,
    val hasReachedWarning: Boolean,
    val isLimitReached: Boolean,
)
