package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MerchantSettings
import com.example.ui.theme.LocalDelivioColors
import com.example.ui.theme.StatusClosedRed
import com.example.ui.theme.StatusOpenGreen
import com.example.ui.theme.StatusWarningAmber
import com.example.util.BusinessHoursHelper
import com.example.util.FormatUtils

/**
 * Main Top Bar:
 * - Shop logo, shop name, Open/Closed/Auto status chip on the left
 * - Three-lines menu icon on the RIGHT with a red badge when there are new orders or unread inbox items
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DelivioTopBar(
    settings: MerchantSettings,
    onToggleOpen: () -> Unit,
    onOpenDrawer: () -> Unit = {},
    badgeCount: Int = 0,
    hasUnreadChat: Boolean = false,
    currentTime: Long = System.currentTimeMillis(),
    isServiceLocked: Boolean = false,
    serviceLockMessage: String? = null,
    isMultiOutlet: Boolean = false,
    otherOutletsNewOrdersCount: Int = 0,
    onOutletClick: () -> Unit = {},
    onContactSupport: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val customColors = LocalDelivioColors.current
    val storeStatus = BusinessHoursHelper.storeStatus(settings, currentTime)
    val effectiveOpen = !isServiceLocked && storeStatus.open
    val isAdminLocked = settings.adminLock
    val isSuspended = !settings.status.equals("active", ignoreCase = true)
    val canToggle = !isServiceLocked && !isAdminLocked && !isSuspended

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // LEFT SIDE: Store Logo, Name & Open/Auto Status Chip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                MerchantLogoView(
                    storeName = settings.storeName,
                    logoUrl = settings.logoUrl,
                    imageUrl = settings.imageUrl,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    shape = RoundedCornerShape(10.dp),
                    letterSize = 18.sp
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isMultiOutlet) settings.displayName.ifBlank { "Merchant Store" }
                            else settings.storeName.ifBlank { "Merchant Store" },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isMultiOutlet) {
                            Surface(
                                color = customColors.accentTint,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onOutletClick)
                                    .testTag("topbar_outlet_chip")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    if (otherOutletsNewOrdersCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(StatusClosedRed)
                                                .testTag("topbar_outlet_red_dot")
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = settings.branchCode.ifBlank { "Switch" },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ExpandMore,
                                        contentDescription = "Switch outlet",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val chipColor = if (effectiveOpen) StatusOpenGreen else StatusClosedRed
                        val statusLabel = when {
                            isSuspended -> "Closed"
                            isAdminLocked -> "Closed by Delivio"
                            effectiveOpen -> "Open"
                            else -> "Closed"
                        }

                        // Global Open/Close Status Chip (reading live storeStatus)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(chipColor.copy(alpha = 0.15f))
                                .clickable(enabled = canToggle, onClick = onToggleOpen)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                .testTag("topbar_status_chip")
                        ) {
                            if (isAdminLocked) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Closed by Delivio",
                                    tint = StatusClosedRed,
                                    modifier = Modifier.size(11.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(chipColor)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = statusLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = chipColor
                            )
                        }

                        if (settings.autoSchedule) {
                            Text(
                                text = "Auto",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = customColors.accent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(customColors.accentTint)
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                    .testTag("topbar_auto_chip")
                            )
                        }

                        if (isAdminLocked) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .clickable(onClick = onContactSupport)
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                                    .testTag("topbar_contact_support_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HeadsetMic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Contact support",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }

                        if (settings.isPaused && settings.pauseUntilTimestamp != null && !isAdminLocked) {
                            Text(
                                text = "Paused (${FormatUtils.formatRemainingTime(settings.pauseUntilTimestamp)})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = StatusWarningAmber,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(StatusWarningAmber.copy(alpha = 0.15f))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // RIGHT SIDE: Three-lines Menu Icon with Red Badge
            IconButton(
                onClick = onOpenDrawer,
                modifier = Modifier
                    .size(42.dp)
                    .testTag("topbar_menu_btn")
            ) {
                if (badgeCount > 0) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ) {
                                Text(
                                    text = if (badgeCount > 99) "99+" else "$badgeCount",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open Menu Drawer",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                } else if (hasUnreadChat) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = Color(0xFFE11D48),
                                modifier = Modifier.size(8.dp)
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open Menu Drawer (New message)",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open Menu Drawer",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}
