package com.example.timemanager.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.timemanager.data.model.FocusModeState
import com.example.timemanager.data.repository.FocusModeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeepFocusUiState(
    val durationMinutes: Int? = 25,
    val customDurationInput: String = "",
    val focus: FocusModeState = FocusModeState(),
    val isStarting: Boolean = false,
    val error: String? = null,
) {
    val canStart: Boolean get() = !isStarting && durationMinutes != null
}

class DeepFocusViewModel(
    private val focusRepository: FocusModeRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DeepFocusUiState())
    val uiState: StateFlow<DeepFocusUiState> = _uiState.asStateFlow()
    private var expiryJob: Job? = null

    init {
        viewModelScope.launch {
            focusRepository.stopIfExpired()
            focusRepository.observeState().collect { focus ->
                _uiState.update { it.copy(focus = focus) }
                expiryJob?.cancel()
                if (focus.isDeepFocusActive()) {
                    expiryJob = launch {
                        delay(focus.remainingMillis())
                        focusRepository.stopIfExpired()
                    }
                }
            }
        }
    }

    fun selectPreset(minutes: Int) {
        _uiState.update { it.copy(durationMinutes = minutes, customDurationInput = "") }
    }

    fun updateCustomDuration(input: String) {
        if (input.any { !it.isDigit() } || input.length > 3) return
        _uiState.update {
            it.copy(
                customDurationInput = input,
                durationMinutes = input.toIntOrNull()?.takeIf { value -> value in 1..720 },
            )
        }
    }

    fun startDeepFocus(replaceActiveFocus: Boolean, onStarted: () -> Unit) {
        val duration = _uiState.value.durationMinutes ?: return
        if (!_uiState.value.canStart) return
        _uiState.update { it.copy(isStarting = true, error = null) }
        viewModelScope.launch {
            try {
                focusRepository.startDeepFocus(duration, replaceActiveFocus)
                onStarted()
            } catch (error: Exception) {
                _uiState.update { it.copy(error = error.message ?: "无法开始静心") }
            } finally {
                _uiState.update { it.copy(isStarting = false) }
            }
        }
    }

    fun endDeepFocus(onEnded: () -> Unit) {
        viewModelScope.launch {
            focusRepository.stopFocus()
            onEnded()
        }
    }

    class Factory(
        private val focusRepository: FocusModeRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DeepFocusViewModel::class.java)) {
                return DeepFocusViewModel(focusRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
