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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun LimitScreen(
    viewModel: LimitViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    BackHandler(onBack = onBack)

    when (val state = uiState) {
        LimitUiState.Loading -> LoadingContent(modifier)
        is LimitUiState.Success -> LimitList(
            apps = state.apps,
            onBack = onBack,
            onLimitChange = viewModel::updateLimitInput,
            onSave = viewModel::saveLimit,
            modifier = modifier,
        )
        is LimitUiState.Error -> ErrorContent(
            message = state.message,
            onRetry = viewModel::loadApps,
            onBack = onBack,
            modifier = modifier,
        )
    }
}

@Composable
private fun LimitList(
    apps: List<LimitAppItemUiState>,
    onBack: () -> Unit,
    onLimitChange: (String, String) -> Unit,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "limit_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) {
                    Text("返回")
                }
                Column {
                    Text(
                        text = "每日使用额度",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "为每个应用设置分钟数",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (apps.isEmpty()) {
            item(key = "limit_empty") {
                Text("没有找到可设置额度的用户应用")
            }
        } else {
            items(
                items = apps,
                key = LimitAppItemUiState::packageName,
            ) { app ->
                LimitAppCard(
                    app = app,
                    onLimitChange = { input -> onLimitChange(app.packageName, input) },
                    onSave = { onSave(app.packageName) },
                )
            }
        }
    }
}

@Composable
private fun LimitAppCard(
    app: LimitAppItemUiState,
    onLimitChange: (String) -> Unit,
    onSave: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(
                    appIcon = app.appIcon,
                    appName = app.appName,
                )
                Spacer(modifier = Modifier.size(12.dp))
                Column {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = app.limitInput,
                    onValueChange = onLimitChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("每日限制") },
                    suffix = { Text("分钟") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = app.errorMessage != null,
                )
                Button(
                    onClick = onSave,
                    enabled = !app.isSaving && app.limitInput.isNotBlank(),
                ) {
                    Text(
                        when {
                            app.isSaving -> "保存中"
                            app.isSaved -> "已保存"
                            else -> "保存"
                        },
                    )
                }
            }

            app.errorMessage?.let { errorMessage ->
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun AppIcon(
    appIcon: Bitmap?,
    appName: String,
) {
    val imageBitmap: ImageBitmap? = remember(appIcon) { appIcon?.asImageBitmap() }
    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = "$appName 图标",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp)),
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
private fun LoadingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("加载额度设置失败")
        Text(message)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack) { Text("返回") }
            Button(onClick = onRetry) { Text("重试") }
        }
    }
}
