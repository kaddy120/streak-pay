package com.workpointstracker.shared.models

import java.time.LocalDate

data class WishItem(
    val id: Long = 0,
    val name: String,
    val price: Double,
    val imageUrl: String? = null,
    val isRedeemed: Boolean = false,
    val redeemedDate: LocalDate? = null
)
