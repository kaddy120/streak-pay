package com.workpointstracker.api

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.nio.file.Path

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImageUploadIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc

    private val apiKey = "test-api-key"

    companion object {
        @TempDir
        @JvmStatic
        lateinit var uploadDir: Path

        @JvmStatic
        @DynamicPropertySource
        fun setProperties(registry: DynamicPropertyRegistry) {
            registry.add("app.image-upload-dir") { uploadDir.toString() }
        }
    }

    @Test
    fun `upload valid JPEG returns 201 with url`() {
        val file = MockMultipartFile(
            "file", "photo.jpg", "image/jpeg",
            ByteArray(100) { it.toByte() }
        )

        val result = mockMvc.perform(
            multipart("/api/images").file(file).header("X-API-Key", apiKey)
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.url").exists())
            .andReturn()

        val url = com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(result.response.contentAsString).get("url").asText()
        assertTrue(url.startsWith("/api/images/"))
        assertTrue(url.endsWith(".jpg"))
    }

    @Test
    fun `upload valid PNG returns 201`() {
        val file = MockMultipartFile(
            "file", "photo.png", "image/png",
            ByteArray(100) { it.toByte() }
        )

        mockMvc.perform(
            multipart("/api/images").file(file).header("X-API-Key", apiKey)
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.endsWith(".png")))
    }

    @Test
    fun `upload empty file returns 400`() {
        val file = MockMultipartFile(
            "file", "empty.jpg", "image/jpeg",
            ByteArray(0)
        )

        mockMvc.perform(
            multipart("/api/images").file(file).header("X-API-Key", apiKey)
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("File is empty"))
    }

    @Test
    fun `upload invalid content type returns 400`() {
        val file = MockMultipartFile(
            "file", "document.pdf", "application/pdf",
            ByteArray(100) { it.toByte() }
        )

        mockMvc.perform(
            multipart("/api/images").file(file).header("X-API-Key", apiKey)
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value(
                org.hamcrest.Matchers.containsString("Invalid file type")
            ))
    }

    @Test
    fun `upload without API key returns 401`() {
        val file = MockMultipartFile(
            "file", "photo.jpg", "image/jpeg",
            ByteArray(100) { it.toByte() }
        )

        mockMvc.perform(
            multipart("/api/images").file(file)
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `GET uploaded image returns file content`() {
        val imageBytes = ByteArray(256) { it.toByte() }
        val file = MockMultipartFile(
            "file", "roundtrip.jpg", "image/jpeg", imageBytes
        )

        // Upload
        val uploadResult = mockMvc.perform(
            multipart("/api/images").file(file).header("X-API-Key", apiKey)
        ).andExpect(status().isCreated).andReturn()

        val url = com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(uploadResult.response.contentAsString).get("url").asText()

        // Download and compare
        val downloadResult = mockMvc.perform(
            get(url).header("X-API-Key", apiKey)
        ).andExpect(status().isOk).andReturn()

        assertArrayEquals(imageBytes, downloadResult.response.contentAsByteArray)
    }

    @Test
    fun `GET uploaded image does not require API key`() {
        val file = MockMultipartFile(
            "file", "public.png", "image/png",
            ByteArray(50) { it.toByte() }
        )

        val uploadResult = mockMvc.perform(
            multipart("/api/images").file(file).header("X-API-Key", apiKey)
        ).andExpect(status().isCreated).andReturn()

        val url = com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(uploadResult.response.contentAsString).get("url").asText()

        // GET without X-API-Key should succeed
        mockMvc.perform(get(url))
            .andExpect(status().isOk)
    }

    @Test
    fun `GET nonexistent image returns 404`() {
        mockMvc.perform(
            get("/api/images/nonexistent.jpg").header("X-API-Key", apiKey)
        ).andExpect(status().isNotFound)
    }
}
