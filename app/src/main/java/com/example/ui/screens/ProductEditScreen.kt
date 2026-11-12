package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.ui.theme.LocalDelivioColors
import com.example.ui.theme.StatusClosedRed
import com.example.ui.theme.StatusOpenGreen
import com.example.util.CurrencyConstants
import com.example.util.FOOD_CATEGORIES
import com.example.util.CONVENIENCE_CATEGORIES
import com.example.util.FormatUtils
import kotlinx.coroutines.launch
import java.util.UUID

private class EditableChoice(
    val id: String = UUID.randomUUID().toString(),
    initialName: String = "",
    initialPriceText: String = "0",
    initialAvailable: Boolean = true,
    initialPopular: Boolean = false
) {
    var name by mutableStateOf(initialName)
    var priceText by mutableStateOf(initialPriceText)
    var available by mutableStateOf(initialAvailable)
    var popular by mutableStateOf(initialPopular)
    var wasTouched by mutableStateOf(false)
}

private class EditableSet(
    val id: String = UUID.randomUUID().toString(),
    initialName: String = "",
    initialRequired: Boolean = true,
    initialMinSelect: Int = 1,
    initialMaxSelect: Int = 1,
    initialChoices: List<EditableChoice> = listOf(EditableChoice())
) {
    var name by mutableStateOf(initialName)
    var required by mutableStateOf(initialRequired)
    var minSelect by mutableIntStateOf(initialMinSelect)
    var maxSelect by mutableIntStateOf(initialMaxSelect)
    var wasTouched by mutableStateOf(false)
    var showAdvancedMin by mutableStateOf(false)
    val choices = mutableStateListOf<EditableChoice>().apply { addAll(initialChoices) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductEditScreen(
    item: MenuItem,
    isNew: Boolean,
    categories: List<MenuCategory>,
    subcategories: List<Subcategory> = emptyList(),
    menus: List<ShopMenu> = emptyList(),
    menuItems: List<MenuItem> = emptyList(),
    businessType: BusinessType,
    onSave: (MenuItem, onComplete: (Boolean, String?) -> Unit) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreateSubcategory: (String, onComplete: (Boolean, Subcategory?, String?) -> Unit) -> Unit = { _, _ -> },
    onRenameSubcategory: (String, String, onComplete: (Boolean, String?) -> Unit) -> Unit = { _, _, _ -> },
    onDeleteSubcategory: (String, onComplete: (Boolean, String?) -> Unit) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    BackHandler { onDismiss() }

    val customColors = LocalDelivioColors.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

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
                ""
            }
        }
    }

    // 1. Basic info
    var name by remember { mutableStateOf(item.name) }
    var priceText by remember { mutableStateOf(if (item.price > 0.0) item.price.toString() else "0") }
    var itemSizeText by remember { mutableStateOf(item.itemSize) }
    var selectedCategoryName by remember { mutableStateOf(initialCatName) }
    var selectedCategoryId by remember {
        mutableStateOf(
            if (initialCatName.isNotBlank()) {
                categories.find { it.name.equals(initialCatName, ignoreCase = true) }?.id
                    ?: initialCatName.lowercase().trim().replace("&", "and").replace("[^a-z0-9]+".toRegex(), "_").trim('_')
            } else ""
        )
    }

    // Category Class
    var selectedSubcategoryId by remember { mutableStateOf(item.subcategoryId ?: "") }
    var selectedSubcategoryName by remember {
        mutableStateOf(
            if (!item.subcategoryId.isNullOrBlank()) {
                subcategories.find { it.id == item.subcategoryId }?.name ?: (item.subcategoryName ?: "")
            } else {
                item.subcategoryName ?: ""
            }
        )
    }

    // 2. Photo
    var imageUrl by remember { mutableStateOf(item.imageUrl) }
    var imageLoadFailed by remember { mutableStateOf(false) }

    // 3. Description
    var description by remember { mutableStateOf(item.description) }

    // 4. Menus & Availability
    val initialSelectedMenuIds = remember(item) {
        val eff = item.getEffectiveMenuIds()
        eff.toSet().ifEmpty { setOf("main") }
    }
    var selectedMenuIds by remember { mutableStateOf(initialSelectedMenuIds) }

    // Availability: Always available (null) vs Set days and times
    val initialAvailability = item.availability
    var availabilityMode by remember { mutableStateOf(if (initialAvailability != null) "custom" else "always") }
    var availDays by remember {
        mutableStateOf(
            initialAvailability?.days?.toSet() ?: setOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
        )
    }
    var availStartTime24 by remember { mutableStateOf(initialAvailability?.start ?: "00:00") }
    var availEndTime24 by remember { mutableStateOf(initialAvailability?.end ?: "23:59") }
    var limitToDates by remember {
        mutableStateOf(!initialAvailability?.dateFrom.isNullOrBlank() || !initialAvailability?.dateTo.isNullOrBlank())
    }
    var availDateFrom by remember { mutableStateOf(initialAvailability?.dateFrom ?: "") }
    var availDateTo by remember { mutableStateOf(initialAvailability?.dateTo ?: "") }
    var showTimePickerForAvailStart by remember { mutableStateOf(false) }
    var showTimePickerForAvailEnd by remember { mutableStateOf(false) }
    var showDatePickerForFrom by remember { mutableStateOf(false) }
    var showDatePickerForTo by remember { mutableStateOf(false) }

    // 5. Options (Sets)
    val setsList = remember {
        mutableStateListOf<EditableSet>().apply {
            if (item.hasSets && item.sets.isNotEmpty()) {
                addAll(item.sets.mapIndexed { sIdx, s ->
                    val resolvedName = s.name.takeIf { it.isNotBlank() }
                        ?: s.customName.takeIf { it.isNotBlank() }
                        ?: s.subcategoryName.takeIf { !it.isNullOrBlank() }
                        ?: "Set ${sIdx + 1}"
                    EditableSet(
                        id = s.id.ifBlank { UUID.randomUUID().toString() },
                        initialName = resolvedName,
                        initialRequired = s.required,
                        initialMinSelect = if (s.required) s.minSelect.coerceAtLeast(1) else 0,
                        initialMaxSelect = s.maxSelect.coerceAtLeast(1),
                        initialChoices = s.choices.map { c ->
                            EditableChoice(
                                id = c.id.ifBlank { UUID.randomUUID().toString() },
                                initialName = c.name,
                                initialPriceText = if (c.price == 0.0) "0" else (if (c.price % 1.0 == 0.0) c.price.toInt().toString() else c.price.toString()),
                                initialAvailable = c.available,
                                initialPopular = c.popular
                            )
                        }.ifEmpty { listOf(EditableChoice()) }
                    )
                })
            }
        }
    }

    // 6. Stock
    var inStock by remember { mutableStateOf(item.inStock) }
    var trackStock by remember { mutableStateOf(item.trackStock) }
    var stockCountText by remember { mutableStateOf(item.stockCount.toString()) }
    var lowStockThresholdText by remember { mutableStateOf(item.lowStockAt.toString()) }

    // 7. Kitchen & tags (collapsed by default)
    var isKitchenExpanded by remember { mutableStateOf(!isNew) }
    var prepTimeText by remember { mutableStateOf(item.prepTimeMinutes.toString()) }
    var selectedTags by remember { mutableStateOf(item.tags.toSet()) }
    var sku by remember { mutableStateOf(item.sku) }
    var barcode by remember { mutableStateOf(item.barcode) }
    var unit by remember { mutableStateOf(item.unit) }

    // Validation & Error state
    var hasTappedSaveOnce by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var failureErrorMessage by remember { mutableStateOf<String?>(null) }
    var activeHelpDialogText by remember { mutableStateOf<String?>(null) }

    // Subcategory dialogs
    var showTypeCategoryDialog by remember { mutableStateOf(false) }
    var showManageCategoriesDialog by remember { mutableStateOf(false) }
    var subcategoryToRename by remember { mutableStateOf<Subcategory?>(null) }
    var subcategoryToDelete by remember { mutableStateOf<Subcategory?>(null) }
    var setIndexToDelete by remember { mutableStateOf<Int?>(null) }

    // Validation computations
    val parsedPrice = priceText.toDoubleOrNull()
    val isPriceValid = parsedPrice != null && parsedPrice >= 0.0
    val isNameValid = name.isNotBlank()
    val isCatMissing = selectedCategoryName.isBlank() || !allowedCategories.any { it.equals(selectedCategoryName, ignoreCase = true) }

    val setErrors = remember(setsList.map { listOf(it.name, it.required.toString(), it.minSelect.toString(), it.maxSelect.toString(), it.choices.size.toString()) + it.choices.map { c -> "${c.name}:${c.priceText}:${c.available}:${c.popular}" } }) {
        val errors = mutableListOf<String>()
        val seenNames = mutableSetOf<String>()

        setsList.forEachIndexed { sIdx, set ->
            val sNum = sIdx + 1
            val trimmedSetName = set.name.trim()
            if (trimmedSetName.isBlank()) {
                errors.add("Set $sNum needs a name")
            } else if (trimmedSetName.length > 50) {
                errors.add("Set $sNum name cannot exceed 50 characters")
            } else if (!seenNames.add(trimmedSetName.lowercase())) {
                errors.add("Set $sNum has duplicate name \"${set.name}\"")
            }

            if (set.choices.isEmpty()) {
                errors.add("Set $sNum needs at least one choice")
            } else {
                val choiceSeen = mutableSetOf<String>()
                set.choices.forEachIndexed { cIdx, choice ->
                    val cNum = cIdx + 1
                    val trimmedChoiceName = choice.name.trim()
                    if (trimmedChoiceName.isBlank()) {
                        errors.add("Set $sNum choice $cNum needs a name")
                    } else if (!choiceSeen.add(trimmedChoiceName.lowercase())) {
                        errors.add("Set $sNum has duplicate choice \"${choice.name}\"")
                    }
                    val cPrice = choice.priceText.toDoubleOrNull()
                    if (cPrice == null || cPrice < 0.0) {
                        errors.add("Set $sNum choice $cNum needs a valid price")
                    }
                }
                if (set.required && set.minSelect > set.maxSelect) {
                    errors.add("Set $sNum minimum choices cannot exceed maximum choices")
                }
            }
        }
        errors
    }

    val canSave = isNameValid && !isCatMissing && isPriceValid && setErrors.isEmpty() && !isSaving

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("product_edit_screen"),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag("product_edit_back_btn")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isNew) "Add Product" else "Edit Product",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!isNew) {
                        IconButton(
                            onClick = { onDelete(item.id) },
                            modifier = Modifier.testTag("delete_menu_item_btn")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Sticky Save Bar
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp, bottom = 16.dp)
                ) {
                    // Summary line says what is missing when Save cannot proceed
                    if (!canSave) {
                        val summaryText = when {
                            !isNameValid -> "Product name is required"
                            isCatMissing -> "Main Category is required"
                            !isPriceValid -> "Please enter a valid price"
                            setErrors.isNotEmpty() -> setErrors.first()
                            else -> "Please correct highlighted fields above"
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = summaryText,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("product_edit_cancel_btn")
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                hasTappedSaveOnce = true
                                if (!canSave) return@Button
                                isSaving = true

                                val priceVal = parsedPrice ?: 0.0
                                val parsedPrep = prepTimeText.toIntOrNull() ?: item.prepTimeMinutes
                                val parsedStock = stockCountText.toIntOrNull() ?: item.stockCount
                                val parsedThreshold = lowStockThresholdText.toIntOrNull() ?: item.lowStockAt

                                val finalCatName = selectedCategoryName
                                val finalCatId = selectedCategoryId.ifBlank {
                                    categories.find { it.name.equals(finalCatName, ignoreCase = true) }?.id
                                        ?: finalCatName.lowercase().trim().replace("&", "and").replace("[^a-z0-9]+".toRegex(), "_").trim('_')
                                }

                                val finalSets = setsList.mapIndexed { idx, s ->
                                    val trimmedName = s.name.trim()
                                    ProductSet(
                                        id = s.id,
                                        name = trimmedName,
                                        subcategoryId = null,
                                        subcategoryName = null,
                                        customName = trimmedName,
                                        required = s.required,
                                        minSelect = if (s.required) s.minSelect.coerceIn(1, s.maxSelect) else 0,
                                        maxSelect = s.maxSelect.coerceIn(1, maxOf(1, s.choices.size)),
                                        sortOrder = idx,
                                        choices = s.choices.map { c ->
                                            ProductSetChoice(
                                                id = c.id,
                                                name = c.name.trim(),
                                                price = c.priceText.toDoubleOrNull() ?: 0.0,
                                                available = c.available,
                                                popular = c.popular
                                            )
                                        }
                                    )
                                }

                                val finalMenuIds = selectedMenuIds.ifEmpty { setOf("main") }.toList()

                                val finalAvailability = if (availabilityMode == "custom") {
                                    ProductAvailability(
                                        days = availDays.toList(),
                                        start = availStartTime24,
                                        end = availEndTime24,
                                        dateFrom = if (limitToDates && availDateFrom.isNotBlank()) availDateFrom else null,
                                        dateTo = if (limitToDates && availDateTo.isNotBlank()) availDateTo else null
                                    )
                                } else null

                                val updated = item.copy(
                                    name = name.trim().ifBlank { "Untitled Item" },
                                    description = description.trim(),
                                    price = priceVal,
                                    categoryId = finalCatId,
                                    categoryName = finalCatName,
                                    subcategoryId = selectedSubcategoryId.ifBlank { null },
                                    subcategoryName = selectedSubcategoryName.ifBlank { null },
                                    menuIds = finalMenuIds,
                                    imageUrl = imageUrl.trim(),
                                    prepTimeMinutes = if (businessType == BusinessType.FOOD) parsedPrep else 0,
                                    tags = if (businessType == BusinessType.FOOD) selectedTags.toList() else emptyList(),
                                    sku = if (businessType == BusinessType.CONVENIENCE) sku.trim() else "",
                                    barcode = if (businessType == BusinessType.CONVENIENCE) barcode.trim() else "",
                                    unit = if (businessType == BusinessType.CONVENIENCE) unit.trim().ifBlank { "1 pc" } else "1 pc",
                                    itemSize = if (businessType == BusinessType.CONVENIENCE) itemSizeText.trim() else "",
                                    availability = finalAvailability,
                                    trackStock = trackStock,
                                    stockCount = parsedStock,
                                    lowStockAt = parsedThreshold,
                                    inStock = if (trackStock && parsedStock <= 0) false else inStock,
                                    businessType = businessType,
                                    hasSets = finalSets.isNotEmpty(),
                                    sets = finalSets
                                )

                                onSave(updated) { success, errorMsg ->
                                    isSaving = false
                                    if (!success) {
                                        failureErrorMessage = errorMsg ?: "Unknown error occurred while saving product."
                                    }
                                }
                            },
                            enabled = canSave,
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
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = customColors.onAccent,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Save Product", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SECTION 1: Photo (first, at the very top)
            SectionCard(
                title = "Photo",
                helpText = "Upload a high quality HTTPS image URL for this dish or product.",
                onHelpClick = { activeHelpDialogText = it }
            ) {
                // Large square preview at top (neutral icon placeholder when empty)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUrl.isNotBlank()) {
                        AsyncImage(
                            model = imageUrl.trim(),
                            contentDescription = "Photo Preview",
                            contentScale = ContentScale.Crop,
                            onError = { imageLoadFailed = true },
                            onSuccess = { imageLoadFailed = false },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (businessType == BusinessType.FOOD) Icons.Default.Restaurant else Icons.Default.ShoppingBag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "No photo yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Image URL field
                OutlinedTextField(
                    value = imageUrl,
                    onValueChange = {
                        imageUrl = it
                        imageLoadFailed = false
                    },
                    label = { Text("Image URL (HTTPS)") },
                    placeholder = { Text("https://example.com/item.jpg") },
                    leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("menu_item_image_url_input")
                )

                // Small status chip (OK or Could not load)
                if (imageUrl.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (imageLoadFailed) StatusClosedRed.copy(alpha = 0.15f) else StatusOpenGreen.copy(alpha = 0.15f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (imageLoadFailed) StatusClosedRed else StatusOpenGreen)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (imageLoadFailed) "Could not load" else "OK",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (imageLoadFailed) StatusClosedRed else StatusOpenGreen
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 2: Basic info
            SectionCard(
                title = "Basic info",
                helpText = "Product details that identify this item on your storefront: name, price, main category, and shop category class.",
                onHelpClick = { activeHelpDialogText = it }
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product Name *") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    isError = hasTappedSaveOnce && !isNameValid,
                    supportingText = if (hasTappedSaveOnce && !isNameValid) {
                        { Text("Product name is required", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    modifier = Modifier.fillMaxWidth().testTag("menu_item_name_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text("Price (${CurrencyConstants.SYMBOL}) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        isError = hasTappedSaveOnce && !isPriceValid,
                        supportingText = if (hasTappedSaveOnce && !isPriceValid) {
                            { Text("Invalid price", color = MaterialTheme.colorScheme.error) }
                        } else null,
                        modifier = Modifier.weight(1f).testTag("menu_item_price_input")
                    )

                    var catExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = catExpanded,
                        onExpandedChange = { catExpanded = it },
                        modifier = Modifier.weight(1.3f)
                    ) {
                        OutlinedTextField(
                            value = if (selectedCategoryName.isNotBlank()) selectedCategoryName else "Category *",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Main Category *") },
                            isError = hasTappedSaveOnce && isCatMissing,
                            supportingText = if (hasTappedSaveOnce && isCatMissing) {
                                { Text("Required", color = MaterialTheme.colorScheme.error) }
                            } else null,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.menuAnchor().fillMaxWidth().testTag("menu_item_category_dropdown")
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
                                        selectedCategoryId = matchedDoc?.id
                                            ?: catName.lowercase().trim().replace("&", "and").replace("[^a-z0-9]+".toRegex(), "_").trim('_')
                                        catExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Item size (CONVENIENCE SHOPS ONLY) directly under Price
                if (businessType == BusinessType.CONVENIENCE) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = itemSizeText,
                            onValueChange = { if (it.length <= 20) itemSizeText = it },
                            label = { Text("Item size (optional)") },
                            placeholder = { Text("e.g. 190ml, 225g, 12pcs") },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("convenience_item_size_input")
                        )

                        // Small unit chips (ml, g, kg, L, pcs, pack)
                        val unitChips = listOf("ml", "g", "kg", "L", "pcs", "pack")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            unitChips.forEach { u ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        val current = itemSizeText.trim()
                                        val next = if (current.isEmpty()) u else "$current$u"
                                        if (next.length <= 20) itemSizeText = next
                                    },
                                    label = { Text(u, fontSize = 11.sp) }
                                )
                            }
                        }

                        // Live preview: "₱92.92 /225g"
                        val priceNum = parsedPrice ?: 0.0
                        val formattedPrice = FormatUtils.formatCurrency(priceNum)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = "Preview: ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formattedPrice,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = customColors.accent
                            )
                            if (itemSizeText.isNotBlank()) {
                                Text(
                                    text = " /${itemSizeText.trim()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Category Class Dropdown with + Add and Manage icon
                var classExpanded by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExposedDropdownMenuBox(
                        expanded = classExpanded,
                        onExpandedChange = { classExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = if (selectedSubcategoryName.isNotBlank()) selectedSubcategoryName else "Other (None)",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category Class") },
                            supportingText = { Text("Sections customers see on your menu") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = classExpanded) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.menuAnchor().fillMaxWidth().testTag("category_class_dropdown")
                        )
                        ExposedDropdownMenu(
                            expanded = classExpanded,
                            onDismissRequest = { classExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Other (None)", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) },
                                onClick = {
                                    selectedSubcategoryId = ""
                                    selectedSubcategoryName = ""
                                    classExpanded = false
                                }
                            )
                            subcategories.forEach { sub ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            sub.name,
                                            fontWeight = if (selectedSubcategoryId == sub.id) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        selectedSubcategoryId = sub.id
                                        selectedSubcategoryName = sub.name
                                        classExpanded = false
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = customColors.accent, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("+ Add", color = customColors.accent, fontWeight = FontWeight.Bold)
                                    }
                                },
                                onClick = {
                                    classExpanded = false
                                    showTypeCategoryDialog = true
                                }
                            )
                        }
                    }

                    IconButton(
                        onClick = { showManageCategoriesDialog = true },
                        modifier = Modifier.padding(top = 4.dp).testTag("manage_subcategories_btn")
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = "Manage Categories", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // SECTION 3: Description
            SectionCard(
                title = "Description",
                helpText = "Describe ingredients, portion sizes, flavors, and anything helpful for hungry customers.",
                onHelpClick = { activeHelpDialogText = it }
            ) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Product description") },
                    placeholder = { Text("e.g. Crisp golden patty topped with melted cheddar, fresh lettuce...") },
                    shape = RoundedCornerShape(12.dp),
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // SECTION 4: Menus and availability
            SectionCard(
                title = "Menus and availability",
                helpText = "Choose which menus this product will belong to and set optional product-specific availability hours or date limits.",
                onHelpClick = { activeHelpDialogText = it }
            ) {
                Text(
                    text = "Show this product in:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val menuListToDisplay = remember(menus) {
                    if (menus.isEmpty()) listOf(ShopMenu(id = "main", name = "Main Menu"))
                    else menus
                }

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    menuListToDisplay.forEach { m ->
                        val isSelected = selectedMenuIds.contains(m.id)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedMenuIds = if (isSelected) {
                                    val next = selectedMenuIds - m.id
                                    if (next.isEmpty()) setOf("main") else next
                                } else {
                                    selectedMenuIds + m.id
                                }
                            },
                            label = {
                                Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text(m.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                    Text(m.scheduleSummary(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = customColors.accentTint,
                                selectedLabelColor = customColors.accent
                            )
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Availability Segmented Choice: Always available vs Set days and times
                Text(
                    text = "Availability",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = availabilityMode == "always",
                        onClick = { availabilityMode = "always" },
                        label = { Text("Always available", fontWeight = if (availabilityMode == "always") FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = customColors.accentTint,
                            selectedLabelColor = customColors.accent
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = availabilityMode == "custom",
                        onClick = { availabilityMode = "custom" },
                        label = { Text("Set days and times", fontWeight = if (availabilityMode == "custom") FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = customColors.accentTint,
                            selectedLabelColor = customColors.accent
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (availabilityMode == "custom") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Choose when customers can order this product.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Quick Day Presets
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = availDays.size == 7,
                                onClick = { availDays = setOf("mon", "tue", "wed", "thu", "fri", "sat", "sun") },
                                label = { Text("Every day", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = availDays == setOf("mon", "tue", "wed", "thu", "fri"),
                                onClick = { availDays = setOf("mon", "tue", "wed", "thu", "fri") },
                                label = { Text("Weekdays", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = availDays == setOf("sat", "sun"),
                                onClick = { availDays = setOf("sat", "sun") },
                                label = { Text("Weekends", fontSize = 11.sp) }
                            )
                        }

                        // Mon to Sun Day chips
                        val weekDays = listOf(
                            "mon" to "Mon",
                            "tue" to "Tue",
                            "wed" to "Wed",
                            "thu" to "Thu",
                            "fri" to "Fri",
                            "sat" to "Sat",
                            "sun" to "Sun"
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            weekDays.forEach { (key, label) ->
                                val isSelected = availDays.contains(key)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        availDays = if (isSelected) {
                                            val next = availDays - key
                                            if (next.isEmpty()) setOf("mon") else next
                                        } else {
                                            availDays + key
                                        }
                                    },
                                    label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = customColors.accent,
                                        selectedLabelColor = customColors.onAccent
                                    )
                                )
                            }
                        }

                        // Start time & End time (12-hour, 15-minute steps)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedCard(
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).clickable { showTimePickerForAvailStart = true }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Start time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = MenuSchedule.formatTime12h(availStartTime24),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = customColors.accent
                                    )
                                }
                            }

                            OutlinedCard(
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).clickable { showTimePickerForAvailEnd = true }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("End time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = MenuSchedule.formatTime12h(availEndTime24),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = customColors.accent
                                    )
                                }
                            }
                        }

                        // Overnight check & hint
                        val isOvernightAvail = remember(availStartTime24, availEndTime24) {
                            val (sH, sM) = availStartTime24.split(":").mapNotNull { it.toIntOrNull() }.let { if (it.size == 2) it[0] to it[1] else 0 to 0 }
                            val (eH, eM) = availEndTime24.split(":").mapNotNull { it.toIntOrNull() }.let { if (it.size == 2) it[0] to it[1] else 23 to 59 }
                            (eH * 60 + eM) < (sH * 60 + sM)
                        }

                        if (isOvernightAvail) {
                            Surface(
                                color = customColors.accentTint.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = null,
                                        tint = customColors.accent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Overnight schedule: Ends after midnight on the following day.",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = customColors.accent
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        // Limit to dates switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Limit to dates", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text("Set a seasonal or promotional date range", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = limitToDates,
                                onCheckedChange = { limitToDates = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = customColors.accent,
                                    checkedTrackColor = customColors.accentTint
                                )
                            )
                        }

                        if (limitToDates) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedCard(
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).clickable { showDatePickerForFrom = true }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("From date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = availDateFrom.ifBlank { "YYYY-MM-DD" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (availDateFrom.isNotBlank()) FontWeight.Bold else FontWeight.Normal,
                                            color = if (availDateFrom.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }

                                OutlinedCard(
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).clickable { showDatePickerForTo = true }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("To date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = availDateTo.ifBlank { "YYYY-MM-DD" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (availDateTo.isNotBlank()) FontWeight.Bold else FontWeight.Normal,
                                            color = if (availDateTo.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }

                        // Summary line: e.g. "Available Mon-Fri, 6:00 AM - 10:00 AM, Nov 20 - Dec 31"
                        val tempAvail = ProductAvailability(
                            days = availDays.toList(),
                            start = availStartTime24,
                            end = availEndTime24,
                            dateFrom = if (limitToDates && availDateFrom.isNotBlank()) availDateFrom else null,
                            dateTo = if (limitToDates && availDateTo.isNotBlank()) availDateTo else null
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.EventAvailable, contentDescription = null, tint = customColors.accent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tempAvail.summary(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 5: Options (Sets)
            SectionCard(
                title = "Options (Sets)",
                helpText = "Sets let customers choose options such as flavor, drink, or add-ons. Example: set 'Drink' with Iced Tea (Free) and Coke (+₱10).",
                onHelpClick = { activeHelpDialogText = it }
            ) {
                Text(
                    text = "Sets let customers choose options such as flavor, drink, or add-ons. Example: set 'Drink' with Iced Tea (Free) and Coke (+₱10).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (setsList.isEmpty()) {
                    OutlinedButton(
                        onClick = { setsList.add(EditableSet(initialChoices = listOf(EditableChoice()))) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("add_set_initial_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+ Add Set", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        setsList.forEachIndexed { setIndex, pSet ->
                            val setNum = setIndex + 1
                            val isDuplicateSetName = setsList.indices.any { it != setIndex && setsList[it].name.trim().equals(pSet.name.trim(), ignoreCase = true) && pSet.name.isNotBlank() }
                            val isSetNameBlank = pSet.name.trim().isBlank()
                            val showSetNameError = (pSet.wasTouched || hasTappedSaveOnce) && (isSetNameBlank || isDuplicateSetName || pSet.name.length > 50)
                            val hasSetError = hasTappedSaveOnce && (isSetNameBlank || isDuplicateSetName || pSet.choices.isEmpty() || pSet.choices.any { it.name.trim().isBlank() })

                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                border = BorderStroke(1.dp, if (hasSetError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth().testTag("set_card_$setIndex")
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    // 1. Header: Set title + action icons (duplicate, move up, move down, delete)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Set $setNum",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = customColors.accent
                                        )

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // Duplicate icon
                                            IconButton(
                                                onClick = {
                                                    val clonedChoices = pSet.choices.map { c ->
                                                        EditableChoice(
                                                            id = UUID.randomUUID().toString(),
                                                            initialName = c.name,
                                                            initialPriceText = c.priceText,
                                                            initialAvailable = c.available,
                                                            initialPopular = c.popular
                                                        )
                                                    }
                                                    val clonedSet = EditableSet(
                                                        id = UUID.randomUUID().toString(),
                                                        initialName = if (pSet.name.isNotBlank()) "${pSet.name} (Copy)" else "",
                                                        initialRequired = pSet.required,
                                                        initialMinSelect = pSet.minSelect,
                                                        initialMaxSelect = pSet.maxSelect,
                                                        initialChoices = clonedChoices
                                                    )
                                                    setsList.add(setIndex + 1, clonedSet)
                                                },
                                                modifier = Modifier.size(36.dp).testTag("duplicate_set_btn_$setIndex")
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate Set", modifier = Modifier.size(18.dp))
                                            }

                                            // Move Up icon
                                            IconButton(
                                                onClick = {
                                                    if (setIndex > 0) {
                                                        val temp = setsList.removeAt(setIndex)
                                                        setsList.add(setIndex - 1, temp)
                                                    }
                                                },
                                                enabled = setIndex > 0,
                                                modifier = Modifier.size(36.dp).testTag("move_up_set_btn_$setIndex")
                                            ) {
                                                Icon(
                                                    Icons.Default.ArrowUpward,
                                                    contentDescription = "Move Up",
                                                    tint = if (setIndex > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            // Move Down icon
                                            IconButton(
                                                onClick = {
                                                    if (setIndex < setsList.size - 1) {
                                                        val temp = setsList.removeAt(setIndex)
                                                        setsList.add(setIndex + 1, temp)
                                                    }
                                                },
                                                enabled = setIndex < setsList.size - 1,
                                                modifier = Modifier.size(36.dp).testTag("move_down_set_btn_$setIndex")
                                            ) {
                                                Icon(
                                                    Icons.Default.ArrowDownward,
                                                    contentDescription = "Move Down",
                                                    tint = if (setIndex < setsList.size - 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            // Delete icon (confirm first)
                                            IconButton(
                                                onClick = { setIndexToDelete = setIndex },
                                                modifier = Modifier.size(36.dp).testTag("delete_set_btn_$setIndex")
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete Set", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }

                                    // 2. Set Name single-line text field
                                    OutlinedTextField(
                                        value = pSet.name,
                                        onValueChange = {
                                            if (it.length <= 50) {
                                                pSet.name = it
                                            }
                                            pSet.wasTouched = true
                                        },
                                        label = { Text("Set name *") },
                                        placeholder = { Text("e.g. 1 pc. Choice of Flavor") },
                                        supportingText = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = if (showSetNameError) {
                                                        when {
                                                            isSetNameBlank -> "Set name is required"
                                                            isDuplicateSetName -> "Set name must be unique"
                                                            pSet.name.length > 50 -> "Max 50 characters"
                                                            else -> "Customers see this as the title of the options"
                                                        }
                                                    } else {
                                                        "Customers see this as the title of the options"
                                                    },
                                                    color = if (showSetNameError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "${pSet.name.length}/50",
                                                    color = if (pSet.name.length >= 50) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        isError = showSetNameError,
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().testTag("set_name_input_$setIndex")
                                    )

                                    // 3. Compact Row: Required switch on left + rounded "Up to X" stepper on right
                                    val maxChoicesCount = maxOf(1, pSet.choices.size)

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Left: Required switch
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Switch(
                                                        checked = pSet.required,
                                                        onCheckedChange = { isReq ->
                                                            pSet.required = isReq
                                                            if (isReq) {
                                                                pSet.minSelect = maxOf(1, pSet.minSelect)
                                                                if (pSet.maxSelect < pSet.minSelect) pSet.maxSelect = pSet.minSelect
                                                            } else {
                                                                pSet.minSelect = 0
                                                            }
                                                            // Close advanced min if equal to default
                                                            if (pSet.minSelect == (if (isReq) 1 else 0)) {
                                                                pSet.showAdvancedMin = false
                                                            }
                                                        },
                                                        colors = SwitchDefaults.colors(
                                                            checkedThumbColor = Color(0xFF1A1A1A),
                                                            checkedTrackColor = customColors.accent,
                                                            checkedBorderColor = Color(0xFF1A1A1A),
                                                            uncheckedThumbColor = Color(0xFF757575),
                                                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                                        ),
                                                        modifier = Modifier.testTag("set_required_switch_$setIndex")
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column {
                                                        Text(
                                                            text = "Required",
                                                            fontWeight = FontWeight.Bold,
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                        Text(
                                                            text = if (pSet.required) "Must pick" else "Optional",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }

                                                // Right: Rounded stepper (minus, "Up to X", plus)
                                                Surface(
                                                    shape = RoundedCornerShape(20.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    ) {
                                                        val minAllowedMax = if (pSet.required) maxOf(1, pSet.minSelect) else 1
                                                        IconButton(
                                                            onClick = {
                                                                if (pSet.maxSelect > minAllowedMax) {
                                                                    pSet.maxSelect -= 1
                                                                    if (pSet.minSelect > pSet.maxSelect) {
                                                                        pSet.minSelect = pSet.maxSelect
                                                                    }
                                                                }
                                                            },
                                                            enabled = pSet.maxSelect > minAllowedMax,
                                                            modifier = Modifier.size(28.dp).testTag("decrease_max_$setIndex")
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Remove,
                                                                contentDescription = "Decrease max choices",
                                                                tint = if (pSet.maxSelect > minAllowedMax) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                        }

                                                        Text(
                                                            text = "Up to ${pSet.maxSelect}",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp,
                                                            modifier = Modifier.padding(horizontal = 8.dp)
                                                        )

                                                        IconButton(
                                                            onClick = {
                                                                if (pSet.maxSelect < maxChoicesCount) {
                                                                    pSet.maxSelect += 1
                                                                }
                                                            },
                                                            enabled = pSet.maxSelect < maxChoicesCount,
                                                            modifier = Modifier.size(28.dp).testTag("increase_max_$setIndex")
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Add,
                                                                contentDescription = "Increase max choices",
                                                                tint = if (pSet.maxSelect < maxChoicesCount) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            // "Need a higher minimum?" tiny text button
                                            val defaultMin = if (pSet.required) 1 else 0
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                TextButton(
                                                    onClick = {
                                                        pSet.showAdvancedMin = !pSet.showAdvancedMin
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = if (pSet.showAdvancedMin) "Hide minimum choices" else "Need a higher minimum?",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }

                                                if (pSet.minSelect > defaultMin && !pSet.showAdvancedMin) {
                                                    Text(
                                                        text = "Min: ${pSet.minSelect}",
                                                        fontSize = 11.sp,
                                                        color = customColors.accent,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            // Advanced minimum stepper (1 up to maximum)
                                            if (pSet.showAdvancedMin) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = "Minimum required",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                        Text(
                                                            text = "Customers must select at least this many",
                                                            fontSize = 10.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }

                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        val minFloor = if (pSet.required) 1 else 0
                                                        OutlinedIconButton(
                                                            onClick = {
                                                                if (pSet.minSelect > minFloor) {
                                                                    pSet.minSelect -= 1
                                                                    if (pSet.minSelect == defaultMin) {
                                                                        pSet.showAdvancedMin = false
                                                                    }
                                                                }
                                                            },
                                                            enabled = pSet.minSelect > minFloor,
                                                            modifier = Modifier.size(26.dp),
                                                            shape = CircleShape
                                                        ) {
                                                            Icon(Icons.Default.Remove, contentDescription = "Decrease min", modifier = Modifier.size(12.dp))
                                                        }

                                                        Text(
                                                            text = "${pSet.minSelect}",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp,
                                                            modifier = Modifier.padding(horizontal = 8.dp)
                                                        )

                                                        OutlinedIconButton(
                                                            onClick = {
                                                                if (pSet.minSelect < pSet.maxSelect) {
                                                                    pSet.minSelect += 1
                                                                }
                                                            },
                                                            enabled = pSet.minSelect < pSet.maxSelect,
                                                            modifier = Modifier.size(26.dp),
                                                            shape = CircleShape
                                                        ) {
                                                            Icon(Icons.Default.Add, contentDescription = "Increase min", modifier = Modifier.size(12.dp))
                                                        }
                                                    }
                                                }
                                            }

                                            // Live customer hint in pale yellow chip
                                            val liveHint = when {
                                                pSet.maxSelect <= 1 -> "Select one"
                                                !pSet.required || pSet.minSelect == 0 || pSet.minSelect == 1 -> "Select up to ${pSet.maxSelect}"
                                                pSet.minSelect == pSet.maxSelect -> "Select ${pSet.minSelect}"
                                                else -> "Select ${pSet.minSelect} to ${pSet.maxSelect}"
                                            }

                                            Surface(
                                                color = Color(0xFFFEF9C3), // Pale yellow
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, Color(0xFFFDE047).copy(alpha = 0.6f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Info,
                                                        contentDescription = null,
                                                        tint = Color(0xFF854D0E),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Customers see: $liveHint",
                                                        color = Color(0xFF854D0E),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // 4. Choices list
                                    val duplicateChoiceNames = remember(pSet.choices.map { it.name }) {
                                        pSet.choices
                                            .map { it.name.trim() }
                                            .filter { it.isNotBlank() }
                                            .groupingBy { it.lowercase() }
                                            .eachCount()
                                            .filter { it.value > 1 }
                                            .keys
                                    }

                                    Text(
                                        text = "Choices (${pSet.choices.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )

                                    pSet.choices.forEachIndexed { cIdx, choice ->
                                        val cNum = cIdx + 1
                                        val isChoiceNameBlank = choice.name.trim().isBlank()
                                        val isChoiceDuplicate = duplicateChoiceNames.contains(choice.name.trim().lowercase())
                                        val showChoiceError = (choice.wasTouched || hasTappedSaveOnce) && (isChoiceNameBlank || isChoiceDuplicate)

                                        Card(
                                            shape = RoundedCornerShape(10.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            border = BorderStroke(
                                                1.dp,
                                                if (showChoiceError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
                                            ),
                                            modifier = Modifier.fillMaxWidth().testTag("choice_card_${setIndex}_$cIdx")
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    // Drag handle icon
                                                    Icon(
                                                        imageVector = Icons.Default.DragHandle,
                                                        contentDescription = "Reorder handle",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(20.dp)
                                                    )

                                                    // Choice Name
                                                    OutlinedTextField(
                                                        value = choice.name,
                                                        onValueChange = {
                                                            choice.name = it
                                                            choice.wasTouched = true
                                                        },
                                                        label = { Text("Choice name *") },
                                                        placeholder = { Text("e.g. Regular") },
                                                        singleLine = true,
                                                        isError = showChoiceError,
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.weight(1.4f).testTag("choice_name_${setIndex}_$cIdx")
                                                    )

                                                    // Price (₱, 0 shows as Free)
                                                    OutlinedTextField(
                                                        value = choice.priceText,
                                                        onValueChange = {
                                                            choice.priceText = it
                                                            choice.wasTouched = true
                                                        },
                                                        label = { Text("Price (₱)") },
                                                        placeholder = { Text("0") },
                                                        singleLine = true,
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.weight(1f).testTag("choice_price_${setIndex}_$cIdx")
                                                    )

                                                    // Delete choice
                                                    IconButton(
                                                        onClick = {
                                                            if (pSet.choices.size > 1) {
                                                                pSet.choices.removeAt(cIdx)
                                                                if (pSet.maxSelect > pSet.choices.size) {
                                                                    pSet.maxSelect = maxOf(1, pSet.choices.size)
                                                                    if (pSet.required && pSet.minSelect > pSet.maxSelect) {
                                                                        pSet.minSelect = pSet.maxSelect
                                                                    }
                                                                }
                                                            }
                                                        },
                                                        enabled = pSet.choices.size > 1,
                                                        modifier = Modifier.size(32.dp).testTag("delete_choice_${setIndex}_$cIdx")
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Delete,
                                                            contentDescription = "Delete Choice",
                                                            tint = if (pSet.choices.size > 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }

                                                // Bottom controls row of choice: Popular toggle & In stock switch
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Popular flame toggle
                                                    Surface(
                                                        onClick = { choice.popular = !choice.popular },
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = if (choice.popular) Color(0xFFFF5722).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                                        border = BorderStroke(1.dp, if (choice.popular) Color(0xFFFF5722) else Color.Transparent)
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Whatshot,
                                                                contentDescription = "Popular flame toggle",
                                                                tint = if (choice.popular) Color(0xFFFF5722) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = if (choice.popular) "Popular" else "Not popular",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = if (choice.popular) FontWeight.Bold else FontWeight.Normal,
                                                                color = if (choice.popular) Color(0xFFFF5722) else MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }

                                                    // In stock switch
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = if (choice.available) "In stock" else "Sold out",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = if (choice.available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                                                        )
                                                        Switch(
                                                            checked = choice.available,
                                                            onCheckedChange = { choice.available = it },
                                                            modifier = Modifier.scale(0.8f).testTag("choice_stock_${setIndex}_$cIdx")
                                                        )
                                                    }

                                                    // Move up/down choice
                                                    Row {
                                                        IconButton(
                                                            onClick = {
                                                                if (cIdx > 0) {
                                                                    val item = pSet.choices.removeAt(cIdx)
                                                                    pSet.choices.add(cIdx - 1, item)
                                                                }
                                                            },
                                                            enabled = cIdx > 0,
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move choice up", modifier = Modifier.size(14.dp))
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                if (cIdx < pSet.choices.size - 1) {
                                                                    val item = pSet.choices.removeAt(cIdx)
                                                                    pSet.choices.add(cIdx + 1, item)
                                                                }
                                                            },
                                                            enabled = cIdx < pSet.choices.size - 1,
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move choice down", modifier = Modifier.size(14.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Warning on duplicate names in same set
                                    if (duplicateChoiceNames.isNotEmpty()) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Warning: Duplicate choice names (${duplicateChoiceNames.joinToString(", ")}) in this set",
                                                    color = MaterialTheme.colorScheme.error,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }

                                    // "+ Add choice" button
                                    OutlinedButton(
                                        onClick = {
                                            pSet.choices.add(EditableChoice())
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.align(Alignment.Start).testTag("add_choice_btn_$setIndex")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("+ Add choice", fontWeight = FontWeight.SemiBold)
                                    }

                                    // 5. Live preview card: "How customers will see this"
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("customer_preview_$setIndex")
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = "HOW CUSTOMERS WILL SEE THIS",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.8.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            // Header: bold title + Required/Optional pill
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = pSet.name.trim().ifBlank { "Option Title" },
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    color = if (pSet.required) Color(0xFF1E1E1E) else Color(0xFF757575),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = if (pSet.required) "Required" else "Optional",
                                                        color = Color.White,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }

                                            // Grey hint
                                            val previewHint = when {
                                                pSet.maxSelect <= 1 -> "Select one"
                                                !pSet.required || pSet.minSelect == 0 -> "Select up to ${pSet.maxSelect}"
                                                pSet.minSelect == pSet.maxSelect -> "Select ${pSet.minSelect}"
                                                else -> "Select ${pSet.minSelect} to ${pSet.maxSelect}"
                                            }
                                            Text(
                                                text = previewHint,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                            // Option rows
                                            val isSingleChoice = pSet.maxSelect <= 1
                                            pSet.choices.forEachIndexed { cIdx, choice ->
                                                val choicePrice = choice.priceText.toDoubleOrNull() ?: 0.0
                                                val priceLabel = if (choicePrice <= 0.0) "Free" else "+ ₱${FormatUtils.formatCurrency(choicePrice)}"

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    if (isSingleChoice) {
                                                        RadioButton(
                                                            selected = cIdx == 0 && choice.available,
                                                            onClick = null,
                                                            colors = RadioButtonDefaults.colors(selectedColor = customColors.accent),
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    } else {
                                                        Checkbox(
                                                            checked = cIdx == 0 && choice.available,
                                                            onCheckedChange = null,
                                                            colors = CheckboxDefaults.colors(checkedColor = customColors.accent),
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.width(10.dp))

                                                    Row(
                                                        modifier = Modifier.weight(1f),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = choice.name.ifBlank { "Choice ${cIdx + 1}" },
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = if (choice.available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                                            fontWeight = FontWeight.Medium
                                                        )

                                                        if (choice.popular) {
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Icon(
                                                                imageVector = Icons.Default.Whatshot,
                                                                contentDescription = "Popular",
                                                                tint = Color(0xFFFF5722),
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }

                                                        if (!choice.available) {
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Surface(
                                                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                                                shape = RoundedCornerShape(4.dp)
                                                            ) {
                                                                Text(
                                                                    text = "Sold out",
                                                                    color = MaterialTheme.colorScheme.error,
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        }
                                                    }

                                                    Text(
                                                        text = priceLabel,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (choicePrice <= 0.0) Color(0xFF2E7D32) else customColors.accent
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (setsList.size < 12) {
                            OutlinedButton(
                                onClick = { setsList.add(EditableSet(initialChoices = listOf(EditableChoice()))) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("add_another_set_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("+ Add Set (${setsList.size}/12)", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // SECTION 6: Stock
            SectionCard(
                title = "Stock",
                helpText = "Manage in-stock availability and low stock inventory alerts.",
                onHelpClick = { activeHelpDialogText = it }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (inStock) "In stock" else "Sold out", fontWeight = FontWeight.Bold)
                            Text(
                                text = if (inStock) "Available to order when store is open" else "Marked as sold out on your menu",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = inStock,
                            onCheckedChange = { inStock = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = customColors.accent,
                                checkedTrackColor = customColors.accentTint
                            )
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // Track stock count toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Track stock count", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Alert when stock is low, and auto mark sold out at 0",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = trackStock,
                            onCheckedChange = { trackStock = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = customColors.accent,
                                checkedTrackColor = customColors.accentTint
                            )
                        )
                    }

                    if (trackStock) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = stockCountText,
                                onValueChange = {
                                    stockCountText = it
                                    val count = it.toIntOrNull() ?: 0
                                    if (count <= 0) {
                                        inStock = false
                                    }
                                },
                                label = { Text("Stock count") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("stock_count_input")
                            )

                            OutlinedTextField(
                                value = lowStockThresholdText,
                                onValueChange = { lowStockThresholdText = it },
                                label = { Text("Low stock at") },
                                placeholder = { Text("5") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("low_stock_threshold_input")
                            )
                        }
                    }
                }
            }

            // SECTION 7: Collapsed "Kitchen & tags"
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { isKitchenExpanded = !isKitchenExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Kitchen & tags",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = {
                                activeHelpDialogText = "Operational settings: preparation time, promotional tags for restaurants, and SKU / barcode inventory for convenience shops."
                            }) {
                                Icon(Icons.Default.HelpOutline, contentDescription = "Help", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                            }
                        }
                        Icon(
                            imageVector = if (isKitchenExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expand/Collapse"
                        )
                    }

                    if (isKitchenExpanded) {
                        Spacer(modifier = Modifier.height(10.dp))
                        if (businessType == BusinessType.FOOD) {
                            OutlinedTextField(
                                value = prepTimeText,
                                onValueChange = { prepTimeText = it },
                                label = { Text("Prep time (minutes)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Tags:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            val allTags = listOf("Best Seller", "Chef Special", "Spicy", "Vegetarian", "Halal")
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                allTags.forEach { tag ->
                                    val isSelected = selectedTags.contains(tag)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedTags = if (isSelected) selectedTags - tag else selectedTags + tag },
                                        label = { Text(tag, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = customColors.accentTint,
                                            selectedLabelColor = customColors.accent
                                        )
                                    )
                                }
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = sku, onValueChange = { sku = it }, label = { Text("SKU") }, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = barcode, onValueChange = { barcode = it }, label = { Text("Barcode") }, modifier = Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Unit") }, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = stockCountText, onValueChange = { stockCountText = it }, label = { Text("Stock") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Extra bottom padding (navigation bar height + 96 dp) so last item scrolls fully above bottom bar
            val density = androidx.compose.ui.platform.LocalDensity.current
            val navBarBottomDp = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
            Spacer(modifier = Modifier.height(navBarBottomDp + 96.dp))
        }
    }

    // Help Dialog (? icon on each section)
    activeHelpDialogText?.let { helpContent ->
        AlertDialog(
            onDismissRequest = { activeHelpDialogText = null },
            icon = { Icon(Icons.Default.HelpOutline, contentDescription = null, tint = customColors.accent) },
            title = { Text("Section Info", fontWeight = FontWeight.Bold) },
            text = { Text(helpContent, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(onClick = { activeHelpDialogText = null }) { Text("Got it") }
            }
        )
    }

    // Type Category Dialog (+ Add from dropdown)
    if (showTypeCategoryDialog) {
        var categoryNameInput by remember { mutableStateOf("") }
        var categoryNameError by remember { mutableStateOf<String?>(null) }
        var isSubmittingCategory by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSubmittingCategory) showTypeCategoryDialog = false },
            title = { Text("Type your Category", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = categoryNameInput,
                    onValueChange = {
                        categoryNameInput = it
                        categoryNameError = null
                    },
                    label = { Text("Category Class Name *") },
                    isError = categoryNameError != null,
                    supportingText = categoryNameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("type_category_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = categoryNameInput.trim()
                        if (trimmed.isBlank()) {
                            categoryNameError = "Category name cannot be empty"
                            return@Button
                        }
                        if (trimmed.length > 40) {
                            categoryNameError = "Max 40 characters"
                            return@Button
                        }
                        if (subcategories.any { it.name.trim().equals(trimmed, ignoreCase = true) }) {
                            categoryNameError = "Category \"$trimmed\" already exists"
                            return@Button
                        }
                        isSubmittingCategory = true
                        onCreateSubcategory(trimmed) { success, createdSub, err ->
                            isSubmittingCategory = false
                            if (success) {
                                val assignedName = createdSub?.name ?: trimmed
                                val assignedId = createdSub?.id ?: ""
                                selectedSubcategoryId = assignedId
                                selectedSubcategoryName = assignedName
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Your Category has been added")
                                }
                                showTypeCategoryDialog = false
                            } else {
                                categoryNameError = err ?: "Failed to add category"
                            }
                        }
                    },
                    enabled = !isSubmittingCategory,
                    colors = ButtonDefaults.buttonColors(containerColor = customColors.accent, contentColor = customColors.onAccent)
                ) {
                    Text(if (isSubmittingCategory) "Saving..." else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTypeCategoryDialog = false }, enabled = !isSubmittingCategory) {
                    Text("Cancel")
                }
            }
        )
    }

    // Manage Categories Dialog
    if (showManageCategoriesDialog) {
        AlertDialog(
            onDismissRequest = { showManageCategoriesDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("My Categories", fontWeight = FontWeight.Bold)
                    Text("${subcategories.size}/30", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            text = {
                if (subcategories.isEmpty()) {
                    Text("No custom category classes yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        subcategories.forEach { sub ->
                            val count = menuItems.count {
                                it.subcategoryId == sub.id || (it.subcategoryId == null && it.subcategoryName?.equals(sub.name, ignoreCase = true) == true)
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(sub.name, fontWeight = FontWeight.Bold)
                                        Text("$count ${if (count == 1) "product" else "products"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row {
                                        IconButton(onClick = { subcategoryToRename = sub }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.Edit, contentDescription = "Rename", modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(onClick = { subcategoryToDelete = sub }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showManageCategoriesDialog = false }) { Text("Done") }
            }
        )
    }

    // Rename Subcategory Dialog
    subcategoryToRename?.let { sub ->
        var renameInput by remember(sub) { mutableStateOf(sub.name) }
        var isRenaming by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isRenaming) subcategoryToRename = null },
            title = { Text("Rename Category", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Name *") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = renameInput.trim()
                        if (trimmed.isBlank() || trimmed.length > 40) return@Button
                        isRenaming = true
                        onRenameSubcategory(sub.id, trimmed) { success, _ ->
                            isRenaming = false
                            if (success) {
                                if (selectedSubcategoryId == sub.id) selectedSubcategoryName = trimmed
                                subcategoryToRename = null
                            }
                        }
                    },
                    enabled = !isRenaming
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { subcategoryToRename = null }, enabled = !isRenaming) { Text("Cancel") }
            }
        )
    }

    // Delete Subcategory Dialog
    subcategoryToDelete?.let { sub ->
        val count = remember(sub, menuItems) {
            menuItems.count { it.subcategoryId == sub.id || (it.subcategoryId == null && it.subcategoryName?.equals(sub.name, ignoreCase = true) == true) }
        }
        AlertDialog(
            onDismissRequest = { subcategoryToDelete = null },
            title = { Text("Delete Category", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = { Text("$count ${if (count == 1) "product uses" else "products use"} this category. They will move to Other.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSubcategory(sub.id) { _, _ -> subcategoryToDelete = null }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { subcategoryToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // Error Dialog with Copy
    failureErrorMessage?.let { errText ->
        AlertDialog(
            onDismissRequest = { failureErrorMessage = null },
            title = { Text("Failed to Save Product", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = { Text(errText, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(onClick = { clipboardManager.setText(AnnotatedString(errText)) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Error")
                }
            },
            dismissButton = {
                TextButton(onClick = { failureErrorMessage = null }) { Text("Close") }
            }
        )
    }

    if (setIndexToDelete != null) {
        val delIdx = setIndexToDelete!!
        AlertDialog(
            onDismissRequest = { setIndexToDelete = null },
            title = { Text("Delete Set ${delIdx + 1}?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this option group? All choices in this set will be removed.") },
            confirmButton = {
                Button(
                    onClick = {
                        if (delIdx in setsList.indices) {
                            setsList.removeAt(delIdx)
                        }
                        setIndexToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { setIndexToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
    if (showTimePickerForAvailStart) {
        TimePickerStepDialog(
            initialTime24 = availStartTime24,
            title = "Select Start Time",
            onDismiss = { showTimePickerForAvailStart = false },
            onTimeSelected = {
                availStartTime24 = it
                showTimePickerForAvailStart = false
            }
        )
    }

    if (showTimePickerForAvailEnd) {
        TimePickerStepDialog(
            initialTime24 = availEndTime24,
            title = "Select End Time",
            onDismiss = { showTimePickerForAvailEnd = false },
            onTimeSelected = {
                availEndTime24 = it
                showTimePickerForAvailEnd = false
            }
        )
    }

    if (showDatePickerForFrom) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = try {
                if (availDateFrom.isNotBlank()) {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
                        timeZone = java.util.TimeZone.getTimeZone("UTC")
                    }
                    sdf.parse(availDateFrom)?.time
                } else null
            } catch (_: Exception) { null }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePickerForFrom = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = millis
                        }
                        val formatted = "%04d-%02d-%02d".format(
                            cal.get(java.util.Calendar.YEAR),
                            cal.get(java.util.Calendar.MONTH) + 1,
                            cal.get(java.util.Calendar.DAY_OF_MONTH)
                        )
                        availDateFrom = formatted
                        if (availDateTo.isNotBlank() && availDateTo < formatted) {
                            availDateTo = formatted
                        }
                    }
                    showDatePickerForFrom = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerForFrom = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showDatePickerForTo) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = try {
                if (availDateTo.isNotBlank()) {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
                        timeZone = java.util.TimeZone.getTimeZone("UTC")
                    }
                    sdf.parse(availDateTo)?.time
                } else null
            } catch (_: Exception) { null }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePickerForTo = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = millis
                        }
                        val formatted = "%04d-%02d-%02d".format(
                            cal.get(java.util.Calendar.YEAR),
                            cal.get(java.util.Calendar.MONTH) + 1,
                            cal.get(java.util.Calendar.DAY_OF_MONTH)
                        )
                        availDateTo = formatted
                        if (availDateFrom.isNotBlank() && availDateFrom > formatted) {
                            availDateFrom = formatted
                        }
                    }
                    showDatePickerForTo = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerForTo = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerStepDialog(
    initialTime24: String,
    title: String,
    onDismiss: () -> Unit,
    onTimeSelected: (String) -> Unit
) {
    val timeOptions = remember {
        val list = mutableListOf<String>()
        for (h in 0..23) {
            for (m in listOf(0, 15, 30, 45)) {
                list.add("%02d:%02d".format(h, m))
            }
        }
        list.add("23:59")
        list
    }

    var selectedTime by remember { mutableStateOf(initialTime24) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(timeOptions) { timeStr ->
                        val isSelected = selectedTime == timeStr
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTime = timeStr }
                                .padding(vertical = 4.dp, horizontal = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = MenuSchedule.formatTime12h(timeStr),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.AccessTime,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onTimeSelected(selectedTime) }) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SectionCard(
    title: String,
    helpText: String,
    onHelpClick: (String) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { onHelpClick(helpText) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.HelpOutline, contentDescription = "Help", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
                }
            }
            content()
        }
    }
}
