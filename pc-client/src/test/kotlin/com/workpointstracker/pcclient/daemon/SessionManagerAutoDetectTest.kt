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

class SessionManagerAutoDetectTest {

    private lateinit var apiClient: ApiClient
    private lateinit var windowMonitor: WindowMonitor
    private lateinit var idleDetector: IdleDetector
    private lateinit var config: Config
    private lateinit var sessionManager: SessionManager

    private val trackedWindow = WindowInfo(wmClass = "jetbrains-idea", title = "IntelliJ IDEA")

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

    /** Set up mocks so the active window is a tracked app */
    private fun mockTrackedApp() {
        every { windowMonitor.getActiveWindow() } returns trackedWindow
        every { windowMonitor.isTrackedApp(trackedWindow) } returns true
        every { windowMonitor.getTrackedAppName(trackedWindow) } returns "IntelliJ"
    }

    /** Set up mocks so the active window is NOT a tracked app */
    private fun mockNonTrackedApp() {
        val window = WindowInfo(wmClass = "Brave-browser", title = "YouTube")
        every { windowMonitor.getActiveWindow() } returns window
        every { windowMonitor.isTrackedApp(window) } returns false
    }

    /** Set up mocks so there's no active window */
    private fun mockNoWindow() {
        every { windowMonitor.getActiveWindow() } returns null
    }

    /** Helper: start a session so we're in ACTIVE state */
    private fun startSession(sessionId: Long = 1L) {
        every { apiClient.createSession(any(), any(), any()) } returns SessionDto(
            id = sessionId,
            deviceId = "pc-test",
            startTime = LocalDateTime.now(),
            type = SessionType.SIDE_WORK
        )
        // Need getSession for syncFromApi during tick
        every { apiClient.getSession(sessionId) } returns SessionDto(
            id = sessionId,
            deviceId = "pc-test",
            startTime = LocalDateTime.now(),
            isPaused = false,
            type = SessionType.SIDE_WORK
        )
        mockTrackedApp()
        sessionManager.tick()
        assertEquals(DaemonState.ACTIVE, sessionManager.state)
    }

    // ── IDLE → ACTIVE: Auto-Start ──

    @Test
    fun `auto-starts session when tracked app is active and user is not idle`() {
        every { apiClient.createSession(any(), any(), any()) } returns SessionDto(
            id = 10,
            deviceId = "pc-test",
            startTime = LocalDateTime.now(),
            type = SessionType.SIDE_WORK
        )
        mockTrackedApp()
        every { idleDetector.getIdleSeconds() } returns 0

        sessionManager.tick()

        assertEquals(DaemonState.ACTIVE, sessionManager.state)
        assertEquals(10L, sessionManager.currentSessionId)
        verify { apiClient.createSession(any(), any(), any()) }
    }

    @Test
    fun `does not auto-start when tracked app is active but user is idle`() {
        mockTrackedApp()
        every { idleDetector.getIdleSeconds() } returns (config.idle_timeout_seconds + 10).toLong()

        sessionManager.tick()

        assertEquals(DaemonState.IDLE, sessionManager.state)
        verify(exactly = 0) { apiClient.createSession(any(), any(), any()) }
    }

    @Test
    fun `does not auto-start when no tracked app is active`() {
        mockNonTrackedApp()
        every { idleDetector.getIdleSeconds() } returns 0

        sessionManager.tick()

        assertEquals(DaemonState.IDLE, sessionManager.state)
        verify(exactly = 0) { apiClient.createSession(any(), any(), any()) }
    }

    @Test
    fun `does not auto-start when no window is active`() {
        mockNoWindow()
        every { idleDetector.getIdleSeconds() } returns 0

        sessionManager.tick()

        assertEquals(DaemonState.IDLE, sessionManager.state)
    }

    // ── ACTIVE → PAUSED: Idle Detection ──

