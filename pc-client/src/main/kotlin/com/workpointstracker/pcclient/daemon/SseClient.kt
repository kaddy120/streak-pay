package com.workpointstracker.pcclient.daemon

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.workpointstracker.pcclient.api.SessionDto
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

sealed class SseEvent {
    data class SessionUpdated(val session: SessionDto) : SseEvent()
    data class SessionDeleted(val id: Long) : SseEvent()
    data class DaemonCommand(val deviceId: String, val action: String) : SseEvent()
    data object Heartbeat : SseEvent()
}

class SseClient(
    private val baseUrl: String,
    private val apiKey: String
) {
    private val logger = LoggerFactory.getLogger(SseClient::class.java)
    private val clientId = "pc-${UUID.randomUUID()}"

    private val mapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val httpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    private var eventSource: EventSource? = null
    private val connected = AtomicBoolean(false)
    private var reconnectAttempt = 0
    private val maxBackoff = 30_000L

    val eventQueue = ConcurrentLinkedQueue<SseEvent>()

    val isConnected: Boolean get() = connected.get()

    fun connect() {
        doConnect()
    }

    fun disconnect() {
        eventSource?.cancel()
        eventSource = null
        connected.set(false)
    }

    private fun doConnect() {
        eventSource?.cancel()

        val url = "${baseUrl.trimEnd('/')}/api/events?clientId=$clientId"
        val request = Request.Builder()
            .url(url)
            .header("X-API-Key", apiKey)
            .build()

        val factory = EventSources.createFactory(httpClient)
        eventSource = factory.newEventSource(request, object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                logger.info("SSE connected")
                reconnectAttempt = 0
                connected.set(true)
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                try {
                    val event = parseEvent(type, data)
                    if (event != null) {
                        eventQueue.add(event)
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to parse SSE event: {}", type, e)
                }
            }

            override fun onClosed(eventSource: EventSource) {
                logger.info("SSE closed")
                connected.set(false)
                scheduleReconnect()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                logger.warn("SSE failure: {}", t?.message)
                connected.set(false)
                scheduleReconnect()
            }
        })
    }

    private fun parseEvent(type: String?, data: String): SseEvent? {
        return when (type) {
            "session.updated" -> SseEvent.SessionUpdated(mapper.readValue(data, SessionDto::class.java))
            "session.deleted" -> {
                val node = mapper.readTree(data)
                SseEvent.SessionDeleted(node.get("id").asLong())
            }
            "daemon.command" -> {
                val node = mapper.readTree(data)
                SseEvent.DaemonCommand(
                    deviceId = node.get("deviceId").asText(),
                    action = node.get("action").asText()
                )
            }
            "heartbeat" -> SseEvent.Heartbeat
            else -> null
        }
    }

    private fun scheduleReconnect() {
        reconnectAttempt++
        val delay = (1000L * (1L shl (reconnectAttempt - 1).coerceAtMost(5))).coerceAtMost(maxBackoff)
        logger.info("Reconnecting in {}ms (attempt {})", delay, reconnectAttempt)
        Thread {
            try {
                Thread.sleep(delay)
                doConnect()
            } catch (_: InterruptedException) {
                // Shutting down
            }
        }.apply {
            isDaemon = true
            start()
        }
    }
}
