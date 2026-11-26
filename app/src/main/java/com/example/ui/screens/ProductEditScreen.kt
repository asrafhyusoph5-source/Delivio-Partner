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
import androidx.compose.ui.focus.onFocusChanged
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
import com.example.ui.components.DayChipsRow
import com.example.ui.components.orderedDaysList
import com.example.ui.theme.LocalDelivioColors
import com.example.ui.theme.StatusClosedRed
import com.example.ui.theme.StatusOpenGreen
import com.example.ui.theme.StatusWarningAmber
import com.example.util.CurrencyConstants
import com.example.util.MainCategoryConfig
import com.example.util.FormatUtils
import com.example.util.VariationUtils
import kotlinx.coroutines.launch
import java.util.UUID

private enum class ProductEditSubRoute {
    FORM,
    ADD_VARIATION,
    SET_STOCK_PRICE,
    SET_EDITOR
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProductEditScreen(
    item: MenuItem,
    isNew: Boolean,
    categories: List<MenuCategory>,
    subcategories: List<Subcategory> = emptyList(),
    menus: List<ShopMenu> = emptyList(),
    menuItems: List<MenuItem> = emptyList(),
    businessType: BusinessType,
    mainCategories: List<MainCategoryOption> = MainCategoryConfig.getBuiltInDefaults(businessType),
    currentTime: Long = System.currentTimeMillis(),
    timeZoneId: String = "Asia/Manila",
    onSave: (MenuItem, onComplete: (Boolean, String?) -> Unit) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreateSubcategory: (String, onComplete: (Boolean, Subcategory?, String?) -> Unit) -> Unit = { _, _ -> },
    onRenameSubcategory: (String, String, onComplete: (Boolean, String?) -> Unit) -> Unit = { _, _, _ -> },
    onDeleteSubcategory: (String, onComplete: (Boolean, String?) -> Unit) -> Unit = { _, _ -> },
    onShowSaleEndedNotice: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler { onDismiss() }

    val customColors = LocalDelivioColors.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val activeMainCategories = remember(mainCategories) {
        MainCategoryConfig.activeOrdered(mainCategories)
    }

    val initialResolvedCat = remember(item, categories, activeMainCategories) {
        MainCategoryConfig.resolveItemCategory(
            itemCategory = item.getEffectiveCategoryName(categories),
            itemCategoryId = item.categoryId,
            activeCategories = activeMainCategories,
            legacyShopCategories = categories
        )
    }

    // Sub-screen navigation (Form <-> Add Variation <-> Set Stock And Price / Set Editor)
    var subRoute by remember { mutableStateOf(ProductEditSubRoute.FORM) }
    var editingSetIndex by remember { mutableStateOf<Int?>(null) }
    var variationsEnabled by remember {
        mutableStateOf(businessType == BusinessType.CONVENIENCE && item.variationsEnabled && item.variants.isNotEmpty())
    }
    var variationsState by remember { mutableStateOf(item.variations) }
    var variantsState by remember { mutableStateOf(item.variants) }
    var draftVariationsForWizard by remember { mutableStateOf(item.variations) }
    var draftVariantsForWizard by remember { mutableStateOf(item.variants) }
    var showRemoveVariationsConfirm by remember { mutableStateOf(false) }
    var isOldSetsExpanded by remember { mutableStateOf(false) }

    // 1. Basic info
    var name by remember { mutableStateOf(item.name) }
    var priceText by remember { mutableStateOf(if (item.price > 0.0) item.price.toString() else "0") }
    var itemSizeText by remember { mutableStateOf(item.itemSize) }
    var hadActiveSaleNoticeShown by remember { mutableStateOf(false) }
    var selectedCategoryName by remember {
        mutableStateOf(
            when {
                initialResolvedCat.matchedOption != null -> initialResolvedCat.matchedOption.name
                initialResolvedCat.isLegacyUnmatched -> initialResolvedCat.storedCategoryName
                isNew -> activeMainCategories.firstOrNull()?.name ?: ""
                else -> ""
            }
        )
    }
    var selectedCategoryId by remember {
        mutableStateOf(
            when {
                initialResolvedCat.matchedOption != null -> initialResolvedCat.matchedOption.id
                initialResolvedCat.isLegacyUnmatched -> initialResolvedCat.storedCategoryId
                isNew -> activeMainCategories.firstOrNull()?.id ?: ""
                else -> ""
            }
        )
    }
    var isLegacyUnmatched by remember { mutableStateOf(initialResolvedCat.isLegacyUnmatched) }
    var legacyDisplayLabel by remember { mutableStateOf(initialResolvedCat.displayLabel) }
    var hasPickedNewCategory by remember { mutableStateOf(false) }

    LaunchedEffect(activeMainCategories) {
        if (!hasPickedNewCategory) {
            val resolved = MainCategoryConfig.resolveItemCategory(
                itemCategory = item.getEffectiveCategoryName(categories),
                itemCategoryId = item.categoryId,
                activeCategories = activeMainCategories,
                legacyShopCategories = categories
            )
            when {
                resolved.matchedOption != null -> {
                    selectedCategoryName = resolved.matchedOption.name
                    selectedCategoryId = resolved.matchedOption.id
                    isLegacyUnmatched = false
                    legacyDisplayLabel = ""
                }
                resolved.isLegacyUnmatched -> {
                    selectedCategoryName = resolved.storedCategoryName
                    selectedCategoryId = resolved.storedCategoryId
                    isLegacyUnmatched = true
                    legacyDisplayLabel = resolved.displayLabel
                }
                isNew && selectedCategoryName.isBlank() -> {
                    selectedCategoryName = activeMainCategories.firstOrNull()?.name ?: ""
                    selectedCategoryId = activeMainCategories.firstOrNull()?.id ?: ""
                    isLegacyUnmatched = false
                    legacyDisplayLabel = ""
                }
            }
        }
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
            initialAvailability?.days
                ?.map { it.trim().lowercase() }
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: setOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
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
    val isConvenienceWithVariations = businessType == BusinessType.CONVENIENCE && variationsEnabled
    val derivedVariantSummary = remember(isConvenienceWithVariations, variantsState) {
        if (isConvenienceWithVariations) VariationUtils.computeDerivedFields(variantsState) else null
    }
    val parsedPrice = priceText.toDoubleOrNull()
    val isPriceValid = if (isConvenienceWithVariations) {
        variantsState.isNotEmpty() && variantsState.all { it.price > 0.0 }
    } else {
        parsedPrice != null && parsedPrice >= 0.0
    }
    val isNameValid = name.isNotBlank()
    val isCatMissing = !isLegacyUnmatched && (
        selectedCategoryName.isBlank() ||
            activeMainCategories.none { MainCategoryConfig.matchesOption(selectedCategoryName, selectedCategoryId, it) }
        )

    val setErrors = remember(
        businessType,
        setsList.map { listOf(it.name, it.required.toString(), it.minSelect.toString(), it.maxSelect.toString(), it.choices.size.toString()) + it.choices.map { c -> "${c.name}:${c.priceText}:${c.available}:${c.popular}" } }
    ) {
        if (businessType == BusinessType.CONVENIENCE) {
            emptyList()
        } else {
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
                    set.choices.forEachIndexed { cIdx, choice ->
                        val cNum = cIdx + 1
                        val trimmedChoiceName = choice.name.trim()
                        if (trimmedChoiceName.isBlank()) {
                            errors.add("Set $sNum choice $cNum needs a name")
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
    }

    val existingSalePrice = item.salePrice
    val regularAtOrBelowActiveSale = !isConvenienceWithVariations &&
        item.saleActive &&
        existingSalePrice != null &&
        existingSalePrice > 0.0 &&
        parsedPrice != null &&
        parsedPrice >= 0.0 &&
        parsedPrice <= existingSalePrice

    val isAvailDaysValid = availabilityMode != "custom" || availDays.isNotEmpty()
    val canSave = isNameValid && !isCatMissing && isPriceValid && isAvailDaysValid && setErrors.isEmpty() && !isSaving

    if (businessType == BusinessType.CONVENIENCE) {
        when (subRoute) {
            ProductEditSubRoute.ADD_VARIATION -> {
                AddVariationScreen(
                    initialVariations = draftVariationsForWizard,
                    onBack = { subRoute = ProductEditSubRoute.FORM },
                    onNext = { nextVars ->
                        draftVariationsForWizard = nextVars
                        subRoute = ProductEditSubRoute.SET_STOCK_PRICE
                    },
                    modifier = modifier
                )
                return
            }
            ProductEditSubRoute.SET_STOCK_PRICE -> {
                SetStockAndPriceScreen(
                    variations = draftVariationsForWizard,
                    existingVariants = draftVariantsForWizard,
                    onBack = { partialVariants ->
                        draftVariantsForWizard = partialVariants
                        subRoute = ProductEditSubRoute.ADD_VARIATION
                    },
                    onSave = { finalVars, finalVariants ->
                        variationsEnabled = true
                        variationsState = finalVars
                        variantsState = finalVariants
                        draftVariationsForWizard = finalVars
                        draftVariantsForWizard = finalVariants
                        val derived = VariationUtils.computeDerivedFields(finalVariants)
                        priceText = derived.priceMin.toString()
                        stockCountText = derived.stockCount.toString()
                        inStock = derived.inStock
                        subRoute = ProductEditSubRoute.FORM
                    },
                    modifier = modifier
                )
                return
            }
            ProductEditSubRoute.FORM, ProductEditSubRoute.SET_EDITOR -> Unit
        }
    } else if (subRoute == ProductEditSubRoute.SET_EDITOR) {
        val targetIdx = editingSetIndex
        val existingSet = targetIdx?.let { setsList.getOrNull(it) }
        val otherNames = setsList
            .filterIndexed { idx, _ -> idx != targetIdx }
            .map { it.name.trim() }
            .filter { it.isNotBlank() }
            .toSet()

        OptionSetEditorScreen(
            initialSet = existingSet,
            isNew = existingSet == null,
            existingSetNames = otherNames,
            onSave = { savedSet ->
                if (targetIdx != null && targetIdx in setsList.indices) {
                    setsList[targetIdx] = savedSet
                } else if (setsList.size < 12) {
                    setsList.add(savedSet)
                }
                editingSetIndex = null
                subRoute = ProductEditSubRoute.FORM
            },
            onBack = {
                editingSetIndex = null
                subRoute = ProductEditSubRoute.FORM
            },
            modifier = modifier
        )
        return
    }

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
                            !isAvailDaysValid -> "Choose at least one day"
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

                                val updatedVariantsForSave = if (isConvenienceWithVariations) {
                                    variantsState.map { v ->
                                        val sp = v.salePrice
                                        if (item.saleActive && sp != null && sp > 0.0 && v.price <= sp) {
                                            v.copy(salePrice = null)
                                        } else {
                                            v
                                        }
                                    }
                                } else emptyList()

                                val derivedForSave = if (isConvenienceWithVariations) {
                                    VariationUtils.computeDerivedFields(updatedVariantsForSave)
                                } else null

                                val priceVal = derivedForSave?.priceMin ?: (parsedPrice ?: 0.0)
                                val parsedPrep = prepTimeText.toIntOrNull() ?: item.prepTimeMinutes
                                val parsedStock = derivedForSave?.stockCount ?: (stockCountText.toIntOrNull() ?: item.stockCount)
                                val parsedThreshold = lowStockThresholdText.toIntOrNull() ?: item.lowStockAt

                                val preserveLegacyUnchanged = isLegacyUnmatched && !hasPickedNewCategory
                                val finalCatName = if (preserveLegacyUnchanged) {
                                    item.category.ifBlank { item.categoryName }
                                } else {
                                    selectedCategoryName
                                }
                                val finalCatId = if (preserveLegacyUnchanged) {
                                    item.categoryId
                                } else {
                                    selectedCategoryId.ifBlank {
                                        activeMainCategories.firstOrNull { MainCategoryConfig.matchesOption(finalCatName, it) }?.id
                                            ?: MainCategoryConfig.slugifyId(finalCatName)
                                    }
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
                                        days = orderedDaysList(availDays),
                                        start = availStartTime24,
                                        end = availEndTime24,
                                        dateFrom = if (limitToDates && availDateFrom.isNotBlank()) availDateFrom else null,
                                        dateTo = if (limitToDates && availDateTo.isNotBlank()) availDateTo else null
                                    )
                                } else null

                                // Behind-the-scenes price drop safeguard:
                                // Preserve all existing sale fields unless regular price is edited to be <= active sale price.
                                val didAutoEndSale = if (isConvenienceWithVariations) {
                                    item.saleActive && updatedVariantsForSave.none { v ->
                                        val sp = v.salePrice
                                        sp != null && sp > 0.0 && sp < v.price
                                    }
                                } else {
                                    item.saleActive && existingSalePrice != null && existingSalePrice > 0.0 && priceVal <= existingSalePrice
                                }

                                val finalSaleActive: Boolean
                                val finalSalePrice: Double?
                                val finalSaleStartsAt: Long?
                                val finalSaleEndsAt: Long?
                                if (didAutoEndSale) {
                                    finalSaleActive = false
                                    finalSalePrice = item.salePrice
                                    finalSaleStartsAt = null
                                    finalSaleEndsAt = null
                                } else {
                                    finalSaleActive = item.saleActive
                                    finalSalePrice = item.salePrice
                                    finalSaleStartsAt = item.saleStartsAt
                                    finalSaleEndsAt = item.saleEndsAt
                                }

                                val updated = item.copy(
                                    name = name.trim().ifBlank { "Untitled Item" },
                                    description = description.trim(),
                                    price = priceVal,
                                    priceMax = derivedForSave?.priceMax,
                                    salePrice = finalSalePrice,
                                    saleStartsAt = finalSaleStartsAt,
                                    saleEndsAt = finalSaleEndsAt,
                                    saleActive = finalSaleActive,
                                    categoryId = finalCatId,
                                    category = finalCatName,
                                    categoryName = if (preserveLegacyUnchanged) item.categoryName.ifBlank { finalCatName } else finalCatName,
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
                                    trackStock = if (isConvenienceWithVariations) true else trackStock,
                                    stockCount = parsedStock,
                                    lowStockAt = parsedThreshold,
                                    inStock = derivedForSave?.inStock ?: (if (trackStock && parsedStock <= 0) false else inStock),
                                    businessType = businessType,
                                    hasSets = finalSets.isNotEmpty(),
                                    sets = finalSets,
                                    variationsEnabled = isConvenienceWithVariations,
                                    variations = if (isConvenienceWithVariations) VariationUtils.normalizeVariationsForSave(variationsState) else emptyList(),
                                    variants = updatedVariantsForSave
                                )

                                onSave(updated) { success, errorMsg ->
                                    isSaving = false
                                    if (!success) {
                                        failureErrorMessage = errorMsg ?: "Unknown error occurred while saving product."
                                    } else if (didAutoEndSale) {
                                        onShowSaleEndedNotice("Price drop ended because the regular price is at or below the sale price.")
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
                    if (isConvenienceWithVariations && derivedVariantSummary != null) {
                        OutlinedCard(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("variations_price_readonly")
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text(
                                    text = "Price",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val rangeText = if (derivedVariantSummary.priceMax > derivedVariantSummary.priceMin) {
                                    "Price from ${FormatUtils.formatCurrency(derivedVariantSummary.priceMin)} – ${FormatUtils.formatCurrency(derivedVariantSummary.priceMax)}"
                                } else {
                                    "Price from ${FormatUtils.formatCurrency(derivedVariantSummary.priceMin)}"
                                }
                                Text(
                                    text = rangeText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = customColors.accent
                                )
                                Text(
                                    text = "Set per variation",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = priceText,
                            onValueChange = {
                                priceText = it
                                hadActiveSaleNoticeShown = false
                            },
                            label = { Text("Price (${CurrencyConstants.SYMBOL}) *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            isError = hasTappedSaveOnce && !isPriceValid,
                            supportingText = if (hasTappedSaveOnce && !isPriceValid) {
                                { Text("Invalid price", color = MaterialTheme.colorScheme.error) }
                            } else null,
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { focusState ->
                                    if (!focusState.isFocused &&
                                        regularAtOrBelowActiveSale &&
                                        !hadActiveSaleNoticeShown
                                    ) {
                                        hadActiveSaleNoticeShown = true
                                        val msg = "Regular price is at or below the active sale price — saving will end that price drop."
                                        coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                }
                                .testTag("menu_item_price_input")
                        )
                    }

                    var catExpanded by remember { mutableStateOf(false) }
                    val displayCategoryValue = when {
                        isLegacyUnmatched && !hasPickedNewCategory -> legacyDisplayLabel
                        selectedCategoryName.isNotBlank() -> selectedCategoryName
                        else -> "Category *"
                    }
                    ExposedDropdownMenuBox(
                        expanded = catExpanded,
                        onExpandedChange = { catExpanded = it },
                        modifier = Modifier.weight(1.3f)
                    ) {
                        OutlinedTextField(
                            value = displayCategoryValue,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Main Category *") },
                            isError = hasTappedSaveOnce && isCatMissing,
                            supportingText = when {
                                hasTappedSaveOnce && isCatMissing -> {
                                    { Text("Required", color = MaterialTheme.colorScheme.error) }
                                }
                                isLegacyUnmatched && !hasPickedNewCategory -> {
                                    { Text("Saved category is no longer active", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                                else -> null
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.menuAnchor().fillMaxWidth().testTag("menu_item_category_dropdown")
                        )
                        ExposedDropdownMenu(
                            expanded = catExpanded,
                            onDismissRequest = { catExpanded = false }
                        ) {
                            activeMainCategories.forEach { catOption ->
                                val isCurrentlySelected = !isLegacyUnmatched &&
                                    MainCategoryConfig.matchesOption(selectedCategoryName, selectedCategoryId, catOption)
                                DropdownMenuItem(
                                    text = { Text(catOption.name, fontWeight = if (isCurrentlySelected) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = {
                                        hasPickedNewCategory = true
                                        isLegacyUnmatched = false
                                        legacyDisplayLabel = ""
                                        selectedCategoryName = catOption.name
                                        selectedCategoryId = catOption.id
                                        catExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (regularAtOrBelowActiveSale && existingSalePrice != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = StatusClosedRed.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth().testTag("sale_auto_end_warning")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = StatusClosedRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Regular price is at or below the active sale price (${FormatUtils.formatCurrency(existingSalePrice)}) — saving will end that price drop.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = StatusClosedRed
                            )
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
                        val priceNum = if (isConvenienceWithVariations) {
                            derivedVariantSummary?.priceMin ?: 0.0
                        } else {
                            parsedPrice ?: 0.0
                        }
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

                        DayChipsRow(
                            selectedDays = availDays,
                            onChange = { availDays = it }
                        )
                        if (availDays.isEmpty()) {
                            Text(
                                text = "Choose at least one day",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("product_avail_days_error")
                            )
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

            // SECTION 5: Variations (Convenience) OR Options (Sets) (Food)
            if (businessType == BusinessType.CONVENIENCE) {
                SectionCard(
                    title = "Variations",
                    helpText = "Variations let convenience shop customers choose options like Color, Size, or Flavor, each with its own price and stock.",
                    onHelpClick = { activeHelpDialogText = it }
                ) {
                    if (!variationsEnabled || variantsState.isEmpty()) {
                        Text(
                            text = "Add options such as Color, Size, or Flavor with their own prices and stock counts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = {
                                draftVariationsForWizard = variationsState
                                draftVariantsForWizard = variantsState
                                subRoute = ProductEditSubRoute.ADD_VARIATION
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("add_variation_initial_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("+ Add variation", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        val summaryLine = VariationUtils.formatSummaryLine(variationsState, variantsState.size)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = customColors.accent.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, customColors.accent.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = summaryLine,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("variations_summary_text")
                                )
                                TextButton(
                                    onClick = {
                                        draftVariationsForWizard = variationsState
                                        draftVariantsForWizard = variantsState
                                        subRoute = ProductEditSubRoute.ADD_VARIATION
                                    },
                                    modifier = Modifier.testTag("edit_variations_btn")
                                ) {
                                    Text("Edit", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Compact read-only table of combinations (label, price, stock)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("Variation", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.4f))
                                    Text("Price", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.9f))
                                    Text("Stock", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.6f))
                                }
                                variantsState.forEachIndexed { idx, variant ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = variant.label,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1.4f)
                                        )
                                        Text(
                                            text = FormatUtils.formatCurrency(variant.price),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(0.9f)
                                        )
                                        Text(
                                            text = variant.stock.toString(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (variant.stock <= 0) StatusClosedRed else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (variant.stock <= 0) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.weight(0.6f)
                                        )
                                    }
                                    if (idx < variantsState.lastIndex) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    draftVariationsForWizard = variationsState
                                    draftVariantsForWizard = variantsState
                                    subRoute = ProductEditSubRoute.SET_STOCK_PRICE
                                },
                                modifier = Modifier.testTag("edit_price_and_stock_btn")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit price and stock", fontWeight = FontWeight.Bold)
                            }

                            TextButton(
                                onClick = { showRemoveVariationsConfirm = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.testTag("remove_all_variations_btn")
                            ) {
                                Text("Remove all variations", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // Collapsed delete-only "Old options (sets)" card if convenience item still has legacy sets
                if (setsList.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("legacy_old_sets_card")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isOldSetsExpanded = !isOldSetsExpanded },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Old options (sets)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${setsList.size} legacy ${if (setsList.size == 1) "set" else "sets"} (remove only)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = if (isOldSetsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Expand/Collapse Old Sets"
                                )
                            }

                            if (isOldSetsExpanded) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    setsList.forEachIndexed { idx, oldSet ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
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
                                                    Text(oldSet.name.ifBlank { "Set ${idx + 1}" }, fontWeight = FontWeight.SemiBold)
                                                    Text(
                                                        text = "${oldSet.choices.size} choices",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { setsList.removeAt(idx) },
                                                    modifier = Modifier.testTag("remove_legacy_set_$idx")
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "Remove old set",
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
            // SECTION 5: Options (Sets) for Food shops — compact summary cards + full-screen editor
            SectionCard(
                title = "Options (Sets)",
                helpText = "Sets let customers choose options such as flavor, drink, or add-ons. Example: set 'Drink' with Iced Tea (Free) and Coke (+₱10).",
                onHelpClick = { activeHelpDialogText = it }
            ) {
                if (setsList.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth().testTag("sets_empty_state_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Let customers customize this item",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Sets let customers choose options such as flavor, drink, or add-ons. Example: set \"Drink\" with Iced Tea (Free) and Coke (+₱10).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            editingSetIndex = null
                            subRoute = ProductEditSubRoute.SET_EDITOR
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("add_set_initial_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+ Add Set", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        setsList.forEachIndexed { setIndex, pSet ->
                            val ruleSummary = buildString {
                                append(if (pSet.required) "Required" else "Optional")
                                append(" - ")
                                append(
                                    when {
                                        !pSet.required -> "Up to ${pSet.maxSelect.coerceAtLeast(1)}"
                                        pSet.minSelect.coerceAtLeast(1) == pSet.maxSelect.coerceAtLeast(1) -> "Select ${pSet.minSelect.coerceAtLeast(1)}"
                                        else -> "Select ${pSet.minSelect.coerceAtLeast(1)} to ${pSet.maxSelect.coerceAtLeast(1)}"
                                    }
                                )
                            }

                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        editingSetIndex = setIndex
                                        subRoute = ProductEditSubRoute.SET_EDITOR
                                    }
                                    .testTag("set_card_$setIndex")
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = pSet.name.trim().ifBlank { "Set ${setIndex + 1}" },
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = ruleSummary,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = customColors.accent,
                                                modifier = Modifier.testTag("set_summary_rule_$setIndex")
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    editingSetIndex = setIndex
                                                    subRoute = ProductEditSubRoute.SET_EDITOR
                                                },
                                                modifier = Modifier.size(36.dp).testTag("edit_set_btn_$setIndex")
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit Set", modifier = Modifier.size(18.dp))
                                            }

                                            IconButton(
                                                onClick = {
                                                    if (setsList.size < 12) {
                                                        val clonedChoices = pSet.choices.map { c ->
                                                            c.deepCopy(newId = UUID.randomUUID().toString())
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
                                                    }
                                                },
                                                enabled = setsList.size < 12,
                                                modifier = Modifier.size(36.dp).testTag("duplicate_set_btn_$setIndex")
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate Set", modifier = Modifier.size(18.dp))
                                            }

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

                                            IconButton(
                                                onClick = { setIndexToDelete = setIndex },
                                                modifier = Modifier.size(36.dp).testTag("delete_set_btn_$setIndex")
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete Set", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }

                                    // Compact Choice Chips
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        pSet.choices.forEach { c ->
                                            val cPrice = c.priceText.toDoubleOrNull() ?: 0.0
                                            val cPriceLabel = if (cPrice <= 0.0) "Free" else "+${FormatUtils.formatCurrency(cPrice)}"
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (c.available) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                                                )
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    if (c.popular) {
                                                        Icon(
                                                            imageVector = Icons.Default.Whatshot,
                                                            contentDescription = "Popular",
                                                            tint = Color(0xFFFF5722),
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                    }
                                                    Text(
                                                        text = "${c.name.trim().ifBlank { "Unnamed" }} ($cPriceLabel)",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Medium,
                                                        color = if (c.available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                                    )
                                                    if (!c.available) {
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "· Sold out",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (setsList.size < 12) {
                            OutlinedButton(
                                onClick = {
                                    editingSetIndex = null
                                    subRoute = ProductEditSubRoute.SET_EDITOR
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("add_another_set_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("+ Add Set (${setsList.size}/12)", fontWeight = FontWeight.SemiBold)
                            }
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
                if (isConvenienceWithVariations && derivedVariantSummary != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth().testTag("variations_stock_readonly")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (derivedVariantSummary.inStock) "In stock (${derivedVariantSummary.stockCount} total)" else "Sold out (0 total)",
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Total stock comes from your variations.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = lowStockThresholdText,
                            onValueChange = { lowStockThresholdText = it },
                            label = { Text("Low stock at (per variant)") },
                            placeholder = { Text("5") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("low_stock_threshold_input")
                        )
                    }
                } else {
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
                                if (!isConvenienceWithVariations) {
                                    OutlinedTextField(value = stockCountText, onValueChange = { stockCountText = it }, label = { Text("Stock") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                                }
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

    if (showRemoveVariationsConfirm) {
        val fallbackPrice = VariationUtils.computeDerivedFields(variantsState).priceMin
        AlertDialog(
            onDismissRequest = { showRemoveVariationsConfirm = false },
            title = { Text("Remove all variations?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This will clear all variations and combinations. The product price will be set to ${FormatUtils.formatCurrency(fallbackPrice)}.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        priceText = if (fallbackPrice > 0.0) formatEditablePrice(fallbackPrice) else "0"
                        variationsEnabled = false
                        variationsState = emptyList()
                        variantsState = emptyList()
                        draftVariationsForWizard = emptyList()
                        draftVariantsForWizard = emptyList()
                        showRemoveVariationsConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_remove_variations_btn")
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveVariationsConfirm = false }) {
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
