package com.workpointstracker.shared

import com.workpointstracker.shared.models.AppSettings
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

data class GracePeriodStatus(
    val isAvailable: Boolean,
    val hoursRemaining: Long,
    val minutesRemaining: Long,
    val daysUntilGracePeriodEarned: Int,
    val isUrgent: Boolean
)

data class StreakInfo(
    val currentStreak: Int,
    val gracePeriod: GracePeriodStatus,
    val streakAtRisk: Boolean
)

class StreakManager(private val settingsProvider: SettingsProvider) {

    companion object {
        const val GRACE_PERIOD_HOURS = 36L
        const val URGENT_THRESHOLD_HOURS = 12L
    }

    suspend fun updateStreak(workDate: LocalDate, sessionEndTime: LocalDateTime) {
        val settings = settingsProvider.getAppSettingsOnce() ?: AppSettings()

        val lastWorkDate = settings.lastWorkDate
        val currentStreak = settings.currentStreak
        val consecutiveWorkDays = settings.consecutiveWorkDays

        val (newStreak, newConsecutiveDays) = if (lastWorkDate == null) {
            Pair(1, 1)
        } else {
            val daysBetween = ChronoUnit.DAYS.between(lastWorkDate, workDate)
            when {
                daysBetween == 0L -> {
                    Pair(currentStreak, consecutiveWorkDays)
                }
                daysBetween == 1L -> {
                    Pair(currentStreak + 1, consecutiveWorkDays + 1)
                }
                daysBetween == 2L && hasGracePeriod(settings) -> {
                    Pair(currentStreak + 1, 1)
                }
                else -> {
                    Pair(1, 1)
                }
            }
        }

        val updatedSettings = settings.copy(
            currentStreak = newStreak,
            lastWorkDate = workDate,
            lastSessionEndTime = sessionEndTime,
            consecutiveWorkDays = newConsecutiveDays
        )

        settingsProvider.saveAppSettings(updatedSettings)
    }

    suspend fun getCurrentStreak(): Int {
        val settings = settingsProvider.getAppSettingsOnce() ?: AppSettings()
        return getValidatedStreak(settings)
    }

    suspend fun getStreakInfo(): StreakInfo {
        val settings = settingsProvider.getAppSettingsOnce() ?: AppSettings()
        val validatedStreak = getValidatedStreak(settings)
        val gracePeriod = calculateGracePeriodStatus(settings)
        val streakAtRisk = validatedStreak > 0 && isStreakAtRisk(settings, gracePeriod)

        return StreakInfo(
            currentStreak = validatedStreak,
            gracePeriod = gracePeriod,
            streakAtRisk = streakAtRisk
        )
    }

    private fun getValidatedStreak(settings: AppSettings): Int {
        val lastSessionEnd = settings.lastSessionEndTime ?: return settings.currentStreak
        val now = LocalDateTime.now()
        val hoursSinceLastSession = ChronoUnit.HOURS.between(lastSessionEnd, now)

        val gracePeriodAvailable = hasGracePeriod(settings)
        val maxHours = if (gracePeriodAvailable) GRACE_PERIOD_HOURS else 24L

        return if (hoursSinceLastSession > maxHours + 24) {
            0
        } else {
            settings.currentStreak
        }
    }

    private fun hasGracePeriod(settings: AppSettings): Boolean {
        return true
    }

    private fun calculateGracePeriodStatus(settings: AppSettings): GracePeriodStatus {
        val lastSessionEnd = settings.lastSessionEndTime

        if (lastSessionEnd == null) {
            return GracePeriodStatus(
                isAvailable = true,
                hoursRemaining = GRACE_PERIOD_HOURS,
                minutesRemaining = 0,
                daysUntilGracePeriodEarned = 0,
                isUrgent = false
            )
        }

        val now = LocalDateTime.now()
        val minutesSinceLastSession = ChronoUnit.MINUTES.between(lastSessionEnd, now)

        val totalMinutesRemaining = (GRACE_PERIOD_HOURS * 60 - minutesSinceLastSession).coerceAtLeast(0)
        val hoursRemaining = totalMinutesRemaining / 60
        val minutesRemaining = totalMinutesRemaining % 60

        return GracePeriodStatus(
            isAvailable = true,
            hoursRemaining = hoursRemaining,
            minutesRemaining = minutesRemaining,
            daysUntilGracePeriodEarned = 0,
            isUrgent = hoursRemaining in 1 until URGENT_THRESHOLD_HOURS
        )
    }

    private fun isStreakAtRisk(settings: AppSettings, gracePeriod: GracePeriodStatus): Boolean {
        if (settings.currentStreak == 0) return false

        val lastWorkDate = settings.lastWorkDate ?: return false
        val today = LocalDate.now()
        val daysSinceWork = ChronoUnit.DAYS.between(lastWorkDate, today)

        if (daysSinceWork == 0L) return false

        return gracePeriod.hoursRemaining <= URGENT_THRESHOLD_HOURS
    }

    fun getMotivationalMessage(streakInfo: StreakInfo, badges: List<Badge>): String {
        return when {
            streakInfo.streakAtRisk && (streakInfo.gracePeriod.hoursRemaining > 0 || streakInfo.gracePeriod.minutesRemaining > 0) -> {
                val timeStr = if (streakInfo.gracePeriod.hoursRemaining > 0)
                    "${streakInfo.gracePeriod.hoursRemaining}h ${streakInfo.gracePeriod.minutesRemaining}m"
                else
                    "${streakInfo.gracePeriod.minutesRemaining}m"
                "Only $timeStr left to keep your ${streakInfo.currentStreak}-day streak!"
            }
            streakInfo.streakAtRisk -> {
                "Work today to keep your ${streakInfo.currentStreak}-day streak alive!"
            }
            streakInfo.currentStreak >= 30 -> {
                "Incredible! ${streakInfo.currentStreak} days and counting!"
            }
            streakInfo.currentStreak >= 7 -> {
                "Amazing ${streakInfo.currentStreak}-day streak! Keep it up!"
            }
            badges.contains(Badge.EARLY_BIRD) -> {
                "Early Bird rising! Your morning routine is paying off."
            }
            badges.contains(Badge.CONSISTENT) -> {
                "Consistency is key! You're building great habits."
            }
            streakInfo.currentStreak > 0 -> {
                "${streakInfo.currentStreak}-day streak! Keep the momentum going!"
            }
            else -> {
                "Start a session to begin your streak!"
            }
        }
    }
}
