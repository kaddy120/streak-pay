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
import android.util.Log
import com.workpointstracker.BuildConfig
import com.workpointstracker.data.remote.ApiClient
import com.workpointstracker.data.remote.ApiService
import com.workpointstracker.data.remote.CreateWishItemRequest
import com.workpointstracker.data.remote.UpdateWishItemRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class WishViewModel(application: Application) : AndroidViewModel(application) {

    private val database = WorkPointsDatabase.getDatabase(application)
    private val wishItemRepository = WishItemRepository(database.wishItemDao())
    private val sessionRepository = SessionRepository(database.sessionDao())
    private val apiService: ApiService by lazy {
        ApiClient.getService(BuildConfig.API_BASE_URL, BuildConfig.API_KEY)
    }

    private val _availableWishItems = MutableStateFlow<List<WishItem>>(emptyList())
    val availableWishItems: StateFlow<List<WishItem>> = _availableWishItems

    private val _redeemedWishItems = MutableStateFlow<List<WishItem>>(emptyList())
    val redeemedWishItems: StateFlow<List<WishItem>> = _redeemedWishItems

    private val _totalPoints = MutableStateFlow<Double?>(0.0)
    val totalPoints: StateFlow<Double?> = _totalPoints

    init {
        viewModelScope.launch {
            fetchTotalPoints()
            fetchWishItems()
        }
    }

    private suspend fun fetchTotalPoints() {
        try {
            val response = apiService.getTotalPoints()
            _totalPoints.value = response.totalPoints
        } catch (e: Exception) {
            Log.w("WishVM", "API points fetch failed, falling back to Room", e)
            val roomPoints = sessionRepository.getTotalPoints().first()
            _totalPoints.value = roomPoints
        }
    }

    private suspend fun fetchWishItems() {
        try {
            val responses = apiService.getWishItems()
            val items = responses.map { resp ->
                WishItem(
                    id = resp.id,
                    name = resp.name,
                    price = resp.price,
                    imagePath = resp.imageUrl ?: "",
                    isRedeemed = resp.isRedeemed,
                    redeemedDate = resp.redeemedDate?.atStartOfDay()
                )
            }
            _availableWishItems.value = items.filter { !it.isRedeemed }
            _redeemedWishItems.value = items.filter { it.isRedeemed }
        } catch (e: Exception) {
            Log.w("WishVM", "API wish items fetch failed, falling back to Room", e)
            _availableWishItems.value = wishItemRepository.getAvailableWishItems().first()
            _redeemedWishItems.value = wishItemRepository.getRedeemedWishItems().first()
        }
    }

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
                // Sync to API (fire-and-forget)
                try {
                    apiService.createWishItem(CreateWishItemRequest(
                        name = name,
                        price = price,
                        imageUrl = null
                    ))
                } catch (_: Exception) { }
                fetchWishItems()
            }
        }
    }

    fun redeemWishItem(wishItem: WishItem) {
        viewModelScope.launch {
            val currentPoints = totalPoints.value ?: 0.0
            val requiredPoints = FormatUtils.priceToPoints(wishItem.price)
            if (currentPoints >= requiredPoints) {
                val updatedWishItem = wishItem.copy(
                    isRedeemed = true,
                    redeemedDate = LocalDateTime.now()
                )
                wishItemRepository.updateWishItem(updatedWishItem)
                // Sync to API (fire-and-forget)
                try {
                    apiService.updateWishItem(wishItem.id, UpdateWishItemRequest(
                        isRedeemed = true,
                        redeemedDate = java.time.LocalDate.now()
                    ))
                } catch (_: Exception) { }
                fetchWishItems()
            }
        }
    }

    fun deleteWishItem(wishItem: WishItem) {
        viewModelScope.launch {
            if (!wishItem.imagePath.startsWith("http")) {
                ImageUtils.deleteImage(getApplication(), wishItem.imagePath)
            }
            wishItemRepository.deleteWishItem(wishItem)
            // Sync to API (fire-and-forget)
            try { apiService.deleteWishItem(wishItem.id) } catch (_: Exception) { }
            fetchWishItems()
        }
    }
}
