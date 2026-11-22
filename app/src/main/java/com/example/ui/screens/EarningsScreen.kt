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
import com.example.util.PricingEngine
import androidx.compose.foundation.clickable

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
        (it.status == OrderStatus.DELIVERED || it.status == OrderStatus.COMPLETED) &&
            !it.serviceType.equals("request_rider", ignoreCase = true)
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

    // Per-order Commission Fee using each order's own pricing.merchantCommissionRate (fallback to settings.commissionRate)
    // Net = itemsSubtotal - commissionFee - merchantFundedVouchers
    val orderBreakdowns = filteredDeliveredOrders.map { order ->
        order to PricingEngine.calculateOrderEarnings(order, settings.commissionRate)
    }
    val itemsSubtotal = PricingEngine.round2(orderBreakdowns.sumOf { it.second.itemsSubtotal })
    val totalVouchers = PricingEngine.round2(orderBreakdowns.sumOf { it.second.vouchers })
    val commissionFee = PricingEngine.round2(orderBreakdowns.sumOf { it.second.commissionFee })
    val netEarnings = PricingEngine.round2((itemsSubtotal - commissionFee - totalVouchers).coerceAtLeast(0.0))
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
            modifier = Modifier
                .fillMaxWidth()
                .testTag("earnings_hero_card")
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
                    ),
                    modifier = Modifier.testTag("earnings_net_total")
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Items Subtotal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(FormatUtils.formatCurrency(itemsSubtotal), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Commission Fee", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "- ${FormatUtils.formatCurrency(commissionFee)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("earnings_commission_fee_total")
                        )
                    }
                    if (totalVouchers > 0.0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Merchant Vouchers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "- ${FormatUtils.formatCurrency(totalVouchers)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("earnings_vouchers_total")
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Pending Balance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(FormatUtils.formatCurrency(pendingPayoutBalance), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = StatusOpenGreen)
                    }
                }
            }
        }

        // Payout Account & Action Card
        val hasPayoutAccount = settings.bankName.isNotBlank() ||
            settings.accountNumber.isNotBlank() ||
            settings.accountHolder.isNotBlank()

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("payout_account_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (hasPayoutAccount) {
                    val providerTitle = settings.bankName.ifBlank { "Payout Account" }
                    val maskedNumber = FormatUtils.maskAccountNumber(settings.accountNumber)
                    val holderName = settings.accountHolder.trim()
                    val subtitleParts = listOf(maskedNumber, holderName).filter { it.isNotBlank() }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = providerTitle,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.testTag("payout_account_provider")
                                )
                                if (subtitleParts.isNotEmpty()) {
                                    Text(
                                        text = subtitleParts.joinToString(" • "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.testTag("payout_account_subtitle")
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = onEditBankDetailsClick,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("edit_payout_account_btn")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Payout Account", modifier = Modifier.size(18.dp))
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onEditBankDetailsClick() }
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                            .testTag("add_payout_account_row"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(customColors.accentTint),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    tint = customColors.accent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Add payout account",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Add a bank account or e-wallet for payouts",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Add payout account",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                    shareEarningsReport(context, period, itemsSubtotal, commissionFee, totalVouchers, netEarnings)
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

        // Order Transactions List (regular orders only, never request_rider)
        val regularCandidateOrders = filteredDeliveredOrders.ifEmpty { deliveredOrders }
            .ifEmpty { orders.filter { !it.serviceType.equals("request_rider", ignoreCase = true) } }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            regularCandidateOrders.take(10).forEach { order ->
                val b = PricingEngine.calculateOrderEarnings(order, settings.commissionRate)
                val rateLabel = PricingEngine.formatPercent(b.commissionRate)
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transaction_item_${order.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${order.formattedOrderNumber} • ${order.customerName}",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${FormatUtils.formatTimeAgo(order.placedAtTimestamp)} • ${order.formattedPaymentMethod}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = buildString {
                                    append("Commission Fee ($rateLabel): -${FormatUtils.formatCurrency(b.commissionFee)}")
                                    if (b.vouchers > 0.0) {
                                        append(" • Vouchers: -${FormatUtils.formatCurrency(b.vouchers)}")
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "+ ${FormatUtils.formatCurrency(b.netEarnings)}",
                                fontWeight = FontWeight.ExtraBold,
                                color = StatusOpenGreen,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Subtotal: ${FormatUtils.formatCurrency(b.itemsSubtotal)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Rider fees (delivery fee + service fee) for Delivio Instant requests (request_rider)
        val requestRiderOrders = orders.filter { it.serviceType.equals("request_rider", ignoreCase = true) }
        val totalRiderFees = PricingEngine.round2(requestRiderOrders.sumOf { it.effectiveInstantTotalFee })

        if (requestRiderOrders.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("instant_rider_fees_header"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Rider fees (delivery fee + service fee)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "- ${FormatUtils.formatCurrency(totalRiderFees)}",
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.testTag("instant_rider_fees_total")
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                requestRiderOrders.forEach { rOrder ->
                    val rDelivery = rOrder.pricing?.deliveryFee?.takeIf { it > 0.0 } ?: rOrder.deliveryFee
                    val rService = rOrder.pricing?.serviceFee?.takeIf { it > 0.0 } ?: rOrder.serviceFee
                    val rTotal = rOrder.effectiveInstantTotalFee
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("instant_rider_fee_item_${rOrder.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${rOrder.formattedOrderNumber} • ${rOrder.customerName}",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = customColors.accentTint
                                    ) {
                                        Text(
                                            text = "Instant",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = customColors.accent,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Delivery ${FormatUtils.formatCurrency(rDelivery)} + Service ${FormatUtils.formatCurrency(rService)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "- ${FormatUtils.formatCurrency(rTotal)}",
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
    vouchers: Double,
    net: Double
) {
    val voucherLine = if (vouchers > 0.0) {
        "\n        Merchant Vouchers    : -${FormatUtils.formatCurrency(vouchers)}"
    } else ""
    val reportText = """
        DELIVIO PARTNER - EARNINGS STATEMENT
        Period: $period
        Generated: ${FormatUtils.formatDateTime(System.currentTimeMillis())}
        ========================================
        Items Subtotal       : ${FormatUtils.formatCurrency(subtotal)}
        Commission Fee       : -${FormatUtils.formatCurrency(commission)}$voucherLine
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
