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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.timemanager.data.preferences.AppLimitPreferences
import com.example.timemanager.data.local.AppDatabase
import com.example.timemanager.data.preferences.RealtimeUsagePreferences
import com.example.timemanager.data.preferences.InterventionPreferences
import com.example.timemanager.data.preferences.OnboardingPreferences
import com.example.timemanager.data.preferences.NotificationStatePreferences
import com.example.timemanager.data.preferences.SessionSettingsPreferences
import com.example.timemanager.data.preferences.TrackedAppPreferences
import com.example.timemanager.data.repository.InstalledAppInfoProvider
import com.example.timemanager.data.repository.InstalledAppRepository
import com.example.timemanager.data.repository.LimitRepository
import com.example.timemanager.data.repository.ManagedAppRepository
import com.example.timemanager.data.repository.NotificationStateRepository
import com.example.timemanager.data.repository.SessionSettingsRepository
import com.example.timemanager.data.repository.InterventionRepository
import com.example.timemanager.data.repository.TrackedAppRepository
import com.example.timemanager.data.repository.UsageStatsReader
import com.example.timemanager.data.repository.UsageRepository
import com.example.timemanager.data.repository.UsageTimeRepository
import com.example.timemanager.data.repository.UsageHistoryRepository
import com.example.timemanager.ui.theme.TimeManagerTheme
import com.example.timemanager.ui.home.HomeScreen
import com.example.timemanager.ui.home.HomeViewModel
import com.example.timemanager.ui.detail.ManagedAppDetailScreen
import com.example.timemanager.ui.detail.ManagedAppDetailViewModel
import com.example.timemanager.ui.limit.LimitScreen
import com.example.timemanager.ui.limit.LimitViewModel
import com.example.timemanager.ui.onboarding.OnboardingScreen
import com.example.timemanager.ui.permissions.PermissionSettingsScreen
import com.example.timemanager.ui.permissions.PermissionUiState
import com.example.timemanager.ui.selection.AppSelectionScreen
import com.example.timemanager.ui.selection.AppSelectionViewModel
import com.example.timemanager.ui.statistics.StatisticsScreen
import com.example.timemanager.ui.statistics.StatisticsViewModel
import com.example.timemanager.ui.statistics.AppHistoryScreen
import com.example.timemanager.ui.statistics.AppHistoryViewModel
import com.example.timemanager.utils.UsagePermissionManager
import com.example.timemanager.utils.OverlayPermissionManager
import com.example.timemanager.notifications.NotificationHelper
import com.example.timemanager.service.UsageMonitorService
import com.example.timemanager.worker.UsageLimitWorkScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var hasUsageAccess by mutableStateOf(false)
    private var hasNotificationPermission by mutableStateOf(false)
    private var hasOverlayPermission by mutableStateOf(false)
    private var onboardingCompleted by mutableStateOf<Boolean?>(null)
    private var destination by mutableStateOf(AppDestination.HOME)
    private lateinit var onboardingPreferences: OnboardingPreferences
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var limitViewModel: LimitViewModel
    private lateinit var appSelectionViewModel: AppSelectionViewModel
    private lateinit var managedAppDetailViewModel: ManagedAppDetailViewModel
    private lateinit var statisticsViewModel: StatisticsViewModel
    private lateinit var appHistoryViewModel: AppHistoryViewModel
    private var detailPackageName: String? = null
    private var appHistoryBackDestination = AppDestination.HOME
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        Log.d(TAG, "notificationPermissionGranted=$isGranted")
        refreshPermissionStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper(applicationContext).createNotificationChannel()
        UsageLimitWorkScheduler.cancelLegacySchedule(applicationContext)
        onboardingPreferences = OnboardingPreferences(applicationContext)
        refreshPermissionStatus()
        val limitRepository = LimitRepository(
            AppLimitPreferences(applicationContext),
        )
        val interventionRepository = InterventionRepository(
            InterventionPreferences(applicationContext),
        )
        val installedAppRepository = InstalledAppRepository(
            InstalledAppInfoProvider(packageManager),
        )
        val trackedAppRepository = TrackedAppRepository(
            TrackedAppPreferences(applicationContext),
        )
        val notificationStateRepository = NotificationStateRepository(
            NotificationStatePreferences(applicationContext),
        )
        val sessionSettingsRepository = SessionSettingsRepository(
            SessionSettingsPreferences(applicationContext),
        )
        val managedAppRepository = ManagedAppRepository(
            trackedAppRepository = trackedAppRepository,
            limitRepository = limitRepository,
            interventionRepository = interventionRepository,
            notificationStateRepository = notificationStateRepository,
            sessionSettingsRepository = sessionSettingsRepository,
        )
        val usageHistoryRepository = UsageHistoryRepository(
            AppDatabase.getInstance(applicationContext).usageHistoryDao(),
        )
        val usageTimeRepository = UsageTimeRepository(
            usageRepository = UsageRepository(UsageStatsReader(applicationContext)),
            preferences = RealtimeUsagePreferences(applicationContext),
            historyRepository = usageHistoryRepository,
        )
        homeViewModel = ViewModelProvider(
            this,
            HomeViewModel.Factory(
                usageTimeRepository = usageTimeRepository,
                limitRepository = limitRepository,
                interventionRepository = interventionRepository,
                usageHistoryRepository = usageHistoryRepository,
                trackedAppRepository = trackedAppRepository,
            ),
        )[HomeViewModel::class.java]
        limitViewModel = ViewModelProvider(
            this,
            LimitViewModel.Factory(
                installedAppRepository = installedAppRepository,
                trackedAppRepository = trackedAppRepository,
                limitRepository = limitRepository,
                usageTimeRepository = usageTimeRepository,
                managedAppRepository = managedAppRepository,
            ),
        )[LimitViewModel::class.java]
        appSelectionViewModel = ViewModelProvider(
            this,
            AppSelectionViewModel.Factory(
                installedAppRepository = installedAppRepository,
                trackedAppRepository = trackedAppRepository,
            ),
        )[AppSelectionViewModel::class.java]
        managedAppDetailViewModel = ViewModelProvider(
            this,
            ManagedAppDetailViewModel.Factory(
                installedAppRepository = installedAppRepository,
                usageTimeRepository = usageTimeRepository,
                limitRepository = limitRepository,
                trackedAppRepository = trackedAppRepository,
                interventionRepository = interventionRepository,
                managedAppRepository = managedAppRepository,
                sessionSettingsRepository = sessionSettingsRepository,
            ),
        )[ManagedAppDetailViewModel::class.java]
        statisticsViewModel = ViewModelProvider(
            this,
            StatisticsViewModel.Factory(
                historyRepository = usageHistoryRepository,
                usageTimeRepository = usageTimeRepository,
                trackedAppRepository = trackedAppRepository,
                installedAppRepository = installedAppRepository,
            ),
        )[StatisticsViewModel::class.java]
        appHistoryViewModel = ViewModelProvider(
            this,
            AppHistoryViewModel.Factory(
                historyRepository = usageHistoryRepository,
                usageTimeRepository = usageTimeRepository,
                installedAppRepository = installedAppRepository,
            ),
        )[AppHistoryViewModel::class.java]
        lifecycleScope.launch {
            val completed = onboardingPreferences.observeCompleted().first()
            onboardingCompleted = completed
            if (completed && hasUsageAccess) {
                startUsageMonitorService()
                homeViewModel.loadTodayUsage()
            }
        }
        setContent {
            TimeManagerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val permissionState = PermissionUiState(
                        usageAccessGranted = hasUsageAccess,
                        notificationGranted = hasNotificationPermission,
                        overlayGranted = hasOverlayPermission,
                    )
                    when (onboardingCompleted) {
                        null -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                        false -> OnboardingScreen(
                            permissionState = permissionState,
                            onRequestUsageAccess = ::openUsageAccessSettings,
                            onRequestNotification = ::requestNotificationPermission,
                            onRequestOverlay = ::openOverlaySettings,
                            onComplete = ::completeOnboarding,
                            modifier = Modifier.padding(innerPadding),
                        )
                        true -> {
                        when (destination) {
                            AppDestination.HOME -> HomeScreen(
                                viewModel = homeViewModel,
                                hasRequiredPermissions = permissionState.allRequiredGranted,
                                onOpenPermissionSettings = {
                                    destination = AppDestination.PERMISSIONS
                                },
                                onOpenManagement = {
                                    limitViewModel.loadApps()
                                    destination = AppDestination.LIMITS
                                },
                                onOpenAppDetail = { packageName ->
                                    detailPackageName = packageName
                                    managedAppDetailViewModel.open(packageName)
                                    destination = AppDestination.MANAGED_APP_DETAIL
                                },
                                onOpenStatistics = {
                                    statisticsViewModel.refresh()
                                    destination = AppDestination.STATISTICS
                                },
                                modifier = Modifier.padding(innerPadding),
                            )
                            AppDestination.LIMITS -> LimitScreen(
                                viewModel = limitViewModel,
                                onBack = { destination = AppDestination.HOME },
                                onAddApp = {
                                    appSelectionViewModel.loadApps()
                                    destination = AppDestination.APP_SELECTION
                                },
                                modifier = Modifier.padding(innerPadding),
                            )
                            AppDestination.APP_SELECTION -> AppSelectionScreen(
                                viewModel = appSelectionViewModel,
                                onBack = { destination = AppDestination.LIMITS },
                                onSelectApp = { packageName ->
                                    limitViewModel.beginAdd(packageName)
                                    destination = AppDestination.LIMITS
                                },
                                modifier = Modifier.padding(innerPadding),
                            )
                            AppDestination.PERMISSIONS -> PermissionSettingsScreen(
                                state = permissionState,
                                onBack = { destination = AppDestination.HOME },
                                onRequestUsageAccess = ::openUsageAccessSettings,
                                onRequestNotification = ::requestNotificationPermission,
                                onRequestOverlay = ::openOverlaySettings,
                                modifier = Modifier.padding(innerPadding),
                            )
                            AppDestination.MANAGED_APP_DETAIL -> ManagedAppDetailScreen(
                                viewModel = managedAppDetailViewModel,
                                onBack = {
                                    destination = AppDestination.HOME
                                    homeViewModel.loadTodayUsage()
                                },
                                onRemoved = {
                                    detailPackageName = null
                                    destination = AppDestination.HOME
                                    homeViewModel.loadTodayUsage()
                                },
                                onOpenHistory = { packageName ->
                                    appHistoryBackDestination = AppDestination.MANAGED_APP_DETAIL
                                    appHistoryViewModel.open(packageName)
                                    destination = AppDestination.APP_HISTORY
                                },
                                modifier = Modifier.padding(innerPadding),
                            )
                            AppDestination.STATISTICS -> StatisticsScreen(
                                viewModel = statisticsViewModel,
                                onBack = { destination = AppDestination.HOME },
                                onOpenAppHistory = { packageName ->
                                    appHistoryBackDestination = AppDestination.STATISTICS
                                    appHistoryViewModel.open(packageName)
                                    destination = AppDestination.APP_HISTORY
                                },
                                modifier = Modifier.padding(innerPadding),
                            )
                            AppDestination.APP_HISTORY -> AppHistoryScreen(
                                viewModel = appHistoryViewModel,
                                onBack = { destination = appHistoryBackDestination },
                                modifier = Modifier.padding(innerPadding),
                            )
                        }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
        if (onboardingCompleted == true && hasUsageAccess) {
            startUsageMonitorService()
            homeViewModel.loadTodayUsage()
            if (destination == AppDestination.MANAGED_APP_DETAIL) {
                managedAppDetailViewModel.refresh()
            }
            if (destination == AppDestination.STATISTICS) statisticsViewModel.refresh()
            if (destination == AppDestination.APP_HISTORY) appHistoryViewModel.refresh()
        }
    }

    private fun openUsageAccessSettings() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun openOverlaySettings() {
        startActivity(OverlayPermissionManager.settingsIntent(this))
    }

    private fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasNotificationPermission
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            refreshPermissionStatus()
        }
    }

    private fun refreshPermissionStatus() {
        hasUsageAccess = UsagePermissionManager.hasUsageAccess(this)
        hasNotificationPermission =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        hasOverlayPermission = OverlayPermissionManager.canDrawOverlays(this)
        Log.d(TAG, "packageName=$packageName")
        Log.d(TAG, "hasUsageAccess=$hasUsageAccess")
        Log.d(TAG, "hasNotificationPermission=$hasNotificationPermission")
        Log.d(TAG, "hasOverlayPermission=$hasOverlayPermission")
    }

    private fun completeOnboarding() {
        if (!hasUsageAccess || !hasNotificationPermission || !hasOverlayPermission) return
        lifecycleScope.launch {
            onboardingPreferences.setCompleted(true)
            onboardingCompleted = true
            destination = AppDestination.HOME
            startUsageMonitorService()
            homeViewModel.loadTodayUsage()
        }
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
    APP_SELECTION,
    PERMISSIONS,
    MANAGED_APP_DETAIL,
    STATISTICS,
    APP_HISTORY,
}
