package com.workpointstracker.shared.models

import java.time.LocalDate
import java.time.LocalDateTime

data class AppSettings(
    val id: Int = 1,
    val userName: String = "Kaddy",
    val currentStreak: Int = 0,
    val lastWorkDate: LocalDate? = null,
    val lastSessionEndTime: LocalDateTime? = null,
    val consecutiveWorkDays: Int = 0
)
