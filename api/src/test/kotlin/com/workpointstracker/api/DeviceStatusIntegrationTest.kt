package com.workpointstracker.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.workpointstracker.api.dto.DeviceStatusResponse
import com.workpointstracker.api.service.DeviceStatusService
import com.workpointstracker.api.sse.SseConnectionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.reset
import org.mockito.Mockito.verify
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeviceStatusIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var mapper: ObjectMapper
    @Autowired lateinit var deviceStatusService: DeviceStatusService
    @SpyBean lateinit var sseConnectionManager: SseConnectionManager

    private val apiKey = "test-api-key"

    @BeforeEach
    fun cleanup() {
        reset(sseConnectionManager)
        // Clear all device statuses
        deviceStatusService.getAllStatuses().forEach {
            deviceStatusService.removeStatus(it.deviceId)
        }
    }

    private fun sendHeartbeat(deviceId: String, state: String, currentApp: String? = null, sessionId: Long? = null, elapsed: Long = 0, paused: Long = 0) {
        val body = mapper.writeValueAsString(mapOf(
            "deviceId" to deviceId,
            "state" to state,
            "currentApp" to currentApp,
            "sessionId" to sessionId,
            "elapsedSeconds" to elapsed,
            "pausedSeconds" to paused
        ))
        mockMvc.perform(
            post("/api/devices/heartbeat")
                .header("X-API-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andExpect(status().isOk)
    }

    // ── Heartbeat ──

    @Test
    fun `heartbeat stores device status`() {
        sendHeartbeat("pc-myhost", "ACTIVE", "VS Code", 42, elapsed = 300, paused = 0)

        val result = mockMvc.perform(
            get("/api/devices/status/pc-myhost").header("X-API-Key", apiKey)
        ).andExpect(status().isOk).andReturn()

        val status: DeviceStatusResponse = mapper.readValue(result.response.contentAsString)
        assertEquals("pc-myhost", status.deviceId)
        assertEquals("ACTIVE", status.state)
        assertEquals("VS Code", status.currentApp)
        assertEquals(42L, status.sessionId)
        assertEquals(300L, status.elapsedSeconds)
        assertEquals(0L, status.pausedSeconds)
        assertNotNull(status.lastHeartbeat)
    }

    @Test
    fun `heartbeat broadcasts device status via SSE`() {
        sendHeartbeat("pc-myhost", "ACTIVE", "VS Code", 42)

        verify(sseConnectionManager).broadcast(
            eq("device.status"),
            argThat<Any> {
                this is DeviceStatusResponse &&
                    this.deviceId == "pc-myhost" &&
                    this.state == "ACTIVE"
            }
        )
    }

    @Test
    fun `heartbeat updates existing status`() {
        sendHeartbeat("pc-myhost", "ACTIVE", "VS Code", 42, elapsed = 100)
        sendHeartbeat("pc-myhost", "PAUSED", "VS Code", 42, elapsed = 100, paused = 30)

        val result = mockMvc.perform(
            get("/api/devices/status/pc-myhost").header("X-API-Key", apiKey)
        ).andExpect(status().isOk).andReturn()

        val status: DeviceStatusResponse = mapper.readValue(result.response.contentAsString)
        assertEquals("PAUSED", status.state)
        assertEquals(30L, status.pausedSeconds)
    }

    // ── Get Status ──

    @Test
    fun `get all statuses returns all devices`() {
        sendHeartbeat("pc-myhost", "ACTIVE", "VS Code", 42)
        sendHeartbeat("pc-workpc", "IDLE")

        val result = mockMvc.perform(
            get("/api/devices/status").header("X-API-Key", apiKey)
        ).andExpect(status().isOk).andReturn()

        val statuses: List<DeviceStatusResponse> = mapper.readValue(result.response.contentAsString)
        assertEquals(2, statuses.size)
        assertTrue(statuses.any { it.deviceId == "pc-myhost" && it.state == "ACTIVE" })
        assertTrue(statuses.any { it.deviceId == "pc-workpc" && it.state == "IDLE" })
    }

    @Test
    fun `get unknown device returns 404`() {
        mockMvc.perform(
            get("/api/devices/status/unknown-device").header("X-API-Key", apiKey)
        ).andExpect(status().isNotFound)
    }

    // ── Commands ──

    @Test
    fun `command broadcasts daemon command via SSE`() {
        val body = mapper.writeValueAsString(mapOf(
            "deviceId" to "pc-myhost",
            "action" to "pause"
        ))

        mockMvc.perform(
            post("/api/devices/command")
                .header("X-API-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andExpect(status().isOk)

        verify(sseConnectionManager).broadcast(
            eq("daemon.command"),
            argThat<Any> {
                @Suppress("UNCHECKED_CAST")
                val map = this as Map<String, String>
                map["deviceId"] == "pc-myhost" && map["action"] == "pause"
            }
        )
    }

    @Test
    fun `command rejects invalid action`() {
        val body = mapper.writeValueAsString(mapOf(
            "deviceId" to "pc-myhost",
            "action" to "invalid"
        ))

        mockMvc.perform(
            post("/api/devices/command")
                .header("X-API-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andExpect(status().isBadRequest)
    }

    // ── Delete ──

    @Test
    fun `delete removes device status`() {
        sendHeartbeat("pc-myhost", "ACTIVE", "VS Code", 42)

        mockMvc.perform(
            delete("/api/devices/status/pc-myhost").header("X-API-Key", apiKey)
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            get("/api/devices/status/pc-myhost").header("X-API-Key", apiKey)
        ).andExpect(status().isNotFound)
    }

    // ── Dashboard Integration ──

    @Test
    fun `dashboard includes device statuses`() {
        sendHeartbeat("pc-myhost", "ACTIVE", "IntelliJ", 42, elapsed = 600)

        val result = mockMvc.perform(
            get("/api/dashboard").header("X-API-Key", apiKey)
        ).andExpect(status().isOk).andReturn()

        val json = mapper.readTree(result.response.contentAsString)
        val deviceStatuses = json.get("deviceStatuses")
        assertNotNull(deviceStatuses)
        assertEquals(1, deviceStatuses.size())
        assertEquals("pc-myhost", deviceStatuses[0].get("deviceId").asText())
        assertEquals("ACTIVE", deviceStatuses[0].get("state").asText())
    }

    // ── API Key Security ──

    @Test
    fun `heartbeat without API key is rejected`() {
        val body = mapper.writeValueAsString(mapOf(
            "deviceId" to "pc-myhost",
            "state" to "IDLE"
        ))

        mockMvc.perform(
            post("/api/devices/heartbeat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andExpect(status().isUnauthorized)
    }
}
