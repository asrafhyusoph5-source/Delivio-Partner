package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MerchantSettings
import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.ui.theme.LocalDelivioColors
import com.example.ui.theme.StatusClosedRed
import com.example.ui.theme.StatusOpenGreen
import com.example.ui.theme.StatusWarningAmber
import com.example.util.FormatUtils

@Composable
fun DashboardScreen(
    settings: MerchantSettings,
    orders: List<Order>,
    onToggleOpen: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onOrderClick: (Order) -> Unit,
    onViewAllOrders: () -> Unit,
    soldOutItemsCount: Int,
    lowStockCount: Int,
    currentTime: Long = System.currentTimeMillis(),
    isServiceLocked: Boolean = false,
    serviceLockMessage: String? = null,
    modifier: Modifier = Modifier
) {
    val customColors = LocalDelivioColors.current
    val effectiveOpen = com.example.util.BusinessHoursHelper.isEffectiveOpen(settings, currentTime, isServiceLocked)

    val activeOrders = orders.filter {
        it.status == OrderStatus.PLACED_ORDER ||
                it.status == OrderStatus.PREPARING ||
                it.status == OrderStatus.READY ||
                it.status == OrderStatus.RIDER_ON_THE_WAY ||
                it.status == OrderStatus.ON_ANOTHER_DELIVERY
    }

    val deliveredOrders = orders.filter {
        (it.status == OrderStatus.DELIVERED || it.status == OrderStatus.COMPLETED) && !it.serviceType.equals("request_rider", ignoreCase = true)
    }
    val deliveredSubtotal = deliveredOrders.sumOf { it.subtotal }
    val commissionAmount = deliveredSubtotal * settings.commissionRate
    val netEarnings = deliveredSubtotal - commissionAmount

    val requestRiderOrders = orders.filter { it.serviceType.equals("request_rider", ignoreCase = true) }
    val deliveryExpenses = requestRiderOrders.sumOf { it.deliveryFee }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // PROMINENT GLOBAL OPEN/CLOSE TOGGLE CARD
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (effectiveOpen) StatusOpenGreen.copy(alpha = 0.10f) else StatusClosedRed.copy(alpha = 0.10f)
            ),
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dashboard_open_close_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (effectiveOpen) StatusOpenGreen else StatusClosedRed)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isServiceLocked) {
                                        "Closed"
                                    } else if (settings.autoSchedule) {
                                        if (effectiveOpen) "Open" else "Closed"
                                    } else {
                                        if (effectiveOpen) "Open - Accepting Orders" else "Closed - Not Accepting Orders"
                                    },
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (effectiveOpen) StatusOpenGreen else StatusClosedRed
                                )
                                if (settings.autoSchedule && !isServiceLocked) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF8B5CF6).copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "Automatic",
                                            color = Color(0xFF8B5CF6),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            val nextDesc = if (!isServiceLocked && settings.autoSchedule) {
                                com.example.util.BusinessHoursHelper.getNextScheduleChangeDescription(settings, currentTime)
                            } else ""
                            Text(
                                text = if (isServiceLocked) {
                                    serviceLockMessage ?: "Service is disabled by admin in this city. Your store is closed automatically."
                                } else if (nextDesc.isNotBlank()) {
                                    nextDesc
                                } else if (effectiveOpen) {
                                    "Store is currently visible to customers"
                                } else {
                                    "Store is hidden from customer marketplace"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isServiceLocked) StatusClosedRed else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = effectiveOpen,
                        onCheckedChange = { if (!isServiceLocked) onToggleOpen() },
                        enabled = !isServiceLocked,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = StatusOpenGreen,
                            checkedTrackColor = StatusOpenGreen.copy(alpha = 0.3f),
                            uncheckedThumbColor = StatusClosedRed,
                            uncheckedTrackColor = StatusClosedRed.copy(alpha = 0.3f),
                            disabledUncheckedThumbColor = StatusClosedRed.copy(alpha = 0.7f),
                            disabledUncheckedTrackColor = StatusClosedRed.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.testTag("dashboard_open_close_switch")
                    )
                }

                // After closing or when locked, show red banner
                if (!effectiveOpen) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val bannerText = if (isServiceLocked) {
                        serviceLockMessage ?: "Service is disabled by admin in this city. Your store is closed automatically."
                    } else {
                        val closedUntilTimeStr = com.example.util.BusinessHoursHelper.formatClosedUntilTime(settings, currentTime)
                        if (closedUntilTimeStr != null) {
                            "Closed - not receiving new orders until $closedUntilTimeStr or until you reopen"
                        } else {
                            "Closed - not receiving new orders until you reopen"
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = StatusClosedRed.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusClosedRed.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().testTag("dashboard_closed_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isServiceLocked) Icons.Default.Lock else Icons.Default.Block,
                                contentDescription = null,
                                tint = StatusClosedRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = bannerText,
                                fontSize = 12.sp,
                                color = StatusClosedRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Pause Options if Open
                if (effectiveOpen) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (settings.isPaused && settings.pauseUntilTimestamp != null) {
                            Text(
                                text = "Paused: ${FormatUtils.formatRemainingTime(settings.pauseUntilTimestamp)} left",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusWarningAmber
                            )
                            TextButton(onClick = onResumeClick) {
                                Text("Resume Now", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text("Need a break?", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(
                                onClick = onPauseClick,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Pause Kitchen (15-60m)", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // STORE ALERTS & LOW STOCK REMINDERS
        if (soldOutItemsCount > 0 || lowStockCount > 0 || activeOrders.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(customColors.accentTint)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Campaign,
                    contentDescription = null,
                    tint = customColors.accent,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Store Status Reminders",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${activeOrders.size} active orders • $soldOutItemsCount sold-out items • $lowStockCount low-stock items",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // KPI METRICS GRID
        Text(
            text = "Today's Performance",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiCard(
                title = "Total Orders",
                value = (orders.size).toString(),
                subtitle = "${deliveredOrders.size} delivered",
                iconVector = Icons.Default.LocalMall,
                modifier = Modifier.weight(1f)
            )

            KpiCard(
                title = "Net Earnings",
                value = FormatUtils.formatCurrency(netEarnings),
                subtitle = "Subtotal: ${FormatUtils.formatCurrency(deliveredSubtotal)}",
                iconVector = Icons.Default.Payments,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiCard(
                title = "Avg Prep Time",
                value = "${settings.defaultPrepTime} mins",
                subtitle = "Target: <20m",
                iconVector = Icons.Default.Timer,
                modifier = Modifier.weight(1f)
            )

            KpiCard(
                title = "Accept Rate",
                value = "98.4%",
                subtitle = "Store Rating: 4.9★",
                iconVector = Icons.Default.Star,
                modifier = Modifier.weight(1f)
            )
        }

        if (deliveryExpenses > 0.0 || requestRiderOrders.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsBike,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Delivery expenses",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${requestRiderOrders.size} manual rider requests",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = FormatUtils.formatCurrency(deliveryExpenses),
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // SALES REPORTS SECTION
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth().testTag("sales_reports_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val context = androidx.compose.ui.platform.LocalContext.current

                var selectedDateRange by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("Today") }
                var customStartMs by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(currentTime - 7 * 86400000L) }
                var customEndMs by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(currentTime) }
                var showDatePickerModal by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

                val (rangeStartMs, rangeEndMs) = androidx.compose.runtime.remember(selectedDateRange, customStartMs, customEndMs, currentTime) {
                    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone(settings.timezone.ifBlank { "Asia/Manila" })).apply {
                        timeInMillis = currentTime
                    }
                    when (selectedDateRange) {
                        "Today" -> {
                            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                            cal.set(java.util.Calendar.MINUTE, 0)
                            cal.set(java.util.Calendar.SECOND, 0)
                            cal.set(java.util.Calendar.MILLISECOND, 0)
                            val start = cal.timeInMillis
                            cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
                            cal.set(java.util.Calendar.MINUTE, 59)
                            cal.set(java.util.Calendar.SECOND, 59)
                            start to cal.timeInMillis
                        }
                        "7 days" -> {
                            cal.add(java.util.Calendar.DAY_OF_YEAR, -6)
                            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                            cal.set(java.util.Calendar.MINUTE, 0)
                            cal.set(java.util.Calendar.SECOND, 0)
                            val start = cal.timeInMillis
                            start to currentTime
                        }
                        "30 days" -> {
                            cal.add(java.util.Calendar.DAY_OF_YEAR, -29)
                            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                            cal.set(java.util.Calendar.MINUTE, 0)
                            cal.set(java.util.Calendar.SECOND, 0)
                            val start = cal.timeInMillis
                            start to currentTime
                        }
                        else -> {
                            customStartMs to customEndMs
                        }
                    }
                }

                // Filter orders in date range, excluding cancelled/rejected
                val filteredSalesOrders = remember(orders, rangeStartMs, rangeEndMs) {
                    orders.filter { ord ->
                        ord.status != OrderStatus.CANCELLED && ord.status != OrderStatus.REJECTED &&
                        !ord.serviceType.equals("request_rider", ignoreCase = true) &&
                        ord.placedAtTimestamp >= rangeStartMs && ord.placedAtTimestamp <= rangeEndMs
                    }
                }

                // Strictly subtotal sum (NEVER show fees, tip, VAT, delivery fee, or grand total with fees)
                val totalGrossSubtotal = filteredSalesOrders.sumOf { it.subtotal }
                val totalOrdersCount = filteredSalesOrders.size
                val totalItemsCount = filteredSalesOrders.sumOf { it.totalItemCount }
                val avgOrderValue = if (totalOrdersCount > 0) totalGrossSubtotal / totalOrdersCount else 0.0

                // Orders by day breakdown
                val tz = java.util.TimeZone.getTimeZone(settings.timezone.ifBlank { "Asia/Manila" })
                val dayFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = tz }
                val displayDateFormat = java.text.SimpleDateFormat("MMM d (EEE)", java.util.Locale.US).apply { timeZone = tz }

                data class DaySales(val dateStr: String, val displayDate: String, val orderCount: Int, val subtotal: Double)
                val ordersByDay = androidx.compose.runtime.remember(filteredSalesOrders) {
                    filteredSalesOrders.groupBy { dayFormat.format(java.util.Date(it.placedAtTimestamp)) }
                        .map { (dateKey, dayOrders) ->
                            val disp = try {
                                displayDateFormat.format(dayFormat.parse(dateKey) ?: java.util.Date())
                            } catch (_: Exception) { dateKey }
                            DaySales(
                                dateStr = dateKey,
                                displayDate = disp,
                                orderCount = dayOrders.size,
                                subtotal = dayOrders.sumOf { it.subtotal }
                            )
                        }
                        .sortedByDescending { it.dateStr }
                }

                // Top products breakdown
                data class ProductSaleStat(val name: String, val quantity: Int, val subtotal: Double)
                val topProducts = androidx.compose.runtime.remember(filteredSalesOrders) {
                    val map = mutableMapOf<String, Pair<Int, Double>>()
                    filteredSalesOrders.forEach { ord ->
                        ord.items.forEach { itm ->
                            val current = map[itm.name] ?: (0 to 0.0)
                            map[itm.name] = (current.first + itm.quantity) to (current.second + itm.lineTotal)
                        }
                    }
                    map.map { (pName, pair) ->
                        ProductSaleStat(name = pName, quantity = pair.first, subtotal = pair.second)
                    }.sortedByDescending { it.quantity }
                }

                // Header with Export CSV button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Sales Reports",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Product items & subtotal only",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = {
                            try {
                                val reportsDir = java.io.File(context.cacheDir, "reports")
                                if (!reportsDir.exists()) reportsDir.mkdirs()
                                val csvFile = java.io.File(reportsDir, "sales_report_${selectedDateRange.lowercase().replace(' ', '_')}_${System.currentTimeMillis()}.csv")

                                csvFile.bufferedWriter().use { writer ->
                                    writer.appendLine("DELIVIO PARTNER - SALES REPORT")
                                    writer.appendLine("Shop,${settings.storeName}")
                                    writer.appendLine("Period,$selectedDateRange")
                                    writer.appendLine("Generated,${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())}")
                                    writer.appendLine()
                                    writer.appendLine("SUMMARY")
                                    writer.appendLine("Total Orders,$totalOrdersCount")
                                    writer.appendLine("Total Items Sold,$totalItemsCount")
                                    writer.appendLine("Subtotal Sales,${FormatUtils.formatCurrency(totalGrossSubtotal)}")
                                    writer.appendLine("Average Order Value,${FormatUtils.formatCurrency(avgOrderValue)}")
                                    writer.appendLine()
                                    writer.appendLine("ORDERS BY DAY")
                                    writer.appendLine("Date,Order Count,Subtotal")
                                    ordersByDay.forEach { d ->
                                        writer.appendLine("\"${d.displayDate}\",${d.orderCount},${d.subtotal}")
                                    }
                                    writer.appendLine()
                                    writer.appendLine("TOP PRODUCTS")
                                    writer.appendLine("Product Name,Quantity Sold,Subtotal")
                                    topProducts.forEach { p ->
                                        writer.appendLine("\"${p.name.replace("\"", "\"\"")}\",${p.quantity},${p.subtotal}")
                                    }
                                }

                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    csvFile
                                )
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Delivio Sales Report - $selectedDateRange")
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Export Sales Report CSV"))
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Export error: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = customColors.accent,
                            contentColor = customColors.onAccent
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("export_csv_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export CSV", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Date Range Chips: Today, 7 days, 30 days, Custom
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Today", "7 days", "30 days", "Custom").forEach { opt ->
                        FilterChip(
                            selected = selectedDateRange == opt,
                            onClick = {
                                selectedDateRange = opt
                                if (opt == "Custom") {
                                    showDatePickerModal = true
                                }
                            },
                            label = { Text(opt, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = customColors.accentTint,
                                selectedLabelColor = customColors.accent
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (selectedDateRange == "Custom") {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Range: ${FormatUtils.formatDate(customStartMs)} - ${FormatUtils.formatDate(customEndMs)} (tap to change)",
                        style = MaterialTheme.typography.bodySmall,
                        color = customColors.accent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { showDatePickerModal = true }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Totals Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = customColors.accentTint.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Subtotal Sales", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(FormatUtils.formatCurrency(totalGrossSubtotal), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = customColors.accent)
                            Text("$totalOrdersCount orders", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Average Order", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(FormatUtils.formatCurrency(avgOrderValue), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                            Text("$totalItemsCount items sold", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))

                // Orders by Day Table
                Text(
                    text = "Orders by Day (${ordersByDay.size} days)",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (ordersByDay.isEmpty()) {
                    Text(
                        text = "No delivered sales in this period.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ordersByDay.take(7).forEach { day ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(day.displayDate, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("${day.orderCount} orders", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    text = FormatUtils.formatCurrency(day.subtotal),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = customColors.accent
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))

                // Top Products Ranking
                Text(
                    text = "Top Products (${topProducts.size})",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (topProducts.isEmpty()) {
                    Text(
                        text = "No product sales recorded in this period.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        topProducts.take(5).forEachIndexed { idx, prod ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text("#${idx + 1}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = customColors.accent)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(prod.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("${prod.quantity} sold", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Text(
                                    text = FormatUtils.formatCurrency(prod.subtotal),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // Custom Date Range Modal
                if (showDatePickerModal) {
                    var startDaysAgo by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(7) }
                    var endDaysAgo by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }

                    AlertDialog(
                        onDismissRequest = { showDatePickerModal = false },
                        title = { Text("Select Custom Period", fontWeight = FontWeight.Bold) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Choose date range window:", style = MaterialTheme.typography.bodySmall)
                                listOf(
                                    "Past 14 Days" to (14 to 0),
                                    "Past 60 Days" to (60 to 0),
                                    "Past 90 Days" to (90 to 0),
                                    "Previous Month" to (60 to 30)
                                ).forEach { (label, pair) ->
                                    Button(
                                        onClick = {
                                            customStartMs = currentTime - (pair.first * 86400000L)
                                            customEndMs = currentTime - (pair.second * 86400000L)
                                            showDatePickerModal = false
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(label, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showDatePickerModal = false }) { Text("Close") }
                        }
                    )
                }
            }
        }

        // INTERACTIVE ANALYTICS CHARTS (Compose Canvas)
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
                    Column {
                        Text("Orders by Hour Today", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Text("Peak hours: 12:00 PM & 7:00 PM", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Hourly", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = customColors.accent)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Canvas Bar Chart: Orders by hour
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                ) {
                    OrdersByHourChart(accentColor = customColors.accent)
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Revenue Last 7 Days", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Text("Net: ${FormatUtils.formatCurrency(netEarnings)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Daily", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = customColors.accent)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Canvas Bar Chart: 7-day revenue trend
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                ) {
                    Revenue7DaysChart(accentColor = customColors.accent)
                }
            }
        }

        // LIVE ACTIVE ORDERS SECTION
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Live Active Orders (${activeOrders.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            TextButton(onClick = onViewAllOrders) {
                Text("View All", fontWeight = FontWeight.Bold)
            }
        }

        if (activeOrders.isEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Kitchen is all caught up!", fontWeight = FontWeight.Bold)
                    Text("No orders currently waiting or preparing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Live orders from customers will appear here in real time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                activeOrders.take(3).forEach { order ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOrderClick(order) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(order.formattedOrderNumber, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    OrderStatusBadge(status = order.status)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${order.customerName} • ${order.totalItemCount} items (${FormatUtils.formatCurrency(order.subtotal)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KpiCard(
    title: String,
    value: String,
    subtitle: String,
    iconVector: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

@Composable
fun OrdersByHourChart(accentColor: Color) {
    val barValues = listOf(3f, 8f, 16f, 9f, 6f, 19f, 12f)
    val labels = listOf("10AM", "12PM", "2PM", "4PM", "6PM", "8PM", "10PM")
    val maxVal = 20f

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val barWidth = width / (barValues.size * 2)

        barValues.forEachIndexed { index, value ->
            val barHeight = (value / maxVal) * (height - 30f)
            val x = (index * 2 + 0.5f) * barWidth
            val y = height - barHeight - 20f

            // Draw bar
            drawRoundRect(
                color = accentColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(6f, 6f)
            )

            // Draw baseline
            drawLine(
                color = Color.LightGray.copy(alpha = 0.5f),
                start = Offset(0f, height - 20f),
                end = Offset(width, height - 20f),
                strokeWidth = 2f
            )
        }
    }

    // Time labels below
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 92.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        labels.forEach { label ->
            Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun Revenue7DaysChart(accentColor: Color) {
    val revenueValues = listOf(320f, 410f, 290f, 480f, 520f, 610f, 450f)
    val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val maxVal = 700f

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val barWidth = width / (revenueValues.size * 2)

        revenueValues.forEachIndexed { index, value ->
            val barHeight = (value / maxVal) * (height - 30f)
            val x = (index * 2 + 0.5f) * barWidth
            val y = height - barHeight - 20f

            drawRoundRect(
                color = if (index == 5) accentColor else accentColor.copy(alpha = 0.5f),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(6f, 6f)
            )

            drawLine(
                color = Color.LightGray.copy(alpha = 0.5f),
                start = Offset(0f, height - 20f),
                end = Offset(width, height - 20f),
                strokeWidth = 2f
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 92.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        dayLabels.forEach { day ->
            Text(day, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
