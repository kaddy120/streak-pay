package com.workpointstracker.api.controller

import com.workpointstracker.api.dto.*
import com.workpointstracker.api.service.SessionService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api")
class SessionController(
    private val sessionService: SessionService
) {
    @PostMapping("/sessions")
    fun createSession(@RequestBody request: CreateSessionRequest): ResponseEntity<SessionResponse> {
        val session = sessionService.createSession(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(session)
    }

    @PutMapping("/sessions/{id}")
    fun updateSession(
        @PathVariable id: Long,
        @RequestBody request: UpdateSessionRequest
    ): ResponseEntity<SessionResponse> {
        return ResponseEntity.ok(sessionService.updateSession(id, request))
    }

    @GetMapping("/sessions/{id}")
    fun getSession(@PathVariable id: Long): ResponseEntity<SessionResponse> {
        return ResponseEntity.ok(sessionService.getSession(id))
    }

    @GetMapping("/sessions")
    fun getSessions(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?
    ): ResponseEntity<List<SessionResponse>> {
        return ResponseEntity.ok(sessionService.getSessions(startDate, endDate))
    }

    @DeleteMapping("/sessions/{id}")
    fun deleteSession(@PathVariable id: Long): ResponseEntity<Void> {
        sessionService.deleteSession(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/sessions/active")
    fun getActiveSessions(
        @RequestParam(required = false) deviceId: String?
    ): ResponseEntity<List<SessionResponse>> {
        return if (deviceId != null) {
            ResponseEntity.ok(sessionService.getActiveSessionsByDevice(deviceId))
        } else {
            ResponseEntity.ok(sessionService.getAllActiveSessions())
        }
    }

    @GetMapping("/stats/today")
    fun getTodayStats(): ResponseEntity<TodayStatsResponse> {
        return ResponseEntity.ok(sessionService.getTodayStats())
    }

    @GetMapping("/stats/points")
    fun getTotalPoints(): ResponseEntity<PointsResponse> {
        return ResponseEntity.ok(sessionService.getTotalPoints())
    }

    @GetMapping("/api/health")
    fun health(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf("status" to "ok"))
    }
}
