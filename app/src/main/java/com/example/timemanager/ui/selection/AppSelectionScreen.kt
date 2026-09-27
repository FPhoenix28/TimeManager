package com.example.timemanager.ui.selection

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.example.timemanager.R
import com.example.timemanager.data.model.TrackedApp
import com.example.timemanager.ui.components.AppIcon
import com.example.timemanager.ui.components.AppSearchField
import com.example.timemanager.ui.components.FullScreenError
import com.example.timemanager.ui.components.FullScreenLoading
import com.example.timemanager.ui.components.matchesAppSearch
import com.example.timemanager.ui.theme.TimeManagerSpacing

@Composable
fun AppSelectionScreen(
    viewModel: AppSelectionViewModel,
    onBack: () -> Unit,
    onSelectApp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    when (val state = uiState) {
        AppSelectionUiState.Loading -> FullScreenLoading(modifier)
        is AppSelectionUiState.Success -> AppSelectionList(
            apps = state.apps,
            onBack = onBack,
            onSelectApp = onSelectApp,
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
    onSelectApp: (String) -> Unit,
    modifier: Modifier,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredApps = remember(apps, searchQuery) {
        apps.filter { matchesAppSearch(it.appName, it.packageName, searchQuery) }
    }
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
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = "返回",
                    )
                }
                Column {
                    Text("添加管理应用", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "选择后设置每日额度",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item("search") {
            AppSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                modifier = Modifier.padding(bottom = TimeManagerSpacing.small),
            )
        }

        if (filteredApps.isEmpty() && searchQuery.isNotBlank()) {
            item("noSearchResults") {
                Text(
                    "未找到相关应用",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = TimeManagerSpacing.extraLarge),
                )
            }
        } else if (apps.isEmpty()) {
            item("empty") {
                Text(
                    "没有找到可选择的用户应用",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = TimeManagerSpacing.extraLarge),
                )
            }
        } else {
            items(items = filteredApps, key = TrackedApp::packageName) { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectApp(app.packageName) }
                        .padding(vertical = TimeManagerSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(bitmap = app.appIcon, appName = app.appName)
                    Spacer(Modifier.size(TimeManagerSpacing.medium))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.appName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "选择  ›",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = TimeManagerSpacing.extraLarge * 3),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    FullScreenError(
        title = "加载目标应用失败",
        message = message,
        onRetry = onRetry,
        onBack = onBack,
        modifier = modifier,
    )
}
