package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.model.BusinessType
import com.example.data.model.MenuItem
import com.example.data.model.MerchantSettings
import com.example.data.model.Order
import com.example.data.model.OrderItem
import com.example.data.model.OrderPromo
import com.example.data.model.OrderStatus
import com.example.data.model.PosDiscountEntry
import com.example.data.model.PosLog
import com.example.data.model.PosOrder
import com.example.data.model.PosOrderItem
import com.example.data.model.PosPayment
import com.example.data.model.PosPaymentBucket
import com.example.data.model.PosRefund
import com.example.data.model.PosShift
import com.example.data.model.SalesSourceFilter
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.theme.DelivioPartnerTheme
import com.example.util.PosAnalyticsEngine
import com.example.util.PosDocumentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h850dp-xxhdpi")
class PosAnalyticsAndUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Fixed anchor: 2026-06-15 14:00:00 Asia/Manila
    private val manilaTz: TimeZone = TimeZone.getTimeZone("Asia/Manila")
    private val fixedNowMs: Long = 1781503200000L // 2026-06-15 14:00:00 UTC+8 (06:00 UTC)

    private val baseSettings = MerchantSettings(
        merchantId = "shop_1",
        storeName = "Kape Delivio",
        businessType = BusinessType.FOOD,
        timezone = "Asia/Manila"
    )

    // =========================================================================
    // 1. PARSER TESTS (schema tolerance + per-doc try/catch)
    // =========================================================================

    @Test
    fun parsePlatformPosSettings_defaultsTimezoneToManilaAndRespectsFlags() {
        val def = PosDocumentParser.parsePlatformPosSettings(null)
        assertEquals("Asia/Manila", def.timezone)
        assertFalse(def.showTryPosLink)
        assertEquals("", def.tryPosUrl)

        val invalidTz = PosDocumentParser.parsePlatformPosSettings(
            mapOf("timezone" to "Mars/Olympus", "posLinkEnabled" to true, "posLinkUrl" to "https://pos.delivio.ph")
        )
        assertEquals("Asia/Manila", invalidTz.timezone)
        assertTrue(invalidTz.showTryPosLink)
        assertEquals("https://pos.delivio.ph", invalidTz.tryPosUrl)

        val validCustom = PosDocumentParser.parsePlatformPosSettings(
            mapOf(
                "timezone" to "Asia/Tokyo",
                "pos" to mapOf("showLink" to true, "url" to "https://try.delivio.ph")
            )
        )
        assertEquals("Asia/Tokyo", validCustom.timezone)
        assertTrue(validCustom.showTryPosLink)
        assertEquals("https://try.delivio.ph", validCustom.tryPosUrl)
    }

    @Test
    fun parsePosOrder_isSchemaTolerantAndSkipsCorruptDocsSafely() {
        val parsed = PosDocumentParser.parsePosOrderDataSafe(
            docId = "pos_101",
            shopId = "shop_1"
        ) {
            mapOf(
                "type" to "TAKE_OUT",
                "status" to "REFUNDED",
                "subtotal" to "450.50",
                "discountTotal" to "50",
                "serviceCharge" to 40L,
                "vatAmount" to "48.00",
                "total" to 488.50,
                "tip" to "20",
                "cashierName" to "Maria",
                "paidAt" to mapOf("_seconds" to (fixedNowMs / 1000L), "_nanoseconds" to 0),
                "createdAt" to fixedNowMs.toString(),
                "refund" to mapOf("amount" to "100.0", "reason" to "Wrong drink", "at" to fixedNowMs),
                "payments" to listOf(
                    mapOf("method" to "gcash", "amount" to "300"),
                    mapOf("method" to "cash", "amount" to 188.50)
                ),
                "items" to listOf(
                    mapOf("name" to "Latte", "variantLabel" to "Oat Milk", "unitPrice" to "150", "qty" to "2", "voided" to false),
                    mapOf("name" to "Croissant", "unitPrice" to 100, "qty" to 1, "voided" to "true")
                ),
                "unknownExtraField" to 12345
            )
        }
        assertNotNull(parsed)
        assertEquals("take_out", parsed!!.type)
        assertEquals("refunded", parsed.status)
        assertEquals(450.50, parsed.subtotal, 0.01)
        assertEquals(50.0, parsed.discountTotal, 0.01)
        assertEquals(400.50, parsed.itemSales, 0.01)
        assertEquals(100.0, parsed.refundAmount, 0.01)
        assertEquals(300.50, parsed.netSales, 0.01)
        assertEquals(40.0, parsed.serviceCharge, 0.01)
        assertEquals(20.0, parsed.tip, 0.01)
        assertEquals(48.0, parsed.vatAmount, 0.01)
        assertEquals(2, parsed.items.size)
        assertFalse(parsed.items[0].voided)
        assertTrue(parsed.items[1].voided)
        assertEquals(2, parsed.nonVoidedItemCount)
        assertEquals(2, parsed.payments.size)
        assertEquals(PosPaymentBucket.GCASH, parsed.payments[0].bucket)
        assertNotNull(parsed.paidAt)

        var loggedPath = ""
        val bad = PosDocumentParser.parsePosOrderDataSafe(
            docId = "bad_doc",
            shopId = "shop_1",
            onParseError = { _, path, _ -> loggedPath = path }
        ) {
            throw IllegalStateException("Corrupt snapshot payload")
        }
        assertNull(bad)
        assertEquals("merchants/shop_1/posOrders/bad_doc", loggedPath)
    }

    @Test
    fun parsePosShiftAndLog_handleMissingAndMalformedTypesGracefully() {
        val shift = PosDocumentParser.parsePosShiftDataSafe("sh_1", "shop_1") {
            mapOf(
                "cashierName" to "Carlo",
                "openedAt" to fixedNowMs - 10_000L,
                "closedAt" to fixedNowMs,
                "openingCash" to "2000",
                "closingCash" to 5450.0,
                "expectedCash" to 5500.0,
                "variance" to -50.0,
                "status" to "closed"
            )
        }
        assertNotNull(shift)
        assertTrue(shift!!.isClosed)
        assertEquals(-50.0, shift.variance, 0.01)

        val log = PosDocumentParser.parsePosLogDataSafe("log_1", "shop_1") {
            mapOf(
                "type" to "VOID",
                "amount" to "180.00",
                "reason" to "Customer changed mind",
                "byName" to "Carlo",
                "approvedBy" to "Manager Ana",
                "at" to fixedNowMs
            )
        }
        assertNotNull(log)
        assertEquals("void", log!!.type)
        assertEquals(180.0, log.amount, 0.01)
        assertEquals("Manager Ana", log.approvedBy)
    }

    // =========================================================================
    // 2. COUNTING RULES & ANALYTICS ENGINE TESTS
    // =========================================================================

    @Test
    fun countingRules_paidAndRefundedCount_voidedOpenSentHeldExcluded_andAllEqualsOnlinePlusPos() {
        val bounds = PosAnalyticsEngine.computeTodayBounds(fixedNowMs, manilaTz)
        val inDayTs = bounds.first + 3_600_000L

        val posPaid = PosOrder(
            id = "p1",
            shopId = "shop_1",
            number = "POS-01",
            type = "dine_in",
            status = "paid",
            tableName = "T2",
            items = listOf(
                PosOrderItem("Burger", "", 200.0, 2, voided = false),
                PosOrderItem("Fries", "", 100.0, 1, voided = true) // voided item line
            ),
            subtotal = 400.0,
            discountTotal = 50.0,
            discountKind = "Senior Citizen",
            serviceCharge = 35.0,
            vatAmount = 42.0,
            total = 427.0,
            payments = listOf(PosPayment("cash", 200.0), PosPayment("gcash", 227.0)),
            tip = 25.0,
            cashierName = "Maria",
            paidAt = inDayTs
        )
        val posRefunded = PosOrder(
            id = "p2",
            shopId = "shop_1",
            number = "POS-02",
            type = "take_out",
            status = "refunded",
            items = listOf(PosOrderItem("Burger", "", 200.0, 1, voided = false)),
            subtotal = 200.0,
            discountTotal = 0.0,
            serviceCharge = 0.0,
            vatAmount = 21.0,
            total = 221.0,
            payments = listOf(PosPayment("maya", 221.0)),
            tip = 10.0,
            cashierName = "Carlo",
            refund = PosRefund(amount = 80.0, reason = "Overcharged", at = inDayTs),
            paidAt = inDayTs + 1000L
        )
        val posVoided = PosOrder(id = "p3", status = "voided", subtotal = 500.0, paidAt = inDayTs)
        val posOpen = PosOrder(id = "p4", status = "open", subtotal = 300.0, paidAt = inDayTs)
        val posSent = PosOrder(id = "p5", status = "sent", subtotal = 300.0, paidAt = inDayTs)
        val posHeld = PosOrder(id = "p6", status = "held", subtotal = 300.0, paidAt = inDayTs)
        val posPaidNoTimestamp = PosOrder(id = "p7", status = "paid", subtotal = 300.0, paidAt = null)

        val counted = PosAnalyticsEngine.filterCountedPosSales(
            listOf(posPaid, posRefunded, posVoided, posOpen, posSent, posHeld, posPaidNoTimestamp),
            bounds.first,
            bounds.second
        )
        assertEquals(listOf("p1", "p2"), counted.map { it.id })

        val onlineOrder = Order(
            id = "on_1",
            merchantId = "shop_1",
            orderNumber = "DLV-10",
            status = OrderStatus.DELIVERED,
            subtotal = 500.0,
            promo = OrderPromo(code = "SAVE50", discount = 50.0, fundedBy = "merchant"), // netSales = 450
            tip = 15.0,
            items = listOf(OrderItem(name = "Burger", quantity = 3, unitPrice = 150.0)),
            placedAtTimestamp = inDayTs
        )

        val onlineSummary = PosAnalyticsEngine.computeOnlineSummary(listOf(onlineOrder))
        val posSummary = PosAnalyticsEngine.computePosSummary(counted)
        val allSummary = PosAnalyticsEngine.combineSummaries(onlineSummary, posSummary)

        // POS item sales = (400 - 50) + (200 - 0) = 550; refunds = 80; net = 470 (serviceCharge/vat/tip NOT added)
        assertEquals(550.0, posSummary.itemSales, 0.01)
        assertEquals(50.0, posSummary.discounts, 0.01)
        assertEquals(80.0, posSummary.refunds, 0.01)
        assertEquals(470.0, posSummary.netSales, 0.01)
        assertEquals(35.0, posSummary.serviceCharge, 0.01)
        assertEquals(35.0, posSummary.tips, 0.01)
        assertEquals(63.0, posSummary.vatAmount, 0.01)
        assertEquals(2, posSummary.ordersCount)

        // Online net = 450, orders = 1
        assertEquals(450.0, onlineSummary.netSales, 0.01)
        assertEquals(1, onlineSummary.ordersCount)

        // All = Online + POS exactly
        assertEquals(onlineSummary.netSales + posSummary.netSales, allSummary.netSales, 0.01)
        assertEquals(onlineSummary.ordersCount + posSummary.ordersCount, allSummary.ordersCount)
        assertEquals(onlineSummary.discounts + posSummary.discounts, allSummary.discounts, 0.01)
        assertEquals(onlineSummary.refunds + posSummary.refunds, allSummary.refunds, 0.01)
        assertEquals(920.0, allSummary.netSales, 0.01)
        assertEquals(3, allSummary.ordersCount)

        // Voided item lines excluded from Top Products
        val (topAll, _, _) = PosAnalyticsEngine.computeProductStats(
            source = SalesSourceFilter.ALL,
            deliveredOnlineOrders = listOf(onlineOrder),
            countedPosOrders = counted,
            menuItems = listOf(MenuItem(id = "m1", name = "Burger"), MenuItem(id = "m2", name = "Fries")),
            businessType = BusinessType.FOOD
        )
        val burger = topAll.first { it.name == "Burger" }
        assertEquals(6, burger.quantity) // 3 online + 2 posPaid + 1 posRefunded
        assertTrue(topAll.none { it.name == "Fries" && it.quantity > 0 })

        // Breakdowns
        val payBreakdown = PosAnalyticsEngine.computePosPaymentBreakdown(counted)
        assertEquals(200.0, payBreakdown.first { it.bucket == PosPaymentBucket.CASH }.amount, 0.01)
        assertEquals(227.0, payBreakdown.first { it.bucket == PosPaymentBucket.GCASH }.amount, 0.01)
        assertEquals(221.0, payBreakdown.first { it.bucket == PosPaymentBucket.MAYA }.amount, 0.01)

        val orderTypeStats = PosAnalyticsEngine.computePosOrderTypeBreakdown(counted)
        assertEquals(1, orderTypeStats.first { it.label == "Dine-In" }.ordersCount)
        assertEquals(1, orderTypeStats.first { it.label == "Take-Out" }.ordersCount)

        val discountsByKind = PosAnalyticsEngine.computePosDiscountsByKind(counted)
        assertEquals("Senior Citizen", discountsByKind.first().kind)
        assertEquals(50.0, discountsByKind.first().amount, 0.01)

        val cashiers = PosAnalyticsEngine.computeCashierPerformance(counted)
        assertEquals(2, cashiers.size)
        assertEquals("Maria", cashiers.first().cashierName)

        // CSV includes source column & keeps Delivio online product-subtotal only
        val csv = PosAnalyticsEngine.buildReportCsv(
            source = SalesSourceFilter.ALL,
            settings = baseSettings,
            periodTitle = "Today",
            startMs = bounds.first,
            endMs = bounds.second,
            currentTime = fixedNowMs,
            tz = manilaTz,
            isMultiOutlet = false,
            outlets = emptyList(),
            selectedOutletIds = setOf("shop_1"),
            deliveredOnlineOrders = listOf(onlineOrder),
            prevDeliveredOnlineOrders = emptyList(),
            countedPosOrders = counted,
            prevCountedPosOrders = emptyList(),
            posShifts = emptyList(),
            posLogs = emptyList(),
            menuItems = emptyList(),
            newCustomerOrdersCount = 1,
            returningCustomerOrdersCount = 0,
            newPercentage = 100,
            returningPercentage = 0,
            acceptRatePercent = 100,
            acceptedCount = 1,
            qualifyingAcceptOrders = 1,
            rejectedCount = 0,
            expiredCount = 0,
            avgPrepMinutes = 10.0,
            targetPrepTime = 25,
            lateOrdersCount = 0,
            averageRating = 5.0,
            reviewsCount = 0,
            deliveryOrdersCount = 1,
            pickupOrdersCount = 0,
            scheduledOrdersCount = 0,
            vouchersRedeemedCount = 1,
            vouchersDiscountTotal = 50.0,
            ordersWithVoucherCount = 1,
            topVoucher = null
        )
        assertTrue(csv.contains("source,order_id,date,type,table,cashier,payment_methods,subtotal,discounts,refunds,net_sales,service_charge,tip,vat,total"))
        assertTrue(csv.contains("delivio,\"DLV-10\","))
        assertTrue(csv.contains("\"delivery\",,,\"COD\",500.00,50.00,0.00,450.00,,,,"))
        assertTrue(csv.contains("pos,\"POS-01\","))
        assertTrue(csv.contains("\"dine_in\",\"T2\",\"Maria\",\"cash: 200.00 | gcash: 227.00\",400.00,50.00,0.00,350.00,35.00,25.00,42.00,427.00"))
    }

    // =========================================================================
    // 3. DASHBOARD UI TESTS
    // =========================================================================

    @Test
    fun dashboard_hidesInStoreCardWhenShopNeverUsedPos_andRespectsTryPosSetting() {
        var showTryPos by mutableStateOf(false)
        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                DashboardScreen(
                    settings = baseSettings,
                    orders = emptyList(),
                    onToggleOpen = {},
                    onPauseClick = {},
                    onResumeClick = {},
                    onOrderClick = {},
                    onViewAllOrders = {},
                    soldOutItemsCount = 0,
                    lowStockCount = 0,
                    currentTime = fixedNowMs,
                    posOrders = emptyList(),
                    hasEverUsedPos = false,
                    showTryPosLink = showTryPos,
                    platformTimezone = "Asia/Manila"
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithTag("dashboard_instore_sales_card").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("dashboard_try_pos_link").fetchSemanticsNodes().size)

        showTryPos = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dashboard_try_pos_link").assertIsDisplayed()
        composeRule.onNodeWithText("Try Delivio POS").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("dashboard_instore_sales_card").fetchSemanticsNodes().size)
    }

    @Test
    fun dashboard_showsInStoreSalesCardWhenPosUsed_andCountsOnlyPaidOrRefunded() {
        val bounds = PosAnalyticsEngine.computeTodayBounds(fixedNowMs, manilaTz)
        val inDayTs = bounds.first + 3_600_000L

        val posOrders = listOf(
            PosOrder(id = "p1", status = "paid", subtotal = 600.0, discountTotal = 100.0, paidAt = inDayTs), // net 500
            PosOrder(id = "p2", status = "voided", subtotal = 999.0, paidAt = inDayTs), // ignored
            PosOrder(id = "p3", status = "open", subtotal = 500.0, paidAt = inDayTs) // ignored
        )

        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                DashboardScreen(
                    settings = baseSettings,
                    orders = emptyList(),
                    onToggleOpen = {},
                    onPauseClick = {},
                    onResumeClick = {},
                    onOrderClick = {},
                    onViewAllOrders = {},
                    soldOutItemsCount = 0,
                    lowStockCount = 0,
                    currentTime = fixedNowMs,
                    posOrders = posOrders,
                    hasEverUsedPos = true,
                    showTryPosLink = true, // must stay hidden once shop uses POS
                    platformTimezone = "Asia/Manila"
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dashboard_instore_sales_card").assertIsDisplayed()
        composeRule.onNodeWithText("In-store sales today").assertIsDisplayed()
        composeRule.onNodeWithText("₱500.00").assertIsDisplayed()
        composeRule.onNodeWithText("1 order").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("dashboard_try_pos_link").fetchSemanticsNodes().size)
    }

    // =========================================================================
    // 4. REPORTS UI TESTS (All / Online / POS segmented control & POS cards)
    // =========================================================================

    @Test
    fun reports_segmentedControlSwitchesBetweenAllOnlineAndPos_andDisplaysPosSections() {
        val bounds = PosAnalyticsEngine.computeTodayBounds(fixedNowMs, manilaTz)
        val inDayTs = bounds.first + 7_200_000L

        val onlineOrders = listOf(
            Order(
                id = "on_1",
                merchantId = "shop_1",
                orderNumber = "DLV-1",
                customerId = "cust_1",
                status = OrderStatus.DELIVERED,
                subtotal = 500.0,
                promo = OrderPromo(code = "SAVE50", discount = 50.0, fundedBy = "merchant"), // net = 450
                placedAtTimestamp = inDayTs,
                items = listOf(OrderItem(name = "Latte", quantity = 2, unitPrice = 250.0))
            )
        )
        val posOrders = listOf(
            PosOrder(
                id = "pos_1",
                shopId = "shop_1",
                number = "POS-1",
                type = "dine_in",
                status = "paid",
                subtotal = 350.0,
                discountTotal = 50.0, // itemSales = 300, net = 300
                serviceCharge = 30.0,
                vatAmount = 36.0,
                total = 366.0,
                tip = 20.0,
                cashierName = "Maria",
                discounts = listOf(PosDiscountEntry("PWD Discount", 50.0)),
                payments = listOf(PosPayment("gcash", 366.0)),
                items = listOf(PosOrderItem("Latte", "", 175.0, 2, voided = false)),
                paidAt = inDayTs
            )
        )
        val shifts = listOf(
            PosShift(
                id = "sh_pos",
                cashierName = "Maria",
                openedAt = bounds.first + 1000L,
                closedAt = inDayTs + 1000L,
                openingCash = 1000.0,
                closingCash = 1550.0,
                expectedCash = 1500.0,
                variance = 50.0,
                status = "closed"
            ),
            PosShift(
                id = "sh_neg",
                cashierName = "Carlo",
                openedAt = bounds.first + 2000L,
                closedAt = inDayTs + 2000L,
                openingCash = 1000.0,
                closingCash = 900.0,
                expectedCash = 1000.0,
                variance = -100.0,
                status = "closed"
            )
        )
        val logs = listOf(
            PosLog(
                id = "lg_1",
                type = "void",
                amount = 120.0,
                reason = "Customer cancelled",
                byName = "Maria",
                approvedBy = "Owner",
                at = inDayTs
            )
        )

        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                ReportsScreen(
                    settings = baseSettings,
                    orders = onlineOrders,
                    menuItems = listOf(MenuItem(id = "m1", name = "Latte")),
                    reviews = emptyList(),
                    vouchers = emptyList(),
                    redemptions = emptyList(),
                    averageRating = 4.9,
                    reviewsCount = 10,
                    currentTime = fixedNowMs,
                    onCheckCustomerPriorDelivered = { _, _, cb -> cb(false, null) },
                    onOpenReviews = {},
                    onCreateVoucher = {},
                    onBack = {},
                    posOrders = posOrders,
                    posShifts = shifts,
                    posLogs = logs,
                    platformTimezone = "Asia/Manila"
                )
            }
        }
        composeRule.waitForIdle()

        // Segmented control visible
        composeRule.onNodeWithTag("reports_segment_all").assertIsDisplayed()
        composeRule.onNodeWithTag("reports_segment_online").assertIsDisplayed()
        composeRule.onNodeWithTag("reports_segment_pos").assertIsDisplayed()

        // All sales shows Online vs POS split and combined summary (450 + 300 = 750)
        composeRule.onNodeWithTag("reports_online_vs_pos_split").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("sales_overview_card").performScrollTo()
            .assert(hasAnyDescendant(hasText("₱750.00", substring = true)))
        composeRule.onNodeWithTag("reports_tips_service_charge_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("reports_footer_disclaimer").performScrollTo()
            .assert(hasText("In-store sales come from Delivio POS and are not subject to Delivio fees.", substring = true))

        // Switch to "In-store (POS)"
        composeRule.onNodeWithTag("reports_segment_pos").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("pos_customers_not_tracked_note").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("sales_overview_card").performScrollTo()
            .assert(hasAnyDescendant(hasText("₱300.00", substring = true)))
        composeRule.onNodeWithTag("reports_pos_payment_methods_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("reports_pos_order_types_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("reports_pos_discounts_card").performScrollTo()
            .assert(hasAnyDescendant(hasText("PWD Discount", substring = true)))
        composeRule.onNodeWithTag("reports_pos_voids_refunds_card").performScrollTo()
            .assert(hasAnyDescendant(hasText("Customer cancelled", substring = true)))
        composeRule.onNodeWithTag("reports_pos_cashier_card").performScrollTo()
            .assert(hasAnyDescendant(hasText("Maria", substring = true)))
        composeRule.onNodeWithTag("reports_pos_shifts_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("In-store sales come from Delivio POS and are not subject to Delivio fees.").performScrollTo().assertIsDisplayed()

        // Switch to "Delivio online" -> Online vs POS split hidden, original disclaimer shown
        composeRule.onNodeWithTag("reports_segment_online").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithTag("reports_online_vs_pos_split").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("reports_footer_disclaimer").performScrollTo()
            .assert(hasText("Reports show product subtotals only.", substring = true))
    }
}
