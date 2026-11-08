package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.ui.theme.LocalDelivioColors
import com.example.ui.theme.StatusClosedRed
import com.example.ui.theme.StatusOpenGreen
import com.example.ui.theme.StatusWarningAmber
import com.example.util.FormatUtils
import com.example.util.FOOD_CATEGORIES
import com.example.util.CONVENIENCE_CATEGORIES

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    settings: MerchantSettings,
    categories: List<MenuCategory>,
    subcategories: List<Subcategory> = emptyList(),
    menus: List<ShopMenu> = emptyList(),
    menuItems: List<MenuItem>,
    promos: List<PromoDeal>,
    businessType: BusinessType,
    currentTime: Long,
    isServiceLocked: Boolean = false,
    serviceLockMessage: String? = null,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    stockFilter: String,
    onStockFilterChange: (String) -> Unit,
    selectedCategoryId: String?,
    onCategorySelect: (String?) -> Unit,
    selectedSubcategoryId: String? = null,
    onSubcategorySelect: (String?) -> Unit = {},
    selectedMenuIdFilter: String? = null,
    onMenuFilterChange: (String?) -> Unit = {},
    isMultiSelectMode: Boolean = false,
    selectedItemIds: Set<String> = emptySet(),
    onToggleMultiSelect: () -> Unit = {},
    onToggleItemSelection: (String) -> Unit = {},
    onBulkMarkInStock: () -> Unit = {},
    onBulkMarkSoldOut: () -> Unit = {},
    onBulkPriceAdjust: (percentDelta: Double, fixedDelta: Double) -> Unit = { _, _ -> },
    onToggleItemAvailability: (MenuItem) -> Unit,
    onAddItem: () -> Unit,
    onEditItem: (MenuItem) -> Unit,
    onDuplicateItem: (String) -> Unit,
    onMoveItemToMenu: (MenuItem, String) -> Unit = { _, _ -> },
    onDeleteItem: (String) -> Unit = {},
    onOpenMenuManagement: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val customColors = LocalDelivioColors.current
    val effectiveOpen = com.example.util.BusinessHoursHelper.isEffectiveOpen(settings, currentTime, isServiceLocked)

    var showFilterBottomSheet by remember { mutableStateOf(false) }
    var showIntroCard by remember { mutableStateOf(true) }
    var itemToDelete by remember { mutableStateOf<MenuItem?>(null) }
    var itemToMoveMenu by remember { mutableStateOf<MenuItem?>(null) }

    // Allowed Main Categories
    val allowedCategories = remember(businessType) {
        if (businessType == BusinessType.FOOD) FOOD_CATEGORIES else CONVENIENCE_CATEGORIES
    }

    fun resolveCategoryName(item: MenuItem): String {
        if (item.categoryName.isNotBlank()) return item.categoryName
        return categories.find { it.id == item.categoryId }?.name ?: ""
    }

    // Products counts for this shop
    val totalProducts = remember(menuItems, businessType) {
        menuItems.count { it.businessType == businessType }
    }
    val soldOutCount = remember(menuItems, businessType) {
        menuItems.count { it.businessType == businessType && !it.inStock }
    }
    val notAvailableNowCount = remember(menuItems, menus, currentTime, settings.timezone, businessType) {
        menuItems.count { it.businessType == businessType && !it.isAvailableNow(menus, currentTime, settings.timezone) }
    }

    // Active filters count for badge
    val activeFilterCount = remember(selectedCategoryId, stockFilter, selectedMenuIdFilter) {
        var count = 0
        if (selectedCategoryId != null) count++
        if (stockFilter != "All") count++
        if (selectedMenuIdFilter != null) count++
        count
    }

    // Filtered items
    val displayedItems = remember(
        menuItems,
        businessType,
        selectedSubcategoryId,
        selectedCategoryId,
        stockFilter,
        selectedMenuIdFilter,
        searchQuery,
        menus,
        currentTime,
        settings.timezone
    ) {
        menuItems.filter { item ->
            val matchType = item.businessType == businessType
            val itemCat = resolveCategoryName(item)

            val matchCategoryClass = when (selectedSubcategoryId) {
                null -> true
                "OTHER" -> item.subcategoryId.isNullOrBlank() && item.subcategoryName.isNullOrBlank()
                else -> item.subcategoryId == selectedSubcategoryId ||
                        item.subcategoryName.equals(selectedSubcategoryId, ignoreCase = true) ||
                        subcategories.find { it.id == selectedSubcategoryId }?.name?.let { item.subcategoryName.equals(it, ignoreCase = true) } == true
            }

            val matchMainCategory = when (selectedCategoryId) {
                null -> true
                else -> {
                    val targetName = categories.find { it.id == selectedCategoryId }?.name ?: selectedCategoryId
                    itemCat.equals(targetName, ignoreCase = true) || item.categoryId == selectedCategoryId
                }
            }

            val matchStock = when (stockFilter) {
                "In stock" -> item.inStock
                "Sold out" -> !item.inStock
                else -> true
            }

            val matchMenu = when (selectedMenuIdFilter) {
                null -> true
                else -> item.getEffectiveMenuIds().contains(selectedMenuIdFilter)
            }

            val matchQuery = searchQuery.isBlank() ||
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    item.description.contains(searchQuery, ignoreCase = true) ||
                    item.tags.any { it.contains(searchQuery, ignoreCase = true) } ||
                    item.sku.contains(searchQuery, ignoreCase = true)

            matchType && matchCategoryClass && matchMainCategory && matchStock && matchMenu && matchQuery
        }
    }

    // Subcategory counts for chips
    val subcategoryCounts = remember(menuItems, subcategories, businessType) {
        subcategories.associate { sub ->
            sub.id to menuItems.count {
                it.businessType == businessType && (it.subcategoryId == sub.id || (it.subcategoryId == null && it.subcategoryName.equals(sub.name, ignoreCase = true)))
            }
        }
    }
    val otherSubcategoryCount = remember(menuItems, businessType) {
        menuItems.count {
            it.businessType == businessType && it.subcategoryId.isNullOrBlank() && it.subcategoryName.isNullOrBlank()
        }
    }

    // Group items by Category Class (Subcategory Name), with "Other" last
    val groupedItems = remember(displayedItems, subcategories) {
        val result = mutableMapOf<String, MutableList<MenuItem>>()
        for (item in displayedItems) {
            val groupName = if (!item.subcategoryId.isNullOrBlank()) {
                subcategories.find { it.id == item.subcategoryId }?.name ?: (item.subcategoryName ?: "Other")
            } else if (!item.subcategoryName.isNullOrBlank()) {
                item.subcategoryName!!
            } else {
                "Other"
            }
            result.getOrPut(groupName) { mutableListOf() }.add(item)
        }

        val sortedKeys = result.keys.sortedWith(Comparator { a, b ->
            if (a == "Other" && b != "Other") 1
            else if (b == "Other" && a != "Other") -1
            else {
                val aIndex = subcategories.indexOfFirst { it.name.equals(a, ignoreCase = true) }
                val bIndex = subcategories.indexOfFirst { it.name.equals(b, ignoreCase = true) }
                if (aIndex != -1 && bIndex != -1) aIndex.compareTo(bIndex)
                else if (aIndex != -1) -1
                else if (bIndex != -1) 1
                else a.compareTo(b, ignoreCase = true)
            }
        })

        sortedKeys.map { key -> key to (result[key] ?: emptyList()) }
    }

    Column(modifier = modifier.fillMaxSize().testTag("menu_screen")) {
        // 1. TOP HEADER: Shop header with status chip
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = settings.storeName.ifBlank { "My Store" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                        Text(
                            text = if (isServiceLocked) serviceLockMessage ?: "Service switched off in this city"
                            else if (effectiveOpen) "Store is open and accepting orders"
                            else "Store is currently closed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Status Chip
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (effectiveOpen) StatusOpenGreen.copy(alpha = 0.15f) else StatusClosedRed.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, if (effectiveOpen) StatusOpenGreen else StatusClosedRed)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (effectiveOpen) StatusOpenGreen else StatusClosedRed)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (effectiveOpen) "Open" else "Closed",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (effectiveOpen) StatusOpenGreen else StatusClosedRed
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search field (products, tags)
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search products, tags, dishes...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("menu_search_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Two buttons side by side: "Menu Management" (outlined, list icon) and "+ Add Product" (yellow/accent)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenMenuManagement,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("menu_management_btn")
                    ) {
                        Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Menu Management", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = onAddItem,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = customColors.accent,
                            contentColor = customColors.onAccent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("add_product_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+ Add Product", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Summary line: "<N> products · <M> sold out · <K> not available now"
                Text(
                    text = "$totalProducts products · $soldOutCount sold out · $notAvailableNowCount not available now",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 2. ONE horizontal chip row of Category Classes ("All" plus classes with counts) and Filter icon with badge
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Filter icon with active filter badge
                BadgedBox(
                    badge = {
                        if (activeFilterCount > 0) {
                            Badge(
                                containerColor = customColors.accent,
                                contentColor = customColors.onAccent
                            ) {
                                Text("$activeFilterCount")
                            }
                        }
                    },
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    IconButton(
                        onClick = { showFilterBottomSheet = true },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (activeFilterCount > 0) customColors.accentTint
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .testTag("menu_filter_btn")
                    ) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = "Filters",
                            tint = if (activeFilterCount > 0) customColors.accent else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Horizontal scroll of Category Classes
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedSubcategoryId == null,
                        onClick = { onSubcategorySelect(null) },
                        label = { Text("All ($totalProducts)", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = customColors.accent,
                            selectedLabelColor = customColors.onAccent
                        ),
                        modifier = Modifier.testTag("class_chip_all")
                    )

                    subcategories.forEach { sub ->
                        val count = subcategoryCounts[sub.id] ?: 0
                        val isSelected = selectedSubcategoryId == sub.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSubcategorySelect(if (isSelected) null else sub.id) },
                            label = { Text("${sub.name} ($count)", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = customColors.accent,
                                selectedLabelColor = customColors.onAccent
                            ),
                            modifier = Modifier.testTag("class_chip_${sub.id}")
                        )
                    }

                    if (otherSubcategoryCount > 0) {
                        val isSelected = selectedSubcategoryId == "OTHER"
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSubcategorySelect(if (isSelected) null else "OTHER") },
                            label = { Text("Other ($otherSubcategoryCount)", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = customColors.accent,
                                selectedLabelColor = customColors.onAccent
                            ),
                            modifier = Modifier.testTag("class_chip_other")
                        )
                    }
                }
            }
        }

        // 3. Products List or Empty State
        if (displayedItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.RestaurantMenu,
                        contentDescription = null,
                        tint = customColors.accent,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "No products yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add your first product to start accepting customer orders.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onAddItem,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = customColors.accent,
                            contentColor = customColors.onAccent
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+ Add Product", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Dismissible intro card: "How your menu works"
                if (showIntroCard && searchQuery.isBlank() && selectedCategoryId == null && selectedSubcategoryId == null) {
                    item(key = "intro_card") {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = customColors.accentTint.copy(alpha = 0.45f)),
                            border = BorderStroke(1.dp, customColors.accent.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth().testTag("menu_intro_card")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = customColors.accent, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "How your menu works",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = customColors.accent
                                        )
                                    }
                                    IconButton(
                                        onClick = { showIntroCard = false },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text("1. Menus: When products can be ordered (e.g. Breakfast from 6 AM).", style = MaterialTheme.typography.bodySmall)
                                Text("2. Category Classes: Sections customers see on your storefront.", style = MaterialTheme.typography.bodySmall)
                                Text("3. Products: Individual dishes, drinks, or retail items.", style = MaterialTheme.typography.bodySmall)
                                Text("4. Sets: Choices customers pick (flavors, drinks, or add-ons).", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                // Grouped product rows
                groupedItems.forEach { (categoryClassName, itemsInGroup) ->
                    item(key = "header_$categoryClassName") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = customColors.accent.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = categoryClassName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = customColors.accent,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "• ${itemsInGroup.size} ${if (itemsInGroup.size == 1) "item" else "items"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }

                    items(itemsInGroup, key = { it.id }) { item ->
                        val isAvailableNow = item.isAvailableNow(menus, currentTime, settings.timezone)
                        val nonMainMenuNames = remember(item, menus) {
                            val eff = item.getEffectiveMenuIds()
                            menus.filter { it.id in eff && it.id != "main" }.map { it.name }
                        }

                        ProductCard(
                            item = item,
                            businessType = businessType,
                            isAvailableNow = isAvailableNow,
                            nonMainMenuNames = nonMainMenuNames,
                            onCardClick = { onEditItem(item) },
                            onToggleStock = { onToggleItemAvailability(item) },
                            onEdit = { onEditItem(item) },
                            onDuplicate = { onDuplicateItem(item.id) },
                            onMoveToMenu = { itemToMoveMenu = item },
                            onDelete = { itemToDelete = item }
                        )
                    }
                }
            }
        }
    }

    // 4. BOTTOM SHEET: Filter by Main category, Stock, Menu, Clear & Apply
    if (showFilterBottomSheet) {
        var tempMainCategory by remember { mutableStateOf(selectedCategoryId) }
        var tempStockFilter by remember { mutableStateOf(stockFilter) }
        var tempMenuFilter by remember { mutableStateOf(selectedMenuIdFilter) }

        ModalBottomSheet(
            onDismissRequest = { showFilterBottomSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Filter Products", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(
                        onClick = {
                            tempMainCategory = null
                            tempStockFilter = "All"
                            tempMenuFilter = null
                        }
                    ) {
                        Text("Clear all")
                    }
                }

                // 1. Main Category filter
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Main Category", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = tempMainCategory == null,
                            onClick = { tempMainCategory = null },
                            label = { Text("All") }
                        )
                        allowedCategories.forEach { catName ->
                            val isSelected = tempMainCategory.equals(catName, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { tempMainCategory = if (isSelected) null else catName },
                                label = { Text(catName) }
                            )
                        }
                    }
                }

                // 2. Stock filter (All, In stock, Sold out)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Stock Availability", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("All", "In stock", "Sold out").forEach { s ->
                            FilterChip(
                                selected = tempStockFilter == s,
                                onClick = { tempStockFilter = s },
                                label = { Text(s) }
                            )
                        }
                    }
                }

                // 3. Menu filter (All plus each menu)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Menu", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = tempMenuFilter == null,
                            onClick = { tempMenuFilter = null },
                            label = { Text("All") }
                        )
                        menus.forEach { m ->
                            val isSelected = tempMenuFilter == m.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { tempMenuFilter = if (isSelected) null else m.id },
                                label = { Text(m.name) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Apply button
                Button(
                    onClick = {
                        onCategorySelect(tempMainCategory)
                        onStockFilterChange(tempStockFilter)
                        onMenuFilterChange(tempMenuFilter)
                        showFilterBottomSheet = false
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = customColors.accent, contentColor = customColors.onAccent),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("apply_menu_filters_btn")
                ) {
                    Text("Apply Filters", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Move to Menu dialog
    itemToMoveMenu?.let { targetItem ->
        var chosenMenuId by remember { mutableStateOf(targetItem.getEffectiveMenuIds().firstOrNull() ?: "main") }
        AlertDialog(
            onDismissRequest = { itemToMoveMenu = null },
            title = { Text("Move \"${targetItem.name}\"", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select which menu will display this product:")
                    menus.forEach { m ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { chosenMenuId = m.id }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = chosenMenuId == m.id,
                                onClick = { chosenMenuId = m.id }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(m.name, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onMoveItemToMenu(targetItem, chosenMenuId)
                        itemToMoveMenu = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = customColors.accent, contentColor = customColors.onAccent)
                ) {
                    Text("Move")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToMoveMenu = null }) { Text("Cancel") }
            }
        )
    }

    // Delete confirmation dialog
    itemToDelete?.let { targetItem ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete Product", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = { Text("Are you sure you want to delete \"${targetItem.name}\"? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteItem(targetItem.id)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProductCard(
    item: MenuItem,
    businessType: BusinessType,
    isAvailableNow: Boolean,
    nonMainMenuNames: List<String>,
    onCardClick: () -> Unit,
    onToggleStock: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveToMenu: () -> Unit,
    onDelete: () -> Unit
) {
    val customColors = LocalDelivioColors.current
    var showMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!item.inStock) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (item.inStock) 1.5.dp else 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
            .testTag("product_card_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rounded image: neutral icon when there is no image (no emoji)
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                if (item.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = if (businessType == BusinessType.FOOD) Icons.Default.Restaurant else Icons.Default.ShoppingBag,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )

                Text(
                    text = FormatUtils.formatCurrency(item.price),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = if (item.inStock) customColors.accent else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Small chips row: sets, sold out, menu name (when not Main Menu), "Not available now" in amber
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Prep time chip
                    if (businessType == BusinessType.FOOD && item.prepTimeMinutes > 0) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "${item.prepTimeMinutes}m prep",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Sets chip: e.g. "2 sets"
                    if (item.hasSets && item.sets.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${item.sets.size} ${if (item.sets.size == 1) "set" else "sets"}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Sold out chip
                    if (!item.inStock) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = StatusClosedRed.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Sold out",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusClosedRed,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    } else if (item.trackStock && item.stockCount <= item.lowStockAt) {
                        // Low stock alert amber chip
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = StatusWarningAmber.copy(alpha = 0.20f)
                        ) {
                            Text(
                                text = "Low stock (${item.stockCount} left)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Non-main menu name chip(s)
                    nonMainMenuNames.forEach { menuName ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = customColors.accent.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = menuName,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = customColors.accent,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Not available now in amber when its menus are closed
                    if (!isAvailableNow && item.inStock) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = StatusWarningAmber.copy(alpha = 0.20f)
                        ) {
                            Text(
                                text = "Not available now",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // In stock switch and ⋮ menu
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

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp).testTag("menu_more_btn_${item.id}")
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onDuplicate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Move to menu") },
                            leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onMoveToMenu()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}
