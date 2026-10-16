package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BusinessType
import com.example.data.model.MenuCategory
import com.example.data.model.MenuItem
import com.example.data.model.PromoDeal
import com.example.ui.theme.LocalDelivioColors
import com.example.ui.theme.StatusClosedRed
import com.example.ui.theme.StatusOpenGreen
import com.example.ui.theme.StatusWarningAmber
import com.example.util.FormatUtils

@Composable
fun MenuScreen(
    categories: List<MenuCategory>,
    menuItems: List<MenuItem>,
    promos: List<PromoDeal>,
    businessType: BusinessType,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    stockFilter: String,
    onStockFilterChange: (String) -> Unit,
    selectedCategoryId: String?,
    onCategorySelect: (String?) -> Unit,
    isMultiSelectMode: Boolean,
    selectedItemIds: Set<String>,
    onToggleMultiSelect: () -> Unit,
    onToggleItemSelection: (String) -> Unit,
    onBulkMarkInStock: () -> Unit,
    onBulkMarkSoldOut: () -> Unit,
    onBulkPriceAdjust: (percentDelta: Double, fixedDelta: Double) -> Unit,
    onToggleItemAvailability: (MenuItem) -> Unit,
    onAddItem: () -> Unit,
    onEditItem: (MenuItem) -> Unit,
    onDuplicateItem: (String) -> Unit,
    onAddCategory: (String) -> Unit,
    onReorderCategory: (categoryId: String, moveUp: Boolean) -> Unit,
    onToggleCategoryVisibility: (String) -> Unit,
    onDeleteCategory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val customColors = LocalDelivioColors.current
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var showManageCategoriesDialog by remember { mutableStateOf(false) }
    var showBulkPriceDialog by remember { mutableStateOf(false) }
    var bulkPriceDeltaText by remember { mutableStateOf("10") } // 10%

    // Filter categories by business type
    val filteredCategories = remember(categories, businessType) {
        categories.filter { it.businessType == businessType }.sortedBy { it.displayOrder }
    }

    // Filter items by category, business type, stock, and search query
    val displayedItems = remember(menuItems, businessType, selectedCategoryId, stockFilter, searchQuery) {
        menuItems.filter { item ->
            val matchType = item.businessType == businessType
            val matchCategory = selectedCategoryId == null || item.categoryId == selectedCategoryId
            val matchStock = when (stockFilter) {
                "In stock" -> item.inStock
                "Sold out" -> !item.inStock
                else -> true
            }
            val matchQuery = searchQuery.isBlank() ||
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    item.description.contains(searchQuery, ignoreCase = true) ||
                    item.sku.contains(searchQuery, ignoreCase = true)
            matchType && matchCategory && matchStock && matchQuery
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Top Search Bar & Multi-Select Action Trigger
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search dishes, SKUs, tags...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("menu_search_input")
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onToggleMultiSelect,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isMultiSelectMode) customColors.accentTint else MaterialTheme.colorScheme.surfaceVariant)
                    .testTag("bulk_select_toggle_btn")
            ) {
                Icon(
                    imageVector = if (isMultiSelectMode) Icons.Default.ChecklistRtl else Icons.Default.Checklist,
                    contentDescription = "Bulk Actions",
                    tint = if (isMultiSelectMode) customColors.accent else MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = onAddItem,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(customColors.accent)
                    .testTag("add_menu_item_top_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Item",
                    tint = customColors.onAccent
                )
            }
        }

        // Active Promo Banner Preview
        if (promos.isNotEmpty() && searchQuery.isBlank()) {
            val activePromo = promos.first()
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = customColors.accentTint),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🏷️", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activePromo.title,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Code: ${activePromo.code} • ${activePromo.discountPercentage}% OFF (Min ${FormatUtils.formatCurrency(activePromo.minOrder)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "ACTIVE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = StatusOpenGreen,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(StatusOpenGreen.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Category Filter Chips & Category Manager Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedCategoryId == null,
                onClick = { onCategorySelect(null) },
                label = { Text("All Categories (${displayedItems.size})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = customColors.accentTint,
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface
                )
            )

            filteredCategories.forEach { cat ->
                val isSelected = selectedCategoryId == cat.id
                FilterChip(
                    selected = isSelected,
                    onClick = { onCategorySelect(cat.id) },
                    label = { Text(cat.name) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = customColors.accentTint,
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.testTag("cat_chip_${cat.id}")
                )
            }

            // Manage Categories button
            AssistChip(
                onClick = { showManageCategoriesDialog = true },
                label = { Text("Manage Categories") },
                leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }

        // Quick Stock Availability Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "In stock", "Sold out").forEach { stockStatus ->
                val isSelected = stockFilter == stockStatus
                FilterChip(
                    selected = isSelected,
                    onClick = { onStockFilterChange(stockStatus) },
                    label = { Text(stockStatus, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = if (stockStatus == "Sold out") StatusClosedRed.copy(alpha = 0.15f) else customColors.accentTint,
                        selectedLabelColor = if (stockStatus == "Sold out") StatusClosedRed else MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }

        // Multi-Select Bulk Actions Bar
        AnimatedVisibility(visible = isMultiSelectMode) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${selectedItemIds.size} selected",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = onBulkMarkInStock,
                            enabled = selectedItemIds.isNotEmpty()
                        ) {
                            Text("In Stock", fontSize = 12.sp, color = StatusOpenGreen)
                        }

                        TextButton(
                            onClick = onBulkMarkSoldOut,
                            enabled = selectedItemIds.isNotEmpty()
                        ) {
                            Text("Sold Out", fontSize = 12.sp, color = StatusClosedRed)
                        }

                        TextButton(
                            onClick = { showBulkPriceDialog = true },
                            enabled = selectedItemIds.isNotEmpty()
                        ) {
                            Text("Price ±%", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Items List
        if (displayedItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔍", fontSize = 42.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No matching items found", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Try adjusting your filters or search terms", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
            ) {
                items(displayedItems, key = { it.id }) { item ->
                    MenuItemRow(
                        item = item,
                        businessType = businessType,
                        isMultiSelectMode = isMultiSelectMode,
                        isSelected = selectedItemIds.contains(item.id),
                        onSelectToggle = { onToggleItemSelection(item.id) },
                        onToggleStock = { onToggleItemAvailability(item) },
                        onEdit = { onEditItem(item) },
                        onDuplicate = { onDuplicateItem(item.id) }
                    )
                }
            }
        }
    }

    // Manage Categories Modal Dialog
    if (showManageCategoriesDialog) {
        AlertDialog(
            onDismissRequest = { showManageCategoriesDialog = false },
            title = { Text("Manage Categories") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newCategoryName,
                            onValueChange = { newCategoryName = it },
                            placeholder = { Text("New Category Name") },
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (newCategoryName.isNotBlank()) {
                                    onAddCategory(newCategoryName.trim())
                                    newCategoryName = ""
                                }
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(customColors.accent)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", tint = customColors.onAccent)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    filteredCategories.forEachIndexed { index, cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(cat.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))

                            Row {
                                IconButton(onClick = { onReorderCategory(cat.id, true) }, enabled = index > 0) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { onReorderCategory(cat.id, false) }, enabled = index < filteredCategories.size - 1) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { onDeleteCategory(cat.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManageCategoriesDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    // Bulk Price Adjustment Dialog
    if (showBulkPriceDialog) {
        AlertDialog(
            onDismissRequest = { showBulkPriceDialog = false },
            title = { Text("Adjust Prices in Bulk") },
            text = {
                Column {
                    Text("Apply percentage adjustment to ${selectedItemIds.size} selected items:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = bulkPriceDeltaText,
                        onValueChange = { bulkPriceDeltaText = it },
                        label = { Text("Percentage change (e.g. 10 for +10%, -5 for -5%)") },
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val delta = bulkPriceDeltaText.toDoubleOrNull() ?: 0.0
                        onBulkPriceAdjust(delta, 0.0)
                        showBulkPriceDialog = false
                    }
                ) {
                    Text("Apply Adjustment")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkPriceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun MenuItemRow(
    item: MenuItem,
    businessType: BusinessType,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    onSelectToggle: () -> Unit,
    onToggleStock: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit
) {
    val customColors = LocalDelivioColors.current
    val isSoldOut = !item.inStock

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSoldOut) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSoldOut) 0.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isMultiSelectMode) onSelectToggle() else onEdit()
            }
            .testTag("menu_row_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox for bulk actions
            if (isMultiSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectToggle() }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Visual Emoji Placeholder
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSoldOut) MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        else customColors.accentTint
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(item.iconEmoji, fontSize = 26.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isSoldOut) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1
                    )
                }

                if (isSoldOut) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "SOLD OUT • ${item.soldOutReason ?: "Unavailable"}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusClosedRed
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = FormatUtils.formatCurrency(item.price),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = if (isSoldOut) MaterialTheme.colorScheme.onSurfaceVariant else customColors.accent
                    )

                    if (businessType == BusinessType.FOOD) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "⏱ ${item.prepTimeMinutes}m",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Stock: ${item.stockCount} (${item.unit})",
                            fontSize = 11.sp,
                            color = if (item.stockCount <= item.lowStockAlertThreshold) StatusClosedRed else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (item.stockCount <= item.lowStockAlertThreshold) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                // Tags preview (Food)
                if (businessType == BusinessType.FOOD && item.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        item.tags.take(2).forEach { tag ->
                            Text(
                                text = tag,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Actions: In-Stock Switch and Duplicate
            Column(horizontalAlignment = Alignment.End) {
                Switch(
                    checked = item.inStock,
                    onCheckedChange = { onToggleStock() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = customColors.accent,
                        checkedTrackColor = customColors.accentTint
                    ),
                    modifier = Modifier.testTag("stock_switch_${item.id}")
                )

                IconButton(
                    onClick = onDuplicate,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Duplicate",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
