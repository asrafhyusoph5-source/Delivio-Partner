package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.ui.theme.LocalDelivioColors
import com.example.ui.theme.StatusClosedRed
import com.example.ui.theme.StatusOpenGreen
import com.example.ui.theme.StatusWarningAmber
import com.example.util.FormatUtils

@Composable
fun OrdersScreen(
    orders: List<Order>,
    activeFilter: String,
    onFilterChanged: (String) -> Unit,
    onOrderClick: (Order) -> Unit,
    onAcceptClick: (Order) -> Unit,
    onRejectClick: (Order) -> Unit,
    onSetReadyClick: (Order) -> Unit,
    onRequestRiderClick: (Order) -> Unit,
    onPrintReceiptClick: (Order) -> Unit,
    modifier: Modifier = Modifier
) {
    val customColors = LocalDelivioColors.current
    // Filter tabs without "Today's Orders" and without "On the way"
    val filterTabs = listOf("New", "Preparing", "Ready", "Completed", "Cancelled")

    // Filter orders based on active tab
    val filteredOrders = remember(orders, activeFilter) {
        orders.filter { order ->
            when (activeFilter) {
                "New" -> order.status == OrderStatus.PLACED_ORDER
                "Preparing" -> order.status == OrderStatus.PREPARING
                "Ready" -> order.status == OrderStatus.READY || order.status == OrderStatus.RIDER_ON_THE_WAY
                "Completed" -> order.status == OrderStatus.DELIVERED
                "Cancelled" -> order.status == OrderStatus.CANCELLED || order.status == OrderStatus.REJECTED
                else -> true
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Segmented Filter Tabs (Horizontal Scrollable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filterTabs.forEach { tabName ->
                val count = when (tabName) {
                    "New" -> orders.count { it.status == OrderStatus.PLACED_ORDER }
                    "Preparing" -> orders.count { it.status == OrderStatus.PREPARING }
                    "Ready" -> orders.count { it.status == OrderStatus.READY || it.status == OrderStatus.RIDER_ON_THE_WAY }
                    "Completed" -> orders.count { it.status == OrderStatus.DELIVERED }
                    "Cancelled" -> orders.count { it.status == OrderStatus.CANCELLED || it.status == OrderStatus.REJECTED }
                    else -> 0
                }
                val isSelected = activeFilter == tabName

                FilterChip(
                    selected = isSelected,
                    onClick = { onFilterChanged(tabName) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tabName, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                            if (count > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) customColors.accent
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = count.toString(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) customColors.onAccent else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = customColors.accentTint,
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.testTag("filter_tab_${tabName.lowercase()}")
                )
            }
        }

        // Orders List
        if (filteredOrders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📦", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No $activeFilter Orders",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Orders in this stage will appear here automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
            ) {
                items(filteredOrders, key = { it.id }) { order ->
                    OrderCard(
                        order = order,
                        onClick = { onOrderClick(order) },
                        onAccept = { onAcceptClick(order) },
                        onReject = { onRejectClick(order) },
                        onSetReady = { onSetReadyClick(order) },
                        onRequestRider = { onRequestRiderClick(order) },
                        onPrintReceipt = { onPrintReceiptClick(order) }
                    )
                }
            }
        }
    }
}

@Composable
fun OrderCard(
    order: Order,
    onClick: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onSetReady: () -> Unit,
    onRequestRider: () -> Unit,
    onPrintReceipt: () -> Unit
) {
    val context = LocalContext.current
    val customColors = LocalDelivioColors.current
    val rider = order.riderRequest?.rider

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("order_card_${order.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Card Header: Order Number, Time Ago & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = order.orderNumber,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    if (order.isTestOrder) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "TEST",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = customColors.accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(customColors.accentTint)
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = FormatUtils.formatTimeAgo(order.placedAtTimestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OrderStatusBadge(status = order.status)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Customer Name & Contact
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = order.customerName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "📍 ${order.deliveryAddress.formatted}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                // Customer Phone Call button
                IconButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_DIAL).apply {
                            data = Uri.parse("tel:${order.customerPhone}")
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "Call Customer",
                        tint = StatusOpenGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Items List Summary
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(10.dp)
            ) {
                Text(
                    text = "${order.totalItemCount} Items Ordered:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                order.items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${item.quantity}x ${item.name}${if (item.selectedSize != null) " (${item.selectedSize})" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = FormatUtils.formatCurrency(item.totalPrice),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Total Amount & Payment Method
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total: ${FormatUtils.formatCurrency(order.total)} (${order.paymentMethod})",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = customColors.accent
                )
            }

            // Prep Countdown Timer if Preparing
            if (order.status == OrderStatus.PREPARING && order.prepEndTime != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(StatusWarningAmber.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Prep Timer",
                            tint = StatusWarningAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Kitchen Prep Countdown",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusWarningAmber
                        )
                    }
                    Text(
                        text = FormatUtils.formatRemainingTime(order.prepEndTime),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = StatusWarningAmber
                    )
                }
            }

            // READY STATUS: "Waiting for rider" banner
            if (order.status == OrderStatus.READY) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(StatusOpenGreen.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🛵", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Food is Ready • Waiting for rider to pick up",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusOpenGreen
                    )
                }
            }

            // RIDER CLAIMED TRACKING CARD
            if (rider != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(customColors.accentTint)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Text(rider.avatarEmoji, fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Claimed by Rider: ${rider.name}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = "${rider.vehiclePlate} • ★ ${rider.rating} • ${order.riderRequest?.status ?: "Assigned"}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Direct Call Rider Icon
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:${rider.phone}")
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(StatusOpenGreen)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Call Rider",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Action Buttons
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(10.dp))

            when (order.status) {
                OrderStatus.PLACED_ORDER -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onReject,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Text("Reject", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Button(
                            onClick = onAccept,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = customColors.accent,
                                contentColor = customColors.onAccent
                            ),
                            modifier = Modifier
                                .weight(1.4f)
                                .height(44.dp)
                                .testTag("card_accept_order_btn")
                        ) {
                            Text("Accept Order", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }
                    }
                }

                OrderStatus.PREPARING -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRequestRider,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Icon(Icons.Default.TwoWheeler, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Request Rider", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = onSetReady,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = customColors.accent,
                                contentColor = customColors.onAccent
                            ),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(44.dp)
                                .testTag("card_set_ready_btn")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Mark Ready", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

                OrderStatus.READY -> {
                    Button(
                        onClick = onPrintReceipt,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = customColors.accent,
                            contentColor = customColors.onAccent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("card_print_receipt_btn")
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Print Receipt", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                OrderStatus.RIDER_ON_THE_WAY, OrderStatus.DELIVERED -> {
                    OutlinedButton(
                        onClick = onPrintReceipt,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View & Print Receipt", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }

                else -> {
                    val reason = order.rejectReason ?: order.cancelReason
                    Text(
                        text = if (reason != null) "Reason: $reason" else "Order Cancelled",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun OrderStatusBadge(status: OrderStatus) {
    val (title, bgColor, textColor) = when (status) {
        OrderStatus.PLACED_ORDER -> Triple("Placed Order", Color(0xFFE0E7FF), Color(0xFF3730A3))
        OrderStatus.PREPARING -> Triple("Preparing", Color(0xFFFEF3C7), Color(0xFF92400E))
        OrderStatus.READY -> Triple("Waiting for rider", Color(0xFFD1FAE5), Color(0xFF065F46))
        OrderStatus.RIDER_ON_THE_WAY -> Triple("Rider is on the way", Color(0xFFDBEAFE), Color(0xFF1E40AF))
        OrderStatus.DELIVERED -> Triple("Delivered", Color(0xFFD1FAE5), Color(0xFF047857))
        OrderStatus.REJECTED -> Triple("Rejected", Color(0xFFFEE2E2), Color(0xFF991B1B))
        OrderStatus.CANCELLED -> Triple("Cancelled", Color(0xFFFEE2E2), Color(0xFF991B1B))
    }

    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = textColor,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
