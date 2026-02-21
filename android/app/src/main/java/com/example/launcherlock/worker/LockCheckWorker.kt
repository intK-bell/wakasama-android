package com.example.launcherlock.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.launcherlock.lock.LockStateEvaluator

class LockCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        LockStateEvaluator.applyTimedLockIfNeeded(applicationContext)
        return Result.success()
    }
}
