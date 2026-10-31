package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.MenuItem
import com.example.data.model.Order
import com.example.data.model.OrderItem
import com.example.ui.theme.LocalDelivioColors
import com.example.ui.theme.StatusOpenGreen
import com.example.ui.theme.StatusWarningAmber
import com.example.util.FormatUtils

@Composable
fun OrderEditDialog(
    order: Order,
    availableShopItems: List<MenuItem>,
    onConfirm: (Order, List<OrderItem>, String) -> Unit,
    onDismiss: () -> Unit
) {
    val customColors = LocalDelivioColors.current
    val initialItems = remember(order.id) {
        order.items.map { it.copy() }
    }
    val initialQuantities = remember(order.id) {
        order.items.associate { it.id to it.quantity }
    }

    var currentItems by remember(order.id) {
        mutableStateOf(order.items.map { it.copy() })
    }

    val isSingleItemSingleQty = remember(initialItems) {
        initialItems.size == 1 && (initialItems.firstOrNull()?.quantity ?: 1) <= 1
    }

    val hasChanged = remember(currentItems, initialItems) {
        if (currentItems.size != initialItems.size) {
            true
        } else {
            currentItems.indices.any { i ->
                val curr = currentItems[i]
                val init = initialItems[i]
                curr.menuItemId != init.menuItemId ||
                curr.quantity != init.quantity ||
                curr.name != init.name ||
                curr.effectivePrice != init.effectivePrice
            }
        }
    }

    val reasons = listOf(
        "Out of stock",
        "Ingredient unavailable",
        "Customer request",
        "Other with a note"
    )
    var selectedReason by remember { mutableStateOf(reasons.first()) }
    var customNote by remember { mutableStateOf("") }
    var swapTargetItem by remember { mutableStateOf<OrderItem?>(null) }

    // Live calculation of new totals
    val isCash = order.paymentMethod.trim().lowercase().let { it.contains("cod") || it.contains("cash") }
    val isWallet = order.paymentMethod.trim().lowercase().contains("wallet")

    val newSubtotal = currentItems.sumOf { it.effectivePrice * it.quantity }
    val delFee = order.deliveryFee
    val discount = order.discount
    val tip = order.tip

    val feesRaw = order.feesRaw
    val serviceFeeType = (feesRaw["serviceFeeType"] as? String) ?: ""
    val serviceFeeRate = FormatUtils.parseDouble(feesRaw["serviceFeeRate"])
        ?: FormatUtils.parseDouble(feesRaw["serviceFeePercent"]) ?: 0.0

    val newServiceFee = if (serviceFeeType.equals("percent", ignoreCase = true) && serviceFeeRate > 0.0) {
        val rate = if (serviceFeeRate > 1.0) serviceFeeRate / 100.0 else serviceFeeRate
        rate * (newSubtotal + delFee)
    } else {
        order.serviceFee
    }

    val vatPercent = FormatUtils.parseDouble(feesRaw["vatPercent"]) ?: 0.0
    val vatMode = (feesRaw["vatMode"] as? String) ?: "exclusive"
    val vatBase = (feesRaw["vatBase"] as? String) ?: "subtotal"

    val vatBaseAmount = if (vatBase.contains("service", ignoreCase = true)) {
        newSubtotal + newServiceFee
    } else {
        newSubtotal
    }

    val newVat = if (vatPercent > 0.0) {
        val rate = if (vatPercent > 1.0) vatPercent / 100.0 else vatPercent
        if (vatMode.equals("inclusive", ignoreCase = true)) {
            vatBaseAmount - (vatBaseAmount / (1.0 + rate))
        } else {
            vatBaseAmount * rate
        }
    } else {
        FormatUtils.parseDouble(feesRaw["vat"]) ?: 0.0
    }

    val vatToAddToTotal = if (vatMode.equals("exclusive", ignoreCase = true)) newVat else 0.0
    val newTotal = (newSubtotal + delFee + newServiceFee + vatToAddToTotal + tip - discount).coerceAtLeast(0.0)
    val refundAmount = (order.total - newTotal).coerceAtLeast(0.0)

    val finalReasonText = if (selectedReason == "Other with a note") {
        if (customNote.isNotBlank()) "Other: ${customNote.trim()}" else ""
    } else {
        selectedReason
    }

    val canSubmit = currentItems.isNotEmpty() &&
            hasChanged &&
            finalReasonText.isNotBlank() &&
            (selectedReason != "Other with a note" || customNote.isNotBlank())

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Edit Order ${order.orderNumber}",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                        )
                        Text(
                            text = "Adjust quantities or swap unavailable items",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Items Section
                    Text(
                        text = "Order Items (${currentItems.size})",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )

                    currentItems.forEachIndexed { index, item ->
                        val initialQty = initialQuantities[item.id] ?: item.quantity

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "Unit price: ₱${FormatUtils.formatCurrency(item.effectivePrice)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (item.selections.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            item.selections.forEach { sel ->
                                                val priceStr = if (sel.price > 0.0) " (+₱${FormatUtils.formatCurrency(sel.price)})" else ""
                                                Text(
                                                    text = "   + ${sel.choiceName}$priceStr",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    // Line total
                                    Text(
                                        text = "₱${FormatUtils.formatCurrency(item.effectivePrice * item.quantity)}",
                                        fontWeight = FontWeight.ExtraBold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = customColors.accent
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Quantity Stepper (Can only go DOWN)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                if (item.quantity > 1) {
                                                    val updated = currentItems.toMutableList()
                                                    updated[index] = item.copy(quantity = item.quantity - 1)
                                                    currentItems = updated
                                                }
                                            },
                                            enabled = item.quantity > 1,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                                        }

                                        Text(
                                            text = "${item.quantity}",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )

                                        IconButton(
                                            onClick = {
                                                // CANNOT increase quantity above original
                                            },
                                            enabled = false, // Disabled: quantity can only go down
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = "Increase (Disabled)",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                            )
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        // Swap Button
                                        OutlinedButton(
                                            onClick = { swapTargetItem = item },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Swap", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }

                                        // Remove Button
                                        IconButton(
                                            onClick = {
                                                if (currentItems.size > 1) {
                                                    val updated = currentItems.toMutableList()
                                                    updated.removeAt(index)
                                                    currentItems = updated
                                                }
                                            },
                                            enabled = currentItems.size > 1,
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Remove Item",
                                                tint = if (currentItems.size > 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isSingleItemSingleQty) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "This order can't be reduced. Use Reject or Cancel if you can't make it.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    } else if (!hasChanged) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Nothing to change yet. Lower a quantity, remove an item, or swap (one item must remain)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (currentItems.size <= 1) {
                        Text(
                            text = "ℹ️ At least one item must remain in the order. To cancel completely, use Reject or Cancel on the main card.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Reason Section (Required)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Reason for Change (Required)",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            reasons.forEach { r ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedReason = r }
                                        .padding(vertical = 4.dp)
                                ) {
                                    RadioButton(
                                        selected = selectedReason == r,
                                        onClick = { selectedReason = r }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(r, style = MaterialTheme.typography.bodyMedium)
                                }
                            }

                            if (selectedReason == "Other with a note") {
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = customNote,
                                    onValueChange = { customNote = it },
                                    label = { Text("Specify Reason / Note") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // Live Total Preview Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = customColors.accentTint.copy(alpha = 0.35f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, customColors.accent.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Price Adjustment Preview",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Original Total:", style = MaterialTheme.typography.bodySmall)
                                Text("₱${FormatUtils.formatCurrency(order.total)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("New Total:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text("₱${FormatUtils.formatCurrency(newTotal)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.ExtraBold, color = customColors.accent)
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(color = customColors.accent.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(6.dp))

                            if (isWallet) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = StatusOpenGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "₱${FormatUtils.formatCurrency(refundAmount)} will be refunded to the customer's wallet",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = StatusOpenGreen
                                    )
                                }
                            } else if (isCash) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Payments, contentDescription = null, tint = customColors.accent, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Cash to collect becomes ₱${FormatUtils.formatCurrency(newTotal)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = customColors.accent
                                    )
                                }
                            } else {
                                Text(
                                    text = "Payment: ${order.formattedPaymentMethod}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Action Buttons
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            if (canSubmit) {
                                onConfirm(order, currentItems, finalReasonText)
                            }
                        },
                        enabled = canSubmit,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = customColors.accent,
                            contentColor = customColors.onAccent
                        ),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(48.dp)
                            .testTag("confirm_order_edit_btn")
                    ) {
                        Text("Save Changes", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Sub-dialog: Swap item modal
    swapTargetItem?.let { target ->
        val candidateItems = remember(availableShopItems, target) {
            availableShopItems.filter {
                it.inStock && !it.hasSets && it.price <= target.effectivePrice && it.id != target.menuItemId
            }
        }

        AlertDialog(
            onDismissRequest = { swapTargetItem = null },
            title = { Text("Swap '${target.name}'") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Select an available set-less shop item of equal or lower price (max ₱${FormatUtils.formatCurrency(target.effectivePrice)}):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (candidateItems.isEmpty()) {
                        Text(
                            text = "No available items without sets with unit price ≤ ₱${FormatUtils.formatCurrency(target.effectivePrice)} found in shop inventory.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(candidateItems, key = { it.id }) { shopItem ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val idx = currentItems.indexOfFirst { it.id == target.id }
                                            if (idx >= 0) {
                                                val swapped = target.copy(
                                                    menuItemId = shopItem.id,
                                                    name = shopItem.name,
                                                    price = shopItem.price,
                                                    basePrice = shopItem.price,
                                                    unitPrice = shopItem.price,
                                                    selections = emptyList()
                                                )
                                                val updatedList = currentItems.toMutableList()
                                                updatedList[idx] = swapped
                                                currentItems = updatedList
                                            }
                                            swapTargetItem = null
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(shopItem.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(
                                                text = shopItem.categoryName.ifBlank { "Item" },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = "₱${FormatUtils.formatCurrency(shopItem.price)}",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp,
                                            color = customColors.accent
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { swapTargetItem = null }) {
                    Text("Close")
                }
            }
        )
    }
}
