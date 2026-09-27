package com.example.timemanager.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.timemanager.data.model.FocusModeState
import com.example.timemanager.data.model.InstalledApp
import com.example.timemanager.data.repository.FocusModeRepository
import com.example.timemanager.data.repository.InstalledAppRepository
import com.example.timemanager.data.repository.TrackedAppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FocusUiState(
    val apps: List<InstalledApp> = emptyList(),
    val managedPackages: Set<String> = emptySet(),
    val selectedPackages: Set<String> = emptySet(),
    val durationMinutes: Int? = 25,
    val customDurationInput: String = "",
    val focus: FocusModeState = FocusModeState(),
    val isLoadingApps: Boolean = true,
    val isStarting: Boolean = false,
    val error: String? = null,
) {
    val canStart: Boolean get() = !isStarting &&
        !isLoadingApps &&
        selectedPackages.isNotEmpty() &&
        durationMinutes != null
}

class FocusViewModel(
    private val focusRepository: FocusModeRepository,
    private val installedAppRepository: InstalledAppRepository,
    private val trackedAppRepository: TrackedAppRepository,
    private val ownPackageName: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FocusUiState())
    val uiState: StateFlow<FocusUiState> = _uiState.asStateFlow()
    private val _focusState = MutableStateFlow(FocusModeState())
    val focusState: StateFlow<FocusModeState> = _focusState.asStateFlow()
    private var appsLoaded = false
    private var expiryJob: Job? = null

    init {
        viewModelScope.launch {
            focusRepository.stopIfExpired()
            focusRepository.observeState().collect { focus ->
                _focusState.value = focus
                _uiState.update { it.copy(focus = focus) }
                expiryJob?.cancel()
                if (focus.isActive()) {
                    expiryJob = launch {
                        delay(focus.remainingMillis())
                        focusRepository.stopIfExpired()
                    }
                }
            }
        }
        viewModelScope.launch {
            trackedAppRepository.observeTrackedPackageNames().collect { packages ->
                _uiState.update { it.copy(managedPackages = packages) }
                reorderApps()
            }
        }
    }

    fun loadAppsIfNeeded() {
        if (appsLoaded) return
        appsLoaded = true
        viewModelScope.launch {
            try {
                val apps = withContext(Dispatchers.IO) {
                    installedAppRepository.getUserInstalledApps()
                }.filter { it.packageName != ownPackageName }
                _uiState.update { it.copy(apps = apps, isLoadingApps = false) }
                reorderApps()
            } catch (error: Exception) {
                appsLoaded = false
                _uiState.update {
                    it.copy(isLoadingApps = false, error = error.message ?: "无法读取应用列表")
                }
            }
        }
    }

    fun togglePackage(packageName: String) {
        if (packageName == ownPackageName) return
        _uiState.update { state ->
            val selected = state.selectedPackages.toMutableSet()
            if (!selected.add(packageName)) selected.remove(packageName)
            state.copy(selectedPackages = selected)
        }
    }

    fun selectAllManaged() {
        _uiState.update { state ->
            state.copy(
                selectedPackages = state.selectedPackages +
                    state.apps.map(InstalledApp::packageName).filter { it in state.managedPackages },
            )
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

    fun startFocus(onStarted: () -> Unit) {
        val state = _uiState.value
        val duration = state.durationMinutes ?: return
        if (!state.canStart) return
        _uiState.update { it.copy(isStarting = true, error = null) }
        viewModelScope.launch {
            try {
                focusRepository.startFocus(duration, state.selectedPackages)
                val saved = focusRepository.getCurrentState()
                _focusState.value = saved
                _uiState.update { it.copy(focus = saved) }
                onStarted()
            } catch (error: Exception) {
                _uiState.update { it.copy(error = error.message ?: "无法开始专注") }
            } finally {
                _uiState.update { it.copy(isStarting = false) }
            }
        }
    }

    fun stopFocus(onStopped: () -> Unit) {
        viewModelScope.launch {
            focusRepository.stopFocus()
            onStopped()
        }
    }

    private fun reorderApps() {
        _uiState.update { state ->
            state.copy(
                apps = state.apps.sortedWith(
                    compareByDescending<InstalledApp> { it.packageName in state.managedPackages }
                        .thenBy { it.appName.lowercase() },
                ),
            )
        }
    }

    class Factory(
        private val focusRepository: FocusModeRepository,
        private val installedAppRepository: InstalledAppRepository,
        private val trackedAppRepository: TrackedAppRepository,
        private val ownPackageName: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FocusViewModel::class.java)) {
                return FocusViewModel(
                    focusRepository,
                    installedAppRepository,
                    trackedAppRepository,
                    ownPackageName,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
