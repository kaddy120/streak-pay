package com.workpointstracker.data.local.database

import androidx.room.*
import com.workpointstracker.data.model.DailyGoal
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyGoalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dailyGoal: DailyGoal)

    @Update
    suspend fun update(dailyGoal: DailyGoal)

    @Query("SELECT * FROM daily_goals WHERE id = 1")
    fun getDailyGoal(): Flow<DailyGoal?>

    @Query("SELECT * FROM daily_goals WHERE id = 1")
    suspend fun getDailyGoalOnce(): DailyGoal?
}
