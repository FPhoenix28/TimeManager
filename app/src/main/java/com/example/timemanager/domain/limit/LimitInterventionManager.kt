package com.example.timemanager.domain.limit

import android.util.Log
import com.example.timemanager.data.model.InterventionState
import com.example.timemanager.data.model.LimitInterventionDecision
import com.example.timemanager.data.model.LimitInterventionRequest
import com.example.timemanager.data.model.UsageLimitEvaluation
import com.example.timemanager.data.repository.InterventionRepository
import com.example.timemanager.utils.UsageDayProvider

/** Owns soft-limit display, grace-period, and daily extension decisions. */
class LimitInterventionManager(
    private val repository: InterventionRepository,
    private val usageDayProvider: UsageDayProvider,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    private val stateCache = mutableMapOf<String, InterventionState>()
    private val retryAfterByPackage = mutableMapOf<String, Long>()
    private var visiblePackageName: String? = null

    suspend fun evaluate(
        appName: String,
        evaluation: UsageLimitEvaluation,
        currentSessionSeconds: Long,
        isNewSession: Boolean,
    ): LimitInterventionDecision {
        val packageName = evaluation.packageName
        val usageDayKey = usageDayProvider.currentUsageDay().key
        var state = getState(packageName, usageDayKey)
        val now = currentTimeMillis()
        val gracePeriodActive = state.gracePeriodEndTime > now
        Log.d(
            TAG,
            "evaluate packageName=$packageName isOverLimit=${evaluation.isLimitReached} " +
                "gracePeriodActive=$gracePeriodActive isNewSession=$isNewSession",
        )

        if (!evaluation.isLimitReached) {
            retryAfterByPackage.remove(packageName)
            return if (visiblePackageName == packageName) {
                visiblePackageName = null
                LimitInterventionDecision.Hide
            } else {
                LimitInterventionDecision.None
            }
        }

        if (visiblePackageName != null) return LimitInterventionDecision.None

        if (gracePeriodActive) {
            retryAfterByPackage.remove(packageName)
            return LimitInterventionDecision.None
        }

        val graceJustExpired = state.gracePeriodEndTime > 0L
        if (graceJustExpired) {
            state = repository.clearGracePeriod(packageName, usageDayKey)
            stateCache[packageName] = state
        }

        val retryAfter = retryAfterByPackage[packageName]
        val retryReady = retryAfter != null && now >= retryAfter
        if (!isNewSession && !graceJustExpired && !retryReady) {
            return LimitInterventionDecision.None
        }

        retryAfterByPackage.remove(packageName)
        visiblePackageName = packageName
        Log.d(TAG, "interventionTriggered=true packageName=$packageName")
        return LimitInterventionDecision.Show(
            LimitInterventionRequest(
                packageName = packageName,
                appName = appName,
                todayUsageSeconds = evaluation.usedSeconds,
                limitSeconds = evaluation.limitSeconds,
                exceededSeconds = (evaluation.usedSeconds - evaluation.limitSeconds)
                    .coerceAtLeast(0L),
                currentSessionSeconds = currentSessionSeconds,
                extensionsRemaining = (MAX_DAILY_EXTENSIONS - state.extensionsUsed)
                    .coerceAtLeast(0),
            ),
        )
    }

    suspend fun grantFiveMinuteGrace(packageName: String): Boolean {
        if (visiblePackageName != packageName) return false
        val usageDayKey = usageDayProvider.currentUsageDay().key
        val state = repository.grantGracePeriod(
            packageName = packageName,
            usageDayKey = usageDayKey,
            gracePeriodEndTime = currentTimeMillis() + GRACE_PERIOD_MILLIS,
            maxExtensions = MAX_DAILY_EXTENSIONS,
        ) ?: return false
        stateCache[packageName] = state
        visiblePackageName = null
        return true
    }

    fun closeVisibleIntervention(packageName: String) {
        retryAfterByPackage.remove(packageName)
        if (visiblePackageName == packageName) visiblePackageName = null
    }

    fun onDisplayFailed(packageName: String) {
        if (visiblePackageName == packageName) visiblePackageName = null
        retryAfterByPackage[packageName] = currentTimeMillis() + DISPLAY_RETRY_DELAY_MILLIS
        Log.w(TAG, "intervention display failed; retry scheduled packageName=$packageName")
    }

    fun onAppLeft(packageName: String): Boolean {
        retryAfterByPackage.remove(packageName)
        val wasVisible = visiblePackageName == packageName
        if (wasVisible) visiblePackageName = null
        return wasVisible
    }

    suspend fun removeApp(packageName: String) {
        stateCache.remove(packageName)
        closeVisibleIntervention(packageName)
        repository.remove(packageName)
    }

    private suspend fun getState(
        packageName: String,
        usageDayKey: String,
    ): InterventionState {
        val cached = stateCache[packageName]
        if (cached?.usageDayKey == usageDayKey) return cached
        return repository.getState(packageName, usageDayKey).also { state ->
            stateCache[packageName] = state
        }
    }

    companion object {
        const val MAX_DAILY_EXTENSIONS = 2
        const val GRACE_PERIOD_MILLIS = 5 * 60 * 1_000L
        private const val DISPLAY_RETRY_DELAY_MILLIS = 5_000L
        private const val TAG = "LimitIntervention"
    }
}
