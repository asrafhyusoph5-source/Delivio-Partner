package com.example.ui.screens

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.DelivioYellow
import com.example.util.FormatUtils
import com.example.util.PosAnalyticsEngine
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

enum class ReportPeriod(val title: String) {
    TODAY("Today"),
    LAST_7_DAYS("Last 7 days"),
    LAST_30_DAYS("Last 30 days"),
    PREVIOUS_MONTH("Previous month"),
    CUSTOM("Custom")
}

data class DayData(val dateKey: String, val displayLabel: String, val dayOfWeek: String, val orders: Int, val subtotal: Double)
data class HourData(val hour: Int, val label: String, val orders: Int)
data class ProductStat(val name: String, val quantity: Int, val revenue: Double)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    settings: MerchantSettings,
    orders: List<Order>,
    menuItems: List<MenuItem>,
    reviews: List<CustomerReview>,
    vouchers: List<MerchantVoucher>,
    redemptions: List<PromoRedemption>,
    averageRating: Double,
    reviewsCount: Int,
    currentTime: Long,
    onCheckCustomerPriorDelivered: (String, Long, (Boolean, String?) -> Unit) -> Unit,
    onOpenReviews: () -> Unit,
    onCreateVoucher: () -> Unit,
    onBack: () -> Unit,
    isMultiOutlet: Boolean = false,
    outlets: List<MerchantSettings> = emptyList(),
    selectedOutletIds: Set<String> = emptySet(),
    isCombinedLoading: Boolean = false,
    onOpenOutletsFilter: () -> Unit = {},
    posOrders: List<PosOrder> = emptyList(),
    prevPosOrders: List<PosOrder> = emptyList(),
    posShifts: List<PosShift> = emptyList(),
    posLogs: List<PosLog> = emptyList(),
    isPosLoading: Boolean = false,
    platformTimezone: String = "",
    onPeriodOrRangeChanged: (startMs: Long, endMs: Long, prevStartMs: Long, prevEndMs: Long) -> Unit = { _, _, _, _ -> },
    initialSourceFilter: SalesSourceFilter = SalesSourceFilter.ALL,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    var selectedSource by remember { mutableStateOf(initialSourceFilter) }
    var selectedPeriod by remember { mutableStateOf(ReportPeriod.TODAY) }
    var customStartMs by remember { mutableLongStateOf(currentTime - 14 * 86400000L) }
    var customEndMs by remember { mutableLongStateOf(currentTime) }
    var lastRefreshedTime by remember { mutableLongStateOf(currentTime) }
    var missingIndexLink by remember { mutableStateOf<String?>(null) }

    val tz = remember(platformTimezone, settings.timezone) {
        PosAnalyticsEngine.resolveTimeZone(platformTimezone.ifBlank { settings.timezone })
    }

    // Compute period ranges (current period and preceding comparison period of equal length)
    val (periodRange, prevPeriodRange) = remember(selectedPeriod, customStartMs, customEndMs, currentTime, tz) {
        PosAnalyticsEngine.computePeriodRanges(selectedPeriod, customStartMs, customEndMs, currentTime, tz)
    }

    val (startMs, endMs) = periodRange
    val (prevStartMs, prevEndMs) = prevPeriodRange

    LaunchedEffect(startMs, endMs, prevStartMs, prevEndMs, selectedOutletIds) {
        onPeriodOrRangeChanged(startMs, endMs, prevStartMs, prevEndMs)
    }

    // Filter orders for current period and comparison period (product orders, excluding rider requests and split children)
    val periodOrders = remember(orders, startMs, endMs) {
        orders.filter { ord ->
            !ord.isSplitChild &&
            !ord.serviceType.equals("request_rider", ignoreCase = true) &&
            ord.placedAtTimestamp in startMs..endMs
        }
    }

    val prevPeriodOrders = remember(orders, prevStartMs, prevEndMs) {
        orders.filter { ord ->
            !ord.isSplitChild &&
            !ord.serviceType.equals("request_rider", ignoreCase = true) &&
            ord.placedAtTimestamp in prevStartMs..prevEndMs
        }
    }

    // Delivered or completed sales orders
    val deliveredPeriodOrders = remember(periodOrders) {
        periodOrders.filter { it.isDeliveredOrCompleted }
    }
    val prevDeliveredOrders = remember(prevPeriodOrders) {
        prevPeriodOrders.filter { it.isDeliveredOrCompleted }
    }

    // 1. Sales Metrics (Product subtotals only, never delivery fee, tip, or platform fee)
    val subtotalSales = remember(deliveredPeriodOrders) { deliveredPeriodOrders.sumOf { it.subtotal } }
    val vouchersGiven = remember(deliveredPeriodOrders) { deliveredPeriodOrders.sumOf { it.effectiveMerchantPromoDiscount } }
    val netSales = remember(deliveredPeriodOrders) { deliveredPeriodOrders.sumOf { it.netSales } }
    val ordersCount = deliveredPeriodOrders.size
    val aov = if (ordersCount > 0) netSales / ordersCount else 0.0
    val itemsSold = remember(deliveredPeriodOrders) { deliveredPeriodOrders.sumOf { it.totalItemCount } }

    val prevSubtotalSales = remember(prevDeliveredOrders) { prevDeliveredOrders.sumOf { it.subtotal } }
    val prevVouchersGiven = remember(prevDeliveredOrders) { prevDeliveredOrders.sumOf { it.effectiveMerchantPromoDiscount } }
    val prevNetSales = remember(prevDeliveredOrders) { prevDeliveredOrders.sumOf { it.netSales } }
    val prevOrdersCount = prevDeliveredOrders.size
    val prevAov = if (prevOrdersCount > 0) prevNetSales / prevOrdersCount else 0.0
    val prevItemsSold = remember(prevDeliveredOrders) { prevDeliveredOrders.sumOf { it.totalItemCount } }

    fun computePercentChange(curr: Double, prev: Double): Double? {
        if (prev <= 0.0) return if (curr > 0.0) 100.0 else null
        return ((curr - prev) / prev) * 100.0
    }

    // 2. New vs. Returning Customers state
    val customerHistoricalMap = remember { mutableStateMapOf<String, Boolean>() }

    // Run async checks for distinct customers in this period
    LaunchedEffect(deliveredPeriodOrders, startMs) {
        val distinctCustomerIds = deliveredPeriodOrders.map { it.effectiveCustomerId }.filter { it.isNotBlank() }.distinct()
        distinctCustomerIds.forEach { custId ->
            if (!customerHistoricalMap.containsKey(custId)) {
                onCheckCustomerPriorDelivered(custId, startMs) { hasPrior, link ->
                    customerHistoricalMap[custId] = hasPrior
                    if (link != null) missingIndexLink = link
                }
            }
        }
    }

    // Count new vs returning orders
    val (newCustomerOrdersCount, returningCustomerOrdersCount) = remember(deliveredPeriodOrders, customerHistoricalMap) {
        var newCount = 0
        var retCount = 0
        // Group by customer sorted by order timestamp ascending
        val byCust = deliveredPeriodOrders.groupBy { it.effectiveCustomerId }
        byCust.forEach { (custId, custOrders) ->
            val hasPriorBeforePeriod = customerHistoricalMap[custId] == true
            val sortedCustOrders = custOrders.sortedBy { it.placedAtTimestamp }
            sortedCustOrders.forEachIndexed { index, _ ->
                if (!hasPriorBeforePeriod && index == 0) {
                    newCount++
                } else {
                    retCount++
                }
            }
        }
        newCount to retCount
    }

    val totalCustomerOrders = newCustomerOrdersCount + returningCustomerOrdersCount
    val newPercentage = if (totalCustomerOrders > 0) (newCustomerOrdersCount.toDouble() / totalCustomerOrders * 100).roundToInt() else 0
    val returningPercentage = if (totalCustomerOrders > 0) 100 - newPercentage else 0

    // POS period data (only "paid" or "refunded" orders with paidAt count)
    val countedPosOrders = remember(posOrders, startMs, endMs) {
        PosAnalyticsEngine.filterCountedPosSales(posOrders, startMs, endMs)
    }
    val prevCountedPosOrders = remember(prevPosOrders, prevStartMs, prevEndMs) {
        PosAnalyticsEngine.filterCountedPosSales(prevPosOrders, prevStartMs, prevEndMs)
    }
    val onlineSummary = remember(deliveredPeriodOrders) { PosAnalyticsEngine.computeOnlineSummary(deliveredPeriodOrders) }
    val prevOnlineSummary = remember(prevDeliveredOrders) { PosAnalyticsEngine.computeOnlineSummary(prevDeliveredOrders) }
    val posSummary = remember(countedPosOrders) { PosAnalyticsEngine.computePosSummary(countedPosOrders) }
    val prevPosSummary = remember(prevCountedPosOrders) { PosAnalyticsEngine.computePosSummary(prevCountedPosOrders) }
    val combinedSummary = remember(onlineSummary, posSummary) { PosAnalyticsEngine.combineSummaries(onlineSummary, posSummary) }
    val prevCombinedSummary = remember(prevOnlineSummary, prevPosSummary) { PosAnalyticsEngine.combineSummaries(prevOnlineSummary, prevPosSummary) }
    val onlineVsPosSplit = remember(onlineSummary, posSummary) { PosAnalyticsEngine.computeOnlineVsPosSplit(onlineSummary, posSummary) }

    val activePosSummary = when (selectedSource) {
        SalesSourceFilter.ALL -> combinedSummary
        SalesSourceFilter.IN_STORE_POS -> posSummary
        SalesSourceFilter.DELIVIO_ONLINE -> onlineSummary
    }
    val activePrevSummary = when (selectedSource) {
        SalesSourceFilter.ALL -> prevCombinedSummary
        SalesSourceFilter.IN_STORE_POS -> prevPosSummary
        SalesSourceFilter.DELIVIO_ONLINE -> prevOnlineSummary
    }

    // 3. Orders by Day (Online-only & Combined/POS)
    val dayFmt = remember(tz) { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = tz } }
    val dayDisplayFmt = remember(tz) { SimpleDateFormat("MMM d", Locale.US).apply { timeZone = tz } }
    val dayOfWeekFmt = remember(tz) { SimpleDateFormat("EEEE", Locale.US).apply { timeZone = tz } }

    val ordersByDayList = remember(deliveredPeriodOrders, startMs, endMs, tz) {
        val grouped = deliveredPeriodOrders.groupBy { dayFmt.format(Date(it.placedAtTimestamp)) }
        val list = mutableListOf<DayData>()
        val calIter = Calendar.getInstance(tz).apply { timeInMillis = startMs }
        val calEnd = Calendar.getInstance(tz).apply { timeInMillis = endMs }

        while (!calIter.after(calEnd)) {
            val key = dayFmt.format(calIter.time)
            val disp = dayDisplayFmt.format(calIter.time)
            val dow = dayOfWeekFmt.format(calIter.time)
            val dayOrds = grouped[key] ?: emptyList()
            list.add(
                DayData(
                    dateKey = key,
                    displayLabel = disp,
                    dayOfWeek = dow,
                    orders = dayOrds.size,
                    subtotal = dayOrds.sumOf { it.netSales }
                )
            )
            calIter.add(Calendar.DAY_OF_YEAR, 1)
        }
        list
    }

    val combinedDailyList = remember(selectedSource, deliveredPeriodOrders, countedPosOrders, startMs, endMs, tz) {
        PosAnalyticsEngine.buildDailyBreakdown(
            onlineDelivered = if (selectedSource == SalesSourceFilter.IN_STORE_POS) emptyList() else deliveredPeriodOrders,
            countedPos = if (selectedSource == SalesSourceFilter.DELIVIO_ONLINE) emptyList() else countedPosOrders,
            startMs = startMs,
            endMs = endMs,
            tz = tz
        )
    }
    val activeDayDataList = remember(selectedSource, ordersByDayList, combinedDailyList) {
        if (selectedSource == SalesSourceFilter.DELIVIO_ONLINE) {
            ordersByDayList
        } else {
            combinedDailyList.map { it.toDayData() }
        }
    }

    // Best day of week
    val bestDayOfWeek = remember(activeDayDataList) {
        val byDow = activeDayDataList.groupBy { it.dayOfWeek }
        byDow.maxByOrNull { (_, days) -> days.sumOf { it.subtotal } }?.let { (dow, days) ->
            dow to (days.sumOf { it.subtotal } to days.sumOf { it.orders })
        }
    }

    // 4. Orders by Hour
    val hourlyDataList = remember(periodOrders, tz) {
        val counts = IntArray(24) { 0 }
        val c = Calendar.getInstance(tz)
        periodOrders.forEach { ord ->
            c.timeInMillis = ord.placedAtTimestamp
            val h = c.get(Calendar.HOUR_OF_DAY)
            if (h in 0..23) counts[h]++
        }
        (0..23).map { h ->
            val ampm = if (h >= 12) "PM" else "AM"
            val h12 = when {
                h == 0 -> 12
                h > 12 -> h - 12
                else -> h
            }
            HourData(hour = h, label = "$h12$ampm", orders = counts[h])
        }
    }

    val peakHour = remember(hourlyDataList) {
        hourlyDataList.maxByOrNull { it.orders }?.takeIf { it.orders > 0 }
    }

    val stackedHourlyData = remember(selectedSource, periodOrders, countedPosOrders, tz) {
        PosAnalyticsEngine.buildHourlyStackedData(
            onlineOrders = if (selectedSource == SalesSourceFilter.IN_STORE_POS) emptyList() else periodOrders,
            countedPosOrders = if (selectedSource == SalesSourceFilter.DELIVIO_ONLINE) emptyList() else countedPosOrders,
            tz = tz
        )
    }
    val stackedPeakHour = remember(stackedHourlyData) {
        stackedHourlyData.maxByOrNull { it.totalOrders }?.takeIf { it.totalOrders > 0 }
    }

    // 5. Top Products, Slow Movers & Per-Variant Sales Breakdown (excludes voided POS lines)
    val (topProducts, slowMovers, variantSalesMap) = remember(selectedSource, deliveredPeriodOrders, countedPosOrders, menuItems, settings.businessType) {
        PosAnalyticsEngine.computeProductStats(
            source = selectedSource,
            deliveredOnlineOrders = deliveredPeriodOrders,
            countedPosOrders = countedPosOrders,
            menuItems = menuItems,
            businessType = settings.businessType
        )
    }
    var selectedVariantProduct by remember { mutableStateOf<String?>(null) }

    // POS Analytics Breakdowns
    val posPaymentStats = remember(countedPosOrders) { PosAnalyticsEngine.computePosPaymentBreakdown(countedPosOrders) }
    val posOrderTypeStats = remember(countedPosOrders) { PosAnalyticsEngine.computePosOrderTypeBreakdown(countedPosOrders) }
    val posDiscountsByKind = remember(countedPosOrders) { PosAnalyticsEngine.computePosDiscountsByKind(countedPosOrders) }
    val posVoidsAndRefunds = remember(posLogs, startMs, endMs) { PosAnalyticsEngine.computeVoidsAndRefundsFromLogs(posLogs, startMs, endMs) }
    val posCashierPerf = remember(countedPosOrders) { PosAnalyticsEngine.computeCashierPerformance(countedPosOrders) }
    val posPeriodShifts = remember(posShifts, startMs, endMs) { PosAnalyticsEngine.filterShiftsForPeriod(posShifts, startMs, endMs) }

    // 6. Order Types & Payment Types
    val deliveryOrdersCount = remember(periodOrders) { periodOrders.count { it.fulfillment.contains("delivery", ignoreCase = true) } }
    val pickupOrdersCount = remember(periodOrders) { periodOrders.count { it.fulfillment.contains("pickup", ignoreCase = true) || it.isPickUp } }
    val scheduledOrdersCount = remember(periodOrders) { periodOrders.count { it.scheduledFor != null && it.scheduledFor > 0 } }

    val cashOrdersCount = remember(periodOrders) { periodOrders.count { it.paymentMethod.contains("cash", ignoreCase = true) || it.paymentMethod.contains("cod", ignoreCase = true) } }
    val walletOrdersCount = remember(periodOrders) { periodOrders.count { it.paymentMethod.contains("wallet", ignoreCase = true) } }
    val onlineOrdersCount = remember(periodOrders) { periodOrders.count { !it.paymentMethod.contains("cash", ignoreCase = true) && !it.paymentMethod.contains("cod", ignoreCase = true) && !it.paymentMethod.contains("wallet", ignoreCase = true) } }

    // 7. Service Quality
    val acceptedCount = remember(periodOrders) {
        periodOrders.count {
            it.status == OrderStatus.PREPARING || it.status == OrderStatus.READY ||
            it.status == OrderStatus.RIDER_ON_THE_WAY || it.status == OrderStatus.ON_ANOTHER_DELIVERY ||
            it.status == OrderStatus.DELIVERED || it.status == OrderStatus.COMPLETED ||
            it.acceptedAt != null
        }
    }
    val rejectedCount = remember(periodOrders) { periodOrders.count { it.status == OrderStatus.REJECTED } }
    val expiredCount = remember(periodOrders) {
        periodOrders.count {
            it.status.name.contains("EXPIRED", ignoreCase = true) ||
            it.rejectReason?.contains("expired", ignoreCase = true) == true
        }
    }
    val qualifyingAcceptOrders = acceptedCount + rejectedCount + expiredCount
    val acceptRatePercent = if (qualifyingAcceptOrders > 0) (acceptedCount.toDouble() / qualifyingAcceptOrders * 100).roundToInt() else null

    val prepTimes = remember(periodOrders) { periodOrders.mapNotNull { it.prepDurationMinutes } }
    val avgPrepMinutes = if (prepTimes.isNotEmpty()) prepTimes.average() else null
    val targetPrepTime = settings.defaultPrepTime + 10
    val lateOrdersCount = remember(periodOrders, targetPrepTime) {
        periodOrders.count { (it.prepDurationMinutes ?: 0.0) > targetPrepTime }
    }

    val cancellationsByReason = remember(periodOrders) {
        periodOrders.filter { it.status == OrderStatus.CANCELLED || it.status == OrderStatus.REJECTED }
            .groupBy { it.rejectReason ?: it.cancelReason ?: "Unspecified" }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
    }

    val starDistribution = remember(reviews) {
        val map = mutableMapOf(5 to 0, 4 to 0, 3 to 0, 2 to 0, 1 to 0)
        reviews.forEach { r ->
            val s = r.ratingMerchant.roundToInt().coerceIn(1, 5)
            map[s] = (map[s] ?: 0) + 1
        }
        map
    }

    // 8. Vouchers Performance
    val periodRedemptions = remember(redemptions, startMs, endMs) {
        redemptions.filter { it.redeemedAt in startMs..endMs }
    }
    val vouchersRedeemedCount = periodRedemptions.size
    val vouchersDiscountTotal = periodRedemptions.sumOf { it.discount }
    val ordersWithVoucherCount = periodRedemptions.map { it.orderId }.filter { it.isNotBlank() }.distinct().size
    val topVoucher = remember(periodRedemptions) {
        periodRedemptions.groupBy { it.promoCode }
            .maxByOrNull { (_, list) -> list.sumOf { it.discount } }
            ?.let { (code, list) -> code to (list.size to list.sumOf { it.discount }) }
    }

    // CSV Export Handler (respects active source segment, period, and selected outlets)
    fun exportFullReportCsv() {
        try {
            val reportsDir = File(context.cacheDir, "reports")
            if (!reportsDir.exists()) reportsDir.mkdirs()
            val filename = "delivio_report_${selectedSource.name.lowercase()}_${selectedPeriod.name.lowercase()}_${System.currentTimeMillis()}.csv"
            val csvFile = File(reportsDir, filename)

            val csvContent = PosAnalyticsEngine.buildReportCsv(
                source = selectedSource,
                settings = settings,
                periodTitle = selectedPeriod.title,
                startMs = startMs,
                endMs = endMs,
                currentTime = currentTime,
                tz = tz,
                isMultiOutlet = isMultiOutlet,
                outlets = outlets,
                selectedOutletIds = selectedOutletIds,
                deliveredOnlineOrders = deliveredPeriodOrders,
                prevDeliveredOnlineOrders = prevDeliveredOrders,
                countedPosOrders = countedPosOrders,
                prevCountedPosOrders = prevCountedPosOrders,
                posShifts = posShifts,
                posLogs = posLogs,
                menuItems = menuItems,
                newCustomerOrdersCount = newCustomerOrdersCount,
                returningCustomerOrdersCount = returningCustomerOrdersCount,
                newPercentage = newPercentage,
                returningPercentage = returningPercentage,
                acceptRatePercent = acceptRatePercent,
                acceptedCount = acceptedCount,
                qualifyingAcceptOrders = qualifyingAcceptOrders,
                rejectedCount = rejectedCount,
                expiredCount = expiredCount,
                avgPrepMinutes = avgPrepMinutes,
                targetPrepTime = targetPrepTime,
                lateOrdersCount = lateOrdersCount,
                averageRating = averageRating,
                reviewsCount = reviewsCount,
                deliveryOrdersCount = deliveryOrdersCount,
                pickupOrdersCount = pickupOrdersCount,
                scheduledOrdersCount = scheduledOrdersCount,
                vouchersRedeemedCount = vouchersRedeemedCount,
                vouchersDiscountTotal = vouchersDiscountTotal,
                ordersWithVoucherCount = ordersWithVoucherCount,
                topVoucher = topVoucher
            )
            csvFile.writeText(csvContent, Charsets.UTF_8)

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", csvFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Delivio Report (${selectedSource.label}) - ${selectedPeriod.title}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Export Report CSV"))
        } catch (e: Exception) {
            Toast.makeText(context, "Export error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Reports & Analytics",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(12.dp)
                                    .clickable {
                                        lastRefreshedTime = System.currentTimeMillis()
                                        onPeriodOrRangeChanged(startMs, endMs, prevStartMs, prevEndMs)
                                        Toast.makeText(context, "Reports updated", Toast.LENGTH_SHORT).show()
                                    }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Updated just now",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("reports_back_button")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { exportFullReportCsv() },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = DelivioYellow,
                            contentColor = Color(0xFF1A1A1A)
                        ),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("export_csv_top_button")
                    ) {
                        Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export CSV", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
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
            // Top Source Segmented Control: "All sales" | "Delivio online" | "In-store (POS)"
            SalesSourceSegmentedControl(
                selectedSource = selectedSource,
                onSelectSource = { selectedSource = it }
            )

            if (isMultiOutlet || isPosLoading) {
                val filterText = when {
                    selectedOutletIds.size <= 1 -> "Outlets: This outlet"
                    selectedOutletIds.size >= outlets.size -> "Outlets: All (${outlets.size})"
                    else -> "Outlets: ${selectedOutletIds.size} selected"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (isMultiOutlet) {
                        FilterChip(
                            selected = selectedOutletIds.size > 1,
                            onClick = onOpenOutletsFilter,
                            leadingIcon = {
                                Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = { Text(filterText, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DelivioYellow,
                                selectedLabelColor = Color(0xFF1A1A1A)
                            ),
                            modifier = Modifier.testTag("reports_outlets_filter_chip")
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    if (isCombinedLoading || isPosLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = DelivioYellow)
                    }
                }
            }

            // 1. Timeframe Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReportPeriod.values().forEach { period ->
                    val isSel = selectedPeriod == period
                    FilterChip(
                        selected = isSel,
                        onClick = {
                            if (period == ReportPeriod.CUSTOM) {
                                // Open date picker for custom start
                                val c = Calendar.getInstance(tz)
                                DatePickerDialog(context, { _, y, m, d ->
                                    val startCal = Calendar.getInstance(tz).apply {
                                        set(y, m, d, 0, 0, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }
                                    customStartMs = startCal.timeInMillis
                                    // Then pick end date
                                    DatePickerDialog(context, { _, y2, m2, d2 ->
                                        val endCal = Calendar.getInstance(tz).apply {
                                            set(y2, m2, d2, 23, 59, 59)
                                            set(Calendar.MILLISECOND, 999)
                                        }
                                        customEndMs = endCal.timeInMillis
                                        selectedPeriod = ReportPeriod.CUSTOM
                                    }, y, m, d).show()
                                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                            } else {
                                selectedPeriod = period
                            }
                        },
                        label = {
                            Text(
                                text = period.title,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DelivioYellow,
                            selectedLabelColor = Color(0xFF1A1A1A)
                        ),
                        modifier = Modifier.testTag("period_chip_${period.name.lowercase()}")
                    )
                }
            }

            // Missing Index Warning Banner if encountered
            missingIndexLink?.let { link ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFF3E0),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB74D)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Notice: Firestore composite index is optimizing customer queries in background.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFE65100),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Split Bar for "All sales": Delivio online vs In-store
            if (selectedSource == SalesSourceFilter.ALL) {
                OnlineVsPosSplitCard(split = onlineVsPosSplit)
            }

            // 2. Section: New vs. Returning Customers (Delivio-only; add note for in-store)
            if (selectedSource != SalesSourceFilter.IN_STORE_POS) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth().testTag("new_vs_returning_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "New vs. Returning Customers",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (selectedSource == SalesSourceFilter.ALL) {
                                Text(
                                    text = "Delivio online only • In-store customers are not tracked",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("pos_customers_not_tracked_note")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Donut Chart
                            Box(
                                modifier = Modifier.size(110.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                DonutCustomerChart(
                                    newPercentage = newPercentage.toFloat(),
                                    returningPercentage = returningPercentage.toFloat(),
                                    modifier = Modifier.fillMaxSize()
                                )
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$totalCustomerOrders",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp
                                    )
                                    Text("Orders", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            // Counts and Legend
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(DelivioYellow))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "New Customers: $newCustomerOrdersCount orders ($newPercentage%)",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text("First order with your shop", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color(0xFF8B5CF6)))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Returning Customers: $returningCustomerOrdersCount orders ($returningPercentage%)",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text("Repeat orders from past diners", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Two Advice Cards
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Advice 1: Returning Customers
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Looking for more returning customers?",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Reply to feedback and keep food quality consistent.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedButton(
                                        onClick = onOpenReviews,
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Ratings", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Advice 2: New Customers
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Looking for more new customers?",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Offer an attractive voucher for first-time buyers.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(
                                        onClick = onCreateVoucher,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = Color(0xFF1A1A1A)),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Create voucher", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth().testTag("pos_customers_not_tracked_note")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = PosBrandBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "New vs. Returning Customers: In-store customers are not tracked.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 3. Section: Sales Overview
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth().testTag("sales_overview_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = when (selectedSource) {
                            SalesSourceFilter.ALL -> "Sales Summary (All sales)"
                            SalesSourceFilter.IN_STORE_POS -> "Sales Summary (In-store POS)"
                            SalesSourceFilter.DELIVIO_ONLINE -> "Sales Overview"
                        },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )

                    if (selectedSource == SalesSourceFilter.DELIVIO_ONLINE) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MetricChangeCard(
                                    title = "Subtotal Sales",
                                    value = FormatUtils.formatCurrency(subtotalSales),
                                    changePercent = computePercentChange(subtotalSales, prevSubtotalSales),
                                    modifier = Modifier.weight(1f)
                                )
                                MetricChangeCard(
                                    title = "Vouchers Given",
                                    value = "-₱${FormatUtils.formatCurrency(vouchersGiven)}",
                                    changePercent = computePercentChange(vouchersGiven, prevVouchersGiven),
                                    isNegativeBenefit = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MetricChangeCard(
                                    title = "Net Sales",
                                    value = FormatUtils.formatCurrency(netSales),
                                    changePercent = computePercentChange(netSales, prevNetSales),
                                    isHighlighted = true,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricChangeCard(
                                    title = "Orders",
                                    value = "$ordersCount",
                                    changePercent = computePercentChange(ordersCount.toDouble(), prevOrdersCount.toDouble()),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MetricChangeCard(
                                    title = "Avg Order Value",
                                    value = FormatUtils.formatCurrency(aov),
                                    changePercent = computePercentChange(aov, prevAov),
                                    modifier = Modifier.weight(1f)
                                )
                                MetricChangeCard(
                                    title = "Items Sold",
                                    value = "$itemsSold",
                                    changePercent = computePercentChange(itemsSold.toDouble(), prevItemsSold.toDouble()),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MetricChangeCard(
                                    title = "Item Sales",
                                    value = FormatUtils.formatCurrency(activePosSummary.itemSales),
                                    changePercent = computePercentChange(activePosSummary.itemSales, activePrevSummary.itemSales),
                                    modifier = Modifier.weight(1f)
                                )
                                MetricChangeCard(
                                    title = "Discounts",
                                    value = "-${FormatUtils.formatCurrency(activePosSummary.discounts)}",
                                    changePercent = computePercentChange(activePosSummary.discounts, activePrevSummary.discounts),
                                    isNegativeBenefit = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MetricChangeCard(
                                    title = "Refunds",
                                    value = "-${FormatUtils.formatCurrency(activePosSummary.refunds)}",
                                    changePercent = computePercentChange(activePosSummary.refunds, activePrevSummary.refunds),
                                    isNegativeBenefit = true,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricChangeCard(
                                    title = "Net Sales",
                                    value = FormatUtils.formatCurrency(activePosSummary.netSales),
                                    changePercent = computePercentChange(activePosSummary.netSales, activePrevSummary.netSales),
                                    isHighlighted = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MetricChangeCard(
                                    title = "Orders",
                                    value = "${activePosSummary.ordersCount}",
                                    changePercent = computePercentChange(activePosSummary.ordersCount.toDouble(), activePrevSummary.ordersCount.toDouble()),
                                    modifier = Modifier.weight(1f)
                                )
                                MetricChangeCard(
                                    title = "Average Order",
                                    value = FormatUtils.formatCurrency(activePosSummary.averageOrder),
                                    changePercent = computePercentChange(activePosSummary.averageOrder, activePrevSummary.averageOrder),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            PosSeparateFeesRow(
                                tips = activePosSummary.tips,
                                serviceCharge = activePosSummary.serviceCharge,
                                vatAmount = activePosSummary.vatAmount
                            )
                        }
                    }
                }
            }

            if (isMultiOutlet && selectedOutletIds.size > 1) {
                val breakdown = remember(selectedSource, outlets, selectedOutletIds, deliveredPeriodOrders, countedPosOrders) {
                    outlets.filter { it.merchantId in selectedOutletIds }.map { outlet ->
                        val oDelivered = if (selectedSource == SalesSourceFilter.IN_STORE_POS) emptyList() else deliveredPeriodOrders.filter { it.merchantId == outlet.merchantId }
                        val pCounted = if (selectedSource == SalesSourceFilter.DELIVIO_ONLINE) emptyList() else countedPosOrders.filter { it.shopId == outlet.merchantId }
                        Triple(outlet, oDelivered.size + pCounted.size, oDelivered.sumOf { it.netSales } + pCounted.sumOf { it.netSales })
                    }
                }
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth().testTag("reports_per_outlet_breakdown")
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Per-Outlet Breakdown",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        breakdown.forEach { (outlet, count, net) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(outlet.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(
                                        text = listOfNotNull(
                                            outlet.branchCode.takeIf { it.isNotBlank() },
                                            "$count orders"
                                        ).joinToString(" • "),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = FormatUtils.formatCurrency(net),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF1A1A1A)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Section: Orders by Day
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (selectedSource == SalesSourceFilter.IN_STORE_POS) "Sales by Day (POS)" else "Orders by Day",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )

                    var selectedDayIndex by remember { mutableStateOf<Int?>(null) }
                    selectedDayIndex?.let { idx ->
                        if (idx in activeDayDataList.indices) {
                            val day = activeDayDataList[idx]
                            val comb = combinedDailyList.getOrNull(idx)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DelivioYellow.copy(alpha = 0.2f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val detailText = if (selectedSource == SalesSourceFilter.ALL && comb != null) {
                                    "${day.dayOfWeek}, ${day.displayLabel}: ${comb.onlineOrders} online + ${comb.posOrders} POS (${FormatUtils.formatCurrency(day.subtotal)})"
                                } else {
                                    "${day.dayOfWeek}, ${day.displayLabel}: ${day.orders} orders (${FormatUtils.formatCurrency(day.subtotal)})"
                                }
                                Text(
                                    text = detailText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(8.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    if (activeDayDataList.all { it.orders == 0 }) {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            Text("No orders recorded during this period.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    } else {
                        DailyBarChart(
                            days = activeDayDataList,
                            selectedIndex = selectedDayIndex,
                            onSelectIndex = { selectedDayIndex = it },
                            modifier = Modifier.fillMaxWidth().height(130.dp)
                        )
                    }

                    // Best day of week
                    bestDayOfWeek?.let { (dow, pair) ->
                        if (pair.second > 0) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = DelivioYellow, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Best day of the week: $dow (${pair.second} orders • ${FormatUtils.formatCurrency(pair.first)})",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Section: Hourly Distribution & Peak Hours
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedSource == SalesSourceFilter.IN_STORE_POS) "Sales by Hour (POS)" else "Orders by Hour",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        val activePeak = if (selectedSource == SalesSourceFilter.DELIVIO_ONLINE) {
                            peakHour?.let { it.label to it.orders }
                        } else {
                            stackedPeakHour?.let { it.label to it.totalOrders }
                        }
                        activePeak?.let { (lbl, cnt) ->
                            Text(
                                text = "Peak: $lbl ($cnt orders)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (selectedSource == SalesSourceFilter.DELIVIO_ONLINE) {
                        HourlyBarChart(
                            hours = hourlyDataList,
                            modifier = Modifier.fillMaxWidth().height(130.dp)
                        )
                    } else {
                        StackedHourlyBarChart(
                            hours = stackedHourlyData,
                            onlineColor = DelivioYellow,
                            posColor = PosBrandBlue,
                            showPosLegend = selectedSource == SalesSourceFilter.ALL,
                            emptyText = "No orders recorded in this period"
                        )
                    }
                }
            }

            // 6. Section: Top Products & Slow Movers
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Product Performance",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text("Top Products", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    if (topProducts.isEmpty()) {
                        Text("No items sold yet in this period.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            topProducts.forEachIndexed { idx, item ->
                                val hasVariantSales = variantSalesMap[item.name]?.isNotEmpty() == true
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .then(
                                            if (hasVariantSales) {
                                                Modifier.clickable { selectedVariantProduct = item.name }
                                            } else Modifier
                                        )
                                        .padding(vertical = 4.dp, horizontal = 2.dp)
                                        .testTag("top_product_row_$idx"),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Text("${idx + 1}.", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DelivioYellow)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(item.name, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1)
                                            if (hasVariantSales) {
                                                Text(
                                                    text = "Tap for variants (${variantSalesMap[item.name]?.size ?: 0})",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "${item.quantity} sold • ${FormatUtils.formatCurrency(item.revenue)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Text("Slow Movers (Opportunity to Promote)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    if (slowMovers.isEmpty()) {
                        Text("All items are active.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            slowMovers.forEach { item ->
                                val hasVariantSales = variantSalesMap[item.name]?.isNotEmpty() == true
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (hasVariantSales) Modifier.clickable { selectedVariantProduct = item.name } else Modifier
                                        )
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(item.name, fontWeight = FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                    Text(
                                        text = "${item.quantity} sold",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // POS Sections (shown in In-store POS view, and in All sales when POS activity exists)
            val showPosBreakdownCards = selectedSource == SalesSourceFilter.IN_STORE_POS ||
                (selectedSource == SalesSourceFilter.ALL && (countedPosOrders.isNotEmpty() || posPeriodShifts.isNotEmpty() || posLogs.isNotEmpty()))

            if (showPosBreakdownCards) {
                PosPaymentMethodsCard(paymentStats = posPaymentStats)
                PosOrderTypesCard(orderTypeStats = posOrderTypeStats)
                PosDiscountsByKindCard(discounts = posDiscountsByKind)
                PosVoidsAndRefundsCard(summary = posVoidsAndRefunds, tz = tz)
                PosCashierPerformanceCard(cashiers = posCashierPerf)
                PosShiftSummariesCard(shifts = posPeriodShifts, tz = tz)
            }

            // Delivio Online Fulfillment, Service Quality & Vouchers (shown in Online & All sales)
            if (selectedSource != SalesSourceFilter.IN_STORE_POS) {
                // 7. Section: Order Types & Payment Types
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = if (selectedSource == SalesSourceFilter.ALL) "Online Fulfillment & Payment Methods" else "Fulfillment & Payment Methods",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )

                        // Fulfillment Types Progress Bars
                        val totalFulfill = (deliveryOrdersCount + pickupOrdersCount).coerceAtLeast(1)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Delivery vs. Pick Up", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Delivery: $deliveryOrdersCount (${(deliveryOrdersCount * 100 / totalFulfill)}%) • Pick Up: $pickupOrdersCount", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            LinearProgressIndicator(
                                progress = { deliveryOrdersCount.toFloat() / totalFulfill },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                color = DelivioYellow,
                                trackColor = Color(0xFF8B5CF6)
                            )
                            if (scheduledOrdersCount > 0) {
                                Text("Includes $scheduledOrdersCount scheduled advance orders", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Payment Types Progress Bars
                        val totalPay = (cashOrdersCount + onlineOrdersCount + walletOrdersCount).coerceAtLeast(1)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Payment Methods", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Cash: $cashOrdersCount • Online: $onlineOrdersCount • Wallet: $walletOrdersCount", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            LinearProgressIndicator(
                                progress = { cashOrdersCount.toFloat() / totalPay },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFF4CAF50),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }

                // 8. Section: Service Quality
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Service Quality & Operations",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QualityStatBox(
                                title = "Accept Rate",
                                value = acceptRatePercent?.let { "$it%" } ?: "No data",
                                subtitle = if (qualifyingAcceptOrders > 0) "$acceptedCount of $qualifyingAcceptOrders" else "No orders",
                                modifier = Modifier.weight(1f)
                            )
                            QualityStatBox(
                                title = "Avg Prep Time",
                                value = avgPrepMinutes?.let { "${it.roundToInt()} mins" } ?: "No data",
                                subtitle = "Target: <${targetPrepTime}m",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QualityStatBox(
                                title = "Late Orders",
                                value = "$lateOrdersCount",
                                subtitle = "> Target prep time",
                                modifier = Modifier.weight(1f)
                            )
                            QualityStatBox(
                                title = "Rejected / Expired",
                                value = "$rejectedCount / $expiredCount",
                                subtitle = "Shop reject / timeout",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Star Rating Distribution
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Customer Rating Distribution", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        val totalRev = reviewsCount.coerceAtLeast(1)
                        (5 downTo 1).forEach { stars ->
                            val count = starDistribution[stars] ?: 0
                            val pct = (count.toFloat() / totalRev)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("$stars★", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                                LinearProgressIndicator(
                                    progress = { pct },
                                    modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = DelivioYellow,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("$count", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(24.dp))
                            }
                        }

                        if (cancellationsByReason.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Cancellations by Reason", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            cancellationsByReason.take(4).forEach { (reason, count) ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(reason, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("$count", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 9. Section: Vouchers Performance
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Vouchers & Promotions",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QualityStatBox(
                                title = "Total Redemptions",
                                value = "$vouchersRedeemedCount",
                                subtitle = "$ordersWithVoucherCount orders",
                                modifier = Modifier.weight(1f)
                            )
                            QualityStatBox(
                                title = "Discount Subsidized",
                                value = "₱${FormatUtils.formatCurrency(vouchersDiscountTotal)}",
                                subtitle = "Paid by merchant",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        topVoucher?.let { (code, pair) ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = DelivioYellow.copy(alpha = 0.2f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ConfirmationNumber, contentDescription = null, tint = Color(0xFF1A1A1A), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Top Voucher: $code", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    Text("${pair.first} uses • ₱${FormatUtils.formatCurrency(pair.second)}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 10. Footer Disclaimer
            val disclaimerText = when (selectedSource) {
                SalesSourceFilter.DELIVIO_ONLINE ->
                    "Reports show product subtotals only. Delivery fees, tips, and platform fees are excluded."
                SalesSourceFilter.IN_STORE_POS ->
                    "In-store sales come from Delivio POS and are not subject to Delivio fees."
                SalesSourceFilter.ALL ->
                    "Reports show product subtotals only (delivery fees and platform fees excluded). In-store sales come from Delivio POS and are not subject to Delivio fees."
            }
            Text(
                text = disclaimerText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("reports_footer_disclaimer")
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    selectedVariantProduct?.let { prodName ->
        val breakdown = remember(prodName, variantSalesMap) {
            variantSalesMap[prodName].orEmpty().entries
                .sortedByDescending { it.value.first }
        }
        AlertDialog(
            onDismissRequest = { selectedVariantProduct = null },
            modifier = Modifier.testTag("reports_variant_breakdown_dialog"),
            title = {
                Column {
                    Text("Variant Breakdown", fontWeight = FontWeight.Bold)
                    Text(
                        text = prodName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                if (breakdown.isEmpty()) {
                    Text("No variant sales recorded for this product in this period.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        breakdown.forEach { (variantLabel, pair) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = variantLabel,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${pair.first} sold • ${FormatUtils.formatCurrency(pair.second)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedVariantProduct = null }) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun MetricChangeCard(
    title: String,
    value: String,
    changePercent: Double?,
    isNegativeBenefit: Boolean = false,
    isHighlighted: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) DelivioYellow.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(4.dp))

            if (changePercent != null) {
                val isUp = changePercent >= 0.0
                val isPositive = if (isNegativeBenefit) !isUp else isUp
                val color = if (isPositive) Color(0xFF2E7D32) else Color(0xFFC62828)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isUp) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${if (isUp) "+" else ""}${"%.1f".format(changePercent)}% vs prev",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
            } else {
                Text("- vs prev", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun QualityStatBox(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun DonutCustomerChart(
    newPercentage: Float,
    returningPercentage: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 14.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2
        val center = Offset(size.width / 2, size.height / 2)

        val newSweep = (newPercentage / 100f) * 360f
        val retSweep = 360f - newSweep

        // Returning customers arc (Purple)
        drawArc(
            color = Color(0xFF8B5CF6),
            startAngle = -90f,
            sweepAngle = retSweep,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // New customers arc (Yellow)
        drawArc(
            color = DelivioYellow,
            startAngle = -90f + retSweep,
            sweepAngle = newSweep,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun DailyBarChart(
    days: List<DayData>,
    selectedIndex: Int?,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val maxVal = remember(days) { (days.maxOfOrNull { it.orders.toFloat() } ?: 1f).coerceAtLeast(1f) }

    Column(modifier = modifier) {
        // Bar canvas area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(days) {
                    detectTapGestures { offset ->
                        val count = days.size.coerceAtLeast(1)
                        val slotWidth = size.width / count
                        val tapped = (offset.x / slotWidth).toInt().coerceIn(0, days.size - 1)
                        onSelectIndex(tapped)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val count = days.size
                if (count == 0) return@Canvas
                val slotWidth = size.width / count
                val barWidth = (slotWidth * 0.65f).coerceAtMost(32.dp.toPx())

                days.forEachIndexed { i, day ->
                    val normHeight = (day.orders.toFloat() / maxVal) * (size.height - 8.dp.toPx())
                    val barHeight = normHeight.coerceAtLeast(if (day.orders > 0) 6.dp.toPx() else 2.dp.toPx())
                    val x = (i * slotWidth) + ((slotWidth - barWidth) / 2)
                    val y = size.height - barHeight

                    val isSelected = selectedIndex == i
                    val color = if (isSelected) Color(0xFF1A1A1A) else DelivioYellow

                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                    )
                }

                // Baseline
                drawLine(
                    color = Color.LightGray.copy(alpha = 0.5f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 2f
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Labels strictly below baseline
        Row(modifier = Modifier.fillMaxWidth()) {
            val step = if (days.size > 14) 3 else if (days.size > 7) 2 else 1
            days.forEachIndexed { idx, day ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (idx % step == 0 || idx == days.size - 1) {
                        Text(
                            text = day.displayLabel.take(5),
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HourlyBarChart(
    hours: List<HourData>,
    modifier: Modifier = Modifier
) {
    val maxVal = remember(hours) { (hours.maxOfOrNull { it.orders.toFloat() } ?: 1f).coerceAtLeast(1f) }

    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val count = hours.size
                if (count == 0) return@Canvas
                val slotWidth = size.width / count
                val barWidth = (slotWidth * 0.7f).coerceAtMost(20.dp.toPx())

                hours.forEachIndexed { i, h ->
                    val normHeight = (h.orders.toFloat() / maxVal) * (size.height - 8.dp.toPx())
                    val barHeight = normHeight.coerceAtLeast(if (h.orders > 0) 4.dp.toPx() else 1.dp.toPx())
                    val x = (i * slotWidth) + ((slotWidth - barWidth) / 2)
                    val y = size.height - barHeight

                    drawRoundRect(
                        color = if (h.orders == maxVal.toInt() && maxVal > 0) Color(0xFF8B5CF6) else DelivioYellow,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                    )
                }

                // Baseline
                drawLine(
                    color = Color.LightGray.copy(alpha = 0.5f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 2f
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Hourly labels strictly below baseline
        Row(modifier = Modifier.fillMaxWidth()) {
            hours.forEachIndexed { idx, h ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (idx % 4 == 0) {
                        Text(
                            text = h.label,
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