    @Test
    fun `auto-pauses when user goes idle during active session`() {
        startSession(sessionId = 42)

        // User goes idle
        every { idleDetector.getIdleSeconds() } returns (config.idle_timeout_seconds + 1).toLong()

        // syncFromApi returns no change
        every { apiClient.getSession(42) } returns SessionDto(
            id = 42, isPaused = false,
            startTime = LocalDateTime.now().minusMinutes(10),
            type = SessionType.SIDE_WORK
        )

        sessionManager.tick()

        assertEquals(DaemonState.PAUSED, sessionManager.state)
        assertNotNull(sessionManager.pausedSince)
        verify {
            apiClient.updateSession(42, match { updates ->
                updates["isPaused"] == true
            })
        }
    }

    @Test
    fun `does not auto-pause when user is just under idle threshold`() {
        startSession(sessionId = 42)

        // Just under threshold
        every { idleDetector.getIdleSeconds() } returns (config.idle_timeout_seconds - 1).toLong()
        mockTrackedApp()

        every { apiClient.getSession(42) } returns SessionDto(
            id = 42, isPaused = false,
            startTime = LocalDateTime.now().minusMinutes(10),
            type = SessionType.SIDE_WORK
        )

        sessionManager.tick()

        assertEquals(DaemonState.ACTIVE, sessionManager.state)
    }

    @Test
    fun `idle pause takes priority over tracked app being open`() {
        startSession(sessionId = 42)

        // Tracked app is open BUT user is idle
        mockTrackedApp()
        every { idleDetector.getIdleSeconds() } returns (config.idle_timeout_seconds + 60).toLong()

        every { apiClient.getSession(42) } returns SessionDto(
            id = 42, isPaused = false,
            startTime = LocalDateTime.now().minusMinutes(10),
            type = SessionType.SIDE_WORK
        )

        sessionManager.tick()

        assertEquals(DaemonState.PAUSED, sessionManager.state)
    }

    // ── ACTIVE → PAUSED: Non-Tracked App ──

    @Test
    fun `does not immediately pause when switching to non-tracked app`() {
        startSession(sessionId = 42)

        // Switch to non-tracked app (first tick — starts grace period)
        mockNonTrackedApp()
        every { idleDetector.getIdleSeconds() } returns 0

        every { apiClient.getSession(42) } returns SessionDto(
            id = 42, isPaused = false,
            startTime = LocalDateTime.now().minusMinutes(10),
            type = SessionType.SIDE_WORK
        )

        sessionManager.tick()

        // Should still be active during grace period
        assertEquals(DaemonState.ACTIVE, sessionManager.state)
    }

    // ── PAUSED → ACTIVE: Auto-Resume ──

    @Test
    fun `auto-resumes when tracked app becomes active while paused`() {
        startSession(sessionId = 42)

        // Pause via idle
        every { idleDetector.getIdleSeconds() } returns (config.idle_timeout_seconds + 1).toLong()
        every { apiClient.getSession(42) } returns SessionDto(
            id = 42, isPaused = false,
            startTime = LocalDateTime.now().minusMinutes(10),
            type = SessionType.SIDE_WORK
        )
        sessionManager.tick()
        assertEquals(DaemonState.PAUSED, sessionManager.state)

        // Now user comes back: tracked app active, not idle
        mockTrackedApp()
        every { idleDetector.getIdleSeconds() } returns 0
        every { apiClient.getSession(42) } returns SessionDto(
            id = 42, isPaused = true,
            pausedAt = LocalDateTime.now().minusSeconds(30),
            startTime = LocalDateTime.now().minusMinutes(10),
            type = SessionType.SIDE_WORK
        )

        sessionManager.tick()

        assertEquals(DaemonState.ACTIVE, sessionManager.state)
        assertNull(sessionManager.pausedSince)
        verify {
            apiClient.updateSession(42, match { updates ->
                updates["isPaused"] == false
            })
        }
    }

