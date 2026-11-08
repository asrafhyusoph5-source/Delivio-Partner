package com.example

import com.example.data.model.Order
import com.example.data.model.OrderItem
import com.example.data.model.OrderStatus
import com.example.data.model.RiderRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderDeliveryOptionTest {

    @Test
    fun testDeliveryOptionDefaultsToPriority() {
        val orderEmpty = Order(deliveryOption = "")
        assertEquals("Priority", orderEmpty.deliveryOptionDisplay)

        val orderBlank = Order(deliveryOption = "   ")
        assertEquals("Priority", orderBlank.deliveryOptionDisplay)

        val orderPriority = Order(deliveryOption = "priority")
        assertEquals("Priority", orderPriority.deliveryOptionDisplay)

        val orderStandard = Order(deliveryOption = "Standard")
        assertEquals("Standard", orderStandard.deliveryOptionDisplay)

        val orderSaver = Order(deliveryOption = "saver")
        assertEquals("Saver", orderSaver.deliveryOptionDisplay)
    }

    @Test
    fun testStandardBatchHoldCondition() {
        val now = 1000000L
        val accepted = now - (3 * 60 * 1000L) // accepted 3 minutes ago
        val holdMinutes = 10

        val standardOrder = Order(
            status = OrderStatus.PREPARING,
            acceptedAt = accepted,
            deliveryOption = "Standard",
            dispatchHoldMinutes = holdMinutes
        )
        // Standard shows "Rider may be matched with another order"
        assertTrue(standardOrder.isRiderBatchHoldActive(now))
        assertEquals("Rider may be matched with another order", standardOrder.riderBatchHoldMessage(now))

        // Suppressed the moment the order is marked Ready (earlier of hold or Ready)
        val readyOrder = standardOrder.copy(status = OrderStatus.READY)
        assertFalse("Standard order marked ready suppresses hold display", readyOrder.isRiderBatchHoldActive(now))

        // Suppressed when hold expires (11 minutes passed, hold is 10 min)
        val expiredOrder = standardOrder.copy(acceptedAt = now - (11 * 60 * 1000L))
        assertFalse("Standard order after hold expires suppresses hold display", expiredOrder.isRiderBatchHoldActive(now))
    }

    @Test
    fun testSaverBatchHoldConditionAndCountdown() {
        val now = 1000000L
        val accepted = now - (3 * 60 * 1000L) // accepted 3 minutes ago
        val holdMinutes = 10 // hold end is now + 7 min = 420 seconds = 07:00

        val saverOrder = Order(
            status = OrderStatus.PREPARING,
            acceptedAt = accepted,
            deliveryOption = "Saver",
            dispatchHoldMinutes = holdMinutes
        )

        // Saver shows "Rider search starts in mm:ss"
        assertTrue(saverOrder.isRiderBatchHoldActive(now))
        assertEquals("Rider search starts in 07:00", saverOrder.riderBatchHoldMessage(now))

        // Even if marked Ready earlier, Saver STILL shows the countdown!
        val saverReadyEarly = saverOrder.copy(status = OrderStatus.READY, readyAt = now)
        assertTrue("Saver order marked ready still shows countdown until hold expires", saverReadyEarly.isRiderBatchHoldActive(now))
        assertEquals("Rider search starts in 07:00", saverReadyEarly.riderBatchHoldMessage(now))

        // When hold expires (10 minutes passed): stops showing
        val saverExpired = saverOrder.copy(acceptedAt = now - (10 * 60 * 1000L))
        assertFalse("Saver order after hold expires stops showing countdown", saverExpired.isRiderBatchHoldActive(now))
    }

    @Test
    fun testNoRiderYet5MinutesAfterHoldEndsForSaver() {
        val now = 2000000L
        val holdMinutes = 10
        val accepted = now - (12 * 60 * 1000L) // accepted 12 minutes ago (hold was 10 min, so hold ended 2 min ago)

        val req = RiderRequest(status = "Searching", requestedAt = accepted)
        val saverOrder = Order(
            status = OrderStatus.READY,
            acceptedAt = accepted,
            readyAt = now - (8 * 60 * 1000L), // marked ready early at 4 minutes in
            deliveryOption = "Saver",
            dispatchHoldMinutes = holdMinutes,
            riderRequest = req
        )

        // Hold ended at accepted + 10 min = 2 minutes ago.
        // "No rider yet" appears only 5 minutes after that hold ends (i.e. at accepted + 15 min).
        // Since accepted was 12 min ago, only 2 min passed since hold ended. So STILL searching!
        assertFalse("Saver order 2 min after hold end is still searching", saverOrder.isNoRiderYet(now))

        // At 14 minutes (4 min after hold end): still searching
        val at14Min = saverOrder.copy(acceptedAt = now - (14 * 60 * 1000L))
        assertFalse("Saver order 4 min after hold end is still searching", at14Min.isNoRiderYet(now))

        // At 15 minutes (5 min after hold end): turns to No rider yet
        val at15Min = saverOrder.copy(acceptedAt = now - (15 * 60 * 1000L))
        assertTrue("Saver order 5 min after hold end turns to No rider yet", at15Min.isNoRiderYet(now))
    }

    @Test
    fun testNoRiderYet5MinutesAfterHoldEndsForStandard() {
        val now = 2000000L
        val holdMinutes = 10

        // Case 1: Standard marked Ready at 4 minutes in.
        // Hold ended at 4 minutes (the moment it was marked Ready).
        // 5 minutes after hold ends = 4 + 5 = 9 minutes after accepted!
        val accepted1 = now - (8 * 60 * 1000L) // 8 min after accepted (4 min after ready)
        val readyAt1 = now - (4 * 60 * 1000L) // marked ready 4 min ago
        val standardOrderEarlyReady = Order(
            status = OrderStatus.READY,
            acceptedAt = accepted1,
            readyAt = readyAt1,
            deliveryOption = "Standard",
            dispatchHoldMinutes = holdMinutes,
            riderRequest = RiderRequest(status = "Searching", requestedAt = accepted1)
        )
        // 4 minutes passed since Ready: 4 < 5, so still searching
        assertFalse("Standard 4 min after early ready is still searching", standardOrderEarlyReady.isNoRiderYet(now))

        // 5 minutes passed since Ready: 5 >= 5, turns to No rider yet
        val ready5MinAgo = standardOrderEarlyReady.copy(
            readyAt = now - (5 * 60 * 1000L)
        )
        assertTrue("Standard 5 min after early ready turns to No rider yet", ready5MinAgo.isNoRiderYet(now))

        // Case 2: Standard not marked ready early (hold ended at 10 minutes).
        // 5 minutes after 10 min hold = 15 minutes!
        val standardOrderNotReady = Order(
            status = OrderStatus.PREPARING,
            acceptedAt = now - (14 * 60 * 1000L),
            deliveryOption = "Standard",
            dispatchHoldMinutes = holdMinutes,
            riderRequest = RiderRequest(status = "Searching", requestedAt = now - (14 * 60 * 1000L))
        )
        assertFalse("Standard 14 min (4 min after hold) is still searching", standardOrderNotReady.isNoRiderYet(now))

        val standard15Min = standardOrderNotReady.copy(acceptedAt = now - (15 * 60 * 1000L))
        assertTrue("Standard 15 min (5 min after hold) turns to No rider yet", standard15Min.isNoRiderYet(now))
    }

    @Test
    fun testPriorityNoRiderYetAt5Minutes() {
        val now = 3000000L
        val req = RiderRequest(status = "Searching", requestedAt = now - (4 * 60 * 1000L))
        val priorityOrder = Order(
            status = OrderStatus.PREPARING,
            acceptedAt = now - (4 * 60 * 1000L),
            deliveryOption = "Priority",
            dispatchHoldMinutes = 0,
            riderRequest = req
        )

        assertFalse("Priority at 4 minutes is still searching", priorityOrder.isNoRiderYet(now))

        val priority5Min = priorityOrder.copy(
            acceptedAt = now - (5 * 60 * 1000L),
            riderRequest = req.copy(requestedAt = now - (5 * 60 * 1000L))
        )
        assertTrue("Priority turns to No rider yet at 5 minutes", priority5Min.isNoRiderYet(now))
    }

    @Test
    fun testFormattedOrderNumber() {
        // With leading '#'
        val order1 = Order(id = "doc123456", orderNumber = "#1042")
        assertEquals("#1042", order1.formattedOrderNumber)

        // Without leading '#'
        val order2 = Order(id = "doc123456", orderNumber = "1042")
        assertEquals("#1042", order2.formattedOrderNumber)

        // Blank order number: fallback to last 6 characters of document ID in uppercase
        val order3 = Order(id = "doc_abc999", orderNumber = "")
        assertEquals("#ABC999", order3.formattedOrderNumber)

        val order4 = Order(id = "xyz", orderNumber = "")
        assertEquals("#XYZ", order4.formattedOrderNumber)
    }

    @Test
    fun testEffectivePaymentLabelOnlySubtotalAndPaymentMethod() {
        // Pick Up paid in cash -> show real overall total (fees.total / order.total)
        val pickUpCash = Order(
            fulfillment = "pickup",
            paymentMethod = "Cash",
            subtotal = 350.0,
            deliveryFee = 50.0,
            serviceFee = 15.0,
            total = 415.0
        )
        assertEquals("Pay at pickup - collect ₱415.00", pickUpCash.effectivePaymentLabel)

        // Pick Up paid in COD -> show real overall total
        val pickUpCod = Order(
            fulfillment = "pickup",
            paymentMethod = "COD",
            subtotal = 120.0,
            total = 135.0
        )
        assertEquals("Pay at pickup - collect ₱135.00", pickUpCod.effectivePaymentLabel)

        // Pick Up paid online (GCash, Maya, Wallet) -> "Paid online - nothing to collect"
        val pickUpOnline = Order(
            fulfillment = "pickup",
            paymentMethod = "GCash",
            subtotal = 200.0,
            total = 215.0
        )
        assertEquals("Paid online - nothing to collect", pickUpOnline.effectivePaymentLabel)

        val pickUpWallet = Order(
            fulfillment = "pickup",
            paymentMethod = "wallet",
            subtotal = 200.0,
            total = 210.0
        )
        assertEquals("Paid online - nothing to collect", pickUpWallet.effectivePaymentLabel)

        // Delivery paid in cash / COD
        val deliveryCod = Order(
            fulfillment = "delivery",
            paymentMethod = "Cash on Delivery",
            subtotal = 500.0
        )
        assertEquals("Cash on Delivery", deliveryCod.effectivePaymentLabel)

        // Delivery paid online / wallet
        val deliveryWallet = Order(
            fulfillment = "delivery",
            paymentMethod = "wallet",
            subtotal = 500.0
        )
        assertEquals("Paid online", deliveryWallet.effectivePaymentLabel)
    }

    @Test
    fun testOnAnotherDeliveryStatusMatchesReadyTab() {
        assertEquals(OrderStatus.ON_ANOTHER_DELIVERY, OrderStatus.fromString("On another delivery"))
        assertEquals(OrderStatus.ON_ANOTHER_DELIVERY, OrderStatus.fromString("on_another_delivery"))
        assertTrue(
            com.example.util.PlatformOrderStatuses.matchesTab(
                "On another delivery",
                com.example.util.PlatformOrderStatuses.MerchantOrderTab.READY
            )
        )
    }

    @Test
    fun testReceiptPrinterSubtotalOnlyAndOrderNumberLine() {
        val order = Order(
            id = "doc_xyz789",
            orderNumber = "4001",
            customerName = "Maria Clara",
            items = listOf(
                com.example.data.model.OrderItem(
                    name = "Classic Burger",
                    quantity = 2,
                    price = 150.0
                )
            ),
            subtotal = 300.0,
            deliveryFee = 49.0,
            serviceFee = 10.0,
            discount = 20.0,
            total = 339.0
        )

        val receiptText = com.example.util.ReceiptPrinterHelper.generateFormattedReceiptText(
            order = order,
            storeName = "Burger Joint",
            paperSize = "80mm"
        )

        // Check Order # line directly under Delivio on both copies
        assertTrue(receiptText.contains("Delivio\nOrder #4001"))

        // Must show Subtotal
        assertTrue(receiptText.contains("Subtotal"))

        // Must NEVER show Total line or fees
        assertFalse(receiptText.contains("Total              ₱"))
        assertFalse(receiptText.contains("Delivery fee"))
        assertFalse(receiptText.contains("Service fee"))
        assertFalse(receiptText.contains("Discount"))
        assertFalse(receiptText.contains("VAT"))

        // Test with custom ReceiptDesign
        val customDesign = com.example.data.model.ReceiptDesign(
            brandLine = "Delivio Express",
            separator = "solid"
        )
        val customText = com.example.util.ReceiptPrinterHelper.generateFormattedReceiptText(
            order = order,
            storeName = "Burger Joint",
            paperSize = "80mm",
            design = customDesign
        )
        assertTrue(customText.contains("Delivio Express\nOrder #4001"))
        assertTrue(customText.contains("─"))
        assertFalse(customText.contains("Total              ₱"))
    }

    @Test
    fun testScheduledOrderVisibilitySelfCheck() {
        // Self-check: a customer order for 8:00 PM is invisible at 5:00 PM and appears at 8:00 PM.
        val zoneId = java.time.ZoneId.of("Asia/Manila")
        val fivePm = java.time.ZonedDateTime.of(2026, 11, 6, 17, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val sevenFortyFourPm = java.time.ZonedDateTime.of(2026, 11, 6, 19, 44, 0, 0, zoneId).toInstant().toEpochMilli()
        val sevenFortyFivePm = java.time.ZonedDateTime.of(2026, 11, 6, 19, 45, 0, 0, zoneId).toInstant().toEpochMilli()
        val eightPm = java.time.ZonedDateTime.of(2026, 11, 6, 20, 0, 0, 0, zoneId).toInstant().toEpochMilli()

        val order = Order(
            id = "order_sched_8pm",
            scheduledFor = eightPm,
            status = OrderStatus.PLACED_ORDER
        )

        // Default lead minutes = 0 (exact time)
        assertFalse("At 5:00 PM order for 8:00 PM is invisible", order.isScheduledDue(fivePm, leadMinutes = 0))
        assertFalse("At 7:45 PM order for 8:00 PM with lead=0 is invisible", order.isScheduledDue(sevenFortyFivePm, leadMinutes = 0))
        assertTrue("At 8:00 PM order for 8:00 PM appears", order.isScheduledDue(eightPm, leadMinutes = 0))

        // When scheduledReleaseLeadMinutes = 15
        assertFalse("At 5:00 PM order for 8:00 PM with lead=15 is invisible", order.isScheduledDue(fivePm, leadMinutes = 15))
        assertFalse("At 7:44 PM order for 8:00 PM with lead=15 is invisible", order.isScheduledDue(sevenFortyFourPm, leadMinutes = 15))
        assertTrue("At 7:45 PM order for 8:00 PM with lead=15 appears", order.isScheduledDue(sevenFortyFivePm, leadMinutes = 15))
    }

    @Test
    fun testReceiptShowsScheduledForTime() {
        val zoneId = java.time.ZoneId.of("Asia/Manila")
        val eightPm = java.time.ZonedDateTime.of(2026, 11, 6, 20, 0, 0, 0, zoneId).toInstant().toEpochMilli()

        val scheduledOrder = Order(
            id = "ord_sched",
            orderNumber = "1234",
            scheduledFor = eightPm,
            items = listOf(OrderItem(name = "Pizza", quantity = 1, price = 250.0)),
            subtotal = 250.0,
            total = 250.0
        )

        val receiptText = com.example.util.ReceiptPrinterHelper.generateFormattedReceiptText(
            order = scheduledOrder,
            storeName = "Pizza Hub",
            paperSize = "80mm"
        )
        assertTrue("Receipt contains 'Scheduled for 8:00 PM'", receiptText.contains("Scheduled for 8:00 PM"))

        val immediateOrder = Order(
            id = "ord_imm",
            orderNumber = "1235",
            scheduledFor = null,
            items = listOf(OrderItem(name = "Pizza", quantity = 1, price = 250.0)),
            subtotal = 250.0,
            total = 250.0
        )
        val immReceiptText = com.example.util.ReceiptPrinterHelper.generateFormattedReceiptText(
            order = immediateOrder,
            storeName = "Pizza Hub",
            paperSize = "80mm"
        )
        assertFalse("Immediate order receipt does NOT contain 'Scheduled for'", immReceiptText.contains("Scheduled for"))
    }

    @Test
    fun testFulfillmentClassificationDeliveryAndPickUpOnly() {
        val zoneId = java.time.ZoneId.of("Asia/Manila")
        val eightPm = java.time.ZonedDateTime.of(2026, 11, 6, 20, 0, 0, 0, zoneId).toInstant().toEpochMilli()

        // Scheduled Delivery order
        val schedDelivery = Order(
            id = "ord_1",
            fulfillment = "delivery",
            scheduledFor = eightPm
        )
        assertTrue(schedDelivery.isDelivery)
        assertFalse(schedDelivery.isPickUp)
        assertTrue(schedDelivery.isScheduled)

        // Scheduled Pick Up order
        val schedPickup = Order(
            id = "ord_2",
            fulfillment = "pickup",
            scheduledFor = eightPm
        )
        assertFalse(schedPickup.isDelivery)
        assertTrue(schedPickup.isPickUp)
        assertTrue(schedPickup.isScheduled)
    }
}

