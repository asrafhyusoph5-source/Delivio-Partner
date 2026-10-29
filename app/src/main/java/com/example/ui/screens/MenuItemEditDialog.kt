package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.BusinessType
import com.example.data.model.MenuCategory
import com.example.data.model.MenuItem
import com.example.util.CurrencyConstants
import com.example.data.model.MenuItemModifier
import com.example.ui.theme.LocalDelivioColors
import com.example.util.FOOD_CATEGORIES
import com.example.util.CONVENIENCE_CATEGORIES

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuItemEditDialog(
    item: MenuItem,
    isNew: Boolean,
    categories: List<MenuCategory>,
    businessType: BusinessType,
    onSave: (MenuItem) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val customColors = LocalDelivioColors.current
    val allowedCategories = remember(businessType) {
        if (businessType == BusinessType.FOOD) FOOD_CATEGORIES else CONVENIENCE_CATEGORIES
    }

    val initialCatName = remember(item, categories, allowedCategories) {
        val directName = item.categoryName
        if (directName.isNotBlank() && allowedCategories.any { it.equals(directName, ignoreCase = true) }) {
            allowedCategories.first { it.equals(directName, ignoreCase = true) }
        } else {
            val fromId = categories.find { it.id == item.categoryId }?.name ?: ""
            if (fromId.isNotBlank() && allowedCategories.any { it.equals(fromId, ignoreCase = true) }) {
                allowedCategories.first { it.equals(fromId, ignoreCase = true) }
            } else if (isNew) {
                allowedCategories.firstOrNull() ?: ""
            } else {
                "" // Needs explicit assignment for legacy items
            }
        }
    }

    var name by remember { mutableStateOf(item.name) }
    var description by remember { mutableStateOf(item.description) }
    var priceText by remember { mutableStateOf(item.price.toString()) }
    var selectedCategoryName by remember { mutableStateOf(initialCatName) }
    var selectedCategoryId by remember {
        mutableStateOf(
            if (initialCatName.isNotBlank()) {
                categories.find { it.name.equals(initialCatName, ignoreCase = true) }?.id
                    ?: initialCatName.lowercase().trim().replace("&", "and").replace("[^a-z0-9]+".toRegex(), "_").trim('_')
            } else {
                ""
            }
        )
    }
    val isCatMissing = selectedCategoryName.isBlank() || !allowedCategories.any { it.equals(selectedCategoryName, ignoreCase = true) }
    var imageUrl by remember { mutableStateOf(item.imageUrl) }
    var iconEmoji by remember { mutableStateOf(item.iconEmoji) }

    // Food fields
    var prepTimeText by remember { mutableStateOf(item.prepTimeMinutes.toString()) }
    var selectedTags by remember { mutableStateOf(item.tags.toSet()) }

    // Convenience fields
    var sku by remember { mutableStateOf(item.sku) }
    var barcode by remember { mutableStateOf(item.barcode) }
    var unit by remember { mutableStateOf(item.unit) }
    var stockCountText by remember { mutableStateOf(item.stockCount.toString()) }
    var lowStockThresholdText by remember { mutableStateOf(item.lowStockAlertThreshold.toString()) }

    val allTagOptions = listOf("Best Seller", "Chef Special", "Spicy", "Vegetarian", "Halal")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Top Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isNew) "Add New Product" else "Edit Product",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Item Name
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Product / Item Name *") },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("menu_item_name_input")
                    )

                    // Price & Category
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = priceText,
                            onValueChange = { priceText = it },
                            label = { Text("Price (${CurrencyConstants.SYMBOL})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("menu_item_price_input")
                        )

                        // Category Selector (Required, lists only this merchant's allowed categories)
                        var catExpanded by remember { mutableStateOf(false) }

                        ExposedDropdownMenuBox(
                            expanded = catExpanded,
                            onExpandedChange = { catExpanded = it },
                            modifier = Modifier.weight(1.3f)
                        ) {
                            OutlinedTextField(
                                value = if (selectedCategoryName.isNotBlank()) selectedCategoryName else "Select Category *",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Category *") },
                                isError = isCatMissing,
                                supportingText = if (isCatMissing) {
                                    { Text("Required", color = MaterialTheme.colorScheme.error) }
                                } else null,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .menuAnchor()
                                    .testTag("menu_item_category_dropdown")
                            )
                            ExposedDropdownMenu(
                                expanded = catExpanded,
                                onDismissRequest = { catExpanded = false }
                            ) {
                                allowedCategories.forEach { catName ->
                                    DropdownMenuItem(
                                        text = { Text(catName, fontWeight = if (selectedCategoryName.equals(catName, ignoreCase = true)) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = {
                                            selectedCategoryName = catName
                                            val matchedDoc = categories.find { it.name.equals(catName, ignoreCase = true) }
                                            selectedCategoryId = matchedDoc?.id ?: catName.lowercase().trim().replace("&", "and").replace("[^a-z0-9]+".toRegex(), "_").trim('_')
                                            catExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Description
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        shape = RoundedCornerShape(12.dp),
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Image URL Input & Preview
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .padding(12.dp)
                    ) {
                        OutlinedTextField(
                            value = imageUrl,
                            onValueChange = { imageUrl = it },
                            label = { Text("Product Image URL (HTTPS)") },
                            placeholder = { Text("https://example.com/item.jpg") },
                            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("menu_item_image_url_input")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (imageUrl.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(1.dp, customColors.accentTint, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = imageUrl.trim(),
                                        contentDescription = "Image preview",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Image Preview", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) "Valid URL structure"
                                        else "Please provide a valid https:// URL",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) customColors.accent else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    // Conditional: FOOD MODE Fields
                    if (businessType == BusinessType.FOOD) {
                        Text(
                            text = "Kitchen & Food Settings",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = customColors.accent
                        )

                        OutlinedTextField(
                            value = prepTimeText,
                            onValueChange = { prepTimeText = it },
                            label = { Text("Estimated Prep Time (minutes)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "Dietary & Promotion Tags:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            allTagOptions.forEach { tag ->
                                val isSelected = selectedTags.contains(tag)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedTags = if (isSelected) selectedTags - tag else selectedTags + tag
                                    },
                                    label = { Text(tag, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = customColors.accentTint,
                                        selectedLabelColor = customColors.accent
                                    )
                                )
                            }
                        }
                    } else {
                        // Conditional: CONVENIENCE STORE MODE Fields
                        Text(
                            text = "Inventory & Barcode Specs",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = customColors.accent
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = sku,
                                onValueChange = { sku = it },
                                label = { Text("Product SKU") },
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = barcode,
                                onValueChange = { barcode = it },
                                label = { Text("Barcode") },
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = unit,
                                onValueChange = { unit = it },
                                label = { Text("Unit (e.g. 500ml, 1pc)") },
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = stockCountText,
                                onValueChange = { stockCountText = it },
                                label = { Text("Stock Count") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = lowStockThresholdText,
                                onValueChange = { lowStockThresholdText = it },
                                label = { Text("Low Alert") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!isNew) {
                        IconButton(
                            onClick = { onDelete(item.id) },
                            modifier = Modifier.testTag("delete_menu_item_btn")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            val parsedPrice = priceText.toDoubleOrNull() ?: item.price
                            val parsedPrep = prepTimeText.toIntOrNull() ?: item.prepTimeMinutes
                            val parsedStock = stockCountText.toIntOrNull() ?: item.stockCount
                            val parsedThreshold = lowStockThresholdText.toIntOrNull() ?: item.lowStockAlertThreshold

                            val finalCatName = selectedCategoryName
                            val finalCatId = selectedCategoryId.ifBlank {
                                categories.find { it.name.equals(finalCatName, ignoreCase = true) }?.id
                                    ?: finalCatName.lowercase().trim().replace("&", "and").replace("[^a-z0-9]+".toRegex(), "_").trim('_')
                            }

                            val updated = item.copy(
                                name = name.trim().ifBlank { "Untitled Item" },
                                description = description.trim(),
                                price = parsedPrice,
                                categoryId = finalCatId,
                                categoryName = finalCatName,
                                imageUrl = imageUrl.trim(),
                                iconEmoji = iconEmoji.ifBlank { if (businessType == BusinessType.FOOD) "🍔" else "🛍️" },
                                prepTimeMinutes = if (businessType == BusinessType.FOOD) parsedPrep else 0,
                                tags = if (businessType == BusinessType.FOOD) selectedTags.toList() else emptyList(),
                                sku = if (businessType == BusinessType.CONVENIENCE) sku.trim() else "",
                                barcode = if (businessType == BusinessType.CONVENIENCE) barcode.trim() else "",
                                unit = if (businessType == BusinessType.CONVENIENCE) unit.trim().ifBlank { "1 pc" } else "1 pc",
                                stockCount = if (businessType == BusinessType.CONVENIENCE) parsedStock else 50,
                                lowStockAlertThreshold = if (businessType == BusinessType.CONVENIENCE) parsedThreshold else 5,
                                businessType = businessType
                            )
                            onSave(updated)
                        },
                        enabled = name.isNotBlank() && !isCatMissing,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = customColors.accent,
                            contentColor = customColors.onAccent
                        ),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(48.dp)
                            .testTag("save_menu_item_btn")
                    ) {
                        Text("Save Product", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
