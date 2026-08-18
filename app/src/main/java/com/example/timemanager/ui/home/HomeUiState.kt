package com.example.timemanager.ui.home

import com.example.timemanager.data.model.AppUsage

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Success(
        val appUsages: List<AppUsage>,
    ) : HomeUiState

    data class Error(
        val message: String,
    ) : HomeUiState
}
