package com.workpointstracker.api.repository

import com.workpointstracker.api.entity.WishItemEntity
import org.springframework.data.jpa.repository.JpaRepository

interface WishItemRepository : JpaRepository<WishItemEntity, Long> {

    fun findByIsRedeemedFalseOrderByPriceAsc(): List<WishItemEntity>

    fun findByIsRedeemedTrueOrderByRedeemedDateDesc(): List<WishItemEntity>

    fun findAllByOrderByIsRedeemedAscPriceAsc(): List<WishItemEntity>
}
