package com.workpointstracker.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.workpointstracker.api.dto.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.nio.file.Path
import java.time.LocalDate

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WishItemIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var mapper: ObjectMapper

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

    @BeforeEach
    fun cleanup() {
        val response = mockMvc.perform(
            get("/api/wishlist").header("X-API-Key", apiKey)
        ).andReturn().response.contentAsString
        val items: List<WishItemResponse> = mapper.readValue(response)
        items.forEach { item ->
            mockMvc.perform(
                delete("/api/wishlist/${item.id}").header("X-API-Key", apiKey)
            )
        }
    }

    // ── Helpers ──

    private fun createWishItem(
        name: String,
        price: Double,
        imageUrl: String? = null
    ): WishItemResponse {
        val body = mapper.writeValueAsString(CreateWishItemRequest(name, price, imageUrl))
        val result = mockMvc.perform(
            post("/api/wishlist")
                .header("X-API-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andExpect(status().isCreated).andReturn()
        return mapper.readValue(result.response.contentAsString)
    }

    private fun uploadTestImage(classpathResource: String): String {
        val bytes = javaClass.classLoader.getResourceAsStream(classpathResource)!!.readAllBytes()
        val contentType = if (classpathResource.endsWith(".png")) "image/png" else "image/jpeg"
        val file = MockMultipartFile("file", classpathResource, contentType, bytes)

        val result = mockMvc.perform(
            multipart("/api/images").file(file).header("X-API-Key", apiKey)
        ).andExpect(status().isCreated).andReturn()

        return mapper.readTree(result.response.contentAsString).get("url").asText()
    }

    // ── Tests ──

    @Test
    fun `create wish item without image returns 201`() {
        val item = createWishItem("Headphones", 49.99)

        assertEquals("Headphones", item.name)
        assertEquals(49.99, item.price)
        assertNull(item.imageUrl)
        assertFalse(item.isRedeemed)
        assertNull(item.redeemedDate)
        assertTrue(item.id > 0)
    }

    @Test
    fun `create wish item with image returns 201 with imageUrl`() {
        val imageUrl = uploadTestImage("img/image1.jpg")
        val item = createWishItem("Camera", 299.99, imageUrl)

        assertEquals("Camera", item.name)
        assertEquals(299.99, item.price)
        assertEquals(imageUrl, item.imageUrl)
    }

    @Test
    fun `get wish item by id returns correct item`() {
        val created = createWishItem("Keyboard", 129.00)

        val result = mockMvc.perform(
            get("/api/wishlist/${created.id}").header("X-API-Key", apiKey)
        ).andExpect(status().isOk).andReturn()

        val fetched: WishItemResponse = mapper.readValue(result.response.contentAsString)
        assertEquals(created.id, fetched.id)
        assertEquals("Keyboard", fetched.name)
        assertEquals(129.00, fetched.price)
    }

    @Test
    fun `get nonexistent wish item returns 404`() {
        mockMvc.perform(
            get("/api/wishlist/99999").header("X-API-Key", apiKey)
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `list wish items returns all items`() {
        createWishItem("Item A", 10.0)
        createWishItem("Item B", 20.0)

        val result = mockMvc.perform(
            get("/api/wishlist").header("X-API-Key", apiKey)
        ).andExpect(status().isOk).andReturn()

        val items: List<WishItemResponse> = mapper.readValue(result.response.contentAsString)
        assertEquals(2, items.size)
    }

    @Test
    fun `list with available filter excludes redeemed`() {
        val item1 = createWishItem("Available Item", 10.0)
        val item2 = createWishItem("Redeemed Item", 20.0)

        // Redeem item2
        val redeemBody = mapper.writeValueAsString(
            UpdateWishItemRequest(isRedeemed = true, redeemedDate = LocalDate.now())
        )
        mockMvc.perform(
            put("/api/wishlist/${item2.id}")
                .header("X-API-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(redeemBody)
        ).andExpect(status().isOk)

        val result = mockMvc.perform(
            get("/api/wishlist?filter=available").header("X-API-Key", apiKey)
        ).andExpect(status().isOk).andReturn()

        val items: List<WishItemResponse> = mapper.readValue(result.response.contentAsString)
        assertEquals(1, items.size)
        assertEquals(item1.id, items[0].id)
    }

    @Test
    fun `update wish item name and price`() {
        val created = createWishItem("Old Name", 50.0)

        val updateBody = mapper.writeValueAsString(
            UpdateWishItemRequest(name = "New Name", price = 75.0)
        )
        val result = mockMvc.perform(
            put("/api/wishlist/${created.id}")
                .header("X-API-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody)
        ).andExpect(status().isOk).andReturn()

        val updated: WishItemResponse = mapper.readValue(result.response.contentAsString)
        assertEquals("New Name", updated.name)
        assertEquals(75.0, updated.price)
    }

    @Test
    fun `update wish item image`() {
        val image1Url = uploadTestImage("img/image1.jpg")
        val created = createWishItem("Gadget", 100.0, image1Url)
        assertEquals(image1Url, created.imageUrl)

        val image2Url = uploadTestImage("img/image2.jpeg")
        val updateBody = mapper.writeValueAsString(
            UpdateWishItemRequest(imageUrl = image2Url)
        )
        val result = mockMvc.perform(
            put("/api/wishlist/${created.id}")
                .header("X-API-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody)
        ).andExpect(status().isOk).andReturn()

        val updated: WishItemResponse = mapper.readValue(result.response.contentAsString)
        assertEquals(image2Url, updated.imageUrl)
    }

    @Test
    fun `delete wish item returns 204`() {
        val created = createWishItem("To Delete", 5.0)

        mockMvc.perform(
            delete("/api/wishlist/${created.id}").header("X-API-Key", apiKey)
        ).andExpect(status().isNoContent)

        // Verify it's gone
        val result = mockMvc.perform(
            get("/api/wishlist").header("X-API-Key", apiKey)
        ).andExpect(status().isOk).andReturn()

        val items: List<WishItemResponse> = mapper.readValue(result.response.contentAsString)
        assertTrue(items.none { it.id == created.id })
    }

    @Test
    fun `end-to-end upload image, create item, retrieve, verify image accessible`() {
        // Upload image
        val imageUrl = uploadTestImage("img/image1.jpg")
        assertTrue(imageUrl.startsWith("/api/images/"))

        // Create wish item with image
        val item = createWishItem("E2E Item", 199.99, imageUrl)
        assertEquals(imageUrl, item.imageUrl)

        // Retrieve wish item by ID
        val getResult = mockMvc.perform(
            get("/api/wishlist/${item.id}").header("X-API-Key", apiKey)
        ).andExpect(status().isOk).andReturn()
        val fetched: WishItemResponse = mapper.readValue(getResult.response.contentAsString)
        assertEquals(imageUrl, fetched.imageUrl)

        // Verify the image itself is accessible (GET images is auth-exempt)
        mockMvc.perform(get(imageUrl))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "image/jpeg"))
    }

    @Test
    fun `create wish item without API key returns 401`() {
        val body = mapper.writeValueAsString(CreateWishItemRequest("No Auth", 10.0))

        mockMvc.perform(
            post("/api/wishlist")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andExpect(status().isUnauthorized)
    }
}
