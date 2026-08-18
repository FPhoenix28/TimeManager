package com.example.timemanager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.core.content.ContextCompat
import com.example.timemanager.data.preferences.AppLimitPreferences
import com.example.timemanager.data.preferences.RealtimeUsagePreferences
import com.example.timemanager.data.repository.InstalledAppInfoProvider
import com.example.timemanager.data.repository.InstalledAppRepository
import com.example.timemanager.data.repository.LimitRepository
import com.example.timemanager.data.repository.UsageStatsReader
import com.example.timemanager.data.repository.UsageRepository
import com.example.timemanager.data.repository.UsageTimeRepository
import com.example.timemanager.ui.theme.TimeManagerTheme
import com.example.timemanager.ui.home.HomeScreen
import com.example.timemanager.ui.home.HomeViewModel
import com.example.timemanager.ui.limit.LimitScreen
import com.example.timemanager.ui.limit.LimitViewModel
import com.example.timemanager.utils.UsagePermissionManager
import com.example.timemanager.notifications.NotificationHelper
import com.example.timemanager.service.UsageMonitorService
import com.example.timemanager.worker.UsageLimitWorkScheduler

class MainActivity : ComponentActivity() {
    private var hasUsageAccess by mutableStateOf(false)
    private var destination by mutableStateOf(AppDestination.HOME)
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var limitViewModel: LimitViewModel
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        Log.d(TAG, "notificationPermissionGranted=$isGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper(applicationContext).createNotificationChannel()
        UsageLimitWorkScheduler.schedule(applicationContext)
        requestNotificationPermissionIfNeeded()
        logUsageAccessStatus()
        val limitRepository = LimitRepository(
            AppLimitPreferences(applicationContext),
        )
        homeViewModel = ViewModelProvider(
            this,
            HomeViewModel.Factory(
                usageTimeRepository = UsageTimeRepository(
                    usageRepository = UsageRepository(UsageStatsReader(applicationContext)),
                    preferences = RealtimeUsagePreferences(applicationContext),
                ),
                limitRepository = limitRepository,
            ),
        )[HomeViewModel::class.java]
        limitViewModel = ViewModelProvider(
            this,
            LimitViewModel.Factory(
                installedAppRepository = InstalledAppRepository(
                    InstalledAppInfoProvider(packageManager),
                ),
                limitRepository = limitRepository,
            ),
        )[LimitViewModel::class.java]
        setContent {
            TimeManagerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (hasUsageAccess) {
                        when (destination) {
                            AppDestination.HOME -> HomeScreen(
                                viewModel = homeViewModel,
                                onOpenLimits = { destination = AppDestination.LIMITS },
                                modifier = Modifier.padding(innerPadding),
                            )
                            AppDestination.LIMITS -> LimitScreen(
                                viewModel = limitViewModel,
                                onBack = { destination = AppDestination.HOME },
                                modifier = Modifier.padding(innerPadding),
                            )
                        }
                    } else {
                        UsageAccessScreen(
                            hasUsageAccess = false,
                            onOpenUsageAccessSettings = ::openUsageAccessSettings,
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        logUsageAccessStatus()
        if (hasUsageAccess) {
            startUsageMonitorService()
            homeViewModel.loadTodayUsage()
        }
    }

    private fun openUsageAccessSettings() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun logUsageAccessStatus() {
        hasUsageAccess = UsagePermissionManager.hasUsageAccess(this)
        Log.d(TAG, "packageName=$packageName")
        Log.d(TAG, "hasUsageAccess=$hasUsageAccess")
    }

    private fun startUsageMonitorService() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, UsageMonitorService::class.java),
        )
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}

private enum class AppDestination {
    HOME,
    LIMITS,
}

@Composable
fun UsageAccessScreen(
    hasUsageAccess: Boolean,
    onOpenUsageAccessSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (hasUsageAccess) {
        Text(
            text = "已开启使用情况访问权限",
            modifier = modifier,
        )
    } else {
        Button(
            onClick = onOpenUsageAccessSettings,
            modifier = modifier,
        ) {
            Text("开启使用情况访问权限")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun UsageAccessScreenPreview() {
    TimeManagerTheme {
        UsageAccessScreen(
            hasUsageAccess = false,
            onOpenUsageAccessSettings = {},
        )
    }
}
