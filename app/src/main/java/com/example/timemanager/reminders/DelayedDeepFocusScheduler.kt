package com.example.timemanager.reminders

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.timemanager.data.model.FocusModeType
import com.example.timemanager.data.preferences.FocusModePreferences
import com.example.timemanager.data.repository.FocusModeRepository
import com.example.timemanager.permissions.PermissionHealthManager
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class DelayedDeepFocusStatus(
    val workId: String = "",
    val pending: Boolean = false,
    val notice: String? = null,
)

enum class DelayedDeepFocusScheduleResult { SCHEDULED, ALREADY_PENDING }

/** WorkManager is the pending-state store; this class holds no preparation timer or Service. */
class DelayedDeepFocusScheduler internal constructor(
    private val workManager: WorkManager,
    private val repository: FocusModeRepository,
    private val permissionsReady: () -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun observe(): Flow<DelayedDeepFocusStatus> = workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME)
        .map { infos ->
            val work = infos.firstOrNull { !it.state.isFinished } ?: infos.firstOrNull()
            DelayedDeepFocusStatus(
                workId = work?.id?.toString().orEmpty(),
                pending = work != null && !work.state.isFinished,
                notice = work?.outputData?.getString(FAILURE_REASON),
            )
        }.distinctUntilChanged()

    suspend fun schedule(): DelayedDeepFocusScheduleResult = withContext(Dispatchers.IO) {
        transitionMutex.withLock {
            if (pendingWork() != null) return@withLock DelayedDeepFocusScheduleResult.ALREADY_PENDING
            check(permissionsReady()) { "请先开启并连接无障碍服务，同时允许悬浮窗，再安排静心。" }
            val request = OneTimeWorkRequestBuilder<DelayedDeepFocusWorker>()
                .setInitialDelay(PREPARATION_MINUTES, TimeUnit.MINUTES)
                .setInputData(workDataOf(EXPECTED_START to now() + PREPARATION_MINUTES * 60_000L))
                .build()
            workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request).result.get()
            check(pendingWork() != null) { "未能保存延迟静心安排，请重试。" }
            DelayedDeepFocusScheduleResult.SCHEDULED
        }
    }

    /** Returns a message only if cancellation was too late; never ends an already-started mode. */
    suspend fun cancelPending(workId: String? = null): String? = withContext(Dispatchers.IO) {
        transitionMutex.withLock {
            if (workId != null && repository.wasDelayedDeepFocusStarted(workId)) {
                return@withLock "静心已经开始，请使用原有的提前结束入口。"
            }
            val work = if (workId == null) pendingWork() else workManager.getWorkInfoById(UUID.fromString(workId)).get()
            if (work == null || work.state.isFinished) return@withLock null
            if (repository.wasDelayedDeepFocusStarted(work.id.toString())) {
                return@withLock "静心已经开始，请使用原有的提前结束入口。"
            }
            workManager.cancelWorkById(work.id).result.get()
            null
        }
    }

    /** Null means completed (or already applied); a reason means no automatic start. */
    internal suspend fun startWhenEligible(id: UUID, expectedStart: Long): String? = withContext(Dispatchers.IO) {
        transitionMutex.withLock {
            currentCoroutineContext().ensureActive()
            val work = workManager.getWorkInfoById(id).get()
            if (work == null || work.state != WorkInfo.State.RUNNING) return@withLock "本次安排已取消或结束。"
            if (repository.wasDelayedDeepFocusStarted(id.toString())) return@withLock null
            val actualStart = now()
            if (expectedStart <= 0L || actualStart < expectedStart || actualStart - expectedStart > MAX_LATENESS_MILLIS) {
                return@withLock "延迟静心未启动：已错过准备期，或系统时间发生变化。"
            }
            if (!permissionsReady()) return@withLock "延迟静心未启动：无障碍服务未连接或悬浮窗权限已关闭。"
            val current = repository.getCurrentState()
            if (current.mode == FocusModeType.DEEP_FOCUS) return@withLock "延迟静心未启动：当前静心尚未关闭。"
            if (current.isActive(actualStart)) return@withLock "延迟静心未启动：普通专注正在进行，不会覆盖它。"
            currentCoroutineContext().ensureActive()
            repository.startDeepFocus(DURATION_MINUTES, replaceActiveFocus = false,
                nowMillis = now(), delayedRequestId = id.toString())
            null
        }
    }

    private fun pendingWork(): WorkInfo? = workManager.getWorkInfosForUniqueWork(WORK_NAME).get()
        .firstOrNull { !it.state.isFinished }

    companion object {
        const val WORK_NAME = "delayed_deep_focus"
        const val EXPECTED_START = "expected_start_millis"
        const val FAILURE_REASON = "failure_reason"
        const val PREPARATION_MINUTES = 5L
        const val DURATION_MINUTES = 30
        const val MAX_LATENESS_MILLIS = 5L * 60_000L
        // Only serializes short transitions. Durable pending state remains in WorkManager.
        private val transitionMutex = Mutex()
        @Volatile private var instance: DelayedDeepFocusScheduler? = null

        fun get(context: Context): DelayedDeepFocusScheduler = instance ?: synchronized(this) {
            instance ?: context.applicationContext.let { app ->
                DelayedDeepFocusScheduler(
                    WorkManager.getInstance(app), FocusModeRepository(FocusModePreferences(app), app.packageName),
                    { PermissionHealthManager.get(app).refresh().let { it.canForceHome && it.overlayGranted } },
                ).also { instance = it }
            }
        }
    }
}
