package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
 * - Shop logo, shop name, Open/Auto status chip on the left
 * - Three-lines menu icon on the RIGHT with a red badge when there are new orders or unread inbox items
 */
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
    modifier: Modifier = Modifier
) {
    val customColors = LocalDelivioColors.current
    val effectiveOpen = BusinessHoursHelper.isEffectiveOpen(settings, currentTime, isServiceLocked)

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
                    bannerUrl = settings.bannerUrl,
                    imageUrl = settings.imageUrl,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    shape = RoundedCornerShape(10.dp),
                    letterSize = 18.sp
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = settings.storeName.ifBlank { "Merchant Store" },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Global Open/Close Status Chip (showing effectiveOpen)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (effectiveOpen) StatusOpenGreen.copy(alpha = 0.15f)
                                    else StatusClosedRed.copy(alpha = 0.15f)
                                )
                                .then(
                                    if (!isServiceLocked) Modifier.clickable { onToggleOpen() }
                                    else Modifier
                                )
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                .testTag("topbar_status_chip")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (effectiveOpen) StatusOpenGreen else StatusClosedRed)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (effectiveOpen) "Open" else "Closed",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (effectiveOpen) StatusOpenGreen else StatusClosedRed
                            )
                        }

                        if (settings.autoSchedule) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Auto",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = customColors.accent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(customColors.accentTint)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }

                        if (settings.isPaused && settings.pauseUntilTimestamp != null) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Paused (${FormatUtils.formatRemainingTime(settings.pauseUntilTimestamp)})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = StatusWarningAmber,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(StatusWarningAmber.copy(alpha = 0.15f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
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
