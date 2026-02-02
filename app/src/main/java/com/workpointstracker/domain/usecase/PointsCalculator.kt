package com.workpointstracker.domain.usecase

import com.workpointstracker.data.model.SessionType
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

class PointsCalculator {

    companion object {
        private const val DAY_JOB_RATE = 0.25
        private const val SIDE_WORK_RATE = 1.0
        private const val EARLY_MORNING_RATE = 1.5
        private const val FIRST_HOUR_BONUS = 0.5

        private val EARLY_MORNING_START = LocalTime.of(5, 0)
        private val EARLY_MORNING_END = LocalTime.of(8, 0)
        private val DAY_JOB_START = LocalTime.of(9, 0)
        private val DAY_JOB_END = LocalTime.of(15, 0)
    }

    fun calculatePoints(
        startTime: LocalDateTime,
        durationMinutes: Long,
        streakDays: Int,
        isFirstSessionOfDay: Boolean
    ): CalculationResult {
        val hours = durationMinutes / 60.0
        val sessionType = determineSessionType(startTime)

        val basePoints = when (sessionType) {
            SessionType.DAY_JOB -> hours * DAY_JOB_RATE
            SessionType.SIDE_WORK -> hours * SIDE_WORK_RATE
            SessionType.EARLY_MORNING -> hours * EARLY_MORNING_RATE
        }

        var totalPoints = basePoints

        // Apply first hour bonus (only for side work and early morning)
        if (isFirstSessionOfDay && sessionType != SessionType.DAY_JOB) {
            totalPoints += FIRST_HOUR_BONUS
        }

        // Apply streak multiplier (only for side work and early morning)
        if (sessionType != SessionType.DAY_JOB) {
            val streakMultiplier = getStreakMultiplier(streakDays)
            totalPoints *= streakMultiplier
        }

        return CalculationResult(
            points = totalPoints,
            sessionType = sessionType,
            basePoints = basePoints,
            streakMultiplier = if (sessionType != SessionType.DAY_JOB) getStreakMultiplier(streakDays) else 1.0
        )
    }

    private fun determineSessionType(startTime: LocalDateTime): SessionType {
        val time = startTime.toLocalTime()
        val dayOfWeek = startTime.dayOfWeek

        // Weekend work is always side work
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return if (time >= EARLY_MORNING_START && time < EARLY_MORNING_END) {
                SessionType.EARLY_MORNING
            } else {
                SessionType.SIDE_WORK
            }
        }

        // Weekday logic
        return when {
            time >= EARLY_MORNING_START && time < EARLY_MORNING_END -> SessionType.EARLY_MORNING
            time >= DAY_JOB_START && time < DAY_JOB_END -> SessionType.DAY_JOB
            else -> SessionType.SIDE_WORK
        }
    }

    private fun getStreakMultiplier(streakDays: Int): Double {
        return when {
            streakDays >= 30 -> 1.20  // 20% bonus
            streakDays >= 7 -> 1.15   // 15% bonus
            streakDays >= 3 -> 1.10   // 10% bonus
            else -> 1.0               // No bonus
        }
    }

    fun getStreakBonusPercentage(streakDays: Int): Int {
        return when {
            streakDays >= 30 -> 20
            streakDays >= 7 -> 15
            streakDays >= 3 -> 10
            else -> 0
        }
    }

    data class CalculationResult(
        val points: Double,
        val sessionType: SessionType,
        val basePoints: Double,
        val streakMultiplier: Double
    )
}
