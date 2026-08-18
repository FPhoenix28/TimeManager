package com.example.timemanager.ui.selection

import com.example.timemanager.data.model.TrackedApp

sealed interface AppSelectionUiState {
    data object Loading : AppSelectionUiState
    data class Success(val apps: List<TrackedApp>) : AppSelectionUiState
    data class Error(val message: String) : AppSelectionUiState
}
