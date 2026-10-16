package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.data.model.MerchantSettings
import com.example.data.model.StaffMember
import com.example.ui.theme.DelivioYellow
import com.example.ui.theme.LocalDelivioColors
import com.example.ui.theme.parseHexColor

@Composable
fun ProfileScreen(
    settings: MerchantSettings,
    staff: List<StaffMember>,
    onBusinessTypeChange: (BusinessType) -> Unit,
    onRequestRiderManual: () -> Unit,
    onOpenColorPicker: () -> Unit,
    onToggleDarkMode: (Boolean) -> Unit,
    onOpenMapLocation: () -> Unit,
    onOpenBusinessHours: () -> Unit,
    onOpenStaffDialog: () -> Unit,
    onLogout: () -> Unit,
    onUpdateStoreSettings: (
        storeName: String,
        phone: String,
        minOrder: Double,
        defaultPrep: Int,
        sound: Boolean,
        vibration: Boolean,
        paperSize: String,
        autoPrint: Boolean
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val customColors = LocalDelivioColors.current

    var soundEnabled by remember(settings.soundEnabled) { mutableStateOf(settings.soundEnabled) }
    var vibrationEnabled by remember(settings.vibrationEnabled) { mutableStateOf(settings.vibrationEnabled) }
    var pushEnabled by remember(settings.pushNotificationsEnabled) { mutableStateOf(settings.pushNotificationsEnabled) }
    var paperSize by remember(settings.printerPaperSize) { mutableStateOf(settings.printerPaperSize) }
    var autoPrint by remember(settings.autoPrintOrders) { mutableStateOf(settings.autoPrintOrders) }
    var defaultPrepTime by remember(settings.defaultPrepTime) { mutableIntStateOf(settings.defaultPrepTime) }

    var showHelpDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Merchant Profile Header Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Avatar / Store Logo Placeholder
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(customColors.accentTint),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (settings.businessType == BusinessType.FOOD) "👨‍🍳" else "🏬",
                            fontSize = 34.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = settings.storeName,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                        )
                        Text(
                            text = "Owner: ${settings.ownerName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = settings.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = settings.phone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // BUSINESS TYPE SELECTOR (Food vs Convenience Store)
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Business Type Architecture",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Select your store type to tailor menu fields, kitchen prep timers, and inventory SKUs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val isFood = settings.businessType == BusinessType.FOOD
                    val isConv = settings.businessType == BusinessType.CONVENIENCE

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isFood) customColors.accentTint else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onBusinessTypeChange(BusinessType.FOOD) }
                            .testTag("business_type_food_btn")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🍔", fontSize = 28.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Food & Dining", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Kitchen Prep & Modifiers", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isConv) customColors.accentTint else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onBusinessTypeChange(BusinessType.CONVENIENCE) }
                            .testTag("business_type_convenience_btn")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🏪", fontSize = 28.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Convenience Store", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("SKUs, Barcodes & Units", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // REQUEST A RIDER BUTTON (Directly Below Business Type Architecture)
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(customColors.accentTint),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeliveryDining,
                            contentDescription = null,
                            tint = customColors.accent,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Manual Rider Dispatch",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Merchant can manually book a rider for order delivery.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onRequestRiderManual,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = customColors.accent,
                        contentColor = customColors.onAccent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("profile_manual_request_rider_btn")
                ) {
                    Icon(Icons.Default.DeliveryDining, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Request a Rider", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
            }
        }

        // STORE LOCATION & PICKUP PIN
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PinDrop, contentDescription = null, tint = customColors.accent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Store Map Location (Pickup Point)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = settings.storeLocation.formatted,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Lat: %.4f • Lng: %.4f".format(settings.storeLocation.latitude, settings.storeLocation.longitude),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onOpenMapLocation,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("set_map_location_btn")
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Set Map Location & Draggable Pin", fontWeight = FontWeight.Bold)
                }
            }
        }

        // THEME COLOR & APPEARANCE (LIGHT / DARK MODE)
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Brand & Theme Appearance", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)

                Spacer(modifier = Modifier.height(14.dp))

                // Theme Accent Color Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { onOpenColorPicker() }
                        .padding(14.dp)
                        .testTag("choose_merchant_theme_color_btn"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(parseHexColor(settings.themeAccentHex, DelivioYellow))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Merchant Accent Color", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(settings.themeAccentHex.uppercase(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("Change", fontWeight = FontWeight.Bold, color = customColors.accent, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Light / Dark Mode Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (settings.isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = null,
                            tint = customColors.accent
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Dark Mode", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("High-contrast #121212 merchant palette", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Switch(
                        checked = settings.isDarkMode,
                        onCheckedChange = { onToggleDarkMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = customColors.accent,
                            checkedTrackColor = customColors.accentTint
                        ),
                        modifier = Modifier.testTag("dark_mode_switch")
                    )
                }
            }
        }

        // STORE OPERATIONS SETTINGS (Delivery Radius Slider Removed as requested!)
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Operational Settings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)

                Spacer(modifier = Modifier.height(14.dp))

                // Business Hours Schedule
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .clickable { onOpenBusinessHours() }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Business Hours Schedule", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text("Mon-Sun operating hours & auto open/close", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }

                // Kitchen Default Prep Time
                if (settings.businessType == BusinessType.FOOD) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Default Prep Time: $defaultPrepTime mins",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(10, 15, 20, 30).forEach { mins ->
                            FilterChip(
                                selected = defaultPrepTime == mins,
                                onClick = {
                                    defaultPrepTime = mins
                                    onUpdateStoreSettings(
                                        settings.storeName, settings.phone, settings.minimumOrder,
                                        mins, soundEnabled, vibrationEnabled, paperSize, autoPrint
                                    )
                                },
                                label = { Text("${mins}m") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = customColors.accentTint,
                                    selectedLabelColor = customColors.accent
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Notifications (Sound, Vibration)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("New Order Sound", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = soundEnabled,
                        onCheckedChange = {
                            soundEnabled = it
                            onUpdateStoreSettings(
                                settings.storeName, settings.phone, settings.minimumOrder,
                                defaultPrepTime, it, vibrationEnabled, paperSize, autoPrint
                            )
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Haptic Vibration Alert", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = vibrationEnabled,
                        onCheckedChange = {
                            vibrationEnabled = it
                            onUpdateStoreSettings(
                                settings.storeName, settings.phone, settings.minimumOrder,
                                defaultPrepTime, soundEnabled, it, paperSize, autoPrint
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Thermal Printer Paper Size
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Receipt Printer Format", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = paperSize == "58mm",
                            onClick = {
                                paperSize = "58mm"
                                onUpdateStoreSettings(
                                    settings.storeName, settings.phone, settings.minimumOrder,
                                    defaultPrepTime, soundEnabled, vibrationEnabled, "58mm", autoPrint
                                )
                            },
                            label = { Text("58mm") }
                        )
                        FilterChip(
                            selected = paperSize == "80mm",
                            onClick = {
                                paperSize = "80mm"
                                onUpdateStoreSettings(
                                    settings.storeName, settings.phone, settings.minimumOrder,
                                    defaultPrepTime, soundEnabled, vibrationEnabled, "80mm", autoPrint
                                )
                            },
                            label = { Text("80mm") }
                        )
                    }
                }
            }
        }

        // STAFF ACCOUNTS
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Staff & Roles (${staff.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = onOpenStaffDialog) {
                        Text("+ Add Staff", fontWeight = FontWeight.Bold)
                    }
                }

                staff.forEach { member ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(member.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text(member.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            text = member.role,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = customColors.accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(customColors.accentTint)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // HELP, TERMS & LANGUAGE
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                ListItem(
                    headlineContent = { Text("App Language") },
                    supportingContent = { Text("English (Default)") },
                    leadingContent = { Icon(Icons.Default.Language, contentDescription = null) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                    modifier = Modifier.clickable { showLanguageDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ListItem(
                    headlineContent = { Text("Merchant Help & Support") },
                    leadingContent = { Icon(Icons.Default.HelpOutline, contentDescription = null) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                    modifier = Modifier.clickable { showHelpDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ListItem(
                    headlineContent = { Text("Terms & Merchant Agreement") },
                    leadingContent = { Icon(Icons.Default.Gavel, contentDescription = null) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                    modifier = Modifier.clickable { showTermsDialog = true }
                )
            }
        }

        // LOG OUT BUTTON ("Create Test Order" button above it was removed as requested!)
        OutlinedButton(
            onClick = onLogout,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("profile_logout_btn")
        ) {
            Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Log Out of Delivio Partner", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Merchant Partner Support") },
            text = { Text("Delivio Partner 24/7 hotline: 1-800-DELIVIO\nEmail: partner-support@delivio.com\nLive kitchen chat available 8:00 AM - 11:00 PM.") },
            confirmButton = { TextButton(onClick = { showHelpDialog = false }) { Text("OK") } }
        )
    }

    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = { Text("Delivio Merchant Terms") },
            text = { Text("Orders accepted via the Delivio Partner terminal are binding commercial deliveries. Standard commission applies as agreed in the Merchant Agreement.") },
            confirmButton = { TextButton(onClick = { showTermsDialog = false }) { Text("Close") } }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Select Language") },
            text = { Text("Current terminal language is set to English (US). Additional regional translations (Filipino, Spanish) can be toggled.") },
            confirmButton = { TextButton(onClick = { showLanguageDialog = false }) { Text("Save") } }
        )
    }
}
