package com.example

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AddressLocation
import com.example.data.model.MerchantSettings
import com.example.data.model.NightConfig
import com.example.data.model.Order
import com.example.data.model.OrderPromo
import com.example.data.model.OrderStatus
import com.example.data.model.PeakConfig
import com.example.data.model.PricingSnapshot
import com.example.data.model.ServiceFeeConfig
import com.example.data.model.ServiceRate
import com.example.data.repository.MerchantRepository
import com.example.ui.screens.EarningsScreen
import com.example.ui.screens.RequestRiderScreen
import com.example.ui.theme.DelivioPartnerTheme
import com.example.util.OrderDocumentParser
import com.example.util.PricingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h850dp-xxhdpi")
class PricingAndEarningsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pricingEngine_schema2InstantQuote_calculatesDeliveryPeakNightAndServiceFee() {
        val rate = ServiceRate(
            baseFare = 50.0,
            includedKm = 2.0,
            perKmRate = 15.0,
            minFee = 50.0,
            maxFee = 200.0,
            schemaVersion = 2,
            ratesVersion = 4L,
            serviceKey = "instant",
            serviceFeeConfig = ServiceFeeConfig(mode = "percent", value = 10.0),
            peakConfig = PeakConfig(enabled = true, multiplier = 1.25),
            nightConfig = NightConfig(enabled = true, surcharge = 20.0)
        )

        // 4.0 km -> base 50 + (4-2)*15 = 80.0
        // peak (1.25) -> 100.0, night (+20) -> 120.0
        // serviceFee 10% of 120.0 -> 12.0, total = 132.0
        val quotePeakNight = PricingEngine.calculateInstantQuote(
            rate = rate,
            distanceKm = 4.0,
            forcePeak = true,
            forceNight = true
        )
        assertEquals(80.0, quotePeakNight.baseDeliveryFee, 0.001)
        assertEquals(120.0, quotePeakNight.deliveryFee, 0.001)
        assertEquals(12.0, quotePeakNight.serviceFee, 0.001)
        assertEquals(132.0, quotePeakNight.totalFee, 0.001)
        assertEquals(4L, quotePeakNight.ratesVersion)
        assertEquals(2, quotePeakNight.schemaVersion)

