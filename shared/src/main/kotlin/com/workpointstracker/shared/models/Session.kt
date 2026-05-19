package com.workpointstracker.shared.models

import java.time.LocalDateTime

data class Session(
    val id: Long = 0,
    val deviceId: String = "android",
    val startTime: LocalDateTime,
    val endTime: LocalDateTime? = null,
    val durationMinutes: Long = 0,
    val pointsEarned: Double = 0.0,
    val type: SessionType,
    val isPaused: Boolean = false,
    val pausedAt: LocalDateTime? = null,
    val totalPausedSeconds: Long = 0
)
