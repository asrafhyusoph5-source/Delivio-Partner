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
import kotlinx.coroutines.launch
import java.util.UUID

private class EditableChoice(
    val id: String = UUID.randomUUID().toString(),
    initialName: String = "",
    initialPriceText: String = "0",
    initialAvailable: Boolean = true
) {
    var name by mutableStateOf(initialName)
    var priceText by mutableStateOf(initialPriceText)
    var available by mutableStateOf(initialAvailable)
}

private class EditableSet(
    val id: String = UUID.randomUUID().toString(),
    initialName: String = "",
    initialSubcategoryId: String? = null,
    initialCustomName: Boolean = false,
    initialRequired: Boolean = true,
    initialMinSelect: Int = 1,
    initialMaxSelect: Int = 1,
    initialChoices: List<EditableChoice> = listOf(EditableChoice())
) {
    var name by mutableStateOf(initialName)
    var subcategoryId by mutableStateOf<String?>(initialSubcategoryId)
    var isCustomName by mutableStateOf(initialCustomName)
    var required by mutableStateOf(initialRequired)
    var minSelect by mutableIntStateOf(initialMinSelect)
    var maxSelect by mutableIntStateOf(initialMaxSelect)
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

    // 4. Menus
    val initialSelectedMenuIds = remember(item) {
        val eff = item.getEffectiveMenuIds()
        eff.toSet().ifEmpty { setOf("main") }
    }
    var selectedMenuIds by remember { mutableStateOf(initialSelectedMenuIds) }

    // 5. Options (Sets)
    val setsList = remember {
        mutableStateListOf<EditableSet>().apply {
            if (item.hasSets && item.sets.isNotEmpty()) {
                addAll(item.sets.map { s ->
                    EditableSet(
                        id = s.id.ifBlank { UUID.randomUUID().toString() },
                        initialName = s.name,
                        initialSubcategoryId = s.subcategoryId,
                        initialCustomName = s.customName || (s.subcategoryId == null && s.name.isNotBlank()),
                        initialRequired = s.required,
                        initialMinSelect = if (s.required) s.minSelect.coerceIn(1, s.maxSelect.coerceIn(1, 10)) else 1,
                        initialMaxSelect = s.maxSelect.coerceIn(1, 10),
                        initialChoices = s.choices.map { c ->
                            EditableChoice(
                                id = c.id.ifBlank { UUID.randomUUID().toString() },
                                initialName = c.name,
                                initialPriceText = if (c.price == 0.0) "0" else c.price.toString(),
                                initialAvailable = c.available
                            )
                        }.ifEmpty { listOf(EditableChoice()) }
                    )
                })
            }
        }
    }

    // 6. Stock
    var inStock by remember { mutableStateOf(item.inStock) }

    // 7. Kitchen & tags (collapsed by default)
    var isKitchenExpanded by remember { mutableStateOf(!isNew) }
    var prepTimeText by remember { mutableStateOf(item.prepTimeMinutes.toString()) }
    var selectedTags by remember { mutableStateOf(item.tags.toSet()) }
    var sku by remember { mutableStateOf(item.sku) }
    var barcode by remember { mutableStateOf(item.barcode) }
    var unit by remember { mutableStateOf(item.unit) }
    var stockCountText by remember { mutableStateOf(item.stockCount.toString()) }
    var lowStockThresholdText by remember { mutableStateOf(item.lowStockAlertThreshold.toString()) }

    // Validation & Error state
    var hasTappedSaveOnce by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var failureErrorMessage by remember { mutableStateOf<String?>(null) }
    var activeHelpDialogText by remember { mutableStateOf<String?>(null) }

    // Subcategory dialogs
    var showTypeCategoryDialog by remember { mutableStateOf(false) }
    var subcategoryTargetSetIndex by remember { mutableStateOf<Int?>(null) }
    var showManageCategoriesDialog by remember { mutableStateOf(false) }
    var subcategoryToRename by remember { mutableStateOf<Subcategory?>(null) }
    var subcategoryToDelete by remember { mutableStateOf<Subcategory?>(null) }

    // Validation computations
    val parsedPrice = priceText.toDoubleOrNull()
    val isPriceValid = parsedPrice != null && parsedPrice >= 0.0
    val isNameValid = name.isNotBlank()
    val isCatMissing = selectedCategoryName.isBlank() || !allowedCategories.any { it.equals(selectedCategoryName, ignoreCase = true) }

    val setErrors = remember(setsList.map { listOf(it.name, it.required.toString(), it.minSelect.toString(), it.maxSelect.toString(), it.choices.size.toString()) + it.choices.map { c -> "${c.name}:${c.priceText}:${c.available}" } }) {
        val errors = mutableListOf<String>()
        val seenNames = mutableSetOf<String>()

        setsList.forEachIndexed { sIdx, set ->
            val sNum = sIdx + 1
            val trimmedSetName = set.name.trim()
            if (trimmedSetName.isBlank()) {
                errors.add("Set $sNum requires a name")
            } else if (!seenNames.add(trimmedSetName.lowercase())) {
                errors.add("Set $sNum has duplicate name \"${set.name}\"")
            }

            if (set.choices.isEmpty()) {
                errors.add("Set $sNum must have at least one choice")
            } else {
                val inStockCount = set.choices.count { it.available }
                if (set.required) {
                    if (set.minSelect > set.maxSelect) {
                        errors.add("Set $sNum minimum choices cannot exceed maximum choices")
                    }
                    if (set.minSelect > inStockCount && inStockCount > 0) {
                        errors.add("Set $sNum requires min ${set.minSelect} choices, but only has $inStockCount in-stock choice(s)")
                    }
                }
                set.choices.forEachIndexed { cIdx, choice ->
                    val cNum = cIdx + 1
                    if (choice.name.trim().isBlank()) {
                        errors.add("Set $sNum choice $cNum requires a name")
                    }
                    val cPrice = choice.priceText.toDoubleOrNull()
                    if (cPrice == null || cPrice < 0.0) {
                        errors.add("Set $sNum choice $cNum requires a valid price")
                    }
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
                        .padding(16.dp)
                ) {
                    // Summary line appears above Save button ONLY after a failed attempt or tap
                    if (hasTappedSaveOnce && !canSave) {
                        val summaryText = when {
                            !isNameValid -> "Product name is required"
                            isCatMissing -> "Main Category is required"
                            !isPriceValid -> "Please enter a valid price"
                            setErrors.isNotEmpty() -> setErrors.first()
                            else -> "Please correct highlighted fields above"
                        }
                        Text(
                            text = "⚠️ $summaryText",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
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
                                val parsedThreshold = lowStockThresholdText.toIntOrNull() ?: item.lowStockAlertThreshold

                                val finalCatName = selectedCategoryName
                                val finalCatId = selectedCategoryId.ifBlank {
                                    categories.find { it.name.equals(finalCatName, ignoreCase = true) }?.id
                                        ?: finalCatName.lowercase().trim().replace("&", "and").replace("[^a-z0-9]+".toRegex(), "_").trim('_')
                                }

                                val finalSets = setsList.mapIndexed { idx, s ->
                                    ProductSet(
                                        id = s.id,
                                        name = s.name.trim(),
                                        subcategoryId = if (s.isCustomName) null else s.subcategoryId,
                                        customName = s.isCustomName,
                                        required = s.required,
                                        minSelect = if (s.required) s.minSelect.coerceIn(1, s.maxSelect) else 0,
                                        maxSelect = s.maxSelect.coerceIn(1, 10),
                                        sortOrder = idx,
                                        choices = s.choices.map { c ->
                                            ProductSetChoice(
                                                id = c.id,
                                                name = c.name.trim(),
                                                price = c.priceText.toDoubleOrNull() ?: 0.0,
                                                available = c.available
                                            )
                                        }
                                    )
                                }

                                val finalMenuIds = selectedMenuIds.ifEmpty { setOf("main") }.toList()

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
                                    inStock = inStock,
                                    prepTimeMinutes = if (businessType == BusinessType.FOOD) parsedPrep else 0,
                                    tags = if (businessType == BusinessType.FOOD) selectedTags.toList() else emptyList(),
                                    sku = if (businessType == BusinessType.CONVENIENCE) sku.trim() else "",
                                    barcode = if (businessType == BusinessType.CONVENIENCE) barcode.trim() else "",
                                    unit = if (businessType == BusinessType.CONVENIENCE) unit.trim().ifBlank { "1 pc" } else "1 pc",
                                    stockCount = if (businessType == BusinessType.CONVENIENCE) parsedStock else 50,
                                    lowStockAlertThreshold = if (businessType == BusinessType.CONVENIENCE) parsedThreshold else 5,
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
            // SECTION 1: Basic info
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
                                    subcategoryTargetSetIndex = null
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

            // SECTION 2: Photo
            SectionCard(
                title = "Photo",
                helpText = "Upload a high quality HTTPS image URL for this dish or product.",
                onHelpClick = { activeHelpDialogText = it }
            ) {
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

                if (imageUrl.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, customColors.accentTint, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = imageUrl.trim(),
                                contentDescription = "Preview",
                                contentScale = ContentScale.Crop,
                                onError = { imageLoadFailed = true },
                                onSuccess = { imageLoadFailed = false },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Live Preview", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = if (imageLoadFailed) "Status: Failed to load" else "Status: OK",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (imageLoadFailed) StatusClosedRed else StatusOpenGreen
                            )
                        }
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

            // SECTION 4: Menus
            SectionCard(
                title = "Menus",
                helpText = "Choose which menus this product will belong to. Products can appear in multiple menus with different schedules.",
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
                            val isDuplicateName = setsList.indices.any { it != setIndex && setsList[it].name.trim().equals(pSet.name.trim(), ignoreCase = true) && pSet.name.isNotBlank() }
                            val isSetNameBlank = pSet.name.trim().isBlank()
                            val hasSetError = hasTappedSaveOnce && (isSetNameBlank || isDuplicateName || pSet.choices.isEmpty())

                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                border = BorderStroke(1.dp, if (hasSetError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth().testTag("set_card_$setIndex")
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Set $setNum", fontWeight = FontWeight.Bold, color = customColors.accent)
                                        IconButton(onClick = { setsList.removeAt(setIndex) }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Set", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }

                                    // Set Name Dropdown
                                    var setNameExpanded by remember { mutableStateOf(false) }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ExposedDropdownMenuBox(
                                            expanded = setNameExpanded,
                                            onExpandedChange = { setNameExpanded = it },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            OutlinedTextField(
                                                value = if (pSet.name.isNotBlank()) pSet.name else if (pSet.isCustomName) "Others" else "Select Set Name *",
                                                onValueChange = {},
                                                readOnly = true,
                                                label = { Text("Set Name *") },
                                                isError = hasTappedSaveOnce && (isSetNameBlank || isDuplicateName),
                                                supportingText = if (hasTappedSaveOnce) {
                                                    when {
                                                        isSetNameBlank -> { { Text("Set name is required", color = MaterialTheme.colorScheme.error) } }
                                                        isDuplicateName -> { { Text("Set name must be unique", color = MaterialTheme.colorScheme.error) } }
                                                        else -> null
                                                    }
                                                } else null,
                                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = setNameExpanded) },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.menuAnchor().fillMaxWidth().testTag("set_name_dropdown_$setIndex")
                                            )

                                            ExposedDropdownMenu(
                                                expanded = setNameExpanded,
                                                onDismissRequest = { setNameExpanded = false }
                                            ) {
                                                subcategories.forEach { sub ->
                                                    val alreadyUsedByOther = setsList.indices.any { it != setIndex && setsList[it].name.trim().equals(sub.name.trim(), ignoreCase = true) }
                                                    DropdownMenuItem(
                                                        text = {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text(sub.name, fontWeight = if (pSet.name.equals(sub.name, ignoreCase = true) && !pSet.isCustomName) FontWeight.Bold else FontWeight.Normal)
                                                                if (alreadyUsedByOther) {
                                                                    Text("Already used", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                                                }
                                                            }
                                                        },
                                                        enabled = !alreadyUsedByOther,
                                                        onClick = {
                                                            pSet.name = sub.name
                                                            pSet.subcategoryId = sub.id
                                                            pSet.isCustomName = false
                                                            setNameExpanded = false
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
                                                        setNameExpanded = false
                                                        subcategoryTargetSetIndex = setIndex
                                                        showTypeCategoryDialog = true
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(Icons.Default.Edit, contentDescription = null, tint = customColors.accent, modifier = Modifier.size(18.dp))
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text("Others", fontWeight = if (pSet.isCustomName) FontWeight.Bold else FontWeight.Normal)
                                                        }
                                                    },
                                                    onClick = {
                                                        pSet.isCustomName = true
                                                        pSet.subcategoryId = null
                                                        setNameExpanded = false
                                                    }
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = { showManageCategoriesDialog = true },
                                            modifier = Modifier.testTag("manage_subcategories_set_${setIndex}_btn")
                                        ) {
                                            Icon(Icons.Default.Tune, contentDescription = "Manage Categories", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }

                                    if (pSet.isCustomName) {
                                        OutlinedTextField(
                                            value = pSet.name,
                                            onValueChange = { pSet.name = it },
                                            label = { Text("Type the set name *") },
                                            placeholder = { Text("e.g. Spicy level, Cutlery") },
                                            singleLine = true,
                                            isError = hasTappedSaveOnce && (isSetNameBlank || isDuplicateName),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth().testTag("custom_set_name_input_$setIndex")
                                        )
                                    }

                                    // Required switch with helper
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(if (pSet.required) "Required" else "Optional", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text("Customer must choose at least the minimum below", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                        }
                                        Switch(
                                            checked = pSet.required,
                                            onCheckedChange = { isReq ->
                                                pSet.required = isReq
                                                if (isReq && pSet.minSelect < 1) pSet.minSelect = 1
                                            }
                                        )
                                    }

                                    // Steppers for min & max choices
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (pSet.required) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Minimum choices", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                                    Text("Default 1, never above maximum", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    IconButton(onClick = { if (pSet.minSelect > 1) pSet.minSelect -= 1 }, enabled = pSet.minSelect > 1) {
                                                        Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    }
                                                    Text("${pSet.minSelect}", fontWeight = FontWeight.Bold)
                                                    IconButton(onClick = { if (pSet.minSelect < pSet.maxSelect) pSet.minSelect += 1 }, enabled = pSet.minSelect < pSet.maxSelect) {
                                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                            HorizontalDivider()
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Maximum choices", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                                Text("1 = picks one. 2+ = can pick several up to this limit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = {
                                                        if (pSet.maxSelect > 1) {
                                                            pSet.maxSelect -= 1
                                                            if (pSet.minSelect > pSet.maxSelect) pSet.minSelect = pSet.maxSelect
                                                        }
                                                    },
                                                    enabled = pSet.maxSelect > 1
                                                ) {
                                                    Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                                                }
                                                Text("${pSet.maxSelect}", fontWeight = FontWeight.Bold)
                                                IconButton(onClick = { if (pSet.maxSelect < 10) pSet.maxSelect += 1 }, enabled = pSet.maxSelect < 10) {
                                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }

                                        val summaryLine = if (pSet.required) {
                                            if (pSet.minSelect == pSet.maxSelect) "Required: customer picks ${pSet.minSelect}"
                                            else "Required: customer picks ${pSet.minSelect} to ${pSet.maxSelect}"
                                        } else {
                                            "Optional: customer picks up to ${pSet.maxSelect}"
                                        }
                                        Surface(color = customColors.accent.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                                            Text(summaryLine, color = customColors.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                        }
                                    }

                                    // Choice rows
                                    Text("Choices / Options", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    pSet.choices.forEachIndexed { cIdx, choice ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = choice.name,
                                                onValueChange = { choice.name = it },
                                                label = { Text("Product *") },
                                                placeholder = { Text("e.g. Regular") },
                                                singleLine = true,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1.3f)
                                            )
                                            OutlinedTextField(
                                                value = choice.priceText,
                                                onValueChange = { choice.priceText = it },
                                                label = { Text("Price (₱)") },
                                                placeholder = { Text("0") },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f)
                                            )
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Switch(
                                                    checked = choice.available,
                                                    onCheckedChange = { choice.available = it },
                                                    modifier = Modifier.scale(0.8f)
                                                )
                                                Text(if (choice.available) "In stock" else "Out", fontSize = 10.sp)
                                            }
                                            IconButton(
                                                onClick = { pSet.choices.removeAt(cIdx) },
                                                enabled = pSet.choices.size > 1,
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }

                                    TextButton(
                                        onClick = { pSet.choices.add(EditableChoice()) },
                                        modifier = Modifier.align(Alignment.Start)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("+ Add product", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        if (setsList.size < 8) {
                            OutlinedButton(
                                onClick = { setsList.add(EditableSet(initialChoices = listOf(EditableChoice()))) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("add_another_set_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("+ Add Set (${setsList.size}/8)", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // SECTION 6: Stock
            SectionCard(
                title = "Stock",
                helpText = "Toggle whether this product is currently in stock or sold out.",
                onHelpClick = { activeHelpDialogText = it }
            ) {
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

            Spacer(modifier = Modifier.height(20.dp))
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
                                if (subcategoryTargetSetIndex == null) {
                                    selectedSubcategoryId = assignedId
                                    selectedSubcategoryName = assignedName
                                } else {
                                    val targetIdx = subcategoryTargetSetIndex!!
                                    if (targetIdx in setsList.indices) {
                                        setsList[targetIdx].name = assignedName
                                        setsList[targetIdx].subcategoryId = assignedId
                                        setsList[targetIdx].isCustomName = false
                                    }
                                }
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
                                setsList.forEach { if (it.subcategoryId == sub.id) it.name = trimmed }
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
