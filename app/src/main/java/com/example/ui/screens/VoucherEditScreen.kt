package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MenuItem
import com.example.data.model.MerchantVoucher
import com.example.ui.theme.DelivioYellow
import com.example.util.FormatUtils
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoucherEditScreen(
    voucher: MerchantVoucher,
    isNew: Boolean,
    availableProducts: List<MenuItem>,
    onCheckCodeExists: (String, (Boolean, String?) -> Unit) -> Unit,
    onSave: (MerchantVoucher, Boolean, (Boolean, String?) -> Unit) -> Unit,
    onDelete: (String, (Boolean, String?) -> Unit) -> Unit,
    onToggleActive: (String, Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    // Form fields
    var code by remember { mutableStateOf(voucher.code) }
    var title by remember { mutableStateOf(voucher.title) }
    var discountType by remember { mutableStateOf(if (voucher.type.equals("fixed", ignoreCase = true)) "fixed" else "percent") }
    var discountValueText by remember { mutableStateOf(if (voucher.value > 0) voucher.value.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } else "10") }
    var maxDiscountText by remember { mutableStateOf(voucher.maxDiscount?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    var minSubtotalText by remember { mutableStateOf(if (voucher.minSubtotal > 0.0) voucher.minSubtotal.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } else "") }
    var appliesTo by remember { mutableStateOf(if (voucher.appliesTo.equals("items", ignoreCase = true)) "items" else "store") }
    var selectedItemIds by remember { mutableStateOf(voucher.itemIds.toSet()) }
    var fulfillment by remember { mutableStateOf(voucher.fulfillment) }
    var startsAt by remember { mutableStateOf(voucher.startsAt) }
    var expiresAt by remember { mutableStateOf(voucher.expiresAt) }
    var usageLimitText by remember { mutableStateOf(voucher.usageLimit?.toString() ?: "") }
    var perUserLimitText by remember { mutableStateOf(voucher.perUserLimit.toString()) }
    var visibility by remember { mutableStateOf(if (voucher.visibility.equals("private", ignoreCase = true)) "private" else "public") }
    var active by remember { mutableStateOf(voucher.active) }

    // UI state
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showProductPicker by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Code generator helper
    fun autoGenerateCode() {
        val prefixes = listOf("SAVE", "TREAT", "DELIVIO", "YUMMY", "FEAST", "DISCOUNT", "SPECIAL")
        val p = prefixes.random()
        val num = (10..99).random()
        code = "$p$num"
        errorMessage = null
    }

    // Date picker helpers
    fun showDatePicker(initialMs: Long?, onDateSelected: (Long?) -> Unit) {
        val cal = Calendar.getInstance()
        if (initialMs != null && initialMs > 0) {
            cal.timeInMillis = initialMs
        }
        val dialog = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selectedCal = Calendar.getInstance()
                selectedCal.set(Calendar.YEAR, year)
                selectedCal.set(Calendar.MONTH, month)
                selectedCal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                selectedCal.set(Calendar.HOUR_OF_DAY, 23)
                selectedCal.set(Calendar.MINUTE, 59)
                selectedCal.set(Calendar.SECOND, 59)
                onDateSelected(selectedCal.timeInMillis)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        dialog.show()
    }

    // Dynamic numeric parsing for live preview
    val parsedValue = discountValueText.toDoubleOrNull() ?: 0.0
    val parsedMaxDiscount = maxDiscountText.toDoubleOrNull()
    val parsedMinSubtotal = minSubtotalText.toDoubleOrNull() ?: 0.0

    // Construct live voucher model for preview
    val previewVoucher = remember(
        code, title, discountType, parsedValue, parsedMaxDiscount, parsedMinSubtotal,
        appliesTo, selectedItemIds, fulfillment, expiresAt, visibility, active
    ) {
        voucher.copy(
            code = code.trim().uppercase(),
            title = title.trim(),
            type = discountType,
            value = parsedValue,
            maxDiscount = parsedMaxDiscount,
            minSubtotal = parsedMinSubtotal,
            appliesTo = appliesTo,
            itemIds = selectedItemIds.toList(),
            fulfillment = fulfillment,
            expiresAt = expiresAt,
            visibility = visibility,
            active = active
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isNew) "Create Voucher" else "Edit Voucher",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("voucher_edit_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Live Customer Preview Section
            Text(
                text = "CUSTOMER PREVIEW",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )

            CustomerVoucherLivePreviewCard(voucher = previewVoucher)

            // 2. Code Section
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Voucher Code", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)

                    if (isNew) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = code,
                                onValueChange = { input ->
                                    val filtered = input.uppercase().filter { it.isLetterOrDigit() }.take(16)
                                    code = filtered
                                    errorMessage = null
                                },
                                label = { Text("Code (4-16 characters)") },
                                placeholder = { Text("e.g. DELIVIO50") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Characters,
                                    keyboardType = KeyboardType.Ascii
                                ),
                                textStyle = LocalTextStyle.current.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("voucher_code_input")
                            )

                            Button(
                                onClick = { autoGenerateCode() },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("auto_generate_code_button")
                            ) {
                                Text("Auto", fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            text = "Must be unique. Customers type this code to redeem.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // Read-only code when editing
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = code,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Cannot change code",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Title Field
                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            title = it
                            errorMessage = null
                        },
                        label = { Text("Voucher Title shown to customers") },
                        placeholder = { Text("e.g. 10% Off Weekend Feast") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("voucher_title_input")
                    )
                }
            }

            // 3. Discount Setup Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Discount Type & Value", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)

                    // Discount Type Selector (Percent vs Fixed)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (discountType == "percent") DelivioYellow else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { discountType = "percent" }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            ) {
                                Text(
                                    text = "Percent Off (%)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (discountType == "percent") Color(0xFF1A1A1A) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (discountType == "fixed") DelivioYellow else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { discountType = "fixed" }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            ) {
                                Text(
                                    text = "Amount Off (₱)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (discountType == "fixed") Color(0xFF1A1A1A) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Value Input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = discountValueText,
                            onValueChange = { discountValueText = it },
                            label = { Text(if (discountType == "percent") "Discount Percentage" else "Discount Amount (₱)") },
                            prefix = { if (discountType == "fixed") Text("₱ ") },
                            suffix = { if (discountType == "percent") Text("%") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("voucher_value_input")
                        )

                        if (discountType == "percent") {
                            OutlinedTextField(
                                value = maxDiscountText,
                                onValueChange = { maxDiscountText = it },
                                label = { Text("Max Cap (Optional)") },
                                prefix = { Text("₱ ") },
                                placeholder = { Text("No limit") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("voucher_max_cap_input")
                            )
                        }
                    }

                    // Minimum order subtotal
                    OutlinedTextField(
                        value = minSubtotalText,
                        onValueChange = { minSubtotalText = it },
                        label = { Text("Minimum Order Subtotal (₱)") },
                        prefix = { Text("₱ ") },
                        placeholder = { Text("0 (No minimum)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("voucher_min_order_input")
                    )
                }
            }

            // 4. Applies To & Fulfillment Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Applies To & Fulfillment", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)

                    // Applies To Segmented
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (appliesTo == "store") DelivioYellow else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { appliesTo = "store" }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                                Text(
                                    text = "Whole Store",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (appliesTo == "store") Color(0xFF1A1A1A) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (appliesTo == "items") DelivioYellow else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { appliesTo = "items" }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                                Text(
                                    text = "Selected Products",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (appliesTo == "items") Color(0xFF1A1A1A) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (appliesTo == "items") {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DelivioYellow),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showProductPicker = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Fastfood,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = if (selectedItemIds.isEmpty()) "Tap to select products" else "${selectedItemIds.size} products selected",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Text("Change", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    // Fulfillment
                    Text("Fulfillment Type", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("both" to "All", "delivery" to "Delivery", "pickup" to "Pick Up").forEach { (fKey, fLabel) ->
                            val isSel = fulfillment.equals(fKey, ignoreCase = true)
                            FilterChip(
                                selected = isSel,
                                onClick = { fulfillment = fKey },
                                label = { Text(fLabel, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = DelivioYellow,
                                    selectedLabelColor = Color(0xFF1A1A1A)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 5. Validity, Schedule & Limits Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Validity & Usage Limits", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)

                    // Expiry Date Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Expiry Date", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = if (expiresAt != null && expiresAt!! > 0) FormatUtils.formatDate(expiresAt!!) else "No expiry (Always active)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (expiresAt != null) {
                                TextButton(onClick = { expiresAt = null }) {
                                    Text("Clear")
                                }
                            }
                            FilledTonalButton(
                                onClick = {
                                    showDatePicker(expiresAt) { selectedMs ->
                                        expiresAt = selectedMs
                                    }
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Set Date")
                            }
                        }
                    }

                    // Total uses limit & per-customer limit
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = usageLimitText,
                            onValueChange = { usageLimitText = it },
                            label = { Text("Total Uses Limit") },
                            placeholder = { Text("Unlimited") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("voucher_usage_limit_input")
                        )

                        OutlinedTextField(
                            value = perUserLimitText,
                            onValueChange = { perUserLimitText = it },
                            label = { Text("Uses Per Customer") },
                            placeholder = { Text("1") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("voucher_per_user_limit_input")
                        )
                    }
                }
            }

            // 6. Visibility & Status Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Visibility Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Show on shop page",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (visibility == "public") "ON: Customers see and claim this voucher on your shop page." else "OFF: Hidden from shop page. Customers must manually type the code.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = visibility == "public",
                            onCheckedChange = { isChecked ->
                                visibility = if (isChecked) "public" else "private"
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF1A1A1A),
                                checkedTrackColor = DelivioYellow
                            ),
                            modifier = Modifier.testTag("voucher_visibility_switch")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Active Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Voucher Active",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "When disabled, customers cannot redeem this voucher.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = active,
                            onCheckedChange = { active = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF1A1A1A),
                                checkedTrackColor = DelivioYellow
                            ),
                            modifier = Modifier.testTag("voucher_active_switch")
                        )
                    }
                }
            }

            // 7. Merchant Funding Notice Banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFFF8E1),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD54F)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFFF57F17),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "You pay for this discount. It is deducted from your earnings for orders that use it.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF5D4037)
                    )
                }
            }

            // Stats section if existing voucher
            if (!isNew && (voucher.usedCount > 0 || voucher.redemptionsCount > 0)) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Voucher Performance", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Times Redeemed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${voucher.usedCount} times", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Discount Subsidized", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₱${FormatUtils.formatCurrency(voucher.totalDiscountGiven)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Unique Orders", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${voucher.ordersCount} orders", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Error Message
            errorMessage?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // 8. Action Buttons: Save & Delete/Deactivate
            Button(
                onClick = {
                    val cleanCode = code.trim().uppercase()
                    if (cleanCode.length < 4 || cleanCode.length > 16) {
                        errorMessage = "Voucher code must be between 4 and 16 letters or numbers."
                        return@Button
                    }
                    if (title.trim().isBlank()) {
                        errorMessage = "Please enter a voucher title for customers."
                        return@Button
                    }
                    val valNum = discountValueText.toDoubleOrNull()
                    if (valNum == null || valNum <= 0.0) {
                        errorMessage = "Please enter a valid positive discount value."
                        return@Button
                    }
                    if (discountType == "percent" && valNum > 100.0) {
                        errorMessage = "Percentage discount cannot exceed 100%."
                        return@Button
                    }
                    if (appliesTo == "items" && selectedItemIds.isEmpty()) {
                        errorMessage = "Please select at least one product for this voucher."
                        return@Button
                    }

                    isSubmitting = true
                    errorMessage = null

                    val updatedVoucher = voucher.copy(
                        code = cleanCode,
                        source = "merchant",
                        fundedBy = "merchant",
                        title = title.trim(),
                        type = discountType,
                        value = valNum,
                        maxDiscount = if (discountType == "percent") maxDiscountText.toDoubleOrNull() else null,
                        minSubtotal = minSubtotalText.toDoubleOrNull() ?: 0.0,
                        appliesTo = appliesTo,
                        itemIds = if (appliesTo == "items") selectedItemIds.toList() else emptyList(),
                        fulfillment = fulfillment,
                        startsAt = startsAt,
                        expiresAt = expiresAt,
                        usageLimit = usageLimitText.toIntOrNull(),
                        perUserLimit = perUserLimitText.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                        active = active,
                        visibility = visibility
                    )

                    onSave(updatedVoucher, isNew) { success, err ->
                        isSubmitting = false
                        if (!success) {
                            errorMessage = err ?: "Failed to save voucher"
                        }
                    }
                },
                enabled = !isSubmitting,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DelivioYellow,
                    contentColor = Color(0xFF1A1A1A)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_voucher_button")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFF1A1A1A),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (isNew) "Create Voucher" else "Save Changes",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            if (!isNew) {
                if (voucher.usedCount == 0) {
                    OutlinedButton(
                        onClick = { showDeleteConfirmDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("delete_voucher_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete Voucher", fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            active = false
                            onToggleActive(voucher.code, false)
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Deactivate Voucher", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "This voucher has been redeemed ${voucher.usedCount} time(s) and cannot be deleted to maintain order history. Deactivate it to prevent future use.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Product Picker Dialog
    if (showProductPicker) {
        ProductPickerSelectionDialog(
            products = availableProducts,
            initiallySelectedIds = selectedItemIds,
            onDismiss = { showProductPicker = false },
            onConfirm = { newSelected ->
                selectedItemIds = newSelected
                showProductPicker = false
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Voucher?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete voucher ${voucher.code}? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        isSubmitting = true
                        onDelete(voucher.code) { success, err ->
                            isSubmitting = false
                            if (!success) {
                                errorMessage = err ?: "Failed to delete voucher"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CustomerVoucherLivePreviewCard(
    voucher: MerchantVoucher,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = DelivioYellow.copy(alpha = 0.18f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, DelivioYellow),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left discount badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DelivioYellow,
                modifier = Modifier.size(68.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (voucher.type == "percent") "${voucher.value.toInt()}%" else "₱${voucher.value.toInt()}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = Color(0xFF1A1A1A)
                        )
                        Text(
                            text = "OFF",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            color = Color(0xFF1A1A1A)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Right details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = voucher.title.ifBlank { "Voucher Title" },
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )

                val minStr = if (voucher.minSubtotal > 0.0) {
                    "Min. spend ₱${FormatUtils.formatCurrency(voucher.minSubtotal)}"
                } else {
                    "No min. spend"
                }

                val maxCapStr = if (voucher.type == "percent" && voucher.maxDiscount != null && voucher.maxDiscount!! > 0) {
                    " • Capped at ₱${FormatUtils.formatCurrency(voucher.maxDiscount!!)}"
                } else ""

                Text(
                    text = "$minStr$maxCapStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = voucher.expiryDisplay,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1A1A1A)
                    ) {
                        Text(
                            text = "USE: ${voucher.code.ifBlank { "CODE" }}",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProductPickerSelectionDialog(
    products: List<MenuItem>,
    initiallySelectedIds: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val tempSelected = remember { mutableStateMapOf<String, Boolean>().apply {
        initiallySelectedIds.forEach { put(it, true) }
    } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Products for Voucher", fontWeight = FontWeight.Bold) },
        text = {
            if (products.isEmpty()) {
                Text("No products found in your menu.")
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = {
                            products.forEach { tempSelected[it.id] = true }
                        }) {
                            Text("Select All")
                        }
                        TextButton(onClick = {
                            tempSelected.clear()
                        }) {
                            Text("Clear All")
                        }
                    }

                    products.forEach { item ->
                        val isChecked = tempSelected[item.id] == true
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isChecked) DelivioYellow.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isChecked) tempSelected.remove(item.id) else tempSelected[item.id] = true
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) tempSelected[item.id] = true else tempSelected.remove(item.id)
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF1A1A1A))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("₱${FormatUtils.formatCurrency(item.price)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(tempSelected.keys) },
                colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = Color(0xFF1A1A1A))
            ) {
                Text("Confirm (${tempSelected.size})", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
