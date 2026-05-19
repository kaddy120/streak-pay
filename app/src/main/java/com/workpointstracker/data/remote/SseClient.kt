package com.workpointstracker.data.remote

import android.util.Log
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class ConnectionState { CONNECTED, DISCONNECTED, RECONNECTING }

sealed class SseEvent {
    data class SessionCreated(val session: SessionResponse) : SseEvent()
    data class SessionUpdated(val session: SessionResponse) : SseEvent()
    data class SessionDeleted(val id: Long) : SseEvent()
    data class StatsUpdated(val totalPoints: Double, val streak: StreakResponse) : SseEvent()
    data class WishItemCreated(val item: WishItemResponse) : SseEvent()
    data class WishItemUpdated(val item: WishItemResponse) : SseEvent()
    data class WishItemDeleted(val id: Long) : SseEvent()
    data object SettingsUpdated : SseEvent()
    data object Heartbeat : SseEvent()
}

class SseClient(
    private val baseUrl: String,
    private val apiKey: String
) {
    private val tag = "SseClient"
    private val clientId = "android-${UUID.randomUUID()}"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val mapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private val _events = MutableSharedFlow<SseEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SseEvent> = _events

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private var eventSource: EventSource? = null
    private var reconnectAttempt = 0
    private val maxBackoff = 30_000L

    private val httpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // infinite for SSE
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    fun connect() {
        doConnect()
    }

    fun disconnect() {
        eventSource?.cancel()
        eventSource = null
        _connectionState.value = ConnectionState.DISCONNECTED
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
                Log.i(tag, "SSE connected")
                reconnectAttempt = 0
                _connectionState.value = ConnectionState.CONNECTED
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                try {
                    val event = parseEvent(type, data)
                    if (event != null) {
                        _events.tryEmit(event)
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Failed to parse SSE event: $type", e)
                }
            }

            override fun onClosed(eventSource: EventSource) {
                Log.i(tag, "SSE closed")
                _connectionState.value = ConnectionState.DISCONNECTED
                scheduleReconnect()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                Log.w(tag, "SSE failure: ${t?.message}")
                _connectionState.value = ConnectionState.DISCONNECTED
                scheduleReconnect()
            }
        })
    }

    private fun parseEvent(type: String?, data: String): SseEvent? {
        return when (type) {
            "session.created" -> SseEvent.SessionCreated(mapper.readValue(data, SessionResponse::class.java))
            "session.updated" -> SseEvent.SessionUpdated(mapper.readValue(data, SessionResponse::class.java))
            "session.deleted" -> {
                val node = mapper.readTree(data)
                SseEvent.SessionDeleted(node.get("id").asLong())
            }
            "stats.updated" -> {
                val node = mapper.readTree(data)
                SseEvent.StatsUpdated(
                    totalPoints = node.get("totalPoints").asDouble(),
                    streak = mapper.treeToValue(node.get("streak"), StreakResponse::class.java)
                )
            }
            "wishitem.created" -> SseEvent.WishItemCreated(mapper.readValue(data, WishItemResponse::class.java))
            "wishitem.updated" -> SseEvent.WishItemUpdated(mapper.readValue(data, WishItemResponse::class.java))
            "wishitem.deleted" -> {
                val node = mapper.readTree(data)
                SseEvent.WishItemDeleted(node.get("id").asLong())
            }
            "settings.updated" -> SseEvent.SettingsUpdated
            "heartbeat" -> SseEvent.Heartbeat
            else -> null
        }
    }

    private fun scheduleReconnect() {
        _connectionState.value = ConnectionState.RECONNECTING
        reconnectAttempt++
        val delay = (1000L * (1L shl (reconnectAttempt - 1).coerceAtMost(5))).coerceAtMost(maxBackoff)
        Log.i(tag, "Reconnecting in ${delay}ms (attempt $reconnectAttempt)")
        scope.launch {
            delay(delay)
            doConnect()
        }
    }
}
