package com.workpointstracker.pcclient.daemon

import com.workpointstracker.pcclient.api.ApiClient
import com.workpointstracker.pcclient.config.Config
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

class Daemon(private val config: Config) {

    private val logger = LoggerFactory.getLogger(Daemon::class.java)
    private val running = AtomicBoolean(false)

    private val apiClient = ApiClient(config.api.base_url, config.api.api_key)
    private val sseClient = SseClient(config.api.base_url, config.api.api_key)
    private val windowMonitor = WindowMonitor(config.tracked_apps)
    private val idleDetector = IdleDetector()
    private val sessionManager = SessionManager(config, apiClient, windowMonitor, idleDetector, sseClient)

    val state: DaemonState get() = sessionManager.state
    val currentAppName: String? get() = sessionManager.currentAppName
    val currentSessionId: Long? get() = sessionManager.currentSessionId
    val activeElapsedSeconds: Long get() = sessionManager.activeElapsedSeconds
    val sessionStartTime: java.time.LocalDateTime? get() = sessionManager.sessionStartTime
    val totalPausedSeconds: Long get() = sessionManager.totalPausedSeconds
    val pausedSince: java.time.LocalDateTime? get() = sessionManager.pausedSince

    fun start() {
        if (running.getAndSet(true)) {
            logger.warn("Daemon already running")
            return
        }

        logger.info("Work Points Daemon starting...")
        logger.info("API: {}", config.api.base_url)
        logger.info("Poll interval: {}s", config.poll_interval_seconds)
        logger.info("Idle timeout: {}s", config.idle_timeout_seconds)
        logger.info("Tracked apps: {}", config.tracked_apps.map { it.name })

        // Connect SSE for real-time remote session updates
        sseClient.connect()

        // Recover any orphaned sessions from previous crash
        sessionManager.recoverCrashedSessions()

        // Install shutdown hook
        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info("Shutdown signal received")
            stop()
        })

        // Main poll loop
        while (running.get()) {
            try {
                sessionManager.tick()
            } catch (e: Exception) {
                logger.error("Error in main loop: {}", e.message, e)
            }
            Thread.sleep(config.poll_interval_seconds * 1000L)
        }

        logger.info("Daemon stopped")
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        logger.info("Stopping daemon...")
        sseClient.disconnect()
        sessionManager.forceStop()
    }

    fun manualStart() = sessionManager.manualStart()
    fun manualPause() = sessionManager.manualPause()
    fun manualResume() = sessionManager.manualResume()
    fun manualStop() = sessionManager.manualStop()
}
