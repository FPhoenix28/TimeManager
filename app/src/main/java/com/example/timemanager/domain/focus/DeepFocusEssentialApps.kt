package com.example.timemanager.domain.focus

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.telecom.InCallService
import android.telecom.TelecomManager

data class DeepFocusEssentialApps(
    val ownPackage: String,
    val launcherPackage: String?,
    val inputMethodPackage: String?,
    val defaultDialerPackage: String?,
    val callUiPackages: Set<String>,
    val settingsPackage: String?,
    val systemUiPackages: Set<String> = setOf("com.android.systemui"),
)

class DeepFocusEssentialAppResolver(private val context: Context) {
    fun resolve(): DeepFocusEssentialApps {
        val packageManager = context.packageManager
        val launcherPackage = packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            0,
        )?.activityInfo?.packageName
        val inputMethodPackage = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD,
        )?.let(ComponentName::unflattenFromString)?.packageName
        val defaultDialerPackage = runCatching {
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        }.getOrNull()
        val callUiPackages = buildSet {
            defaultDialerPackage?.let(::add)
            @Suppress("DEPRECATION")
            packageManager.queryIntentServices(
                Intent(InCallService.SERVICE_INTERFACE),
                PackageManager.MATCH_SYSTEM_ONLY,
            ).mapNotNullTo(this) { it.serviceInfo?.packageName }
        }
        val settingsPackage = packageManager.resolveActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            0,
        )?.activityInfo?.packageName

        return DeepFocusEssentialApps(
            ownPackage = context.packageName,
            launcherPackage = launcherPackage,
            inputMethodPackage = inputMethodPackage,
            defaultDialerPackage = defaultDialerPackage,
            callUiPackages = callUiPackages,
            settingsPackage = settingsPackage,
        )
    }
}
