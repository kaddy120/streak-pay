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
import java.util.concurrent.ConcurrentLinkedQueue

class SessionManagerSseTest {

    private lateinit var apiClient: ApiClient
    private lateinit var windowMonitor: WindowMonitor
    private lateinit var idleDetector: IdleDetector
    private lateinit var config: Config
    private lateinit var sseClient: SseClient
    private lateinit var sessionManager: SessionManager

    private val now = LocalDateTime.of(2026, 2, 8, 14, 0, 0)

    @BeforeEach
    fun setup() {
        apiClient = mockk(relaxed = true)
        windowMonitor = mockk(relaxed = true)
        idleDetector = mockk(relaxed = true)
        config = Config()
        sseClient = mockk(relaxed = true)

        // SSE connected by default with a real event queue
        val realQueue = ConcurrentLinkedQueue<SseEvent>()
        every { sseClient.isConnected } returns true
        every { sseClient.eventQueue } returns realQueue

        // Default: no tracked window, not idle
        every { windowMonitor.getActiveWindow() } returns null
        every { windowMonitor.isTrackedApp(any()) } returns false
        every { idleDetector.getIdleSeconds() } returns 0

        sessionManager = SessionManager(config, apiClient, windowMonitor, idleDetector, sseClient)
    }

    /** Helper: simulate the daemon having an active session */
    private fun simulateActiveSession(sessionId: Long = 1L, startTime: LocalDateTime = now.minusMinutes(30)) {
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
        every { apiClient.updateSession(any(), any()) } returns SessionDto(id = sessionId)
        sessionManager.manualPause()
        assertEquals(DaemonState.PAUSED, sessionManager.state)
    }

    // ── SSE Event Processing ──

    @Test
    fun `SSE pause event pauses active session and populates activeElapsedSeconds`() {
        simulateActiveSession(sessionId = 42)

        val pausedAt = now.minusMinutes(1)
        sseClient.eventQueue.add(SseEvent.SessionUpdated(SessionDto(
            id = 42,
            deviceId = "pc-test",
            startTime = now.minusMinutes(30),
            isPaused = true,
            pausedAt = pausedAt,
            totalPausedSeconds = 0,
            activeElapsedSeconds = 1740,
            type = SessionType.SIDE_WORK
        )))

        sessionManager.tick()

        assertEquals(DaemonState.PAUSED, sessionManager.state)
        assertEquals(pausedAt, sessionManager.pausedSince)
        assertEquals(1740, sessionManager.activeElapsedSeconds)
    }

    @Test
    fun `SSE resume event resumes paused session and populates activeElapsedSeconds`() {
        simulatePausedSession(sessionId = 42)

        sseClient.eventQueue.add(SseEvent.SessionUpdated(SessionDto(
            id = 42,
            deviceId = "pc-test",
            startTime = now.minusMinutes(30),
            isPaused = false,
            pausedAt = null,
            totalPausedSeconds = 300,
            activeElapsedSeconds = 1500,
            type = SessionType.SIDE_WORK
        )))

        sessionManager.tick()

        assertEquals(DaemonState.ACTIVE, sessionManager.state)
        assertEquals(300, sessionManager.totalPausedSeconds)
        assertNull(sessionManager.pausedSince)
        assertEquals(1500, sessionManager.activeElapsedSeconds)
    }

    @Test
    fun `SSE end event resets to idle and zeroes activeElapsedSeconds`() {
        simulateActiveSession(sessionId = 42)

        sseClient.eventQueue.add(SseEvent.SessionUpdated(SessionDto(
            id = 42,
            deviceId = "pc-test",
            startTime = now.minusMinutes(30),
            endTime = now,
            type = SessionType.SIDE_WORK
        )))

        sessionManager.tick()

        assertEquals(DaemonState.IDLE, sessionManager.state)
        assertNull(sessionManager.currentSessionId)
        assertEquals(0, sessionManager.activeElapsedSeconds)
    }

    @Test
    fun `SSE delete event resets matching session`() {
        simulateActiveSession(sessionId = 42)

        sseClient.eventQueue.add(SseEvent.SessionDeleted(42))

        sessionManager.tick()

        assertEquals(DaemonState.IDLE, sessionManager.state)
        assertNull(sessionManager.currentSessionId)
    }

    @Test
    fun `SSE delete event ignores non-matching session`() {
        simulateActiveSession(sessionId = 42)

        sseClient.eventQueue.add(SseEvent.SessionDeleted(99))

        sessionManager.tick()

        assertEquals(DaemonState.ACTIVE, sessionManager.state)
        assertEquals(42L, sessionManager.currentSessionId)
    }

    @Test
    fun `SSE heartbeat is ignored`() {
        simulateActiveSession(sessionId = 42)

        sseClient.eventQueue.add(SseEvent.Heartbeat)

        sessionManager.tick()

        assertEquals(DaemonState.ACTIVE, sessionManager.state)
        assertEquals(42L, sessionManager.currentSessionId)
    }

