package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.BusinessType
import com.example.data.model.MenuItem
import com.example.data.model.MenuSchedule
import com.example.data.model.ShopMenu
import com.example.ui.theme.LocalDelivioColors
import com.example.ui.theme.StatusClosedRed
import com.example.ui.theme.StatusOpenGreen
import com.example.ui.theme.StatusWarningAmber
import com.example.util.CurrencyConstants
import com.example.util.FormatUtils
import com.example.util.PriceDropUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceDropsScreen(
    menuItems: List<MenuItem>,
    menus: List<ShopMenu>,
    businessType: BusinessType,
    currentTime: Long,
    timeZoneId: String,
    onBack: () -> Unit,
    onApplyPriceDrops: (
        itemSalePrices: Map<String, Double>,
        saleStartsAt: Long?,
        saleEndsAt: Long?,
        onComplete: (Boolean, Int, String?) -> Unit
    ) -> Unit,
    onEndPriceDrop: (itemId: String, onComplete: (Boolean, String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    onApplyVariationPriceDrops: ((
        itemSalePrices: Map<String, Double>,
        variantSalePrices: Map<String, Map<String, Double>>,
        saleStartsAt: Long?,
        saleEndsAt: Long?,
        onComplete: (Boolean, Int, String?) -> Unit
    ) -> Unit)? = null
) {
    BackHandler { onBack() }

    val customColors = LocalDelivioColors.current
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Active, 1: New price drop

    val shopItems = remember(menuItems, businessType) {
        menuItems.filter { it.businessType == businessType }
    }

    val activeDropItems = remember(shopItems, currentTime) {
        shopItems
            .filter { it.isSaleInEffect(currentTime) || it.isSaleScheduled(currentTime) }
            .sortedWith(
                compareBy<MenuItem>(
                    { !it.isSaleInEffect(currentTime) },
                    { it.name.lowercase() }
                )
            )
    }

    // State for "New price drop" tab
    var searchQuery by remember { mutableStateOf("") }
    val selectedItemIds = remember { mutableStateListOf<String>() }
    val priceInputs = remember { mutableStateMapOf<String, String>() }
    val variantPriceInputs = remember { mutableStateMapOf<String, String>() }
    val touchedItemIds = remember { mutableStateMapOf<String, Boolean>() }
    var hasAttemptedSave by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // Schedule state
    var startMode by remember { mutableStateOf("now") } // "now" | "scheduled"
    var endMode by remember { mutableStateOf("until_ended") } // "until_ended" | "scheduled"

    val defaultDates = remember(currentTime, timeZoneId) {
        val tz = TimeZone.getTimeZone(timeZoneId.ifBlank { "Asia/Manila" })
        val cal = Calendar.getInstance(tz).apply { timeInMillis = currentTime }
        val today = "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
        val endCal = Calendar.getInstance(tz).apply {
            timeInMillis = currentTime
            add(Calendar.DAY_OF_YEAR, 7)
        }
        val plus7 = "%04d-%02d-%02d".format(
            endCal.get(Calendar.YEAR),
            endCal.get(Calendar.MONTH) + 1,
            endCal.get(Calendar.DAY_OF_MONTH)
        )
        today to plus7
    }

    var startDateStr by remember { mutableStateOf(defaultDates.first) }
    var startTime24 by remember { mutableStateOf("09:00") }
    var endDateStr by remember { mutableStateOf(defaultDates.second) }
    var endTime24 by remember { mutableStateOf("23:59") }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    var endingItemId by remember { mutableStateOf<String?>(null) }
    var feedbackBannerError by remember { mutableStateOf<String?>(null) }

    val selectedItems = remember(shopItems, selectedItemIds.toList()) {
        selectedItemIds.mapNotNull { id -> shopItems.find { it.id == id } }
    }

    val startEpochMs: Long? = remember(startMode, startDateStr, startTime24, timeZoneId) {
        if (startMode == "scheduled") combineDateTimeToMillis(startDateStr, startTime24, timeZoneId) else null
    }
    val endEpochMs: Long? = remember(endMode, endDateStr, endTime24, timeZoneId) {
        if (endMode == "scheduled") combineDateTimeToMillis(endDateStr, endTime24, timeZoneId) else null
    }

    val scheduleError: String? = remember(startEpochMs, endEpochMs, currentTime, startMode, endMode) {
        when {
            endMode == "scheduled" && startMode == "scheduled" && startEpochMs != null && endEpochMs != null && endEpochMs <= startEpochMs ->
                "End date and time must be after start date and time."
            endMode == "scheduled" && endEpochMs != null && endEpochMs <= currentTime ->
                "End date and time must be in the future."
            else -> null
        }
    }

    val validations = remember(selectedItems, priceInputs.toMap()) {
        selectedItems
            .filter { !(it.variationsEnabled && it.variants.isNotEmpty()) }
            .associate { item ->
                item.id to PriceDropUtils.validateSalePrice(item.price, priceInputs[item.id].orEmpty())
            }
    }

    val variantValidations = remember(selectedItems, variantPriceInputs.toMap()) {
        val map = mutableMapOf<String, com.example.util.SalePriceValidation>()
        selectedItems
            .filter { it.variationsEnabled && it.variants.isNotEmpty() }
            .forEach { item ->
                item.variants.forEach { v ->
                    val k = "${item.id}::${v.key}"
                    val raw = variantPriceInputs[k].orEmpty()
                    if (raw.isNotBlank()) {
                        map[k] = PriceDropUtils.validateSalePrice(v.price, raw)
                    }
                }
            }
        map.toMap()
    }

    val allSelectedValid = selectedItems.isNotEmpty() &&
        scheduleError == null &&
        selectedItems.all { item ->
            if (item.variationsEnabled && item.variants.isNotEmpty()) {
                val entered = item.variants.mapNotNull { v ->
                    val k = "${item.id}::${v.key}"
                    val raw = variantPriceInputs[k].orEmpty()
                    if (raw.isBlank()) null else variantValidations[k]
                }
                entered.isNotEmpty() && entered.all { it.isValid }
            } else {
                validations[item.id]?.isValid == true
            }
        }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("price_drops_screen"),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("price_drops_back_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Price Drops",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (activeDropItems.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = customColors.accent.copy(alpha = 0.16f),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = "${activeDropItems.size} active",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = customColors.accent,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = customColors.accent
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = {
                                Text(
                                    text = if (activeDropItems.isNotEmpty()) "Active (${activeDropItems.size})" else "Active",
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.testTag("price_drops_tab_active")
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = {
                                Text(
                                    text = "New price drop",
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.testTag("price_drops_tab_new")
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (selectedTab == 1) {
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
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!feedbackBannerError.isNullOrBlank()) {
                            Text(
                                text = feedbackBannerError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Button(
                            onClick = {
                                hasAttemptedSave = true
                                feedbackBannerError = null
                                if (selectedItems.isEmpty()) {
                                    feedbackBannerError = "Choose at least one product."
                                    return@Button
                                }
                                if (!allSelectedValid) {
                                    feedbackBannerError = scheduleError ?: "Fix the highlighted new prices before saving."
                                    return@Button
                                }
                                val singlePayload = selectedItems
                                    .filter { !(it.variationsEnabled && it.variants.isNotEmpty()) }
                                    .associate { item ->
                                        item.id to validations[item.id]!!.parsedPrice!!
                                    }
                                val variantPayload = selectedItems
                                    .filter { it.variationsEnabled && it.variants.isNotEmpty() }
                                    .associate { item ->
                                        val mapForItem = item.variants.mapNotNull { v ->
                                            val k = "${item.id}::${v.key}"
                                            val res = variantValidations[k]
                                            if (res?.isValid == true && res.parsedPrice != null) {
                                                v.key to res.parsedPrice
                                            } else null
                                        }.toMap()
                                        item.id to mapForItem
                                    }
                                    .filterValues { it.isNotEmpty() }

                                isSaving = true
                                val onDone: (Boolean, Int, String?) -> Unit = { success, _, err ->
                                    isSaving = false
                                    if (success) {
                                        selectedItemIds.clear()
                                        priceInputs.clear()
                                        variantPriceInputs.clear()
                                        touchedItemIds.clear()
                                        hasAttemptedSave = false
                                        feedbackBannerError = null
                                        selectedTab = 0
                                    } else {
                                        feedbackBannerError = err ?: "Failed to save price drops"
                                    }
                                }
                                if (variantPayload.isNotEmpty() && onApplyVariationPriceDrops != null) {
                                    onApplyVariationPriceDrops(singlePayload, variantPayload, startEpochMs, endEpochMs, onDone)
                                } else {
                                    onApplyPriceDrops(singlePayload, startEpochMs, endEpochMs, onDone)
                                }
                            },
                            enabled = !isSaving,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = customColors.accent,
                                contentColor = customColors.onAccent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("price_drop_save_btn")
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = customColors.onAccent,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                val label = if (selectedItems.isEmpty()) {
                                    "Save Price Drop"
                                } else {
                                    "Save Price Drop (${selectedItems.size})"
                                }
                                Text(label, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (selectedTab == 0) {
                ActivePriceDropsTab(
                    activeDropItems = activeDropItems,
                    businessType = businessType,
                    currentTime = currentTime,
                    timeZoneId = timeZoneId,
                    endingItemId = endingItemId,
                    onEndDropClick = { itemId ->
                        endingItemId = itemId
                        onEndPriceDrop(itemId) { _, _ ->
                            endingItemId = null
                        }
                    },
                    onCreateNewClick = { selectedTab = 1 }
                )
            } else {
                NewPriceDropTab(
                    shopItems = shopItems,
                    menus = menus,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    selectedItemIds = selectedItemIds,
                    onToggleItemSelected = { item ->
                        if (selectedItemIds.contains(item.id)) {
                            selectedItemIds.remove(item.id)
                            priceInputs.remove(item.id)
                            item.variants.forEach { v -> variantPriceInputs.remove("${item.id}::${v.key}") }
                            touchedItemIds.remove(item.id)
                        } else {
                            selectedItemIds.add(item.id)
                            if (item.variationsEnabled && item.variants.isNotEmpty()) {
                                item.variants.forEach { v ->
                                    val sp = v.salePrice
                                    if (item.saleActive && sp != null && sp > 0.0 && sp < v.price) {
                                        variantPriceInputs["${item.id}::${v.key}"] = formatEditablePrice(sp)
                                    }
                                }
                            } else if (item.saleActive && item.salePrice != null && item.salePrice > 0.0 && item.salePrice < item.price) {
                                priceInputs[item.id] = formatEditablePrice(item.salePrice)
                            }
                        }
                        feedbackBannerError = null
                    },
                    onPreselectMenuGroup = { menu ->
                        val matching = shopItems.filter {
                            ((it.variationsEnabled && it.variants.any { v -> v.price > 0.0 }) || it.price > 0.0) &&
                                it.getEffectiveMenuIds().contains(menu.id)
                        }
                        matching.forEach { item ->
                            if (!selectedItemIds.contains(item.id)) {
                                selectedItemIds.add(item.id)
                                if (item.variationsEnabled && item.variants.isNotEmpty()) {
                                    item.variants.forEach { v ->
                                        val sp = v.salePrice
                                        if (item.saleActive && sp != null && sp > 0.0 && sp < v.price) {
                                            variantPriceInputs["${item.id}::${v.key}"] = formatEditablePrice(sp)
                                        }
                                    }
                                } else if (item.saleActive && item.salePrice != null && item.salePrice > 0.0 && item.salePrice < item.price) {
                                    priceInputs[item.id] = formatEditablePrice(item.salePrice)
                                }
                            }
                        }
                        feedbackBannerError = null
                    },
                    onClearSelection = {
                        selectedItemIds.clear()
                        priceInputs.clear()
                        variantPriceInputs.clear()
                        touchedItemIds.clear()
                        hasAttemptedSave = false
                        feedbackBannerError = null
                    },
                    selectedItems = selectedItems,
                    priceInputs = priceInputs,
                    onPriceInputChange = { itemId, newVal ->
                        priceInputs[itemId] = newVal
                        touchedItemIds[itemId] = true
                        feedbackBannerError = null
                    },
                    variantPriceInputs = variantPriceInputs,
                    onVariantPriceInputChange = { itemId, variantKey, newVal ->
                        variantPriceInputs["$itemId::$variantKey"] = newVal
                        touchedItemIds[itemId] = true
                        feedbackBannerError = null
                    },
                    touchedItemIds = touchedItemIds,
                    hasAttemptedSave = hasAttemptedSave,
                    validations = validations,
                    variantValidations = variantValidations,
                    startMode = startMode,
                    onStartModeChange = { startMode = it },
                    startDateStr = startDateStr,
                    startTime24 = startTime24,
                    onPickStartDate = { showStartDatePicker = true },
                    onPickStartTime = { showStartTimePicker = true },
                    endMode = endMode,
                    onEndModeChange = { endMode = it },
                    endDateStr = endDateStr,
                    endTime24 = endTime24,
                    onPickEndDate = { showEndDatePicker = true },
                    onPickEndTime = { showEndTimePicker = true },
                    scheduleError = scheduleError
                )
            }
        }
    }

    if (showStartDatePicker) {
        SimpleDatePickerDialog(
            initialDateYmd = startDateStr,
            onDismiss = { showStartDatePicker = false },
            onDateSelected = {
                startDateStr = it
                showStartDatePicker = false
            }
        )
    }

    if (showStartTimePicker) {
        PriceDropTimePickerDialog(
            initialTime24 = startTime24,
            title = "Start time",
            onDismiss = { showStartTimePicker = false },
            onTimeSelected = {
                startTime24 = it
                showStartTimePicker = false
            }
        )
    }

    if (showEndDatePicker) {
        SimpleDatePickerDialog(
            initialDateYmd = endDateStr,
            onDismiss = { showEndDatePicker = false },
            onDateSelected = {
                endDateStr = it
                showEndDatePicker = false
            }
        )
    }

    if (showEndTimePicker) {
        PriceDropTimePickerDialog(
            initialTime24 = endTime24,
            title = "End time",
            onDismiss = { showEndTimePicker = false },
            onTimeSelected = {
                endTime24 = it
                showEndTimePicker = false
            }
        )
    }
}

@Composable
private fun ActivePriceDropsTab(
    activeDropItems: List<MenuItem>,
    businessType: BusinessType,
    currentTime: Long,
    timeZoneId: String,
    endingItemId: String?,
    onEndDropClick: (String) -> Unit,
    onCreateNewClick: () -> Unit
) {
    val customColors = LocalDelivioColors.current

    if (activeDropItems.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
                .testTag("active_price_drops_empty"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocalOffer,
                    contentDescription = null,
                    tint = customColors.accent,
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    text = "No active price drops",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Discount selected products to highlight deals in your shop.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onCreateNewClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = customColors.accent,
                        contentColor = customColors.onAccent
                    ),
                    modifier = Modifier.height(48.dp).testTag("empty_new_price_drop_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New price drop", fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("active_price_drops_list"),
        contentPadding = PaddingValues(top = 14.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(activeDropItems, key = { it.id }) { item ->
            val isInEffect = item.isSaleInEffect(currentTime)
            val pctLabel = item.formattedDiscountPercent
            val saleVal = item.salePrice ?: 0.0
            val scheduleText = PriceDropUtils.formatEndSummary(
                saleStartsAt = item.saleStartsAt,
                saleEndsAt = item.saleEndsAt,
                nowMs = currentTime,
                timeZoneId = timeZoneId
            )

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("active_drop_card_${item.id}")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(10.dp))
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
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isInEffect) StatusOpenGreen.copy(alpha = 0.15f) else StatusWarningAmber.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = if (isInEffect) "Active" else "Scheduled",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isInEffect) StatusOpenGreen else Color(0xFFB45309),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            if (item.variationsEnabled && item.variants.isNotEmpty()) {
                                val discountedVariants = item.variants.filter { v ->
                                    val sp = v.salePrice
                                    sp != null && sp > 0.0 && sp < v.price
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    discountedVariants.forEach { v ->
                                        val vPct = PriceDropUtils.formatDiscountPercent(v.price, v.salePrice!!)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.testTag("active_drop_variant_${item.id}_${v.key}")
                                        ) {
                                            Text(
                                                text = v.label,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Text(
                                                text = FormatUtils.formatCurrency(v.salePrice),
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 13.sp,
                                                color = customColors.accent
                                            )
                                            Text(
                                                text = FormatUtils.formatCurrency(v.price),
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textDecoration = TextDecoration.LineThrough
                                            )
                                            if (vPct.isNotBlank()) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = StatusClosedRed.copy(alpha = 0.14f)
                                                ) {
                                                    Text(
                                                        text = vPct,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = StatusClosedRed,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = FormatUtils.formatCurrency(saleVal),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        color = customColors.accent
                                    )
                                    Text(
                                        text = FormatUtils.formatCurrency(item.price),
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textDecoration = TextDecoration.LineThrough
                                    )
                                    if (pctLabel.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = StatusClosedRed.copy(alpha = 0.14f)
                                        ) {
                                            Text(
                                                text = pctLabel,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = StatusClosedRed,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = scheduleText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedButton(
                            onClick = { onEndDropClick(item.id) },
                            enabled = endingItemId != item.id,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, StatusClosedRed.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusClosedRed),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 44.dp)
                                .testTag("end_price_drop_btn_${item.id}")
                        ) {
                            if (endingItemId == item.id) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = StatusClosedRed
                                )
                            } else {
                                Text("End price drop", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewPriceDropTab(
    shopItems: List<MenuItem>,
    menus: List<ShopMenu>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedItemIds: List<String>,
    onToggleItemSelected: (MenuItem) -> Unit,
    onPreselectMenuGroup: (ShopMenu) -> Unit,
    onClearSelection: () -> Unit,
    selectedItems: List<MenuItem>,
    priceInputs: Map<String, String>,
    onPriceInputChange: (String, String) -> Unit,
    variantPriceInputs: Map<String, String>,
    onVariantPriceInputChange: (String, String, String) -> Unit,
    touchedItemIds: Map<String, Boolean>,
    hasAttemptedSave: Boolean,
    validations: Map<String, com.example.util.SalePriceValidation>,
    variantValidations: Map<String, com.example.util.SalePriceValidation>,
    startMode: String,
    onStartModeChange: (String) -> Unit,
    startDateStr: String,
    startTime24: String,
    onPickStartDate: () -> Unit,
    onPickStartTime: () -> Unit,
    endMode: String,
    onEndModeChange: (String) -> Unit,
    endDateStr: String,
    endTime24: String,
    onPickEndDate: () -> Unit,
    onPickEndTime: () -> Unit,
    scheduleError: String?
) {
    val customColors = LocalDelivioColors.current

    val filteredProducts = remember(shopItems, searchQuery) {
        val q = searchQuery.trim()
        val base = shopItems.sortedBy { it.name.lowercase() }
        if (q.isBlank()) base
        else base.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.category.contains(q, ignoreCase = true) ||
                it.categoryName.contains(q, ignoreCase = true) ||
                (it.subcategoryName?.contains(q, ignoreCase = true) == true)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Choose products card (search + menu group preselect + product checkboxes)
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "1. Choose products (${selectedItemIds.size} selected)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (selectedItemIds.isNotEmpty()) {
                            TextButton(
                                onClick = onClearSelection,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("price_drop_clear_selection_btn")
                            ) {
                                Text("Clear", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search products...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("price_drop_search_input")
                    )

                    if (menus.isNotEmpty()) {
                        Text(
                            text = "Preselect a menu group:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            menus.forEach { menu ->
                                val menuItemsInGroup = remember(shopItems, menu.id) {
                                    shopItems.filter { it.price > 0.0 && it.getEffectiveMenuIds().contains(menu.id) }
                                }
                                val allInGroupSelected = menuItemsInGroup.isNotEmpty() &&
                                    menuItemsInGroup.all { selectedItemIds.contains(it.id) }
                                FilterChip(
                                    selected = allInGroupSelected,
                                    onClick = { onPreselectMenuGroup(menu) },
                                    label = {
                                        Text("${menu.name} (${menuItemsInGroup.size})", fontSize = 12.sp)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (allInGroupSelected) Icons.Default.Check else Icons.Default.PlaylistAddCheck,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = customColors.accent,
                                        selectedLabelColor = customColors.onAccent
                                    ),
                                    modifier = Modifier.testTag("price_drop_menu_group_${menu.id}")
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    if (filteredProducts.isEmpty()) {
                        Text(
                            text = "No matching products found.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            filteredProducts.forEach { item ->
                                val isSelected = selectedItemIds.contains(item.id)
                                val isVarItem = item.variationsEnabled && item.variants.isNotEmpty()
                                val canDiscount = if (isVarItem) item.variants.any { it.price > 0.0 } else item.price > 0.0
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) customColors.accentTint.copy(alpha = 0.35f)
                                            else Color.Transparent
                                        )
                                        .clickable(enabled = canDiscount) { onToggleItemSelected(item) }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                        .testTag("price_drop_row_${item.id}"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = if (canDiscount) {
                                            { onToggleItemSelected(item) }
                                        } else null,
                                        enabled = canDiscount,
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = customColors.accent,
                                            checkmarkColor = customColors.onAccent
                                        ),
                                        modifier = Modifier.testTag("price_drop_select_${item.id}")
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val priceSubtitle = when {
                                            !canDiscount -> "Regular price must be above 0"
                                            isVarItem && item.hasPriceRange ->
                                                "${item.variants.size} variations • from ${FormatUtils.formatCurrency(item.price)}"
                                            isVarItem ->
                                                "${item.variants.size} variations • ${FormatUtils.formatCurrency(item.price)}"
                                            else ->
                                                "Regular: ${FormatUtils.formatCurrency(item.price)}"
                                        }
                                        Text(
                                            text = priceSubtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (canDiscount) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                                        )
                                    }
                                    if (item.saleActive && item.salePrice != null && item.salePrice < item.price) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = customColors.accent.copy(alpha = 0.16f)
                                        ) {
                                            Text(
                                                text = "On sale",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = customColors.accent,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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

        // 2. Price Drop Table for selected products
        if (selectedItems.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("price_drop_table_card")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "2. Set new prices",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Type the new sale price only. Discount percent updates automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Table column header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Product",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1.25f)
                            )
                            Text(
                                text = "New price",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1.1f)
                            )
                            Text(
                                text = "Discount",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(64.dp)
                            )
                        }

                        selectedItems.forEachIndexed { idx, item ->
                            if (item.variationsEnabled && item.variants.isNotEmpty()) {
                                val anyVariantEntered = item.variants.any { v ->
                                    variantPriceInputs["${item.id}::${v.key}"].orEmpty().isNotBlank()
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "${item.variants.size} variations",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    item.variants.forEach { variant ->
                                        val vk = "${item.id}::${variant.key}"
                                        val raw = variantPriceInputs[vk].orEmpty()
                                        val vVal = variantValidations[vk]
                                        val showVErr = raw.isNotBlank() && vVal != null && !vVal.isValid
                                        val vPct = if (vVal?.isValid == true && vVal.parsedPrice != null) {
                                            PriceDropUtils.formatDiscountPercent(variant.price, vVal.parsedPrice)
                                        } else {
                                            "--"
                                        }
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Column(modifier = Modifier.weight(1.25f)) {
                                                    Text(
                                                        text = variant.label,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = FormatUtils.formatCurrency(variant.price),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                OutlinedTextField(
                                                    value = raw,
                                                    onValueChange = { onVariantPriceInputChange(item.id, variant.key, it) },
                                                    label = { Text("Sale price", fontSize = 11.sp) },
                                                    prefix = { Text(CurrencyConstants.SYMBOL, fontWeight = FontWeight.Bold) },
                                                    placeholder = { Text("Optional") },
                                                    singleLine = true,
                                                    isError = showVErr,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier
                                                        .weight(1.1f)
                                                        .testTag("price_drop_variant_input_${item.id}_${variant.key}")
                                                )
                                                Box(
                                                    modifier = Modifier.width(64.dp),
                                                    contentAlignment = Alignment.CenterEnd
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = if (vPct != "--") StatusClosedRed.copy(alpha = 0.14f)
                                                        else MaterialTheme.colorScheme.surfaceVariant
                                                    ) {
                                                        Text(
                                                            text = vPct,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = if (vPct != "--") StatusClosedRed
                                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier
                                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                                                .testTag("price_drop_variant_pct_${item.id}_${variant.key}")
                                                        )
                                                    }
                                                }
                                            }
                                            if (showVErr && !vVal?.errorMessage.isNullOrBlank()) {
                                                Text(
                                                    text = vVal!!.errorMessage!!,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.testTag("price_drop_variant_error_${item.id}_${variant.key}")
                                                )
                                            }
                                        }
                                    }

                                    if (hasAttemptedSave && !anyVariantEntered) {
                                        Text(
                                            text = "Enter a sale price for at least one variation.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.testTag("price_drop_error_${item.id}")
                                        )
                                    }

                                    if (idx < selectedItems.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(top = 6.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        )
                                    }
                                }
                            } else {
                                val inputText = priceInputs[item.id].orEmpty()
                                val validation = validations[item.id]
                                val showError = (touchedItemIds[item.id] == true || hasAttemptedSave) &&
                                    (validation != null && !validation.isValid)
                                val pctLabel = if (validation?.isValid == true && validation.parsedPrice != null) {
                                    PriceDropUtils.formatDiscountPercent(item.price, validation.parsedPrice)
                                } else {
                                    "--"
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1.25f)) {
                                            Text(
                                                text = item.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = FormatUtils.formatCurrency(item.price),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        OutlinedTextField(
                                            value = inputText,
                                            onValueChange = { onPriceInputChange(item.id, it) },
                                            label = { Text("New price", fontSize = 11.sp) },
                                            prefix = { Text(CurrencyConstants.SYMBOL, fontWeight = FontWeight.Bold) },
                                            placeholder = { Text("0.00") },
                                            singleLine = true,
                                            isError = showError,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(1.1f)
                                                .testTag("price_drop_input_${item.id}")
                                        )

                                        Box(
                                            modifier = Modifier.width(64.dp),
                                            contentAlignment = Alignment.CenterEnd
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (pctLabel != "--") StatusClosedRed.copy(alpha = 0.14f)
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            ) {
                                                Text(
                                                    text = pctLabel,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (pctLabel != "--") StatusClosedRed
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier
                                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                                        .testTag("price_drop_pct_${item.id}")
                                                )
                                            }
                                        }
                                    }

                                    if (showError && !validation?.errorMessage.isNullOrBlank()) {
                                        Text(
                                            text = validation!!.errorMessage!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.testTag("price_drop_error_${item.id}")
                                        )
                                    }

                                    if (idx < selectedItems.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(top = 6.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Optional Schedule Card
            item {
                PriceDropScheduleCard(
                    title = "3. Schedule (optional)",
                    startMode = startMode,
                    onStartModeChange = onStartModeChange,
                    startDateStr = startDateStr,
                    startTime24 = startTime24,
                    onPickStartDate = onPickStartDate,
                    onPickStartTime = onPickStartTime,
                    endMode = endMode,
                    onEndModeChange = onEndModeChange,
                    endDateStr = endDateStr,
                    endTime24 = endTime24,
                    onPickEndDate = onPickEndDate,
                    onPickEndTime = onPickEndTime,
                    scheduleError = scheduleError
                )
            }
        }
    }
}

@Composable
fun PriceDropScheduleCard(
    title: String,
    startMode: String,
    onStartModeChange: (String) -> Unit,
    startDateStr: String,
    startTime24: String,
    onPickStartDate: () -> Unit,
    onPickStartTime: () -> Unit,
    endMode: String,
    onEndModeChange: (String) -> Unit,
    endDateStr: String,
    endTime24: String,
    onPickEndDate: () -> Unit,
    onPickEndTime: () -> Unit,
    scheduleError: String?
) {
    val customColors = LocalDelivioColors.current

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Start option
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Start",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = startMode == "now",
                        onClick = { onStartModeChange("now") },
                        label = { Text("Start now") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = customColors.accent,
                            selectedLabelColor = customColors.onAccent
                        ),
                        modifier = Modifier.testTag("price_drop_start_now")
                    )
                    FilterChip(
                        selected = startMode == "scheduled",
                        onClick = { onStartModeChange("scheduled") },
                        label = { Text("At a date and time") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = customColors.accent,
                            selectedLabelColor = customColors.onAccent
                        ),
                        modifier = Modifier.testTag("price_drop_start_scheduled")
                    )
                }

                if (startMode == "scheduled") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onPickStartDate,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(startDateStr, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onPickStartTime,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(MenuSchedule.formatTime12h(startTime24), fontSize = 12.sp)
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // End option
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "End",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = endMode == "until_ended",
                        onClick = { onEndModeChange("until_ended") },
                        label = { Text("Until I end it") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = customColors.accent,
                            selectedLabelColor = customColors.onAccent
                        ),
                        modifier = Modifier.testTag("price_drop_end_until_ended")
                    )
                    FilterChip(
                        selected = endMode == "scheduled",
                        onClick = { onEndModeChange("scheduled") },
                        label = { Text("At a date and time") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = customColors.accent,
                            selectedLabelColor = customColors.onAccent
                        ),
                        modifier = Modifier.testTag("price_drop_end_scheduled")
                    )
                }

                if (endMode == "scheduled") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onPickEndDate,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(endDateStr, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onPickEndTime,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(MenuSchedule.formatTime12h(endTime24), fontSize = 12.sp)
                        }
                    }
                }
            }

            if (!scheduleError.isNullOrBlank()) {
                Text(
                    text = scheduleError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

fun formatEditablePrice(value: Double): String {
    return if (value % 1.0 == 0.0) value.toInt().toString()
    else "%.2f".format(Locale.US, value)
}

fun combineDateTimeToMillis(dateYmd: String, time24: String, timeZoneId: String): Long {
    return try {
        val partsDate = dateYmd.split("-")
        val y = partsDate.getOrNull(0)?.toIntOrNull() ?: return System.currentTimeMillis()
        val m = (partsDate.getOrNull(1)?.toIntOrNull() ?: 1) - 1
        val d = partsDate.getOrNull(2)?.toIntOrNull() ?: 1
        val partsTime = time24.split(":")
        val h = partsTime.getOrNull(0)?.toIntOrNull() ?: 0
        val min = partsTime.getOrNull(1)?.toIntOrNull() ?: 0
        val tz = TimeZone.getTimeZone(timeZoneId.ifBlank { "Asia/Manila" })
        Calendar.getInstance(tz).apply {
            set(Calendar.YEAR, y)
            set(Calendar.MONTH, m)
            set(Calendar.DAY_OF_MONTH, d)
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, min)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    } catch (_: Exception) {
        System.currentTimeMillis()
    }
}

fun extractDateYmd(timestampMs: Long, timeZoneId: String): String {
    val tz = TimeZone.getTimeZone(timeZoneId.ifBlank { "Asia/Manila" })
    val cal = Calendar.getInstance(tz).apply { timeInMillis = timestampMs }
    return "%04d-%02d-%02d".format(
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH)
    )
}

fun extractTime24(timestampMs: Long, timeZoneId: String): String {
    val tz = TimeZone.getTimeZone(timeZoneId.ifBlank { "Asia/Manila" })
    val cal = Calendar.getInstance(tz).apply { timeInMillis = timestampMs }
    return "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleDatePickerDialog(
    initialDateYmd: String,
    onDismiss: () -> Unit,
    onDateSelected: (String) -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = try {
            if (initialDateYmd.isNotBlank()) {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                sdf.parse(initialDateYmd)?.time
            } else null
        } catch (_: Exception) { null }
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { millis ->
                    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                        timeInMillis = millis
                    }
                    val formatted = "%04d-%02d-%02d".format(
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH) + 1,
                        cal.get(Calendar.DAY_OF_MONTH)
                    )
                    onDateSelected(formatted)
                } ?: onDismiss()
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
fun PriceDropTimePickerDialog(
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
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
                            .padding(vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
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
