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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.data.model.OrderChatThread
import com.example.ui.components.CustomerChatButton
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
    val (bgColor, textColor, icon, label) = if (order.isPickUp) {
        listOf(
            Color(0xFFD1FAE5),
            Color(0xFF047857),
            Icons.Default.ShoppingBag,
            "Pick Up"
        )
    } else {
        listOf(
            Color(0xFFDBEAFE),
            Color(0xFF1D4ED8),
            Icons.Default.DeliveryDining,
            "Delivery"
        )
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor as Color,
        modifier = modifier.testTag(if (order.isPickUp) "fulfillment_chip_pickup" else "fulfillment_chip_delivery")
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
fun ScheduledChip(
    order: Order,
    modifier: Modifier = Modifier
) {
    val sched = order.scheduledFor ?: return
    if (sched <= 0) return
    val timeStr = order.scheduledTimeFormatted.ifBlank {
        FormatUtils.formatScheduledTime(sched)
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFEDE9FE),
        modifier = modifier.testTag("scheduled_chip_${order.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = Color(0xFF6D28D9),
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Scheduled - $timeStr",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6D28D9)
            )
        }
    }
}

@Composable
fun DeliveryOptionChip(
    deliveryOption: String,
    modifier: Modifier = Modifier
) {
    val norm = when {
        deliveryOption.contains("saver", ignoreCase = true) -> "Saver"
        deliveryOption.contains("standard", ignoreCase = true) -> "Standard"
        else -> "Priority" // missing or default is Priority
    }

    val (bgColor, textColor, icon) = when (norm) {
        "Saver" -> Triple(Color(0xFFDCFCE7), Color(0xFF15803D), Icons.Default.Savings)
        "Standard" -> Triple(Color(0xFFE0F2FE), Color(0xFF0369A1), Icons.AutoMirrored.Filled.DirectionsBike)
        else -> Triple(Color(0xFFFEF3C7), Color(0xFFB45309), Icons.Default.Bolt)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = modifier.testTag("delivery_option_chip_${norm.lowercase()}")
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
                text = norm,
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
    onHandedToRiderClick: (Order) -> Unit = {},
    currentTime: Long = System.currentTimeMillis(),
    orderChats: Map<String, OrderChatThread> = emptyMap(),
    onOpenChat: (Order) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val customColors = LocalDelivioColors.current
    val density = LocalDensity.current
    val navBarBottomDp = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }

    val filterTabs = listOf("New", "Preparing", "Ready", "Completed")
    val fulfillmentOptions = listOf("Delivery", "Pick Up")

    var searchQuery by remember { mutableStateOf("") }
    var orderToPickUp by remember { mutableStateOf<Order?>(null) }
    var orderToCancel by remember { mutableStateOf<Order?>(null) }
    var orderToHandToRider by remember { mutableStateOf<Order?>(null) }

    // Never list orders with isSplitChild; ignore scheduled orders until released
    val activeOrders = remember(orders, currentTime) {
        orders.filter { order ->
            !order.isSplitChild && order.isScheduledDue(currentTime)
        }
    }

    // Counts for summary strip and badges (ignoring hidden orders)
    val newCount = remember(activeOrders) { activeOrders.count { it.status == OrderStatus.PLACED_ORDER } }
    val preparingCount = remember(activeOrders) { activeOrders.count { it.status == OrderStatus.PREPARING } }
    val readyCount = remember(activeOrders) {
        activeOrders.count {
            it.status == OrderStatus.READY || it.status == OrderStatus.RIDER_ON_THE_WAY || it.status == OrderStatus.ON_ANOTHER_DELIVERY
        }
    }

    // Filter orders based on active status tab
    val statusFilteredOrders = remember(activeOrders, activeFilter) {
        activeOrders.filter { order ->
            when (activeFilter) {
                "New" -> order.status == OrderStatus.PLACED_ORDER
                "Preparing" -> order.status == OrderStatus.PREPARING
                "Ready" -> order.status == OrderStatus.READY || order.status == OrderStatus.RIDER_ON_THE_WAY || order.status == OrderStatus.ON_ANOTHER_DELIVERY
                "Completed" -> order.status == OrderStatus.DELIVERED || order.status == OrderStatus.COMPLETED
                "Cancelled" -> order.status == OrderStatus.CANCELLED || order.status == OrderStatus.REJECTED
                else -> true
            }
        }
    }

    // Filter by fulfillment (Delivery or Pick Up)
    fun filterByFulfillment(list: List<Order>, filter: String): List<Order> {
        return when (filter) {
            "Pick Up" -> list.filter { it.isPickUp }
            "Delivery" -> list.filter { !it.isPickUp }
            else -> list
        }
    }

    // Filter by fulfillment, search by order number, and sort oldest first
    val displayFilteredOrders = remember(statusFilteredOrders, fulfillmentFilter, searchQuery) {
        val byFulfillment = filterByFulfillment(statusFilteredOrders, fulfillmentFilter)
        val searched = if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase().removePrefix("#")
            byFulfillment.filter { ord ->
                ord.formattedOrderNumber.lowercase().removePrefix("#").contains(q) ||
                ord.orderNumber.lowercase().contains(q) ||
                ord.customerName.lowercase().contains(q)
            }
        } else {
            byFulfillment
        }
        // Sort oldest first
        searched.sortedBy { ord -> ord.placedAtTimestamp }
    }

    if (orderToPickUp != null) {
        AlertDialog(
            onDismissRequest = { orderToPickUp = null },
            title = { Text("Mark Order as Picked Up?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Confirm that customer \"${orderToPickUp?.customerName}\" has picked up order ${orderToPickUp?.formattedOrderNumber}. This will complete the order.")
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

    if (orderToHandToRider != null) {
        val riderName = orderToHandToRider?.riderName?.ifBlank { "the delivery rider" } ?: "the delivery rider"
        AlertDialog(
            onDismissRequest = { orderToHandToRider = null },
            title = { Text("Handed to Rider?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Confirm that you have handed order ${orderToHandToRider?.formattedOrderNumber} to $riderName.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = orderToHandToRider
                        orderToHandToRider = null
                        target?.let {
                            onHandedToRiderClick(it)
                            onMarkPickedUpClick(it)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = customColors.accent,
                        contentColor = customColors.onAccent
                    )
                ) {
                    Text("Confirm Handover")
                }
            },
            dismissButton = {
                TextButton(onClick = { orderToHandToRider = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (orderToCancel != null) {
        AlertDialog(
            onDismissRequest = { orderToCancel = null },
            title = { Text("Cancel Scheduled Order?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to cancel scheduled order ${orderToCancel?.formattedOrderNumber}?")
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
        // ==============================================================
        // 1. SUMMARY STRIP: New, Preparing, Ready
        // ==============================================================
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag("orders_summary_strip")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SummaryStripItem(
                    label = "New",
                    count = newCount,
                    isSelected = activeFilter == "New",
                    accentColor = Color(0xFF3B82F6),
                    onClick = { onFilterChanged("New") },
                    modifier = Modifier.weight(1f)
                )

                VerticalDivider(
                    modifier = Modifier.height(32.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )

                SummaryStripItem(
                    label = "Preparing",
                    count = preparingCount,
                    isSelected = activeFilter == "Preparing",
                    accentColor = Color(0xFFF59E0B),
                    onClick = { onFilterChanged("Preparing") },
                    modifier = Modifier.weight(1f)
                )

                VerticalDivider(
                    modifier = Modifier.height(32.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )

                SummaryStripItem(
                    label = "Ready",
                    count = readyCount,
                    isSelected = activeFilter == "Ready",
                    accentColor = Color(0xFF10B981),
                    onClick = { onFilterChanged("Ready") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ==============================================================
        // 2. SEARCH BY ORDER NUMBER
        // ==============================================================
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by order #...", fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("orders_search_input")
        )

        // ==============================================================
        // 3. STATUS CHIPS: New, Preparing, Ready, Completed
        // ==============================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filterTabs.forEach { tabName ->
                val count = when (tabName) {
                    "New" -> newCount
                    "Preparing" -> preparingCount
                    "Ready" -> readyCount
                    "Completed" -> activeOrders.count { it.status == OrderStatus.DELIVERED || it.status == OrderStatus.COMPLETED }
                    else -> 0
                }
                val isSelected = activeFilter == tabName

                FilterChip(
                    selected = isSelected,
                    onClick = { onFilterChanged(tabName) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = tabName,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
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

        // ==============================================================
        // 4. FULFILLMENT TOGGLE: Delivery and Pick Up
        // ==============================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            fulfillmentOptions.forEach { opt ->
                val count = when (opt) {
                    "Pick Up" -> statusFilteredOrders.count { it.isPickUp }
                    else -> statusFilteredOrders.count { !it.isPickUp }
                }
                val isSelected = fulfillmentFilter == opt

                FilterChip(
                    selected = isSelected,
                    onClick = { onFulfillmentFilterChanged(opt) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when (opt) {
                                "Pick Up" -> Icon(Icons.Default.ShoppingBag, contentDescription = null, modifier = Modifier.size(14.dp))
                                else -> Icon(Icons.Default.DeliveryDining, contentDescription = null, modifier = Modifier.size(14.dp))
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

        // ==============================================================
        // 5. ORDERS LIST (Oldest first)
        // ==============================================================
        val isListEmpty = displayFilteredOrders.isEmpty()

        if (isListEmpty) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = "Empty orders",
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "No orders match \"$searchQuery\"" else "No $fulfillmentFilter Orders",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "Try checking the order number or clear the search." else "Orders in this stage will appear here automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedButton(
                        onClick = {
                            if (searchQuery.isNotBlank()) searchQuery = ""
                            else onFilterChanged("New")
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("orders_empty_state_action_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (searchQuery.isNotBlank()) "Clear Search" else "View New Orders", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = navBarBottomDp + 96.dp)
            ) {
                items(displayFilteredOrders, key = { it.id }) { order ->
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
                        onHandedToRider = { orderToHandToRider = order },
                        onEditOrder = { onEditOrderClick(order) },
                        onRequestRiderAgain = { onRequestRiderAgainClick(order) },
                        currentTime = currentTime,
                        orderChats = orderChats,
                        onOpenChat = onOpenChat
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryStripItem(
    label: String,
    count: Int,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val customColors = LocalDelivioColors.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) customColors.accentTint else Color.Transparent,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag("summary_strip_${label.lowercase()}")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = "$count",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isSelected) customColors.accent else if (count > 0) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) customColors.accent else MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    onHandedToRider: () -> Unit = {},
    onEditOrder: () -> Unit = {},
    onRequestRiderAgain: () -> Unit = {},
    currentTime: Long = System.currentTimeMillis(),
    orderChats: Map<String, OrderChatThread> = emptyMap(),
    onOpenChat: (Order) -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val customColors = LocalDelivioColors.current
    val rider = order.riderRequest?.rider

    val borderBarColor = when {
        order.isPickUp -> Color(0xFF10B981) // Green
        order.isScheduled -> Color(0xFF8B5CF6) // Purple
        else -> Color(0xFF2563EB) // Blue
    }

    var showItemDetails by remember { mutableStateOf(false) }
    var showOriginalItems by remember { mutableStateOf(false) }

    // Elapsed time calculation: turns amber at >= 15 min, red at >= 30 min
    val elapsedMillis = (currentTime - order.placedAtTimestamp).coerceAtLeast(0L)
    val elapsedMinutes = elapsedMillis / 60000L
    val timeAgoStr = FormatUtils.formatTimeAgo(order.placedAtTimestamp)
    val isWaitingLong = elapsedMinutes >= 15
    val isOverdue = elapsedMinutes >= 30

    // Customer first name
    val customerFirstName = order.customerName.trim().split(" ").firstOrNull()?.ifBlank { "Customer" } ?: "Customer"

    // Item summary string
    val itemSummary = if (order.items.isNotEmpty()) {
        order.items.joinToString(", ") { "${it.quantity}x ${it.name}" }
    } else {
        "No items listed"
    }

    // Payment chip info
    val paymentMethod = order.paymentMethod.lowercase()
    val isCash = paymentMethod.contains("cash") || paymentMethod.contains("cod") || order.paymentMethod.equals("COD", ignoreCase = true)
    val isWallet = paymentMethod.contains("wallet")
    val (paymentBg, paymentColor, paymentLabel) = when {
        order.isPickUp && isCash -> {
            Triple(Color(0xFFFEF3C7), Color(0xFFB45309), "Cash: Pay at pickup")
        }
        isCash -> {
            Triple(Color(0xFFFEF3C7), Color(0xFFB45309), "Cash")
        }
        isWallet -> {
            Triple(Color(0xFFEDE9FE), Color(0xFF6D28D9), "Wallet")
        }
        else -> {
            Triple(Color(0xFFDCFCE7), Color(0xFF15803D), "Online")
        }
    }

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
                // ==============================================================
                // 1. CARD HEADER: #number, time ago (amber >= 15m, red >= 30m), status badge
                // ==============================================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = order.formattedOrderNumber,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Time ago badge
                        if (isOverdue) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFEE2E2),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = timeAgoStr,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFDC2626)
                                    )
                                }
                            }
                        } else if (isWaitingLong) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFEF3C7),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = null,
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = timeAgoStr,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFD97706)
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = timeAgoStr,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }

                        OrderStatusBadge(status = order.status, isPickUp = order.isPickUp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ==============================================================
                // 2. CHIPS ROW: Fulfillment, Scheduled, Payment, Delivery Option
                // ==============================================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FulfillmentChip(order = order)

                    if (order.isScheduled) {
                        ScheduledChip(order = order)
                    }

                    // Payment Chip: Cash, Online, Wallet
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = paymentBg,
                        modifier = Modifier.testTag("payment_chip_${order.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = when (paymentMethod) {
                                    "cash" -> Icons.Default.Payments
                                    "wallet" -> Icons.Default.AccountBalanceWallet
                                    else -> Icons.Default.CreditCard
                                },
                                contentDescription = null,
                                tint = paymentColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = paymentLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = paymentColor
                            )
                        }
                    }

                    // Delivery option chip on Delivery orders
                    if (!order.isPickUp) {
                        DeliveryOptionChip(deliveryOption = order.deliveryOption)
                    }

                    if (order.isEdited) {
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

                    if (order.isTestOrder) {
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

                Spacer(modifier = Modifier.height(10.dp))

                // ==============================================================
                // 3. CUSTOMER INFO: Customer First Name prominent + Phone Dial
                // ==============================================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = customerFirstName,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (order.customerName.isNotBlank() && !order.customerName.equals(customerFirstName, ignoreCase = true)) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "(${order.customerName})",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        if (order.isPickUp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingBag,
                                    contentDescription = null,
                                    tint = Color(0xFF047857),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Customer will pick up at store",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF047857)
                                )
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = order.deliveryAddress.formatted.ifBlank { "Delivery address" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (order.isChatVisible(currentTime)) {
                            val thread = orderChats[order.id]
                            val hasUnread = thread?.isUnreadForMerchant() == true
                            CustomerChatButton(
                                hasUnread = hasUnread,
                                onClick = { onOpenChat(order) },
                                orderId = order.id
                            )
                        }

                        if (order.customerPhone.isNotBlank()) {
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
                    }
                }

                // ==============================================================
                // 4. RIDER STATE ("Waiting for rider", "Rider arriving")
                // ==============================================================
                if (!order.isPickUp) {
                    val assignedRiderName = order.riderName.ifBlank { rider?.name ?: "" }
                    val hasRider = assignedRiderName.isNotBlank() || rider != null || order.riderId.isNotBlank()
                    val isOnAnotherDelivery = order.status == OrderStatus.ON_ANOTHER_DELIVERY ||
                        order.riderRequest?.status?.equals("On another delivery", ignoreCase = true) == true ||
                        order.riderRequest?.status?.equals("on_another_delivery", ignoreCase = true) == true

                    val (riderBadgeBg, riderBadgeColor, riderStateLabel) = when {
                        !hasRider -> {
                            Triple(Color(0xFFEFF6FF), Color(0xFF1D4ED8), "Waiting for rider")
                        }
                        isOnAnotherDelivery -> {
                            Triple(Color(0xFFFEF3C7), Color(0xFFB45309), "Rider is delivering another order first")
                        }
                        order.status == OrderStatus.RIDER_ON_THE_WAY || order.status == OrderStatus.READY -> {
                            Triple(Color(0xFFDCFCE7), Color(0xFF15803D), "Rider arriving${if (assignedRiderName.isNotBlank()) " ($assignedRiderName)" else ""}")
                        }
                        else -> {
                            Triple(Color(0xFFDBEAFE), Color(0xFF1E40AF), "Rider assigned • $assignedRiderName")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = riderBadgeBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, riderBadgeColor.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth().testTag("rider_state_strip_${order.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = if (!hasRider) Icons.Default.Schedule else Icons.AutoMirrored.Filled.DirectionsBike,
                                    contentDescription = null,
                                    tint = riderBadgeColor,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = riderStateLabel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = riderBadgeColor
                                )
                            }

                            val rPhone = order.riderPhone.ifBlank { rider?.phone ?: "" }
                            if (hasRider && rPhone.isNotBlank()) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = "Call Rider",
                                    tint = StatusOpenGreen,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                                data = Uri.parse("tel:$rPhone")
                                            }
                                            context.startActivity(intent)
                                        }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ==============================================================
                // 5. ITEM SUMMARY & TOTAL
                // ==============================================================
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${order.totalItemCount} Items",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                text = "Subtotal: ${FormatUtils.formatCurrency(order.subtotal)}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = customColors.accent
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        val hasVariantsInOrder = order.items.any { it.variantLabel.isNotBlank() }
                        if (!hasVariantsInOrder) {
                            Text(
                                text = itemSummary,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = if (showItemDetails) 100 else 2
                            )
                        } else {
                            val visibleItems = if (showItemDetails) order.items else order.items.take(2)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                visibleItems.forEach { orderItem ->
                                    Text(
                                        text = "${orderItem.quantity}x ${orderItem.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (orderItem.variantLabel.isNotBlank()) {
                                        Text(
                                            text = "${orderItem.variantLabel} x${orderItem.quantity}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        if (order.items.size > 2) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (showItemDetails) "Show less" else "Show details...",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = customColors.accent,
                                modifier = Modifier
                                    .clickable { showItemDetails = !showItemDetails }
                                    .padding(vertical = 2.dp)
                            )
                        }
                    }
                }

                // Prep Countdown Timer if Preparing
                if (order.status == OrderStatus.PREPARING && order.prepEndTime != null) {
                    Spacer(modifier = Modifier.height(8.dp))
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

                // ==============================================================
                // 6. ACTION BUTTONS: One large primary action within thumb reach
                // ==============================================================
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(10.dp))

                if (isUpcomingScheduled) {
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
                            // Large Accept and secondary Reject
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onReject()
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .testTag("card_reject_order_btn")
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Reject", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }

                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onAccept()
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = customColors.accent,
                                        contentColor = customColors.onAccent
                                    ),
                                    modifier = Modifier
                                        .weight(1.8f)
                                        .height(52.dp)
                                        .testTag("card_accept_order_btn")
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Accept Order", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                                }
                            }
                        }

                        OrderStatus.PREPARING -> {
                            // Large Mark Ready and secondary Edit Order
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (!order.isEdited) {
                                    OutlinedButton(
                                        onClick = onEditOrder,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.weight(1f).height(50.dp).testTag("card_edit_order_btn")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Edit order", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    }
                                }

                                Button(
                                    onClick = onSetReady,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = customColors.accent,
                                        contentColor = customColors.onAccent
                                    ),
                                    modifier = Modifier.weight(1.5f).height(50.dp).testTag("card_set_ready_btn")
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Mark Ready", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                                }
                            }
                        }

                        OrderStatus.READY -> {
                            if (order.isPickUp) {
                                // Large Handed to customer (Mark Picked Up) and secondary Receipt
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = onMarkPickedUp,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = StatusOpenGreen,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.weight(1.8f).height(50.dp).testTag("card_mark_picked_up_btn")
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Handed to Customer", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                                    }

                                    OutlinedButton(
                                        onClick = onPrintReceipt,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.weight(1f).height(50.dp).testTag("card_print_receipt_btn")
                                    ) {
                                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Receipt", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    }
                                }
                            } else {
                                // Large Handed to rider and secondary Receipt / Request rider again
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = onHandedToRider,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = customColors.accent,
                                            contentColor = customColors.onAccent
                                        ),
                                        modifier = Modifier.weight(1.8f).height(50.dp).testTag("card_handed_to_rider_btn")
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Handed to Rider", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                                    }

                                    OutlinedButton(
                                        onClick = onPrintReceipt,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.weight(1f).height(50.dp).testTag("card_print_receipt_btn")
                                    ) {
                                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Receipt", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }

                        OrderStatus.RIDER_ON_THE_WAY, OrderStatus.DELIVERED, OrderStatus.COMPLETED -> {
                            OutlinedButton(
                                onClick = onPrintReceipt,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(18.dp))
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
        OrderStatus.ON_ANOTHER_DELIVERY -> Triple("On another delivery", Color(0xFFE0E7FF), Color(0xFF3730A3))
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
