package com.example.timemanager.ui.home

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Success(
        val appUsages: List<AppUsageWithLimit>,
        val weeklyTrend: WeeklyTrendSummary,
    ) : HomeUiState

    data class Error(
        val message: String,
    ) : HomeUiState
}

data class WeeklyTrendSummary(
    val totalSeconds: Long,
    val changePercent: Double?,
)
