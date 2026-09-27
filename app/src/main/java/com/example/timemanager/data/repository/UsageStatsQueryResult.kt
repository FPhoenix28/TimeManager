package com.example.timemanager.data.repository

import com.example.timemanager.data.model.AppUsage

sealed interface UsageStatsQueryResult {
    data class Success(val usages: List<AppUsage>) : UsageStatsQueryResult
    data object Unavailable : UsageStatsQueryResult
}
