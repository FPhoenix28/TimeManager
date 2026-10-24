package com.example.timemanager.ui.limit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import com.example.timemanager.R
import com.example.timemanager.ui.components.AppIcon
import com.example.timemanager.ui.components.EmptyState
import com.example.timemanager.ui.components.FullScreenError
import com.example.timemanager.ui.components.FullScreenLoading
import com.example.timemanager.ui.theme.TimeManagerSpacing

@Composable
fun LimitScreen(
    viewModel: LimitViewModel,
    onBack: () -> Unit,
    onAddApp: () -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
    onOpenAppDetail: (String) -> Unit = viewModel::beginEdit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    when (val state = uiState) {
        LimitUiState.Loading -> FullScreenLoading(modifier)
        is LimitUiState.Success -> {
            ManagedAppsList(
                apps = state.apps,
                onBack = onBack,
                showBack = showBack,
                onAddApp = onAddApp,
                onOpenPermissions = onOpenPermissions,
                onEdit = if (com.example.timemanager.config.BuildTimeConfig.supportsPreciseAppDurations)
                    onOpenAppDetail else viewModel::beginEdit,
                onRemove = viewModel::removeManagedApp,
                modifier = modifier,
            )
            state.editor?.let { editor ->
                LimitEditorDialog(
                    editor = editor,
                    onInputChange = viewModel::updateLimitInput,
                    onDismiss = viewModel::dismissEditor,
                    onSave = viewModel::saveEditor,
                )
            }
        }
        is LimitUiState.Error -> ErrorContent(
            message = state.message,
            onRetry = viewModel::loadApps,
            onBack = onBack,
            modifier = modifier,
        )
    }
}

@Composable
private fun ManagedAppsList(
    apps: List<ManagedAppItemUiState>,
    onBack: () -> Unit,
    showBack: Boolean,
    onAddApp: () -> Unit,
    onOpenPermissions: () -> Unit,
    onEdit: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = TimeManagerSpacing.medium,
            top = TimeManagerSpacing.small,
            end = TimeManagerSpacing.medium,
            bottom = TimeManagerSpacing.extraLarge,
        ),
    ) {
        item("header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = TimeManagerSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showBack) {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "返回",
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("应用管理", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "只对你主动选择的应用进行计时和提醒",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item("add") {
            Button(
                onClick = onAddApp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = TimeManagerSpacing.large),
            ) { Text("添加管理应用") }
        }
        item("appsTitle") {
            Text(
                text = "已管理应用",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(
                    top = TimeManagerSpacing.extraLarge,
                    bottom = TimeManagerSpacing.small,
                ),
            )
        }

        if (apps.isEmpty()) {
            item("empty") {
                EmptyState(
                    title = "还没有管理任何应用",
                    message = "先选择一个容易占用你时间的应用，\n再为它设置每日额度。",
                    actionLabel = "选择应用",
                    onAction = onAddApp,
                    modifier = Modifier.padding(vertical = TimeManagerSpacing.large),
                )
            }
        } else {
            items(items = apps, key = ManagedAppItemUiState::packageName) { app ->
                ManagedAppRow(
                    app = app,
                    onEdit = { onEdit(app.packageName) },
                    onRemove = { onRemove(app.packageName) },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = TimeManagerSpacing.extraLarge * 3),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }

        item("systemTitle") {
            Text(
                text = "系统设置",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(
                    top = TimeManagerSpacing.extraLarge,
                    bottom = TimeManagerSpacing.small,
                ),
            )
        }
        item("permissions") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = TimeManagerSpacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("权限与系统设置", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "查看使用情况、通知和悬浮窗权限",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onOpenPermissions) { Text("查看  ›") }
            }
        }
    }
}

@Composable
private fun ManagedAppRow(
    app: ManagedAppItemUiState,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (com.example.timemanager.config.BuildTimeConfig.supportsPreciseAppDurations)
                Modifier.clickable(enabled = !app.isRemoving, onClick = onEdit) else Modifier)
            .padding(vertical = TimeManagerSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(bitmap = app.appIcon, appName = app.appName)
        Spacer(Modifier.size(TimeManagerSpacing.medium))
        Column(modifier = Modifier.weight(1f)) {
            Text(app.appName, style = MaterialTheme.typography.titleSmall)
            Text(
                "今日 ${formatDuration(app.todayUsageSeconds)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (com.example.timemanager.config.BuildTimeConfig.supportsPreciseAppDurations)
                    app.limitSeconds?.let { "每日额度 ${com.example.timemanager.ui.detail.formatAppDetailDuration(it)}" } ?: "每日额度未设置"
                else app.limitMinutes?.let { "每日额度 $it 分钟" } ?: "每日额度未设置",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            TextButton(onClick = onEdit, enabled = !app.isRemoving) { Text("编辑") }
            TextButton(onClick = onRemove, enabled = !app.isRemoving) {
                Text(
                    text = if (app.isRemoving) "移除中" else "移除",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
fun LimitEditorDialog(
    editor: LimitEditorUiState,
    onInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editor.isNew) "添加 ${editor.appName}" else "编辑 ${editor.appName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small)) {
                Text(if (editor.isNew) "设置额度后，该应用才会加入管理列表。" else "修改每日使用额度。")
                OutlinedTextField(
                    value = editor.limitInput,
                    onValueChange = onInputChange,
                    label = { Text("每日额度") },
                    suffix = { Text("分钟") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = editor.errorMessage != null,
                    supportingText = editor.errorMessage?.let { message -> { Text(message) } },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = editor.limitInput.isNotBlank() && !editor.isSaving,
            ) { Text(if (editor.isSaving) "保存中" else "保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !editor.isSaving) { Text("取消") }
        },
    )
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    FullScreenError(
        title = "加载应用管理失败",
        message = message,
        onRetry = onRetry,
        onBack = onBack,
        modifier = modifier,
    )
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    return if (hours > 0L) "${hours}h ${minutes}min" else "$minutes min"
}
