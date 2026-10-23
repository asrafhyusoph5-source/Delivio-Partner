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
fun FulfillmentChip(
    order: Order,
    modifier: Modifier = Modifier
) {
    val customColors = LocalDelivioColors.current
    val (bgColor, textColor, icon) = when {
        order.isPickUp -> Triple(
            customColors.accentTint,
            customColors.accent,
            Icons.Default.ShoppingBag
        )
        order.isScheduled -> Triple(
            StatusWarningAmber.copy(alpha = 0.15f),
            StatusWarningAmber,
            Icons.Default.Schedule
        )
        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Default.DeliveryDining
        )
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = order.fulfillmentChipLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

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
    onCancelRiderRequestClick: (Order) -> Unit = {},
    onRetryRiderRequestClick: (Order) -> Unit = {},
    onMarkPickedUpClick: (Order) -> Unit = {},
    onCancelOrderClick: (Order) -> Unit = {},
    currentTime: Long = System.currentTimeMillis(),
    modifier: Modifier = Modifier
) {
    val customColors = LocalDelivioColors.current
    val filterTabs = listOf("New", "Preparing", "Ready", "Completed", "Cancelled")

    var isUpcomingExpanded by remember { mutableStateOf(true) }
    var orderToPickUp by remember { mutableStateOf<Order?>(null) }
    var orderToCancel by remember { mutableStateOf<Order?>(null) }

    // Placed orders partitioning for New tab
    val allPlacedOrders = remember(orders) {
        orders.filter { it.status == OrderStatus.PLACED_ORDER }
    }
    val upcomingScheduledOrders = remember(allPlacedOrders, currentTime) {
        allPlacedOrders.filter { it.isScheduled && !it.isScheduledDue(currentTime) }
            .sortedBy { it.scheduledFor ?: it.placedAtTimestamp }
    }
    val normalNewOrders = remember(allPlacedOrders, currentTime) {
        allPlacedOrders.filter { !it.isScheduled || it.isScheduledDue(currentTime) }
    }

    // Filter orders based on active tab
    val filteredOrders = remember(orders, activeFilter, currentTime) {
        orders.filter { order ->
            when (activeFilter) {
                "New" -> order.status == OrderStatus.PLACED_ORDER && (!order.isScheduled || order.isScheduledDue(currentTime))
                "Preparing" -> order.status == OrderStatus.PREPARING
                "Ready" -> order.status == OrderStatus.READY || order.status == OrderStatus.RIDER_ON_THE_WAY
                "Completed" -> order.status == OrderStatus.DELIVERED || order.status == OrderStatus.COMPLETED
                "Cancelled" -> order.status == OrderStatus.CANCELLED || order.status == OrderStatus.REJECTED
                else -> true
            }
        }
    }

    if (orderToPickUp != null) {
        AlertDialog(
            onDismissRequest = { orderToPickUp = null },
            title = { Text("Mark Order as Picked Up?") },
            text = {
                Text("Confirm that customer \"${orderToPickUp?.customerName}\" has picked up order ${orderToPickUp?.orderNumber}. This will complete the order.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = orderToPickUp
                        orderToPickUp = null
                        target?.let { onMarkPickedUpClick(it) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusOpenGreen)
                ) {
                    Text("Confirm Picked Up")
                }
            },
            dismissButton = {
                TextButton(onClick = { orderToPickUp = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (orderToCancel != null) {
        AlertDialog(
            onDismissRequest = { orderToCancel = null },
            title = { Text("Cancel Scheduled Order?") },
            text = {
                Text("Are you sure you want to cancel scheduled order ${orderToCancel?.orderNumber}?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = orderToCancel
                        orderToCancel = null
                        target?.let { onCancelOrderClick(it) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Cancel Order")
                }
            },
            dismissButton = {
                TextButton(onClick = { orderToCancel = null }) {
                    Text("Back")
                }
            }
        )
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
                    "New" -> normalNewOrders.size
                    "Preparing" -> orders.count { it.status == OrderStatus.PREPARING }
                    "Ready" -> orders.count { it.status == OrderStatus.READY || it.status == OrderStatus.RIDER_ON_THE_WAY }
                    "Completed" -> orders.count { it.status == OrderStatus.DELIVERED || it.status == OrderStatus.COMPLETED }
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
        val isNewTabEmpty = activeFilter == "New" && normalNewOrders.isEmpty() && upcomingScheduledOrders.isEmpty()
        val isOtherTabEmpty = activeFilter != "New" && filteredOrders.isEmpty()

        if (isNewTabEmpty || isOtherTabEmpty) {
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
                // In New tab, show collapsible upcoming scheduled section at top
                if (activeFilter == "New" && upcomingScheduledOrders.isNotEmpty()) {
                    item(key = "upcoming_scheduled_header") {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = StatusWarningAmber.copy(alpha = 0.12f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StatusWarningAmber.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isUpcomingExpanded = !isUpcomingExpanded }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = StatusWarningAmber,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Upcoming scheduled (${upcomingScheduledOrders.size})",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        color = StatusWarningAmber
                                    )
                                }
                                Icon(
                                    imageVector = if (isUpcomingExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isUpcomingExpanded) "Collapse" else "Expand",
                                    tint = StatusWarningAmber
                                )
                            }
                        }
                    }

                    if (isUpcomingExpanded) {
                        items(upcomingScheduledOrders, key = { "upcoming_${it.id}" }) { order ->
                            OrderCard(
                                order = order,
                                isUpcomingScheduled = true,
                                onClick = { onOrderClick(order) },
                                onAccept = { onAcceptClick(order) },
                                onReject = { onRejectClick(order) },
                                onCancel = { orderToCancel = order },
                                onSetReady = { onSetReadyClick(order) },
                                onRequestRider = { onRequestRiderClick(order) },
                                onPrintReceipt = { onPrintReceiptClick(order) },
                                onCancelRiderRequest = { onCancelRiderRequestClick(order) },
                                onRetryRiderRequest = { onRetryRiderRequestClick(order) },
                                onMarkPickedUp = { orderToPickUp = order }
                            )
                        }

                        if (normalNewOrders.isNotEmpty()) {
                            item(key = "normal_new_divider") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                    Text(
                                        text = " Orders Due Now (${normalNewOrders.size}) ",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                }
                            }
                        }
                    }
                }

                // Render orders for the active filter
                val listToRender = if (activeFilter == "New") normalNewOrders else filteredOrders
                items(listToRender, key = { it.id }) { order ->
                    OrderCard(
                        order = order,
                        isUpcomingScheduled = false,
                        onClick = { onOrderClick(order) },
                        onAccept = { onAcceptClick(order) },
                        onReject = { onRejectClick(order) },
                        onCancel = { orderToCancel = order },
                        onSetReady = { onSetReadyClick(order) },
                        onRequestRider = { onRequestRiderClick(order) },
                        onPrintReceipt = { onPrintReceiptClick(order) },
                        onCancelRiderRequest = { onCancelRiderRequestClick(order) },
                        onRetryRiderRequest = { onRetryRiderRequestClick(order) },
                        onMarkPickedUp = { orderToPickUp = order }
                    )
                }
            }
        }
    }
}

