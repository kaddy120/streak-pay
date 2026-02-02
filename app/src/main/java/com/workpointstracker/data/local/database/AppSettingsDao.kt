package com.workpointstracker.data.local.database

import androidx.room.*
import com.workpointstracker.data.model.AppSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface AppSettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(appSettings: AppSettings)

    @Update
    suspend fun update(appSettings: AppSettings)

    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun getAppSettings(): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getAppSettingsOnce(): AppSettings?
}
