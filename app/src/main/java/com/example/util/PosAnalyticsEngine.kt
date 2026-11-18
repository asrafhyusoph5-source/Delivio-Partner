package com.example.util

import com.example.data.model.BusinessType
import com.example.data.model.MenuItem
import com.example.data.model.MerchantSettings
import com.example.data.model.Order
import com.example.data.model.PosLog
import com.example.data.model.PosOrder
import com.example.data.model.PosPaymentBucket
import com.example.data.model.PosShift
import com.example.data.model.SalesSourceFilter
import com.example.ui.screens.DayData
import com.example.ui.screens.ProductStat
import com.example.ui.screens.ReportPeriod
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

data class StackedHourData(
    val hour: Int,
    val label: String,
    val onlineOrders: Int,
    val posOrders: Int
) {
    val totalOrders: Int get() = onlineOrders + posOrders
}

data class CombinedDayData(
    val dateKey: String,
    val displayLabel: String,
    val dayOfWeek: String,
    val onlineOrders: Int,
    val posOrders: Int,
    val onlineSales: Double,
    val posSales: Double
) {
    val totalOrders: Int get() = onlineOrders + posOrders
    val totalSales: Double get() = onlineSales + posSales
    fun toDayData(): DayData = DayData(
        dateKey = dateKey,
        displayLabel = displayLabel,
        dayOfWeek = dayOfWeek,
        orders = totalOrders,
        subtotal = totalSales
    )
}

data class SalesSummaryMetrics(
    val itemSales: Double = 0.0,
    val grossSubtotal: Double = 0.0,
    val discounts: Double = 0.0,
    val refunds: Double = 0.0,
    val netSales: Double = 0.0,
    val ordersCount: Int = 0,
    val averageOrder: Double = 0.0,
    val tips: Double = 0.0,
    val serviceCharge: Double = 0.0,
    val vatAmount: Double = 0.0,
    val itemsSold: Int = 0
)

data class OnlineVsPosSplit(
    val onlineNetSales: Double,
    val posNetSales: Double,
    val onlineOrdersCount: Int,
    val posOrdersCount: Int,
    val onlinePercent: Int,
    val posPercent: Int
) {
    val totalSales: Double get() = onlineNetSales + posNetSales
}

data class PaymentMethodStat(
    val bucket: PosPaymentBucket,
    val amount: Double,
    val orderCount: Int,
    val percent: Int
)

data class OrderTypeStat(
    val label: String,
    val ordersCount: Int,
    val salesAmount: Double,
    val percent: Int
)

data class DiscountKindStat(
    val kind: String,
    val orderCount: Int,
    val amount: Double
)

data class VoidsAndRefundsSummary(
    val voidCount: Int,
    val voidAmount: Double,
    val refundCount: Int,
    val refundAmount: Double,
    val recentLogs: List<PosLog>
)

data class CashierPerformanceStat(
    val cashierName: String,
    val ordersCount: Int,
    val netSales: Double,
    val averageOrder: Double
)

object PosAnalyticsEngine {

    fun resolveTimeZone(tzId: String?): TimeZone {
        val trimmed = tzId?.trim().orEmpty()
        return if (trimmed.isNotBlank() && TimeZone.getAvailableIDs().contains(trimmed)) {
            TimeZone.getTimeZone(trimmed)
        } else {
            TimeZone.getTimeZone("Asia/Manila")
        }
    }

    fun computeTodayBounds(currentTime: Long, tz: TimeZone): Pair<Long, Long> {
        val cal = Calendar.getInstance(tz).apply { timeInMillis = currentTime }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return start to cal.timeInMillis
    }

