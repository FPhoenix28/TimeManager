package com.example.timemanager.utils

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.example.timemanager.permissions.isAccessibilityComponentEnabled
import com.example.timemanager.service.TimeManagerAccessibilityService

object AccessibilityPermissionManager {
    fun isEnabled(context: Context): Boolean {
        // Configured access and a live Service connection are separate facts. OriginOS may
        // omit a bound service from AccessibilityManager's returned list in the Dev process.
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )
        return isAccessibilityComponentEnabled(
            enabled, context.packageName, TimeManagerAccessibilityService::class.java.name,
        )
    }

    fun settingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
}
