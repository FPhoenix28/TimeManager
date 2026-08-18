package com.example.timemanager.domain.limit

import com.example.timemanager.data.model.UsageLimitEvaluation

/** Pure limit calculation with no Android or persistence dependencies. */
class UsageLimitEvaluator {
    fun evaluate(
        packageName: String,
        todayUsageSeconds: Long,
        limitMinutes: Int,
    ): UsageLimitEvaluation {
        require(packageName.isNotBlank()) { "packageName must not be blank." }
        require(todayUsageSeconds >= 0L) { "todayUsageSeconds must not be negative." }
        require(limitMinutes >= 0) { "limitMinutes must not be negative." }

        val limitSeconds = limitMinutes.toLong() * SECONDS_PER_MINUTE
        val hasUsage = todayUsageSeconds > 0L
        val rawProgress = when {
            limitSeconds == 0L && hasUsage -> 1.0
            limitSeconds == 0L -> 0.0
            else -> todayUsageSeconds.toDouble() / limitSeconds
        }

        return UsageLimitEvaluation(
            packageName = packageName,
            usedSeconds = todayUsageSeconds,
            limitSeconds = limitSeconds,
            remainingSeconds = (limitSeconds - todayUsageSeconds).coerceAtLeast(0L),
            progress = rawProgress.coerceIn(0.0, 1.0).toFloat(),
            hasReachedWarning = hasUsage && rawProgress >= WARNING_PROGRESS,
            isLimitReached = hasUsage && rawProgress >= LIMIT_REACHED_PROGRESS,
        )
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60L
        const val WARNING_PROGRESS = 0.8
        const val LIMIT_REACHED_PROGRESS = 1.0
    }
}
