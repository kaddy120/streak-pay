package com.workpointstracker.api.service

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class DeviceStatus(
    val deviceId: String,
    val state: String,
    val currentApp: String?,
    val sessionId: Long?,
    val elapsedSeconds: Long,
    val pausedSeconds: Long,
    val lastHeartbeat: Instant
)

@Service
class DeviceStatusService {
    private val statuses = ConcurrentHashMap<String, DeviceStatus>()

    fun updateStatus(status: DeviceStatus) {
        statuses[status.deviceId] = status
    }

    fun getStatus(deviceId: String): DeviceStatus? = statuses[deviceId]

    fun getAllStatuses(): List<DeviceStatus> = statuses.values.toList()

    fun removeStatus(deviceId: String) {
        statuses.remove(deviceId)
    }

    @Scheduled(fixedRate = 30_000)
    fun evictStale() {
        val cutoff = Instant.now().minusSeconds(60)
        statuses.entries.removeIf { it.value.lastHeartbeat.isBefore(cutoff) }
    }
}
