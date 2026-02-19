package com.example.launcherlock.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

object JapanHolidayCalendar {
    private val cache = ConcurrentHashMap<Int, Set<LocalDate>>()

    fun isHoliday(date: LocalDate): Boolean {
        val holidays = cache.getOrPut(date.year) { buildHolidaySet(date.year) }
        return date in holidays
    }

    private fun buildHolidaySet(year: Int): Set<LocalDate> {
        val base = mutableSetOf<LocalDate>()

        // Fixed-date holidays
        base += LocalDate.of(year, 1, 1) // New Year's Day
        base += LocalDate.of(year, 2, 11) // National Foundation Day
        base += LocalDate.of(year, 2, 23) // Emperor's Birthday
        base += LocalDate.of(year, 4, 29) // Showa Day
        base += LocalDate.of(year, 5, 3) // Constitution Memorial Day
        base += LocalDate.of(year, 5, 4) // Greenery Day
        base += LocalDate.of(year, 5, 5) // Children's Day
        base += LocalDate.of(year, 8, 11) // Mountain Day
        base += LocalDate.of(year, 11, 3) // Culture Day
        base += LocalDate.of(year, 11, 23) // Labor Thanksgiving Day

        // Happy Monday system holidays
        base += nthWeekdayOfMonth(year, 1, DayOfWeek.MONDAY, 2) // Coming of Age Day
        base += nthWeekdayOfMonth(year, 7, DayOfWeek.MONDAY, 3) // Marine Day
        base += nthWeekdayOfMonth(year, 9, DayOfWeek.MONDAY, 3) // Respect for the Aged Day
        base += nthWeekdayOfMonth(year, 10, DayOfWeek.MONDAY, 2) // Sports Day

        // Equinox holidays
        base += LocalDate.of(year, 3, vernalEquinoxDay(year))
        base += LocalDate.of(year, 9, autumnalEquinoxDay(year))

        val holidaysWithSubstitute = base.toMutableSet()

        // Substitute holidays when the holiday falls on Sunday
        base.sorted().forEach { holiday ->
            if (holiday.dayOfWeek == DayOfWeek.SUNDAY) {
                var substitute = holiday.plusDays(1)
                while (substitute in holidaysWithSubstitute) {
                    substitute = substitute.plusDays(1)
                }
                holidaysWithSubstitute += substitute
            }
        }

        // Citizen's Holiday: a weekday between two holidays
        var day = LocalDate.of(year, 1, 2)
        val end = LocalDate.of(year, 12, 30)
        while (!day.isAfter(end)) {
            if (
                day.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) &&
                day !in holidaysWithSubstitute &&
                day.minusDays(1) in holidaysWithSubstitute &&
                day.plusDays(1) in holidaysWithSubstitute
            ) {
                holidaysWithSubstitute += day
            }
            day = day.plusDays(1)
        }

        return holidaysWithSubstitute
    }

    private fun nthWeekdayOfMonth(year: Int, month: Int, weekday: DayOfWeek, nth: Int): LocalDate {
        val firstDay = LocalDate.of(year, month, 1)
        val dayOffset = (weekday.value - firstDay.dayOfWeek.value + 7) % 7
        return firstDay.plusDays(dayOffset.toLong() + 7L * (nth - 1))
    }

    private fun vernalEquinoxDay(year: Int): Int {
        val day = floor(20.8431 + 0.242194 * (year - 1980) - floor((year - 1980) / 4.0))
        return day.toInt()
    }

    private fun autumnalEquinoxDay(year: Int): Int {
        val day = floor(23.2488 + 0.242194 * (year - 1980) - floor((year - 1980) / 4.0))
        return day.toInt()
    }
}
