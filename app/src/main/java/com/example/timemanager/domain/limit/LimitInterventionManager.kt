package com.example.timemanager.domain.limit

import android.util.Log
import com.example.timemanager.data.model.InterventionState
import com.example.timemanager.data.model.EffectiveAppLimit
import com.example.timemanager.data.model.LimitInterventionDecision
import com.example.timemanager.data.model.LimitInterventionRequest
import com.example.timemanager.data.model.UsageLimitEvaluation
import com.example.timemanager.data.repository.InterventionRepository
import com.example.timemanager.config.BuildTimeConfig
import com.example.timemanager.config.TimeConfig
import com.example.timemanager.utils.UsageDayProvider

/** Owns soft-limit display, effective daily allowance, and extension decisions. */
class LimitInterventionManager(
    private val repository: InterventionRepository,
    private val usageDayProvider: UsageDayProvider,
    private val timeConfig: TimeConfig = BuildTimeConfig.value,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    private val stateCache = mutableMapOf<String, InterventionState>()
    private val retryAfterByPackage = mutableMapOf<String, Long>()
    private val lastTriggeredLimitByPackage = mutableMapOf<String, TriggeredLimit>()
    private val triggerState = LimitInterventionTriggerState()
    private var visiblePackageName: String? = null

    /** Records an app-entry event until the first limit evaluation for that session consumes it. */
    fun onManagedAppEntered(packageName: String) {
        triggerState.onManagedAppEntered(packageName)
    }

    suspend fun getEffectiveLimit(
        packageName: String,
        baseDailyLimitMinutes: Int,
    ): EffectiveAppLimit {
        val usageDayKey = usageDayProvider.currentUsageDay().key
        val state = getState(packageName, usageDayKey)
        val baseLimitSeconds = baseDailyLimitMinutes * SECONDS_PER_MINUTE
        return EffectiveAppLimit(
            packageName = packageName,
            baseLimitSeconds = baseLimitSeconds,
            todayExtensionSeconds = state.todayExtensionSeconds,
        )
    }

    suspend fun evaluate(
        appName: String,
        evaluation: UsageLimitEvaluation,
        baseLimitSeconds: Long,
        currentSessionSeconds: Long,
        isNewSession: Boolean,
    ): LimitInterventionDecision {
        val packageName = evaluation.packageName
        val usageDayKey = usageDayProvider.currentUsageDay().key
        val state = getState(packageName, usageDayKey)
        if (isNewSession) triggerState.onManagedAppEntered(packageName)
        val triggerSnapshot = triggerState.evaluate(
            packageName = packageName,
            usageDayKey = usageDayKey,
            currentOverLimit = evaluation.isLimitReached,
        )
        val overlayShowing = visiblePackageName != null
        val now = currentTimeMillis()
        val retryAfter = retryAfterByPackage[packageName]
        val retryReady = retryAfter != null && now >= retryAfter
        val lastTriggeredLimitSeconds = lastTriggeredLimitByPackage[packageName]
            ?.takeIf { it.usageDayKey == usageDayKey }
            ?.limitSeconds
        val shouldIntervene = evaluation.isLimitReached &&
            !overlayShowing &&
            (
                triggerSnapshot.limitCrossed ||
                    triggerSnapshot.enteredWhileOverLimit ||
                    retryReady
                )
        Log.d(
            TAG,
            "DailyLimit: packageName=$packageName " +
                "todayUsageSeconds=${evaluation.usedSeconds} " +
                "baseLimitSeconds=$baseLimitSeconds " +
                "extensionSeconds=${state.todayExtensionSeconds} " +
                "effectiveLimitSeconds=${evaluation.limitSeconds} " +
                "lastTriggeredLimitSeconds=$lastTriggeredLimitSeconds " +
                "previousOverLimit=${triggerSnapshot.previousOverLimit} " +
                "currentOverLimit=${triggerSnapshot.currentOverLimit} " +
                "limitCrossed=${triggerSnapshot.limitCrossed} " +
                "isNewSession=$isNewSession " +
                "enteredWhileOverLimit=${triggerSnapshot.enteredWhileOverLimit} " +
                "overlayShowing=$overlayShowing " +
                "shouldIntervene=$shouldIntervene",
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

        if (!shouldIntervene) return LimitInterventionDecision.None

        retryAfterByPackage.remove(packageName)
        lastTriggeredLimitByPackage[packageName] = TriggeredLimit(
            usageDayKey = usageDayKey,
            limitSeconds = evaluation.limitSeconds,
        )
        visiblePackageName = packageName
        Log.d(TAG, "interventionTriggered=true packageName=$packageName")
        return LimitInterventionDecision.Show(
            LimitInterventionRequest(
                type = com.example.timemanager.data.model.InterventionType.DAILY_LIMIT_REACHED,
                packageName = packageName,
                appName = appName,
                todayUsageSeconds = evaluation.usedSeconds,
                baseLimitSeconds = baseLimitSeconds,
                extensionSeconds = state.todayExtensionSeconds,
                effectiveLimitSeconds = evaluation.limitSeconds,
                exceededSeconds = (evaluation.usedSeconds - evaluation.limitSeconds)
                    .coerceAtLeast(0L),
                currentSessionSeconds = currentSessionSeconds,
                extensionDurationSeconds = timeConfig.extensionDurationSeconds,
            ),
        )
    }

    suspend fun grantConfiguredExtension(
        packageName: String,
        baseLimitSeconds: Long,
    ): Boolean {
        if (visiblePackageName != packageName) return false
        val usageDayKey = usageDayProvider.currentUsageDay().key
        val before = getState(packageName, usageDayKey)
        val state = repository.grantExtension(
            packageName = packageName,
            usageDayKey = usageDayKey,
            extensionSeconds = timeConfig.extensionDurationSeconds,
        )
        stateCache[packageName] = state
        triggerState.onLimitExtended(packageName, usageDayKey)
        retryAfterByPackage.remove(packageName)
        visiblePackageName = null
        Log.d(
            TAG,
            "Extension: packageName=$packageName " +
                "beforeExtension=${before.todayExtensionSeconds} " +
                "afterExtension=${state.todayExtensionSeconds} " +
                "newEffectiveLimit=${baseLimitSeconds + state.todayExtensionSeconds}",
        )
        return true
    }

    fun closeVisibleIntervention(packageName: String) {
        retryAfterByPackage.remove(packageName)
        if (visiblePackageName == packageName) visiblePackageName = null
    }

    fun onDisplayFailed(packageName: String) {
        if (visiblePackageName == packageName) visiblePackageName = null
        lastTriggeredLimitByPackage.remove(packageName)
        retryAfterByPackage[packageName] = currentTimeMillis() + DISPLAY_RETRY_DELAY_MILLIS
        Log.w(TAG, "intervention display failed; retry scheduled packageName=$packageName")
    }

    fun onAppLeft(packageName: String): Boolean {
        retryAfterByPackage.remove(packageName)
        triggerState.onAppLeft(packageName)
        val wasVisible = visiblePackageName == packageName
        if (wasVisible) visiblePackageName = null
        return wasVisible
    }

    suspend fun removeApp(packageName: String) {
        stateCache.remove(packageName)
        lastTriggeredLimitByPackage.remove(packageName)
        triggerState.remove(packageName)
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
        private const val SECONDS_PER_MINUTE = 60L
        private const val DISPLAY_RETRY_DELAY_MILLIS = 5_000L
        private const val TAG = "LimitIntervention"
    }

    private data class TriggeredLimit(
        val usageDayKey: String,
        val limitSeconds: Long,
    )
}

internal class LimitInterventionTriggerState {
    private data class PreviousState(
        val usageDayKey: String,
        val isOverLimit: Boolean,
    )

    data class Snapshot(
        val previousOverLimit: Boolean,
        val currentOverLimit: Boolean,
        val limitCrossed: Boolean,
        val enteredWhileOverLimit: Boolean,
    )

    private val previousStateByPackage = mutableMapOf<String, PreviousState>()
    private val pendingAppEntries = mutableSetOf<String>()

    fun onManagedAppEntered(packageName: String) {
        pendingAppEntries += packageName
    }

    fun evaluate(
        packageName: String,
        usageDayKey: String,
        currentOverLimit: Boolean,
    ): Snapshot {
        val previousState = previousStateByPackage[packageName]
        val previousOverLimit = previousState
            ?.takeIf { it.usageDayKey == usageDayKey }
            ?.isOverLimit
            ?: false
        val limitCrossed = !previousOverLimit && currentOverLimit
        val enteredThisSession = pendingAppEntries.remove(packageName)
        val enteredWhileOverLimit = enteredThisSession && currentOverLimit

        previousStateByPackage[packageName] = PreviousState(
            usageDayKey = usageDayKey,
            isOverLimit = currentOverLimit,
        )
        return Snapshot(
            previousOverLimit = previousOverLimit,
            currentOverLimit = currentOverLimit,
            limitCrossed = limitCrossed,
            enteredWhileOverLimit = enteredWhileOverLimit,
        )
    }

    fun onAppLeft(packageName: String) {
        pendingAppEntries.remove(packageName)
    }

    fun onLimitExtended(packageName: String, usageDayKey: String) {
        pendingAppEntries.remove(packageName)
        previousStateByPackage[packageName] = PreviousState(
            usageDayKey = usageDayKey,
            isOverLimit = false,
        )
    }

    fun remove(packageName: String) {
        pendingAppEntries.remove(packageName)
        previousStateByPackage.remove(packageName)
    }
}
