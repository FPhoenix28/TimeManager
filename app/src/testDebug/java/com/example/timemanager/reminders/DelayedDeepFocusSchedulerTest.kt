package com.example.timemanager.reminders

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.timemanager.data.model.FocusModeType
import com.example.timemanager.data.preferences.FocusModePreferences
import com.example.timemanager.data.repository.FocusModeRepository
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** WorkManager test DB and application sandbox only; real Focus repository/preferences. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DelayedDeepFocusSchedulerTest {
    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var repository: FocusModeRepository
    private lateinit var scheduler: DelayedDeepFocusScheduler
    private var now = 1_800_000_000_000L
    private var permissions = true
    private var startPermissionCheck: (() -> Unit)? = null

    private fun newScheduler() = DelayedDeepFocusScheduler(workManager, repository,
        { startPermissionCheck?.invoke(); permissions }, { now })

    @Before fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
                if (workerClassName == DelayedDeepFocusWorker::class.java.name)
                    DelayedDeepFocusWorker(appContext, workerParameters, scheduler) else null
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder()
            .setExecutor(SynchronousExecutor()).setTaskExecutor(SynchronousExecutor()).setWorkerFactory(factory).build())
        workManager = WorkManager.getInstance(context)
        repository = FocusModeRepository(FocusModePreferences(context), context.packageName)
        repository.stopFocus()
        scheduler = newScheduler()
    }

    @After fun tearDown() { WorkManagerTestInitHelper.closeWorkDatabase() }

    private fun work(): WorkInfo = workManager.getWorkInfosForUniqueWork(DelayedDeepFocusScheduler.WORK_NAME).get().single()
    private suspend fun finish(id: UUID): WorkInfo = withTimeout(10_000L) {
        workManager.getWorkInfoByIdFlow(id).first { it?.state?.isFinished == true }!!
    }
    private fun releaseDelay(id: UUID) {
        WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(id)
    }
    private suspend fun enqueue(): UUID {
        assertEquals(DelayedDeepFocusScheduleResult.SCHEDULED, scheduler.schedule())
        return work().id
    }

    @Test fun scheduleCreatesOneFiveMinuteRequestAndStartsFullThirtyMinutes() = runBlocking {
        val id = enqueue()
        assertEquals(300_000L, work().initialDelayMillis)
        assertEquals(FocusModeType.OFF, repository.getCurrentState().mode)
        now += 300_000L
        releaseDelay(id)
        assertEquals(WorkInfo.State.SUCCEEDED, finish(id).state)
        val focus = repository.getCurrentState()
        assertTrue(focus.isDeepFocusActive(now))
        assertEquals(now, focus.startTimeMillis)
        assertEquals(now + 1_800_000L, focus.endTimeMillis)
    }

    @Test fun repeatedScheduleKeepsOriginalRequestAndTime() = runBlocking {
        val id = enqueue()
        now += 60_000L
        assertEquals(DelayedDeepFocusScheduleResult.ALREADY_PENDING, scheduler.schedule())
        assertEquals(id, work().id)
        now += 240_000L
        releaseDelay(id)
        assertEquals(WorkInfo.State.SUCCEEDED, finish(id).state)
        assertEquals(now, repository.getCurrentState().startTimeMillis)
    }

    @Test fun cancelBeforeExecutionPreventsStart() = runBlocking {
        val id = enqueue()
        assertNull(scheduler.cancelPending(id.toString()))
        now += 300_000L
        assertEquals(WorkInfo.State.CANCELLED, finish(id).state)
        assertEquals(FocusModeType.OFF, repository.getCurrentState().mode)
        assertFalse(scheduler.observe().first().pending)
    }

    @Test fun activeOrdinaryFocusIsNeverReplaced() = runBlocking {
        repository.startFocus(30, setOf("test.app"), now)
        val before = repository.getCurrentState()
        val id = enqueue()
        now += 300_000L
        releaseDelay(id)
        assertEquals(WorkInfo.State.FAILED, finish(id).state)
        assertEquals(before, repository.getCurrentState())
    }

    @Test fun existingDeepFocusKeepsItsOriginalEndTime() = runBlocking {
        repository.startDeepFocus(30, false, now)
        val before = repository.getCurrentState()
        val id = enqueue()
        now += 300_000L
        releaseDelay(id)
        assertEquals(WorkInfo.State.FAILED, finish(id).state)
        assertEquals(before, repository.getCurrentState())
    }

    @Test fun expiredOrdinaryFocusAllowsNormalDeepStart() = runBlocking {
        repository.startFocus(1, setOf("test.app"), now)
        val id = enqueue()
        now += 300_000L
        releaseDelay(id)
        assertEquals(WorkInfo.State.SUCCEEDED, finish(id).state)
        assertTrue(repository.getCurrentState().isDeepFocusActive(now))
    }

    @Test fun missingPermissionRejectsScheduling() = runBlocking {
        permissions = false
        try { scheduler.schedule(); fail("Expected permission rejection") }
        catch (expected: IllegalStateException) { assertTrue(expected.message!!.contains("无障碍")) }
        assertTrue(workManager.getWorkInfosForUniqueWork(DelayedDeepFocusScheduler.WORK_NAME).get().isEmpty())
    }

    @Test fun permissionRevocationDuringPreparationDoesNotStart() = runBlocking {
        val id = enqueue()
        permissions = false
        now += 300_000L
        releaseDelay(id)
        assertEquals(WorkInfo.State.FAILED, finish(id).state)
        assertEquals(FocusModeType.OFF, repository.getCurrentState().mode)
        assertTrue(scheduler.observe().first().notice!!.contains("权限"))
    }

    @Test fun excessivelyLateWorkExpiresWithoutStarting() = runBlocking {
        val id = enqueue()
        now += 300_000L + DelayedDeepFocusScheduler.MAX_LATENESS_MILLIS + 1L
        releaseDelay(id)
        assertEquals(WorkInfo.State.FAILED, finish(id).state)
        assertEquals(FocusModeType.OFF, repository.getCurrentState().mode)
    }

    @Test fun modestDelayStillProvidesFullThirtyMinutes() = runBlocking {
        val id = enqueue()
        now += 420_000L
        releaseDelay(id)
        assertEquals(WorkInfo.State.SUCCEEDED, finish(id).state)
        assertEquals(now + 1_800_000L, repository.getCurrentState().endTimeMillis)
    }

    @Test fun schedulerRecreationFindsDurablePendingWork() = runBlocking {
        val id = enqueue()
        scheduler = newScheduler()
        assertEquals(id.toString(), scheduler.observe().first().workId)
        assertTrue(scheduler.observe().first().pending)
        now += 300_000L
        releaseDelay(id)
        assertEquals(WorkInfo.State.SUCCEEDED, finish(id).state)
    }

    @Test fun appliedWorkReceiptSurvivesEarlyEndAndPreventsRestart() = runBlocking {
        val id = enqueue()
        now += 300_000L
        releaseDelay(id)
        finish(id)
        repository.stopFocus()
        assertFalse(repository.startDeepFocus(30, false, now + 1_000L, delayedRequestId = id.toString()))
        assertEquals(FocusModeType.OFF, repository.getCurrentState().mode)
    }

    @Test fun cancellationAfterStartCommitReportsTooLateAndDoesNotResetEndTime() = runBlocking {
        val id = enqueue()
        val entered = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        startPermissionCheck = { entered.countDown(); check(proceed.await(5, TimeUnit.SECONDS)) }
        now += 300_000L
        releaseDelay(id)
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val cancellation = async(Dispatchers.IO) { scheduler.cancelPending(id.toString()) }
        proceed.countDown()
        assertTrue(cancellation.await()!!.contains("已经开始"))
        assertEquals(WorkInfo.State.SUCCEEDED, finish(id).state)
        assertEquals(now + 1_800_000L, repository.getCurrentState().endTimeMillis)
    }
}
