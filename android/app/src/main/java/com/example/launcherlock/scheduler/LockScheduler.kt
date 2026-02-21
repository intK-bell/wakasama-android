package com.example.launcherlock.scheduler

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.launcherlock.worker.LockCheckWorker
import com.example.launcherlock.worker.SubmissionRetryWorker
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object LockScheduler {
    private const val PREFS_NAME = "launcher_lock"
    private const val LOCK_HOUR_KEY = "lock_hour"
    private const val LOCK_MINUTE_KEY = "lock_minute"
    private const val DEFAULT_LOCK_HOUR = 14
    private const val DEFAULT_LOCK_MINUTE = 0
    private const val LOCK_CHECK_WORK = "lock_check_daily"
    private const val LEGACY_LOCK_CHECK_WORK = "lock_check_14_00"
    private const val RETRY_WORK = "submission_retry"

    fun schedule(context: Context) {
        scheduleDailyLockCheck(context)
        scheduleRetry(context)
    }

    private fun scheduleDailyLockCheck(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(LEGACY_LOCK_CHECK_WORK)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lockHour = prefs.getInt(LOCK_HOUR_KEY, DEFAULT_LOCK_HOUR).coerceIn(0, 23)
        val lockMinute = prefs.getInt(LOCK_MINUTE_KEY, DEFAULT_LOCK_MINUTE).coerceIn(0, 59)
        val now = LocalDateTime.now()
        val nextRunTime = now.toLocalDate().atTime(LocalTime.of(lockHour, lockMinute)).let {
            if (it.isAfter(now)) it else it.plusDays(1)
        }
        val initialDelay = Duration.between(now, nextRunTime).toMinutes().coerceAtLeast(1)

        val request = PeriodicWorkRequestBuilder<LockCheckWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            LOCK_CHECK_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun scheduleRetry(context: Context) {
        val retryRequest = PeriodicWorkRequestBuilder<SubmissionRetryWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            RETRY_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            retryRequest
        )
    }

    fun runImmediateLockCheck(context: Context, owner: LifecycleOwner, onFinished: () -> Unit) {
        val oneShot = OneTimeWorkRequestBuilder<LockCheckWorker>().build()
        val workManager = WorkManager.getInstance(context)
        workManager.enqueue(oneShot)

        val liveData = workManager.getWorkInfoByIdLiveData(oneShot.id)
        liveData.observe(owner, object : Observer<WorkInfo?> {
            override fun onChanged(value: WorkInfo?) {
                if (value?.state == WorkInfo.State.SUCCEEDED ||
                    value?.state == WorkInfo.State.FAILED ||
                    value?.state == WorkInfo.State.CANCELLED) {
                    onFinished()
                    liveData.removeObserver(this)
                }
            }
        })
    }
}
