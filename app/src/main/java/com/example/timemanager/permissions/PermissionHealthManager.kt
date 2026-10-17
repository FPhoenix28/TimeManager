package com.example.timemanager.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.timemanager.utils.AccessibilityPermissionManager
import com.example.timemanager.utils.OverlayPermissionManager
import com.example.timemanager.utils.UsagePermissionManager
import com.example.timemanager.config.InterventionRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Cached permission detection only. Refresh at lifecycle/feature boundaries, never per tick. */
class PermissionHealthManager private constructor(context: Context) {
    private val context = context.applicationContext
    private val mutableState = MutableStateFlow(PermissionHealthState(false, false, false))
    val state: StateFlow<PermissionHealthState> = mutableState.asStateFlow()

    init {
        // Application-scoped state only; no UI or Service reference is retained.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            InterventionRuntime.coordinator.accessibilityConnection.collect { refresh() }
        }
    }

    fun refresh(): PermissionHealthState {
        val notificationRuntimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return PermissionHealthState(
            usageAccessGranted = UsagePermissionManager.hasUsageAccess(context),
            notificationGranted = notificationRuntimeGranted &&
                NotificationManagerCompat.from(context).areNotificationsEnabled(),
            overlayGranted = OverlayPermissionManager.canDrawOverlays(context),
            accessibilityGranted = AccessibilityPermissionManager.isEnabled(context),
            accessibilityConnected = InterventionRuntime.coordinator.accessibilityConnected,
        ).also { mutableState.value = it }
    }

    companion object {
        @Volatile private var instance: PermissionHealthManager? = null
        fun get(context: Context): PermissionHealthManager = instance ?: synchronized(this) {
            instance ?: PermissionHealthManager(context).also { instance = it }
        }
    }
}
