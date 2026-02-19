package com.example.launcherlock.scheduler

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.launcherlock.worker.LockCheckWorker
import com.example.launcherlock.worker.SubmissionRetryWorker
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object LockScheduler {
    private const val LOCK_CHECK_WORK = "lock_check_14_00"
    private const val RETRY_WORK = "submission_retry"

    fun schedule(context: Context) {
        scheduleDailyLockCheck(context)
        scheduleRetry(context)
    }

    private fun scheduleDailyLockCheck(context: Context) {
        val now = LocalDateTime.now()
        val nextRunTime = now.toLocalDate().atTime(LocalTime.of(14, 0)).let {
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

    fun runImmediateLockCheck(context: Context) {
        val oneShot = OneTimeWorkRequestBuilder<LockCheckWorker>().build()
        WorkManager.getInstance(context).enqueue(oneShot)
    }
}
