package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PosPaymentBucket
import com.example.data.model.PosShift
import com.example.data.model.SalesSourceFilter
import com.example.ui.theme.DelivioYellow
import com.example.util.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

val PosBrandBlue = Color(0xFF0284C7)
val PosPositiveGreen = Color(0xFF2E7D32)
val PosNegativeRed = Color(0xFFC62828)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesSourceSegmentedControl(
    selectedSource: SalesSourceFilter,
    onSelectSource: (SalesSourceFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = SalesSourceFilter.values()
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .testTag("reports_source_segmented_control")
    ) {
        options.forEachIndexed { index, option ->
            val tag = when (option) {
                SalesSourceFilter.ALL -> "reports_segment_all"
                SalesSourceFilter.DELIVIO_ONLINE -> "reports_segment_online"
                SalesSourceFilter.IN_STORE_POS -> "reports_segment_pos"
            }
            val selected = selectedSource == option
            SegmentedButton(
                selected = selected,
                onClick = { onSelectSource(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = DelivioYellow,
                    activeContentColor = Color(0xFF1A1A1A),
                    inactiveContainerColor = MaterialTheme.colorScheme.surface,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(tag)
            ) {
                Text(
                    text = option.label,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun OnlineVsPosSplitCard(
    split: OnlineVsPosSplit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("reports_online_vs_pos_split")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Delivio online vs In-store",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = FormatUtils.formatCurrency(split.totalSales),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Two-segment proportion bar
            val onlineFraction = if (split.totalSales > 0.0) (split.onlineNetSales / split.totalSales).toFloat().coerceIn(0f, 1f) else 0.5f
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (split.totalSales > 0.0) {
                    if (onlineFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(onlineFraction.coerceAtLeast(0.01f))
                                .background(DelivioYellow)
                        )
                    }
                    val posFraction = (1f - onlineFraction)
                    if (posFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(posFraction.coerceAtLeast(0.01f))
                                .background(PosBrandBlue)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DelivioYellow.copy(alpha = 0.14f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(DelivioYellow))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delivio online", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${FormatUtils.formatCurrency(split.onlineNetSales)} (${split.onlinePercent}%)",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "${split.onlineOrdersCount} orders",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PosBrandBlue.copy(alpha = 0.12f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(PosBrandBlue))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("In-store (POS)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${FormatUtils.formatCurrency(split.posNetSales)} (${split.posPercent}%)",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "${split.posOrdersCount} orders",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PosSeparateFeesRow(
    tips: Double,
    serviceCharge: Double,
    vatAmount: Double,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = modifier
            .fillMaxWidth()
            .testTag("reports_tips_service_charge_card")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Tips, Service Charge & VAT (shown separately)",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tips", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(FormatUtils.formatCurrency(tips), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Service Charge", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(FormatUtils.formatCurrency(serviceCharge), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("VAT", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(FormatUtils.formatCurrency(vatAmount), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun PosPaymentMethodsCard(
    paymentStats: List<PaymentMethodStat>,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("reports_pos_payment_methods_card")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Sales by Payment Method (POS)",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            val maxAmount = paymentStats.maxOfOrNull { it.amount }?.coerceAtLeast(1.0) ?: 1.0
            paymentStats.forEach { stat ->
                val color = when (stat.bucket) {
                    PosPaymentBucket.CASH -> Color(0xFF16A34A)
                    PosPaymentBucket.GCASH -> Color(0xFF007DFE)
                    PosPaymentBucket.MAYA -> Color(0xFF10B981)
                    PosPaymentBucket.CARD -> Color(0xFF8B5CF6)
                    PosPaymentBucket.OTHER -> Color(0xFF6B7280)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${stat.bucket.label} (${stat.orderCount})",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "${FormatUtils.formatCurrency(stat.amount)} • ${stat.percent}%",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (stat.amount / maxAmount).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = color,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PosOrderTypesCard(
    orderTypeStats: List<OrderTypeStat>,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("reports_pos_order_types_card")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Sales by Order Type (POS)",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            val dineIn = orderTypeStats.getOrNull(0) ?: OrderTypeStat("Dine-In", 0, 0.0, 0)
            val takeOut = orderTypeStats.getOrNull(1) ?: OrderTypeStat("Take-Out", 0, 0.0, 0)
            val totalOrders = (dineIn.ordersCount + takeOut.ordersCount).coerceAtLeast(1)

            LinearProgressIndicator(
                progress = { dineIn.ordersCount.toFloat() / totalOrders },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = PosBrandBlue,
                trackColor = Color(0xFF8B5CF6)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                orderTypeStats.forEachIndexed { idx, stat ->
                    val dotColor = if (idx == 0) PosBrandBlue else Color(0xFF8B5CF6)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(dotColor))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stat.label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(FormatUtils.formatCurrency(stat.salesAmount), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                            Text("${stat.ordersCount} orders (${stat.percent}%)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PosDiscountsByKindCard(
    discounts: List<DiscountKindStat>,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("reports_pos_discounts_card")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Discounts by Kind (POS)",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            if (discounts.isEmpty()) {
                Text(
                    text = "No POS discounts recorded in this period.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                discounts.forEach { d ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(d.kind, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("${d.orderCount} orders", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            text = "-${FormatUtils.formatCurrency(d.amount)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = PosNegativeRed
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PosVoidsAndRefundsCard(
    summary: VoidsAndRefundsSummary,
    tz: TimeZone,
    modifier: Modifier = Modifier
) {
    val timeFmt = remember(tz) { SimpleDateFormat("MMM d, h:mm a", Locale.US).apply { timeZone = tz } }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("reports_pos_voids_refunds_card")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Voids & Refunds (POS Logs)",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PosNegativeRed.copy(alpha = 0.08f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Voids", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = FormatUtils.formatCurrency(summary.voidAmount),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = PosNegativeRed
                        )
                        Text("${summary.voidCount} logged", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PosNegativeRed.copy(alpha = 0.08f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Refunds", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = FormatUtils.formatCurrency(summary.refundAmount),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = PosNegativeRed
                        )
                        Text("${summary.refundCount} logged", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (summary.recentLogs.isEmpty()) {
                Text(
                    text = "No voids or refunds logged in this period.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                summary.recentLogs.take(5).forEach { log ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val badge = if (log.type == "void") "VOID" else "REFUND"
                            val who = listOfNotNull(
                                log.byName.takeIf { it.isNotBlank() },
                                log.approvedBy.takeIf { it.isNotBlank() }?.let { "Approved: $it" }
                            ).joinToString(" • ")
                            Text(
                                text = "$badge${if (log.reason.isNotBlank()) " — ${log.reason}" else ""}",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val whenStr = log.at?.let { timeFmt.format(Date(it)) } ?: ""
                            Text(
                                text = listOf(who, whenStr).filter { it.isNotBlank() }.joinToString(" • "),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = FormatUtils.formatCurrency(log.amount),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = PosNegativeRed
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PosCashierPerformanceCard(
    cashiers: List<CashierPerformanceStat>,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("reports_pos_cashier_card")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Cashier Performance (POS)",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            if (cashiers.isEmpty()) {
                Text(
                    text = "No POS orders recorded by cashiers in this period.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                cashiers.forEach { stat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stat.cashierName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                text = "${stat.ordersCount} orders • Avg ${FormatUtils.formatCurrency(stat.averageOrder)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = FormatUtils.formatCurrency(stat.netSales),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            color = PosBrandBlue
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PosShiftSummariesCard(
    shifts: List<PosShift>,
    tz: TimeZone,
    modifier: Modifier = Modifier
) {
    val dateFmt = remember(tz) { SimpleDateFormat("MMM d, h:mm a", Locale.US).apply { timeZone = tz } }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("reports_pos_shifts_card")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Shift Summaries (POS)",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            if (shifts.isEmpty()) {
                Text(
                    text = "No POS shifts recorded in this period.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                shifts.forEach { shift ->
                    val varianceColor = if (shift.variance >= 0.0) PosPositiveGreen else PosNegativeRed
                    val variancePrefix = if (shift.variance > 0.0) "+" else if (shift.variance < 0.0) "-" else ""
                    val absVariance = kotlin.math.abs(shift.variance)

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = shift.cashierName.ifBlank { "Cashier" },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (shift.isClosed) MaterialTheme.colorScheme.surfaceVariant else DelivioYellow.copy(alpha = 0.25f)
                                ) {
                                    Text(
                                        text = if (shift.isClosed) "Closed" else "Open",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            val openedStr = shift.openedAt?.let { dateFmt.format(Date(it)) } ?: "Unknown"
                            val closedStr = shift.closedAt?.let { dateFmt.format(Date(it)) } ?: "Still open"
                            Text(
                                text = "Opened: $openedStr",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Closed: $closedStr",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Expected: ${FormatUtils.formatCurrency(shift.expectedCash)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (shift.isClosed) {
                                        "Variance: $variancePrefix${FormatUtils.formatCurrency(absVariance)}"
                                    } else {
                                        "Opening: ${FormatUtils.formatCurrency(shift.openingCash)}"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (shift.isClosed) varianceColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StackedHourlyBarChart(
    hours: List<StackedHourData>,
    onlineColor: Color,
    posColor: Color = PosBrandBlue,
    showPosLegend: Boolean = true,
    emptyText: String = "No orders received today yet",
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val maxVal = remember(hours) { (hours.maxOfOrNull { it.totalOrders.toFloat() } ?: 1f).coerceAtLeast(1f) }
    val allZero = remember(hours) { hours.all { it.totalOrders == 0 } }

    if (allZero) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(110.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = emptyText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (showPosLegend) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(onlineColor))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Delivio online", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(posColor))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("In-store (POS)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        selectedIndex?.let { idx ->
            if (idx in hours.indices) {
                val item = hours[idx]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        val text = if (showPosLegend) {
                            "${item.onlineOrders} online • ${item.posOrders} POS (${item.totalOrders} total) at ${item.label}"
                        } else {
                            "${item.totalOrders} ${if (item.totalOrders == 1) "order" else "orders"} at ${item.label}"
                        }
                        Text(
                            text = text,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .pointerInput(hours) {
                    detectTapGestures { offset ->
                        val count = hours.size.coerceAtLeast(1)
                        val slotWidth = size.width / count
                        val tapped = (offset.x / slotWidth).toInt().coerceIn(0, hours.size - 1)
                        selectedIndex = tapped
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val count = hours.size
                if (count == 0) return@Canvas
                val slotWidth = size.width / count
                val barWidth = (slotWidth * 0.7f).coerceAtMost(20.dp.toPx())
                val drawableHeight = size.height - 8.dp.toPx()

                hours.forEachIndexed { i, item ->
                    val x = (i * slotWidth) + ((slotWidth - barWidth) / 2)
                    val total = item.totalOrders
                    if (total == 0) {
                        drawRoundRect(
                            color = onlineColor.copy(alpha = 0.25f),
                            topLeft = Offset(x, size.height - 1.dp.toPx()),
                            size = Size(barWidth, 1.dp.toPx()),
                            cornerRadius = CornerRadius(2f, 2f)
                        )
                    } else {
                        val totalNorm = (total.toFloat() / maxVal) * drawableHeight
                        val totalHeight = totalNorm.coerceAtLeast(4.dp.toPx())
                        val onlineHeight = if (item.onlineOrders > 0) {
                            (item.onlineOrders.toFloat() / total.toFloat()) * totalHeight
                        } else 0f
                        val posHeight = (totalHeight - onlineHeight).coerceAtLeast(0f)
                        val isSelected = selectedIndex == i

                        if (onlineHeight > 0f) {
                            drawRoundRect(
                                color = if (isSelected) Color(0xFF1A1A1A) else onlineColor,
                                topLeft = Offset(x, size.height - onlineHeight),
                                size = Size(barWidth, onlineHeight),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                        }
                        if (posHeight > 0f) {
                            drawRoundRect(
                                color = if (isSelected) Color(0xFF0369A1) else posColor,
                                topLeft = Offset(x, size.height - onlineHeight - posHeight),
                                size = Size(barWidth, posHeight),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                        }
                    }
                }

                drawLine(
                    color = Color.LightGray.copy(alpha = 0.5f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 2f
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            hours.forEachIndexed { idx, item ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (idx % 4 == 0) {
                        Text(
                            text = item.label,
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
