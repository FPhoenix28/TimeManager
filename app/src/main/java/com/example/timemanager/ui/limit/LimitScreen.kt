package com.example.timemanager.ui.limit

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun LimitScreen(
    viewModel: LimitViewModel,
    onBack: () -> Unit,
    onAddApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    BackHandler(onBack = onBack)

    when (val state = uiState) {
        LimitUiState.Loading -> LoadingContent(modifier)
        is LimitUiState.Success -> {
            ManagedAppsList(
                apps = state.apps,
                onBack = onBack,
                onAddApp = onAddApp,
                onEdit = viewModel::beginEdit,
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
    onAddApp: () -> Unit,
    onEdit: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "management_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("返回") }
                Column(modifier = Modifier.weight(1f)) {
                    Text("应用管理", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "仅管理已选择的应用",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item(key = "add_managed_app") {
            Button(
                onClick = onAddApp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("添加管理应用")
            }
        }

        if (apps.isEmpty()) {
            item(key = "managed_apps_empty") {
                Text("还没有管理任何应用，请先添加应用并设置每日额度")
            }
        } else {
            items(items = apps, key = ManagedAppItemUiState::packageName) { app ->
                ManagedAppCard(
                    app = app,
                    onEdit = { onEdit(app.packageName) },
                    onRemove = { onRemove(app.packageName) },
                )
            }
        }
    }
}

@Composable
private fun ManagedAppCard(
    app: ManagedAppItemUiState,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app.appIcon, app.appName)
                Spacer(modifier = Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.appName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "今日使用：${formatDuration(app.todayUsageSeconds)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = app.limitMinutes?.let { "每日额度：$it 分钟" }
                            ?: "每日额度：未设置",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onEdit, enabled = !app.isRemoving) { Text("编辑") }
                TextButton(onClick = onRemove, enabled = !app.isRemoving) {
                    Text(if (app.isRemoving) "移除中" else "移除管理")
                }
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (editor.isNew) "设置额度后，该应用才会加入管理列表。" else "修改每日使用额度。")
                OutlinedTextField(
                    value = editor.limitInput,
                    onValueChange = onInputChange,
                    label = { Text("每日额度") },
                    suffix = { Text("分钟") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = editor.errorMessage != null,
                    supportingText = editor.errorMessage?.let { message ->
                        { Text(message) }
                    },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = editor.limitInput.isNotBlank() && !editor.isSaving,
            ) {
                Text(if (editor.isSaving) "保存中" else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !editor.isSaving) { Text("取消") }
        },
    )
}

@Composable
private fun AppIcon(appIcon: Bitmap?, appName: String) {
    val imageBitmap = remember(appIcon) { appIcon?.asImageBitmap() }
    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = "$appName 图标",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
        )
    } else {
        Text(
            text = appName.firstOrNull()?.toString() ?: "?",
            modifier = Modifier.size(48.dp),
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

@Composable
private fun LoadingContent(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { CircularProgressIndicator() }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("加载应用管理失败")
        Text(message)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack) { Text("返回") }
            Button(onClick = onRetry) { Text("重试") }
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    return if (hours > 0L) "$hours 小时 $minutes 分钟" else "$minutes 分钟"
}
