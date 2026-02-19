package com.example.launcherlock.model

enum class LockDayMode {
    EVERY_DAY,
    WEEKDAY,
    HOLIDAY
}

data class LockConfig(
    val mode: LockDayMode,
    val questions: List<String>,
    val lockHour: Int = 14,
    val lockMinute: Int = 0
)
