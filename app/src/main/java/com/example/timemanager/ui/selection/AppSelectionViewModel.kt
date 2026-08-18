package com.example.timemanager.ui.selection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.timemanager.data.model.InstalledApp
import com.example.timemanager.data.model.TrackedApp
import com.example.timemanager.data.repository.InstalledAppRepository
import com.example.timemanager.data.repository.TrackedAppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppSelectionViewModel(
    private val installedAppRepository: InstalledAppRepository,
    private val trackedAppRepository: TrackedAppRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<AppSelectionUiState>(AppSelectionUiState.Loading)
    val uiState: StateFlow<AppSelectionUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        loadApps()
    }

    fun loadApps() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = AppSelectionUiState.Loading
            val installedApps = runCatching {
                withContext(Dispatchers.IO) {
                    installedAppRepository.getUserInstalledApps()
                }
            }.getOrElse { error ->
                _uiState.value = AppSelectionUiState.Error(
                    error.message ?: "无法读取安装应用",
                )
                return@launch
            }

            trackedAppRepository.observeTrackedPackageNames()
                .catch { error ->
                    _uiState.value = AppSelectionUiState.Error(
                        error.message ?: "无法读取目标应用",
                    )
                }
                .collect { trackedPackages ->
                    _uiState.value = AppSelectionUiState.Success(
                        installedApps.map { app -> app.toTrackedApp(trackedPackages) },
                    )
                }
        }
    }

    fun setTracked(packageName: String, isTracked: Boolean) {
        updateSelection(packageName, isTracked)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                trackedAppRepository.setTracked(packageName, isTracked)
            }.onFailure { error ->
                _uiState.value = AppSelectionUiState.Error(
                    error.message ?: "保存目标应用失败",
                )
            }
        }
    }

    private fun updateSelection(packageName: String, isTracked: Boolean) {
        val state = _uiState.value as? AppSelectionUiState.Success ?: return
        _uiState.value = state.copy(
            apps = state.apps.map { app ->
                if (app.packageName == packageName) app.copy(isTracked = isTracked) else app
            },
        )
    }

    private fun InstalledApp.toTrackedApp(trackedPackages: Set<String>) = TrackedApp(
        packageName = packageName,
        appName = appName,
        appIcon = appIcon,
        isTracked = packageName in trackedPackages,
    )

    class Factory(
        private val installedAppRepository: InstalledAppRepository,
        private val trackedAppRepository: TrackedAppRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AppSelectionViewModel::class.java)) {
                return AppSelectionViewModel(installedAppRepository, trackedAppRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
