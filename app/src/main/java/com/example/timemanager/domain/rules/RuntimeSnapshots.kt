package com.example.timemanager.domain.rules

import com.example.timemanager.data.model.FocusModeStatus
import com.example.timemanager.data.model.SessionGatePhase
import com.example.timemanager.permissions.AccessibilityRuntimeState

@JvmInline
value class UsageDayKey(val value: String) {
    init { require(value.isNotBlank()) }
}

/** Shared across profiles. The existing usage-day provider supplies the key. */
data class UsageDayRuntimeIdentity(val packageName: String, val usageDayKey: UsageDayKey) {
    init { require(packageName.isNotBlank()) }
}

data class DailyNotificationReceipts(
    val warningSent: Boolean = false,
    val reachedSent: Boolean = false,
)

data class DailyEdgeSnapshot(
    /** null is the legacy first-observation baseline (not previously over), not missing usage. */
    val previousOverLimit: Boolean? = null,
    val enteredThisVisit: Boolean = false,
    val displayRetryReady: Boolean = false,
    val observationAvailable: Boolean = true,
)

data class DailyRuntimeSnapshot(
    val identity: UsageDayRuntimeIdentity,
    val usageSeconds: Long,
    val todayExtensionSeconds: Long,
    val notificationReceipts: DailyNotificationReceipts = DailyNotificationReceipts(),
    val edge: DailyEdgeSnapshot = DailyEdgeSnapshot(),
) {
    init { require(usageSeconds >= 0L && todayExtensionSeconds >= 0L) }
}

data class PendingSessionGateSnapshot(
    val gateId: Long,
    val phase: SessionGatePhase,
    val endElapsedMillis: Long,
    val reminderCommitted: Boolean,
) {
    init { require(gateId > 0L && endElapsedMillis >= 0L) }
}

/** A read-only view of the existing owner's state, not a new stage manager or cache. */
data class SessionEpisodeSnapshot(
    val episodeUsageSeconds: Long,
    val firstReminderSeconds: Long,
    val reminderCount: Int,
    val nextReminderAtSeconds: Long,
    val lastEngagementElapsedMillis: Long,
    val pendingGate: PendingSessionGateSnapshot? = null,
) {
    init {
        require(episodeUsageSeconds >= 0L && reminderCount >= 0)
        require(firstReminderSeconds > 0L && nextReminderAtSeconds > 0L)
        require(lastEngagementElapsedMillis >= 0L)
    }
}

data class SessionAdmissionSnapshot(
    val reentryGateArmed: Boolean = false,
    val bypassExpiresAtElapsedMillis: Long? = null,
    val foregroundAdmitted: Boolean = false,
) {
    init { require(bypassExpiresAtElapsedMillis == null || bypassExpiresAtElapsedMillis >= 0L) }
}

data class PhysicalSessionSnapshot(val usageSeconds: Long, val actuallyInForeground: Boolean) {
    init { require(usageSeconds >= 0L) }
}

/** No ProfileId or usage day: an episode can continue across profile and 04:00 changes. */
data class SessionRuntimeSnapshot(
    val packageName: String,
    val episode: SessionEpisodeSnapshot,
    val physicalSession: PhysicalSessionSnapshot?,
    val admission: SessionAdmissionSnapshot = SessionAdmissionSnapshot(),
) {
    init { require(packageName.isNotBlank()) }
}

sealed interface FocusRuntimeSnapshot {
    data object Off : FocusRuntimeSnapshot

    class Focus(
        val startWallMillis: Long,
        val endWallMillis: Long,
        blockedPackages: Collection<String>,
        val status: FocusModeStatus = FocusModeStatus.ACTIVE,
    ) : FocusRuntimeSnapshot {
        val blockedPackages: Set<String> = buildSet { addAll(blockedPackages) }
        init { require(startWallMillis > 0L && endWallMillis > startWallMillis) }
    }

    data class DeepFocus(
        val startWallMillis: Long,
        val endWallMillis: Long,
        val status: FocusModeStatus,
    ) : FocusRuntimeSnapshot {
        init { require(startWallMillis > 0L && endWallMillis > startWallMillis) }
    }

    /** Independent supplied rule facts; legacy runtime modes remain mutually exclusive. */
    data class Concurrent(val focus: Focus, val deepFocus: DeepFocus) : FocusRuntimeSnapshot
}

data class CapabilitySnapshot(
    val usageAccessGranted: Boolean,
    /** Application-overlay grant, not the accessibility-owned overlay capability. */
    val overlayGranted: Boolean,
    val notificationsGranted: Boolean,
    val accessibility: AccessibilityRuntimeState,
)
