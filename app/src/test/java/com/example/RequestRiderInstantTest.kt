package com.example

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AddressLocation
import com.example.data.model.MerchantSettings
import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.data.model.Rider
import com.example.data.model.RiderRequest
import com.example.data.repository.MerchantRepository
import com.example.ui.components.RiderTrackingModal
import com.example.ui.screens.OrderDetailScreen
import com.example.ui.screens.OrdersScreen
import com.example.ui.screens.RequestRiderScreen
import com.example.ui.theme.DelivioPartnerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h850dp-xxhdpi")
class RequestRiderInstantTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun buildInstantRiderOrderPayload_setsPaidOrCodAndRequiredFields() {
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

        val codPayload = repo.buildInstantRiderOrderPayload(
            merchantId = "m_branch_1",
            settings = settings,
            recipientName = "Juan Cruz",
            recipientPhone = "09171234567",
            deliveryAddress = "5th Ave, BGC",
            dropoffArea = "Taguig",
            dropoffLat = 14.5520,
            dropoffLng = 121.0480,
            whatToDeliver = "Cake box",
            relatedOrderNumber = "#1042",
            cashToCollect = 450.0,
            riderNote = "Fragile",
            distanceKm = 1.5,
            deliveryFee = 45.0,
            serviceFee = 0.0,
            orderNum = "#R1001",
            isCod = true
        )

        assertEquals(450.0, codPayload["cashToCollect"])
        assertEquals("cod", codPayload["paymentMethod"])
        assertEquals("merchant", codPayload["feePayer"])
        assertEquals("request_rider", codPayload["serviceType"])
        assertEquals("Ready", codPayload["status"])
        assertNull(codPayload["riderId"])
        assertEquals("m_branch_1", codPayload["merchantId"])
        assertEquals("Cafe Delivio - BGC Branch", codPayload["merchantName"])
        assertEquals("BGC Branch", codPayload["branchName"])
        assertEquals("BGC", codPayload["branchCode"])
        assertEquals("Juan Cruz", codPayload["recipientName"])
        assertEquals("09171234567", codPayload["recipientPhone"])
        assertEquals("#1042", codPayload["relatedOrder"])
        assertEquals("#1042", codPayload["relatedOrderNumber"])

        @Suppress("UNCHECKED_CAST")
        val dropoff = codPayload["dropoff"] as Map<String, Any?>
        assertEquals("Juan Cruz", dropoff["name"])
        assertEquals("09171234567", dropoff["phone"])
        assertEquals("5th Ave, BGC", dropoff["address"])
        assertEquals(14.5520, dropoff["lat"])
        assertEquals(121.0480, dropoff["lng"])
        assertEquals("Fragile", dropoff["note"])

        @Suppress("UNCHECKED_CAST")
        val fees = codPayload["fees"] as Map<String, Any?>
        assertEquals(45.0, fees["deliveryFee"])
        assertEquals("merchant", fees["feePayer"])

