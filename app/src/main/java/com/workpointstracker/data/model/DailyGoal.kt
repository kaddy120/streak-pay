package com.workpointstracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_goals")
data class DailyGoal(
    @PrimaryKey
    val id: Int = 1,
    val dayJobHours: Double = 7.5,
    val sideWorkHours: Double = 4.0
)
