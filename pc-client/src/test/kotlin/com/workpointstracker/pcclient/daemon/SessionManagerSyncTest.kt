package com.workpointstracker.pcclient.daemon

import com.workpointstracker.pcclient.api.ApiClient
import com.workpointstracker.pcclient.api.SessionDto
import com.workpointstracker.pcclient.config.Config
import com.workpointstracker.shared.models.SessionType
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SessionManagerSyncTest {

    private lateinit var apiClient: ApiClient
    private lateinit var windowMonitor: WindowMonitor
    private lateinit var idleDetector: IdleDetector
    private lateinit var config: Config
    private lateinit var sessionManager: SessionManager

    private val now = LocalDateTime.of(2026, 2, 8, 14, 0, 0)

    @BeforeEach
    fun setup() {
        apiClient = mockk(relaxed = true)
        windowMonitor = mockk(relaxed = true)
        idleDetector = mockk(relaxed = true)
        config = Config()

        // Default: no tracked window, not idle
        every { windowMonitor.getActiveWindow() } returns null
        every { windowMonitor.isTrackedApp(any()) } returns false
        every { idleDetector.getIdleSeconds() } returns 0

        sessionManager = SessionManager(config, apiClient, windowMonitor, idleDetector)
    }

    /** Helper: simulate the daemon having an active session */
    private fun simulateActiveSession(sessionId: Long = 1L, startTime: LocalDateTime = now.minusMinutes(30)) {
        // Use manualStart to set up session state, but we need to mock the API response
        every { apiClient.createSession(any(), any(), any()) } returns SessionDto(
            id = sessionId,
            deviceId = "pc-test",
            startTime = startTime,
            type = SessionType.SIDE_WORK
        )
        sessionManager.manualStart()
        assertEquals(DaemonState.ACTIVE, sessionManager.state)
        assertEquals(sessionId, sessionManager.currentSessionId)
    }

    /** Helper: simulate the daemon being in paused state */
    private fun simulatePausedSession(sessionId: Long = 1L, startTime: LocalDateTime = now.minusMinutes(30)) {
        simulateActiveSession(sessionId, startTime)
        // Pause it
        every { apiClient.updateSession(any(), any()) } returns SessionDto(id = sessionId)
        sessionManager.manualPause()
        assertEquals(DaemonState.PAUSED, sessionManager.state)
    }

    // ── syncFromApi: Session Deleted Remotely ──

    @Test
    fun `syncFromApi resets state when session deleted remotely`() {
        simulateActiveSession(sessionId = 42)

        // API returns null (session not found / deleted)
        every { apiClient.getSession(42) } returns null

        sessionManager.tick()

        assertEquals(DaemonState.IDLE, sessionManager.state)
        assertNull(sessionManager.currentSessionId)
    }

    // ── syncFromApi: Session Ended Remotely ──

    @Test
    fun `syncFromApi resets state when session ended remotely`() {
        simulateActiveSession(sessionId = 42)

        every { apiClient.getSession(42) } returns SessionDto(
            id = 42,
            deviceId = "pc-test",
            startTime = now.minusMinutes(30),
            endTime = now, // ended
            type = SessionType.SIDE_WORK
        )

        sessionManager.tick()

        assertEquals(DaemonState.IDLE, sessionManager.state)
        assertNull(sessionManager.currentSessionId)
    }

    // ── syncFromApi: Remote Pause ──

    @Test
    fun `syncFromApi pauses daemon when API says paused but daemon is active`() {
        simulateActiveSession(sessionId = 42)

        val pausedAt = now.minusMinutes(2)
        every { apiClient.getSession(42) } returns SessionDto(
            id = 42,
            deviceId = "pc-test",
            startTime = now.minusMinutes(30),
            isPaused = true,
            pausedAt = pausedAt,
            totalPausedSeconds = 0,
            type = SessionType.SIDE_WORK
        )

        sessionManager.tick()

        assertEquals(DaemonState.PAUSED, sessionManager.state)
        assertEquals(pausedAt, sessionManager.pausedSince)
    }

    @Test
    fun `syncFromApi remote pause skips auto-detection for that tick`() {
        simulateActiveSession(sessionId = 42)

        // User has a tracked app open (would normally prevent pause or auto-resume)
        val window = WindowInfo(wmClass = "jetbrains-idea", title = "IntelliJ IDEA")
        every { windowMonitor.getActiveWindow() } returns window
        every { windowMonitor.isTrackedApp(window) } returns true
        every { windowMonitor.getTrackedAppName(window) } returns "IntelliJ"
        every { idleDetector.getIdleSeconds() } returns 0

        val pausedAt = now.minusMinutes(1)
        every { apiClient.getSession(42) } returns SessionDto(
            id = 42,
            deviceId = "pc-test",
            startTime = now.minusMinutes(30),
            isPaused = true,
            pausedAt = pausedAt,
            totalPausedSeconds = 0,
            type = SessionType.SIDE_WORK
        )

        sessionManager.tick()

        // Should stay PAUSED even though a tracked app is open
        assertEquals(DaemonState.PAUSED, sessionManager.state)

        // No resume API call should have been made (auto-detection skipped)
        verify(exactly = 0) {
            apiClient.updateSession(42, match { updates ->
                updates["isPaused"] == false
            })
        }
    }

    // ── syncFromApi: Remote Resume ──

    @Test
    fun `syncFromApi resumes daemon when API says running but daemon is paused`() {
        simulatePausedSession(sessionId = 42)

        every { apiClient.getSession(42) } returns SessionDto(
            id = 42,
            deviceId = "pc-test",
            startTime = now.minusMinutes(30),
            isPaused = false,
            pausedAt = null,
            totalPausedSeconds = 300,
            type = SessionType.SIDE_WORK
        )

        sessionManager.tick()

        assertEquals(DaemonState.ACTIVE, sessionManager.state)
        assertEquals(300, sessionManager.totalPausedSeconds)
        assertNull(sessionManager.pausedSince)
    }

    // ── syncFromApi: No Change ──

    @Test
    fun `syncFromApi does nothing when states match - both active`() {
        simulateActiveSession(sessionId = 42)

        every { apiClient.getSession(42) } returns SessionDto(
            id = 42,
            deviceId = "pc-test",
            startTime = now.minusMinutes(30),
            isPaused = false,
            type = SessionType.SIDE_WORK
        )

        sessionManager.tick()

        // Still active, no state change
        assertEquals(DaemonState.ACTIVE, sessionManager.state)
    }

    @Test
    fun `syncFromApi does nothing when states match - both paused`() {
        simulatePausedSession(sessionId = 42)

        every { apiClient.getSession(42) } returns SessionDto(
            id = 42,
            deviceId = "pc-test",
            startTime = now.minusMinutes(30),
            isPaused = true,
            pausedAt = now.minusMinutes(5),
            type = SessionType.SIDE_WORK
        )

        sessionManager.tick()

        // Still paused
        assertEquals(DaemonState.PAUSED, sessionManager.state)
    }

    @Test
    fun `syncFromApi skipped when no active session`() {
        assertEquals(DaemonState.IDLE, sessionManager.state)

        sessionManager.tick()

        // No API call made
        verify(exactly = 0) { apiClient.getSession(any()) }
    }

    // ── syncFromApi: API Error Handling ──

    @Test
    fun `syncFromApi handles API error gracefully`() {
        simulateActiveSession(sessionId = 42)

        every { apiClient.getSession(42) } throws RuntimeException("Network error")

        // Should not crash
        sessionManager.tick()

        // State unchanged
        assertEquals(DaemonState.ACTIVE, sessionManager.state)
        assertEquals(42L, sessionManager.currentSessionId)
    }

    // ── Auto-Detection Still Works When No Remote Change ──

    @Test
    fun `auto-detection runs normally when no remote change detected`() {
        simulateActiveSession(sessionId = 42)

        // API says same state (active) — no remote change
        every { apiClient.getSession(42) } returns SessionDto(
            id = 42,
            deviceId = "pc-test",
            startTime = now.minusMinutes(30),
            isPaused = false,
            type = SessionType.SIDE_WORK
        )

        // User is idle → should auto-pause
        every { idleDetector.getIdleSeconds() } returns (config.idle_timeout_seconds + 10).toLong()

        sessionManager.tick()

        // Auto-detection ran and paused due to idle
        assertEquals(DaemonState.PAUSED, sessionManager.state)

        // API was called to pause
        verify {
            apiClient.updateSession(42, match { updates ->
                updates["isPaused"] == true
            })
        }
    }

    // ── Manual Control Still Works ──

    @Test
    fun `manualStart creates session via API`() {
        every { apiClient.createSession(any(), any(), any()) } returns SessionDto(
            id = 99,
            deviceId = "pc-test",
            startTime = now,
            type = SessionType.SIDE_WORK
        )

        sessionManager.manualStart()

        assertEquals(DaemonState.ACTIVE, sessionManager.state)
        assertEquals(99L, sessionManager.currentSessionId)
        verify { apiClient.createSession(any(), any(), any()) }
    }

    @Test
    fun `manualStop ends session via API - short session gets deleted`() {
        simulateActiveSession(sessionId = 42)

        sessionManager.manualStop()

        assertEquals(DaemonState.IDLE, sessionManager.state)
        // Session started moments ago (<15 min), so endSession() discards it
        verify { apiClient.deleteSession(42) }
    }

    // ── Sequential Remote Operations ──

    @Test
    fun `full remote lifecycle - pause then resume then stop`() {
        simulateActiveSession(sessionId = 42)

        // Tick 1: remote pause detected
        val pausedAt = now.minusMinutes(5)
        every { apiClient.getSession(42) } returns SessionDto(
            id = 42,
            deviceId = "pc-test",
            startTime = now.minusMinutes(30),
            isPaused = true,
            pausedAt = pausedAt,
            totalPausedSeconds = 0,
            type = SessionType.SIDE_WORK
        )
        sessionManager.tick()
        assertEquals(DaemonState.PAUSED, sessionManager.state)

        // Tick 2: remote resume detected
        every { apiClient.getSession(42) } returns SessionDto(
            id = 42,
            deviceId = "pc-test",
            startTime = now.minusMinutes(30),
            isPaused = false,
            pausedAt = null,
            totalPausedSeconds = 300,
            type = SessionType.SIDE_WORK
        )
        sessionManager.tick()
        assertEquals(DaemonState.ACTIVE, sessionManager.state)
        assertEquals(300, sessionManager.totalPausedSeconds)

        // Tick 3: remote stop detected
        every { apiClient.getSession(42) } returns SessionDto(
            id = 42,
            deviceId = "pc-test",
            startTime = now.minusMinutes(30),
            endTime = now,
            type = SessionType.SIDE_WORK
        )
        sessionManager.tick()
        assertEquals(DaemonState.IDLE, sessionManager.state)
        assertNull(sessionManager.currentSessionId)
    }
}
