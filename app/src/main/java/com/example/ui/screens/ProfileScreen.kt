package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChangeRequest
import com.example.data.model.MerchantSettings
import com.example.data.model.StaffMember
import com.example.ui.components.MerchantLogoView
import com.example.ui.theme.LocalDelivioColors
import com.example.util.FormatUtils
import java.util.Locale

/**
 * Profile Screen:
 * 1. Shop card (with rating stars ONLY under shop name & Owner line; tapping stars opens ReviewsScreen)
 * 2. Delivio Wallet (Owner only)
 * 3. Inbox
 * 4. Manual Rider Dispatch (with Request a Rider button)
 * 5. Settings card (Settings row: icon in soft yellow circle, title, subtitle "Store, orders, printer and appearance", chevron,
 *    plus App Language, Help & Support, Terms)
 * 6. Log out button
 *
 * "Business Type Architecture", "Operational Settings", and "Printer" have moved to SettingsScreen.
 */
@Composable
fun ProfileScreen(
    settings: MerchantSettings,
    staff: List<StaffMember> = emptyList(),
    changeRequests: List<ChangeRequest> = emptyList(),
    onRequestAdminChange: (kind: String, requestedValue: String, reason: String) -> Unit = { _, _, _ -> },
    onOpenStoreProfile: () -> Unit = {},
    onUpdateMerchantProfile: (ownerName: String, phone: String, logoUrl: String) -> Unit = { _, _, _ -> },
    onRequestRiderManual: () -> Unit = {},
    onOpenColorPicker: () -> Unit = {},
    onToggleDarkMode: (Boolean) -> Unit = {},
    onOpenMapLocation: () -> Unit = {},
    onOpenBusinessHours: () -> Unit = {},
    onOpenStaffDialog: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenPrinter: () -> Unit = {},
    onLogout: () -> Unit = {},
    isOwner: Boolean = true,
    canAccessInbox: Boolean = true,
    unreadInboxCount: Int = 0,
    onOpenInbox: () -> Unit = {},
    canAccessReviews: Boolean = true,
    reviewsCount: Int = 0,
    averageRating: Double = 5.0,
    onOpenReviews: () -> Unit = {},
    walletBalance: Double = 0.0,
    isSharedWallet: Boolean = false,
    onOpenWallet: () -> Unit = {},
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
    modifier: Modifier = Modifier
) {
    val customColors = LocalDelivioColors.current
    val density = LocalDensity.current

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
        // ==============================================================
        // 1. Merchant Profile Header Card (Tappable: opens Store Profile Details screen)
        // ==============================================================
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenStoreProfile() }
                .testTag("profile_header_card")
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Logo with small pencil badge
                    Box(modifier = Modifier.size(72.dp)) {
                        MerchantLogoView(
                            storeName = settings.storeName,
                            logoUrl = settings.logoUrl,
                            imageUrl = settings.imageUrl,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            letterSize = 28.sp
                        )
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(customColors.accent)
                                .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit store profile",
                                tint = customColors.onAccent,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = settings.storeName.ifBlank { "Merchant Store" },
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked name",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = if (settings.ownerName.isNotBlank()) "Owner: ${settings.ownerName}" else "Owner: Not set",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // RATINGS AS STARS ONLY (under shop name and Owner line)
                        // Shows five stars only (no number, no text): filled, half, outline from current average.
                        // With no ratings yet, shows five grey outline stars.
                        // Accessibility label: "Rated 4.8 out of 5, 12 reviews" or "No ratings yet".
                        // Tapping the stars opens the existing reviews screen.
                        val ratingAccessibilityLabel = if (reviewsCount > 0) {
                            "Rated %.1f out of 5, %d reviews".format(Locale.US, averageRating, reviewsCount)
                        } else {
                            "No ratings yet"
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .semantics { contentDescription = ratingAccessibilityLabel }
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onOpenReviews() }
                                .padding(vertical = 2.dp)
                                .testTag("profile_rating_stars_row")
                        ) {
                            for (i in 1..5) {
                                val starIcon = when {
                                    averageRating >= i -> Icons.Filled.Star
                                    averageRating >= (i - 0.5) -> Icons.Filled.StarHalf
                                    else -> Icons.Outlined.StarOutline
                                }
                                val starTint = if (reviewsCount == 0) {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                } else if (averageRating >= (i - 0.5)) {
                                    Color(0xFFFFB300)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                }
                                Icon(
                                    imageVector = starIcon,
                                    contentDescription = null,
                                    tint = starTint,
                                    modifier = Modifier.size(18.dp)
                                )
                                if (i < 5) Spacer(modifier = Modifier.width(2.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = settings.email.ifBlank { "No email set" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (settings.email.isNotBlank()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Read-only email",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        Text(
                            text = if (settings.phone.isNotBlank()) "Phone: ${settings.phone}" else "Phone: Not set",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Chevron on the right to indicate tappable
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Edit Store Profile",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                )

                // Address & Coordinates
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = customColors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = settings.storeLocation.street.ifBlank { "No address set" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (settings.storeLocation.latitude != 0.0 || settings.storeLocation.longitude != 0.0)
                                "Lat: %.5f • Lng: %.5f".format(settings.storeLocation.latitude, settings.storeLocation.longitude)
                            else "GPS Coordinates not set",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ==============================================================
        // 2. WALLET ROW (Owner only; hidden from staff)
        // ==============================================================
        if (isOwner) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenWallet() }
                    .testTag("profile_wallet_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(customColors.accentTint),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = customColors.accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Delivio Wallet",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (isSharedWallet) "Shared wallet (linked customer account)" else "Merchant store wallet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = FormatUtils.formatCurrency(walletBalance),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = customColors.accent
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "View Wallet",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ==============================================================
        // 3. INBOX ROW (Owner and Manager only)
        // ==============================================================
        if (canAccessInbox) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenInbox() }
                    .testTag("profile_inbox_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(customColors.accentTint),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inbox,
                                contentDescription = null,
                                tint = customColors.accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Inbox",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Platform updates, order notices, and promotions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (unreadInboxCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.error
                            ) {
                                Text(
                                    text = "$unreadInboxCount",
                                    color = MaterialTheme.colorScheme.onError,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "View Inbox",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ==============================================================
        // 4. MANUAL RIDER DISPATCH (Request a Rider)
        // ==============================================================
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth().testTag("profile_manual_rider_card")
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

        // ==============================================================
        // 5. SETTINGS ROW + APP LANGUAGE, HELP & SUPPORT, TERMS
        // ==============================================================
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth().testTag("profile_settings_card")
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                // Settings row (at least 56 dp tall, icon in soft yellow circle, title, subtitle, chevron)
                ListItem(
                    headlineContent = {
                        Text(
                            text = "Settings",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                    },
                    supportingContent = {
                        Text(
                            text = "Store, orders, printer and appearance",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(customColors.accentTint),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = customColors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Open Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .clickable { onOpenSettings() }
                        .testTag("profile_settings_row")
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                )

                // App Language
                ListItem(
                    headlineContent = {
                        Text(
                            text = "App Language",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    supportingContent = {
                        Text(
                            text = "English (Default)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .clickable { showLanguageDialog = true }
                        .testTag("profile_language_row")
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                )

                // Merchant Help & Support
                ListItem(
                    headlineContent = {
                        Text(
                            text = "Merchant Help & Support",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    supportingContent = {
                        Text(
                            text = "24/7 hotline and partner FAQs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .clickable { showHelpDialog = true }
                        .testTag("profile_help_row")
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                )

                // Terms & Merchant Agreement
                ListItem(
                    headlineContent = {
                        Text(
                            text = "Terms & Merchant Agreement",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    supportingContent = {
                        Text(
                            text = "Policies, commercial terms & rules",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Gavel,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .clickable { showTermsDialog = true }
                        .testTag("profile_terms_row")
                )
            }
        }

        // ==============================================================
        // 6. LOG OUT BUTTON
        // ==============================================================
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

        // Bottom scroll padding: navigation bar height + 96 dp
        val navBarBottomDp = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
        Spacer(modifier = Modifier.height(navBarBottomDp + 96.dp))
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Merchant Partner Support", fontWeight = FontWeight.Bold) },
            text = { Text("Delivio Partner 24/7 hotline: 1-800-DELIVIO\nEmail: partner-support@delivio.com\nLive kitchen chat available 8:00 AM - 11:00 PM.") },
            confirmButton = { TextButton(onClick = { showHelpDialog = false }) { Text("OK") } }
        )
    }

    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = { Text("Delivio Merchant Terms", fontWeight = FontWeight.Bold) },
            text = { Text("Orders accepted via the Delivio Partner terminal are binding commercial deliveries. Standard commission applies as agreed in the Merchant Agreement.") },
            confirmButton = { TextButton(onClick = { showTermsDialog = false }) { Text("Close") } }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Select Language", fontWeight = FontWeight.Bold) },
            text = { Text("Current terminal language is set to English (US). Additional regional translations (Filipino, Spanish) can be toggled.") },
            confirmButton = { TextButton(onClick = { showLanguageDialog = false }) { Text("Save") } }
        )
    }
}
