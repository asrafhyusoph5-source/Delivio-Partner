package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AddressLocation
import com.example.data.model.MerchantSettings
import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.data.model.Rider
import com.example.data.model.RiderRequest
import com.example.data.repository.MerchantRepository
import com.example.ui.components.ManualRiderBookingSheet
import com.example.ui.components.RiderTrackingModal
import com.example.ui.screens.OrdersScreen
import com.example.ui.theme.DelivioPartnerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h850dp-xxhdpi")
class RequestRiderInstantTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun buildInstantRiderOrderPayload_setsCashOrPaidAndRequiredFields() {
        val repo = MerchantRepository.getInstance(ApplicationProvider.getApplicationContext())
        val settings = MerchantSettings(
            merchantId = "m_branch_1",
            storeName = "Cafe Delivio",
            branchName = "BGC Branch",
            branchCode = "BGC",
            cityId = "taguig",
            storeLocation = AddressLocation(
                street = "High Street, BGC",
                city = "Taguig",
                latitude = 14.5500,
                longitude = 121.0500
            )
        )

        val cashPayload = repo.buildInstantRiderOrderPayload(
            merchantId = "m_branch_1",
            settings = settings,
            recipientName = "Juan Cruz",
            recipientPhone = "09171234567",
            deliveryAddress = "5th Ave, BGC",
            dropoffArea = "Taguig",
            dropoffLat = 14.5520,
            dropoffLng = 121.0480,
            whatToDeliver = "Cake box",
            relatedOrderNumber = "",
            cashToCollect = 450.0,
            riderNote = "Fragile",
            distanceKm = 1.5,
            deliveryFee = 45.0,
            serviceFee = 10.0,
            orderNum = "#R1001"
        )

        assertEquals(450.0, cashPayload["cashToCollect"])
        assertEquals("cash", cashPayload["paymentMethod"])
        assertEquals("request_rider", cashPayload["serviceType"])
        assertEquals("Ready", cashPayload["status"])
        assertNull(cashPayload["riderId"])
        assertEquals("m_branch_1", cashPayload["merchantId"])
        assertEquals("Cafe Delivio", cashPayload["merchantName"])
        assertEquals("BGC Branch", cashPayload["branchName"])
        assertEquals("BGC", cashPayload["branchCode"])
        assertEquals("Juan Cruz", cashPayload["recipientName"])
        assertEquals("09171234567", cashPayload["recipientPhone"])

        @Suppress("UNCHECKED_CAST")
        val rrCash = cashPayload["riderRequest"] as Map<String, Any?>
        assertEquals("Searching", rrCash["status"])
        assertEquals(3.0, rrCash["radiusKm"])
        assertTrue(rrCash["createdAt"] != null)

        val paidPayload = repo.buildInstantRiderOrderPayload(
            merchantId = "m_branch_1",
            settings = settings,
            recipientName = "Maria Clara",
            recipientPhone = "09179998888",
            deliveryAddress = "Makati Ave",
            dropoffArea = "Makati",
            dropoffLat = 14.5550,
            dropoffLng = 121.0300,
            whatToDeliver = "Documents",
            relatedOrderNumber = "",
            cashToCollect = 0.0,
            riderNote = "",
            distanceKm = 2.0,
            deliveryFee = 45.0,
            serviceFee = 10.0,
            orderNum = "#R1002"
        )

