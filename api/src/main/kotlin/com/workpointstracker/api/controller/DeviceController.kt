package com.workpointstracker.api.controller

import com.workpointstracker.api.dto.DaemonCommandRequest
import com.workpointstracker.api.dto.DeviceHeartbeatRequest
import com.workpointstracker.api.dto.DeviceStatusResponse
import com.workpointstracker.api.service.DeviceStatus
import com.workpointstracker.api.service.DeviceStatusService
import com.workpointstracker.api.sse.SseConnectionManager
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
@RequestMapping("/api/devices")
class DeviceController(
    private val deviceStatusService: DeviceStatusService,
    private val sseConnectionManager: SseConnectionManager
) {
    @PostMapping("/heartbeat")
    fun heartbeat(@RequestBody request: DeviceHeartbeatRequest): ResponseEntity<Void> {
        val status = DeviceStatus(
            deviceId = request.deviceId,
            state = request.state,
            currentApp = request.currentApp,
            sessionId = request.sessionId,
            elapsedSeconds = request.elapsedSeconds,
            pausedSeconds = request.pausedSeconds,
            lastHeartbeat = Instant.now()
        )
        deviceStatusService.updateStatus(status)

        sseConnectionManager.broadcast("device.status", toResponse(status))

        return ResponseEntity.ok().build()
    }

    @GetMapping("/status")
    fun getAllStatuses(): ResponseEntity<List<DeviceStatusResponse>> {
        return ResponseEntity.ok(deviceStatusService.getAllStatuses().map { toResponse(it) })
    }

    @GetMapping("/status/{deviceId}")
    fun getDeviceStatus(@PathVariable deviceId: String): ResponseEntity<DeviceStatusResponse> {
        val status = deviceStatusService.getStatus(deviceId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(toResponse(status))
    }

    @PostMapping("/command")
    fun sendCommand(@RequestBody request: DaemonCommandRequest): ResponseEntity<Map<String, Any>> {
        val validActions = setOf("start", "pause", "resume", "stop")
        if (request.action !in validActions) {
            return ResponseEntity.badRequest().body(mapOf(
                "error" to "Invalid action: ${request.action}" as Any
            ))
        }

        sseConnectionManager.broadcast("daemon.command", mapOf(
            "deviceId" to request.deviceId,
            "action" to request.action
        ))

        return ResponseEntity.ok(mapOf(
            "ok" to true as Any,
            "action" to request.action as Any,
            "deviceId" to request.deviceId as Any
        ))
    }

    @DeleteMapping("/status/{deviceId}")
    fun removeStatus(@PathVariable deviceId: String): ResponseEntity<Void> {
        deviceStatusService.removeStatus(deviceId)
        return ResponseEntity.noContent().build()
    }

    private fun toResponse(status: DeviceStatus) = DeviceStatusResponse(
        deviceId = status.deviceId,
        state = status.state,
        currentApp = status.currentApp,
        sessionId = status.sessionId,
        elapsedSeconds = status.elapsedSeconds,
        pausedSeconds = status.pausedSeconds,
        lastHeartbeat = status.lastHeartbeat.toString()
    )
}
