package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.data.model.MerchantSettings
import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.ui.theme.LocalDelivioColors
import com.example.ui.theme.StatusOpenGreen
import com.example.util.FormatUtils

@Composable
fun OrderDetailScreen(
    order: Order,
    settings: MerchantSettings,
    onBack: () -> Unit,
    onAccept: (Order) -> Unit,
    onReject: (Order) -> Unit,
    onSetReady: (Order) -> Unit,
    onRequestRider: (Order) -> Unit = {},
    onPrintReceipt: (Order) -> Unit,
    onMarkPickedUp: (Order) -> Unit = {},
    onEditOrder: (Order) -> Unit = {},
    onRequestRiderAgain: (Order) -> Unit = {},
    currentTime: Long = System.currentTimeMillis()
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val customColors = LocalDelivioColors.current

    var showConfirmPickedUpDialog by remember { mutableStateOf(false) }

    if (showConfirmPickedUpDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmPickedUpDialog = false },
            title = { Text("Mark Order as Picked Up?") },
            text = {
                Text("Confirm that customer \"${order.customerName}\" has picked up order ${order.orderNumber}. This will complete the order.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmPickedUpDialog = false
                        onMarkPickedUp(order)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusOpenGreen)
                ) {
                    Text("Confirm Picked Up")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmPickedUpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val assignedRiderName = order.riderName.ifBlank { order.riderRequest?.rider?.name ?: "" }
    val hasRiderAssigned = !order.isPickUp && (assignedRiderName.isNotBlank() || order.riderRequest?.rider != null || order.riderId.isNotBlank())
    val rider = if (!order.isPickUp) order.riderRequest?.rider else null

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("order_detail_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Order ${order.orderNumber}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
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
                        }
                        Text(
                            text = FormatUtils.formatDateTime(order.placedAtTimestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val jsonToCopy = order.rawJson.ifBlank { order.toDebugJson() }
                            val clip = android.content.ClipData.newPlainText("Delivio Order JSON", jsonToCopy)
                            clipboard.setPrimaryClip(clip)
                            android.widget.Toast.makeText(context, "Order data copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Copy order data", modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    OrderStatusBadge(status = order.status, isPickUp = order.isPickUp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        },
        bottomBar = {
            // Sticky bottom action bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (order.status) {
                        OrderStatus.PLACED_ORDER -> {
                            OutlinedButton(
                                onClick = { onReject(order) },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                            ) {
                                Text("Reject", fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { onAccept(order) },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = customColors.accent,
                                    contentColor = customColors.onAccent
                                ),
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(50.dp)
                                    .testTag("detail_accept_btn")
                            ) {
                                Text("Accept Order", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        OrderStatus.PREPARING -> {
                            // "Edit order" button on Preparing (hidden after one edit)
                            if (!order.isEdited) {
                                OutlinedButton(
                                    onClick = { onEditOrder(order) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("detail_edit_order_btn")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Edit order", fontWeight = FontWeight.Bold)
                                }
                            }
                            // Mark Ready works with or without a rider
                            Button(
                                onClick = { onSetReady(order) },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = customColors.accent,
                                    contentColor = customColors.onAccent
                                ),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(50.dp)
                                    .testTag("detail_set_ready_btn")
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Mark Ready", fontWeight = FontWeight.ExtraBold)
                            }
                            if (!order.isPickUp && order.canRequestRiderAgain(currentTime)) {
                                OutlinedButton(
                                    onClick = { onRequestRiderAgain(order) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("detail_request_rider_again_btn")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Retry Rider", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        OrderStatus.READY -> {
                            if (order.isPickUp) {
                                Button(
                                    onClick = { showConfirmPickedUpDialog = true },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = StatusOpenGreen,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .height(50.dp)
                                        .testTag("detail_mark_picked_up_btn")
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Mark as Picked Up", fontWeight = FontWeight.ExtraBold)
                                }
                                OutlinedButton(
                                    onClick = { onPrintReceipt(order) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("detail_print_receipt_btn")
                                ) {
                                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Receipt", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                if (order.canRequestRiderAgain(currentTime)) {
                                    OutlinedButton(
                                        onClick = { onRequestRiderAgain(order) },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1.1f)
                                            .height(50.dp)
                                            .testTag("detail_request_rider_again_btn")
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Retry Rider", fontWeight = FontWeight.Bold)
                                    }
                                }
                                Button(
                                    onClick = { onPrintReceipt(order) },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = customColors.accent,
                                        contentColor = customColors.onAccent
                                    ),
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(50.dp)
                                        .testTag("detail_print_receipt_btn")
                                ) {
                                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Print Receipt", fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                        OrderStatus.RIDER_ON_THE_WAY -> {
                            Button(
                                onClick = { onRequestRider(order) },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = customColors.accent,
                                    contentColor = customColors.onAccent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("View Live Rider Map", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        else -> {
                            Button(
                                onClick = { onPrintReceipt(order) },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = customColors.accentTint,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("View & Print Receipt", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Timeline Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Order Status Progression", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    val statuses = listOf(
                        OrderStatus.PLACED_ORDER,
                        OrderStatus.PREPARING,
                        OrderStatus.READY,
                        OrderStatus.RIDER_ON_THE_WAY,
                        OrderStatus.DELIVERED
                    )

                    val currentIndex = statuses.indexOf(order.status).let { if (it == -1) 0 else it }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        statuses.forEachIndexed { index, st ->
                            val isCompleted = index <= currentIndex
                            val isCurrent = index == currentIndex

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isCurrent -> customColors.accent
                                                isCompleted -> StatusOpenGreen
                                                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isCompleted) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = if (isCurrent) customColors.onAccent else Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = when (st) {
                                        OrderStatus.PLACED_ORDER -> "Placed"
                                        OrderStatus.PREPARING -> "Prep"
                                        OrderStatus.READY -> "Ready"
                                        OrderStatus.RIDER_ON_THE_WAY -> "On Way"
                                        OrderStatus.DELIVERED -> "Delivered"
                                        else -> ""
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCompleted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Customer Contact & Delivery Info Card
            Card(
                shape = RoundedCornerShape(16.dp),
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
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(customColors.accentTint),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("👤", fontSize = 20.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = order.customerName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = order.customerPhone,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Call Customer Button (enabled once order placed)
                        FilledIconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:${order.customerPhone}")
                                }
                                context.startActivity(intent)
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = StatusOpenGreen,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.testTag("call_customer_btn")
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = "Call Customer")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = if (order.isPickUp) Icons.Default.ShoppingBag else Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = customColors.accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            if (order.isPickUp) {
                                Text("Pick Up Order", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text("🛍️ Customer will pick up at store location.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = customColors.accent)
                            } else {
                                Text("Delivery Address", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text(order.deliveryAddress.formatted, style = MaterialTheme.typography.bodyMedium)
                                if (order.deliveryAddress.notes.isNotBlank()) {
                                    Text(
                                        text = "Instructions: ${order.deliveryAddress.notes}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Searching for Rider Card (Delivery only)
            if (!order.isPickUp && order.isSearchingRider) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRequestRider(order) }
                        .testTag("detail_searching_rider_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Searching for rider...",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            order.riderRequest?.requestedAt?.let { t ->
                                Text(
                                    text = FormatUtils.formatTimeAgo(t),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Distance: %.1f km".format(order.riderRequest?.distanceKm ?: 0.0),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Rider Fee: ${FormatUtils.formatCurrency(order.riderRequest?.deliveryFee ?: order.deliveryFee)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (!order.riderRequest?.note.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Note for Rider: ${order.riderRequest!!.note}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (order.canRequestRiderAgain(currentTime)) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { onRequestRiderAgain(order) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = customColors.accent,
                                    contentColor = customColors.onAccent
                                ),
                                modifier = Modifier.fillMaxWidth().height(42.dp).testTag("detail_request_rider_again_btn")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Request rider again", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Rider Contact Info Card (Delivery only; stays visible in Ready tab until Delivered)
            if (!order.isPickUp && hasRiderAssigned) {
                val rPhone = order.riderPhone.ifBlank { rider?.phone ?: "" }
                val currentRiderStatus = order.riderRequest?.status ?: if (order.status == OrderStatus.RIDER_ON_THE_WAY) "On the way" else "Rider assigned"
                val vehicleAndPlate = order.riderVehicle?.ifBlank { null }
                    ?: rider?.vehiclePlate?.ifBlank { null }
                    ?: "Motorcycle"

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(customColors.accentTint),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(rider?.avatarEmoji ?: "🛵", fontSize = 20.sp)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = assignedRiderName.ifBlank { "Delivery Rider" },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = vehicleAndPlate,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Status: $currentRiderStatus",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = customColors.accent
                                    )
                                }
                            }

                            // Rider Call Button
                            if (rPhone.isNotBlank()) {
                                FilledIconButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL).apply {
                                            data = Uri.parse("tel:$rPhone")
                                        }
                                        context.startActivity(intent)
                                    },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = StatusOpenGreen,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.testTag("call_rider_btn")
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = "Call Rider")
                                }
                            }
                        }

                        if (order.canRequestRiderAgain(currentTime)) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { onRequestRiderAgain(order) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Request rider again", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Order Items & Modifiers Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Ordered Items (${order.totalItemCount})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    order.items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${item.quantity}x ${item.name}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (item.selectedSize != null) {
                                    Text("Size: ${item.selectedSize}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                item.modifiers.forEach { mod ->
                                    Text("+ ${mod.name} (${FormatUtils.formatCurrency(mod.price)})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (!item.notes.isNullOrBlank()) {
                                    Text("Note: ${item.notes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                            Text(
                                text = FormatUtils.formatCurrency(item.lineTotal),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    }

                    // If edited, display original items and edit reason
                    if (order.isEdited && order.originalItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFEF3C7).copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Original Order Items (Before Edit)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309)
                                )
                                if (order.editNote != null && order.editNote.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Reason: ${order.editNote}",
                                        fontSize = 11.sp,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                order.originalItems.forEach { orig ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${orig.quantity}x ${orig.name}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = FormatUtils.formatCurrency(orig.lineTotal),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (order.originalTotal != null && order.originalTotal > 0.0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Original Total: ${FormatUtils.formatCurrency(order.originalTotal)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Billing summary
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Items subtotal", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(FormatUtils.formatCurrency(order.subtotal), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }

                    // STRICT RULE: Pick Up orders never show delivery fee or rider tip
                    if (!order.isPickUp) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Delivery fee", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(FormatUtils.formatCurrency(order.deliveryFee), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                        if (order.tip > 0.0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Rider tip", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(FormatUtils.formatCurrency(order.tip), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    if (order.serviceFee > 0.0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Service fee", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(FormatUtils.formatCurrency(order.serviceFee), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }

                    if (order.discount > 0.0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Discount", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                            Text("- ${FormatUtils.formatCurrency(order.discount)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Total", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = FormatUtils.formatCurrency(order.total),
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleMedium,
                            color = customColors.accent
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Payment Method", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(order.effectivePaymentLabel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }

                    if (!order.isPickUp) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Delivery fee goes to the rider.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }
        }
    }
}
