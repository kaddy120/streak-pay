package com.workpointstracker.api.service

import com.workpointstracker.api.dto.*
import com.workpointstracker.api.entity.SessionEntity
import com.workpointstracker.api.repository.SessionRepository
import com.workpointstracker.api.sse.SseConnectionManager
import com.workpointstracker.shared.PointsCalculator
import com.workpointstracker.shared.models.SessionType
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Service
class SessionService(
    private val sessionRepository: SessionRepository,
    private val streakService: StreakService,
    private val sseConnectionManager: SseConnectionManager
) {
    private val logger = LoggerFactory.getLogger(SessionService::class.java)
    private val pointsCalculator = PointsCalculator()

    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun cleanupShortSessions() {
        val deleted = sessionRepository.deleteShortCompletedSessions()
        if (deleted > 0) {
            logger.info("Cleaned up {} sessions with duration < {} min", deleted, PointsCalculator.MIN_SESSION_DURATION_MINUTES)
        }
    }

    @Transactional
    fun createSession(request: CreateSessionRequest): SessionResponse {
        val entity = SessionEntity(
            deviceId = request.deviceId,
            startTime = request.startTime,
            type = request.type
        )
        val saved = sessionRepository.save(entity)
        val response = saved.toResponse()
        sseConnectionManager.broadcast("session.created", response)
        return response
    }

    @Transactional
    fun updateSession(id: Long, request: UpdateSessionRequest): SessionResponse {
        val entity = sessionRepository.findById(id)
            .orElseThrow { NoSuchElementException("Session not found: $id") }

        request.startTime?.let { entity.startTime = it }
        request.isPaused?.let { paused ->
            entity.isPaused = paused
            if (paused) {
                // Use client-provided pausedAt if given, otherwise server time
                entity.pausedAt = request.pausedAt ?: LocalDateTime.now()
            } else {
                // On resume: auto-compute additional paused seconds if client didn't provide
                if (entity.pausedAt != null && request.totalPausedSeconds == null) {
                    val additionalPaused = ChronoUnit.SECONDS.between(entity.pausedAt, LocalDateTime.now())
                    entity.totalPausedSeconds += additionalPaused.coerceAtLeast(0)
                }
                entity.pausedAt = null
            }
        }
        // Allow explicit pausedAt/totalPausedSeconds override (PC daemon backward compat)
        if (request.isPaused == null) {
            request.pausedAt?.let { entity.pausedAt = it }
        }
        request.totalPausedSeconds?.let { entity.totalPausedSeconds = it }
        request.durationMinutes?.let { entity.durationMinutes = it }

        // If ending the session, calculate points
        request.endTime?.let { endTime ->
            entity.endTime = endTime
            entity.isPaused = false

            // Calculate duration if not provided
            if (request.durationMinutes == null) {
                val totalSeconds = ChronoUnit.SECONDS.between(entity.startTime, endTime)
                val activeSeconds = (totalSeconds - entity.totalPausedSeconds).coerceAtLeast(0)
                entity.durationMinutes = activeSeconds / 60
            }

            // Calculate points
            val date = entity.startTime.toLocalDate()
            val dayStart = date.atStartOfDay()
            val dayEnd = date.plusDays(1).atStartOfDay()
            val isFirstSession = sessionRepository.getCompletedSessionsCountForDate(dayStart, dayEnd) == 0

            val streakDays = streakService.getCurrentStreak()
            val result = pointsCalculator.calculatePoints(
                startTime = entity.startTime,
                durationMinutes = entity.durationMinutes,
                streakDays = streakDays,
                isFirstSessionOfDay = isFirstSession
            )
            entity.pointsEarned = result.points

            // Discard sessions shorter than minimum duration
            if (entity.durationMinutes < PointsCalculator.MIN_SESSION_DURATION_MINUTES) {
                sessionRepository.deleteById(entity.id)
                return entity.toResponse()
            }

            // Update streak if qualifying session (>= MIN_SESSION_DURATION_MINUTES for non-DAY_JOB)
            if (entity.durationMinutes >= PointsCalculator.MIN_SESSION_DURATION_MINUTES) {
                val qualifyingMinutes = sessionRepository.getTotalQualifyingMinutesForDate(dayStart, dayEnd) +
                    if (entity.type != SessionType.DAY_JOB) entity.durationMinutes else 0
                if (qualifyingMinutes >= 60) {
                    streakService.updateStreak(date, endTime)
                }
            }
        }

        val isEnding = request.endTime != null
        val saved = sessionRepository.save(entity)
        val response = saved.toResponse()
        sseConnectionManager.broadcast("session.updated", response)

        if (isEnding) {
            val totalPoints = sessionRepository.getTotalPoints()
            val streakInfo = streakService.getStreakInfo()
            sseConnectionManager.broadcast("stats.updated", mapOf(
                "totalPoints" to totalPoints,
                "streak" to streakInfo
            ))
        }

        return response
    }

    fun getSession(id: Long): SessionResponse {
        val entity = sessionRepository.findById(id)
            .orElseThrow { NoSuchElementException("Session not found: $id") }
        return entity.toResponse()
    }

    fun getSessions(startDate: LocalDate?, endDate: LocalDate?): List<SessionResponse> {
        return if (startDate != null && endDate != null) {
            sessionRepository.findByDateRange(
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay()
            ).map { it.toResponse() }
        } else {
            sessionRepository.findRecentCompleted().map { it.toResponse() }
        }
    }

    @Transactional
    fun deleteSession(id: Long) {
        if (!sessionRepository.existsById(id)) {
            throw NoSuchElementException("Session not found: $id")
        }
        sessionRepository.deleteById(id)
        sseConnectionManager.broadcast("session.deleted", mapOf("id" to id))
    }

    fun getTodayStats(): TodayStatsResponse {
        val today = LocalDate.now()
        val dayStart = today.atStartOfDay()
        val dayEnd = today.plusDays(1).atStartOfDay()

        val completedToday = sessionRepository.findCompletedForDate(dayStart, dayEnd)
        val totalMinutes = completedToday.sumOf { it.durationMinutes }
        val totalPoints = completedToday.sumOf { it.pointsEarned }

        val dayJobMinutes = sessionRepository.getTotalMinutesForDateAndType(dayStart, dayEnd, SessionType.DAY_JOB)
        val sideWorkMinutes = sessionRepository.getTotalMinutesForDateAndType(dayStart, dayEnd, SessionType.SIDE_WORK)
        val earlyMorningMinutes = sessionRepository.getTotalMinutesForDateAndType(dayStart, dayEnd, SessionType.EARLY_MORNING)

        return TodayStatsResponse(
            totalMinutes = totalMinutes,
            totalPoints = totalPoints,
            sessionCount = completedToday.size,
            dayJobMinutes = dayJobMinutes,
            sideWorkMinutes = sideWorkMinutes,
            earlyMorningMinutes = earlyMorningMinutes
        )
    }

    fun getTotalPoints(): PointsResponse {
        return PointsResponse(totalPoints = sessionRepository.getTotalPoints())
    }

    fun getActiveSessionsByDevice(deviceId: String): List<SessionResponse> {
        return sessionRepository.findActiveByDeviceId(deviceId).map { it.toResponse() }
    }

    fun getAllActiveSessions(): List<SessionResponse> {
        return sessionRepository.findAllActive().map { it.toResponse() }
    }

    fun getAllCompletedSessions(): List<SessionResponse> {
        return sessionRepository.findAllCompleted().map { it.toResponse() }
    }

    private fun SessionEntity.computeActiveElapsedSeconds(): Long {
        return when {
            // Completed session: use stored duration
            endTime != null -> durationMinutes * 60
            // Paused session: elapsed up to pause point minus paused time
            isPaused && pausedAt != null -> {
                val elapsedToP = ChronoUnit.SECONDS.between(startTime, pausedAt)
                (elapsedToP - totalPausedSeconds).coerceAtLeast(0)
            }
            // Active session: elapsed up to now minus paused time
            else -> {
                val totalSeconds = ChronoUnit.SECONDS.between(startTime, LocalDateTime.now())
                (totalSeconds - totalPausedSeconds).coerceAtLeast(0)
            }
        }
    }

    private fun SessionEntity.toResponse(): SessionResponse = SessionResponse(
        id = id,
        deviceId = deviceId,
        startTime = startTime,
        endTime = endTime,
        durationMinutes = durationMinutes,
        pointsEarned = pointsEarned,
        type = type,
        isPaused = isPaused,
        pausedAt = pausedAt,
        totalPausedSeconds = totalPausedSeconds,
        activeElapsedSeconds = computeActiveElapsedSeconds()
    )
}
