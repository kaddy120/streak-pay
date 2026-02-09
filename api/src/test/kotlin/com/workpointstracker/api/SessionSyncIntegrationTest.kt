package com.workpointstracker.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.workpointstracker.api.dto.CreateSessionRequest
import com.workpointstracker.api.dto.SessionResponse
import com.workpointstracker.api.dto.UpdateSessionRequest
import com.workpointstracker.api.sse.SseConnectionManager
import com.workpointstracker.shared.models.SessionType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.reset
import org.mockito.Mockito.verify
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionSyncIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var mapper: ObjectMapper
    @SpyBean lateinit var sseConnectionManager: SseConnectionManager

    private val apiKey = "test-api-key"

    @BeforeEach
    fun cleanup() {
        reset(sseConnectionManager)
        // Delete all sessions via listing then deleting each
        val response = mockMvc.perform(
            get("/api/sessions").header("X-API-Key", apiKey)
        ).andReturn().response.contentAsString
        val sessions: List<SessionResponse> = mapper.readValue(response)
        sessions.forEach { session ->
            mockMvc.perform(
                delete("/api/sessions/${session.id}").header("X-API-Key", apiKey)
            )
        }
        // Also clean up active sessions
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

    // ── Helper methods ──

    private fun createSession(deviceId: String, startTime: LocalDateTime, type: SessionType = SessionType.SIDE_WORK): SessionResponse {
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

    private fun getSession(id: Long): SessionResponse {
        val result = mockMvc.perform(
            get("/api/sessions/$id").header("X-API-Key", apiKey)
        ).andExpect(status().isOk).andReturn()
        return mapper.readValue(result.response.contentAsString)
    }

    private fun getActiveSessions(deviceId: String? = null): List<SessionResponse> {
        val url = if (deviceId != null) "/api/sessions/active?deviceId=$deviceId" else "/api/sessions/active"
        val result = mockMvc.perform(
            get(url).header("X-API-Key", apiKey)
        ).andExpect(status().isOk).andReturn()
        return mapper.readValue(result.response.contentAsString)
    }

    // ── 1. Session Creation ──

    @Test
    fun `create session returns correct response`() {
        val startTime = LocalDateTime.of(2026, 2, 8, 10, 0, 0)
        val session = createSession("android", startTime, SessionType.SIDE_WORK)

        assertEquals("android", session.deviceId)
        assertEquals(startTime, session.startTime)
        assertNull(session.endTime)
        assertEquals(SessionType.SIDE_WORK, session.type)
        assertFalse(session.isPaused)
        assertEquals(0, session.totalPausedSeconds)
        assertTrue(session.id > 0)
    }

    @Test
    fun `created session appears in active sessions`() {
        val session = createSession("pc-myhost", LocalDateTime.now().minusMinutes(10))

        val active = getActiveSessions()
        assertEquals(1, active.size)
        assertEquals(session.id, active[0].id)
    }

    // ── 2. Active Sessions Filtering ──

    @Test
    fun `get active sessions without deviceId returns all`() {
        createSession("android", LocalDateTime.now().minusMinutes(10))
        createSession("pc-myhost", LocalDateTime.now().minusMinutes(5))

        val active = getActiveSessions()
        assertEquals(2, active.size)
        assertTrue(active.any { it.deviceId == "android" })
        assertTrue(active.any { it.deviceId == "pc-myhost" })
    }

    @Test
    fun `get active sessions with deviceId filters to that device`() {
        createSession("android", LocalDateTime.now().minusMinutes(10))
        createSession("pc-myhost", LocalDateTime.now().minusMinutes(5))

        val androidOnly = getActiveSessions("android")
        assertEquals(1, androidOnly.size)
        assertEquals("android", androidOnly[0].deviceId)

        val pcOnly = getActiveSessions("pc-myhost")
        assertEquals(1, pcOnly.size)
        assertEquals("pc-myhost", pcOnly[0].deviceId)
    }

    @Test
    fun `completed sessions do not appear in active sessions`() {
        val session = createSession("android", LocalDateTime.now().minusMinutes(30))
        updateSession(session.id, UpdateSessionRequest(endTime = LocalDateTime.now()))

        val active = getActiveSessions()
        assertEquals(0, active.size)
    }

    // ── 3. activeElapsedSeconds Computation ──

    @Test
    fun `active running session has correct activeElapsedSeconds`() {
        val startTime = LocalDateTime.now().minusMinutes(10)
        val session = createSession("pc-myhost", startTime)

        val fetched = getSession(session.id)
        // Should be approximately 600 seconds (10 minutes), allow 5s tolerance
        assertTrue(fetched.activeElapsedSeconds in 595..605,
            "Expected ~600s, got ${fetched.activeElapsedSeconds}")
    }

    @Test
    fun `paused session has correct activeElapsedSeconds frozen at pause time`() {
        val startTime = LocalDateTime.of(2026, 2, 8, 10, 0, 0)
        val pausedAt = LocalDateTime.of(2026, 2, 8, 10, 10, 0) // 10 min later
        val session = createSession("pc-myhost", startTime)

        val paused = updateSession(session.id, UpdateSessionRequest(
            isPaused = true,
            pausedAt = pausedAt
        ))

        // Should be exactly 600 seconds (10 minutes between start and pause)
        assertEquals(600, paused.activeElapsedSeconds)
    }

    @Test
    fun `paused session with prior paused time has correct activeElapsedSeconds`() {
        val startTime = LocalDateTime.of(2026, 2, 8, 10, 0, 0)
        val pausedAt = LocalDateTime.of(2026, 2, 8, 10, 20, 0) // 20 min later
        val session = createSession("pc-myhost", startTime)

        // First pause was 5 min (300s already accumulated)
        val paused = updateSession(session.id, UpdateSessionRequest(
            isPaused = true,
            pausedAt = pausedAt,
            totalPausedSeconds = 300
        ))

        // 20 min total - 5 min paused = 15 min = 900s
        assertEquals(900, paused.activeElapsedSeconds)
    }

    @Test
    fun `resumed session has correct activeElapsedSeconds`() {
        val startTime = LocalDateTime.now().minusMinutes(20)
        val session = createSession("pc-myhost", startTime)

        // Pause
        updateSession(session.id, UpdateSessionRequest(
            isPaused = true,
            pausedAt = LocalDateTime.now().minusMinutes(5)
        ))

        // Resume after 5 min pause (300s accumulated)
        val resumed = updateSession(session.id, UpdateSessionRequest(
            isPaused = false,
            totalPausedSeconds = 300
        ))

        assertFalse(resumed.isPaused)
        assertNull(resumed.pausedAt)
        assertEquals(300, resumed.totalPausedSeconds)
        // ~20 min total - 5 min paused = ~15 min = ~900s
        assertTrue(resumed.activeElapsedSeconds in 895..905,
            "Expected ~900s, got ${resumed.activeElapsedSeconds}")
    }

    @Test
    fun `completed session activeElapsedSeconds uses stored durationMinutes`() {
        val startTime = LocalDateTime.of(2026, 2, 8, 10, 0, 0)
        val endTime = LocalDateTime.of(2026, 2, 8, 11, 0, 0)
        val session = createSession("pc-myhost", startTime)

        val completed = updateSession(session.id, UpdateSessionRequest(
            endTime = endTime
        ))

        // 60 minutes of active time = 3600 seconds
        assertEquals(3600, completed.activeElapsedSeconds)
        assertEquals(60, completed.durationMinutes)
    }

    // ── 4. Pause / Resume Lifecycle ──

    @Test
    fun `pause sets isPaused and pausedAt`() {
        val session = createSession("android", LocalDateTime.now().minusMinutes(10))
        val pausedAt = LocalDateTime.now()

        val paused = updateSession(session.id, UpdateSessionRequest(
            isPaused = true,
            pausedAt = pausedAt
        ))

        assertTrue(paused.isPaused)
        assertNotNull(paused.pausedAt)
    }

    @Test
    fun `resume clears isPaused and pausedAt, updates totalPausedSeconds`() {
        val session = createSession("android", LocalDateTime.now().minusMinutes(10))

        // Pause
        updateSession(session.id, UpdateSessionRequest(
            isPaused = true,
            pausedAt = LocalDateTime.now().minusMinutes(2)
        ))

        // Resume with 120s of pause
        val resumed = updateSession(session.id, UpdateSessionRequest(
            isPaused = false,
            totalPausedSeconds = 120
        ))

        assertFalse(resumed.isPaused)
        assertNull(resumed.pausedAt)
        assertEquals(120, resumed.totalPausedSeconds)
    }

    // ── 5. Stop / End Session ──

    @Test
    fun `stop session sets endTime and calculates duration`() {
        val startTime = LocalDateTime.of(2026, 2, 8, 10, 0, 0)
        val endTime = LocalDateTime.of(2026, 2, 8, 10, 45, 0)
        val session = createSession("pc-myhost", startTime)

        val stopped = updateSession(session.id, UpdateSessionRequest(
            endTime = endTime,
            isPaused = false
        ))

        assertEquals(endTime, stopped.endTime)
        assertEquals(45, stopped.durationMinutes)
        assertFalse(stopped.isPaused)
    }

    @Test
    fun `stop session with paused time subtracts from duration`() {
        val startTime = LocalDateTime.of(2026, 2, 8, 10, 0, 0)
        val endTime = LocalDateTime.of(2026, 2, 8, 11, 0, 0)
        val session = createSession("pc-myhost", startTime)

        val stopped = updateSession(session.id, UpdateSessionRequest(
            endTime = endTime,
            isPaused = false,
            totalPausedSeconds = 600  // 10 min paused
        ))

        // 60 min total - 10 min paused = 50 min active
        assertEquals(50, stopped.durationMinutes)
    }

    @Test
    fun `stop session without durationMinutes lets API compute it`() {
        val startTime = LocalDateTime.of(2026, 2, 8, 10, 0, 0)
        val endTime = LocalDateTime.of(2026, 2, 8, 10, 45, 0)
        val session = createSession("pc-myhost", startTime)

        // Client sends only endTime + isPaused, no durationMinutes
        val stopped = updateSession(session.id, UpdateSessionRequest(
            endTime = endTime,
            isPaused = false
        ))

        // API should auto-compute: 45 min total - 0 min paused = 45 min
        assertEquals(45, stopped.durationMinutes)
        assertEquals(endTime, stopped.endTime)
        assertFalse(stopped.isPaused)
    }

    @Test
    fun `stop session without durationMinutes with paused time lets API compute it`() {
        val startTime = LocalDateTime.of(2026, 2, 8, 10, 0, 0)
        val endTime = LocalDateTime.of(2026, 2, 8, 11, 0, 0)
        val session = createSession("pc-myhost", startTime)

        // Client sends endTime + totalPausedSeconds but no durationMinutes
        val stopped = updateSession(session.id, UpdateSessionRequest(
            endTime = endTime,
            isPaused = false,
            totalPausedSeconds = 600  // 10 min paused
        ))

        // API should auto-compute: 60 min total - 10 min paused = 50 min
        assertEquals(50, stopped.durationMinutes)
    }

    @Test
    fun `delete session removes it`() {
        val session = createSession("android", LocalDateTime.now())

        mockMvc.perform(
            delete("/api/sessions/${session.id}").header("X-API-Key", apiKey)
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            get("/api/sessions/${session.id}").header("X-API-Key", apiKey)
        ).andExpect(status().isNotFound)
    }

    // ── 6. Edit Start Time (Remote Editing) ──

    @Test
    fun `update startTime changes session start and recalculates elapsed`() {
        val originalStart = LocalDateTime.now().minusMinutes(30)
        val session = createSession("android", originalStart)

        val newStart = LocalDateTime.now().minusMinutes(60) // moved back 30 min
        val updated = updateSession(session.id, UpdateSessionRequest(startTime = newStart))

        assertEquals(newStart, updated.startTime)
        // Elapsed should now be ~60 min = ~3600s
        assertTrue(updated.activeElapsedSeconds in 3595..3605,
            "Expected ~3600s, got ${updated.activeElapsedSeconds}")
    }

    // ── 7. Cross-Device Scenario ──

    @Test
    fun `android creates session, PC reads it as active`() {
        val session = createSession("android", LocalDateTime.now().minusMinutes(5))

        // PC queries all active sessions (no deviceId filter)
        val allActive = getActiveSessions()
        assertTrue(allActive.any { it.id == session.id && it.deviceId == "android" })

        // PC filters for non-PC sessions — should find the android session
        val nonPc = allActive.filter { it.deviceId != "pc-myhost" }
        assertEquals(1, nonPc.size)
    }

    @Test
    fun `PC creates session, android reads and pauses it`() {
        val startTime = LocalDateTime.of(2026, 2, 8, 10, 0, 0)
        val session = createSession("pc-myhost", startTime)

        // Android polls and finds the PC session
        val active = getActiveSessions().filter { it.deviceId != "android" }
        assertEquals(1, active.size)
        assertEquals(session.id, active[0].id)

        // Android pauses the PC session
        val pauseTime = LocalDateTime.of(2026, 2, 8, 10, 15, 0)
        val paused = updateSession(session.id, UpdateSessionRequest(
            isPaused = true,
            pausedAt = pauseTime
        ))

        assertTrue(paused.isPaused)
        assertEquals(900, paused.activeElapsedSeconds) // 15 min

        // PC reads back and sees it paused
        val pcSession = getSession(session.id)
        assertTrue(pcSession.isPaused)
        assertEquals(900, pcSession.activeElapsedSeconds)
    }

    @Test
    fun `PC creates session, android stops it`() {
        val startTime = LocalDateTime.of(2026, 2, 8, 10, 0, 0)
        val session = createSession("pc-myhost", startTime)

        // Android stops the session
        val endTime = LocalDateTime.of(2026, 2, 8, 10, 30, 0)
        val stopped = updateSession(session.id, UpdateSessionRequest(
            endTime = endTime,
            isPaused = false
        ))

        assertEquals(30, stopped.durationMinutes)

        // Session no longer in active list
        val active = getActiveSessions()
        assertTrue(active.none { it.id == session.id })
    }

    @Test
    fun `full cross-device lifecycle - create, pause, resume, stop`() {
        // PC creates
        val startTime = LocalDateTime.of(2026, 2, 8, 14, 0, 0)
        val session = createSession("pc-myhost", startTime)

        // Android pauses at 14:30
        val pause1 = updateSession(session.id, UpdateSessionRequest(
            isPaused = true,
            pausedAt = LocalDateTime.of(2026, 2, 8, 14, 30, 0)
        ))
        assertEquals(1800, pause1.activeElapsedSeconds) // 30 min

        // Android resumes at 14:35 (5 min pause = 300s)
        val resume1 = updateSession(session.id, UpdateSessionRequest(
            isPaused = false,
            totalPausedSeconds = 300
        ))
        assertFalse(resume1.isPaused)
        assertEquals(300, resume1.totalPausedSeconds)

        // Android pauses again at 15:00
        val pause2 = updateSession(session.id, UpdateSessionRequest(
            isPaused = true,
            pausedAt = LocalDateTime.of(2026, 2, 8, 15, 0, 0)
        ))
        // 60 min total - 5 min paused = 55 min = 3300s
        assertEquals(3300, pause2.activeElapsedSeconds)

        // Android stops at 15:05 (5 more min paused = 600s total)
        val stopped = updateSession(session.id, UpdateSessionRequest(
            endTime = LocalDateTime.of(2026, 2, 8, 15, 5, 0),
            isPaused = false,
            totalPausedSeconds = 600
        ))
        // 65 min total - 10 min paused = 55 min
        assertEquals(55, stopped.durationMinutes)

        // No more active sessions
        assertEquals(0, getActiveSessions().size)
    }

    // ── 8. Minimum Duration Enforcement ──

    @Test
    fun `ending session under 15 minutes deletes it`() {
        val startTime = LocalDateTime.of(2026, 2, 8, 10, 0, 0)
        val endTime = LocalDateTime.of(2026, 2, 8, 10, 10, 0) // 10 min
        val session = createSession("android", startTime)

        updateSession(session.id, UpdateSessionRequest(
            endTime = endTime,
            isPaused = false
        ))

        // Session should be gone
        mockMvc.perform(
            get("/api/sessions/${session.id}").header("X-API-Key", apiKey)
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `ending session at exactly 15 minutes keeps it`() {
        val startTime = LocalDateTime.of(2026, 2, 8, 10, 0, 0)
        val endTime = LocalDateTime.of(2026, 2, 8, 10, 15, 0) // exactly 15 min
        val session = createSession("android", startTime)

        val stopped = updateSession(session.id, UpdateSessionRequest(
            endTime = endTime,
            isPaused = false
        ))

        assertEquals(15, stopped.durationMinutes)

        // Session should still exist
        val fetched = getSession(session.id)
        assertEquals(session.id, fetched.id)
        assertEquals(15, fetched.durationMinutes)
    }

    @Test
    fun `paused session with active time below 15 minutes is discarded`() {
        val startTime = LocalDateTime.of(2026, 2, 8, 10, 0, 0)
        val endTime = LocalDateTime.of(2026, 2, 8, 10, 30, 0) // 30 min total
        val session = createSession("android", startTime)

        // 18 min paused = 1080s, so active = 30 - 18 = 12 min
        updateSession(session.id, UpdateSessionRequest(
            endTime = endTime,
            isPaused = false,
            totalPausedSeconds = 1080
        ))

        // Session should be gone (12 active min < 15)
        mockMvc.perform(
            get("/api/sessions/${session.id}").header("X-API-Key", apiKey)
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `recent sessions exclude short sessions`() {
        // Create a long session that will be kept
        val longStart = LocalDateTime.of(2026, 2, 8, 9, 0, 0)
        val longEnd = LocalDateTime.of(2026, 2, 8, 9, 30, 0)
        val longSession = createSession("android", longStart)
        updateSession(longSession.id, UpdateSessionRequest(endTime = longEnd, isPaused = false))

        // Create a short session that will be discarded
        val shortStart = LocalDateTime.of(2026, 2, 8, 10, 0, 0)
        val shortEnd = LocalDateTime.of(2026, 2, 8, 10, 5, 0)
        val shortSession = createSession("android", shortStart)
        updateSession(shortSession.id, UpdateSessionRequest(endTime = shortEnd, isPaused = false))

        // GET /api/sessions should only contain the long session
        val result = mockMvc.perform(
            get("/api/sessions").header("X-API-Key", apiKey)
        ).andExpect(status().isOk).andReturn()
        val sessions: List<SessionResponse> = mapper.readValue(result.response.contentAsString)

        assertEquals(1, sessions.size)
        assertEquals(longSession.id, sessions[0].id)
    }

    // ── 9. API Key Security ──

    @Test
    fun `requests without API key are rejected`() {
        mockMvc.perform(get("/api/sessions/active"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `requests with wrong API key are rejected`() {
        mockMvc.perform(
            get("/api/sessions/active").header("X-API-Key", "wrong-key")
        ).andExpect(status().isUnauthorized)
    }

    // ── 10. Edit Start Time + Resume (atomic) ──

    @Test
    fun `edit start time and resume in single request updates all fields and broadcasts resumed state`() {
        val now = LocalDateTime.now()
        val session = createSession("android", now.minusMinutes(30))

        // Pause it 10 min ago
        updateSession(session.id, UpdateSessionRequest(
            isPaused = true,
            pausedAt = now.minusMinutes(10)
        ))

        // Reset spy to only capture the edit+resume broadcast
        reset(sseConnectionManager)

        // Atomic edit+resume: move start time back to 40 min ago AND resume
        val newStartTime = now.minusMinutes(40)
        val updated = updateSession(session.id, UpdateSessionRequest(
            startTime = newStartTime,
            isPaused = false
        ))

        // Assert API response
        assertFalse(updated.isPaused)
        assertNull(updated.pausedAt)
        assertEquals(newStartTime, updated.startTime)
        // totalPausedSeconds should be auto-computed: ~600s (10 min of pause)
        assertTrue(updated.totalPausedSeconds in 595..605,
            "Expected ~600s paused, got ${updated.totalPausedSeconds}")
        // activeElapsedSeconds = 40min - ~10min paused = ~30min = ~1800s
        assertTrue(updated.activeElapsedSeconds in 1795..1810,
            "Expected ~1800s active, got ${updated.activeElapsedSeconds}")

        // Verify SSE broadcast with resumed state
        verify(sseConnectionManager).broadcast(
            eq("session.updated"),
            argThat<Any> { this is SessionResponse && !this.isPaused && this.pausedAt == null }
        )
    }

    @Test
    fun `resume without start time change broadcasts resumed state via SSE`() {
        val now = LocalDateTime.now()
        val originalStart = now.minusMinutes(20)
        val session = createSession("android", originalStart)

        // Pause 5 min ago
        updateSession(session.id, UpdateSessionRequest(
            isPaused = true,
            pausedAt = now.minusMinutes(5)
        ))

        reset(sseConnectionManager)

        // Resume only, no start time change
        val resumed = updateSession(session.id, UpdateSessionRequest(
            isPaused = false
        ))

        assertFalse(resumed.isPaused)
        assertNull(resumed.pausedAt)
        assertTrue(
            ChronoUnit.SECONDS.between(originalStart, resumed.startTime) == 0L,
            "startTime should be unchanged but was ${resumed.startTime}"
        )
        // Auto-computed paused seconds: ~300s (5 min)
        assertTrue(resumed.totalPausedSeconds in 295..305,
            "Expected ~300s paused, got ${resumed.totalPausedSeconds}")

        // Verify SSE broadcast with resumed state
        verify(sseConnectionManager).broadcast(
            eq("session.updated"),
            argThat<Any> { this is SessionResponse && !this.isPaused }
        )
    }

    @Test
    fun `edit start time while paused without resuming keeps session paused`() {
        val now = LocalDateTime.now()
        val session = createSession("android", now.minusMinutes(30))
        val pausedAt = now.minusMinutes(10)

        // Pause it
        updateSession(session.id, UpdateSessionRequest(
            isPaused = true,
            pausedAt = pausedAt
        ))

        reset(sseConnectionManager)

        // Edit start time only — no isPaused field, should stay paused
        val newStartTime = now.minusMinutes(40)
        val updated = updateSession(session.id, UpdateSessionRequest(
            startTime = newStartTime
        ))

        assertTrue(updated.isPaused, "Session should still be paused")
        assertEquals(newStartTime, updated.startTime)
        assertNotNull(updated.pausedAt)

        // Verify SSE broadcast still shows paused state
        verify(sseConnectionManager).broadcast(
            eq("session.updated"),
            argThat<Any> { this is SessionResponse && this.isPaused }
        )
    }

    @Test
    fun `SSE event after edit+resume contains correct activeElapsedSeconds`() {
        val now = LocalDateTime.now()
        val session = createSession("android", now.minusMinutes(20))

        // Pause 5 min ago
        updateSession(session.id, UpdateSessionRequest(
            isPaused = true,
            pausedAt = now.minusMinutes(5)
        ))

        reset(sseConnectionManager)

        // Edit start time to 25 min ago + resume
        val newStartTime = now.minusMinutes(25)
        val updated = updateSession(session.id, UpdateSessionRequest(
            startTime = newStartTime,
            isPaused = false
        ))

        // activeElapsedSeconds = 25min - ~5min paused = ~20min = ~1200s
        assertTrue(updated.activeElapsedSeconds in 1195..1210,
            "Expected ~1200s active, got ${updated.activeElapsedSeconds}")

        // GET the session again to confirm persisted state matches
        val fetched = getSession(session.id)
        assertFalse(fetched.isPaused)
        assertNull(fetched.pausedAt)
        assertTrue(
            ChronoUnit.SECONDS.between(newStartTime, fetched.startTime) == 0L,
            "startTime should match but was ${fetched.startTime}"
        )
        assertTrue(fetched.activeElapsedSeconds in 1195..1210,
            "Persisted activeElapsedSeconds expected ~1200s, got ${fetched.activeElapsedSeconds}")

        // Verify SSE broadcast has correct activeElapsedSeconds
        verify(sseConnectionManager).broadcast(
            eq("session.updated"),
            argThat<Any> {
                this is SessionResponse &&
                    !this.isPaused &&
                    this.activeElapsedSeconds in 1195..1210
            }
        )
    }
}
