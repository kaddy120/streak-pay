package com.workpointstracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey
    val id: Int = 1,
    val userName: String = "Kaddy",
    val currentStreak: Int = 0,
    val lastWorkDate: LocalDate? = null,
    val lastSessionEndTime: LocalDateTime? = null,
    val consecutiveWorkDays: Int = 0  // Days worked without using grace period
)
