package com.example.launcherlock.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.launcherlock.BuildConfig
import com.example.launcherlock.network.NetworkModule
import com.example.launcherlock.queue.AppDatabase
import com.example.launcherlock.repo.SubmissionRepository
import com.example.launcherlock.security.DeviceSigningManager

class SubmissionRetryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("launcher_lock", Context.MODE_PRIVATE)
        val baseUrl = prefs.getString("api_base_url", "") ?: ""
        val token = BuildConfig.APP_TOKEN.trim()

        if (baseUrl.isBlank() || token.isBlank()) return Result.retry()

        val signingManager = DeviceSigningManager(applicationContext)
        val api = NetworkModule.createApi(applicationContext, baseUrl)
        val dao = AppDatabase.getInstance(applicationContext).pendingSubmissionDao()
        val repo = SubmissionRepository(api, dao, signingManager)
        repo.flushQueue()
        return Result.success()
    }
}
