package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.model.AddressLocation
import com.example.data.model.FailedOrderReport
import com.example.data.model.FulfillmentType
import com.example.data.model.MerchantSettings
import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.data.model.Rider
import com.example.data.model.RiderRequest
import com.example.data.model.fulfillmentOf
import com.example.ui.components.FailedOrdersBanner
import com.example.ui.components.ManualRiderBookingSheet
import com.example.ui.components.RiderTrackingModal
import com.example.ui.screens.OrdersScreen
import com.example.ui.theme.DelivioPartnerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h850dp-xxhdpi")
class InstantRequestRiderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val testSettings = MerchantSettings(
        merchantId = "m1",
        storeName = "Test Bakery",
        cityId = "taguig",
        storeLocation = AddressLocation(
            street = "High Street, Taguig",
            city = "Taguig",
            latitude = 14.5500,
            longitude = 121.0500
        )
    )

    @Test
    fun failedOrdersBanner_displaysExactMessageAndCallbacksWork() {
        var reportedClicked = false
        var dismissedClicked = false

        composeRule.setContent {
            DelivioPartnerTheme {
                FailedOrdersBanner(
                    failedReports = listOf(
                        FailedOrderReport(orderId = "abc123", errorMessage = "Bad field")
                    ),
                    onReport = { reportedClicked = true },
                    onDismiss = { dismissedClicked = true }
                )
            }
        }

        composeRule.onNodeWithTag("failed_orders_banner").assertIsDisplayed()
        composeRule.onNodeWithText("1 order could not be displayed (#abc123) - Report", substring = false)
            .assertIsDisplayed()

        composeRule.onNodeWithTag("failed_orders_report_btn").performClick()
        assertTrue(reportedClicked)

        composeRule.onNodeWithTag("failed_orders_dismiss_btn").performClick()
        assertTrue(dismissedClicked)
    }

    @Test
    fun instantOrder_fulfillmentAndTimingThresholds_behaveAccurately() {
        val now = 10_000_000L
        val baseOrder = Order(
            id = "inst_1",
            serviceType = "request_rider",
            fulfillment = "delivery",
            hasPickup = false,
            status = OrderStatus.READY,
            riderRequest = RiderRequest(
                status = "Searching",
                createdAt = now - 30_000L, // 30s ago
                requestedAt = now - 30_000L,
                radiusKm = 3.0,
                alertsSent = 2
            )
        )

        assertEquals(FulfillmentType.DELIVERY, fulfillmentOf(baseOrder))
        assertTrue(baseOrder.isInstantRiderRequest)
        assertFalse(baseOrder.isInstantNoRiderYet(now))
        assertFalse(baseOrder.isInstantExpired(now))
        assertFalse(baseOrder.canRequestRiderAgain(now))

        // At 95 seconds -> "No rider accepted yet" + Try again enabled
        val at95s = baseOrder.copy(
            riderRequest = baseOrder.riderRequest?.copy(createdAt = now - 95_000L, requestedAt = now - 95_000L)
        )
        assertTrue(at95s.isInstantNoRiderYet(now))
        assertFalse(at95s.isInstantExpired(now))
        assertTrue(at95s.canRequestRiderAgain(now))

        // At 305 seconds (5+ mins) -> Expired
        val at305s = baseOrder.copy(
            riderRequest = baseOrder.riderRequest?.copy(createdAt = now - 305_000L, requestedAt = now - 305_000L)
        )
        assertFalse(at305s.isInstantNoRiderYet(now))
        assertTrue(at305s.isInstantExpired(now))
    }

    @Test
    fun manualRiderBookingSheet_showsDefaultZeroCashAndFirestoreErrorWithRetry() {
        composeRule.setContent {
            DelivioPartnerTheme {
                ManualRiderBookingSheet(
                    settings = testSettings,
                    isSubmitting = false,
                    errorMessage = "Firestore error [permission-denied]: Missing or insufficient permissions.",
                    onBookRider = { _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
                    onOpenLocationPicker = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("cash_to_collect_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Cash to collect from the recipient (P)").assertIsDisplayed()
        composeRule.onNodeWithTag("cash_to_collect_input").assertTextContains("0")
        composeRule.onNodeWithTag("manual_booking_error_card").assertIsDisplayed()
        composeRule.onNodeWithText("Firestore error [permission-denied]: Missing or insufficient permissions.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("manual_booking_retry_btn").assertIsDisplayed()
    }

    @Test
    fun riderTrackingModal_searchingBelow90s_showsLookingTimerAlertsAndCancelRequest() {
        val now = System.currentTimeMillis()
        var cancelled = false
        val searchingOrder = Order(
            id = "inst_search_1",
            orderNumber = "INST-001",
            serviceType = "request_rider",
            status = OrderStatus.READY,
            customerName = "Juan",
            recipientName = "Juan",
            deliveryAddress = AddressLocation(street = "BGC, Taguig", latitude = 14.555, longitude = 121.052),
            riderRequest = RiderRequest(
                status = "Searching",
                createdAt = now - 20_000L,
                requestedAt = now - 20_000L,
                radiusKm = 3.0,
                alertsSent = 5
            )
        )

        composeRule.setContent {
            DelivioPartnerTheme {
                RiderTrackingModal(
                    order = searchingOrder,
                    merchantLocation = testSettings.storeLocation,
                    onConfirmRequestRider = { _, _, _, _, _, _ -> },
                    onRetryRiderRequest = {},
                    onCancelRiderRequest = { cancelled = true },
                    onExpireRiderRequest = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("looking_for_rider_text").assertIsDisplayed()
        composeRule.onNodeWithText("Looking for a rider...").assertIsDisplayed()
        composeRule.onNodeWithText("Rider alerts sent: 5 • 3.0 km radius").assertIsDisplayed()
        composeRule.onNodeWithTag("rider_search_timer").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel request").performClick()
        assertTrue(cancelled)
    }

    @Test
    fun riderTrackingModal_searchingAfter90s_showsNoRiderAcceptedYetTryAgainAndCancel() {
        val now = System.currentTimeMillis()
        var retried = false
        var cancelled = false
        val delayedOrder = Order(
            id = "inst_search_95",
            orderNumber = "INST-002",
            serviceType = "request_rider",
            status = OrderStatus.READY,
            customerName = "Maria",
            recipientName = "Maria",
            deliveryAddress = AddressLocation(street = "BGC, Taguig", latitude = 14.555, longitude = 121.052),
            riderRequest = RiderRequest(
                status = "Searching",
                createdAt = now - 105_000L,
                requestedAt = now - 105_000L,
                radiusKm = 3.0,
                alertsSent = 7
            )
        )

        composeRule.setContent {
            DelivioPartnerTheme {
                RiderTrackingModal(
                    order = delayedOrder,
                    merchantLocation = testSettings.storeLocation,
                    onConfirmRequestRider = { _, _, _, _, _, _ -> },
                    onRetryRiderRequest = { retried = true },
                    onCancelRiderRequest = { cancelled = true },
                    onExpireRiderRequest = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("no_rider_accepted_yet_text").assertIsDisplayed()
        composeRule.onNodeWithText("No rider accepted yet").assertIsDisplayed()
        composeRule.onNodeWithTag("try_again_rider_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("cancel_rider_request_btn").assertIsDisplayed()

        composeRule.onNodeWithTag("try_again_rider_btn").performClick()
        assertTrue(retried)

        composeRule.onNodeWithTag("cancel_rider_request_btn").performClick()
        assertTrue(cancelled)
    }

    @Test
    fun riderTrackingModal_acceptedRider_showsNamePhoneVehiclePlateAndLiveStatus() {
        val acceptedOrder = Order(
            id = "inst_accepted_1",
            orderNumber = "INST-003",
            serviceType = "request_rider",
            status = OrderStatus.RIDER_ON_THE_WAY,
            riderId = "r_77",
            riderName = "Marco Reyes",
            riderPhone = "09178889999",
            riderVehicle = "Honda Click 125",
            riderPlate = "ABC-1234",
            deliveryAddress = AddressLocation(street = "BGC, Taguig", latitude = 14.555, longitude = 121.052),
            rider = Rider(id = "r_77", name = "Marco Reyes", phone = "09178889999", vehiclePlate = "ABC-1234", vehicleType = "Honda Click 125"),
            riderRequest = RiderRequest(
                status = "On the way",
                createdAt = System.currentTimeMillis() - 60_000L
            )
        )

        composeRule.setContent {
            DelivioPartnerTheme {
                RiderTrackingModal(
                    order = acceptedOrder,
                    merchantLocation = testSettings.storeLocation,
                    onConfirmRequestRider = { _, _, _, _, _, _ -> },
                    onRetryRiderRequest = {},
                    onCancelRiderRequest = {},
                    onExpireRiderRequest = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("assigned_rider_card").assertIsDisplayed()
        composeRule.onNodeWithText("Marco Reyes").assertIsDisplayed()
        composeRule.onNodeWithText("09178889999").assertIsDisplayed()
        composeRule.onNodeWithText("Honda Click 125 • ABC-1234").assertIsDisplayed()
        composeRule.onAllNodesWithText("On the way").onFirst().assertIsDisplayed()
    }

    @Test
    fun ordersScreen_showsRequestRiderUnderDeliveryWithInstantChip() {
        val instantOrder = Order(
            id = "inst_card_1",
            orderNumber = "INST-100",
            serviceType = "request_rider",
            fulfillment = "delivery",
            hasPickup = false,
            status = OrderStatus.READY,
            customerName = "Pedro",
            recipientName = "Pedro",
            paymentMethod = "cash",
            cashToCollect = 250.0,
            total = 65.0,
            placedAtTimestamp = System.currentTimeMillis() - 20_000L,
            deliveryAddress = AddressLocation(street = "Taguig"),
            riderRequest = RiderRequest(status = "Searching", createdAt = System.currentTimeMillis() - 20_000L)
        )

        composeRule.setContent {
            DelivioPartnerTheme {
                OrdersScreen(
                    orders = listOf(instantOrder),
                    activeFilter = "Ready",
                    onFilterChanged = {},
                    fulfillmentFilter = "Delivery",
                    onFulfillmentFilterChanged = {},
                    onOrderClick = {},
                    onAcceptClick = {},
                    onRejectClick = {},
                    onSetReadyClick = {},
                    onPrintReceiptClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("order_card_inst_card_1").assertIsDisplayed()
        composeRule.onNodeWithTag("instant_chip_inst_card_1", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("INSTANT").assertIsDisplayed()
        composeRule.onNodeWithText("Looking for a rider...").assertIsDisplayed()
    }
}
