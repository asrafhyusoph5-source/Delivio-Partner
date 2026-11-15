package com.example

import com.example.data.model.AddressLocation
import com.example.data.model.OrderStatus
import com.example.util.OrderParser
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderParserUnitTest {

    @Test
    fun parseOrderData_emptyOrNullFields_returnsSafeDefaultsWithoutOutletLoaded() {
        val data = hashMapOf<String, Any?>(
            "unknownField1" to "ignored",
            "weirdArray" to listOf(1, null, mapOf("a" to "b")),
            "promo" to null,
            "fees" to null,
            "scheduledFor" to null,
            "branchName" to null,
            "branchCode" to null,
            "chainId" to null,
            "items" to listOf<Any?>(
                null,
                "corrupted-string-item",
                hashMapOf<String, Any?>(
                    "id" to "i1",
                    "name" to "Iced Latte",
                    "quantity" to "2",
                    "unitPrice" to "125.50",
                    "regularUnitPrice" to 150L,
                    "variantKey" to "opt_large__opt_oat",
                    "variantLabel" to "Large / Oat Milk",
                    "variantImageUrl" to "https://example.com/latte.jpg",
                    "unknownItemKey" to true
                )
            )
        )

        val order = OrderParser.parseOrderData(
            docId = "ord_missing_fields_1",
            data = data,
            fallbackMerchantId = "",
            fallbackStoreLocation = AddressLocation()
        )
        assertNotNull(order)
        val parsed = order!!

        assertEquals("ord_missing_fields_1", parsed.id)
        assertEquals("", parsed.merchantId)
        assertEquals(OrderStatus.PLACED_ORDER, parsed.status)
        assertEquals(0L, parsed.placedAtTimestamp)
        assertNull(parsed.scheduledFor)
        assertNull(parsed.promo)
        assertTrue(parsed.feesRaw.isEmpty())
        assertEquals("", parsed.branchName)
        assertEquals("", parsed.branchCode)
        assertEquals("", parsed.chainId)
        assertEquals(1, parsed.items.size)
        val parsedItem = parsed.items.first()
        assertEquals("Iced Latte", parsedItem.name)
        assertEquals(2, parsedItem.quantity)
        assertEquals(125.50, parsedItem.unitPrice, 0.001)
        assertEquals(150.0, parsedItem.regularUnitPrice, 0.001)
        assertEquals("opt_large__opt_oat", parsedItem.variantKey)
        assertEquals("Large / Oat Milk", parsedItem.variantLabel)
        assertEquals("https://example.com/latte.jpg", parsedItem.variantImageUrl)
        assertEquals(251.0, parsed.subtotal, 0.01) // 2 * 125.50 computed fallback
        assertFalse(parsed.isPickUp)
    }

    @Test
    fun parseOrderData_acceptsNumbersStringsAndMultipleTimestampFormats() {
        val nowSeconds = 1770000000L
        val data = hashMapOf<String, Any?>(
            "orderNumber" to 1045L,
            "customerName" to "Ana Cruz",
            "customerPhone" to "09171234567",
            "subtotal" to "1,250.75",
            "deliveryFee" to 49L,
            "serviceFee" to 10.5f,
            "total" to "NaN", // invalid number string falls back cleanly to computed/0
            "placedAt" to Timestamp(nowSeconds, 0),
            "scheduledFor" to mapOf("_seconds" to (nowSeconds + 3600L), "_nanoseconds" to 0),
            "acceptedAt" to "2026-05-01T10:30:00Z",
            "deliveryAddress" to "123 Ayala Ave, Makati",
            "branchName" to "Makati Branch",
            "branchCode" to "MKT-01",
            "chainId" to "chain_delivio",
            "promo" to mapOf(
                "code" to "SAVE50",
                "discount" to "50",
                "fundedBy" to "merchant"
            ),
            "fees" to mapOf("platformFee" to 12.0, "extra" to null),
            "riderRequest" to mapOf(
                "status" to "Searching",
                "createdAt" to (nowSeconds * 1000L),
                "radiusKm" to "4.5",
                "alertsSent" to 3.0
            )
        )

        val order = OrderParser.parseOrderData(
            docId = "ord_mixed_types",
            data = data,
            fallbackMerchantId = "merch_1",
            fallbackStoreLocation = AddressLocation()
        )
        assertNotNull(order)
        val parsed = order!!

        assertEquals("#1045", parsed.orderNumber)
        assertEquals(1250.75, parsed.subtotal, 0.001)
        assertEquals(49.0, parsed.deliveryFee, 0.001)
        assertEquals(10.5, parsed.serviceFee, 0.001)
        assertEquals(nowSeconds * 1000L, parsed.placedAtTimestamp)
        assertEquals((nowSeconds + 3600L) * 1000L, parsed.scheduledFor)
        assertNotNull(parsed.acceptedAt)
        assertEquals("123 Ayala Ave, Makati", parsed.deliveryAddress.formatted)
        assertEquals("Makati Branch", parsed.branchName)
        assertEquals("MKT-01", parsed.branchCode)
        assertEquals("chain_delivio", parsed.chainId)
        assertEquals("SAVE50", parsed.promo?.code)
        assertEquals(50.0, parsed.promo?.discount ?: 0.0, 0.001)
        assertEquals(4.5, parsed.riderRequest?.radiusKm ?: 0.0, 0.001)
        assertEquals(3, parsed.riderRequest?.alertsSent)
    }

    @Test
    fun parseDocumentsSafely_skipsCorruptedDocLogsNonFatalAndReportsId() {
        val docs = listOf("good_1", "corrupt_99", "good_2")
        val reported = mutableListOf<Pair<String, String>>()

        val result = OrderParser.parseDocumentsSafely(
            documents = docs,
            idProvider = { it },
            dataProvider = { docId ->
                if (docId == "corrupt_99") {
                    throw IllegalStateException("Simulated malformed Firestore payload")
                }
                mapOf("customerName" to "User $docId", "subtotal" to 100.0)
            },
            fallbackMerchantId = "m1",
            nonFatalReporter = { id, err -> reported.add(id to err.message.orEmpty()) }
        )

        assertEquals(2, result.orders.size)
        assertEquals(listOf("good_1", "good_2"), result.orders.map { it.id })
        assertEquals(1, result.failedReports.size)
        assertEquals("corrupt_99", result.failedReports.first().orderId)
        assertEquals(1, reported.size)
        assertEquals("corrupt_99", reported.first().first)
        assertTrue(reported.first().second.contains("Simulated malformed Firestore payload"))
    }
}
