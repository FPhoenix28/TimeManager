package com.example.timemanager.data.repository

import com.example.timemanager.data.model.AppLimit
import com.example.timemanager.data.preferences.AppLimitPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Exposes per-app daily limits without leaking DataStore details. */
class LimitRepository(
    private val preferences: AppLimitPreferences,
) {
    fun observeLimit(packageName: String): Flow<AppLimit?> =
        preferences.observeLimitMinutes(packageName).map { limitMinutes ->
            limitMinutes?.let {
                AppLimit(
                    packageName = packageName,
                    limitMinutes = it,
                )
            }
        }

    fun observeAllLimits(): Flow<List<AppLimit>> =
        preferences.observeAllLimits().map { limitsByPackage ->
            limitsByPackage.map { (packageName, limitMinutes) ->
                AppLimit(
                    packageName = packageName,
                    limitMinutes = limitMinutes,
                )
            }.sortedBy(AppLimit::packageName)
        }

    suspend fun saveLimit(appLimit: AppLimit) {
        preferences.setLimitMinutes(
            packageName = appLimit.packageName,
            limitMinutes = appLimit.limitMinutes,
        )
    }

    suspend fun removeLimit(packageName: String) {
        preferences.removeLimit(packageName)
    }
}
