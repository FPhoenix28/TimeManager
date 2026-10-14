package com.example.timemanager.ui.detail

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import com.example.timemanager.ui.limit.LimitEditorUiState

sealed interface ManagedAppDetailUiState {
    data object Loading : ManagedAppDetailUiState
    data object Removed : ManagedAppDetailUiState
    data class Error(val message: String) : ManagedAppDetailUiState

    @Immutable
    data class Ready(
        val packageName: String,
        val appName: String,
        val appIcon: Bitmap?,
        val isInstalled: Boolean,
        val todayUsageSeconds: Long,
        val baseLimitSeconds: Long?,
        val extensionSeconds: Long,
        val effectiveLimitSeconds: Long?,
        val remainingSeconds: Long,
        val exceededSeconds: Long,
        val sessionReminderMinutes: Int,
        val isOverLimit: Boolean,
        val historyTotalSeconds: Long = 0L,
        val historyChangePercent: Double? = null,
        val editor: LimitEditorUiState? = null,
        val sessionEditor: SessionReminderEditorUiState? = null,
        val isRemoving: Boolean = false,
        val errorMessage: String? = null,
    ) : ManagedAppDetailUiState
}

@Immutable
data class SessionReminderEditorUiState(
    val input: String,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)
