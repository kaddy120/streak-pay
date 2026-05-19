package com.workpointstracker.shared.models

data class DailyGoal(
    val id: Int = 1,
    val dayJobHours: Double = 7.5,
    val sideWorkHours: Double = 4.0
)
