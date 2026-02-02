package com.workpointstracker.data.repository

import com.workpointstracker.data.local.database.AppSettingsDao
import com.workpointstracker.data.local.database.DailyGoalDao
import com.workpointstracker.data.model.AppSettings
import com.workpointstracker.data.model.DailyGoal
import kotlinx.coroutines.flow.Flow

class SettingsRepository(
    private val appSettingsDao: AppSettingsDao,
    private val dailyGoalDao: DailyGoalDao
) {

    fun getAppSettings(): Flow<AppSettings?> = appSettingsDao.getAppSettings()

    suspend fun getAppSettingsOnce(): AppSettings? = appSettingsDao.getAppSettingsOnce()

    suspend fun updateAppSettings(appSettings: AppSettings) = appSettingsDao.update(appSettings)

    suspend fun insertAppSettings(appSettings: AppSettings) = appSettingsDao.insert(appSettings)

    fun getDailyGoal(): Flow<DailyGoal?> = dailyGoalDao.getDailyGoal()

    suspend fun getDailyGoalOnce(): DailyGoal? = dailyGoalDao.getDailyGoalOnce()

    suspend fun updateDailyGoal(dailyGoal: DailyGoal) = dailyGoalDao.update(dailyGoal)

    suspend fun insertDailyGoal(dailyGoal: DailyGoal) = dailyGoalDao.insert(dailyGoal)
}
