package com.example.timemanager.utils

import android.app.AppOpsManager
import android.content.Context
import android.os.Process

/** Checks whether this app has access to device usage statistics. */
object UsagePermissionManager {
    fun hasUsageAccess(context: Context): Boolean {
        val appOpsManager = context.getSystemService(AppOpsManager::class.java)
        val mode = appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
