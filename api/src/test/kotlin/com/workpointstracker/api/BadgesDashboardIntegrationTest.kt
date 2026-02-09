package com.workpointstracker.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.workpointstracker.api.dto.*
import com.workpointstracker.shared.models.SessionType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BadgesDashboardIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var mapper: ObjectMapper

    private val apiKey = "test-api-key"

    @BeforeEach
    fun cleanup() {
        val response = mockMvc.perform(
            get("/api/sessions").header("X-API-Key", apiKey)
        ).andReturn().response.contentAsString
        val sessions: List<SessionResponse> = mapper.readValue(response)
        sessions.forEach { session ->
            mockMvc.perform(
                delete("/api/sessions/${session.id}").header("X-API-Key", apiKey)
            )
        }
        val activeResponse = mockMvc.perform(
            get("/api/sessions/active").header("X-API-Key", apiKey)
        ).andReturn().response.contentAsString
        val activeSessions: List<SessionResponse> = mapper.readValue(activeResponse)
        activeSessions.forEach { session ->
            mockMvc.perform(
                delete("/api/sessions/${session.id}").header("X-API-Key", apiKey)
            )
        }
    }

    // ── Helpers ──

    private fun createSession(
        deviceId: String,
        startTime: LocalDateTime,
        type: SessionType = SessionType.SIDE_WORK
    ): SessionResponse {
        val body = mapper.writeValueAsString(CreateSessionRequest(deviceId, startTime, type))
        val result = mockMvc.perform(
            post("/api/sessions")
                .header("X-API-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andExpect(status().isCreated).andReturn()
        return mapper.readValue(result.response.contentAsString)
    }

    private fun updateSession(id: Long, request: UpdateSessionRequest): SessionResponse {
        val body = mapper.writeValueAsString(request)
        val result = mockMvc.perform(
            put("/api/sessions/$id")
                .header("X-API-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andExpect(status().isOk).andReturn()
        return mapper.readValue(result.response.contentAsString)
    }

    // ── Badge Tests ──

    @Test
    fun `GET badges returns correct structure`() {
        val result = mockMvc.perform(
            get("/api/badges").header("X-API-Key", apiKey)
        ).andExpect(status().isOk).andReturn()

        val badges: BadgesResponse = mapper.readValue(result.response.contentAsString)
        assertNotNull(badges.badges)
        assertNotNull(badges.highlightedBadges)
        assertNotNull(badges.motivationalMessage)
        assertTrue(badges.motivationalMessage.isNotBlank())
    }

    @Test
    fun `GET badges without API key returns 401`() {
        mockMvc.perform(get("/api/badges"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `highlightedBadges is subset of badges`() {
        // Seed a completed session to potentially earn badges
        val startTime = LocalDateTime.of(2026, 2, 8, 10, 0, 0)
        val endTime = LocalDateTime.of(2026, 2, 8, 10, 30, 0)
        val session = createSession("android", startTime)
        updateSession(session.id, UpdateSessionRequest(endTime = endTime, isPaused = false))

        val result = mockMvc.perform(
            get("/api/badges").header("X-API-Key", apiKey)
        ).andExpect(status().isOk).andReturn()

        val badges: BadgesResponse = mapper.readValue(result.response.contentAsString)
        val allBadgeNames = badges.badges.map { it.name }.toSet()
        val highlightedNames = badges.highlightedBadges.map { it.name }.toSet()
        assertTrue(allBadgeNames.containsAll(highlightedNames),
            "Highlighted badges should be a subset of all badges")
    }

    // ── Dashboard Tests ──

    @Test
    fun `GET dashboard returns all required fields`() {
        val result = mockMvc.perform(
            get("/api/dashboard").header("X-API-Key", apiKey)
        ).andExpect(status().isOk).andReturn()

        val dashboard: DashboardResponse = mapper.readValue(result.response.contentAsString)
        assertNotNull(dashboard.totalPoints)
        assertNotNull(dashboard.streak)
        assertNotNull(dashboard.userName)
        assertNotNull(dashboard.recentSessions)
        assertNotNull(dashboard.badges)
        assertNotNull(dashboard.activeSessions)
        assertNotNull(dashboard.goals)
    }

    @Test
    fun `GET dashboard without API key returns 401`() {
        mockMvc.perform(get("/api/dashboard"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `dashboard totalPoints matches points endpoint`() {
        // Seed a completed session
        val startTime = LocalDateTime.of(2026, 2, 8, 10, 0, 0)
        val endTime = LocalDateTime.of(2026, 2, 8, 10, 30, 0)
        val session = createSession("android", startTime)
        updateSession(session.id, UpdateSessionRequest(endTime = endTime, isPaused = false))

        val dashboardResult = mockMvc.perform(
            get("/api/dashboard").header("X-API-Key", apiKey)
        ).andExpect(status().isOk).andReturn()
        val dashboard: DashboardResponse = mapper.readValue(dashboardResult.response.contentAsString)

        val pointsResult = mockMvc.perform(
            get("/api/stats/points").header("X-API-Key", apiKey)
        ).andExpect(status().isOk).andReturn()
        val points: PointsResponse = mapper.readValue(pointsResult.response.contentAsString)

        assertEquals(points.totalPoints, dashboard.totalPoints,
            "Dashboard totalPoints should match /api/stats/points")
    }

    @Test
    fun `dashboard shows active sessions`() {
        // Create session without ending it
        val session = createSession("pc-myhost", LocalDateTime.now().minusMinutes(10))

        val result = mockMvc.perform(
            get("/api/dashboard").header("X-API-Key", apiKey)
        ).andExpect(status().isOk).andReturn()

        val dashboard: DashboardResponse = mapper.readValue(result.response.contentAsString)
        assertTrue(dashboard.activeSessions.any { it.id == session.id },
            "Dashboard should include the active session")
    }

    @Test
    fun `dashboard shows completed sessions in recentSessions`() {
        val startTime = LocalDateTime.of(2026, 2, 8, 10, 0, 0)
        val endTime = LocalDateTime.of(2026, 2, 8, 10, 30, 0)
        val session = createSession("android", startTime)
        updateSession(session.id, UpdateSessionRequest(endTime = endTime, isPaused = false))

        val result = mockMvc.perform(
            get("/api/dashboard").header("X-API-Key", apiKey)
        ).andExpect(status().isOk).andReturn()

        val dashboard: DashboardResponse = mapper.readValue(result.response.contentAsString)
        assertTrue(dashboard.recentSessions.any { it.id == session.id },
            "Dashboard should include completed session in recentSessions")
    }
}
