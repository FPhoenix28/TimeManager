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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timemanager.data.preferences.AppLimitPreferences
import com.example.timemanager.data.local.AppDatabase
import com.example.timemanager.data.preferences.RealtimeUsagePreferences
import com.example.timemanager.data.preferences.InterventionPreferences
import com.example.timemanager.data.preferences.OnboardingPreferences
import com.example.timemanager.data.preferences.NotificationStatePreferences
import com.example.timemanager.data.preferences.SessionSettingsPreferences
import com.example.timemanager.data.preferences.TrackedAppPreferences
import com.example.timemanager.data.preferences.FocusModePreferences
import com.example.timemanager.data.repository.InstalledAppInfoProvider
import com.example.timemanager.data.repository.InstalledAppRepository
import com.example.timemanager.data.repository.LimitRepository
import com.example.timemanager.data.repository.ManagedAppRepository
import com.example.timemanager.data.repository.NotificationStateRepository
import com.example.timemanager.data.repository.SessionSettingsRepository
import com.example.timemanager.data.repository.InterventionRepository
import com.example.timemanager.data.repository.TrackedAppRepository
import com.example.timemanager.data.repository.FocusModeRepository
import com.example.timemanager.data.repository.UsageStatsReader
import com.example.timemanager.data.repository.UsageRepository
import com.example.timemanager.data.repository.UsageTimeRepository
import com.example.timemanager.data.repository.UsageHistoryRepository
import com.example.timemanager.domain.history.UsageHistoryBackfillManager
import com.example.timemanager.ui.theme.TimeManagerTheme
import com.example.timemanager.ui.home.HomeScreen
import com.example.timemanager.ui.home.HomeViewModel
import com.example.timemanager.ui.detail.ManagedAppDetailScreen
import com.example.timemanager.ui.detail.ManagedAppDetailViewModel
import com.example.timemanager.ui.limit.LimitScreen
import com.example.timemanager.ui.limit.LimitViewModel
import com.example.timemanager.ui.onboarding.OnboardingScreen
import com.example.timemanager.ui.permissions.PermissionSettingsScreen
import com.example.timemanager.permissions.PermissionHealthManager
import com.example.timemanager.permissions.PermissionHealthNoticeTracker
import com.example.timemanager.ui.selection.AppSelectionScreen
import com.example.timemanager.ui.selection.AppSelectionViewModel
import com.example.timemanager.ui.statistics.StatisticsScreen
import com.example.timemanager.ui.statistics.StatisticsViewModel
import com.example.timemanager.ui.statistics.AppHistoryScreen
import com.example.timemanager.ui.statistics.AppHistoryViewModel
import com.example.timemanager.ui.components.PrimaryDestination
import com.example.timemanager.ui.components.TimeManagerNavigationBar
import com.example.timemanager.ui.focus.FocusViewModel
import com.example.timemanager.ui.focus.FocusSetupScreen
import com.example.timemanager.ui.focus.FocusActiveScreen
import com.example.timemanager.ui.focus.DeepFocusViewModel
import com.example.timemanager.ui.focus.DeepFocusSetupScreen
import com.example.timemanager.ui.focus.DeepFocusActiveScreen
import com.example.timemanager.utils.OverlayPermissionManager
import com.example.timemanager.utils.AccessibilityPermissionManager
import com.example.timemanager.notifications.NotificationHelper
import com.example.timemanager.notifications.DeepFocusNotificationHelper
import com.example.timemanager.service.UsageMonitorService
import com.example.timemanager.domain.todo.TodoRuntime
import com.example.timemanager.notifications.TodoNotificationHelper
import com.example.timemanager.ui.todo.TodoScreen
import com.example.timemanager.ui.todo.TodoViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val permissionHealthManager by lazy { PermissionHealthManager.get(applicationContext) }
    private val permissionNotices = PermissionHealthNoticeTracker.process
    private val hasUsageAccess get() = permissionHealthManager.state.value.usageAccessGranted
    private val hasNotificationPermission get() = permissionHealthManager.state.value.notificationGranted
    private val hasOverlayPermission get() = permissionHealthManager.state.value.overlayGranted
    private val hasAccessibilityPermission get() = permissionHealthManager.state.value.accessibilityGranted
    private var onboardingCompleted by mutableStateOf<Boolean?>(null)
    private var destination by mutableStateOf(AppDestination.HOME)
    private lateinit var onboardingPreferences: OnboardingPreferences
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var limitViewModel: LimitViewModel
    private lateinit var appSelectionViewModel: AppSelectionViewModel
    private lateinit var managedAppDetailViewModel: ManagedAppDetailViewModel
    private lateinit var statisticsViewModel: StatisticsViewModel
    private lateinit var appHistoryViewModel: AppHistoryViewModel
    private lateinit var focusViewModel: FocusViewModel
    private lateinit var deepFocusViewModel: DeepFocusViewModel
    private lateinit var todoViewModel: TodoViewModel
    private var todosBackDestination = AppDestination.HOME
    private lateinit var usageHistoryBackfillManager: UsageHistoryBackfillManager
    private var detailPackageName: String? = null
    private var appHistoryPackageName: String? = null
    private var appHistoryBackDestination = AppDestination.HOME
    private var permissionsBackDestination = AppDestination.HOME
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        Log.d(TAG, "notificationPermissionGranted=$isGranted")
        refreshPermissionStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreNavigationState(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper(applicationContext).createNotificationChannel()
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
        val focusModeRepository = FocusModeRepository(
            FocusModePreferences(applicationContext),
            packageName,
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
        val usageStatsReader = UsageStatsReader(applicationContext)
        val usageTimeRepository = UsageTimeRepository(
            usageRepository = UsageRepository(usageStatsReader),
            preferences = RealtimeUsagePreferences(applicationContext),
            historyRepository = usageHistoryRepository,
        )
        usageHistoryBackfillManager = UsageHistoryBackfillManager(
            usageStatsReader = usageStatsReader,
            historyRepository = usageHistoryRepository,
            trackedAppRepository = trackedAppRepository,
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
                historyRepository = usageHistoryRepository,
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
        focusViewModel = ViewModelProvider(
            this,
            FocusViewModel.Factory(
                focusModeRepository,
                installedAppRepository,
                trackedAppRepository,
                packageName,
            ),
        )[FocusViewModel::class.java]
        deepFocusViewModel = ViewModelProvider(
            this,
            DeepFocusViewModel.Factory(focusModeRepository),
        )[DeepFocusViewModel::class.java]
        todoViewModel = ViewModelProvider(this,
            TodoViewModel.Factory(TodoRuntime.get(applicationContext).repository))[TodoViewModel::class.java]
        handleDeepFocusIntent(intent)
        handleTodoIntent(intent)
        restoreDestinationContent()
        lifecycleScope.launch {
            val completed = onboardingPreferences.observeCompleted().first()
            onboardingCompleted = completed
            if (completed && hasUsageAccess) {
                startUsageMonitorService()
                triggerHistoryBackfill()
                // A cold notification entry still needs a ready Home when Todo closes.
                if (destination == AppDestination.HOME || destination == AppDestination.TODOS) {
                    homeViewModel.loadTodayUsage()
                }
            }
        }
        setContent {
            TimeManagerTheme {
                val focusState by focusViewModel.focusState.collectAsStateWithLifecycle()
                val permissionState by permissionHealthManager.state.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }
                LaunchedEffect(permissionState, destination, onboardingCompleted) {
                    permissionNotices.update(permissionState)
                    if (onboardingCompleted == true && destination == AppDestination.HOME &&
                        permissionNotices.consumePending()
                    ) {
                        if (snackbarHostState.showSnackbar(
                                message = "部分干预功能未启用",
                                actionLabel = "查看",
                                duration = SnackbarDuration.Short,
                            ) == SnackbarResult.ActionPerformed
                        ) {
                            permissionsBackDestination = AppDestination.HOME
                            destination = AppDestination.PERMISSIONS
                        }
                    }
                }
                val primaryDestination = destination.toPrimaryDestination()
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (onboardingCompleted == true && primaryDestination != null) {
                            TimeManagerNavigationBar(
                                selected = primaryDestination,
                                onSelect = { selected ->
                                    when (selected) {
                                        PrimaryDestination.HOME -> {
                                            destination = AppDestination.HOME
                                        }
                                        PrimaryDestination.STATISTICS -> {
                                            triggerHistoryBackfill()
                                            statisticsViewModel.loadIfNeeded()
                                            destination = AppDestination.STATISTICS
                                        }
                                        PrimaryDestination.MANAGEMENT -> {
                                            limitViewModel.loadIfNeeded()
                                            destination = AppDestination.LIMITS
                                        }
                                    }
                                },
                            )
                        }
                    },
                ) { innerPadding ->
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
                        true -> AnimatedContent(
                            targetState = destination,
                            transitionSpec = {
                                fadeIn(
                                    animationSpec = tween(DESTINATION_FADE_IN_MILLIS),
                                ) togetherWith fadeOut(
                                    animationSpec = tween(DESTINATION_FADE_OUT_MILLIS),
                                ) using SizeTransform(
                                    clip = false,
                                    sizeAnimationSpec = { _, _ -> snap() },
                                )
                            },
                            label = "TimeManagerDestinationFade",
                        ) { animatedDestination ->
                        when (animatedDestination) {
                            AppDestination.HOME -> HomeScreen(
                                viewModel = homeViewModel,
                                todoViewModel = todoViewModel,
                                onOpenTodos = {
                                    todosBackDestination = AppDestination.HOME
                                    destination = AppDestination.TODOS
                                },
                                permissionHealth = permissionState,
                                onOpenPermissionSettings = {
                                    permissionsBackDestination = AppDestination.HOME
                                    destination = AppDestination.PERMISSIONS
                                },
                                onOpenManagement = {
                                    limitViewModel.loadIfNeeded()
                                    destination = AppDestination.LIMITS
                                },
                                onOpenAppDetail = { packageName ->
                                    detailPackageName = packageName
                                    managedAppDetailViewModel.open(packageName)
                                    destination = AppDestination.MANAGED_APP_DETAIL
                                },
                                onOpenStatistics = {
                                    triggerHistoryBackfill()
                                    statisticsViewModel.loadIfNeeded()
                                    destination = AppDestination.STATISTICS
                                },
                                focusState = focusState,
                                onOpenFocus = {
                                    focusViewModel.loadAppsIfNeeded()
                                    destination = if (focusViewModel.focusState.value.isActive()) {
                                        AppDestination.FOCUS_ACTIVE
                                    } else {
                                        AppDestination.FOCUS_SETUP
                                    }
                                },
                                onOpenDeepFocus = {
                                    destination = if (
                                        focusViewModel.focusState.value.mode ==
                                        com.example.timemanager.data.model.FocusModeType.DEEP_FOCUS
                                    ) {
                                        AppDestination.DEEP_FOCUS_ACTIVE
                                    } else {
                                        AppDestination.DEEP_FOCUS_SETUP
                                    }
                                },
                                modifier = Modifier.padding(innerPadding),
                            )
                            AppDestination.TODOS -> TodoScreen(
                                viewModel = todoViewModel,
                                onBack = { destination = todosBackDestination },
                                modifier = Modifier.padding(innerPadding),
                            )
                            AppDestination.LIMITS -> LimitScreen(
                                viewModel = limitViewModel,
                                onBack = { destination = AppDestination.HOME },
                                showBack = false,
                                onAddApp = {
                                    appSelectionViewModel.loadIfNeeded()
                                    destination = AppDestination.APP_SELECTION
                                },
                                onOpenPermissions = {
                                    permissionsBackDestination = AppDestination.LIMITS
                                    destination = AppDestination.PERMISSIONS
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
                                onBack = { destination = permissionsBackDestination },
                                onRequestUsageAccess = ::openUsageAccessSettings,
                                onRequestNotification = ::requestNotificationPermission,
                                onRequestOverlay = ::openOverlaySettings,
                                onRequestAccessibility = ::openAccessibilitySettings,
                                modifier = Modifier.padding(innerPadding),
                            )
                            AppDestination.FOCUS_SETUP -> FocusSetupScreen(
                                viewModel = focusViewModel,
                                accessibilityEnabled = permissionState.accessibilityGranted,
                                onCheckAccessibility = { refreshPermissionStatus(); hasAccessibilityPermission },
                                onBack = { destination = AppDestination.HOME },
                                onStarted = { destination = AppDestination.FOCUS_ACTIVE },
                                onRequestAccessibility = ::openAccessibilitySettings,
                                modifier = Modifier.padding(innerPadding),
                            )
                            AppDestination.FOCUS_ACTIVE -> FocusActiveScreen(
                                viewModel = focusViewModel,
                                accessibilityEnabled = permissionState.accessibilityGranted,
                                onBack = { destination = AppDestination.HOME },
                                onStopped = { destination = AppDestination.HOME },
                                onRequestAccessibility = ::openAccessibilitySettings,
                                modifier = Modifier.padding(innerPadding),
                            )
                            AppDestination.DEEP_FOCUS_SETUP -> DeepFocusSetupScreen(
                                viewModel = deepFocusViewModel,
                                accessibilityEnabled = permissionState.accessibilityGranted,
                                overlayEnabled = permissionState.overlayGranted,
                                onCheckAccessibility = { refreshPermissionStatus(); hasAccessibilityPermission },
                                onBack = { destination = AppDestination.HOME },
                                onStarted = { destination = AppDestination.DEEP_FOCUS_ACTIVE },
                                onRequestAccessibility = ::openAccessibilitySettings,
                                onRequestOverlay = ::openOverlaySettings,
                                modifier = Modifier.padding(innerPadding),
                            )
                            AppDestination.DEEP_FOCUS_ACTIVE -> DeepFocusActiveScreen(
                                viewModel = deepFocusViewModel,
                                accessibilityEnabled = permissionState.accessibilityGranted,
                                overlayEnabled = permissionState.overlayGranted,
                                onEnded = { destination = AppDestination.HOME },
                                onRequestAccessibility = ::openAccessibilitySettings,
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
                                    appHistoryPackageName = packageName
                                    appHistoryViewModel.open(packageName)
                                    destination = AppDestination.APP_HISTORY
                                },
                                modifier = Modifier.padding(innerPadding),
                            )
                            AppDestination.STATISTICS -> StatisticsScreen(
                                viewModel = statisticsViewModel,
                                onBack = { destination = AppDestination.HOME },
                                showBack = false,
                                onOpenAppHistory = { packageName ->
                                    appHistoryBackDestination = AppDestination.STATISTICS
                                    appHistoryPackageName = packageName
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
            triggerHistoryBackfill()
            when (destination) {
                AppDestination.HOME -> homeViewModel.loadTodayUsage()
                AppDestination.MANAGED_APP_DETAIL -> managedAppDetailViewModel.refresh()
                AppDestination.STATISTICS -> statisticsViewModel.refresh()
                AppDestination.APP_HISTORY -> appHistoryViewModel.refresh()
                AppDestination.LIMITS,
                AppDestination.TODOS,
                AppDestination.APP_SELECTION,
                AppDestination.PERMISSIONS,
                AppDestination.FOCUS_SETUP,
                AppDestination.FOCUS_ACTIVE,
                AppDestination.DEEP_FOCUS_SETUP,
                AppDestination.DEEP_FOCUS_ACTIVE,
                -> Unit
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepFocusIntent(intent)
        handleTodoIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_DESTINATION, destination.name)
        outState.putString(STATE_PERMISSION_BACK_DESTINATION, permissionsBackDestination.name)
        outState.putString(STATE_HISTORY_BACK_DESTINATION, appHistoryBackDestination.name)
        outState.putString(STATE_DETAIL_PACKAGE, detailPackageName)
        outState.putString(STATE_HISTORY_PACKAGE, appHistoryPackageName)
        outState.putString(STATE_TODOS_BACK, todosBackDestination.name)
        super.onSaveInstanceState(outState)
    }

    private fun restoreNavigationState(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) return
        destination = savedInstanceState.getString(STATE_DESTINATION)
            .toAppDestinationOrDefault(AppDestination.HOME)
        permissionsBackDestination = savedInstanceState
            .getString(STATE_PERMISSION_BACK_DESTINATION)
            .toAppDestinationOrDefault(AppDestination.HOME)
        appHistoryBackDestination = savedInstanceState
            .getString(STATE_HISTORY_BACK_DESTINATION)
            .toAppDestinationOrDefault(AppDestination.HOME)
        detailPackageName = savedInstanceState.getString(STATE_DETAIL_PACKAGE)
        appHistoryPackageName = savedInstanceState.getString(STATE_HISTORY_PACKAGE)
        todosBackDestination = savedInstanceState.getString(STATE_TODOS_BACK)
            .toAppDestinationOrDefault(AppDestination.HOME)
    }

    private fun restoreDestinationContent() {
        when (destination) {
            AppDestination.LIMITS -> limitViewModel.loadIfNeeded()
            AppDestination.APP_SELECTION -> appSelectionViewModel.loadIfNeeded()
            AppDestination.MANAGED_APP_DETAIL -> detailPackageName?.let {
                managedAppDetailViewModel.open(it)
            } ?: run { destination = AppDestination.HOME }
            AppDestination.STATISTICS -> statisticsViewModel.refresh()
            AppDestination.FOCUS_SETUP,
            AppDestination.FOCUS_ACTIVE,
            -> focusViewModel.loadAppsIfNeeded()
            AppDestination.DEEP_FOCUS_SETUP,
            AppDestination.DEEP_FOCUS_ACTIVE,
            -> Unit
            AppDestination.APP_HISTORY -> appHistoryPackageName?.let {
                appHistoryViewModel.open(it)
            } ?: run { destination = appHistoryBackDestination }
            AppDestination.HOME,
            AppDestination.TODOS,
            AppDestination.PERMISSIONS,
            -> Unit
        }
    }

    private fun openUsageAccessSettings() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun openOverlaySettings() {
        startActivity(OverlayPermissionManager.settingsIntent(this))
    }

    private fun openAccessibilitySettings() {
        startActivity(AccessibilityPermissionManager.settingsIntent())
    }

    private fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    private fun refreshPermissionStatus() {
        val health = permissionHealthManager.refresh()
        permissionNotices.update(health)
        Log.d(TAG, "packageName=$packageName")
        Log.d(TAG, "hasUsageAccess=$hasUsageAccess")
        Log.d(TAG, "hasNotificationPermission=$hasNotificationPermission")
        Log.d(TAG, "hasOverlayPermission=$hasOverlayPermission")
        Log.d(TAG, "hasAccessibilityPermission=$hasAccessibilityPermission")
    }

    private fun completeOnboarding() {
        if (!hasUsageAccess || !hasNotificationPermission || !hasOverlayPermission) return
        lifecycleScope.launch {
            onboardingPreferences.setCompleted(true)
            onboardingCompleted = true
            destination = AppDestination.HOME
            startUsageMonitorService()
            triggerHistoryBackfill()
            homeViewModel.loadTodayUsage()
        }
    }

    private fun triggerHistoryBackfill() {
        lifecycleScope.launch {
            try {
                val result = usageHistoryBackfillManager.backfillIfNeeded()
                Log.d(TAG, "historyBackfill=$result")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "History backfill failed; existing history remains available", error)
            }
        }
    }

    private fun startUsageMonitorService() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, UsageMonitorService::class.java),
        )
    }

    private fun handleDeepFocusIntent(intent: Intent?) {
        if (intent?.action == DeepFocusNotificationHelper.ACTION_OPEN_DEEP_FOCUS) {
            destination = AppDestination.DEEP_FOCUS_ACTIVE
        }
    }

    private fun handleTodoIntent(intent: Intent?) {
        if (intent?.action == TodoNotificationHelper.ACTION_OPEN_TODOS && destination != AppDestination.TODOS) {
            todosBackDestination = if (destination.toPrimaryDestination() != null) destination else AppDestination.HOME
            destination = AppDestination.TODOS
        }
    }

    private companion object {
        const val TAG = "MainActivity"
        const val STATE_DESTINATION = "navigation.destination"
        const val STATE_PERMISSION_BACK_DESTINATION = "navigation.permissionBack"
        const val STATE_HISTORY_BACK_DESTINATION = "navigation.historyBack"
        const val STATE_DETAIL_PACKAGE = "navigation.detailPackage"
        const val STATE_HISTORY_PACKAGE = "navigation.historyPackage"
        const val STATE_TODOS_BACK = "navigation.todosBack"
        const val DESTINATION_FADE_IN_MILLIS = 180
        const val DESTINATION_FADE_OUT_MILLIS = 120
    }
}

private enum class AppDestination {
    HOME,
    TODOS,
    LIMITS,
    APP_SELECTION,
    PERMISSIONS,
    MANAGED_APP_DETAIL,
    STATISTICS,
    APP_HISTORY,
    FOCUS_SETUP,
    FOCUS_ACTIVE,
    DEEP_FOCUS_SETUP,
    DEEP_FOCUS_ACTIVE,
}

private fun AppDestination.toPrimaryDestination(): PrimaryDestination? = when (this) {
    AppDestination.HOME -> PrimaryDestination.HOME
    AppDestination.STATISTICS -> PrimaryDestination.STATISTICS
    AppDestination.LIMITS -> PrimaryDestination.MANAGEMENT
    else -> null
}

private fun String?.toAppDestinationOrDefault(default: AppDestination): AppDestination =
    this?.let { value -> AppDestination.entries.firstOrNull { it.name == value } } ?: default
