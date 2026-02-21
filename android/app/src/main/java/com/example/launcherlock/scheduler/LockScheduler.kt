package com.example.launcherlock.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.launcherlock.worker.SubmissionRetryWorker
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object LockScheduler {
    private const val PREFS_NAME = "launcher_lock"
    private const val LOCK_HOUR_KEY = "lock_hour"
    private const val LOCK_MINUTE_KEY = "lock_minute"
    private const val DEFAULT_LOCK_HOUR = 14
    private const val DEFAULT_LOCK_MINUTE = 0
    const val ACTION_TIMER_LOCK = "com.example.launcherlock.action.TIMER_LOCK"
    private const val TIMER_LOCK_REQUEST_CODE = 10_021
    private const val LEGACY_LOCK_CHECK_WORK = "lock_check_14_00"
    private const val LEGACY_PERIODIC_LOCK_CHECK_WORK = "lock_check_daily"
    private const val LEGACY_ONE_SHOT_LOCK_CHECK_WORK = "lock_check_daily_once"
    private const val RETRY_WORK = "submission_retry"
    private val JST_ZONE: ZoneId = ZoneId.of("Asia/Tokyo")

    fun schedule(context: Context) {
        scheduleDailyLockAlarm(context)
        scheduleRetry(context)
    }

    private fun scheduleDailyLockAlarm(context: Context) {
        cancelLegacyLockWorks(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lockHour = prefs.getInt(LOCK_HOUR_KEY, DEFAULT_LOCK_HOUR).coerceIn(0, 23)
        val lockMinute = prefs.getInt(LOCK_MINUTE_KEY, DEFAULT_LOCK_MINUTE).coerceIn(0, 59)
        val now = ZonedDateTime.now(JST_ZONE)
        val nextRunTime = now.toLocalDate().atTime(LocalTime.of(lockHour, lockMinute)).atZone(JST_ZONE).let {
            if (it.isAfter(now)) it else it.plusDays(1)
        }
        val triggerAtMillis = now.toInstant().toEpochMilli() +
            Duration.between(now, nextRunTime).toMillis().coerceAtLeast(1_000L)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = timerLockPendingIntent(context)
        alarmManager.cancel(pendingIntent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            return
        }
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    fun onTimerAlarmFired(context: Context) {
        com.example.launcherlock.lock.LockStateEvaluator.applyTimedLockIfNeeded(context)
        schedule(context)
    }

    private fun timerLockPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, com.example.launcherlock.receiver.LockEventReceiver::class.java).apply {
            action = ACTION_TIMER_LOCK
        }
        return PendingIntent.getBroadcast(
            context,
            TIMER_LOCK_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelLegacyLockWorks(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(LEGACY_LOCK_CHECK_WORK)
        workManager.cancelUniqueWork(LEGACY_PERIODIC_LOCK_CHECK_WORK)
        workManager.cancelUniqueWork(LEGACY_ONE_SHOT_LOCK_CHECK_WORK)
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

    fun runImmediateLockCheck(context: Context, onFinished: () -> Unit) {
        onTimerAlarmFired(context)
        onFinished()
    }
}
