package com.workpointstracker.ui.wish

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.workpointstracker.data.local.database.WorkPointsDatabase
import com.workpointstracker.data.model.WishItem
import com.workpointstracker.data.repository.SessionRepository
import com.workpointstracker.data.repository.WishItemRepository
import com.workpointstracker.util.FormatUtils
import com.workpointstracker.util.ImageUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class WishItemDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val database = WorkPointsDatabase.getDatabase(application)
    private val wishItemRepository = WishItemRepository(database.wishItemDao())
    private val sessionRepository = SessionRepository(database.sessionDao())

    private val _wishItem = MutableStateFlow<WishItem?>(null)
    val wishItem: StateFlow<WishItem?> = _wishItem

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode

    private val _editedName = MutableStateFlow("")
    val editedName: StateFlow<String> = _editedName

    private val _editedPrice = MutableStateFlow("")
    val editedPrice: StateFlow<String> = _editedPrice

    private val _newImageUri = MutableStateFlow<Uri?>(null)
    val newImageUri: StateFlow<Uri?> = _newImageUri

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess

    private val _deleteSuccess = MutableStateFlow(false)
    val deleteSuccess: StateFlow<Boolean> = _deleteSuccess

    val totalPoints = sessionRepository.getTotalPoints()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun loadWishItem(itemId: Long) {
        viewModelScope.launch {
            val item = wishItemRepository.getWishItemById(itemId)
            _wishItem.value = item
            item?.let {
                _editedName.value = it.name
                _editedPrice.value = it.price.toString()
            }
        }
    }

    fun enterEditMode() {
        _isEditMode.value = true
        _newImageUri.value = null
    }

    fun cancelEditMode() {
        _isEditMode.value = false
        // Reset to original values
        _wishItem.value?.let {
            _editedName.value = it.name
            _editedPrice.value = it.price.toString()
        }
        _newImageUri.value = null
    }

    fun updateName(name: String) {
        _editedName.value = name
    }

    fun updatePrice(price: String) {
        _editedPrice.value = price
    }

    fun updateImage(uri: Uri) {
        _newImageUri.value = uri
    }

    fun isValidInput(): Boolean {
        return _editedName.value.isNotBlank() &&
               _editedPrice.value.toDoubleOrNull() != null &&
               _editedPrice.value.toDoubleOrNull()!! > 0
    }

    fun saveChanges() {
        viewModelScope.launch {
            val item = _wishItem.value ?: return@launch
            val newPrice = _editedPrice.value.toDoubleOrNull() ?: return@launch

            var imagePath = item.imagePath

            // If a new image was selected, save it and delete the old one
            _newImageUri.value?.let { uri ->
                val newPath = ImageUtils.saveImageToInternalStorage(getApplication(), uri)
                if (newPath != null) {
                    // Delete old image
                    ImageUtils.deleteImage(getApplication(), item.imagePath)
                    imagePath = newPath
                }
            }

            val updatedItem = item.copy(
                name = _editedName.value.trim(),
                price = newPrice,
                imagePath = imagePath
            )

            wishItemRepository.updateWishItem(updatedItem)
            _wishItem.value = updatedItem
            _isEditMode.value = false
            _newImageUri.value = null
            _saveSuccess.value = true
        }
    }

    fun deleteWishItem() {
        viewModelScope.launch {
            val item = _wishItem.value ?: return@launch
            ImageUtils.deleteImage(getApplication(), item.imagePath)
            wishItemRepository.deleteWishItem(item)
            _deleteSuccess.value = true
        }
    }

    fun redeemWishItem() {
        viewModelScope.launch {
            val item = _wishItem.value ?: return@launch
            val currentPoints = totalPoints.value ?: 0.0
            val requiredPoints = FormatUtils.priceToPoints(item.price)

            if (currentPoints >= requiredPoints) {
                val updatedWishItem = item.copy(
                    isRedeemed = true,
                    redeemedDate = LocalDateTime.now()
                )
                wishItemRepository.updateWishItem(updatedWishItem)
                _wishItem.value = updatedWishItem
                _saveSuccess.value = true
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishItemDetailScreen(
    itemId: Long,
    onBackClick: () -> Unit,
    viewModel: WishItemDetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val wishItem by viewModel.wishItem.collectAsState()
    val totalPoints by viewModel.totalPoints.collectAsState()
    val isEditMode by viewModel.isEditMode.collectAsState()
    val editedName by viewModel.editedName.collectAsState()
    val editedPrice by viewModel.editedPrice.collectAsState()
    val newImageUri by viewModel.newImageUri.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val deleteSuccess by viewModel.deleteSuccess.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.updateImage(it) }
    }

    LaunchedEffect(itemId) {
        viewModel.loadWishItem(itemId)
    }

    LaunchedEffect(deleteSuccess) {
        if (deleteSuccess) {
            onBackClick()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isEditMode) {
                            viewModel.cancelEditMode()
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Filled.Close else Icons.Filled.ArrowBack,
                            contentDescription = if (isEditMode) "Cancel" else "Back"
                        )
                    }
                },
                actions = {
                    if (!isEditMode && wishItem != null) {
                        // Edit button
                        IconButton(onClick = { viewModel.enterEditMode() }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit")
                        }
                        // Delete button
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        wishItem?.let { item ->
            val requiredPoints = FormatUtils.priceToPoints(
                if (isEditMode) editedPrice.toDoubleOrNull() ?: item.price else item.price
            )
            val canAfford = (totalPoints ?: 0.0) >= requiredPoints
            val imageFile = ImageUtils.getImageFile(context, item.imagePath)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Image section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.45f)
                        .padding(horizontal = 32.dp)
                        .then(
                            if (isEditMode) {
                                Modifier.clickable { imagePickerLauncher.launch("image/*") }
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(
                            model = newImageUri ?: imageFile
                        ),
                        contentDescription = item.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                            .then(
                                if (isEditMode) {
                                    Modifier.border(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(16.dp)
                                    )
                                } else Modifier
                            ),
                        contentScale = ContentScale.Fit
                    )

                    if (isEditMode) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Tap to change image",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Content section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.55f)
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    if (isEditMode) {
                        // Editable fields
                        OutlinedTextField(
                            value = editedName,
                            onValueChange = { viewModel.updateName(it) },
                            label = { Text("Item Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = editedPrice,
                            onValueChange = { viewModel.updatePrice(it) },
                            label = { Text("Price (R)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            supportingText = {
                                val priceValue = editedPrice.toDoubleOrNull()
                                if (priceValue != null && priceValue > 0) {
                                    Text("= ${FormatUtils.formatPriceAsPoints(priceValue)} required")
                                }
                            }
                        )
                    } else {
                        // Item name (view mode)
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Points required badge
                        PointsBadge(
                            points = requiredPoints,
                            canAfford = canAfford,
                            isRedeemed = item.isRedeemed
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Price info
                        Text(
                            text = "Price: ${FormatUtils.formatPrice(item.price)}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Points status
                        Text(
                            text = "Your points: ${FormatUtils.formatPoints(totalPoints ?: 0.0)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (item.isRedeemed) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Redeemed on ${item.redeemedDate?.let { FormatUtils.formatDate(it) } ?: "Unknown"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Action buttons
                    if (isEditMode) {
                        // Save button
                        Button(
                            onClick = { viewModel.saveChanges() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            enabled = viewModel.isValidInput(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Save Changes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (!item.isRedeemed) {
                        // Redeem button
                        Button(
                            onClick = { viewModel.redeemWishItem() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            enabled = canAfford,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (canAfford)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (canAfford)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (canAfford) "Redeem" else "Not enough points",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        } ?: run {
            // Loading state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Item") },
            text = { Text("Are you sure you want to remove \"${wishItem?.name}\" from your wish list?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteWishItem()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PointsBadge(
    points: Double,
    canAfford: Boolean,
    isRedeemed: Boolean
) {
    val backgroundColor = when {
        isRedeemed -> MaterialTheme.colorScheme.surfaceVariant
        canAfford -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val borderColor = when {
        isRedeemed -> MaterialTheme.colorScheme.outline
        canAfford -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    val textColor = when {
        isRedeemed -> MaterialTheme.colorScheme.onSurfaceVariant
        canAfford -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }

    Box(
        modifier = Modifier
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(20.dp)
            )
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "${String.format("%.1f", points)} pts",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}
