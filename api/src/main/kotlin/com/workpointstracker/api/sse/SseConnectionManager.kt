package com.workpointstracker.api.sse

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class SseConnectionManager(
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(SseConnectionManager::class.java)
    private val emitters = ConcurrentHashMap<String, SseEmitter>()

    fun register(clientId: String): SseEmitter {
        val emitter = SseEmitter(0L) // no timeout
        emitters[clientId]?.complete() // close previous connection from same client
        emitters[clientId] = emitter

        emitter.onCompletion { emitters.remove(clientId) }
        emitter.onTimeout { emitters.remove(clientId) }
        emitter.onError { emitters.remove(clientId) }

        logger.info("SSE client connected: {}", clientId)
        return emitter
    }

    fun broadcast(eventName: String, data: Any) {
        val json = objectMapper.writeValueAsString(data)
        val toRemove = mutableListOf<String>()

        emitters.forEach { (clientId, emitter) ->
            try {
                emitter.send(
                    SseEmitter.event()
                        .name(eventName)
                        .data(json)
                )
            } catch (e: Exception) {
                toRemove.add(clientId)
            }
        }

        toRemove.forEach { emitters.remove(it) }
    }

    @Scheduled(fixedRate = 30_000)
    fun heartbeat() {
        broadcast("heartbeat", mapOf("ts" to Instant.now().toString()))
    }
}
