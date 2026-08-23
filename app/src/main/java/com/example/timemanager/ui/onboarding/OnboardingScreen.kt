package com.example.timemanager.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.example.timemanager.ui.permissions.PermissionCards
import com.example.timemanager.ui.permissions.PermissionUiState
import com.example.timemanager.ui.theme.TimeManagerSpacing

@Composable
fun OnboardingScreen(
    permissionState: PermissionUiState,
    onRequestUsageAccess: () -> Unit,
    onRequestNotification: () -> Unit,
    onRequestOverlay: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = step > 0) { step = 0 }

    if (step == 0) {
        WelcomeStep(
            onStart = { step = 1 },
            modifier = modifier,
        )
    } else {
        PermissionStep(
            state = permissionState,
            onBack = { step = 0 },
            onRequestUsageAccess = onRequestUsageAccess,
            onRequestNotification = onRequestNotification,
            onRequestOverlay = onRequestOverlay,
            onComplete = onComplete,
            modifier = modifier,
        )
    }
}

@Composable
private fun WelcomeStep(
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(TimeManagerSpacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "TimeManager",
            style = MaterialTheme.typography.displaySmall,
        )
        Text(
            text = "管理你的应用使用时间，\n减少无意识刷手机。",
            modifier = Modifier.padding(
                top = TimeManagerSpacing.medium,
                bottom = TimeManagerSpacing.extraLarge,
            ),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("开始设置")
        }
    }
}

@Composable
private fun PermissionStep(
    state: PermissionUiState,
    onBack: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onRequestNotification: () -> Unit,
    onRequestOverlay: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(TimeManagerSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.compact),
    ) {
        item {
            Text(
                text = "完成必要设置",
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        item {
            Text(
                text = "这些权限用于统计使用时间、显示提醒和冷静页面。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            PermissionCards(
                state = state,
                onRequestUsageAccess = onRequestUsageAccess,
                onRequestNotification = onRequestNotification,
                onRequestOverlay = onRequestOverlay,
            )
        }
        item {
            Button(
                onClick = onComplete,
                enabled = state.allRequiredGranted,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("开始使用 TimeManager")
            }
        }
        item {
            TextButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("返回")
            }
        }
    }
}
