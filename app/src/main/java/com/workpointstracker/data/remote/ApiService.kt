package com.workpointstracker.data.remote

import com.workpointstracker.shared.models.SessionType
import okhttp3.MultipartBody
import retrofit2.http.*
import java.time.LocalDate
import java.time.LocalDateTime

// Request/Response DTOs for the REST API

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
    val id: Long = 0,
    val deviceId: String = "",
    val startTime: LocalDateTime = LocalDateTime.now(),
    val endTime: LocalDateTime? = null,
    val durationMinutes: Long = 0,
    val pointsEarned: Double = 0.0,
    val type: SessionType = SessionType.DAY_JOB,
    val isPaused: Boolean = false,
    val pausedAt: LocalDateTime? = null,
    val totalPausedSeconds: Long = 0,
    val activeElapsedSeconds: Long = 0
)

data class TodayStatsResponse(
    val totalMinutes: Long = 0,
    val totalPoints: Double = 0.0,
    val sessionCount: Int = 0,
    val dayJobMinutes: Long = 0,
    val sideWorkMinutes: Long = 0,
    val earlyMorningMinutes: Long = 0
)

data class PointsResponse(
    val totalPoints: Double = 0.0
)

data class StreakResponse(
    val currentStreak: Int = 0,
    val gracePeriodHoursRemaining: Long = 0,
    val gracePeriodMinutesRemaining: Long = 0,
    val streakAtRisk: Boolean = false,
    val isUrgent: Boolean = false
)

data class AppSettingsResponse(
    val userName: String = "",
    val currentStreak: Int = 0,
    val lastWorkDate: LocalDate? = null,
    val lastSessionEndTime: LocalDateTime? = null,
    val consecutiveWorkDays: Int = 0
)

data class DailyGoalResponse(
    val dayJobHours: Double = 7.5,
    val sideWorkHours: Double = 4.0
)

data class WishItemResponse(
    val id: Long = 0,
    val name: String = "",
    val price: Double = 0.0,
    val imageUrl: String? = null,
    val isRedeemed: Boolean = false,
    val redeemedDate: LocalDate? = null
)

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

data class BadgesResponse(
    val badges: List<BadgeDto> = emptyList(),
    val highlightedBadges: List<BadgeDto> = emptyList(),
    val motivationalMessage: String = ""
) {
    data class BadgeDto(
        val name: String = "",
        val displayName: String = "",
        val description: String = "",
        val icon: String = "",
        val isPermanent: Boolean = false
    )
}

data class DeviceStatusResponse(
    val deviceId: String = "",
    val state: String = "",
    val currentApp: String? = null,
    val sessionId: Long? = null,
    val elapsedSeconds: Long = 0,
    val pausedSeconds: Long = 0,
    val lastHeartbeat: String = ""
)

data class DashboardResponse(
    val totalPoints: Double = 0.0,
    val streak: StreakResponse = StreakResponse(),
    val userName: String = "",
    val recentSessions: List<SessionResponse> = emptyList(),
    val badges: BadgesResponse = BadgesResponse(),
    val activeSessions: List<SessionResponse> = emptyList(),
    val goals: DailyGoalResponse = DailyGoalResponse(),
    val deviceStatuses: List<DeviceStatusResponse> = emptyList()
)

data class ImageUploadResponse(
    val url: String = ""
)

interface ApiService {

    // Sessions
    @POST("api/sessions")
    suspend fun createSession(@Body request: CreateSessionRequest): SessionResponse

    @PUT("api/sessions/{id}")
    suspend fun updateSession(@Path("id") id: Long, @Body request: UpdateSessionRequest): SessionResponse

    @GET("api/sessions/{id}")
    suspend fun getSession(@Path("id") id: Long): SessionResponse

    @GET("api/sessions")
    suspend fun getSessions(
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null
    ): List<SessionResponse>

    @DELETE("api/sessions/{id}")
    suspend fun deleteSession(@Path("id") id: Long)

    @GET("api/sessions/active")
    suspend fun getActiveSessions(): List<SessionResponse>

    // Stats
    @GET("api/stats/today")
    suspend fun getTodayStats(): TodayStatsResponse

    @GET("api/stats/points")
    suspend fun getTotalPoints(): PointsResponse

    @GET("api/stats/streak")
    suspend fun getStreakInfo(): StreakResponse

    // Settings
    @GET("api/settings")
    suspend fun getSettings(): AppSettingsResponse

    @PUT("api/settings")
    suspend fun updateSettings(@Body request: Map<String, String?>): AppSettingsResponse

    // Goals
    @GET("api/goals")
    suspend fun getGoals(): DailyGoalResponse

    @PUT("api/goals")
    suspend fun updateGoals(@Body request: Map<String, Double?>): DailyGoalResponse

    // Wish Items
    @GET("api/wishlist")
    suspend fun getWishItems(@Query("filter") filter: String? = null): List<WishItemResponse>

    @POST("api/wishlist")
    suspend fun createWishItem(@Body request: CreateWishItemRequest): WishItemResponse

    @PUT("api/wishlist/{id}")
    suspend fun updateWishItem(@Path("id") id: Long, @Body request: UpdateWishItemRequest): WishItemResponse

    @DELETE("api/wishlist/{id}")
    suspend fun deleteWishItem(@Path("id") id: Long)

    @GET("api/wishlist/{id}")
    suspend fun getWishItem(@Path("id") id: Long): WishItemResponse

    // Badges
    @GET("api/badges")
    suspend fun getBadges(): BadgesResponse

    // Dashboard
    @GET("api/dashboard")
    suspend fun getDashboard(): DashboardResponse

    // Image Upload
    @Multipart
    @POST("api/images")
    suspend fun uploadImage(@Part file: MultipartBody.Part): ImageUploadResponse
}
