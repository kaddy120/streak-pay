package com.workpointstracker.api.controller

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID

@RestController
@RequestMapping("/api/images")
class ImageController(
    @Value("\${app.image-upload-dir:./uploads}") uploadDir: String
) {
    private val uploadPath: Path = Paths.get(uploadDir).toAbsolutePath()
    private val allowedTypes = setOf("image/jpeg", "image/png", "image/webp", "image/gif")

    init {
        Files.createDirectories(uploadPath)
    }

    @PostMapping
    fun uploadImage(@RequestParam("file") file: MultipartFile): ResponseEntity<Map<String, String>> {
        if (file.isEmpty) {
            return ResponseEntity.badRequest().body(mapOf("error" to "File is empty"))
        }
        if (file.contentType == null || file.contentType !in allowedTypes) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Invalid file type. Allowed: jpeg, png, webp, gif"))
        }
        if (file.size > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(mapOf("error" to "File too large. Max 5MB"))
        }

        val contentType = file.contentType!!
        val extension = when (contentType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "bin"
        }
        val filename = "${UUID.randomUUID()}.$extension"
        val targetPath = uploadPath.resolve(filename)
        file.transferTo(targetPath.toFile())

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(mapOf("url" to "/api/images/$filename"))
    }

    @GetMapping("/{filename}")
    fun getImage(@PathVariable filename: String): ResponseEntity<Resource> {
        val filePath: Path = uploadPath.resolve(filename)
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build()
        }

        val contentType = when {
            filename.endsWith(".jpg") || filename.endsWith(".jpeg") -> MediaType.IMAGE_JPEG
            filename.endsWith(".png") -> MediaType.IMAGE_PNG
            filename.endsWith(".webp") -> MediaType.parseMediaType("image/webp")
            filename.endsWith(".gif") -> MediaType.IMAGE_GIF
            else -> MediaType.APPLICATION_OCTET_STREAM
        }

        val resource = FileSystemResource(filePath)
        return ResponseEntity.ok()
            .contentType(contentType)
            .body(resource)
    }
}