    fun computePeriodRanges(
        period: ReportPeriod,
        customStartMs: Long,
        customEndMs: Long,
        currentTime: Long,
        tz: TimeZone
    ): Pair<Pair<Long, Long>, Pair<Long, Long>> {
        val cal = Calendar.getInstance(tz).apply { timeInMillis = currentTime }
        return when (period) {
            ReportPeriod.TODAY -> {
                val (start, end) = computeTodayBounds(currentTime, tz)
                val prevStart = start - 86400000L
                val prevEnd = start - 1L
                (start to end) to (prevStart to prevEnd)
            }
            ReportPeriod.LAST_7_DAYS -> {
                cal.add(Calendar.DAY_OF_YEAR, -6)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                val end = currentTime
                val duration = (end - start).coerceAtLeast(1L)
                val prevStart = start - duration
                val prevEnd = start - 1L
                (start to end) to (prevStart to prevEnd)
            }
            ReportPeriod.LAST_30_DAYS -> {
                cal.add(Calendar.DAY_OF_YEAR, -29)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                val end = currentTime
                val duration = (end - start).coerceAtLeast(1L)
                val prevStart = start - duration
                val prevEnd = start - 1L
                (start to end) to (prevStart to prevEnd)
            }
            ReportPeriod.PREVIOUS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.add(Calendar.MONTH, -1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, maxDay)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val end = cal.timeInMillis

                val prevCal = Calendar.getInstance(tz).apply {
                    timeInMillis = start
                    add(Calendar.MONTH, -1)
                }
                val prevStart = prevCal.timeInMillis
                val prevEnd = start - 1L
                (start to end) to (prevStart to prevEnd)
            }
            ReportPeriod.CUSTOM -> {
                val start = minOf(customStartMs, customEndMs)
                val end = maxOf(customStartMs, customEndMs)
                val duration = (end - start).coerceAtLeast(86400000L)
                val prevStart = start - duration
                val prevEnd = start - 1L
                (start to end) to (prevStart to prevEnd)
            }
        }
    }

    fun filterCountedPosSales(posOrders: List<PosOrder>, startMs: Long, endMs: Long): List<PosOrder> {
        return posOrders.filter { ord ->
            ord.isCountedSale && ord.paidAt != null && ord.paidAt in startMs..endMs
        }
    }

    fun computeOnlineSummary(deliveredOnlineOrders: List<Order>): SalesSummaryMetrics {
        val gross = deliveredOnlineOrders.sumOf { it.subtotal }
        val discounts = deliveredOnlineOrders.sumOf { it.effectiveMerchantPromoDiscount }
        val net = deliveredOnlineOrders.sumOf { it.netSales }
        val count = deliveredOnlineOrders.size
        val aov = if (count > 0) net / count else 0.0
        val tips = deliveredOnlineOrders.sumOf { it.tip }
        val items = deliveredOnlineOrders.sumOf { it.totalItemCount }
        return SalesSummaryMetrics(
            itemSales = net,
            grossSubtotal = gross,
            discounts = discounts,
            refunds = 0.0,
            netSales = net,
            ordersCount = count,
            averageOrder = aov,
            tips = tips,
            serviceCharge = 0.0,
            vatAmount = 0.0,
            itemsSold = items
        )
    }

    fun computePosSummary(countedPosOrders: List<PosOrder>): SalesSummaryMetrics {
        val gross = countedPosOrders.sumOf { it.subtotal }
        val discounts = countedPosOrders.sumOf { it.discountTotal }
        // Counting rule: POS item sales = subtotal - discountTotal
        val itemSales = countedPosOrders.sumOf { it.itemSales }
        val refunds = countedPosOrders.sumOf { it.refundAmount }
        val net = (itemSales - refunds).coerceAtLeast(0.0)
        val count = countedPosOrders.size
        val aov = if (count > 0) net / count else 0.0
        val tips = countedPosOrders.sumOf { it.tip }
        val serviceCharge = countedPosOrders.sumOf { it.serviceCharge }
        val vat = countedPosOrders.sumOf { it.vatAmount }
        val items = countedPosOrders.sumOf { it.nonVoidedItemCount }
        return SalesSummaryMetrics(
            itemSales = itemSales,
            grossSubtotal = gross,
            discounts = discounts,
            refunds = refunds,
            netSales = net,
            ordersCount = count,
            averageOrder = aov,
            tips = tips,
            serviceCharge = serviceCharge,
            vatAmount = vat,
            itemsSold = items
        )
    }

    fun combineSummaries(online: SalesSummaryMetrics, pos: SalesSummaryMetrics): SalesSummaryMetrics {
        val totalOrders = online.ordersCount + pos.ordersCount
        val totalNet = online.netSales + pos.netSales
        return SalesSummaryMetrics(
            itemSales = online.itemSales + pos.itemSales,
            grossSubtotal = online.grossSubtotal + pos.grossSubtotal,
            discounts = online.discounts + pos.discounts,
            refunds = online.refunds + pos.refunds,
            netSales = totalNet,
            ordersCount = totalOrders,
            averageOrder = if (totalOrders > 0) totalNet / totalOrders else 0.0,
            tips = online.tips + pos.tips,
            serviceCharge = online.serviceCharge + pos.serviceCharge,
            vatAmount = online.vatAmount + pos.vatAmount,
            itemsSold = online.itemsSold + pos.itemsSold
        )
    }

    fun computeOnlineVsPosSplit(online: SalesSummaryMetrics, pos: SalesSummaryMetrics): OnlineVsPosSplit {
        val total = online.netSales + pos.netSales
        val onlinePct = if (total > 0.0) ((online.netSales / total) * 100.0).roundToInt().coerceIn(0, 100) else 0
        val posPct = if (total > 0.0) (100 - onlinePct).coerceIn(0, 100) else 0
        return OnlineVsPosSplit(
            onlineNetSales = online.netSales,
            posNetSales = pos.netSales,
            onlineOrdersCount = online.ordersCount,
            posOrdersCount = pos.ordersCount,
            onlinePercent = onlinePct,
            posPercent = posPct
        )
    }

    fun buildHourlyStackedData(
        onlineOrders: List<Order>,
        countedPosOrders: List<PosOrder>,
        tz: TimeZone
    ): List<StackedHourData> {
        val onlineCounts = IntArray(24)
        val posCounts = IntArray(24)
        val cal = Calendar.getInstance(tz)

        onlineOrders.forEach { ord ->
            cal.timeInMillis = ord.placedAtTimestamp
            val h = cal.get(Calendar.HOUR_OF_DAY)
            if (h in 0..23) onlineCounts[h]++
        }
        countedPosOrders.forEach { posOrd ->
            val ts = posOrd.paidAt ?: return@forEach
            cal.timeInMillis = ts
            val h = cal.get(Calendar.HOUR_OF_DAY)
            if (h in 0..23) posCounts[h]++
        }

        return (0..23).map { h ->
            val ampm = if (h >= 12) "PM" else "AM"
            val h12 = when {
                h == 0 -> 12
                h > 12 -> h - 12
                else -> h
            }
            StackedHourData(
                hour = h,
                label = "$h12$ampm",
                onlineOrders = onlineCounts[h],
                posOrders = posCounts[h]
            )
        }
    }

    fun buildDailyBreakdown(
        onlineDelivered: List<Order>,
        countedPos: List<PosOrder>,
        startMs: Long,
        endMs: Long,
        tz: TimeZone
    ): List<CombinedDayData> {
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = tz }
        val dayDisplayFmt = SimpleDateFormat("MMM d", Locale.US).apply { timeZone = tz }
        val dayOfWeekFmt = SimpleDateFormat("EEEE", Locale.US).apply { timeZone = tz }

        val onlineByDay = onlineDelivered.groupBy { dayFmt.format(Date(it.placedAtTimestamp)) }
        val posByDay = countedPos.filter { it.paidAt != null }.groupBy { dayFmt.format(Date(it.paidAt!!)) }

        val result = mutableListOf<CombinedDayData>()
        val calIter = Calendar.getInstance(tz).apply {
            timeInMillis = startMs
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val calEnd = Calendar.getInstance(tz).apply { timeInMillis = endMs }

        while (!calIter.after(calEnd)) {
            val key = dayFmt.format(calIter.time)
            val disp = dayDisplayFmt.format(calIter.time)
            val dow = dayOfWeekFmt.format(calIter.time)
            val oDay = onlineByDay[key].orEmpty()
            val pDay = posByDay[key].orEmpty()
            result.add(
                CombinedDayData(
                    dateKey = key,
                    displayLabel = disp,
                    dayOfWeek = dow,
                    onlineOrders = oDay.size,
                    posOrders = pDay.size,
                    onlineSales = oDay.sumOf { it.netSales },
                    posSales = pDay.sumOf { it.netSales }
                )
            )
            calIter.add(Calendar.DAY_OF_YEAR, 1)
        }
        return result
    }

    fun computeProductStats(
        source: SalesSourceFilter,
        deliveredOnlineOrders: List<Order>,
        countedPosOrders: List<PosOrder>,
        menuItems: List<MenuItem>,
        businessType: BusinessType
    ): Triple<List<ProductStat>, List<ProductStat>, Map<String, Map<String, Pair<Int, Double>>>> {
        val salesMap = mutableMapOf<String, Pair<Int, Double>>()
        val variantMap = mutableMapOf<String, MutableMap<String, Pair<Int, Double>>>()

        if (source == SalesSourceFilter.ALL || source == SalesSourceFilter.DELIVIO_ONLINE) {
            deliveredOnlineOrders.forEach { ord ->
                ord.items.forEach { itm ->
                    val itemUnit = if (itm.unitPrice > 0.0) itm.unitPrice else itm.basePrice
                    val lineRev = itm.quantity * itemUnit
                    val curr = salesMap[itm.name] ?: (0 to 0.0)
                    salesMap[itm.name] = (curr.first + itm.quantity) to (curr.second + lineRev)
                    if (itm.variantLabel.isNotBlank()) {
                        val pv = variantMap.getOrPut(itm.name) { mutableMapOf() }
                        val vc = pv[itm.variantLabel] ?: (0 to 0.0)
                        pv[itm.variantLabel] = (vc.first + itm.quantity) to (vc.second + lineRev)
                    }
                }
            }
        }

        if (source == SalesSourceFilter.ALL || source == SalesSourceFilter.IN_STORE_POS) {
            countedPosOrders.forEach { posOrd ->
                // Exclude voided item lines per POS counting rules
                posOrd.items.filter { !it.voided && it.qty > 0 }.forEach { itm ->
                    val curr = salesMap[itm.name] ?: (0 to 0.0)
                    salesMap[itm.name] = (curr.first + itm.qty) to (curr.second + itm.lineTotal)
                    if (itm.variantLabel.isNotBlank()) {
                        val pv = variantMap.getOrPut(itm.name) { mutableMapOf() }
                        val vc = pv[itm.variantLabel] ?: (0 to 0.0)
                        pv[itm.variantLabel] = (vc.first + itm.qty) to (vc.second + itm.lineTotal)
                    }
                }
            }
        }

        val sortedDesc = salesMap.map { (name, pair) ->
            ProductStat(name = name, quantity = pair.first, revenue = pair.second)
        }.sortedByDescending { it.quantity }

        val top = sortedDesc.take(5)

        val allMenuNames = menuItems.filter { it.businessType == businessType }.map { it.name }.distinct()
        val slowFromMenu = allMenuNames.map { name ->
            val sold = salesMap[name] ?: (0 to 0.0)
            ProductStat(name = name, quantity = sold.first, revenue = sold.second)
        }
        val slow = if (slowFromMenu.isNotEmpty()) {
            slowFromMenu.sortedBy { it.quantity }.take(5)
        } else {
            sortedDesc.reversed().take(5)
        }

        return Triple(top, slow, variantMap.mapValues { it.value.toMap() })
    }

    fun computePosPaymentBreakdown(countedPosOrders: List<PosOrder>): List<PaymentMethodStat> {
        val amounts = EnumMapHelper.createPaymentMap()
        val counts = PosPaymentBucket.values().associateWith { 0 }.toMutableMap()

        countedPosOrders.forEach { ord ->
            val usedBuckets = mutableSetOf<PosPaymentBucket>()
            if (ord.payments.isNotEmpty()) {
                ord.payments.forEach { p ->
                    val b = p.bucket
                    amounts[b] = (amounts[b] ?: 0.0) + p.amount
                    usedBuckets.add(b)
                }
            } else if (ord.total > 0.0 || ord.itemSales > 0.0) {
                val fallbackAmt = if (ord.total > 0.0) ord.total else ord.itemSales
                amounts[PosPaymentBucket.OTHER] = (amounts[PosPaymentBucket.OTHER] ?: 0.0) + fallbackAmt
                usedBuckets.add(PosPaymentBucket.OTHER)
            }
            usedBuckets.forEach { b -> counts[b] = (counts[b] ?: 0) + 1 }
        }

        val totalAmount = amounts.values.sum()
        return PosPaymentBucket.values().map { bucket ->
            val amt = amounts[bucket] ?: 0.0
            val pct = if (totalAmount > 0.0) ((amt / totalAmount) * 100.0).roundToInt() else 0
            PaymentMethodStat(
                bucket = bucket,
                amount = amt,
                orderCount = counts[bucket] ?: 0,
                percent = pct
            )
        }
    }

    fun computePosOrderTypeBreakdown(countedPosOrders: List<PosOrder>): List<OrderTypeStat> {
        val dineIn = countedPosOrders.filter { it.isDineIn }
        val takeOut = countedPosOrders.filter { it.isTakeOut }
        val totalCount = countedPosOrders.size
        val dineInPct = if (totalCount > 0) ((dineIn.size.toDouble() / totalCount) * 100.0).roundToInt() else 0
        val takeOutPct = if (totalCount > 0) (100 - dineInPct) else 0
        return listOf(
            OrderTypeStat("Dine-In", dineIn.size, dineIn.sumOf { it.netSales }, dineInPct),
            OrderTypeStat("Take-Out", takeOut.size, takeOut.sumOf { it.netSales }, takeOutPct)
        )
    }

    fun computePosDiscountsByKind(countedPosOrders: List<PosOrder>): List<DiscountKindStat> {
        val amountByKind = linkedMapOf<String, Double>()
        val countByKind = linkedMapOf<String, Int>()

        countedPosOrders.forEach { ord ->
            if (ord.discounts.isNotEmpty()) {
                val seenKinds = mutableSetOf<String>()
                ord.discounts.forEach { d ->
                    val label = d.kind.trim().ifBlank { "Order Discount" }
                    amountByKind[label] = (amountByKind[label] ?: 0.0) + d.amount
                    if (seenKinds.add(label)) {
                        countByKind[label] = (countByKind[label] ?: 0) + 1
                    }
                }
            } else if (ord.discountTotal > 0.0) {
                val label = ord.discountKind.trim().ifBlank { "Order Discount" }
                amountByKind[label] = (amountByKind[label] ?: 0.0) + ord.discountTotal
                countByKind[label] = (countByKind[label] ?: 0) + 1
            }
        }

        return amountByKind.entries.map { (kind, amt) ->
            DiscountKindStat(
                kind = kind,
                orderCount = countByKind[kind] ?: 0,
                amount = amt
            )
        }.sortedByDescending { it.amount }
    }

    fun computeVoidsAndRefundsFromLogs(
        logs: List<PosLog>,
        startMs: Long,
        endMs: Long
    ): VoidsAndRefundsSummary {
        val inPeriod = logs.filter { log ->
            val ts = log.at ?: 0L
            ts in startMs..endMs
        }
        val voidLogs = inPeriod.filter { it.type == "void" }
        val refundLogs = inPeriod.filter { it.type == "refund" }
        val combinedRecent = (voidLogs + refundLogs).sortedByDescending { it.at ?: 0L }
        return VoidsAndRefundsSummary(
            voidCount = voidLogs.size,
            voidAmount = voidLogs.sumOf { it.amount },
            refundCount = refundLogs.size,
            refundAmount = refundLogs.sumOf { it.amount },
            recentLogs = combinedRecent.take(10)
        )
    }

    fun computeCashierPerformance(countedPosOrders: List<PosOrder>): List<CashierPerformanceStat> {
        return countedPosOrders.groupBy { it.cashierName.trim().ifBlank { "Unassigned" } }
            .map { (name, ords) ->
                val net = ords.sumOf { it.netSales }
                val count = ords.size
                CashierPerformanceStat(
                    cashierName = name,
                    ordersCount = count,
                    netSales = net,
                    averageOrder = if (count > 0) net / count else 0.0
                )
            }
            .sortedByDescending { it.netSales }
    }

    fun filterShiftsForPeriod(shifts: List<PosShift>, startMs: Long, endMs: Long): List<PosShift> {
        return shifts.filter { sh ->
            val opened = sh.openedAt ?: 0L
            val closed = sh.closedAt ?: 0L
            (opened in startMs..endMs) || (closed in startMs..endMs)
        }.sortedByDescending { it.openedAt ?: it.closedAt ?: 0L }
    }

    fun computePercentChange(curr: Double, prev: Double): Double? {
        if (prev <= 0.0) return if (curr > 0.0) 100.0 else null
        return ((curr - prev) / prev) * 100.0
    }

    private fun csvEscape(raw: String): String = "\"${raw.replace("\"", "\"\"")}\""

    fun buildReportCsv(
        source: SalesSourceFilter,
        settings: MerchantSettings,
        periodTitle: String,
        startMs: Long,
        endMs: Long,
        currentTime: Long,
        tz: TimeZone,
        isMultiOutlet: Boolean,
        outlets: List<MerchantSettings>,
        selectedOutletIds: Set<String>,
        deliveredOnlineOrders: List<Order>,
        prevDeliveredOnlineOrders: List<Order>,
        countedPosOrders: List<PosOrder>,
        prevCountedPosOrders: List<PosOrder>,
        posShifts: List<PosShift>,
        posLogs: List<PosLog>,
        menuItems: List<MenuItem>,
        newCustomerOrdersCount: Int,
        returningCustomerOrdersCount: Int,
        newPercentage: Int,
        returningPercentage: Int,
        acceptRatePercent: Int?,
        acceptedCount: Int,
        qualifyingAcceptOrders: Int,
        rejectedCount: Int,
        expiredCount: Int,
        avgPrepMinutes: Double?,
        targetPrepTime: Int,
        lateOrdersCount: Int,
        averageRating: Double,
        reviewsCount: Int,
        deliveryOrdersCount: Int,
        pickupOrdersCount: Int,
        scheduledOrdersCount: Int,
        vouchersRedeemedCount: Int,
        vouchersDiscountTotal: Double,
        ordersWithVoucherCount: Int,
        topVoucher: Pair<String, Pair<Int, Double>>?
    ): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = tz }
        val onlineSummary = computeOnlineSummary(deliveredOnlineOrders)
        val prevOnlineSummary = computeOnlineSummary(prevDeliveredOnlineOrders)
        val posSummary = computePosSummary(countedPosOrders)
        val prevPosSummary = computePosSummary(prevCountedPosOrders)

        val activeSummary = when (source) {
            SalesSourceFilter.DELIVIO_ONLINE -> onlineSummary
            SalesSourceFilter.IN_STORE_POS -> posSummary
            SalesSourceFilter.ALL -> combineSummaries(onlineSummary, posSummary)
        }
        val prevSummary = when (source) {
            SalesSourceFilter.DELIVIO_ONLINE -> prevOnlineSummary
            SalesSourceFilter.IN_STORE_POS -> prevPosSummary
            SalesSourceFilter.ALL -> combineSummaries(prevOnlineSummary, prevPosSummary)
        }

        val dailyList = buildDailyBreakdown(
            onlineDelivered = if (source == SalesSourceFilter.IN_STORE_POS) emptyList() else deliveredOnlineOrders,
            countedPos = if (source == SalesSourceFilter.DELIVIO_ONLINE) emptyList() else countedPosOrders,
            startMs = startMs,
            endMs = endMs,
            tz = tz
        )
        val hourlyList = buildHourlyStackedData(
            onlineOrders = if (source == SalesSourceFilter.IN_STORE_POS) emptyList() else deliveredOnlineOrders,
            countedPosOrders = if (source == SalesSourceFilter.DELIVIO_ONLINE) emptyList() else countedPosOrders,
            tz = tz
        )
        val (topProducts, slowMovers, _) = computeProductStats(
            source = source,
            deliveredOnlineOrders = deliveredOnlineOrders,
            countedPosOrders = countedPosOrders,
            menuItems = menuItems,
            businessType = settings.businessType
        )

        return buildString {
            appendLine("DELIVIO PARTNER - ANALYTICS & PERFORMANCE REPORT")
            appendLine("Shop Name,${csvEscape(settings.displayName)}")
            appendLine("Source View,${csvEscape(source.label)}")
            if (isMultiOutlet && selectedOutletIds.size > 1) {
                val selectedNames = outlets.filter { it.merchantId in selectedOutletIds }.joinToString(" | ") { it.displayName }
                appendLine("Outlets Included,${csvEscape(selectedNames)}")
            }
            appendLine("Period,${csvEscape("$periodTitle (${FormatUtils.formatDate(startMs)} - ${FormatUtils.formatDate(endMs)})")}")
            appendLine("Generated At,${sdf.format(Date(currentTime))}")
            appendLine()

            if (isMultiOutlet && selectedOutletIds.size > 1) {
                appendLine("PER-OUTLET BREAKDOWN")
                appendLine("Outlet,Code,Orders,Net Sales")
                outlets.filter { it.merchantId in selectedOutletIds }.forEach { outlet ->
                    val oOrders = if (source == SalesSourceFilter.IN_STORE_POS) emptyList() else deliveredOnlineOrders.filter { it.merchantId == outlet.merchantId }
                    val pOrders = if (source == SalesSourceFilter.DELIVIO_ONLINE) emptyList() else countedPosOrders.filter { it.shopId == outlet.merchantId }
                    val count = oOrders.size + pOrders.size
                    val net = oOrders.sumOf { it.netSales } + pOrders.sumOf { it.netSales }
                    appendLine("${csvEscape(outlet.displayName)},${csvEscape(outlet.branchCode)},$count,${"%.2f".format(Locale.US, net)}")
                }
                appendLine()
            }

            appendLine("SALES SUMMARY")
            appendLine("Metric,Current Period,Previous Period,Change (%)")
            fun appendMetricRow(name: String, curr: Double, prev: Double) {
                val pct = computePercentChange(curr, prev)?.let { "%.1f%%".format(Locale.US, it) } ?: "-"
                appendLine("$name,${"%.2f".format(Locale.US, curr)},${"%.2f".format(Locale.US, prev)},$pct")
            }
            if (source == SalesSourceFilter.DELIVIO_ONLINE) {
                appendMetricRow("Subtotal Sales", onlineSummary.grossSubtotal, prevOnlineSummary.grossSubtotal)
                appendMetricRow("Vouchers Given", onlineSummary.discounts, prevOnlineSummary.discounts)
            } else {
                appendMetricRow("Item Sales", activeSummary.itemSales, prevSummary.itemSales)
                appendMetricRow("Discounts", activeSummary.discounts, prevSummary.discounts)
                appendMetricRow("Refunds", activeSummary.refunds, prevSummary.refunds)
            }
            appendMetricRow("Net Sales", activeSummary.netSales, prevSummary.netSales)
            val ordPct = computePercentChange(activeSummary.ordersCount.toDouble(), prevSummary.ordersCount.toDouble())
                ?.let { "%.1f%%".format(Locale.US, it) } ?: "-"
            appendLine("Orders,${activeSummary.ordersCount},${prevSummary.ordersCount},$ordPct")
            appendMetricRow("Average Order Value", activeSummary.averageOrder, prevSummary.averageOrder)
            val itemsPct = computePercentChange(activeSummary.itemsSold.toDouble(), prevSummary.itemsSold.toDouble())
                ?.let { "%.1f%%".format(Locale.US, it) } ?: "-"
            appendLine("Items Sold,${activeSummary.itemsSold},${prevSummary.itemsSold},$itemsPct")
            if (source != SalesSourceFilter.DELIVIO_ONLINE) {
                appendMetricRow("Tips (separate)", activeSummary.tips, prevSummary.tips)
                appendMetricRow("Service Charge (separate)", activeSummary.serviceCharge, prevSummary.serviceCharge)
                appendMetricRow("VAT (separate)", activeSummary.vatAmount, prevSummary.vatAmount)
            }
            appendLine()

            if (source != SalesSourceFilter.IN_STORE_POS) {
                appendLine("NEW VS RETURNING CUSTOMERS (Delivio Online Only)")
                appendLine("Category,Orders Count,Percentage (%)")
                appendLine("New Customers,$newCustomerOrdersCount,$newPercentage%")
                appendLine("Returning Customers,$returningCustomerOrdersCount,$returningPercentage%")
                if (source == SalesSourceFilter.ALL) {
                    appendLine("Note,In-store customers are not tracked")
                }
                appendLine()
            }

            appendLine("ORDERS DETAIL")
            appendLine("source,order_id,date,type,table,cashier,payment_methods,subtotal,discounts,refunds,net_sales,service_charge,tip,vat,total")
            if (source == SalesSourceFilter.ALL || source == SalesSourceFilter.DELIVIO_ONLINE) {
                deliveredOnlineOrders.forEach { ord ->
                    // Keep product subtotals only for Delivio orders
                    val fulfillmentLabel = if (ord.isPickUp) "pickup" else "delivery"
                    val dateStr = sdf.format(Date(ord.placedAtTimestamp))
                    appendLine(
                        listOf(
                            "delivio",
                            csvEscape(ord.orderNumber.ifBlank { ord.id }),
                            csvEscape(dateStr),
                            csvEscape(fulfillmentLabel),
                            "",
                            "",
                            csvEscape(ord.paymentMethod),
                            "%.2f".format(Locale.US, ord.subtotal),
                            "%.2f".format(Locale.US, ord.effectiveMerchantPromoDiscount),
                            "0.00",
                            "%.2f".format(Locale.US, ord.netSales),
                            "",
                            "",
                            "",
                            ""
                        ).joinToString(",")
                    )
                }
            }
            if (source == SalesSourceFilter.ALL || source == SalesSourceFilter.IN_STORE_POS) {
                countedPosOrders.forEach { pos ->
                    val dateStr = sdf.format(Date(pos.effectiveTimestampMs))
                    val paymentsSummary = if (pos.payments.isNotEmpty()) {
                        pos.payments.joinToString(" | ") { "${it.method}: ${"%.2f".format(Locale.US, it.amount)}" }
                    } else {
                        "other"
                    }
                    appendLine(
                        listOf(
                            "pos",
                            csvEscape(pos.number.ifBlank { pos.id }),
                            csvEscape(dateStr),
                            csvEscape(pos.type),
                            csvEscape(pos.tableLabel),
                            csvEscape(pos.cashierName),
                            csvEscape(paymentsSummary),
                            "%.2f".format(Locale.US, pos.subtotal),
                            "%.2f".format(Locale.US, pos.discountTotal),
                            "%.2f".format(Locale.US, pos.refundAmount),
                            "%.2f".format(Locale.US, pos.netSales),
                            "%.2f".format(Locale.US, pos.serviceCharge),
                            "%.2f".format(Locale.US, pos.tip),
                            "%.2f".format(Locale.US, pos.vatAmount),
                            "%.2f".format(Locale.US, pos.total)
                        ).joinToString(",")
                    )
                }
            }
            appendLine()

            appendLine("ORDERS BY DAY")
            appendLine("Date,Day of Week,Online Orders,POS Orders,Total Orders,Net Sales")
            dailyList.forEach { d ->
                appendLine("${csvEscape(d.dateKey)},${csvEscape(d.dayOfWeek)},${d.onlineOrders},${d.posOrders},${d.totalOrders},${"%.2f".format(Locale.US, d.totalSales)}")
            }
            appendLine()

            appendLine("HOURLY ORDER DISTRIBUTION")
            appendLine("Hour,Online Orders,POS Orders,Total Orders")
            hourlyList.forEach { h ->
                appendLine("${csvEscape(h.label)},${h.onlineOrders},${h.posOrders},${h.totalOrders}")
            }
            appendLine()

            appendLine("TOP PRODUCTS")
            appendLine("Rank,Product Name,Quantity Sold,Revenue")
            topProducts.forEachIndexed { idx, p ->
                appendLine("${idx + 1},${csvEscape(p.name)},${p.quantity},${"%.2f".format(Locale.US, p.revenue)}")
            }
            appendLine()

            appendLine("SLOW MOVERS")
            appendLine("Product Name,Quantity Sold,Revenue")
            slowMovers.forEach { p ->
                appendLine("${csvEscape(p.name)},${p.quantity},${"%.2f".format(Locale.US, p.revenue)}")
            }
            appendLine()

            if (source == SalesSourceFilter.IN_STORE_POS || source == SalesSourceFilter.ALL) {
                val payBreakdown = computePosPaymentBreakdown(countedPosOrders)
                appendLine("POS SALES BY PAYMENT METHOD")
                appendLine("Method,Orders,Amount,Share (%)")
                payBreakdown.forEach { p ->
                    appendLine("${p.bucket.label},${p.orderCount},${"%.2f".format(Locale.US, p.amount)},${p.percent}%")
                }
                appendLine()

                val typeBreakdown = computePosOrderTypeBreakdown(countedPosOrders)
                appendLine("POS SALES BY ORDER TYPE")
                appendLine("Type,Orders,Net Sales,Share (%)")
                typeBreakdown.forEach { t ->
                    appendLine("${t.label},${t.ordersCount},${"%.2f".format(Locale.US, t.salesAmount)},${t.percent}%")
                }
                appendLine()

                val cashiers = computeCashierPerformance(countedPosOrders)
                appendLine("CASHIER PERFORMANCE")
                appendLine("Cashier,Orders,Net Sales,Average Order")
                cashiers.forEach { c ->
                    appendLine("${csvEscape(c.cashierName)},${c.ordersCount},${"%.2f".format(Locale.US, c.netSales)},${"%.2f".format(Locale.US, c.averageOrder)}")
                }
                appendLine()

                val voidsRefunds = computeVoidsAndRefundsFromLogs(posLogs, startMs, endMs)
                appendLine("VOIDS & REFUNDS (posLogs)")
                appendLine("Voids Count,${voidsRefunds.voidCount}")
                appendLine("Voids Amount,${"%.2f".format(Locale.US, voidsRefunds.voidAmount)}")
                appendLine("Refunds Count,${voidsRefunds.refundCount}")
                appendLine("Refunds Amount,${"%.2f".format(Locale.US, voidsRefunds.refundAmount)}")
                appendLine()

                val shifts = filterShiftsForPeriod(posShifts, startMs, endMs)
                appendLine("POS SHIFT SUMMARIES")
                appendLine("Cashier,Status,Opened,Closed,Expected Cash,Closing Cash,Variance")
                shifts.forEach { s ->
                    val op = s.openedAt?.let { sdf.format(Date(it)) } ?: ""
                    val cl = s.closedAt?.let { sdf.format(Date(it)) } ?: "Open"
                    appendLine("${csvEscape(s.cashierName)},${s.status},${csvEscape(op)},${csvEscape(cl)},${"%.2f".format(Locale.US, s.expectedCash)},${"%.2f".format(Locale.US, s.closingCash)},${"%.2f".format(Locale.US, s.variance)}")
                }
                appendLine()
            }

            if (source != SalesSourceFilter.IN_STORE_POS) {
                appendLine("SERVICE QUALITY & FULFILLMENT (Delivio Online)")
                appendLine("Metric,Value")
                appendLine("Accept Rate,${acceptRatePercent?.let { "$it%" } ?: "No data"} ($acceptedCount of $qualifyingAcceptOrders)")
                appendLine("Orders Rejected by Shop,$rejectedCount")
                appendLine("Orders Expired with No Response,$expiredCount")
                appendLine("Average Prep Time,${avgPrepMinutes?.let { "%.1f mins".format(Locale.US, it) } ?: "No data"}")
                appendLine("Late Orders (> ${targetPrepTime}m),$lateOrdersCount")
                appendLine("Store Rating,${"%.1f".format(Locale.US, averageRating)} ($reviewsCount reviews)")
                appendLine("Delivery Orders,$deliveryOrdersCount")
                appendLine("Pick Up Orders,$pickupOrdersCount")
                appendLine("Scheduled Orders,$scheduledOrdersCount")
                appendLine()

                appendLine("VOUCHERS SUBSIDY")
                appendLine("Total Redemptions,$vouchersRedeemedCount")
                appendLine("Total Discount Subsidized,${"%.2f".format(Locale.US, vouchersDiscountTotal)}")
                appendLine("Orders with Voucher,$ordersWithVoucherCount")
                appendLine("Top Voucher,${topVoucher?.first ?: "None"}")
                appendLine()
            }

            appendLine("DISCLAIMER")
            if (source == SalesSourceFilter.DELIVIO_ONLINE) {
                appendLine("Reports show product subtotals only. Delivery fees tips and platform fees are excluded.")
            } else {
                appendLine("In-store sales come from Delivio POS and are not subject to Delivio fees.")
            }
        }
    }
}

private object EnumMapHelper {
    fun createPaymentMap(): MutableMap<PosPaymentBucket, Double> {
        val map = linkedMapOf<PosPaymentBucket, Double>()
        PosPaymentBucket.values().forEach { map[it] = 0.0 }
        return map
    }
}
