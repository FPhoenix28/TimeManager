package com.example.timemanager.ui.limit

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable

sealed interface LimitUiState {
    data object Loading : LimitUiState

    @Immutable
    data class Success(
        val apps: List<LimitAppItemUiState>,
    ) : LimitUiState

    data class Error(
        val message: String,
    ) : LimitUiState
}

@Immutable
data class LimitAppItemUiState(
    val packageName: String,
    val appName: String,
    val appIcon: Bitmap?,
    val limitInput: String,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
)