        @Suppress("UNCHECKED_CAST")
        val rrCash = codPayload["riderRequest"] as Map<String, Any?>
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
            cashToCollect = 200.0,
            riderNote = "",
            distanceKm = 2.0,
            deliveryFee = 45.0,
            serviceFee = 0.0,
            orderNum = "#R1002",
            isCod = false
        )

        assertEquals(0.0, paidPayload["cashToCollect"])
        assertEquals("paid", paidPayload["paymentMethod"])
    }

    @Test
    fun requestRiderScreen_step1ToStep2Flow_preservesDataAndHandlesPaidVsCod() {
        var submittedIsCod: Boolean? = null
        var submittedCash: Double? = null
        var submittedName = ""
        val settings = MerchantSettings(
            merchantId = "m_1",
            storeName = "Cafe Delivio",
            branchName = "BGC Branch",
            storeLocation = AddressLocation(
                street = "High Street, BGC",
                city = "Taguig",
                latitude = 14.5500,
                longitude = 121.0500
            )
        )

        composeRule.setContent {
            DelivioPartnerTheme {
                RequestRiderScreen(
                    settings = settings,
                    onBookRider = { name, _, _, _, _, _, _, _, isCod, cod, _, _, _ ->
                        submittedName = name
                        submittedIsCod = isCod
                        submittedCash = cod
                    },
                    onOpenLocationPicker = {},
                    onDismiss = {}
                )
            }
        }

        // Screen 1: Pin Point, Step 1 of 2
        composeRule.onNodeWithText("Pin Point").assertIsDisplayed()
        composeRule.onNodeWithText("Step 1 of 2").assertIsDisplayed()
        composeRule.onNodeWithText("Pick location").assertIsDisplayed()
        composeRule.onNodeWithText("Cafe Delivio - BGC Branch").assertIsDisplayed()
        composeRule.onNodeWithTag("step1_delivery_fee_row").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Delivery fee").assertIsDisplayed()

        // NEXT disabled until pin + address exist
        composeRule.onNodeWithTag("request_rider_next_btn").assertIsNotEnabled()

        // Open MapPickerScreen via Place Pin button and confirm location
        composeRule.onNodeWithTag("place_pin_btn").performScrollTo().performClick()
        composeRule.onNodeWithTag("confirm_location_picker_btn").assertIsDisplayed().performClick()

        // Back on Step 1: pin address is shown and NEXT is enabled
        composeRule.onNodeWithTag("confirmed_pin_address").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("rider_note_input").performScrollTo().performTextInput("Leave at lobby")
        composeRule.onNodeWithTag("request_rider_next_btn").assertIsEnabled().performClick()

        // Screen 2: Customer details, Step 2 of 2
        composeRule.onNodeWithText("Customer details").assertIsDisplayed()
        composeRule.onNodeWithText("Step 2 of 2").assertIsDisplayed()

        // Fill recipient details & Already paid check
        composeRule.onNodeWithTag("recipient_name_input").performScrollTo().performTextInput("Maria Santos")
        composeRule.onNodeWithTag("recipient_phone_input").performScrollTo().performTextInput("09171234567")
        composeRule.onNodeWithText("Paid Online", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("total_cost_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Delivery fee (paid by you)").assertIsDisplayed()

        // Switch to Cash on Delivery -> Order amount * required (> 0)
        composeRule.onNodeWithTag("payment_option_cod").performScrollTo().performClick()
        composeRule.onNodeWithTag("order_amount_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("book_rider_submit_btn").assertIsNotEnabled()

        composeRule.onNodeWithTag("order_amount_input").performTextInput("250")
        composeRule.onNodeWithTag("cod_rider_collects_text").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Rider collects ₱250.00 from the recipient").assertIsDisplayed()

        // Back arrow goes back to Step 1 preserving data, then Next returns to Step 2 with data intact
        composeRule.onNodeWithTag("request_rider_back_btn").performClick()
        composeRule.onNodeWithText("Pin Point").assertIsDisplayed()
        composeRule.onNodeWithText("Leave at lobby").assertIsDisplayed()
        composeRule.onNodeWithTag("request_rider_next_btn").assertIsEnabled().performClick()
        composeRule.onNodeWithText("Customer details").assertIsDisplayed()
        composeRule.onNodeWithText("Maria Santos").assertIsDisplayed()

        // Submit Cash on Delivery booking
        composeRule.onNodeWithTag("book_rider_submit_btn").assertIsEnabled().performClick()
        assertEquals("Maria Santos", submittedName)
        assertEquals(true, submittedIsCod)
        assertEquals(250.0, submittedCash ?: 0.0, 0.001)
    }

    @Test
    fun requestRiderScreen_showsInlineErrorWithRetryOnStep2() {
        val settings = MerchantSettings(
            storeName = "Test Shop",
            storeLocation = AddressLocation(street = "BGC", latitude = 14.55, longitude = 121.05)
        )
        composeRule.setContent {
            DelivioPartnerTheme {
                RequestRiderScreen(
                    settings = settings,
                    initialDropoffPinnedForTest = true,
                    submissionError = "permission-denied: Missing or insufficient permissions.",
                    onBookRider = { _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
                    onOpenLocationPicker = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("request_rider_next_btn").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("manual_booking_error_banner").assertIsDisplayed()
        composeRule.onNodeWithText("permission-denied: Missing or insufficient permissions.").assertIsDisplayed()
        composeRule.onNodeWithTag("manual_booking_retry_btn").assertIsDisplayed()
    }

    @Test
    fun callButtons_openDialerWithActionDialIntent() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = Shadows.shadowOf(app)
        shadowApp.clearNextStartedActivities()

        val order = Order(
            id = "ord_call_1",
            orderNumber = "#C101",
            customerName = "Ana Reyes",
            customerPhone = "09171112233",
            serviceType = "request_rider",
            fulfillment = "delivery",
            status = OrderStatus.READY,
            riderId = "r_1",
            riderName = "Carlo",
            riderPhone = "09184445566",
            riderRequest = RiderRequest(
                status = "Rider assigned",
                rider = Rider(id = "r_1", name = "Carlo", phone = "09184445566")
            )
        )

        composeRule.setContent {
            DelivioPartnerTheme {
                OrderDetailScreen(
                    order = order,
                    settings = MerchantSettings(storeName = "Test Shop"),
                    onBack = {},
                    onAccept = {},
                    onReject = {},
                    onSetReady = {},
                    onPrintReceipt = {}
                )
            }
        }

        composeRule.onNodeWithTag("call_customer_btn").performClick()
        val custIntent = shadowApp.nextStartedActivity
        assertEquals(Intent.ACTION_DIAL, custIntent.action)
        assertEquals(Uri.parse("tel:09171112233"), custIntent.data)

        composeRule.onNodeWithTag("call_rider_btn").performScrollTo().performClick()
        val riderIntent = shadowApp.nextStartedActivity
        assertEquals(Intent.ACTION_DIAL, riderIntent.action)
        assertEquals(Uri.parse("tel:09184445566"), riderIntent.data)
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
