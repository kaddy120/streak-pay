package com.workpointstracker.data.local.database

import androidx.room.*
import com.workpointstracker.data.model.WishItem
import kotlinx.coroutines.flow.Flow

@Dao
interface WishItemDao {
    @Insert
    suspend fun insert(wishItem: WishItem): Long

    @Update
    suspend fun update(wishItem: WishItem)

    @Delete
    suspend fun delete(wishItem: WishItem)

    @Query("SELECT * FROM wish_items WHERE id = :id")
    suspend fun getWishItemById(id: Long): WishItem?

    @Query("SELECT * FROM wish_items WHERE isRedeemed = 0 ORDER BY price ASC")
    fun getAvailableWishItems(): Flow<List<WishItem>>

    @Query("SELECT * FROM wish_items WHERE isRedeemed = 1 ORDER BY redeemedDate DESC")
    fun getRedeemedWishItems(): Flow<List<WishItem>>

    @Query("SELECT * FROM wish_items ORDER BY isRedeemed ASC, price ASC")
    fun getAllWishItems(): Flow<List<WishItem>>
}
