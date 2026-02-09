package com.workpointstracker.pcclient.api

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.workpointstracker.shared.models.SessionType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

data class SessionDto(
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

data class TodayStats(
    val totalMinutes: Long = 0,
    val totalPoints: Double = 0.0,
    val sessionCount: Int = 0,
    val dayJobMinutes: Long = 0,
    val sideWorkMinutes: Long = 0,
    val earlyMorningMinutes: Long = 0
)

data class StreakDto(
    val currentStreak: Int = 0,
    val gracePeriodHoursRemaining: Long = 0,
    val gracePeriodMinutesRemaining: Long = 0,
    val streakAtRisk: Boolean = false,
    val isUrgent: Boolean = false
)

data class PointsDto(
    val totalPoints: Double = 0.0
)

class ApiClient(
    private val baseUrl: String,
    private val apiKey: String
) {
    private val logger = LoggerFactory.getLogger(ApiClient::class.java)
    private val JSON = "application/json".toMediaType()

    private val mapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    fun createSession(deviceId: String, startTime: LocalDateTime, type: SessionType): SessionDto? {
        val body = mapper.writeValueAsString(mapOf(
            "deviceId" to deviceId,
            "startTime" to startTime.toString(),
            "type" to type.name
        ))
        return post("/api/sessions", body)?.let { mapper.readValue(it, SessionDto::class.java) }
    }

    fun updateSession(id: Long, updates: Map<String, Any?>): SessionDto? {
        val body = mapper.writeValueAsString(updates)
        return put("/api/sessions/$id", body)?.let { mapper.readValue(it, SessionDto::class.java) }
    }

    fun deleteSession(id: Long): Boolean {
        return delete("/api/sessions/$id")
    }

    fun getSession(id: Long): SessionDto? {
        return get("/api/sessions/$id")?.let { mapper.readValue(it, SessionDto::class.java) }
    }

    fun getActiveSessions(deviceId: String): List<SessionDto> {
        val response = get("/api/sessions/active?deviceId=$deviceId") ?: return emptyList()
        return mapper.readValue(response, mapper.typeFactory.constructCollectionType(List::class.java, SessionDto::class.java))
    }

    fun getAllActiveSessions(): List<SessionDto> {
        val response = get("/api/sessions/active") ?: return emptyList()
        return mapper.readValue(response, mapper.typeFactory.constructCollectionType(List::class.java, SessionDto::class.java))
    }

    fun getRecentSessions(): List<SessionDto> {
        val response = get("/api/sessions") ?: return emptyList()
        return mapper.readValue(response, mapper.typeFactory.constructCollectionType(List::class.java, SessionDto::class.java))
    }

    fun getTodayStats(): TodayStats? {
        return get("/api/stats/today")?.let { mapper.readValue(it, TodayStats::class.java) }
    }

    fun getTotalPoints(): PointsDto? {
        return get("/api/stats/points")?.let { mapper.readValue(it, PointsDto::class.java) }
    }

    fun getStreakInfo(): StreakDto? {
        return get("/api/stats/streak")?.let { mapper.readValue(it, StreakDto::class.java) }
    }

    fun sendHeartbeat(
        deviceId: String,
        state: String,
        currentApp: String?,
        sessionId: Long?,
        elapsedSeconds: Long,
        pausedSeconds: Long
    ): Boolean {
        val body = mapper.writeValueAsString(mapOf(
            "deviceId" to deviceId,
            "state" to state,
            "currentApp" to currentApp,
            "sessionId" to sessionId,
            "elapsedSeconds" to elapsedSeconds,
            "pausedSeconds" to pausedSeconds
        ))
        return post("/api/devices/heartbeat", body) != null
    }

    fun removeDeviceStatus(deviceId: String): Boolean {
        return delete("/api/devices/status/$deviceId")
    }

    private fun get(path: String): String? {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .header("X-API-Key", apiKey)
            .get()
            .build()
        return execute(request)
    }

    private fun post(path: String, body: String): String? {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .header("X-API-Key", apiKey)
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON))
            .build()
        return execute(request)
    }

    private fun put(path: String, body: String): String? {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .header("X-API-Key", apiKey)
            .header("Content-Type", "application/json")
            .put(body.toRequestBody(JSON))
            .build()
        return execute(request)
    }

    private fun delete(path: String): Boolean {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .header("X-API-Key", apiKey)
            .delete()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            logger.error("DELETE {} failed: {}", path, e.message)
            false
        }
    }

    private fun execute(request: Request): String? {
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    logger.error("HTTP {} {} → {}", request.method, request.url.encodedPath, response.code)
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Request {} {} failed: {}", request.method, request.url.encodedPath, e.message)
            null
        }
    }
}
