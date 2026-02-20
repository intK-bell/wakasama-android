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
    companion object {
        private const val DEFAULT_WEEKDAY_CSV = "1,2,3,4,5"
    }

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("launcher_lock", Context.MODE_PRIVATE)
        val modeName = prefs.getString("lock_mode", LockDayMode.EVERY_DAY.name) ?: LockDayMode.EVERY_DAY.name
        val mode = runCatching { LockDayMode.valueOf(modeName) }.getOrDefault(LockDayMode.EVERY_DAY)
        val lockWeekdays = parseWeekdays(
            prefs.getString("lock_weekdays", DEFAULT_WEEKDAY_CSV) ?: DEFAULT_WEEKDAY_CSV
        )

        val today = LocalDate.now(ZoneId.of("Asia/Tokyo"))
        val isWeekend = today.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        val isHoliday = JapanHolidayCalendar.isHoliday(today)
        val isSelectedWeekday = today.dayOfWeek.value in lockWeekdays
        val shouldLock = when (mode) {
            LockDayMode.EVERY_DAY -> true
            LockDayMode.WEEKDAY -> isSelectedWeekday && !isHoliday
            LockDayMode.HOLIDAY -> isWeekend || isHoliday
        }

        prefs.edit().putBoolean("is_locked", shouldLock).apply()
        return Result.success()
    }

    private fun parseWeekdays(raw: String): Set<Int> {
        val parsed = raw.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
            .toSet()
        return if (parsed.isEmpty()) {
            setOf(
                DayOfWeek.MONDAY.value,
                DayOfWeek.TUESDAY.value,
                DayOfWeek.WEDNESDAY.value,
                DayOfWeek.THURSDAY.value,
                DayOfWeek.FRIDAY.value
            )
        } else {
            parsed
        }
    }
}
