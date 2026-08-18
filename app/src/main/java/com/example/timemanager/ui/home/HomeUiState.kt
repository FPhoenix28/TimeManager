package com.example.timemanager.ui.home

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Success(
        val appUsages: List<AppUsageWithLimit>,
    ) : HomeUiState

    data class Error(
        val message: String,
    ) : HomeUiState
}
