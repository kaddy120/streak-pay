package com.workpointstracker.pcclient.daemon

import com.workpointstracker.pcclient.api.ApiClient
import com.workpointstracker.pcclient.api.SessionDto
import com.workpointstracker.pcclient.config.Config
import com.workpointstracker.shared.PointsCalculator
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

enum class DaemonState {
    IDLE,
    ACTIVE,
    PAUSED
}

class SessionManager(
    private val config: Config,
    private val apiClient: ApiClient,
    private val windowMonitor: WindowMonitor,
    private val idleDetector: IdleDetector,
    private val sseClient: SseClient? = null
) {
    private val logger = LoggerFactory.getLogger(SessionManager::class.java)
    private val pointsCalculator = PointsCalculator()
    val deviceId = "pc-${getHostname()}"

    var state: DaemonState = DaemonState.IDLE
        private set
    var currentSessionId: Long? = null
        private set
    var currentAppName: String? = null
        private set
    var sessionStartTime: LocalDateTime? = null
        private set
    var pausedSince: LocalDateTime? = null
        private set
    private var nonTrackedSince: LocalDateTime? = null
    var totalPausedSeconds: Long = 0
        private set
    var activeElapsedSeconds: Long = 0
        private set

    fun tick() {
        // Process SSE events or fall back to API polling for remote state changes
        val remoteChanged = processRemoteChanges()

        // Skip auto-detection if a remote device just changed the state
        if (remoteChanged) return

        val window = windowMonitor.getActiveWindow()
        val isTracked = window != null && windowMonitor.isTrackedApp(window)
        val appName = window?.let { windowMonitor.getTrackedAppName(it) }
        val idleSeconds = idleDetector.getIdleSeconds()
        val isIdle = idleSeconds >= config.idle_timeout_seconds

        when (state) {
            DaemonState.IDLE -> handleIdle(isTracked, isIdle, appName)
            DaemonState.ACTIVE -> handleActive(isTracked, isIdle, appName)
            DaemonState.PAUSED -> handlePaused(isTracked, isIdle, appName)
        }
    }

    /**
     * Processes remote state changes via SSE events when connected,
     * falls back to API polling when SSE is disconnected.
     */
    private fun processRemoteChanges(): Boolean {
        if (sseClient != null && sseClient.isConnected) {
            return processSseEvents()
        }
        return syncFromApi()
    }

    /**
     * Drains SSE event queue and applies any relevant session changes.
     * DaemonCommand events are processed regardless of whether there's an active session.
     */
    private fun processSseEvents(): Boolean {
        var remoteChanged = false
        val sessionId = currentSessionId

        while (true) {
            val event = sseClient?.eventQueue?.poll() ?: break
            when (event) {
                is SseEvent.DaemonCommand -> {
                    if (event.deviceId == deviceId) {
                        logger.info("Received remote command: {}", event.action)
                        when (event.action) {
                            "start" -> manualStart()
                            "pause" -> manualPause()
                            "resume" -> manualResume()
                            "stop" -> manualStop()
                        }
                        remoteChanged = true
                    }
                }
                is SseEvent.SessionUpdated -> {
                    if (sessionId != null && event.session.id == sessionId) {
                        remoteChanged = applyRemoteSession(event.session) || remoteChanged
                    }
                }
                is SseEvent.SessionDeleted -> {
                    if (sessionId != null && event.id == sessionId) {
                        logger.info("Session {} deleted remotely via SSE", sessionId)
                        resetState()
                        remoteChanged = true
                    }
                }
                is SseEvent.Heartbeat -> { /* keepalive */ }
            }
        }
        return remoteChanged
    }

    /**
     * Applies a remote session state to the local daemon.
     * Returns true if state changed.
     */
    private fun applyRemoteSession(apiSession: com.workpointstracker.pcclient.api.SessionDto): Boolean {
        val sessionId = apiSession.id
        if (apiSession.endTime != null) {
            logger.info("Session {} ended remotely, resetting local state", sessionId)
            resetState()
            return true
        }
        if (apiSession.isPaused && state == DaemonState.ACTIVE) {
            logger.info("Session {} paused remotely, syncing local state", sessionId)
            pausedSince = apiSession.pausedAt ?: LocalDateTime.now()
            totalPausedSeconds = apiSession.totalPausedSeconds
            activeElapsedSeconds = apiSession.activeElapsedSeconds
            sessionStartTime = apiSession.startTime
            nonTrackedSince = null
            state = DaemonState.PAUSED
            return true
        }
        if (!apiSession.isPaused && state == DaemonState.PAUSED) {
            logger.info("Session {} resumed remotely, syncing local state", sessionId)
            totalPausedSeconds = apiSession.totalPausedSeconds
            activeElapsedSeconds = apiSession.activeElapsedSeconds
            sessionStartTime = apiSession.startTime
            pausedSince = null
            nonTrackedSince = null
            state = DaemonState.ACTIVE
            return true
        }
        // Sync activeElapsedSeconds even when state hasn't changed (e.g. startTime edited remotely)
        if (apiSession.activeElapsedSeconds != activeElapsedSeconds) {
            logger.info("Session {} activeElapsedSeconds changed remotely: {} -> {}", sessionId, activeElapsedSeconds, apiSession.activeElapsedSeconds)
            activeElapsedSeconds = apiSession.activeElapsedSeconds
            totalPausedSeconds = apiSession.totalPausedSeconds
            sessionStartTime = apiSession.startTime
            return true
        }
        return false
    }

    /**
     * Fallback: Checks the API for remote state changes via polling.
     * Used when SSE is disconnected.
     */
    private fun syncFromApi(): Boolean {
        val sessionId = currentSessionId ?: return false
        try {
            val apiSession = apiClient.getSession(sessionId) ?: run {
                logger.info("Session {} deleted remotely, resetting local state", sessionId)
                resetState()
                return true
            }
            return applyRemoteSession(apiSession)
        } catch (e: Exception) {
            logger.debug("API sync check failed: {}", e.message)
        }
        return false
    }

    private fun handleIdle(isTracked: Boolean, isIdle: Boolean, appName: String?) {
        if (isTracked && !isIdle) {
            startSession(appName)
        }
    }

    private fun handleActive(isTracked: Boolean, isIdle: Boolean, appName: String?) {
        if (isTracked) {
            currentAppName = appName
            nonTrackedSince = null
        }

        if (isIdle) {
            pauseSession("User idle for ${config.idle_timeout_seconds}s")
            return
        }

        if (!isTracked) {
            if (nonTrackedSince == null) {
                nonTrackedSince = LocalDateTime.now()
            }
            val nonTrackedSeconds = ChronoUnit.SECONDS.between(nonTrackedSince, LocalDateTime.now())
            if (nonTrackedSeconds >= config.non_tracked_grace_seconds) {
                pauseSession("Non-tracked app for ${config.non_tracked_grace_seconds}s")
            }
        }
    }

    private fun handlePaused(isTracked: Boolean, isIdle: Boolean, appName: String?) {
        // Check if paused too long → end session
        val pausedSeconds = pausedSince?.let {
            ChronoUnit.SECONDS.between(it, LocalDateTime.now())
        } ?: 0

        if (pausedSeconds >= config.end_after_paused_seconds) {
            endSession()
            return
        }

        // Resume if active on tracked app
        if (isTracked && !isIdle) {
            resumeSession(appName)
        }
    }

    private fun startSession(appName: String?) {
        val now = LocalDateTime.now()
        val sessionType = pointsCalculator.determineSessionType(now)

        logger.info("Starting session: type={}, app={}", sessionType, appName)

        val session = apiClient.createSession(deviceId, now, sessionType)
        if (session != null) {
            currentSessionId = session.id
            currentAppName = appName
            sessionStartTime = now
            activeElapsedSeconds = session.activeElapsedSeconds
            nonTrackedSince = null
            state = DaemonState.ACTIVE
            logger.info("Session started: id={}", session.id)
        } else {
            logger.error("Failed to create session via API")
        }
    }

    private fun pauseSession(reason: String) {
        val sessionId = currentSessionId ?: return
        val now = LocalDateTime.now()

        logger.info("Pausing session {}: {}", sessionId, reason)

        // Compute elapsed so /api/status returns the correct value immediately
        sessionStartTime?.let {
            val totalSeconds = ChronoUnit.SECONDS.between(it, now)
            activeElapsedSeconds = (totalSeconds - totalPausedSeconds).coerceAtLeast(0)
        }

        apiClient.updateSession(sessionId, mapOf(
            "isPaused" to true,
            "pausedAt" to now.toString()
        ))

        pausedSince = now
        nonTrackedSince = null
        state = DaemonState.PAUSED
    }

    private fun resumeSession(appName: String?) {
        val sessionId = currentSessionId ?: return
        val pauseStart = pausedSince ?: return
        val now = LocalDateTime.now()
        val thisPauseSeconds = ChronoUnit.SECONDS.between(pauseStart, now)
        totalPausedSeconds += thisPauseSeconds

        logger.info("Resuming session {}: paused for {}s (total paused: {}s), app={}", sessionId, thisPauseSeconds, totalPausedSeconds, appName)

        apiClient.updateSession(sessionId, mapOf(
            "isPaused" to false,
            "pausedAt" to null,
            "totalPausedSeconds" to totalPausedSeconds
        ))

        currentAppName = appName
        pausedSince = null
        nonTrackedSince = null
        state = DaemonState.ACTIVE
    }

    private fun endSession() {
        val sessionId = currentSessionId ?: return
        val now = LocalDateTime.now()
        // Include current pause duration if ending while paused
        val finalPausedSeconds = totalPausedSeconds +
            (pausedSince?.let { ChronoUnit.SECONDS.between(it, now) } ?: 0)

        logger.info("Ending session {}", sessionId)
        apiClient.updateSession(sessionId, mapOf(
            "endTime" to now.toString(),
            "totalPausedSeconds" to finalPausedSeconds,
            "isPaused" to false
        ))

        resetState()
    }

    fun forceStop() {
        if (state != DaemonState.IDLE) {
            endSession()
        }
    }

    fun manualStart() {
        if (state != DaemonState.IDLE) return
        startSession("Manual")
    }

    fun manualPause() {
        if (state != DaemonState.ACTIVE) return
        pauseSession("Manual pause from dashboard")
    }

    fun manualResume() {
        if (state != DaemonState.PAUSED) return
        resumeSession(currentAppName)
    }

    fun manualStop() {
        if (state == DaemonState.IDLE) return
        endSession()
    }

    private fun resetState() {
        state = DaemonState.IDLE
        currentSessionId = null
        currentAppName = null
        sessionStartTime = null
        pausedSince = null
        nonTrackedSince = null
        totalPausedSeconds = 0
        activeElapsedSeconds = 0
    }

    fun sendHeartbeat() {
        try {
            val elapsed = computeLocalElapsed()
            apiClient.sendHeartbeat(deviceId, state.name, currentAppName, currentSessionId, elapsed, totalPausedSeconds)
        } catch (e: Exception) {
            logger.debug("Heartbeat failed: {}", e.message)
        }
    }

    private fun computeLocalElapsed(): Long {
        return when (state) {
            DaemonState.ACTIVE -> {
                sessionStartTime?.let {
                    val total = ChronoUnit.SECONDS.between(it, LocalDateTime.now())
                    (total - totalPausedSeconds).coerceAtLeast(0)
                } ?: 0L
            }
            DaemonState.PAUSED -> activeElapsedSeconds
            DaemonState.IDLE -> 0L
        }
    }

    fun recoverCrashedSessions() {
        val activeSessions = apiClient.getActiveSessions(deviceId)
        if (activeSessions.isNotEmpty()) {
            logger.warn("Found {} orphaned sessions from this device, closing them", activeSessions.size)
            val now = LocalDateTime.now()
            for (session in activeSessions) {
                apiClient.updateSession(session.id, mapOf(
                    "endTime" to now.toString(),
                    "isPaused" to false
                ))
            }
        }
    }

    private fun getHostname(): String {
        return try {
            java.net.InetAddress.getLocalHost().hostName
        } catch (e: Exception) {
            "unknown"
        }
    }
}
