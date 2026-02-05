package com.workpointstracker.ui.wish

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.workpointstracker.data.model.WishItem
import com.workpointstracker.util.FormatUtils
import com.workpointstracker.util.FormatUtils.priceToPoints
import com.workpointstracker.util.ImageUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishScreen(
    viewModel: WishViewModel = viewModel(),
    onItemClick: (Long) -> Unit = {}
) {
    val availableItems by viewModel.availableWishItems.collectAsState()
    val redeemedItems by viewModel.redeemedWishItems.collectAsState()
    val totalPoints by viewModel.totalPoints.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Wish List",
                style = MaterialTheme.typography.headlineMedium
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = FormatUtils.formatPoints(totalPoints ?: 0.0),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Item")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tabs
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Available") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Redeemed") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Content
        when (selectedTab) {
            0 -> WishItemsGrid(
                items = availableItems,
                totalPoints = totalPoints ?: 0.0,
                onItemClick = { onItemClick(it.id) },
                onDeleteClick = { viewModel.deleteWishItem(it) }
            )
            1 -> WishItemsGrid(
                items = redeemedItems,
                totalPoints = totalPoints ?: 0.0,
                isRedeemed = true,
                onItemClick = { onItemClick(it.id) },
                onDeleteClick = { viewModel.deleteWishItem(it) }
            )
        }
    }

    if (showAddDialog) {
        AddWishItemDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, price, uri ->
                viewModel.addWishItem(name, price, uri)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun WishItemsGrid(
    items: List<WishItem>,
    totalPoints: Double,
    isRedeemed: Boolean = false,
    onItemClick: (WishItem) -> Unit = {},
    onDeleteClick: (WishItem) -> Unit = {}
) {
    val context = LocalContext.current

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items) { item ->
            WishItemCard(
                wishItem = item,
                canAfford = totalPoints >= priceToPoints(item.price),
                isRedeemed = isRedeemed,
                onItemClick = { onItemClick(item) },
                onDeleteClick = { onDeleteClick(item) },
                context = context
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishItemCard(
    wishItem: WishItem,
    canAfford: Boolean,
    isRedeemed: Boolean,
    onItemClick: () -> Unit,
    onDeleteClick: () -> Unit,
    context: android.content.Context
) {
    val imageFile = ImageUtils.getImageFile(context, wishItem.imagePath)
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f),
        shape = RoundedCornerShape(12.dp),
        onClick = onItemClick
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Image
            Image(
                painter = rememberAsyncImagePainter(imageFile),
                contentDescription = wishItem.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentScale = ContentScale.Crop
            )

            // Info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = wishItem.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = FormatUtils.formatPriceAsPoints(wishItem.price),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isRedeemed) MaterialTheme.colorScheme.outline
                    else if (canAfford) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWishItemDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Double, Uri) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { imageUri = it }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Wish Item") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Price (R)") },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        val priceValue = price.toDoubleOrNull()
                        if (priceValue != null && priceValue > 0) {
                            Text("= ${FormatUtils.formatPriceAsPoints(priceValue)} required")
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (imageUri == null) "Select Image" else "Image Selected")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val priceValue = price.toDoubleOrNull()
                    if (name.isNotBlank() && priceValue != null && imageUri != null) {
                        onAdd(name, priceValue, imageUri!!)
                    }
                },
                enabled = name.isNotBlank() && price.toDoubleOrNull() != null && imageUri != null
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