        assertEquals(0.0, paidPayload["cashToCollect"])
        assertEquals("paid", paidPayload["paymentMethod"])
    }

    @Test
    fun manualRiderBookingSheet_showsCashToCollectLabelAndInlineErrorWithRetry() {
        composeRule.setContent {
            DelivioPartnerTheme {
                ManualRiderBookingSheet(
                    settings = MerchantSettings(storeName = "Test Shop"),
                    submissionError = "permission-denied: Missing or insufficient permissions.",
                    onBookRider = { _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
                    onOpenLocationPicker = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("manual_booking_error_banner").assertIsDisplayed()
        composeRule.onNodeWithText("permission-denied: Missing or insufficient permissions.").assertIsDisplayed()
        composeRule.onNodeWithTag("manual_booking_retry_btn").assertIsDisplayed()

        composeRule.onNodeWithTag("cash_to_collect_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Cash to collect from the recipient (P)").assertIsDisplayed()
    }

    @Test
    fun riderTrackingModal_searchingBelow90s_showsLookingTimerAlertsCountAndCancelRequest() {
        var cancelled = false
        val start30sAgo = System.currentTimeMillis() - 30_000L
        val searchingOrder = Order(
            id = "inst_search",
            orderNumber = "#R2200",
            serviceType = "request_rider",
            status = OrderStatus.READY,
            customerName = "Pedro",
            riderRequest = RiderRequest(
                id = "rr_inst_search",
                orderId = "inst_search",
                status = "Searching",
                createdAt = start30sAgo,
                requestedAt = start30sAgo,
                radiusKm = 3.0,
                alertsSent = 4
            )
        )

        composeRule.setContent {
            DelivioPartnerTheme {
                RiderTrackingModal(
                    order = searchingOrder,
                    merchantLocation = AddressLocation(street = "BGC", latitude = 14.55, longitude = 121.05),
                    onConfirmRequestRider = { _, _, _, _, _, _ -> },
                    onRetryRiderRequest = {},
                    onCancelRiderRequest = { cancelled = true },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("looking_for_rider_title").assertIsDisplayed()
        composeRule.onNodeWithText("Looking for a rider...").assertIsDisplayed()
        composeRule.onNodeWithTag("searching_timer_text").assertIsDisplayed()
        composeRule.onNodeWithText("4 rider alerts sent", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel request").performClick()
        assertTrue(cancelled)
    }

    @Test
    fun riderTrackingModal_searchingAfter90s_showsNoRiderYetWithTryAgainAndCancel() {
        var retried = false
        var cancelled = false
        val start100sAgo = System.currentTimeMillis() - 100_000L
        val timedOutOrder = Order(
            id = "inst_1",
            orderNumber = "#R2201",
            serviceType = "request_rider",
            status = OrderStatus.READY,
            customerName = "Pedro",
            riderRequest = RiderRequest(
                id = "rr_inst_1",
                orderId = "inst_1",
                status = "Searching",
                createdAt = start100sAgo,
                requestedAt = start100sAgo,
                radiusKm = 3.0,
                alertsSent = 4
            )
        )

        composeRule.setContent {
            DelivioPartnerTheme {
                RiderTrackingModal(
                    order = timedOutOrder,
                    merchantLocation = AddressLocation(street = "BGC", latitude = 14.55, longitude = 121.05),
                    onConfirmRequestRider = { _, _, _, _, _, _ -> },
                    onRetryRiderRequest = { retried = true },
                    onCancelRiderRequest = { cancelled = true },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("no_rider_accepted_title").assertIsDisplayed()
        composeRule.onNodeWithText("No rider accepted yet").assertIsDisplayed()

        composeRule.onNodeWithTag("try_again_rider_request_btn").performClick()
        assertTrue(retried)

        composeRule.onNodeWithTag("cancel_rider_request_btn").performClick()
        assertTrue(cancelled)
    }

    @Test
    fun riderTrackingModal_triggersExpireAt5Minutes() {
        var expiredCalled = false
        val start310sAgo = System.currentTimeMillis() - 310_000L
        val expiringOrder = Order(
            id = "inst_exp",
            orderNumber = "#R2209",
            serviceType = "request_rider",
            status = OrderStatus.READY,
            riderRequest = RiderRequest(
                status = "Searching",
                createdAt = start310sAgo,
                requestedAt = start310sAgo,
                radiusKm = 4.5
            )
        )

        composeRule.setContent {
            DelivioPartnerTheme {
                RiderTrackingModal(
                    order = expiringOrder,
                    merchantLocation = AddressLocation(street = "BGC", latitude = 14.55, longitude = 121.05),
                    onConfirmRequestRider = { _, _, _, _, _, _ -> },
                    onRetryRiderRequest = {},
                    onCancelRiderRequest = {},
                    onExpireRiderRequest = { expiredCalled = true },
                    onDismiss = {}
                )
            }
        }

        composeRule.waitForIdle()
        assertTrue(expiredCalled)
    }

    @Test
    fun riderTrackingModal_showsAcceptedRiderDetails() {
        val assignedOrder = Order(
            id = "inst_2",
            orderNumber = "#R3005",
            serviceType = "request_rider",
            status = OrderStatus.READY,
            riderRequest = RiderRequest(
                status = "Rider assigned",
                rider = Rider(
                    id = "r_1",
                    name = "Carlo Mendoza",
                    phone = "09185551234",
                    vehicleType = "Motorcycle",
                    vehiclePlate = "ABC-987"
                )
            )
        )

        composeRule.setContent {
            DelivioPartnerTheme {
                RiderTrackingModal(
                    order = assignedOrder,
                    merchantLocation = AddressLocation(street = "BGC", latitude = 14.55, longitude = 121.05),
                    onConfirmRequestRider = { _, _, _, _, _, _ -> },
                    onRetryRiderRequest = {},
                    onCancelRiderRequest = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("Carlo Mendoza").assertIsDisplayed()
        composeRule.onNodeWithText("Motorcycle • Plate: ABC-987").assertIsDisplayed()
        composeRule.onNodeWithText("09185551234").assertIsDisplayed()
        composeRule.onNodeWithText("Rider assigned").assertIsDisplayed()
    }

    @Test
    fun ordersScreen_showsInstantChipOnlyForRequestRiderOrders() {
        val instantOrder = Order(
            id = "ord_inst",
            orderNumber = "#R4401",
            customerName = "Liza",
            serviceType = "request_rider",
            fulfillment = "delivery",
            status = OrderStatus.READY
        )
        val foodOrder = Order(
            id = "ord_food",
            orderNumber = "#4402",
            customerName = "Mark",
            serviceType = "food",
            fulfillment = "delivery",
            status = OrderStatus.READY
        )

        composeRule.setContent {
            DelivioPartnerTheme {
                OrdersScreen(
                    orders = listOf(instantOrder, foodOrder),
                    activeFilter = "Ready",
                    onFilterChanged = {},
                    fulfillmentFilter = "Delivery",
                    onOrderClick = {},
                    onAcceptClick = {},
                    onRejectClick = {},
                    onSetReadyClick = {},
                    onPrintReceiptClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("instant_chip_ord_inst", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("instant_chip_ord_food").fetchSemanticsNodes().size)
    }
}
