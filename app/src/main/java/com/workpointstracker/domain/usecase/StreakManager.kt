package com.workpointstracker.domain.usecase

import com.workpointstracker.data.model.AppSettings
import com.workpointstracker.data.repository.SettingsRepository
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class StreakManager(private val settingsRepository: SettingsRepository) {

    suspend fun updateStreak(workDate: LocalDate) {
        val settings = settingsRepository.getAppSettingsOnce() ?: AppSettings()

        val lastWorkDate = settings.lastWorkDate
        val currentStreak = settings.currentStreak

        val newStreak = if (lastWorkDate == null) {
            // First time working
            1
        } else {
            val daysBetween = ChronoUnit.DAYS.between(lastWorkDate, workDate)
            when {
                daysBetween == 0L -> currentStreak // Same day, no change
                daysBetween == 1L -> currentStreak + 1 // Consecutive day
                else -> 1 // Streak broken, restart
            }
        }

        val updatedSettings = settings.copy(
            currentStreak = newStreak,
            lastWorkDate = workDate
        )

        settingsRepository.insertAppSettings(updatedSettings)
    }

    suspend fun getCurrentStreak(): Int {
        val settings = settingsRepository.getAppSettingsOnce() ?: AppSettings()
        return settings.currentStreak
    }
}