    @Test
    fun `does not auto-resume when user is still idle even with tracked app`() {
        startSession(sessionId = 42)

        // Pause via idle
        every { idleDetector.getIdleSeconds() } returns (config.idle_timeout_seconds + 1).toLong()
        every { apiClient.getSession(42) } returns SessionDto(
            id = 42, isPaused = false,
            startTime = LocalDateTime.now().minusMinutes(10),
            type = SessionType.SIDE_WORK
        )
        sessionManager.tick()
        assertEquals(DaemonState.PAUSED, sessionManager.state)

        // Tracked app is open but user is still idle
        mockTrackedApp()
        every { idleDetector.getIdleSeconds() } returns (config.idle_timeout_seconds + 60).toLong()
        every { apiClient.getSession(42) } returns SessionDto(
            id = 42, isPaused = true,
            pausedAt = LocalDateTime.now().minusSeconds(30),
            startTime = LocalDateTime.now().minusMinutes(10),
            type = SessionType.SIDE_WORK
        )

        sessionManager.tick()

        assertEquals(DaemonState.PAUSED, sessionManager.state)
    }

    @Test
    fun `does not auto-resume when non-tracked app is active`() {
        startSession(sessionId = 42)

        // Pause via idle
        every { idleDetector.getIdleSeconds() } returns (config.idle_timeout_seconds + 1).toLong()
        every { apiClient.getSession(42) } returns SessionDto(
            id = 42, isPaused = false,
            startTime = LocalDateTime.now().minusMinutes(10),
            type = SessionType.SIDE_WORK
        )
        sessionManager.tick()
        assertEquals(DaemonState.PAUSED, sessionManager.state)

        // Non-tracked app active, not idle
        mockNonTrackedApp()
        every { idleDetector.getIdleSeconds() } returns 0
        every { apiClient.getSession(42) } returns SessionDto(
            id = 42, isPaused = true,
            pausedAt = LocalDateTime.now().minusSeconds(30),
            startTime = LocalDateTime.now().minusMinutes(10),
            type = SessionType.SIDE_WORK
        )

        sessionManager.tick()

        assertEquals(DaemonState.PAUSED, sessionManager.state)
    }

    // ── PAUSED → IDLE: Session Ends After Long Pause ──

    @Test
    fun `session ends after being paused too long`() {
        startSession(sessionId = 42)

        // Pause
        every { idleDetector.getIdleSeconds() } returns (config.idle_timeout_seconds + 1).toLong()
        every { apiClient.getSession(42) } returns SessionDto(
            id = 42, isPaused = false,
            startTime = LocalDateTime.now().minusMinutes(10),
            type = SessionType.SIDE_WORK
        )
        sessionManager.tick()
        assertEquals(DaemonState.PAUSED, sessionManager.state)

        // Simulate paused for longer than end_after_paused_seconds by
        // directly setting pausedSince far in the past via reflection
        val field = SessionManager::class.java.getDeclaredField("pausedSince")
        field.isAccessible = true
        field.set(sessionManager, LocalDateTime.now().minusSeconds(config.end_after_paused_seconds.toLong() + 60))

        // Mock syncFromApi to return paused state (matching daemon state)
        every { apiClient.getSession(42) } returns SessionDto(
            id = 42, isPaused = true,
            pausedAt = LocalDateTime.now().minusSeconds(config.end_after_paused_seconds.toLong() + 60),
            startTime = LocalDateTime.now().minusMinutes(60),
            type = SessionType.SIDE_WORK
        )
        mockNoWindow()

        sessionManager.tick()

        assertEquals(DaemonState.IDLE, sessionManager.state)
        assertNull(sessionManager.currentSessionId)
    }

