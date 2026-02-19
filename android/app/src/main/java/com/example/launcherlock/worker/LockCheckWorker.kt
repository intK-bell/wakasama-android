package com.example.launcherlock.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.launcherlock.model.JapanHolidayCalendar
import com.example.launcherlock.model.LockDayMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class LockCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("launcher_lock", Context.MODE_PRIVATE)
        val modeName = prefs.getString("lock_mode", LockDayMode.EVERY_DAY.name) ?: LockDayMode.EVERY_DAY.name
        val mode = runCatching { LockDayMode.valueOf(modeName) }.getOrDefault(LockDayMode.EVERY_DAY)

        val today = LocalDate.now(ZoneId.of("Asia/Tokyo"))
        val isWeekend = today.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        val isHoliday = JapanHolidayCalendar.isHoliday(today)
        val shouldLock = when (mode) {
            LockDayMode.EVERY_DAY -> true
            LockDayMode.WEEKDAY -> !isWeekend && !isHoliday
            LockDayMode.HOLIDAY -> isWeekend || isHoliday
        }

        prefs.edit().putBoolean("is_locked", shouldLock).apply()
        return Result.success()
    }
}
