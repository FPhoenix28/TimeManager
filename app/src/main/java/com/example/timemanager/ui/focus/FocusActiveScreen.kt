package com.example.timemanager.ui.focus

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import com.example.timemanager.ui.theme.TimeManagerSpacing

@Composable
fun FocusActiveScreen(
    viewModel: FocusViewModel,
    accessibilityEnabled: Boolean,
    onBack: () -> Unit,
    onStopped: () -> Unit,
    onRequestAccessibility: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focus = state.focus
    var showEndConfirmation by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(TimeManagerSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.large),
    ) {
        item("header") {
            Column(verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.medium)) {
                TextButton(onClick = onBack) { Text("← 返回") }
                Text(
                    if (focus.isActive()) "专注进行中" else "专注已结束",
                    style = MaterialTheme.typography.headlineMedium,
                )
                if (focus.isActive()) {
                    FocusCountdown(focus.endTimeMillis, MaterialTheme.typography.displayMedium)
                    Text(
                        "已暂时屏蔽 ${focus.blockedPackages.size} 个应用",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        if (focus.isActive() && !accessibilityEnabled) {
            item("permissionWarning") {
                Column(verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small)) {
                    Text(
                        "无障碍服务已关闭，专注模式暂时无法阻止应用。",
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = onRequestAccessibility) { Text("重新开启") }
                }
            }
        }
        if (focus.isActive()) {
            items(focus.blockedPackages.toList(), key = { it }) { packageName ->
                val name = state.apps.firstOrNull { it.packageName == packageName }
                    ?.appName ?: packageName
                Text(name, style = MaterialTheme.typography.bodyLarge)
            }
            item("end") {
                OutlinedButton(
                    onClick = { showEndConfirmation = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("提前结束专注") }
            }
        } else {
            item("done") {
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回首页") }
            }
        }
    }

    if (showEndConfirmation) {
        val remainingMinutes = (focus.remainingMillis() + 59_999L) / 60_000L
        AlertDialog(
            onDismissRequest = { showEndConfirmation = false },
            title = { Text("结束本次专注？") },
            text = { Text("当前专注还有约 $remainingMinutes 分钟，确定提前结束吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showEndConfirmation = false
                    viewModel.stopFocus(onStopped)
                }) { Text("结束专注") }
            },
            dismissButton = {
                TextButton(onClick = { showEndConfirmation = false }) { Text("继续专注") }
            },
        )
    }
}
