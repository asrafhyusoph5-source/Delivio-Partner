package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ProductVariant
import com.example.data.model.ProductVariation
import com.example.data.model.VariationOption
import com.example.ui.theme.LocalDelivioColors
import com.example.util.CurrencyConstants
import com.example.util.VariantCombinationDraft
import com.example.util.VariationUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetStockAndPriceScreen(
    variations: List<ProductVariation>,
    existingVariants: List<ProductVariant>,
    onBack: (List<ProductVariant>) -> Unit,
    onSave: (List<ProductVariation>, List<ProductVariant>) -> Unit,
    modifier: Modifier = Modifier
) {
    val customColors = LocalDelivioColors.current

    val drafts = remember(variations, existingVariants) {
        VariationUtils.buildCombinationDrafts(variations, existingVariants)
    }

    val priceInputs = remember(drafts) {
        mutableStateMapOf<String, String>().apply {
            drafts.forEach { d ->
                val prev = d.existingVariant
                this[d.key] = if (prev != null && prev.price > 0.0) {
                    if (prev.price % 1.0 == 0.0) prev.price.toInt().toString() else "%.2f".format(java.util.Locale.US, prev.price)
                } else ""
            }
        }
    }

    val stockInputs = remember(drafts) {
        mutableStateMapOf<String, String>().apply {
            drafts.forEach { d ->
                val prev = d.existingVariant
                this[d.key] = if (prev != null) prev.stock.toString() else ""
            }
        }
    }

    val touchedRows = remember { mutableStateMapOf<String, Boolean>() }
    val checkedKeys = remember { mutableStateMapOf<String, Boolean>() }
    var hasTriedSave by remember { mutableStateOf(false) }
    var showBatchEditSheet by remember { mutableStateOf(false) }

    fun parseCurrentVariantsSnapshot(): List<ProductVariant> {
        return drafts.mapNotNull { d ->
            val p = priceInputs[d.key]?.trim()?.toDoubleOrNull()
            val s = stockInputs[d.key]?.trim()?.toIntOrNull()
            if (p != null && p > 0.0 && s != null && s >= 0) {
                val prevSale = d.existingVariant?.salePrice
                val validSale = if (prevSale != null && prevSale > 0.0 && prevSale < p) prevSale else null
                ProductVariant(
                    key = d.key,
                    optionIds = d.optionIds,
                    label = d.label,
                    price = VariationUtils.roundCents(p),
                    stock = s,
                    salePrice = validSale
                )
            } else {
                d.existingVariant?.copy(key = d.key, optionIds = d.optionIds, label = d.label)
            }
        }
    }

    BackHandler { onBack(parseCurrentVariantsSnapshot()) }

    fun rowPriceError(key: String): String? {
        val raw = priceInputs[key].orEmpty().trim()
        if (raw.isEmpty()) return "Required"
        val parsed = raw.toDoubleOrNull()
        if (parsed == null || parsed <= 0.0) return "Must be > 0"
        return null
    }

    fun rowStockError(key: String): String? {
        val raw = stockInputs[key].orEmpty().trim()
        if (raw.isEmpty()) return "Required"
        val parsed = raw.toIntOrNull()
        if (parsed == null || parsed < 0) return "Integer >= 0"
        return null
    }

    val allValid = drafts.isNotEmpty() && drafts.all { d ->
        rowPriceError(d.key) == null && rowStockError(d.key) == null
    }

    val selectedKeysCount = drafts.count { checkedKeys[it.key] == true }
    val allChecked = drafts.isNotEmpty() && selectedKeysCount == drafts.size

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("set_stock_price_screen"),
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onBack(parseCurrentVariantsSnapshot()) },
                        modifier = Modifier.testTag("set_stock_price_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Set Stock And Price",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
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
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hasTriedSave && !allValid) {
                        Text(
                            text = "Fill a valid price (> 0) and whole-number stock (>= 0) for all combinations.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("set_stock_price_error_banner")
                        )
                    }
                    Button(
                        onClick = {
                            hasTriedSave = true
                            drafts.forEach { touchedRows[it.key] = true }
                            if (allValid) {
                                val builtVariants = drafts.map { d ->
                                    val priceVal = VariationUtils.roundCents(priceInputs[d.key]!!.trim().toDouble())
                                    val stockVal = stockInputs[d.key]!!.trim().toInt()
                                    val prevSale = d.existingVariant?.salePrice
                                    val preservedSale = if (prevSale != null && prevSale > 0.0 && prevSale < priceVal) prevSale else null
                                    ProductVariant(
                                        key = d.key,
                                        optionIds = d.optionIds,
                                        label = d.label,
                                        price = priceVal,
                                        stock = stockVal,
                                        salePrice = preservedSale
                                    )
                                }
                                onSave(VariationUtils.normalizeVariationsForSave(variations), builtVariants)
                            }
                        },
                        enabled = allValid,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = customColors.accent,
                            contentColor = customColors.onAccent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("set_stock_price_save_btn")
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top bar: "Set same price, stock for all variations" + "Batch Edit" button
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Set same price, stock for all variations",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = { showBatchEditSheet = true },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 40.dp)
                                .testTag("batch_edit_btn")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Batch Edit", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Column Header with Select All Checkbox
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = allChecked,
                        onCheckedChange = { checked ->
                            drafts.forEach { checkedKeys[it.key] = checked }
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = customColors.accent,
                            checkmarkColor = customColors.onAccent
                        ),
                        modifier = Modifier.testTag("batch_select_all_checkbox")
                    )
                    Text(
                        text = "Variations (${drafts.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1.2f)
                    )
                    Text(
                        text = "Price (${CurrencyConstants.SYMBOL})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Stock",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(0.85f)
                    )
                }
            }

            // Rows: grouped under Variation 1 options when >= 2 variations; flat otherwise
            if (variations.size >= 2) {
                val firstGroup = variations.first()
                firstGroup.options.forEach { firstOpt ->
                    val groupDrafts = drafts.filter { it.optionIds.firstOrNull() == firstOpt.id }
                    if (groupDrafts.isNotEmpty()) {
                        item(key = "header_${firstOpt.id}") {
                            VariantGroupHeader(
                                variationName = firstGroup.name,
                                option = firstOpt,
                                showImage = firstGroup.imageEnabled
                            )
                        }
                        items(groupDrafts, key = { it.key }) { draft ->
                            val showErr = hasTriedSave || touchedRows[draft.key] == true
                            VariantPriceStockRow(
                                draft = draft,
                                isChecked = checkedKeys[draft.key] == true,
                                onCheckedChange = { checkedKeys[draft.key] = it },
                                priceValue = priceInputs[draft.key].orEmpty(),
                                onPriceChange = { next ->
                                    val filtered = next.filter { ch -> ch.isDigit() || ch == '.' }
                                    if (filtered.count { it == '.' } <= 1) {
                                        priceInputs[draft.key] = filtered
                                        touchedRows[draft.key] = true
                                    }
                                },
                                stockValue = stockInputs[draft.key].orEmpty(),
                                onStockChange = { next ->
                                    val filtered = next.filter { ch -> ch.isDigit() }
                                    stockInputs[draft.key] = filtered
                                    touchedRows[draft.key] = true
                                },
                                priceError = if (showErr) rowPriceError(draft.key) else null,
                                stockError = if (showErr) rowStockError(draft.key) else null
                            )
                        }
                    }
                }
            } else {
                items(drafts, key = { it.key }) { draft ->
                    val showErr = hasTriedSave || touchedRows[draft.key] == true
                    VariantPriceStockRow(
                        draft = draft,
                        isChecked = checkedKeys[draft.key] == true,
                        onCheckedChange = { checkedKeys[draft.key] = it },
                        priceValue = priceInputs[draft.key].orEmpty(),
                        onPriceChange = { next ->
                            val filtered = next.filter { ch -> ch.isDigit() || ch == '.' }
                            if (filtered.count { it == '.' } <= 1) {
                                priceInputs[draft.key] = filtered
                                touchedRows[draft.key] = true
                            }
                        },
                        stockValue = stockInputs[draft.key].orEmpty(),
                        onStockChange = { next ->
                            val filtered = next.filter { ch -> ch.isDigit() }
                            stockInputs[draft.key] = filtered
                            touchedRows[draft.key] = true
                        },
                        priceError = if (showErr) rowPriceError(draft.key) else null,
                        stockError = if (showErr) rowStockError(draft.key) else null
                    )
                }
            }
        }
    }

    if (showBatchEditSheet) {
        var batchPriceText by remember { mutableStateOf("") }
        var batchStockText by remember { mutableStateOf("") }
        var batchError by remember { mutableStateOf<String?>(null) }

        fun applyBatch(applyOnlyChecked: Boolean) {
            val trimmedPrice = batchPriceText.trim()
            val trimmedStock = batchStockText.trim()
            if (trimmedPrice.isEmpty() && trimmedStock.isEmpty()) {
                batchError = "Enter a price or stock amount to apply"
                return
            }
            if (trimmedPrice.isNotEmpty()) {
                val p = trimmedPrice.toDoubleOrNull()
                if (p == null || p <= 0.0) {
                    batchError = "Price must be greater than 0"
                    return
                }
            }
            if (trimmedStock.isNotEmpty()) {
                val s = trimmedStock.toIntOrNull()
                if (s == null || s < 0) {
                    batchError = "Stock must be a whole number 0 or above"
                    return
                }
            }
            drafts.forEach { d ->
                if (!applyOnlyChecked || checkedKeys[d.key] == true) {
                    if (trimmedPrice.isNotEmpty()) priceInputs[d.key] = trimmedPrice
                    if (trimmedStock.isNotEmpty()) stockInputs[d.key] = trimmedStock
                    touchedRows[d.key] = true
                }
            }
            showBatchEditSheet = false
        }

        ModalBottomSheet(
            onDismissRequest = { showBatchEditSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Batch Edit Price & Stock",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Apply the same price and/or stock to all combinations or only the checked rows.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = batchPriceText,
                        onValueChange = { next ->
                            val filtered = next.filter { ch -> ch.isDigit() || ch == '.' }
                            if (filtered.count { it == '.' } <= 1) {
                                batchPriceText = filtered
                                batchError = null
                            }
                        },
                        label = { Text("Price (${CurrencyConstants.SYMBOL})") },
                        placeholder = { Text("0.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("batch_edit_price_input")
                    )

                    OutlinedTextField(
                        value = batchStockText,
                        onValueChange = { next ->
                            batchStockText = next.filter { ch -> ch.isDigit() }
                            batchError = null
                        },
                        label = { Text("Stock") },
                        placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("batch_edit_stock_input")
                    )
                }

                if (batchError != null) {
                    Text(
                        text = batchError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { applyBatch(applyOnlyChecked = true) },
                        enabled = selectedKeysCount > 0,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("batch_apply_checked_btn")
                    ) {
                        Text("Apply to checked ($selectedKeysCount)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { applyBatch(applyOnlyChecked = false) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = customColors.accent,
                            contentColor = customColors.onAccent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("batch_apply_all_btn")
                    ) {
                        Text("Apply to all", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun VariantGroupHeader(
    variationName: String,
    option: VariationOption,
    showImage: Boolean
) {
    val customColors = LocalDelivioColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (showImage) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (option.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = option.imageUrl.trim(),
                        contentDescription = option.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
        Surface(
            color = customColors.accent.copy(alpha = 0.14f),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                text = "${variationName.trim()}: ${option.name}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = customColors.accent,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun VariantPriceStockRow(
    draft: VariantCombinationDraft,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    priceValue: String,
    onPriceChange: (String) -> Unit,
    stockValue: String,
    onStockChange: (String) -> Unit,
    priceError: String?,
    stockError: String?
) {
    val customColors = LocalDelivioColors.current
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("variant_row_${draft.key}")
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = onCheckedChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = customColors.accent,
                        checkmarkColor = customColors.onAccent
                    ),
                    modifier = Modifier.testTag("variant_checkbox_${draft.key}")
                )

                Text(
                    text = draft.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1.1f)
                )

                OutlinedTextField(
                    value = priceValue,
                    onValueChange = onPriceChange,
                    placeholder = { Text("0.00", fontSize = 12.sp) },
                    prefix = { Text(CurrencyConstants.SYMBOL, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    isError = priceError != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("variant_price_input_${draft.key}")
                )

                OutlinedTextField(
                    value = stockValue,
                    onValueChange = onStockChange,
                    placeholder = { Text("0", fontSize = 12.sp) },
                    isError = stockError != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(0.85f)
                        .testTag("variant_stock_input_${draft.key}")
                )
            }

            if (priceError != null || stockError != null) {
                val msg = listOfNotNull(
                    priceError?.let { "Price: $it" },
                    stockError?.let { "Stock: $it" }
                ).joinToString(" • ")
                Text(
                    text = msg,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 48.dp, top = 4.dp)
                )
            }
        }
    }
}
