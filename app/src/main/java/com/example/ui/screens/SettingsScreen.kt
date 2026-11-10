package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BusinessType
import com.example.data.model.ChangeRequest
import com.example.data.model.MerchantSettings
import com.example.data.model.StaffMember
import com.example.ui.theme.DelivioYellow
import com.example.ui.theme.LocalDelivioColors
import com.example.util.SoundVibrationHelper

/**
 * Redesigned Settings screen with grouped cards and small grey section titles:
 * - STORE: Business hours, Business type (locked chip + request change / pending chip),
 *          Store map location (pickup point), Staff accounts & roles
 * - ORDERS: Default prep time chips (10m, 15m, 20m, 30m; selected is yellow),
 *           New order sound switch with "Test" link, Haptic vibration alert switch
 * - PRINTER: Bluetooth receipt printer (scan, pair, test print),
 *            Receipt paper width chips (58 mm and 80 mm)
 * - APPEARANCE: Dark Mode toggle
 *
 * All switches use the same style: yellow track with a dark thumb when on, grey track when off.
 * Selected chips are always yellow with dark text.
 * Each row is at least 56 dp tall, with an icon in a soft yellow circle, a title, a one-line subtitle,
 * and the control on the right.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: MerchantSettings,
    staff: List<StaffMember> = emptyList(),
    changeRequests: List<ChangeRequest> = emptyList(),
    onOpenBusinessHours: () -> Unit = {},
    onRequestAdminChange: (kind: String, requestedValue: String, reason: String) -> Unit = { _, _, _ -> },
    onUpdateStoreSettings: (
        storeName: String,
        phone: String,
        minOrder: Double,
        defaultPrep: Int,
        sound: Boolean,
        vibration: Boolean,
        paperSize: String,
        autoPrint: Boolean
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onOpenPrinter: () -> Unit = {},
    onOpenMapLocation: () -> Unit = {},
    onOpenStaffDialog: () -> Unit = {},
    onToggleDarkMode: (Boolean) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val customColors = LocalDelivioColors.current
    val density = LocalDensity.current
    val isDark = isSystemInDarkTheme() || settings.isDarkMode

    // Dialog state for Business Type Change Request
    var showTypeChangeDialog by remember { mutableStateOf(false) }

    // Consistent switch colors: yellow track with dark thumb when on, grey track when off
    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = Color(0xFF1A1A1A),
        checkedTrackColor = DelivioYellow,
        checkedBorderColor = Color.Transparent,
        uncheckedThumbColor = Color(0xFF757575),
        uncheckedTrackColor = if (isDark) Color(0xFF383838) else Color(0xFFE0E0E0),
        uncheckedBorderColor = Color.Transparent
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.testTag("settings_top_bar")
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==============================================================
            // 1. STORE SECTION
            // ==============================================================
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "STORE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth().testTag("settings_store_card")
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        // Business hours schedule row
                        SettingsRow(
                            icon = Icons.Default.Schedule,
                            title = "Business Hours Schedule",
                            subtitle = "Mon-Sun operating hours and auto open/close",
                            onClick = onOpenBusinessHours,
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Open Business Hours",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.testTag("settings_business_hours_row")
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                        )

                        // Business type row
                        val pendingTypeRequest = changeRequests.firstOrNull { it.kind == "type" && it.status == "Pending" }
                        val currentTypeLabel = when (settings.businessType) {
                            BusinessType.CONVENIENCE -> "Convenience Store"
                            else -> "Food and Dining"
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag("settings_business_type_row")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 56.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(customColors.accentTint),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Storefront,
                                        contentDescription = null,
                                        tint = customColors.accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Business Type",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "Locked per merchant agreement. Request a change and the admin will review it.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                // Current type as chip with lock
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = DelivioYellow,
                                    modifier = Modifier.testTag("business_type_chip")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Locked",
                                            tint = Color(0xFF1A1A1A),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = currentTypeLabel,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = Color(0xFF1A1A1A)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (pendingTypeRequest != null) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth().testTag("pending_business_type_chip")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.HourglassEmpty,
                                            contentDescription = null,
                                            tint = customColors.accent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Pending: ${pendingTypeRequest.requestedValue}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { showTypeChangeDialog = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .testTag("request_business_type_change_btn")
                                ) {
                                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Request a change", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                        )

                        // Store Map Location (Pickup Point)
                        SettingsRow(
                            icon = Icons.Default.PinDrop,
                            title = "Store Map Location (Pickup Point)",
                            subtitle = settings.storeLocation.street.ifBlank { "Pickup point on OpenStreetMap" },
                            onClick = onOpenMapLocation,
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Set Location",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.testTag("settings_store_location_row")
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                        )

                        // Staff & Roles
                        SettingsRow(
                            icon = Icons.Default.People,
                            title = "Staff & Roles (${staff.size})",
                            subtitle = "Store staff accounts & access levels",
                            onClick = onOpenStaffDialog,
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Manage Staff",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.testTag("settings_staff_row")
                        )
                    }
                }
            }

            // ==============================================================
            // 2. ORDERS SECTION
            // ==============================================================
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "ORDERS",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth().testTag("settings_orders_card")
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        // Default prep time row
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag("settings_prep_time_row")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 44.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(customColors.accentTint),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = null,
                                        tint = customColors.accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Default Prep Time",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "Estimated kitchen preparation time",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Prep time chips: 10m, 15m, 20m, 30m (selected chip is yellow with dark text)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(10, 15, 20, 30).forEach { mins ->
                                    val isSelected = settings.defaultPrepTime == mins
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            onUpdateStoreSettings(
                                                settings.storeName,
                                                settings.phone,
                                                settings.minimumOrder,
                                                mins,
                                                settings.soundEnabled,
                                                settings.vibrationEnabled,
                                                settings.printerPaperSize,
                                                settings.autoPrintOrders
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = "${mins}m",
                                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                            )
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = DelivioYellow,
                                            selectedLabelColor = Color(0xFF1A1A1A),
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                            labelColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isSelected,
                                            borderColor = if (isSelected) DelivioYellow else Color.Transparent,
                                            borderWidth = 1.dp
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("prep_time_chip_$mins")
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                        )

                        // New order sound switch with Test link
                        SettingsRow(
                            icon = Icons.Default.VolumeUp,
                            title = "New Order Sound",
                            subtitle = "Loud kitchen alert on incoming orders",
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = {
                                            SoundVibrationHelper.playNewOrderAlert(
                                                context = context,
                                                soundEnabled = true,
                                                vibrationEnabled = false
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.testTag("test_order_sound_btn")
                                    ) {
                                        Text(
                                            text = "Test",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = customColors.accent
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Switch(
                                        checked = settings.soundEnabled,
                                        onCheckedChange = { checked ->
                                            onUpdateStoreSettings(
                                                settings.storeName,
                                                settings.phone,
                                                settings.minimumOrder,
                                                settings.defaultPrepTime,
                                                checked,
                                                settings.vibrationEnabled,
                                                settings.printerPaperSize,
                                                settings.autoPrintOrders
                                            )
                                        },
                                        colors = switchColors,
                                        modifier = Modifier.testTag("settings_sound_switch")
                                    )
                                }
                            },
                            modifier = Modifier.testTag("settings_sound_row")
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                        )

                        // Haptic vibration alert switch
                        SettingsRow(
                            icon = Icons.Default.Vibration,
                            title = "Haptic Vibration Alert",
                            subtitle = "Vibrate terminal on incoming orders",
                            trailingContent = {
                                Switch(
                                    checked = settings.vibrationEnabled,
                                    onCheckedChange = { checked ->
                                        onUpdateStoreSettings(
                                            settings.storeName,
                                            settings.phone,
                                            settings.minimumOrder,
                                            settings.defaultPrepTime,
                                            settings.soundEnabled,
                                            checked,
                                            settings.printerPaperSize,
                                            settings.autoPrintOrders
                                        )
                                    },
                                    colors = switchColors,
                                    modifier = Modifier.testTag("settings_vibration_switch")
                                )
                            },
                            modifier = Modifier.testTag("settings_vibration_row")
                        )
                    }
                }
            }

            // ==============================================================
            // 3. PRINTER SECTION
            // ==============================================================
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "PRINTER",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth().testTag("settings_printer_card")
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        // Bluetooth printer row
                        val printerStatus = if (settings.bluetoothPrinterAddress.isNotBlank()) {
                            settings.bluetoothPrinterName.ifBlank { "Connected" }
                        } else {
                            "Not connected"
                        }

                        SettingsRow(
                            icon = Icons.Default.Print,
                            title = "Bluetooth Receipt Printer",
                            subtitle = "Bluetooth receipt printer (scan, pair, test print)",
                            onClick = onOpenPrinter,
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = printerStatus,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (settings.bluetoothPrinterAddress.isNotBlank())
                                            customColors.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Open Printer Setup",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            modifier = Modifier.testTag("settings_bluetooth_printer_row")
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                        )

                        // Receipt paper width chips: 58 mm and 80 mm (selected is yellow with dark text)
                        SettingsRow(
                            icon = Icons.Default.Receipt,
                            title = "Receipt Paper Width",
                            subtitle = "Thermal printer roll width for receipts",
                            trailingContent = {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("58mm" to "58 mm", "80mm" to "80 mm").forEach { (value, label) ->
                                        val isSelected = settings.printerPaperSize == value
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                onUpdateStoreSettings(
                                                    settings.storeName,
                                                    settings.phone,
                                                    settings.minimumOrder,
                                                    settings.defaultPrepTime,
                                                    settings.soundEnabled,
                                                    settings.vibrationEnabled,
                                                    value,
                                                    settings.autoPrintOrders
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = label,
                                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                                )
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = DelivioYellow,
                                                selectedLabelColor = Color(0xFF1A1A1A),
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                                labelColor = MaterialTheme.colorScheme.onSurface
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                enabled = true,
                                                selected = isSelected,
                                                borderColor = if (isSelected) DelivioYellow else Color.Transparent,
                                                borderWidth = 1.dp
                                            ),
                                            modifier = Modifier.testTag("paper_size_chip_$value")
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.testTag("settings_paper_width_row")
                        )
                    }
                }
            }

            // ==============================================================
            // 4. APPEARANCE SECTION
            // ==============================================================
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "APPEARANCE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth().testTag("settings_appearance_card")
                ) {
                    SettingsRow(
                        icon = Icons.Default.DarkMode,
                        title = "Dark Mode",
                        subtitle = "High-contrast #121212 merchant palette",
                        trailingContent = {
                            Switch(
                                checked = settings.isDarkMode,
                                onCheckedChange = { onToggleDarkMode(it) },
                                colors = switchColors,
                                modifier = Modifier.testTag("settings_dark_mode_switch")
                            )
                        },
                        modifier = Modifier.testTag("settings_dark_mode_row")
                    )
                }
            }

            // Extra bottom padding (navigation bar height + 96 dp) so last card scrolls fully above bottom bars
            val navBarBottomDp = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
            Spacer(modifier = Modifier.height(navBarBottomDp + 96.dp))
        }
    }

    // Business Type Change Request Dialog
    if (showTypeChangeDialog) {
        val currentType = when (settings.businessType) {
            BusinessType.CONVENIENCE -> "Convenience Store"
            else -> "Food and Dining"
        }
        val targetOption = if (currentType.contains("conv", ignoreCase = true)) "Food and Dining" else "Convenience Store"
        var selectedValue by remember { mutableStateOf(targetOption) }
        var changeReason by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showTypeChangeDialog = false },
            title = { Text("Request Business Type Change", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Current Type: $currentType",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Select Requested Type:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    listOf("Food and Dining", "Convenience Store").forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedValue = option }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedValue == option,
                                onClick = { selectedValue = option },
                                colors = RadioButtonDefaults.colors(selectedColor = customColors.accent)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(option, fontWeight = FontWeight.Medium)
                        }
                    }

                    OutlinedTextField(
                        value = changeReason,
                        onValueChange = { changeReason = it },
                        label = { Text("Reason for change") },
                        placeholder = { Text("Describe reason for changing business type") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRequestAdminChange("type", selectedValue, changeReason.trim())
                        showTypeChangeDialog = false
                    },
                    enabled = changeReason.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = customColors.accent,
                        contentColor = customColors.onAccent
                    )
                ) {
                    Text("Submit Request", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTypeChangeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Standard reusable settings row:
 * At least 56 dp tall, with an icon in a soft yellow circle, a title, a one-line subtitle,
 * and the control on the right.
 */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailingContent: @Composable () -> Unit,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val customColors = LocalDelivioColors.current
    val rowModifier = if (onClick != null) {
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    } else {
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(customColors.accentTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = customColors.accent,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        trailingContent()
    }
}
