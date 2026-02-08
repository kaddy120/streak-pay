package com.workpointstracker.api.entity

import com.workpointstracker.shared.models.WishItem
import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "wish_items")
class WishItemEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(nullable = false)
    var name: String = "",

    @Column(nullable = false)
    var price: Double = 0.0,

    @Column(name = "image_url", columnDefinition = "TEXT")
    var imageUrl: String? = null,

    @Column(name = "is_redeemed")
    var isRedeemed: Boolean = false,

    @Column(name = "redeemed_date")
    var redeemedDate: LocalDate? = null
) {
    fun toShared(): WishItem = WishItem(
        id = id,
        name = name,
        price = price,
        imageUrl = imageUrl,
        isRedeemed = isRedeemed,
        redeemedDate = redeemedDate
    )

    companion object {
        fun fromShared(item: WishItem): WishItemEntity = WishItemEntity(
            id = item.id,
            name = item.name,
            price = item.price,
            imageUrl = item.imageUrl,
            isRedeemed = item.isRedeemed,
            redeemedDate = item.redeemedDate
        )
    }
}
