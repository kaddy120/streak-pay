package com.workpointstracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "wish_items")
data class WishItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val price: Double,
    val imagePath: String,
    val isRedeemed: Boolean = false,
    val redeemedDate: LocalDateTime? = null
)
