package com.workpointstracker.api.service

import com.workpointstracker.api.dto.StreakResponse
import com.workpointstracker.api.entity.AppSettingsEntity
import com.workpointstracker.api.repository.AppSettingsRepository
import com.workpointstracker.shared.SettingsProvider
import com.workpointstracker.shared.StreakInfo
import com.workpointstracker.shared.StreakManager
import com.workpointstracker.shared.models.AppSettings
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class StreakService(
    private val appSettingsRepository: AppSettingsRepository
) {
    private val settingsProvider = object : SettingsProvider {
        override suspend fun getAppSettingsOnce(): AppSettings? {
            return appSettingsRepository.findById(1L)
                .map { it.toShared() }
                .orElse(null)
        }

        override suspend fun saveAppSettings(appSettings: AppSettings) {
            appSettingsRepository.save(AppSettingsEntity.fromShared(appSettings))
        }
    }

    val streakManager = StreakManager(settingsProvider)

    fun getCurrentStreak(): Int = runBlocking {
        streakManager.getCurrentStreak()
    }

    fun updateStreak(workDate: LocalDate, sessionEndTime: LocalDateTime) = runBlocking {
        streakManager.updateStreak(workDate, sessionEndTime)
    }

    fun getStreakInfo(): StreakResponse = runBlocking {
        val info = streakManager.getStreakInfo()
        StreakResponse(
            currentStreak = info.currentStreak,
            gracePeriodHoursRemaining = info.gracePeriod.hoursRemaining,
            gracePeriodMinutesRemaining = info.gracePeriod.minutesRemaining,
            streakAtRisk = info.streakAtRisk,
            isUrgent = info.gracePeriod.isUrgent
        )
    }

    fun getStreakInfoShared(): StreakInfo = runBlocking {
        streakManager.getStreakInfo()
    }

    fun getSettings(): AppSettings {
        return appSettingsRepository.findById(1L)
            .map { it.toShared() }
            .orElse(AppSettings())
    }

    fun updateSettings(settings: AppSettings) {
        appSettingsRepository.save(AppSettingsEntity.fromShared(settings))
    }
}
