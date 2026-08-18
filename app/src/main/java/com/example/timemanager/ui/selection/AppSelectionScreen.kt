package com.example.timemanager.ui.selection

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.example.timemanager.data.model.TrackedApp

@Composable
fun AppSelectionScreen(
    viewModel: AppSelectionViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    BackHandler(onBack = onBack)

    when (val state = uiState) {
        AppSelectionUiState.Loading -> LoadingContent(modifier)
        is AppSelectionUiState.Success -> AppSelectionList(
            apps = state.apps,
            onBack = onBack,
            onTrackedChange = viewModel::setTracked,
            modifier = modifier,
        )
        is AppSelectionUiState.Error -> ErrorContent(
            message = state.message,
            onBack = onBack,
            onRetry = viewModel::loadApps,
            modifier = modifier,
        )
    }
}

@Composable
private fun AppSelectionList(
    apps: List<TrackedApp>,
    onBack: () -> Unit,
    onTrackedChange: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "selection_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("返回") }
                Column {
                    Text("目标应用", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "仅实时统计已勾选的应用",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (apps.isEmpty()) {
            item(key = "selection_empty") {
                Text("没有找到可选择的用户应用")
            }
        } else {
            items(
                items = apps,
                key = TrackedApp::packageName,
            ) { app ->
                TrackedAppCard(
                    app = app,
                    onClick = { onTrackedChange(app.packageName, !app.isTracked) },
                )
            }
        }
    }
}

@Composable
private fun TrackedAppCard(app: TrackedApp, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackedAppIcon(app.appIcon, app.appName)
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Checkbox(
                checked = app.isTracked,
                onCheckedChange = null,
            )
        }
    }
}

@Composable
private fun TrackedAppIcon(appIcon: Bitmap?, appName: String) {
    val imageBitmap = remember(appIcon) { appIcon?.asImageBitmap() }
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
private fun LoadingContent(modifier: Modifier) {
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
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("加载目标应用失败")
        Text(message)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack) { Text("返回") }
            Button(onClick = onRetry) { Text("重试") }
        }
    }
}
