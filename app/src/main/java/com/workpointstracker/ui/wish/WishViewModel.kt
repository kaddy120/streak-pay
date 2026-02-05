package com.workpointstracker.ui.wish

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.workpointstracker.data.local.database.WorkPointsDatabase
import com.workpointstracker.data.model.WishItem
import com.workpointstracker.data.repository.SessionRepository
import com.workpointstracker.data.repository.WishItemRepository
import com.workpointstracker.util.FormatUtils
import com.workpointstracker.util.ImageUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class WishViewModel(application: Application) : AndroidViewModel(application) {

    private val database = WorkPointsDatabase.getDatabase(application)
    private val wishItemRepository = WishItemRepository(database.wishItemDao())
    private val sessionRepository = SessionRepository(database.sessionDao())

    val availableWishItems = wishItemRepository.getAvailableWishItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val redeemedWishItems = wishItemRepository.getRedeemedWishItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalPoints = sessionRepository.getTotalPoints()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun addWishItem(name: String, price: Double, imageUri: Uri) {
        viewModelScope.launch {
            val imagePath = ImageUtils.saveImageToInternalStorage(getApplication(), imageUri)
            if (imagePath != null) {
                val wishItem = WishItem(
                    name = name,
                    price = price,
                    imagePath = imagePath
                )
                wishItemRepository.insertWishItem(wishItem)
            }
        }
    }

    fun redeemWishItem(wishItem: WishItem) {
        viewModelScope.launch {
            val currentPoints = totalPoints.value ?: 0.0
            val requiredPoints = FormatUtils.priceToPoints(wishItem.price)
            if (currentPoints >= requiredPoints) {
                // Update wish item as redeemed
                val updatedWishItem = wishItem.copy(
                    isRedeemed = true,
                    redeemedDate = LocalDateTime.now()
                )
                wishItemRepository.updateWishItem(updatedWishItem)

                // Deduct points by inserting a negative session
                // (We'll track this differently in a real app, but for simplicity)
                // Actually, let's not do this. Instead, we'll just mark as redeemed
                // and let the UI handle the display of remaining points
                // The user doesn't "lose" points, they just mark items as purchased
            }
        }
    }

    fun deleteWishItem(wishItem: WishItem) {
        viewModelScope.launch {
            ImageUtils.deleteImage(getApplication(), wishItem.imagePath)
            wishItemRepository.deleteWishItem(wishItem)
        }
    }
}
