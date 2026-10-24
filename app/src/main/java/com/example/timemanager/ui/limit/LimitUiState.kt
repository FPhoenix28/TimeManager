package com.example.timemanager.ui.limit

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable

sealed interface LimitUiState {
    data object Loading : LimitUiState
    @Immutable
    data class Success(
        val apps: List<ManagedAppItemUiState>,
        val editor: LimitEditorUiState? = null,
    ) : LimitUiState
    data class Error(val message: String) : LimitUiState
}

@Immutable
data class ManagedAppItemUiState(
    val packageName: String,
    val appName: String,
    val appIcon: Bitmap?,
    val todayUsageSeconds: Long,
    val limitMinutes: Int?,
    val isRemoving: Boolean = false,
    val limitSeconds: Long? = limitMinutes?.times(60L),
)

@Immutable
data class LimitEditorUiState(
    val packageName: String,
    val appName: String,
    val appIcon: Bitmap?,
    val limitInput: String,
    val isNew: Boolean,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)
