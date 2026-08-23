package com.example.timemanager.ui.limit

import android.util.Log
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.timemanager.data.model.AppLimit
import com.example.timemanager.data.model.InstalledApp
import com.example.timemanager.data.repository.InstalledAppRepository
import com.example.timemanager.data.repository.LimitRepository
import com.example.timemanager.data.repository.ManagedAppRepository
import com.example.timemanager.data.repository.TrackedAppRepository
import com.example.timemanager.data.repository.UsageTimeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LimitViewModel(
    private val installedAppRepository: InstalledAppRepository,
    private val trackedAppRepository: TrackedAppRepository,
    private val limitRepository: LimitRepository,
    private val usageTimeRepository: UsageTimeRepository,
    private val managedAppRepository: ManagedAppRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<LimitUiState>(LimitUiState.Loading)
    val uiState: StateFlow<LimitUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var installedAppsByPackage: Map<String, InstalledApp> = emptyMap()

    fun loadIfNeeded() {
        if (_uiState.value == LimitUiState.Loading && loadJob?.isActive != true) loadApps()
    }

    fun loadApps() {
        val hasContent = _uiState.value is LimitUiState.Success
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            if (!hasContent) _uiState.value = LimitUiState.Loading
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val installedApps = installedAppRepository.getUserInstalledApps()
                    installedAppsByPackage = installedApps.associateBy(InstalledApp::packageName)
                    val trackedPackages = trackedAppRepository.observeTrackedPackageNames().first()
                    val limitsByPackage = trackedPackages.associateWith { packageName ->
                        limitRepository.observeLimit(packageName).first()?.limitMinutes
                    }
                    val usageByPackage = usageTimeRepository.getTodayUsage()
                        .associateBy { usage -> usage.packageName }

                    LimitUiState.Success(
                        apps = installedApps.asSequence()
                            .filter { app -> app.packageName in trackedPackages }
                            .map { app ->
                                ManagedAppItemUiState(
                                    packageName = app.packageName,
                                    appName = app.appName,
                                    appIcon = app.appIcon,
                                    todayUsageSeconds = usageByPackage[app.packageName]
                                        ?.usageTimeMillis
                                        ?.div(MILLIS_PER_SECOND)
                                        ?: 0L,
                                    limitMinutes = limitsByPackage[app.packageName],
                                )
                            }
                            .sortedBy { app -> app.appName.lowercase() }
                            .toList(),
                    )
                }
            }
            result.onSuccess {
                _uiState.value = it
                Log.d(TAG_PERFORMANCE, "Total=${SystemClock.elapsedRealtime() - startedAt}ms apps=${it.apps.size}")
            }
            result.onFailure { error ->
                if (!hasContent) {
                    _uiState.value = LimitUiState.Error(
                        error.message ?: "无法加载应用管理数据",
                    )
                }
            }
        }
    }

    fun beginAdd(packageName: String) {
        val app = installedAppsByPackage[packageName]
        if (app != null) {
            showEditor(app, null, isNew = true)
            return
        }
        viewModelScope.launch {
            val installedApp = withContext(Dispatchers.IO) {
                installedAppRepository.getUserInstalledApps()
                    .firstOrNull { it.packageName == packageName }
            } ?: return@launch
            installedAppsByPackage = installedAppsByPackage + (packageName to installedApp)
            showEditor(installedApp, null, isNew = true)
        }
    }

    fun beginEdit(packageName: String) {
        val state = _uiState.value as? LimitUiState.Success ?: return
        val app = state.apps.firstOrNull { it.packageName == packageName } ?: return
        showEditor(
            InstalledApp(app.packageName, app.appName, app.appIcon),
            app.limitMinutes,
            isNew = false,
        )
    }

    fun updateLimitInput(input: String) {
        if (input.length > MAX_LIMIT_INPUT_LENGTH || input.any { !it.isDigit() }) return
        updateEditor { it.copy(limitInput = input, errorMessage = null) }
    }

    fun dismissEditor() {
        val state = _uiState.value as? LimitUiState.Success ?: return
        if (state.editor?.isSaving == true) return
        _uiState.value = state.copy(editor = null)
    }

    fun saveEditor() {
        val state = _uiState.value as? LimitUiState.Success ?: return
        val editor = state.editor ?: return
        val limitMinutes = editor.limitInput.toIntOrNull()
        if (limitMinutes == null) {
            updateEditor { it.copy(errorMessage = "请输入有效分钟数") }
            return
        }
        Log.d(
            TAG,
            "userInput=$limitMinutes packageName=${editor.packageName}",
        )

        updateEditor { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (!editor.isNew && !trackedAppRepository.isTracked(editor.packageName)) {
                        error("该应用已不在管理列表中")
                    }
                    limitRepository.saveLimit(AppLimit(editor.packageName, limitMinutes))
                    if (editor.isNew) {
                        trackedAppRepository.setTracked(editor.packageName, true)
                    }
                }
            }.fold(
                onSuccess = { loadApps() },
                onFailure = { error ->
                    updateEditor {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.message ?: "保存失败",
                        )
                    }
                },
            )
        }
    }

    fun removeManagedApp(packageName: String) {
        updateManagedApp(packageName) { it.copy(isRemoving = true) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    managedAppRepository.remove(packageName)
                }
            }.fold(
                onSuccess = { loadApps() },
                onFailure = { error ->
                    _uiState.value = LimitUiState.Error(
                        error.message ?: "移除管理应用失败",
                    )
                },
            )
        }
    }

    private fun showEditor(app: InstalledApp, limitMinutes: Int?, isNew: Boolean) {
        val state = _uiState.value as? LimitUiState.Success ?: return
        _uiState.value = state.copy(
            editor = LimitEditorUiState(
                packageName = app.packageName,
                appName = app.appName,
                appIcon = app.appIcon,
                limitInput = limitMinutes?.toString().orEmpty(),
                isNew = isNew,
            ),
        )
    }

    private fun updateEditor(transform: (LimitEditorUiState) -> LimitEditorUiState) {
        val state = _uiState.value as? LimitUiState.Success ?: return
        val editor = state.editor ?: return
        _uiState.value = state.copy(editor = transform(editor))
    }

    private fun updateManagedApp(
        packageName: String,
        transform: (ManagedAppItemUiState) -> ManagedAppItemUiState,
    ) {
        val state = _uiState.value as? LimitUiState.Success ?: return
        _uiState.value = state.copy(
            apps = state.apps.map { app ->
                if (app.packageName == packageName) transform(app) else app
            },
        )
    }

    class Factory(
        private val installedAppRepository: InstalledAppRepository,
        private val trackedAppRepository: TrackedAppRepository,
        private val limitRepository: LimitRepository,
        private val usageTimeRepository: UsageTimeRepository,
        private val managedAppRepository: ManagedAppRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LimitViewModel::class.java)) {
                return LimitViewModel(
                    installedAppRepository,
                    trackedAppRepository,
                    limitRepository,
                    usageTimeRepository,
                    managedAppRepository,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    private companion object {
        const val TAG = "LimitUI"
        const val TAG_PERFORMANCE = "ManagementLoad"
        const val MAX_LIMIT_INPUT_LENGTH = 4
        const val MILLIS_PER_SECOND = 1_000L
    }
}
