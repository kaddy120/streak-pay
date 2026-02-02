package com.workpointstracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey
    val id: Int = 1,
    val userName: String = "User",
    val currentStreak: Int = 0,
    val lastWorkDate: LocalDate? = null
)
