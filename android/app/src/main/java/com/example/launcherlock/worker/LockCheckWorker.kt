package com.example.launcherlock.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.launcherlock.lock.LockStateEvaluator
import com.example.launcherlock.scheduler.LockScheduler

class LockCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        LockStateEvaluator.applyTimedLockIfNeeded(applicationContext)
        // Schedule the next day's timer lock window.
        LockScheduler.schedule(applicationContext)
        return Result.success()
    }
}
