package com.workpointstracker.ui.wish

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.workpointstracker.WorkPointsApplication
import com.workpointstracker.data.model.WishItem
import com.workpointstracker.data.remote.ApiService
import com.workpointstracker.data.remote.CreateWishItemRequest
import com.workpointstracker.data.remote.UpdateWishItemRequest
import com.workpointstracker.util.FormatUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

class WishViewModel(application: Application) : AndroidViewModel(application) {

    private val apiService: ApiService = (application as WorkPointsApplication).apiService

    private val _availableWishItems = MutableStateFlow<List<WishItem>>(emptyList())
    val availableWishItems: StateFlow<List<WishItem>> = _availableWishItems

    private val _redeemedWishItems = MutableStateFlow<List<WishItem>>(emptyList())
    val redeemedWishItems: StateFlow<List<WishItem>> = _redeemedWishItems

    private val _totalPoints = MutableStateFlow<Double?>(0.0)
    val totalPoints: StateFlow<Double?> = _totalPoints

    init {
        viewModelScope.launch {
            try { _totalPoints.value = apiService.getTotalPoints().totalPoints } catch (_: Exception) {}
            fetchWishItems()
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
                    imageUrl = resp.imageUrl,
                    isRedeemed = resp.isRedeemed,
                    redeemedDate = resp.redeemedDate?.atStartOfDay()
                )
            }
            _availableWishItems.value = items.filter { !it.isRedeemed }
            _redeemedWishItems.value = items.filter { it.isRedeemed }
        } catch (e: Exception) {
            Log.w("WishVM", "Wish items fetch failed", e)
        }
    }

    fun addWishItem(name: String, price: Double, imageUri: Uri) {
        viewModelScope.launch {
            try {
                // Upload image first
                val imageUrl = uploadImage(imageUri)

                apiService.createWishItem(CreateWishItemRequest(
                    name = name,
                    price = price,
                    imageUrl = imageUrl
                ))
                fetchWishItems()
            } catch (e: Exception) {
                Log.e("WishVM", "Failed to add wish item", e)
            }
        }
    }

    private suspend fun uploadImage(uri: Uri): String? {
        return try {
            val context = getApplication<Application>()
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File.createTempFile("upload_", ".jpg", context.cacheDir)
            FileOutputStream(tempFile).use { out -> inputStream.copyTo(out) }
            inputStream.close()

            val requestBody = tempFile.asRequestBody("image/jpeg".toMediaType())
            val part = MultipartBody.Part.createFormData("file", tempFile.name, requestBody)
            val response = apiService.uploadImage(part)
            tempFile.delete()
            response.url
        } catch (e: Exception) {
            Log.w("WishVM", "Image upload failed", e)
            null
        }
    }

    fun redeemWishItem(wishItem: WishItem) {
        viewModelScope.launch {
            val currentPoints = totalPoints.value ?: 0.0
            val requiredPoints = FormatUtils.priceToPoints(wishItem.price)
            if (currentPoints >= requiredPoints) {
                try {
                    apiService.updateWishItem(wishItem.id, UpdateWishItemRequest(
                        isRedeemed = true,
                        redeemedDate = java.time.LocalDate.now()
                    ))
                    fetchWishItems()
                } catch (_: Exception) { }
            }
        }
    }

    fun deleteWishItem(wishItem: WishItem) {
        viewModelScope.launch {
            try {
                apiService.deleteWishItem(wishItem.id)
                fetchWishItems()
            } catch (_: Exception) { }
        }
    }
}
