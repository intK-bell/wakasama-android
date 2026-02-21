package com.example.launcherlock.lock

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.launcherlock.model.JapanHolidayCalendar
import com.example.launcherlock.model.LockDayMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object LockStateEvaluator {
    const val PREFS_NAME = "launcher_lock"
    const val IS_LOCKED_KEY = "is_locked"
    private const val DEFAULT_WEEKDAY_CSV = "1,2,3,4,5"
    private val JST_ZONE: ZoneId = ZoneId.of("Asia/Tokyo")

    fun applyTimedLockIfNeeded(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!shouldLockNow(prefs)) {
            return false
        }
        val wasLocked = prefs.getBoolean(IS_LOCKED_KEY, false)
        if (!wasLocked) {
            prefs.edit { putBoolean(IS_LOCKED_KEY, true) }
            return true
        }
        return false
    }

    fun shouldLockNow(prefs: SharedPreferences): Boolean {
        val now = LocalDate.now(JST_ZONE)
        val currentTime = LocalTime.now(JST_ZONE)
        val lockHour = prefs.getInt("lock_hour", 14).coerceIn(0, 23)
        val lockMinute = prefs.getInt("lock_minute", 0).coerceIn(0, 59)
        val lockTime = LocalTime.of(lockHour, lockMinute)
        if (currentTime.isBefore(lockTime)) {
            return false
        }

        val modeName = prefs.getString("lock_mode", LockDayMode.EVERY_DAY.name) ?: LockDayMode.EVERY_DAY.name
        val mode = runCatching { LockDayMode.valueOf(modeName) }.getOrDefault(LockDayMode.EVERY_DAY)
        val lockWeekdays = parseWeekdays(
            prefs.getString("lock_weekdays", DEFAULT_WEEKDAY_CSV) ?: DEFAULT_WEEKDAY_CSV
        )
        val isWeekend = now.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        val isHoliday = JapanHolidayCalendar.isHoliday(now)
        val isSelectedWeekday = now.dayOfWeek.value in lockWeekdays
        return when (mode) {
            LockDayMode.EVERY_DAY -> true
            LockDayMode.WEEKDAY -> isSelectedWeekday && !isHoliday
            LockDayMode.HOLIDAY -> isWeekend || isHoliday
        }
    }

    private fun parseWeekdays(raw: String): Set<Int> {
        if (raw.isBlank()) return emptySet()
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
