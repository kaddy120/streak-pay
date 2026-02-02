package com.workpointstracker.data.repository

import com.workpointstracker.data.local.database.WishItemDao
import com.workpointstracker.data.model.WishItem
import kotlinx.coroutines.flow.Flow

class WishItemRepository(private val wishItemDao: WishItemDao) {

    fun getAvailableWishItems(): Flow<List<WishItem>> = wishItemDao.getAvailableWishItems()

    fun getRedeemedWishItems(): Flow<List<WishItem>> = wishItemDao.getRedeemedWishItems()

    fun getAllWishItems(): Flow<List<WishItem>> = wishItemDao.getAllWishItems()

    suspend fun insertWishItem(wishItem: WishItem): Long = wishItemDao.insert(wishItem)

    suspend fun updateWishItem(wishItem: WishItem) = wishItemDao.update(wishItem)

    suspend fun deleteWishItem(wishItem: WishItem) = wishItemDao.delete(wishItem)

    suspend fun getWishItemById(id: Long): WishItem? = wishItemDao.getWishItemById(id)
}
