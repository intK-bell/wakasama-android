package com.example.launcherlock.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.launcherlock.network.NetworkModule
import com.example.launcherlock.queue.AppDatabase
import com.example.launcherlock.repo.SubmissionRepository

class SubmissionRetryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("launcher_lock", Context.MODE_PRIVATE)
        val baseUrl = prefs.getString("api_base_url", "") ?: ""
        val token = prefs.getString(
            "api_app_token",
            prefs.getString("api_jwt", "")
        ) ?: ""

        if (baseUrl.isBlank() || token.isBlank()) return Result.retry()

        val api = NetworkModule.createApi(baseUrl) { token }
        val dao = AppDatabase.getInstance(applicationContext).pendingSubmissionDao()
        val repo = SubmissionRepository(api, dao)
        repo.flushQueue()
        return Result.success()
    }
}
