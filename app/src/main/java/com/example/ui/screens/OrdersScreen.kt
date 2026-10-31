package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
    val (bgColor, textColor, icon, label) = when {
        order.isPickUp -> listOf(
            Color(0xFFD1FAE5),
            Color(0xFF047857),
            Icons.Default.ShoppingBag,
            "Pick Up"
        )
        order.isScheduled -> listOf(
            Color(0xFFEDE9FE),
            Color(0xFF6D28D9),
            Icons.Default.Schedule,
            if (order.scheduledForFormatted.isNotBlank()) "Scheduled: ${order.scheduledForFormatted}" else "Scheduled"
        )
        else -> listOf(
            Color(0xFFDBEAFE),
            Color(0xFF1D4ED8),
            Icons.Default.DeliveryDining,
            "Delivery"
        )
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor as Color,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon as androidx.compose.ui.graphics.vector.ImageVector,
                contentDescription = null,
                tint = textColor as Color,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label as String,
                fontSize = 12.sp,
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
    fulfillmentFilter: String = "All",
    onFulfillmentFilterChanged: (String) -> Unit = {},
    onOrderClick: (Order) -> Unit,
    onAcceptClick: (Order) -> Unit,
    onRejectClick: (Order) -> Unit,
    onSetReadyClick: (Order) -> Unit,
    onRequestRiderClick: (Order) -> Unit = {},
    onPrintReceiptClick: (Order) -> Unit,
    onCancelRiderRequestClick: (Order) -> Unit = {},
    onRetryRiderRequestClick: (Order) -> Unit = {},
    onMarkPickedUpClick: (Order) -> Unit = {},
    onCancelOrderClick: (Order) -> Unit = {},
    onEditOrderClick: (Order) -> Unit = {},
    onRequestRiderAgainClick: (Order) -> Unit = {},
    currentTime: Long = System.currentTimeMillis(),
    modifier: Modifier = Modifier
) {
    val customColors = LocalDelivioColors.current
    val filterTabs = listOf("New", "Preparing", "Ready", "Completed", "Cancelled")
    val fulfillmentOptions = listOf("All", "Delivery", "Pick Up", "Scheduled")

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

    // Filter orders based on active status tab
    val statusFilteredOrders = remember(orders, activeFilter, currentTime) {
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

    // Helper to filter by fulfillment
    fun filterByFulfillment(list: List<Order>, filter: String): List<Order> {
        return when (filter) {
            "Delivery" -> list.filter { !it.isPickUp && !it.isScheduled }
            "Pick Up" -> list.filter { it.isPickUp }
            "Scheduled" -> list.filter { it.isScheduled }
            else -> list
        }
    }

    val displayUpcomingScheduled = remember(upcomingScheduledOrders, fulfillmentFilter) {
        filterByFulfillment(upcomingScheduledOrders, fulfillmentFilter)
    }

    val displayNormalNew = remember(normalNewOrders, fulfillmentFilter) {
        filterByFulfillment(normalNewOrders, fulfillmentFilter)
    }

    val displayFilteredOrders = remember(statusFilteredOrders, fulfillmentFilter) {
        filterByFulfillment(statusFilteredOrders, fulfillmentFilter)
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
        // 1. Status Segmented Filter Tabs (Horizontal Scrollable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filterTabs.forEach { tabName ->
                val count = when (tabName) {
                    "New" -> normalNewOrders.size + upcomingScheduledOrders.size
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

        // 2. Fulfillment Filter Chips: "All, Delivery, Pick Up, Scheduled" with counts
        val baseForCounts = if (activeFilter == "New") (normalNewOrders + upcomingScheduledOrders) else statusFilteredOrders
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            fulfillmentOptions.forEach { opt ->
                val count = when (opt) {
                    "All" -> baseForCounts.size
                    "Delivery" -> baseForCounts.count { !it.isPickUp && !it.isScheduled }
                    "Pick Up" -> baseForCounts.count { it.isPickUp }
                    "Scheduled" -> baseForCounts.count { it.isScheduled }
                    else -> 0
                }
                val isSelected = fulfillmentFilter == opt

                FilterChip(
                    selected = isSelected,
                    onClick = { onFulfillmentFilterChanged(opt) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when (opt) {
                                "Delivery" -> Icon(Icons.Default.DeliveryDining, contentDescription = null, modifier = Modifier.size(14.dp))
                                "Pick Up" -> Icon(Icons.Default.ShoppingBag, contentDescription = null, modifier = Modifier.size(14.dp))
                                "Scheduled" -> Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp))
                                else -> Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(opt, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "($count)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) customColors.accent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = customColors.accentTint,
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.testTag("fulfillment_filter_${opt.lowercase().replace(" ", "_")}")
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Orders List
        val isNewTabEmpty = activeFilter == "New" && displayNormalNew.isEmpty() && displayUpcomingScheduled.isEmpty()
        val isOtherTabEmpty = activeFilter != "New" && displayFilteredOrders.isEmpty()

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
                        text = if (fulfillmentFilter != "All") "No $fulfillmentFilter Orders" else "No $activeFilter Orders",
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
                if (activeFilter == "New" && displayUpcomingScheduled.isNotEmpty()) {
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
                                        text = "Upcoming scheduled (${displayUpcomingScheduled.size})",
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
                        items(displayUpcomingScheduled, key = { "upcoming_${it.id}" }) { order ->
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
                                onMarkPickedUp = { orderToPickUp = order },
                                onEditOrder = { onEditOrderClick(order) },
                                onRequestRiderAgain = { onRequestRiderAgainClick(order) },
                                currentTime = currentTime
                            )
                        }

                        if (displayNormalNew.isNotEmpty()) {
                            item(key = "normal_new_divider") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                    Text(
                                        text = " Orders Due Now (${displayNormalNew.size}) ",
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
                val listToRender = if (activeFilter == "New") displayNormalNew else displayFilteredOrders
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
                        onMarkPickedUp = { orderToPickUp = order },
                        onEditOrder = { onEditOrderClick(order) },
                        onRequestRiderAgain = { onRequestRiderAgainClick(order) },
                        currentTime = currentTime
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OrderCard(
    order: Order,
    isUpcomingScheduled: Boolean = false,
    onClick: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onCancel: () -> Unit = {},
    onSetReady: () -> Unit,
    onRequestRider: () -> Unit = {},
    onPrintReceipt: () -> Unit,
    onCancelRiderRequest: () -> Unit = {},
    onRetryRiderRequest: () -> Unit = {},
    onMarkPickedUp: () -> Unit = {},
    onEditOrder: () -> Unit = {},
    onRequestRiderAgain: () -> Unit = {},
    currentTime: Long = System.currentTimeMillis()
) {
    val context = LocalContext.current
    val customColors = LocalDelivioColors.current
    val rider = order.riderRequest?.rider

    val borderBarColor = when {
        order.isPickUp -> Color(0xFF10B981) // Green
        order.isScheduled -> Color(0xFF8B5CF6) // Purple
        else -> Color(0xFF2563EB) // Blue
    }

    var showOriginalItems by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val jsonToCopy = order.rawJson.ifBlank { order.toDebugJson() }
                    val clip = ClipData.newPlainText("Delivio Order JSON", jsonToCopy)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Order data copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            )
            .testTag("order_card_${order.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Colored Left Accent Border Bar
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(6.dp)
                    .background(borderBarColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp)
            ) {
                // Card Header: Order Number, Large Fulfillment Chip, Edited badge, Status Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = order.orderNumber,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        FulfillmentChip(order = order)

                        if (order.isEdited) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFFEF3C7)
                            ) {
                                Text(
                                    text = "Edited",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFB45309),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

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
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF047857)
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

                // Current Items List Summary
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
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                            item.selections.forEach { sel ->
                                val selPriceStr = if (sel.price > 0.0) " (+₱${FormatUtils.formatCurrency(sel.price)})" else ""
                                Text(
                                    text = "   + ${sel.choiceName}$selPriceStr",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // If order was edited, show original items expansion
                    if (order.isEdited && order.originalItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showOriginalItems = !showOriginalItems }
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (showOriginalItems) "Hide original items" else "View original items (${order.originalItems.size} items)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFB45309)
                            )
                            Icon(
                                imageVector = if (showOriginalItems) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = Color(0xFFB45309),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        if (showOriginalItems) {
                            Column(modifier = Modifier.padding(top = 4.dp)) {
                                if (order.editNote != null && order.editNote.isNotBlank()) {
                                    Text(
                                        text = "Reason: ${order.editNote}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                }
                                order.originalItems.forEach { origItem ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${origItem.quantity}x ${origItem.name}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = FormatUtils.formatCurrency(origItem.lineTotal),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (order.originalTotal != null && order.originalTotal > 0.0) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Original Total: ${FormatUtils.formatCurrency(order.originalTotal)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
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

                    // STRICT RULE: Pick Up cards NEVER show delivery fee, tip, or rider message
                    if (!order.isPickUp) {
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

                        if (order.tip > 0.0) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Rider tip",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = FormatUtils.formatCurrency(order.tip),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
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
                            text = "Total",
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
                    // Accurate payment label
                    Text(
                        text = order.effectivePaymentLabel,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (!order.isPickUp) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Delivery fee goes to the rider.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
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

                // RIDER SECTION (Delivery and Scheduled only; NEVER Pick Up)
                if (!order.isPickUp) {
                    val assignedRiderName = order.riderName.ifBlank { rider?.name ?: "" }
                    val hasRiderAccepted = assignedRiderName.isNotBlank() || rider != null || order.riderId.isNotBlank()
                    val canShowRiderSection = order.status == OrderStatus.PREPARING ||
                            order.status == OrderStatus.READY ||
                            order.status == OrderStatus.RIDER_ON_THE_WAY

                    if (canShowRiderSection) {
                        if (!hasRiderAccepted) {
                            // Preparing tab shows "Searching for rider..." while riderId is null
                            val requestedAt = order.riderRequest?.requestedAt
                            val elapsedMs = if (requestedAt != null) currentTime - requestedAt else 0L
                            val elapsedMinutes = elapsedMs / 60000L
                            val isOver5MinNoRider = elapsedMinutes >= 5

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
                                modifier = Modifier.fillMaxWidth().testTag("searching_for_rider_card")
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
                                                    text = "No rider yet (5m+)",
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 13.sp,
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

                                    if (order.canRequestRiderAgain(currentTime)) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = onRequestRiderAgain,
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = customColors.accent,
                                                contentColor = customColors.onAccent
                                            ),
                                            modifier = Modifier.fillMaxWidth().height(36.dp).testTag("card_request_rider_again_btn")
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Request rider again", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        } else {
                            // Rider has accepted: show rider details, vehicle & plate, call icon, live status
                            val rPhone = order.riderPhone.ifBlank { rider?.phone ?: "" }
                            val currentRiderStatus = order.riderRequest?.status ?: if (order.status == OrderStatus.RIDER_ON_THE_WAY) "On the way" else "Rider assigned"
                            val vehicleAndPlate = order.riderVehicle?.ifBlank { null }
                                ?: rider?.vehiclePlate?.ifBlank { null }
                                ?: "Motorcycle"

                            Spacer(modifier = Modifier.height(10.dp))
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = customColors.accentTint.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth().testTag("rider_assigned_card")
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
                                                    text = vehicleAndPlate,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "Status: $currentRiderStatus",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
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

                                    // If rider released or can request again
                                    if (order.canRequestRiderAgain(currentTime)) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedButton(
                                            onClick = onRequestRiderAgain,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth().height(36.dp).testTag("card_request_rider_again_btn")
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Request rider again", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Pick Up Ready Banner
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
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text("Reject", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = onCancel,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.weight(1f).height(44.dp)
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
                                    modifier = Modifier.weight(1f).height(44.dp)
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
                                    modifier = Modifier.weight(1.4f).height(44.dp).testTag("card_accept_order_btn")
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
                                // "Edit order" button on Preparing cards (hidden after one edit)
                                if (!order.isEdited) {
                                    OutlinedButton(
                                        onClick = onEditOrder,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f).height(44.dp).testTag("card_edit_order_btn")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Edit order", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    }
                                }

                                // Mark Ready button (works with or without a rider)
                                Button(
                                    onClick = onSetReady,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = customColors.accent,
                                        contentColor = customColors.onAccent
                                    ),
                                    modifier = Modifier.weight(1.2f).height(44.dp).testTag("card_set_ready_btn")
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
                                        modifier = Modifier.weight(1.4f).height(44.dp).testTag("card_mark_picked_up_btn")
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Mark as Picked Up", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                    }

                                    OutlinedButton(
                                        onClick = onPrintReceipt,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f).height(44.dp).testTag("card_print_receipt_btn")
                                    ) {
                                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Receipt", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (order.canRequestRiderAgain(currentTime)) {
                                        OutlinedButton(
                                            onClick = onRequestRiderAgain,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1.2f).height(44.dp).testTag("card_request_rider_again_btn")
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Request rider again", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }

                                    Button(
                                        onClick = onPrintReceipt,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = customColors.accent,
                                            contentColor = customColors.onAccent
                                        ),
                                        modifier = Modifier.weight(1f).height(44.dp).testTag("card_print_receipt_btn")
                                    ) {
                                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Receipt", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
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
