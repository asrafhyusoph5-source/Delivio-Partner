package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalDelivioColors
import com.example.viewmodel.MerchantTab

@Composable
fun DelivioBottomBar(
    selectedTab: MerchantTab,
    newOrdersBadgeCount: Int,
    onTabSelected: (MerchantTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val customColors = LocalDelivioColors.current

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Orders, Menu, Dashboard, Earnings, Profile
            val tabs = listOf(
                MerchantTab.ORDERS,
                MerchantTab.MENU,
                MerchantTab.DASHBOARD,
                MerchantTab.EARNINGS,
                MerchantTab.PROFILE
            )

            tabs.forEach { tab ->
                val isSelected = tab == selectedTab
                val label = tab.label
                val icon = when (tab) {
                    MerchantTab.ORDERS -> if (isSelected) Icons.Filled.ReceiptLong else Icons.Outlined.ReceiptLong
                    MerchantTab.MENU -> if (isSelected) Icons.Filled.RestaurantMenu else Icons.Outlined.RestaurantMenu
                    MerchantTab.DASHBOARD -> if (isSelected) Icons.Filled.Dashboard else Icons.Outlined.Dashboard
                    MerchantTab.EARNINGS -> if (isSelected) Icons.Filled.AccountBalanceWallet else Icons.Outlined.AccountBalanceWallet
                    MerchantTab.PROFILE -> if (isSelected) Icons.Filled.Storefront else Icons.Outlined.Storefront
                }

                val indicatorColor = if (isSelected) customColors.accentTint else androidx.compose.ui.graphics.Color.Transparent
                val contentColor = if (isSelected) customColors.accent else MaterialTheme.colorScheme.onSurfaceVariant

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple()
                        ) { onTabSelected(tab) }
                        .padding(vertical = 4.dp)
                        .testTag("nav_tab_${tab.name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(indicatorColor)
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BadgedBox(
                            badge = {
                                if (tab == MerchantTab.ORDERS && newOrdersBadgeCount > 0) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                        modifier = Modifier.offset(x = 6.dp, y = (-4).dp)
                                    ) {
                                        Text(
                                            text = if (newOrdersBadgeCount > 99) "99+" else newOrdersBadgeCount.toString(),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = contentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
