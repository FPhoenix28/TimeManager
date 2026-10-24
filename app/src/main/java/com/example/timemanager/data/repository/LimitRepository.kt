package com.example.timemanager.data.repository

import android.util.Log
import com.example.timemanager.data.model.AppLimit
import com.example.timemanager.data.preferences.AppLimitPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Exposes per-app daily limits without leaking DataStore details. */
class LimitRepository(
    private val preferences: AppLimitPreferences,
    private val diagnostic: (String) -> Unit = { Log.d(TAG, it) },
) {
    fun observeLimit(packageName: String): Flow<AppLimit?> =
        preferences.observeLimitSeconds(packageName).map { limitSeconds ->
            limitSeconds?.let {
                AppLimit(
                    packageName = packageName,
                    limitMinutes = (it / 60L).toInt(),
                    limitSeconds = it,
                )
            }
        }

    fun observeAllLimits(): Flow<List<AppLimit>> =
        preferences.observeAllLimitSeconds().map { limitsByPackage ->
            limitsByPackage.map { (packageName, limitSeconds) ->
                AppLimit(
                    packageName = packageName,
                    limitMinutes = (limitSeconds / 60L).toInt(),
                    limitSeconds = limitSeconds,
                )
            }.sortedBy(AppLimit::packageName)
        }

    suspend fun saveLimit(appLimit: AppLimit) {
        diagnostic(
            "SAVE packageName=${appLimit.packageName} " +
                "requestedLimitMinutes=${appLimit.limitMinutes}",
        )
        if (appLimit.limitSeconds == appLimit.limitMinutes * 60L) {
            preferences.setLimitMinutes(appLimit.packageName, appLimit.limitMinutes)
        } else {
            preferences.setLimitSeconds(appLimit.packageName, appLimit.limitSeconds)
        }
        val storedLimitMinutes = preferences.observeLimitMinutes(appLimit.packageName).first()
        diagnostic(
            "READBACK packageName=${appLimit.packageName} " +
                "storedLimitMinutes=$storedLimitMinutes " +
                "storedLimitSeconds=${preferences.observeLimitSeconds(appLimit.packageName).first()}",
        )
    }

    suspend fun saveLimitSeconds(packageName: String, seconds: Long) {
        preferences.setLimitSeconds(packageName, seconds)
        diagnostic("SAVE_SECONDS packageName=$packageName requested=$seconds " +
            "readback=${preferences.observeLimitSeconds(packageName).first()}")
    }

    suspend fun removeLimit(packageName: String) {
        preferences.removeLimit(packageName)
    }

    private companion object {
        const val TAG = "LimitRepository"
    }
}