@Composable
fun OrderCard(
    order: Order,
    isUpcomingScheduled: Boolean = false,
    onClick: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onCancel: () -> Unit = {},
    onSetReady: () -> Unit,
    onRequestRider: () -> Unit,
    onPrintReceipt: () -> Unit,
    onCancelRiderRequest: () -> Unit = {},
    onRetryRiderRequest: () -> Unit = {},
    onMarkPickedUp: () -> Unit = {}
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
            // Card Header: Order Number, Fulfillment Chip & Status Badge
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
                    Spacer(modifier = Modifier.width(6.dp))
                    FulfillmentChip(order = order)
                    if (order.serviceType.equals("request_rider", ignoreCase = true)) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = customColors.accentTint
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TwoWheeler,
                                    contentDescription = null,
                                    tint = customColors.accent,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Request a Rider",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = customColors.accent
                                )
                            }
                        }
                    }
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
                    OrderStatusBadge(status = order.status, isPickUp = order.isPickUp)
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
                    if (order.isPickUp) {
                        Text(
                            text = "🛍️ Customer will pick up",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = customColors.accent
                        )
                    } else {
                        Text(
                            text = "📍 ${order.deliveryAddress.formatted}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
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
                            text = FormatUtils.formatCurrency(item.lineTotal),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Order Price Breakdown
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Items subtotal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = FormatUtils.formatCurrency(order.subtotal),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Delivery fee",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = FormatUtils.formatCurrency(order.deliveryFee),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (order.serviceFee > 0.0) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Service fee",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = FormatUtils.formatCurrency(order.serviceFee),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                if (order.discount > 0.0) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Discount",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "- ${FormatUtils.formatCurrency(order.discount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Total (${order.formattedPaymentMethod})",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = FormatUtils.formatCurrency(order.total),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = customColors.accent
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Delivery fee goes to the rider.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
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

            // Searching for rider or Ready banner
            val requestedAt = order.riderRequest?.requestedAt
            val elapsedMs = if (requestedAt != null) System.currentTimeMillis() - requestedAt else 0L
            val elapsedMinutes = elapsedMs / 60000L
            val isOver5MinNoRider = order.isSearchingRider && elapsedMinutes >= 5

            if (!order.isPickUp && order.isSearchingRider) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isOver5MinNoRider) StatusWarningAmber.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isOver5MinNoRider) StatusWarningAmber else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRequestRider() }
                        .testTag("searching_for_rider_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isOver5MinNoRider) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = StatusWarningAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "No rider yet",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        color = StatusWarningAmber
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Searching for rider...",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (requestedAt != null) {
                                Text(
                                    text = FormatUtils.formatTimeAgo(requestedAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (isOver5MinNoRider) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Over 5 minutes passed with no rider yet. You can retry the search or cancel.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onCancelRiderRequest,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.weight(1f).height(38.dp)
                                ) {
                                    Text("Cancel", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Button(
                                    onClick = onRetryRiderRequest,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = customColors.accent,
                                        contentColor = customColors.onAccent
                                    ),
                                    modifier = Modifier.weight(1f).height(38.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Retry", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Distance: %.1f km".format(order.riderRequest?.distanceKm ?: 0.0),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Rider Fee: ${FormatUtils.formatCurrency(order.riderRequest?.deliveryFee ?: order.deliveryFee)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (!order.riderRequest?.note.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Note: ${order.riderRequest!!.note}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onCancelRiderRequest,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                            ) {
                                Text("Cancel Request", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else if (order.status == OrderStatus.READY && !order.isPickUp && rider == null && order.riderName.isBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(StatusOpenGreen.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🍽️", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Food is Ready • Waiting for rider dispatch",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusOpenGreen
                    )
                }
            } else if (order.status == OrderStatus.READY && order.isPickUp) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(StatusOpenGreen.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🛍️", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ready for Pick Up • Customer will pick up",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusOpenGreen
                    )
                }
            }

            // RIDER CLAIMED TRACKING CARD
            val assignedRiderName = order.riderName.ifBlank { rider?.name ?: "" }
            val hasRiderClaimed = !order.isPickUp && (assignedRiderName.isNotBlank() || rider != null)
            val isBeforePickup = order.status != OrderStatus.RIDER_ON_THE_WAY &&
                    order.status != OrderStatus.DELIVERED &&
                    order.status != OrderStatus.COMPLETED &&
                    order.status != OrderStatus.CANCELLED &&
                    order.status != OrderStatus.REJECTED &&
                    !order.riderRequest?.status.equals("Picked up", ignoreCase = true) &&
                    !order.riderRequest?.status.equals("On the way", ignoreCase = true) &&
                    !order.riderRequest?.status.equals("Delivered", ignoreCase = true)

            if (hasRiderClaimed) {
                val rPhone = order.riderPhone.ifBlank { rider?.phone ?: "" }
                val currentRiderStatus = order.riderRequest?.status ?: if (order.status == OrderStatus.RIDER_ON_THE_WAY) "On the way" else "Rider assigned"

                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = customColors.accentTint.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(customColors.accent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = rider?.avatarEmoji ?: "🛵",
                                        fontSize = 20.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = assignedRiderName.ifBlank { "Delivery Rider" },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Status: $currentRiderStatus",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = customColors.accent
                                    )
                                }
                            }

                            if (rPhone.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL).apply {
                                            data = Uri.parse("tel:$rPhone")
                                        }
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
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

                        if (order.riderLocation != null && order.riderLocation.latitude != 0.0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Live rider: %.4f, %.4f".format(order.riderLocation.latitude, order.riderLocation.longitude),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(
                                    onClick = onRequestRider,
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Live Map", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (isBeforePickup) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onCancelRiderRequest,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                            ) {
                                Text("Cancel request", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Action Buttons
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(10.dp))

            if (isUpcomingScheduled) {
                // Upcoming scheduled orders allow Reject and Cancel only
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

                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            } else {
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
                            if (order.canRequestRider) {
                                OutlinedButton(
                                    onClick = onRequestRider,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("card_request_rider_btn")
                                ) {
                                    Icon(Icons.Default.TwoWheeler, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Request a Rider", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                }
                            }

                            Button(
                                onClick = onSetReady,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = customColors.accent,
                                    contentColor = customColors.onAccent
                                ),
                                modifier = Modifier
                                    .weight(if (order.canRequestRider) 1.2f else 1f)
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
                        if (order.isPickUp) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = onMarkPickedUp,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = StatusOpenGreen,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .height(44.dp)
                                        .testTag("card_mark_picked_up_btn")
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Mark as Picked Up", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                }

                                OutlinedButton(
                                    onClick = onPrintReceipt,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("card_print_receipt_btn")
                                ) {
                                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Receipt", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                }
                            }
                        } else if (order.canRequestRider) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = onRequestRider,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = customColors.accent,
                                        contentColor = customColors.onAccent
                                    ),
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(44.dp)
                                        .testTag("card_request_rider_btn")
                                ) {
                                    Icon(Icons.Default.TwoWheeler, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Request a Rider", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                }

                                OutlinedButton(
                                    onClick = onPrintReceipt,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("card_print_receipt_btn")
                                ) {
                                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Print Receipt", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                }
                            }
                        } else {
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
                    }

                    OrderStatus.RIDER_ON_THE_WAY, OrderStatus.DELIVERED, OrderStatus.COMPLETED -> {
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
}

@Composable
fun OrderStatusBadge(status: OrderStatus, isPickUp: Boolean = false) {
    val (title, bgColor, textColor) = when (status) {
        OrderStatus.PLACED_ORDER -> Triple("Placed Order", Color(0xFFE0E7FF), Color(0xFF3730A3))
        OrderStatus.PREPARING -> Triple("Preparing", Color(0xFFFEF3C7), Color(0xFF92400E))
        OrderStatus.READY -> if (isPickUp) Triple("Ready for Pick Up", Color(0xFFD1FAE5), Color(0xFF065F46)) else Triple("Waiting for rider", Color(0xFFD1FAE5), Color(0xFF065F46))
        OrderStatus.RIDER_ON_THE_WAY -> Triple("Rider is on the way", Color(0xFFDBEAFE), Color(0xFF1E40AF))
        OrderStatus.DELIVERED -> Triple("Delivered", Color(0xFFD1FAE5), Color(0xFF047857))
        OrderStatus.COMPLETED -> Triple("Completed", Color(0xFFD1FAE5), Color(0xFF047857))
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