    @Test
    fun `SSE ignores events for other sessions`() {
        simulateActiveSession(sessionId = 42)

        sseClient.eventQueue.add(SseEvent.SessionUpdated(SessionDto(
            id = 99,
            deviceId = "android",
            startTime = now.minusMinutes(10),
            isPaused = true,
            pausedAt = now.minusMinutes(1),
            type = SessionType.SIDE_WORK
        )))

        sessionManager.tick()

        assertEquals(DaemonState.ACTIVE, sessionManager.state)
    }

    // ── SSE vs API Polling ──

    @Test
    fun `uses SSE instead of API polling when connected`() {
        simulateActiveSession(sessionId = 42)

        // Tick with empty SSE queue - should NOT fall back to API polling
        sessionManager.tick()

        verify(exactly = 0) { apiClient.getSession(any()) }
    }

    @Test
    fun `falls back to API polling when disconnected`() {
        // Disconnect SSE
        every { sseClient.isConnected } returns false

        simulateActiveSession(sessionId = 42)

        // API returns same state (no change)
        every { apiClient.getSession(42) } returns SessionDto(
            id = 42,
            deviceId = "pc-test",
            startTime = now.minusMinutes(30),
            isPaused = false,
            type = SessionType.SIDE_WORK
        )

        sessionManager.tick()

        verify(exactly = 1) { apiClient.getSession(42) }
    }

    // ── Auto-Detection Integration with SSE ──

    @Test
    fun `auto-detection runs after SSE finds no changes`() {
        simulateActiveSession(sessionId = 42)

        // SSE connected, empty queue → no remote change
        // User is idle → should auto-pause
        every { idleDetector.getIdleSeconds() } returns (config.idle_timeout_seconds + 10).toLong()

        sessionManager.tick()

        assertEquals(DaemonState.PAUSED, sessionManager.state)
        verify {
            apiClient.updateSession(42, match { updates ->
                updates["isPaused"] == true
            })
        }
    }

    @Test
    fun `auto-detection skipped after SSE remote change`() {
        simulateActiveSession(sessionId = 42)

        // SSE delivers pause event
        sseClient.eventQueue.add(SseEvent.SessionUpdated(SessionDto(
            id = 42,
            deviceId = "pc-test",
            startTime = now.minusMinutes(30),
            isPaused = true,
            pausedAt = now.minusMinutes(1),
            totalPausedSeconds = 0,
            type = SessionType.SIDE_WORK
        )))

        // User has tracked app open (would normally trigger auto-resume in PAUSED state)
        val window = WindowInfo(wmClass = "jetbrains-idea", title = "IntelliJ IDEA")
        every { windowMonitor.getActiveWindow() } returns window
        every { windowMonitor.isTrackedApp(window) } returns true
        every { windowMonitor.getTrackedAppName(window) } returns "IntelliJ"
        every { idleDetector.getIdleSeconds() } returns 0

        sessionManager.tick()

        // Should stay PAUSED (remote change took priority, auto-detection skipped)
        assertEquals(DaemonState.PAUSED, sessionManager.state)

        // No resume API call should have been made
        verify(exactly = 0) {
            apiClient.updateSession(42, match { updates ->
                updates["isPaused"] == false
            })
        }
    }

    // ── Full SSE Lifecycle ──

    @Test
    fun `full SSE lifecycle - pause, resume, stop via events with activeElapsedSeconds`() {
        simulateActiveSession(sessionId = 42)

        // Tick 1: SSE pause
        sseClient.eventQueue.add(SseEvent.SessionUpdated(SessionDto(
            id = 42,
            deviceId = "pc-test",
            startTime = now.minusMinutes(30),
            isPaused = true,
            pausedAt = now.minusMinutes(5),
            totalPausedSeconds = 0,
            activeElapsedSeconds = 1500,
            type = SessionType.SIDE_WORK
        )))
        sessionManager.tick()
        assertEquals(DaemonState.PAUSED, sessionManager.state)
        assertEquals(1500, sessionManager.activeElapsedSeconds)

        // Tick 2: SSE resume
        sseClient.eventQueue.add(SseEvent.SessionUpdated(SessionDto(
            id = 42,
            deviceId = "pc-test",
            startTime = now.minusMinutes(30),
            isPaused = false,
            pausedAt = null,
            totalPausedSeconds = 300,
            activeElapsedSeconds = 1500,
            type = SessionType.SIDE_WORK
        )))
        sessionManager.tick()
        assertEquals(DaemonState.ACTIVE, sessionManager.state)
        assertEquals(300, sessionManager.totalPausedSeconds)
        assertEquals(1500, sessionManager.activeElapsedSeconds)

        // Tick 3: SSE stop
        sseClient.eventQueue.add(SseEvent.SessionUpdated(SessionDto(
            id = 42,
            deviceId = "pc-test",
            startTime = now.minusMinutes(30),
            endTime = now,
            type = SessionType.SIDE_WORK
        )))
        sessionManager.tick()
        assertEquals(DaemonState.IDLE, sessionManager.state)
        assertNull(sessionManager.currentSessionId)
        assertEquals(0, sessionManager.activeElapsedSeconds)

        // No API polling should have occurred during any of these ticks
        verify(exactly = 0) { apiClient.getSession(any()) }
    }
}
