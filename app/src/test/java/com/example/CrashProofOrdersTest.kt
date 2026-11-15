package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.OrderStatus
import com.example.ui.screens.OrdersScreen
import com.example.ui.theme.DelivioPartnerTheme
import com.example.util.CrashReportStore
import com.example.util.OrderDocumentParser
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h850dp-xxhdpi")
class CrashProofOrdersTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        CrashReportStore.clearReports(context)
    }

    @Test
    fun parseOrderMap_toleratesStringsDoublesTimestampsNullOptionalsAndUnknownFields() {
        val raw = mapOf<String, Any?>(
            "merchantId" to "m_1",
            "orderNumber" to "2051",
            "customerName" to "Ana Santos",
            "customerPhone" to null,
            "deliveryAddress" to "123 Ayala Ave, Makati",
            "dropoffLat" to "14.5547",
            "dropoffLng" to 121.0244f,
            "subtotal" to "350.50",
            "deliveryFee" to 45L,
            "serviceFee" to "10",
            "total" to "405.50",
            "placedAtTimestamp" to mapOf("seconds" to 1700000000L, "nanoseconds" to 0),
            "createdAt" to "2026-01-15T10:00:00Z",
            "status" to "Placed Order",
            "paymentMethod" to null,
            "promo" to null,
            "branchName" to null,
            "branchCode" to null,
            "chainId" to null,
            "scheduledFor" to null,
            "fees" to mapOf("deliveryFee" to "45", "total" to 405.5),
            "unknownCustomerAppObject" to mapOf("nested" to listOf(1, 2, 3)),
            "items" to listOf(
                mapOf(
                    "id" to "i1",
                    "name" to "Shampoo Bottle",
                    "qty" to "2",
                    "price" to "175.25",
                    "variantKey" to null,
                    "variantLabel" to null,
                    "variantImageUrl" to null,
                    "unitPrice" to null,
                    "regularUnitPrice" to null,
                    "extraUnknownItemFlag" to true
                )
            )
        )

        val order = OrderDocumentParser.parseOrderMap(
            docId = "order_2051",
            data = raw,
            fallbackMerchantId = "m_fallback"
        )

        assertNotNull(order)
        order!!
        assertEquals("#2051", order.orderNumber)
        assertEquals("Ana Santos", order.customerName)
        assertEquals("123 Ayala Ave, Makati", order.deliveryAddress.street)
        assertEquals(14.5547, order.dropoffLat ?: 0.0, 0.0001)
        assertEquals(350.50, order.subtotal, 0.001)
        assertEquals(45.0, order.deliveryFee, 0.001)
        assertEquals(405.50, order.total, 0.001)
        assertEquals(1700000000000L, order.placedAtTimestamp)
        assertEquals(OrderStatus.PLACED_ORDER, order.status)
        assertNull(order.promo)
        assertNull(order.scheduledFor)
        assertEquals("", order.branchName)
        assertEquals(1, order.items.size)
        assertEquals(2, order.items.first().quantity)
        assertEquals(175.25, order.items.first().price, 0.001)
        assertEquals("", order.items.first().variantKey)
        assertEquals("", order.items.first().variantLabel)
    }

    @Test
    fun parseOrderMap_emptyMap_usesSafeDefaultsWithoutCrashing() {
        val order = OrderDocumentParser.parseOrderMap(
            docId = "empty_99",
            data = emptyMap(),
            fallbackMerchantId = ""
        )
        assertNotNull(order)
        order!!
        assertEquals("empty_99", order.id)
        assertEquals("Customer", order.customerName)
        assertTrue(order.items.isEmpty())
        assertEquals(0.0, order.subtotal, 0.001)
        assertEquals(0.0, order.total, 0.001)
    }

    @Test
    fun parseOrderDataSafe_catchesCorruptDocumentAndLogsNonFatalWithPath() {
        var reportedDocId = ""
        var reportedPath = ""
        val parsed = OrderDocumentParser.parseOrderDataSafe(
            docId = "bad_doc_88",
            fallbackMerchantId = "m_1",
            onParseError = { docId, path, err ->
                reportedDocId = docId
                reportedPath = path
                CrashReportStore.logNonFatal(context, path, err)
            },
            dataProvider = {
                throw RuntimeException("Corrupted snapshot deserialization")
            }
        )

        assertNull(parsed)
        assertEquals("bad_doc_88", reportedDocId)
        assertEquals("orders/bad_doc_88", reportedPath)

        val reports = CrashReportStore.loadReports(context)
        assertEquals(1, reports.size)
        assertEquals("orders/bad_doc_88", reports.first().documentPath)
        assertTrue(reports.first().exceptionMessage.contains("Corrupted snapshot"))
    }

    @Test
    fun ordersScreen_showsUnparseableBannerAndTriggersReportClick() {
        var reportClicked = false
        composeRule.setContent {
            DelivioPartnerTheme {
                OrdersScreen(
                    orders = emptyList(),
                    activeFilter = "New",
                    onFilterChanged = {},
                    onOrderClick = {},
                    onAcceptClick = {},
                    onRejectClick = {},
                    onSetReadyClick = {},
                    onPrintReceiptClick = {},
                    unparseableOrderIds = listOf("bad123"),
                    onReportUnparseableOrders = { reportClicked = true }
                )
            }
        }

        composeRule.onNodeWithTag("unparseable_orders_banner").assertIsDisplayed()
        composeRule.onNodeWithText("1 order could not be displayed (#bad123) - Report").assertIsDisplayed()
        composeRule.onNodeWithTag("unparseable_orders_report_btn").performClick()
        assertTrue(reportClicked)
    }

    @Test
    fun ordersScreen_hidesUnparseableBannerWhenNoFailures() {
        composeRule.setContent {
            DelivioPartnerTheme {
                OrdersScreen(
                    orders = emptyList(),
                    activeFilter = "New",
                    onFilterChanged = {},
                    onOrderClick = {},
                    onAcceptClick = {},
                    onRejectClick = {},
                    onSetReadyClick = {},
                    onPrintReceiptClick = {},
                    unparseableOrderIds = emptyList()
                )
            }
        }

        assertEquals(0, composeRule.onAllNodesWithTag("unparseable_orders_banner").fetchSemanticsNodes().size)
    }

    @Test
    fun parseOrderMap_acceptsTimestampCreatedAtAndOptionalVariantFieldsPresent() {
        val nowTs = Timestamp(Date(1710000000000L))
        val raw = mapOf<String, Any?>(
            "createdAt" to nowTs,
            "items" to listOf(
                mapOf(
                    "name" to "Milk",
                    "qty" to 1,
                    "unitPrice" to 95.0,
                    "regularUnitPrice" to 110.0,
                    "variantKey" to "1L",
                    "variantLabel" to "1 Liter",
                    "variantImageUrl" to "https://cdn.example.com/1l.png"
                )
            )
        )
        val order = OrderDocumentParser.parseOrderMap("var_1", raw)
        assertNotNull(order)
        order!!
        assertEquals(1710000000000L, order.placedAtTimestamp)
        assertEquals("1 Liter", order.items.first().variantLabel)
        assertEquals(95.0, order.items.first().unitPrice, 0.001)
        assertEquals(110.0, order.items.first().regularUnitPrice, 0.001)
    }
}
