package com.workpointstracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime? = null,
    val durationMinutes: Long = 0,
    val pointsEarned: Double = 0.0,
    val type: SessionType,
    val isPaused: Boolean = false,
    val pausedAt: LocalDateTime? = null,
    val totalPausedMinutes: Long = 0
)

enum class SessionType {
    DAY_JOB,
    SIDE_WORK,
    EARLY_MORNING
}
