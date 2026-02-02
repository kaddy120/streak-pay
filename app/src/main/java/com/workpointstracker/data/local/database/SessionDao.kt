package com.workpointstracker.data.local.database

import androidx.room.*
import com.workpointstracker.data.model.Session
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: Session): Long

    @Update
    suspend fun update(session: Session)

    @Delete
    suspend fun delete(session: Session)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): Session?

    @Query("SELECT * FROM sessions WHERE endTime IS NOT NULL ORDER BY startTime DESC LIMIT 10")
    fun getRecentSessions(): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE endTime IS NOT NULL ORDER BY startTime DESC")
    fun getAllCompletedSessions(): Flow<List<Session>>

    @Query("""
        SELECT COUNT(*) FROM sessions
        WHERE endTime IS NOT NULL
        AND date(startTime) = :date
        AND type != 'DAY_JOB'
    """)
    suspend fun getCompletedSessionsCountForDate(date: String): Int

    @Query("""
        SELECT * FROM sessions
        WHERE endTime IS NOT NULL
        AND date(startTime) BETWEEN :startDate AND :endDate
        ORDER BY startTime ASC
    """)
    suspend fun getSessionsInDateRange(startDate: String, endDate: String): List<Session>

    @Query("SELECT SUM(pointsEarned) FROM sessions WHERE endTime IS NOT NULL")
    fun getTotalPoints(): Flow<Double?>

    @Query("""
        SELECT SUM(durationMinutes) FROM sessions
        WHERE endTime IS NOT NULL
        AND date(startTime) = :date
        AND type = :sessionType
    """)
    suspend fun getTotalMinutesForDateAndType(date: String, sessionType: String): Long?
}
