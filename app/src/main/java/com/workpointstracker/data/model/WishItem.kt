package com.workpointstracker.data.model

import java.time.LocalDateTime

data class WishItem(
    val id: Long = 0,
    val name: String,
    val price: Double,
    val imageUrl: String? = null,
    val isRedeemed: Boolean = false,
    val redeemedDate: LocalDateTime? = null
)
