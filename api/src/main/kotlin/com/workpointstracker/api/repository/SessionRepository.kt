package com.workpointstracker.api.repository

import com.workpointstracker.api.entity.SessionEntity
import com.workpointstracker.shared.models.SessionType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface SessionRepository : JpaRepository<SessionEntity, Long> {

    fun findByEndTimeIsNotNullOrderByStartTimeDesc(
    ): List<SessionEntity>

    @Query("SELECT s FROM SessionEntity s WHERE s.startTime >= :start AND s.startTime < :end ORDER BY s.startTime DESC")
    fun findByDateRange(
        @Param("start") start: LocalDateTime,
        @Param("end") end: LocalDateTime
    ): List<SessionEntity>

    @Query("SELECT s FROM SessionEntity s WHERE s.endTime IS NOT NULL ORDER BY s.startTime DESC")
    fun findAllCompleted(): List<SessionEntity>

    @Query("SELECT s FROM SessionEntity s WHERE s.endTime IS NOT NULL ORDER BY s.startTime DESC LIMIT 10")
    fun findRecentCompleted(): List<SessionEntity>

    @Query("SELECT COALESCE(SUM(s.pointsEarned), 0.0) FROM SessionEntity s WHERE s.endTime IS NOT NULL")
    fun getTotalPoints(): Double

    @Query("SELECT COALESCE(SUM(s.durationMinutes), 0) FROM SessionEntity s WHERE s.endTime IS NOT NULL AND s.startTime >= :start AND s.startTime < :end AND s.type = :type")
    fun getTotalMinutesForDateAndType(
        @Param("start") start: LocalDateTime,
        @Param("end") end: LocalDateTime,
        @Param("type") type: SessionType
    ): Long

    @Query("SELECT s FROM SessionEntity s WHERE s.endTime IS NOT NULL AND s.startTime >= :start AND s.startTime < :end")
    fun findCompletedForDate(
        @Param("start") start: LocalDateTime,
        @Param("end") end: LocalDateTime
    ): List<SessionEntity>

    @Query("SELECT COUNT(s) FROM SessionEntity s WHERE s.endTime IS NOT NULL AND s.startTime >= :start AND s.startTime < :end AND s.type <> 'DAY_JOB'")
    fun getCompletedSessionsCountForDate(
        @Param("start") start: LocalDateTime,
        @Param("end") end: LocalDateTime
    ): Int

    @Query("SELECT COALESCE(SUM(s.durationMinutes), 0) FROM SessionEntity s WHERE s.endTime IS NOT NULL AND s.startTime >= :start AND s.startTime < :end AND (s.type = 'SIDE_WORK' OR s.type = 'EARLY_MORNING')")
    fun getTotalQualifyingMinutesForDate(
        @Param("start") start: LocalDateTime,
        @Param("end") end: LocalDateTime
    ): Long

    @Query("SELECT s FROM SessionEntity s WHERE s.endTime IS NULL AND s.deviceId = :deviceId")
    fun findActiveByDeviceId(@Param("deviceId") deviceId: String): List<SessionEntity>

    @Query("SELECT s FROM SessionEntity s WHERE s.endTime IS NULL")
    fun findAllActive(): List<SessionEntity>
}
