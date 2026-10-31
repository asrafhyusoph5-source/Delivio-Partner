package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
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
import com.example.data.model.BusinessType
import com.example.data.model.MenuCategory
import com.example.data.model.MenuItem
import com.example.data.model.ProductSet
import com.example.data.model.ProductSetChoice
import com.example.data.model.Subcategory
import com.example.ui.theme.LocalDelivioColors
import com.example.util.CurrencyConstants
import com.example.util.FOOD_CATEGORIES
import com.example.util.CONVENIENCE_CATEGORIES
import java.util.UUID

// Local mutable state wrappers for Sets editing
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
    initialRequired: Boolean = true,
    initialMinSelect: Int = 1,
    initialMaxSelect: Int = 1,
    initialChoices: List<EditableChoice> = listOf(EditableChoice())
) {
    var name by mutableStateOf(initialName)
    var subcategoryId by mutableStateOf<String?>(initialSubcategoryId)
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

    // Basic fields
    var name by remember { mutableStateOf(item.name) }
    var description by remember { mutableStateOf(item.description) }
    var priceText by remember { mutableStateOf(if (item.price > 0.0) item.price.toString() else "0") }
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

    // Category Class (Subcategory) state
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

    // Dialog states for Subcategories
    var showTypeCategoryDialog by remember { mutableStateOf(false) }
    var subcategoryTargetSetIndex by remember { mutableStateOf<Int?>(null) } // null = Category Class; int = Set card index
    var showManageCategoriesDialog by remember { mutableStateOf(false) }
    var subcategoryToRename by remember { mutableStateOf<Subcategory?>(null) }
    var subcategoryToDelete by remember { mutableStateOf<Subcategory?>(null) }

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

    // Sets state
    val setsList = remember {
        mutableStateListOf<EditableSet>().apply {
            if (item.hasSets && item.sets.isNotEmpty()) {
                addAll(item.sets.map { s ->
                    EditableSet(
                        id = s.id.ifBlank { UUID.randomUUID().toString() },
                        initialName = s.name,
                        initialSubcategoryId = s.subcategoryId,
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

    // Error Dialog state
    var failureErrorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val isCatMissing = selectedCategoryName.isBlank() || !allowedCategories.any { it.equals(selectedCategoryName, ignoreCase = true) }
    val parsedPrice = priceText.toDoubleOrNull()
    val isPriceValid = parsedPrice != null && parsedPrice >= 0.0
    val isNameValid = name.isNotBlank()

    // Sets validation
    val setErrors = remember(setsList.map { listOf(it.name, it.required.toString(), it.minSelect.toString(), it.maxSelect.toString(), it.choices.size.toString()) + it.choices.map { c -> "${c.name}:${c.priceText}:${c.available}" } }) {
        val errors = mutableListOf<String>()
        val seenNames = mutableSetOf<String>()

        setsList.forEachIndexed { sIdx, set ->
            val sNum = sIdx + 1
            val trimmedSetName = set.name.trim()
            if (trimmedSetName.isBlank()) {
                errors.add("Set $sNum requires a name")
            } else if (!seenNames.add(trimmedSetName.lowercase())) {
                errors.add("Set $sNum has a duplicate name \"${set.name}\"")
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
                        errors.add("Set $sNum requires minimum ${set.minSelect} choice(s), but only has $inStockCount in-stock choice(s)")
                    }
                }
                set.choices.forEachIndexed { cIdx, choice ->
                    val cNum = cIdx + 1
                    if (choice.name.trim().isBlank()) {
                        errors.add("Set $sNum choice $cNum requires a name")
                    }
                    val cPrice = choice.priceText.toDoubleOrNull()
                    if (cPrice == null || cPrice < 0.0) {
                        errors.add("Set $sNum choice $cNum requires a valid price (≥ 0)")
                    }
                }
            }
        }
        errors
    }

    val canSave = isNameValid && !isCatMissing && isPriceValid && setErrors.isEmpty() && !isSaving

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("product_edit_screen"),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isNew) "Add New Product" else "Edit Product",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("product_edit_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isNew) {
                        IconButton(
                            onClick = { onDelete(item.id) },
                            modifier = Modifier.testTag("delete_menu_item_btn")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    if (setErrors.isNotEmpty()) {
                        Text(
                            text = setErrors.first(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
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
                                        subcategoryId = s.subcategoryId,
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

                                val updated = item.copy(
                                    name = name.trim().ifBlank { "Untitled Item" },
                                    description = description.trim(),
                                    price = priceVal,
                                    categoryId = finalCatId,
                                    categoryName = finalCatName,
                                    subcategoryId = selectedSubcategoryId.ifBlank { null },
                                    subcategoryName = selectedSubcategoryName.ifBlank { null },
                                    imageUrl = imageUrl.trim(),
                                    iconEmoji = iconEmoji.ifBlank { if (businessType == BusinessType.FOOD) "🍔" else "🛍️" },
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Item Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Product / Item Name *") },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                isError = name.isBlank(),
                supportingText = if (name.isBlank()) {
                    { Text("Product name is required", color = MaterialTheme.colorScheme.error) }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("menu_item_name_input")
            )

            // 2. Price & Category
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
                    isError = !isPriceValid,
                    supportingText = if (!isPriceValid) {
                        { Text("Valid price required", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("menu_item_price_input")
                )

                // Category Selector
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
                                text = {
                                    Text(
                                        catName,
                                        fontWeight = if (selectedCategoryName.equals(catName, ignoreCase = true)) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
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

            // Category Class (Subcategory) Selector directly below Price & Category
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
                        label = { Text("Category Class (Optional)") },
                        supportingText = { Text("Empty means \"Other\"") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = classExpanded) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .testTag("category_class_dropdown")
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
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .testTag("manage_subcategories_btn")
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "Manage Categories",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 3. Sets Section: Directly below Price and Category
            if (setsList.isEmpty()) {
                OutlinedButton(
                    onClick = {
                        setsList.add(EditableSet(initialChoices = listOf(EditableChoice())))
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_set_initial_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("+ Add Set", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    setsList.forEachIndexed { setIndex, pSet ->
                        val setNum = setIndex + 1
                        val isDuplicateName = setsList.indices.any { it != setIndex && setsList[it].name.trim().equals(pSet.name.trim(), ignoreCase = true) && pSet.name.isNotBlank() }
                        val isSetNameBlank = pSet.name.trim().isBlank()
                        val hasSetError = isSetNameBlank || isDuplicateName || pSet.choices.isEmpty()

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (hasSetError) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("set_card_$setIndex")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Set Card Header: Title + Delete Set
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
                                    IconButton(
                                        onClick = { setsList.removeAt(setIndex) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete Set",
                                            tint = MaterialTheme.colorScheme.error
                                        )
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
                                            value = if (pSet.name.isNotBlank()) pSet.name else "Select Set Name *",
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Set Name *") },
                                            placeholder = { Text("Select Set Name") },
                                            singleLine = true,
                                            isError = isSetNameBlank || isDuplicateName,
                                            supportingText = {
                                                if (isSetNameBlank) {
                                                    Text("Set name is required", color = MaterialTheme.colorScheme.error)
                                                } else if (isDuplicateName) {
                                                    Text("Set name must be unique within product", color = MaterialTheme.colorScheme.error)
                                                }
                                            },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = setNameExpanded) },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .menuAnchor()
                                                .fillMaxWidth()
                                                .testTag("set_name_dropdown_$setIndex")
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
                                                            Text(
                                                                sub.name,
                                                                fontWeight = if (pSet.name.equals(sub.name, ignoreCase = true)) FontWeight.Bold else FontWeight.Normal,
                                                                color = if (alreadyUsedByOther) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface
                                                            )
                                                            if (alreadyUsedByOther) {
                                                                Text(
                                                                    "Already used",
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.outline
                                                                )
                                                            }
                                                        }
                                                    },
                                                    enabled = !alreadyUsedByOther,
                                                    onClick = {
                                                        pSet.name = sub.name
                                                        pSet.subcategoryId = sub.id
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
                                        }
                                    }

                                    IconButton(
                                        onClick = { showManageCategoriesDialog = true },
                                        modifier = Modifier.testTag("manage_subcategories_set_${setIndex}_btn")
                                    ) {
                                        Icon(
                                            Icons.Default.Tune,
                                            contentDescription = "Manage Categories",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                // Clear Set Rules: Required switch + Steppers + Live summary
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (pSet.required) "Required" else "Optional",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Customer must choose at least the minimum below",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                        Switch(
                                            checked = pSet.required,
                                            onCheckedChange = { isReq ->
                                                pSet.required = isReq
                                                if (isReq && pSet.minSelect < 1) {
                                                    pSet.minSelect = 1
                                                }
                                            }
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // If Required: Minimum choices stepper (1 to maxSelect)
                                    if (pSet.required) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Minimum choices",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "Default 1, never above maximum",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        if (pSet.minSelect > 1) pSet.minSelect -= 1
                                                    },
                                                    enabled = pSet.minSelect > 1,
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.Remove, contentDescription = "Decrease Min", modifier = Modifier.size(16.dp))
                                                }

                                                Text(
                                                    text = "${pSet.minSelect}",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp)
                                                )

                                                IconButton(
                                                    onClick = {
                                                        if (pSet.minSelect < pSet.maxSelect) pSet.minSelect += 1
                                                    },
                                                    enabled = pSet.minSelect < pSet.maxSelect,
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = "Increase Min", modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    }

                                    // Maximum choices stepper (1 to 10)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Maximum choices",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "1 = customer picks one. 2 or more = customer can pick several, up to this number.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    if (pSet.maxSelect > 1) {
                                                        pSet.maxSelect -= 1
                                                        if (pSet.minSelect > pSet.maxSelect) {
                                                            pSet.minSelect = pSet.maxSelect
                                                        }
                                                    }
                                                },
                                                enabled = pSet.maxSelect > 1,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "Decrease Max", modifier = Modifier.size(16.dp))
                                            }

                                            Text(
                                                text = "${pSet.maxSelect}",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp)
                                            )

                                            IconButton(
                                                onClick = {
                                                    if (pSet.maxSelect < 10) pSet.maxSelect += 1
                                                },
                                                enabled = pSet.maxSelect < 10,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Increase Max", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }

                                    // Live summary line
                                    val summaryText = if (pSet.required) {
                                        if (pSet.minSelect == pSet.maxSelect) {
                                            "Required: customer picks ${pSet.minSelect}"
                                        } else {
                                            "Required: customer picks ${pSet.minSelect} to ${pSet.maxSelect}"
                                        }
                                    } else {
                                        "Optional: customer picks up to ${pSet.maxSelect}"
                                    }

                                    Surface(
                                        color = customColors.accent.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Info,
                                                contentDescription = null,
                                                tint = customColors.accent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = summaryText,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = customColors.accent
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                // Choices list
                                Text(
                                    text = "Choices / Options",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )

                                pSet.choices.forEachIndexed { cIdx, choice ->
                                    val isCNameBlank = choice.name.trim().isBlank()
                                    val cPriceVal = choice.priceText.toDoubleOrNull()
                                    val isCPriceInvalid = cPriceVal == null || cPriceVal < 0.0

                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = choice.name,
                                                onValueChange = { choice.name = it },
                                                label = { Text("Product Name *") },
                                                placeholder = { Text("e.g. Iced Tea") },
                                                singleLine = true,
                                                isError = isCNameBlank,
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.weight(1.3f)
                                            )

                                            OutlinedTextField(
                                                value = choice.priceText,
                                                onValueChange = { choice.priceText = it },
                                                label = { Text("Price (₱)") },
                                                supportingText = { Text("0 = Free", fontSize = 10.sp) },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                singleLine = true,
                                                isError = isCPriceInvalid,
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.weight(1.1f)
                                            )

                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Switch(
                                                    checked = choice.available,
                                                    onCheckedChange = { choice.available = it },
                                                    modifier = Modifier.scale(0.85f)
                                                )
                                                Text(
                                                    text = if (choice.available) "In stock" else "Out",
                                                    fontSize = 10.sp,
                                                    color = if (choice.available) customColors.accent else MaterialTheme.colorScheme.error
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    pSet.choices.removeAt(cIdx)
                                                },
                                                enabled = pSet.choices.size > 1,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Delete Choice",
                                                    tint = if (pSet.choices.size > 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
                                                )
                                            }
                                        }
                                        if (isCNameBlank) {
                                            Text(
                                                "Option name is required",
                                                color = MaterialTheme.colorScheme.error,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                                            )
                                        }
                                    }
                                }

                                TextButton(
                                    onClick = {
                                        pSet.choices.add(EditableChoice())
                                    },
                                    modifier = Modifier.align(Alignment.Start)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("+ Add product", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    // Below the card show another "+ Add Set" button (maximum 8 sets)
                    if (setsList.size < 8) {
                        OutlinedButton(
                            onClick = {
                                setsList.add(EditableSet(initialChoices = listOf(EditableChoice())))
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("add_another_set_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("+ Add Set (${setsList.size}/8)", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // 4. Description
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                shape = RoundedCornerShape(12.dp),
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            // 5. Image URL Input & Preview
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

            // 6. Food Mode vs Convenience Mode Fields
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

                val allTagOptions = listOf("Best Seller", "Chef Special", "Spicy", "Vegetarian", "Halal")
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

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Failure Error Dialog with Copy button
    failureErrorMessage?.let { errMsg ->
        AlertDialog(
            onDismissRequest = { failureErrorMessage = null },
            title = {
                Text(
                    "Failed to Save Product",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Column {
                    Text(
                        "An error occurred while communicating with Firestore:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(errMsg))
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Error")
                }
            },
            dismissButton = {
                TextButton(onClick = { failureErrorMessage = null }) {
                    Text("Dismiss")
                }
            }
        )
    }

    // Subcategories (Category Class) Dialogs

    // 1. Type your Category Dialog
    if (showTypeCategoryDialog) {
        var categoryNameInput by remember { mutableStateOf("") }
        var categoryNameError by remember { mutableStateOf<String?>(null) }
        var isSubmitting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                if (!isSubmitting) {
                    showTypeCategoryDialog = false
                    categoryNameError = null
                }
            },
            title = {
                Text(
                    text = "Type your Category",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = categoryNameInput,
                        onValueChange = {
                            categoryNameInput = it
                            categoryNameError = null
                        },
                        label = { Text("Category Name *") },
                        placeholder = { Text("e.g. Milk Tea, Pastries, Shakes") },
                        singleLine = true,
                        isError = categoryNameError != null,
                        supportingText = categoryNameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("type_category_name_input")
                    )
                }
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
                            categoryNameError = "Category name cannot exceed 40 characters"
                            return@Button
                        }
                        if (subcategories.any { it.name.trim().equals(trimmed, ignoreCase = true) }) {
                            categoryNameError = "Category \"$trimmed\" already exists"
                            return@Button
                        }
                        if (subcategories.size >= 30) {
                            categoryNameError = "Maximum 30 categories allowed per shop"
                            return@Button
                        }
                        isSubmitting = true
                        onCreateSubcategory(trimmed) { success, createdSub, err ->
                            isSubmitting = false
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
                    enabled = !isSubmitting,
                    modifier = Modifier.testTag("save_type_category_btn")
                ) {
                    Text(if (isSubmitting) "Saving..." else "Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showTypeCategoryDialog = false
                        categoryNameError = null
                    },
                    enabled = !isSubmitting
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // 2. Manage Categories Dialog
    if (showManageCategoriesDialog) {
        AlertDialog(
            onDismissRequest = { showManageCategoriesDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "My Categories",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "${subcategories.size}/30",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                if (subcategories.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No custom categories yet. Tap + Add to create one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        subcategories.forEach { sub ->
                            val count = menuItems.count {
                                it.subcategoryId == sub.id || (it.subcategoryId == null && it.subcategoryName?.equals(sub.name, ignoreCase = true) == true)
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = sub.name,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "$count ${if (count == 1) "product" else "products"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = { subcategoryToRename = sub },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Rename ${sub.name}",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { subcategoryToDelete = sub },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete ${sub.name}",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showManageCategoriesDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    // 3. Rename Category Dialog
    subcategoryToRename?.let { sub ->
        var renameInput by remember(sub) { mutableStateOf(sub.name) }
        var renameError by remember { mutableStateOf<String?>(null) }
        var isRenaming by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                if (!isRenaming) {
                    subcategoryToRename = null
                    renameError = null
                }
            },
            title = {
                Text(
                    text = "Rename Category",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = {
                            renameInput = it
                            renameError = null
                        },
                        label = { Text("Category Name *") },
                        singleLine = true,
                        isError = renameError != null,
                        supportingText = renameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = renameInput.trim()
                        if (trimmed.isBlank()) {
                            renameError = "Name cannot be empty"
                            return@Button
                        }
                        if (trimmed.length > 40) {
                            renameError = "Name cannot exceed 40 characters"
                            return@Button
                        }
                        if (subcategories.any { it.id != sub.id && it.name.trim().equals(trimmed, ignoreCase = true) }) {
                            renameError = "Category \"$trimmed\" already exists"
                            return@Button
                        }
                        isRenaming = true
                        onRenameSubcategory(sub.id, trimmed) { success, err ->
                            isRenaming = false
                            if (success) {
                                if (selectedSubcategoryId == sub.id) {
                                    selectedSubcategoryName = trimmed
                                }
                                setsList.forEach { s ->
                                    if (s.subcategoryId == sub.id || s.name.equals(sub.name, ignoreCase = true)) {
                                        s.name = trimmed
                                    }
                                }
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Category renamed successfully")
                                }
                                subcategoryToRename = null
                            } else {
                                renameError = err ?: "Failed to rename category"
                            }
                        }
                    },
                    enabled = !isRenaming
                ) {
                    Text(if (isRenaming) "Saving..." else "Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        subcategoryToRename = null
                        renameError = null
                    },
                    enabled = !isRenaming
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // 4. Delete Category Dialog
    subcategoryToDelete?.let { sub ->
        var isDeleting by remember { mutableStateOf(false) }
        val affectedCount = remember(sub, menuItems) {
            menuItems.count {
                it.subcategoryId == sub.id || (it.subcategoryId == null && it.subcategoryName?.equals(sub.name, ignoreCase = true) == true)
            }
        }

        AlertDialog(
            onDismissRequest = {
                if (!isDeleting) subcategoryToDelete = null
            },
            title = {
                Text(
                    text = "Delete Category",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    text = "$affectedCount ${if (affectedCount == 1) "product uses" else "products use"} this category. They will move to Other.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDeleting = true
                        onDeleteSubcategory(sub.id) { success, err ->
                            isDeleting = false
                            if (success) {
                                if (selectedSubcategoryId == sub.id) {
                                    selectedSubcategoryId = ""
                                    selectedSubcategoryName = ""
                                }
                                // Sets keep their saved names, only detach subcategoryId
                                setsList.forEach { s ->
                                    if (s.subcategoryId == sub.id) {
                                        s.subcategoryId = null
                                    }
                                }
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Category deleted")
                                }
                                subcategoryToDelete = null
                            } else {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(err ?: "Failed to delete category")
                                }
                                subcategoryToDelete = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = !isDeleting
                ) {
                    Text(if (isDeleting) "Deleting..." else "Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { subcategoryToDelete = null },
                    enabled = !isDeleting
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
