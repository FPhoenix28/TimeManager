package com.example.timemanager.ui.focus

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.example.timemanager.ui.components.AppIcon
import com.example.timemanager.ui.theme.TimeManagerSpacing

@Composable
fun FocusSetupScreen(
    viewModel: FocusViewModel,
    accessibilityEnabled: Boolean,
    onBack: () -> Unit,
    onStarted: () -> Unit,
    onRequestAccessibility: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDisclosure by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(TimeManagerSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.medium),
    ) {
        item("header") {
            Column(verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small)) {
                TextButton(onClick = onBack) { Text("← 返回") }
                Text("专注模式", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "在一段时间内，暂时远离你选择的应用。其他应用仍可正常使用。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item("duration") {
            Column(verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small)) {
                Text("专注时长", style = MaterialTheme.typography.titleMedium)
                listOf(listOf(15, 25, 45), listOf(60, 90)).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small)) {
                        row.forEach { minutes ->
                            val onClick = { viewModel.selectPreset(minutes) }
                            if (state.durationMinutes == minutes && state.customDurationInput.isEmpty()) {
                                Button(onClick = onClick) { Text("$minutes 分钟") }
                            } else {
                                OutlinedButton(onClick = onClick) { Text("$minutes 分钟") }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = state.customDurationInput,
                    onValueChange = viewModel::updateCustomDuration,
                    label = { Text("自定义分钟数（1–720）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item("selectionHeader") {
            Column(verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small)) {
                Text("暂时屏蔽的应用", style = MaterialTheme.typography.titleMedium)
                if (state.managedPackages.isNotEmpty()) {
                    TextButton(onClick = viewModel::selectAllManaged) { Text("全选已管理应用") }
                }
                if (!accessibilityEnabled) {
                    Text(
                        "无障碍服务未开启；开始专注前需要授权。",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        if (state.isLoadingApps) {
            item("loading") { Text("正在读取应用…") }
        } else {
            items(state.apps, key = { it.packageName }) { app ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(TimeManagerSpacing.compact),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app.appIcon, app.appName)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.appName, style = MaterialTheme.typography.bodyLarge)
                        if (app.packageName in state.managedPackages) {
                            Text(
                                "已管理应用",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Checkbox(
                        checked = app.packageName in state.selectedPackages,
                        onCheckedChange = { viewModel.togglePackage(app.packageName) },
                    )
                }
                HorizontalDivider()
            }
        }
        item("start") {
            Button(
                onClick = {
                    if (accessibilityEnabled) viewModel.startFocus(onStarted)
                    else showDisclosure = true
                },
                enabled = state.canStart,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("开始专注") }
        }
    }

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text("开启专注所需的权限") },
            text = {
                Text(
                    "TimeManager 的无障碍服务会接收应用窗口切换事件，识别当前打开的应用；" +
                        "专注期间遇到你选中的应用时，会将你带回桌面并显示提示。" +
                        "它不读取页面内容，也不上传这些事件。请在系统设置中手动开启。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDisclosure = false
                    onRequestAccessibility()
                }) { Text("了解，前往设置") }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosure = false }) { Text("取消") }
            },
        )
    }
}
