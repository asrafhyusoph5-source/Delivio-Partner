package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MerchantSettings
import com.example.ui.theme.DelivioYellow
import com.example.ui.theme.LocalDelivioColors
import com.example.ui.theme.StatusClosedRed
import com.example.ui.theme.StatusOpenGreen
import com.example.util.BusinessHoursHelper
import com.example.util.FormatUtils
import java.util.Locale

/**
 * Right-side sliding Navigation Drawer (Driver-app look):
 * - Header: Yellow background, shop logo, name, five stars only (opens reviews screen),
 *           and an Open/Closed switch that uses existing manual-close logic with confirm dialog.
 * - 2x2 tiles: Inbox (unread badge), Earnings, Wallet, Reports.
 * - List: Orders, Menu, Dashboard, Vouchers, Request a Rider (highlighted yellow row),
 *         Store profile, Settings, Help and support (headset), Log out at the bottom with app version.
 */
@Composable
fun MerchantRightDrawer(
    settings: MerchantSettings,
    currentTime: Long,
    isServiceLocked: Boolean,
    unreadInboxCount: Int,
    newOrdersCount: Int,
    averageRating: Double,
    reviewsCount: Int,
    walletBalance: Double,
    onToggleOpen: () -> Unit,
    onOpenReviews: () -> Unit,
    onNavigateOrders: () -> Unit,
    onNavigateMenu: () -> Unit,
    onNavigateDashboard: () -> Unit,
    onNavigateEarnings: () -> Unit,
    onOpenReports: () -> Unit = onNavigateDashboard,
    onOpenWallet: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenVouchers: () -> Unit,
    onRequestRider: () -> Unit,
    onOpenStoreProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val customColors = LocalDelivioColors.current
    val density = LocalDensity.current
    val effectiveOpen = BusinessHoursHelper.isEffectiveOpen(settings, currentTime, isServiceLocked)
    val isDark = isSystemInDarkTheme()

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(0.82f)
            .testTag("merchant_right_drawer"),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            // ==============================================================
            // 1. YELLOW HEADER
            // ==============================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DelivioYellow)
                    .padding(horizontal = 18.dp, vertical = 20.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenStoreProfile() }
                            .padding(6.dp)
                            .testTag("drawer_header_profile_btn"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Shop Logo
                        MerchantLogoView(
                            storeName = settings.storeName,
                            logoUrl = settings.logoUrl,
                            bannerUrl = settings.bannerUrl,
                            imageUrl = settings.imageUrl,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .border(2.dp, Color(0xFF1A1A1A), CircleShape),
                            shape = CircleShape,
                            letterSize = 22.sp
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = settings.storeName.ifBlank { "Merchant Store" },
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp,
                                    color = Color(0xFF1A1A1A)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            // View store profile with chevron
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 1.dp, bottom = 3.dp)
                            ) {
                                Text(
                                    text = "View store profile",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF4A4A4A)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color(0xFF4A4A4A),
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            // FIVE STARS ONLY (Tapping opens Reviews)
                            val ratingLabel = if (reviewsCount > 0) {
                                "Rated %.1f out of 5, %d reviews".format(Locale.US, averageRating, reviewsCount)
                            } else {
                                "No ratings yet"
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .semantics { contentDescription = ratingLabel }
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onOpenReviews() }
                                    .padding(vertical = 2.dp)
                                    .testTag("drawer_rating_stars_btn")
                            ) {
                                for (i in 1..5) {
                                    val starIcon = when {
                                        averageRating >= i -> Icons.Filled.Star
                                        averageRating >= (i - 0.5) -> Icons.Filled.StarHalf
                                        else -> Icons.Outlined.StarOutline
                                    }
                                    val starTint = if (reviewsCount == 0) {
                                        Color(0xFF555555)
                                    } else if (averageRating >= (i - 0.5)) {
                                        Color(0xFF1A1A1A) // Dark filled stars on yellow background
                                    } else {
                                        Color(0xFF666666)
                                    }
                                    Icon(
                                        imageVector = starIcon,
                                        contentDescription = null,
                                        tint = starTint,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    if (i < 5) Spacer(modifier = Modifier.width(2.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Open / Closed Switch with pill status
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1A1A1A).copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (effectiveOpen) StatusOpenGreen else StatusClosedRed)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (effectiveOpen) "Store: Open" else "Store: Closed",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF1A1A1A)
                                )
                                if (settings.autoSchedule) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Auto",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF1A1A1A),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.White.copy(alpha = 0.7f))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }

                            Switch(
                                checked = effectiveOpen,
                                onCheckedChange = { onToggleOpen() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF1A1A1A),
                                    checkedTrackColor = Color(0xFFFFF176),
                                    checkedBorderColor = Color(0xFF1A1A1A),
                                    uncheckedThumbColor = Color(0xFF757575),
                                    uncheckedTrackColor = Color(0xFFE0E0E0),
                                    uncheckedBorderColor = Color.Transparent
                                ),
                                modifier = Modifier.testTag("drawer_open_switch")
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ==============================================================
            // 2. 2x2 TILES (Inbox, Earnings, Wallet, Reports)
            // ==============================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Tile 1: Inbox
                    DrawerTile(
                        title = "Inbox",
                        subtitle = if (unreadInboxCount > 0) "$unreadInboxCount new" else "Messages",
                        icon = Icons.Default.Inbox,
                        badgeCount = unreadInboxCount,
                        onClick = onOpenInbox,
                        modifier = Modifier.weight(1f).testTag("drawer_tile_inbox")
                    )

                    // Tile 2: Earnings
                    DrawerTile(
                        title = "Earnings",
                        subtitle = "Payouts & sales",
                        icon = Icons.Default.AccountBalanceWallet,
                        onClick = onNavigateEarnings,
                        modifier = Modifier.weight(1f).testTag("drawer_tile_earnings")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Tile 3: Wallet
                    DrawerTile(
                        title = "Wallet",
                        subtitle = FormatUtils.formatCurrency(walletBalance),
                        icon = Icons.Default.AccountBalance,
                        onClick = onOpenWallet,
                        modifier = Modifier.weight(1f).testTag("drawer_tile_wallet")
                    )

                    // Tile 4: Reports
                    DrawerTile(
                        title = "Reports",
                        subtitle = "Analytics & CSV",
                        icon = Icons.Default.BarChart,
                        onClick = onOpenReports,
                        modifier = Modifier.weight(1f).testTag("drawer_tile_reports")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )
            Spacer(modifier = Modifier.height(6.dp))

            // ==============================================================
            // 3. NAVIGATION LIST
            // ==============================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                // Orders
                DrawerListItem(
                    icon = Icons.Default.ReceiptLong,
                    title = "Orders",
                    badgeText = if (newOrdersCount > 0) "$newOrdersCount" else null,
                    badgeColor = MaterialTheme.colorScheme.error,
                    onClick = onNavigateOrders,
                    modifier = Modifier.testTag("drawer_item_orders")
                )

                // Menu
                DrawerListItem(
                    icon = Icons.Default.RestaurantMenu,
                    title = "Menu",
                    onClick = onNavigateMenu,
                    modifier = Modifier.testTag("drawer_item_menu")
                )

                // Dashboard
                DrawerListItem(
                    icon = Icons.Default.Dashboard,
                    title = "Dashboard",
                    onClick = onNavigateDashboard,
                    modifier = Modifier.testTag("drawer_item_dashboard")
                )

                // Reports
                DrawerListItem(
                    icon = Icons.Default.BarChart,
                    title = "Reports",
                    onClick = onOpenReports,
                    modifier = Modifier.testTag("drawer_item_reports")
                )

                // Vouchers
                DrawerListItem(
                    icon = Icons.Default.ConfirmationNumber,
                    title = "Vouchers",
                    onClick = onOpenVouchers,
                    modifier = Modifier.testTag("drawer_item_vouchers")
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Request a Rider (HIGHLIGHTED YELLOW ROW)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DelivioYellow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .clickable(onClick = onRequestRider)
                        .testTag("drawer_item_request_rider")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1A1A1A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeliveryDining,
                                contentDescription = null,
                                tint = DelivioYellow,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Request a Rider",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = Color(0xFF1A1A1A)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color(0xFF1A1A1A),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }



                // Settings
                DrawerListItem(
                    icon = Icons.Default.Settings,
                    title = "Settings",
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("drawer_item_settings")
                )

                // Help and Support
                DrawerListItem(
                    icon = Icons.Default.HeadsetMic,
                    title = "Help & Support",
                    onClick = onOpenHelp,
                    modifier = Modifier.testTag("drawer_item_help")
                )
            }

            Spacer(modifier = Modifier.weight(1f, fill = false))
            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )

            // ==============================================================
            // 4. LOG OUT & APP VERSION
            // ==============================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onLogout)
                        .padding(vertical = 8.dp)
                        .testTag("drawer_logout_btn"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = "Log Out",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Log Out",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Delivio Partner v2.5.0",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )

                val navBarBottomDp = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
                Spacer(modifier = Modifier.height(navBarBottomDp + 16.dp))
            }
        }
    }
}

/**
 * 2x2 Tile inside the right drawer
 */
@Composable
private fun DrawerTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badgeCount: Int = 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val customColors = LocalDelivioColors.current

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(customColors.accentTint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = customColors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (badgeCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.error
                    ) {
                        Text(
                            text = if (badgeCount > 99) "99+" else "$badgeCount",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * List item inside the right drawer
 */
@Composable
private fun DrawerListItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    badgeText: String? = null,
    badgeColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (badgeText != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = badgeColor
            ) {
                Text(
                    text = badgeText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
}