        // Legacy schema 1 fallback: flat serviceFee + clamped delivery fee, no peak/night
        val legacyRate = rate.copy(schemaVersion = 1, serviceFee = 15.0)
        val legacyQuote = PricingEngine.calculateInstantQuote(
            rate = legacyRate,
            distanceKm = 4.0,
            forcePeak = true,
            forceNight = true
        )
        assertEquals(80.0, legacyQuote.deliveryFee, 0.001)
        assertEquals(15.0, legacyQuote.serviceFee, 0.001)
        assertEquals(95.0, legacyQuote.totalFee, 0.001)
    }

    @Test
    fun buildInstantRiderOrderPayload_writesFeesAndPricingSnapshotWithPayerMerchant() {
        val repo = MerchantRepository.getInstance(ApplicationProvider.getApplicationContext())
        val settings = MerchantSettings(
            merchantId = "m_1",
            storeName = "Deli Shop",
            cityId = "taguig",
            commissionRate = 0.12,
            storeLocation = AddressLocation(street = "BGC", city = "Taguig", latitude = 14.55, longitude = 121.05)
        )

        val payload = repo.buildInstantRiderOrderPayload(
            merchantId = "m_1",
            settings = settings,
            recipientName = "Ana",
            recipientPhone = "09171234567",
            deliveryAddress = "Makati Ave",
            dropoffArea = "Makati",
            dropoffLat = 14.56,
            dropoffLng = 121.03,
            whatToDeliver = "Box",
            relatedOrderNumber = "",
            cashToCollect = 0.0,
            riderNote = "",
            distanceKm = 3.5,
            deliveryFee = 85.0,
            serviceFee = 15.0,
            orderNum = "#R9001",
            isCod = false,
            ratesVersion = 7L,
            pricingSchema = 2
        )

        @Suppress("UNCHECKED_CAST")
        val fees = payload["fees"] as Map<String, Any?>
        assertEquals(85.0, fees["deliveryFee"])
        assertEquals(15.0, fees["serviceFee"])
        assertEquals(0.0, fees["tip"])
        assertEquals(0.0, fees["vat"])
        assertEquals(0.0, fees["subtotal"])
        assertEquals(100.0, fees["total"])

        @Suppress("UNCHECKED_CAST")
        val pricing = payload["pricing"] as Map<String, Any?>
        assertEquals(2, pricing["schema"])
        assertEquals(7L, pricing["ratesVersion"])
        assertEquals("instant", pricing["serviceKey"])
        assertEquals("motorcycle", pricing["vehicle"])
        assertEquals(85.0, pricing["deliveryFee"])
        assertEquals(15.0, pricing["serviceFee"])
        assertEquals(0.0, pricing["tip"])
        assertEquals(0.0, pricing["vat"])
        assertEquals(0.0, pricing["itemsSubtotal"])
        assertEquals(100.0, pricing["total"])
        assertEquals("merchant", pricing["payer"])
        assertEquals(12.0, pricing["merchantCommissionRate"])
    }

    @Test
    fun orderDocumentParser_parsesPricingSnapshotAndCommissionRateVariants() {
        val rawOrderWithPricing = mapOf<String, Any?>(
            "orderNumber" to "#5501",
            "customerName" to "Ben",
            "status" to "Delivered",
            "fees" to mapOf("subtotal" to 400.0, "deliveryFee" to 50.0, "serviceFee" to 10.0, "vat" to 0.0, "total" to 460.0),
            "pricing" to mapOf(
                "schema" to 2,
                "ratesVersion" to 3,
                "serviceKey" to "food",
                "vehicle" to "motorcycle",
                "deliveryFee" to 50.0,
                "serviceFee" to 10.0,
                "tip" to 20.0,
                "vat" to 0.0,
                "itemsSubtotal" to 400.0,
                "total" to 480.0,
                "payer" to "customer",
                "merchantCommissionRate" to 12.0
            )
        )

        val parsed = OrderDocumentParser.parseOrderDataSafe("ord_5501", "m_1") { rawOrderWithPricing }
        assertNotNull(parsed)
        assertNotNull(parsed!!.pricing)
        assertEquals(2, parsed.pricing!!.schema)
        assertEquals(3L, parsed.pricing!!.ratesVersion)
        assertEquals("food", parsed.pricing!!.serviceKey)
        assertEquals(0.12, parsed.pricing!!.merchantCommissionRate ?: 0.0, 0.0001)
        assertEquals(0.12, parsed.effectiveCommissionRate(0.15), 0.0001)

        // Legacy order without pricing snapshot -> pricing == null -> falls back to settings.commissionRate
        val legacyOrder = OrderDocumentParser.parseOrderDataSafe("ord_legacy", "m_1") {
            mapOf("orderNumber" to "#100", "subtotal" to 300.0, "status" to "Delivered")
        }
        assertNotNull(legacyOrder)
        assertNull(legacyOrder!!.pricing)
        assertEquals(0.18, legacyOrder.effectiveCommissionRate(0.18), 0.0001)
    }

    @Test
    fun requestRiderScreen_step2ShowsDeliveryFeeServiceFeeAndTotalCostAndButton() {
        var capturedDelivery = 0.0
        var capturedService = 0.0
        var capturedRatesVer = 0L
        var capturedSchema = 0

        val rate = ServiceRate(
            baseFare = 60.0,
            includedKm = 2.0,
            perKmRate = 10.0,
            minFee = 60.0,
            maxFee = 300.0,
            schemaVersion = 2,
            ratesVersion = 5L,
            serviceKey = "instant",
            serviceFeeConfig = ServiceFeeConfig(mode = "flat", value = 15.0)
        )

        val settings = MerchantSettings(
            merchantId = "m_1",
            storeName = "Test Kitchen",
            storeLocation = AddressLocation(street = "BGC", city = "Taguig", latitude = 14.5500, longitude = 121.0500)
        )

        composeRule.setContent {
            DelivioPartnerTheme {
                RequestRiderScreen(
                    settings = settings,
                    initialDropoffPinnedForTest = true,
                    initialRate = rate,
                    onBookRider = { _, _, _, _, _, _, _, _, _, _, _, _, delFee, svcFee, rVer, schemaVer ->
                        capturedDelivery = delFee
                        capturedService = svcFee
                        capturedRatesVer = rVer
                        capturedSchema = schemaVer
                    },
                    onOpenLocationPicker = {},
                    onDismiss = {}
                )
            }
        }

        // Advance to Step 2
        composeRule.onNodeWithTag("request_rider_next_btn").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("recipient_name_input").performScrollTo().performTextInput("Liza Soberano")
        composeRule.onNodeWithTag("recipient_phone_input").performScrollTo().performTextInput("09171234567")

        composeRule.onNodeWithTag("total_cost_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Delivery fee (paid by you)").assertIsDisplayed()
        composeRule.onNodeWithText("Service fee").assertIsDisplayed()
        composeRule.onNodeWithTag("step2_delivery_fee_value").assertIsDisplayed()
        composeRule.onNodeWithTag("step2_service_fee_value").assertIsDisplayed()
        composeRule.onNodeWithTag("step2_total_cost_value").assertIsDisplayed()

        // Submit and verify fees & metadata forwarded cleanly
        composeRule.onNodeWithTag("book_rider_submit_btn").assertIsEnabled().performClick()
        assertTrue(capturedDelivery >= 60.0)
        assertEquals(15.0, capturedService, 0.001)
        assertEquals(5L, capturedRatesVer)
        assertEquals(2, capturedSchema)
    }

    @Test
    fun earningsScreen_usesOrderSnapshotRatesVouchersRiderFeesAndFixesEmptyPayoutCard() {
        val now = System.currentTimeMillis()
        // Order 1: old snapshot rate 10% on 1,000 subtotal with no voucher -> commission 100, net 900
        val orderSnap10 = Order(
            id = "o1",
            orderNumber = "#1001",
            customerName = "Alice",
            subtotal = 1000.0,
            status = OrderStatus.DELIVERED,
            placedAtTimestamp = now,
            pricing = PricingSnapshot(
                schema = 2,
                itemsSubtotal = 1000.0,
                merchantCommissionRate = 0.10
            )
        )
        // Order 2: no snapshot -> fallback to current shop rate 20%, subtotal 500 - voucher 100 = 400 -> comm 80, net 320
        val orderLegacy20 = Order(
            id = "o2",
            orderNumber = "#1002",
            customerName = "Bob",
            subtotal = 500.0,
            promo = OrderPromo(code = "PROMO100", discount = 100.0, fundedBy = "merchant"),
            status = OrderStatus.DELIVERED,
            placedAtTimestamp = now,
            pricing = null
        )
        // Instant rider request: delivery 80 + service 20 = 100
        val instantReq = Order(
            id = "o3",
            orderNumber = "#R3003",
            customerName = "Charlie",
            serviceType = "request_rider",
            status = OrderStatus.DELIVERED,
            deliveryFee = 80.0,
            serviceFee = 20.0,
            total = 100.0,
            placedAtTimestamp = now,
            pricing = PricingSnapshot(
                schema = 2,
                serviceKey = "instant",
                deliveryFee = 80.0,
                serviceFee = 20.0,
                total = 100.0,
                payer = "merchant"
            )
        )

        var editBankClicked = false

        // Test 1: No payout account saved -> shows "Add payout account" tappable row, never blank bank line or pencil
        composeRule.setContent {
            DelivioPartnerTheme {
                EarningsScreen(
                    settings = MerchantSettings(
                        merchantId = "m1",
                        storeName = "Test",
                        commissionRate = 0.20,
                        bankName = "",
                        accountHolder = "",
                        accountNumber = ""
                    ),
                    orders = listOf(orderSnap10, orderLegacy20, instantReq),
                    payouts = emptyList(),
                    period = "Today",
                    onPeriodChange = {},
                    onRequestPayoutClick = {},
                    onEditBankDetailsClick = { editBankClicked = true }
                )
            }
        }

        // Net = 1500 - 180 (100+80) - 100 (voucher) = ₱1,220.00
        composeRule.onNodeWithText("Commission Fee").assertIsDisplayed()
        composeRule.onNodeWithTag("earnings_net_total").assertTextEquals("₱1,220.00")
        composeRule.onNodeWithTag("earnings_commission_fee_total").assertTextEquals("- ₱180.00")
        composeRule.onNodeWithTag("earnings_vouchers_total").assertTextEquals("- ₱100.00")

        // Payout card shows tappable "Add payout account" and hides pencil button
        composeRule.onNodeWithTag("add_payout_account_row").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(editBankClicked)

        // Transaction history lines show order#, customer, subtotal, and per-order commission rate
        composeRule.onNodeWithText("#1001 • Alice").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Commission Fee (10%): -₱100.00").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Subtotal: ₱1,000.00").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("#1002 • Bob").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Commission Fee (20%): -₱80.00 • Vouchers: -₱100.00").performScrollTo().assertIsDisplayed()

        // Instant rider fees deduction section
        composeRule.onNodeWithText("Rider fees (delivery fee + service fee)").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("instant_rider_fees_total").assertTextEquals("- ₱100.00")
        composeRule.onNodeWithText("Delivery ₱80.00 + Service ₱20.00").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun earningsScreen_showsSavedPayoutAccountWithMaskedNumber() {
        composeRule.setContent {
            DelivioPartnerTheme {
                EarningsScreen(
                    settings = MerchantSettings(
                        merchantId = "m1",
                        storeName = "Test",
                        bankName = "GCash",
                        accountHolder = "Maria Santos",
                        accountNumber = "09171235678"
                    ),
                    orders = emptyList(),
                    payouts = emptyList(),
                    period = "Today",
                    onPeriodChange = {},
                    onRequestPayoutClick = {},
                    onEditBankDetailsClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("payout_account_provider").assertIsDisplayed()
        composeRule.onNodeWithText("GCash").assertIsDisplayed()
        composeRule.onNodeWithText("•••• 5678 • Maria Santos").assertIsDisplayed()
        composeRule.onAllNodesWithText("Add payout account").assertCountEquals(0)
    }
}
