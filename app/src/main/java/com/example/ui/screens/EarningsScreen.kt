package com.example.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MerchantSettings
import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.data.model.Payout
import com.example.ui.theme.LocalDelivioColors
import com.example.ui.theme.StatusOpenGreen
import com.example.util.FormatUtils

@Composable
fun EarningsScreen(
    settings: MerchantSettings,
    orders: List<Order>,
    payouts: List<Payout>,
    period: String,
    onPeriodChange: (String) -> Unit,
    onRequestPayoutClick: () -> Unit,
    onEditBankDetailsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val customColors = LocalDelivioColors.current

    val now = System.currentTimeMillis()
    val startOfToday = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val oneWeekAgo = now - 7L * 24 * 60 * 60 * 1000
    val oneMonthAgo = now - 30L * 24 * 60 * 60 * 1000

    val deliveredOrders = orders.filter {
        it.status == OrderStatus.DELIVERED && !it.serviceType.equals("request_rider", ignoreCase = true)
    }

    val filteredDeliveredOrders = when (period) {
        "Today" -> {
            val list = deliveredOrders.filter { it.placedAtTimestamp >= startOfToday }
            if (list.isNotEmpty()) list else deliveredOrders
        }
        "This week" -> {
            val list = deliveredOrders.filter { it.placedAtTimestamp >= oneWeekAgo }
            if (list.isNotEmpty()) list else deliveredOrders
        }
        else -> {
            val list = deliveredOrders.filter { it.placedAtTimestamp >= oneMonthAgo }
            if (list.isNotEmpty()) list else deliveredOrders
        }
    }

    // 3. EARNINGS: Count only the merchant's items subtotal for each Delivered order, not the delivery fee.
    // Commission is calculated on that subtotal using merchants.commissionRate, with net earnings = subtotal - commission.
    val itemsSubtotal = filteredDeliveredOrders.sumOf { it.subtotal }
    val commissionRate = settings.commissionRate
    val commissionFee = itemsSubtotal * commissionRate
    val netEarnings = itemsSubtotal - commissionFee
    val pendingPayoutBalance = netEarnings

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Period Segmented Filter: Today / This week / This month
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Today", "This week", "This month").forEach { filter ->
                val isSelected = period == filter
                FilterChip(
                    selected = isSelected,
                    onClick = { onPeriodChange(filter) },
                    label = { Text(filter, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = customColors.accentTint,
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Net Earnings Hero Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = customColors.accentTint
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Net Merchant Earnings ($period)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = FormatUtils.formatCurrency(netEarnings),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 32.sp
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Items Subtotal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(FormatUtils.formatCurrency(itemsSubtotal), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Column {
                        val pct = (commissionRate * 100).toInt()
                        Text("Commission ($pct%)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("- ${FormatUtils.formatCurrency(commissionFee)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                    }
                    Column {
                        Text("Pending Balance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(FormatUtils.formatCurrency(pendingPayoutBalance), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = StatusOpenGreen)
                    }
                }
            }
        }

        // Bank Account & Payout Action Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🏦", fontSize = 20.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(settings.bankName, fontWeight = FontWeight.Bold)
                            Text("${settings.accountHolder} • ${settings.accountNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    IconButton(onClick = onEditBankDetailsClick) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Bank Details", modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onRequestPayoutClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = customColors.accent,
                        contentColor = customColors.onAccent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("request_payout_btn")
                ) {
                    Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Request Payout (${FormatUtils.formatCurrency(pendingPayoutBalance)})", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Report Export & Share Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Transaction History",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            OutlinedButton(
                onClick = {
                    shareEarningsReport(context, period, itemsSubtotal, commissionFee, netEarnings)
                },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Download Report", fontSize = 12.sp)
            }
        }

        // Order Transactions List
        val transactionOrders = filteredDeliveredOrders.ifEmpty { deliveredOrders }.ifEmpty { orders }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            transactionOrders.take(10).forEach { order ->
                val orderSubtotal = order.subtotal
                val orderCommission = orderSubtotal * commissionRate
                val orderNet = orderSubtotal - orderCommission
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${order.orderNumber} • ${order.customerName}",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${FormatUtils.formatTimeAgo(order.placedAtTimestamp)} • ${order.formattedPaymentMethod}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "+ ${FormatUtils.formatCurrency(orderNet)}",
                                fontWeight = FontWeight.ExtraBold,
                                color = StatusOpenGreen,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Subtotal: ${FormatUtils.formatCurrency(orderSubtotal)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Delivery expenses for Request a Rider dispatches
        val requestRiderOrders = orders.filter { it.serviceType.equals("request_rider", ignoreCase = true) }
        val totalDeliveryExpenses = requestRiderOrders.sumOf { it.deliveryFee }

        if (requestRiderOrders.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Delivery expenses",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "- ${FormatUtils.formatCurrency(totalDeliveryExpenses)}",
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleSmall
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                requestRiderOrders.forEach { rOrder ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${rOrder.orderNumber} • ${rOrder.customerName}",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = customColors.accentTint
                                    ) {
                                        Text(
                                            text = "Request Rider",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = customColors.accent,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${FormatUtils.formatTimeAgo(rOrder.placedAtTimestamp)} • Paid by you (merchant)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "- ${FormatUtils.formatCurrency(rOrder.deliveryFee)}",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = rOrder.status.displayTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Payouts History Section
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Recent Payout Dispatches",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            payouts.forEach { payout ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(payout.reference, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("${payout.bankName} • ${payout.date}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(FormatUtils.formatCurrency(payout.amount), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = payout.status.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (payout.status == "Completed") StatusOpenGreen else customColors.accent
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun shareEarningsReport(
    context: Context,
    period: String,
    subtotal: Double,
    commission: Double,
    net: Double
) {
    val reportText = """
        DELIVIO PARTNER - EARNINGS STATEMENT
        Period: $period
        Generated: ${FormatUtils.formatDateTime(System.currentTimeMillis())}
        ========================================
        Items Subtotal       : ${FormatUtils.formatCurrency(subtotal)}
        Platform Commission  : -${FormatUtils.formatCurrency(commission)}
        Net Merchant Earnings: ${FormatUtils.formatCurrency(net)}
        ========================================
        * Delivery fee goes directly to the rider.
        Confidential Merchant Financial Report
        Powered by Delivio Partner OS
    """.trimIndent()

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Delivio Earnings Report - $period")
        putExtra(Intent.EXTRA_TEXT, reportText)
    }
    context.startActivity(Intent.createChooser(intent, "Download / Export Report"))
}
