package com.workpointstracker.api.dto

import com.workpointstracker.shared.models.SessionType
import java.time.LocalDate
import java.time.LocalDateTime

// Session DTOs
data class CreateSessionRequest(
    val deviceId: String = "android",
    val startTime: LocalDateTime,
    val type: SessionType
)

data class UpdateSessionRequest(
    val startTime: LocalDateTime? = null,
    val endTime: LocalDateTime? = null,
    val isPaused: Boolean? = null,
    val pausedAt: LocalDateTime? = null,
    val totalPausedSeconds: Long? = null,
    val durationMinutes: Long? = null
)

data class SessionResponse(
    val id: Long,
    val deviceId: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime?,
    val durationMinutes: Long,
    val pointsEarned: Double,
    val type: SessionType,
    val isPaused: Boolean,
    val pausedAt: LocalDateTime?,
    val totalPausedSeconds: Long,
    val activeElapsedSeconds: Long
)

// Stats DTOs
data class TodayStatsResponse(
    val totalMinutes: Long,
    val totalPoints: Double,
    val sessionCount: Int,
    val dayJobMinutes: Long,
    val sideWorkMinutes: Long,
    val earlyMorningMinutes: Long
)

data class PointsResponse(
    val totalPoints: Double
)

data class StreakResponse(
    val currentStreak: Int,
    val gracePeriodHoursRemaining: Long,
    val gracePeriodMinutesRemaining: Long,
    val streakAtRisk: Boolean,
    val isUrgent: Boolean
)

// Settings DTOs
data class AppSettingsResponse(
    val userName: String,
    val currentStreak: Int,
    val lastWorkDate: LocalDate?,
    val lastSessionEndTime: LocalDateTime?,
    val consecutiveWorkDays: Int
)

data class UpdateSettingsRequest(
    val userName: String? = null
)

// Goals DTOs
data class DailyGoalResponse(
    val dayJobHours: Double,
    val sideWorkHours: Double
)

data class UpdateGoalRequest(
    val dayJobHours: Double? = null,
    val sideWorkHours: Double? = null
)

// Wish Item DTOs
data class CreateWishItemRequest(
    val name: String,
    val price: Double,
    val imageUrl: String? = null
)

data class UpdateWishItemRequest(
    val name: String? = null,
    val price: Double? = null,
    val imageUrl: String? = null,
    val isRedeemed: Boolean? = null,
    val redeemedDate: LocalDate? = null
)

data class WishItemResponse(
    val id: Long,
    val name: String,
    val price: Double,
    val imageUrl: String?,
    val isRedeemed: Boolean,
    val redeemedDate: LocalDate?
)

// Badge DTOs
data class BadgesResponse(
    val badges: List<BadgeDto>,
    val highlightedBadges: List<BadgeDto>,
    val motivationalMessage: String
) {
    data class BadgeDto(
        val name: String,
        val displayName: String,
        val description: String,
        val icon: String,
        val isPermanent: Boolean
    )
}

// Dashboard DTO
data class DashboardResponse(
    val totalPoints: Double,
    val streak: StreakResponse,
    val userName: String,
    val recentSessions: List<SessionResponse>,
    val badges: BadgesResponse,
    val activeSessions: List<SessionResponse>,
    val goals: DailyGoalResponse,
    val deviceStatuses: List<DeviceStatusResponse> = emptyList()
)

// Device Status DTOs
data class DeviceHeartbeatRequest(
    val deviceId: String,
    val state: String,
    val currentApp: String? = null,
    val sessionId: Long? = null,
    val elapsedSeconds: Long = 0,
    val pausedSeconds: Long = 0
)

data class DeviceStatusResponse(
    val deviceId: String,
    val state: String,
    val currentApp: String?,
    val sessionId: Long?,
    val elapsedSeconds: Long,
    val pausedSeconds: Long,
    val lastHeartbeat: String
)

data class DaemonCommandRequest(
    val deviceId: String,
    val action: String
)

// Image Upload DTO
data class ImageUploadResponse(
    val url: String
)
