package com.workpointstracker.api.controller

import com.workpointstracker.api.sse.SseConnectionManager
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api")
class SseController(
    private val sseConnectionManager: SseConnectionManager
) {
    @GetMapping("/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun events(@RequestParam clientId: String): SseEmitter {
        return sseConnectionManager.register(clientId)
    }
}
