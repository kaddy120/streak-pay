package com.workpointstracker.api.controller

import com.workpointstracker.api.dto.*
import com.workpointstracker.api.entity.WishItemEntity
import com.workpointstracker.api.repository.WishItemRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/wishlist")
class WishItemController(
    private val wishItemRepository: WishItemRepository
) {
    @GetMapping
    fun getWishItems(
        @RequestParam(required = false) filter: String?
    ): ResponseEntity<List<WishItemResponse>> {
        val items = when (filter) {
            "available" -> wishItemRepository.findByIsRedeemedFalseOrderByPriceAsc()
            "redeemed" -> wishItemRepository.findByIsRedeemedTrueOrderByRedeemedDateDesc()
            else -> wishItemRepository.findAllByOrderByIsRedeemedAscPriceAsc()
        }
        return ResponseEntity.ok(items.map { it.toResponse() })
    }

    @PostMapping
    fun createWishItem(@RequestBody request: CreateWishItemRequest): ResponseEntity<WishItemResponse> {
        val entity = WishItemEntity(
            name = request.name,
            price = request.price,
            imageUrl = request.imageUrl
        )
        val saved = wishItemRepository.save(entity)
        return ResponseEntity.status(HttpStatus.CREATED).body(saved.toResponse())
    }

    @PutMapping("/{id}")
    fun updateWishItem(
        @PathVariable id: Long,
        @RequestBody request: UpdateWishItemRequest
    ): ResponseEntity<WishItemResponse> {
        val entity = wishItemRepository.findById(id)
            .orElseThrow { NoSuchElementException("Wish item not found: $id") }

        request.name?.let { entity.name = it }
        request.price?.let { entity.price = it }
        request.imageUrl?.let { entity.imageUrl = it }
        request.isRedeemed?.let { entity.isRedeemed = it }
        request.redeemedDate?.let { entity.redeemedDate = it }

        val saved = wishItemRepository.save(entity)
        return ResponseEntity.ok(saved.toResponse())
    }

    @DeleteMapping("/{id}")
    fun deleteWishItem(@PathVariable id: Long): ResponseEntity<Void> {
        if (!wishItemRepository.existsById(id)) {
            throw NoSuchElementException("Wish item not found: $id")
        }
        wishItemRepository.deleteById(id)
        return ResponseEntity.noContent().build()
    }

    private fun WishItemEntity.toResponse(): WishItemResponse = WishItemResponse(
        id = id,
        name = name,
        price = price,
        imageUrl = imageUrl,
        isRedeemed = isRedeemed,
        redeemedDate = redeemedDate
    )
}