    @Test
    fun `session does not end when paused time is under threshold`() {
        startSession(sessionId = 42)

        // Pause
        every { idleDetector.getIdleSeconds() } returns (config.idle_timeout_seconds + 1).toLong()
        every { apiClient.getSession(42) } returns SessionDto(
            id = 42, isPaused = false,
            startTime = LocalDateTime.now().minusMinutes(10),
            type = SessionType.SIDE_WORK
        )
        sessionManager.tick()
        assertEquals(DaemonState.PAUSED, sessionManager.state)

        // Still paused but not long enough to end
        mockNoWindow()
        every { idleDetector.getIdleSeconds() } returns 0
        every { apiClient.getSession(42) } returns SessionDto(
            id = 42, isPaused = true,
            pausedAt = LocalDateTime.now().minusSeconds(10),
            startTime = LocalDateTime.now().minusMinutes(10),
            type = SessionType.SIDE_WORK
        )

        sessionManager.tick()

        // Still paused, not ended
        assertEquals(DaemonState.PAUSED, sessionManager.state)
        assertNotNull(sessionManager.currentSessionId)
    }

    // ── Full Auto-Detection Lifecycle ──

    @Test
    fun `full lifecycle - idle start, idle pause, resume, end`() {
        // 1. Start: tracked app + not idle → ACTIVE
        every { apiClient.createSession(any(), any(), any()) } returns SessionDto(
            id = 50, deviceId = "pc-test",
            startTime = LocalDateTime.now(),
            type = SessionType.SIDE_WORK
        )
        mockTrackedApp()
        every { idleDetector.getIdleSeconds() } returns 0

        sessionManager.tick()
        assertEquals(DaemonState.ACTIVE, sessionManager.state)
        assertEquals(50L, sessionManager.currentSessionId)

        // 2. Idle → PAUSED
        every { idleDetector.getIdleSeconds() } returns (config.idle_timeout_seconds + 10).toLong()
        every { apiClient.getSession(50) } returns SessionDto(
            id = 50, isPaused = false,
            startTime = LocalDateTime.now().minusMinutes(10),
            type = SessionType.SIDE_WORK
        )

        sessionManager.tick()
        assertEquals(DaemonState.PAUSED, sessionManager.state)

        // 3. Come back: tracked + not idle → ACTIVE
        every { idleDetector.getIdleSeconds() } returns 0
        mockTrackedApp()
        every { apiClient.getSession(50) } returns SessionDto(
            id = 50, isPaused = true,
            pausedAt = LocalDateTime.now().minusSeconds(20),
            startTime = LocalDateTime.now().minusMinutes(10),
            type = SessionType.SIDE_WORK
        )

        sessionManager.tick()
        assertEquals(DaemonState.ACTIVE, sessionManager.state)
        // totalPausedSeconds may be 0 in tests since pause duration is negligible
        assertTrue(sessionManager.totalPausedSeconds >= 0)

        // 4. Idle again → PAUSED
        every { idleDetector.getIdleSeconds() } returns (config.idle_timeout_seconds + 10).toLong()
        every { apiClient.getSession(50) } returns SessionDto(
            id = 50, isPaused = false,
            startTime = LocalDateTime.now().minusMinutes(15),
            type = SessionType.SIDE_WORK
        )

        sessionManager.tick()
        assertEquals(DaemonState.PAUSED, sessionManager.state)

        // 5. Paused too long → END
        val pausedField = SessionManager::class.java.getDeclaredField("pausedSince")
        pausedField.isAccessible = true
        pausedField.set(sessionManager, LocalDateTime.now().minusSeconds(config.end_after_paused_seconds.toLong() + 60))

        every { apiClient.getSession(50) } returns SessionDto(
            id = 50, isPaused = true,
            pausedAt = LocalDateTime.now().minusSeconds(config.end_after_paused_seconds.toLong() + 60),
            startTime = LocalDateTime.now().minusMinutes(60),
            type = SessionType.SIDE_WORK
        )
        mockNoWindow()

        sessionManager.tick()
        assertEquals(DaemonState.IDLE, sessionManager.state)
        assertNull(sessionManager.currentSessionId)
    }
}
