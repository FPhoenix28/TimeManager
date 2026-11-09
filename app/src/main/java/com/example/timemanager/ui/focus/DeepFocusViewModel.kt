package com.example.timemanager.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.timemanager.data.model.FocusModeState
import com.example.timemanager.data.repository.FocusModeRepository
import com.example.timemanager.data.repository.TodoRepository
import com.example.timemanager.reminders.DelayedDeepFocusScheduler
import com.example.timemanager.reminders.DelayedDeepFocusStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.CancellationException
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
    private val delayedScheduler: DelayedDeepFocusScheduler,
    todoRepository: TodoRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DeepFocusUiState())
    val uiState: StateFlow<DeepFocusUiState> = _uiState.asStateFlow()
    val todoDayState = todoRepository.dayState
    val delayedStart = delayedScheduler.observe()
        .catch { emit(DelayedDeepFocusStatus(notice = "无法读取延迟静心安排，请稍后重试。")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DelayedDeepFocusStatus())
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

    fun cancelDelayedStart() {
        val workId = delayedStart.value.workId.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            try {
                val message = delayedScheduler.cancelPending(workId)
                _uiState.update { it.copy(error = message) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { _uiState.update { it.copy(error = "未能取消安排，请重试。") } }
        }
    }

    class Factory(
        private val focusRepository: FocusModeRepository,
        private val delayedScheduler: DelayedDeepFocusScheduler,
        private val todoRepository: TodoRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DeepFocusViewModel::class.java)) {
                return DeepFocusViewModel(focusRepository, delayedScheduler, todoRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
