package com.example.data.model

enum class SalesSourceFilter(val label: String) {
    ALL("All sales"),
    DELIVIO_ONLINE("Delivio online"),
    IN_STORE_POS("In-store (POS)")
}

enum class PosPaymentBucket(val label: String) {
    CASH("Cash"),
    GCASH("GCash"),
    MAYA("Maya"),
    CARD("Card"),
    OTHER("Other");

    companion object {
        fun fromRaw(raw: String): PosPaymentBucket {
            val clean = raw.trim().lowercase()
            return when {
                clean == "gcash" || clean.contains("gcash") -> GCASH
                clean == "maya" || clean == "paymaya" || clean.contains("maya") -> MAYA
                clean == "card" || clean.contains("card") || clean.contains("credit") || clean.contains("debit") -> CARD
                clean == "cash" || clean.contains("cash") -> CASH
                else -> OTHER
            }
        }
    }
}

data class PosOrderItem(
    val name: String = "Item",
    val variantLabel: String = "",
    val unitPrice: Double = 0.0,
    val qty: Int = 1,
    val voided: Boolean = false
) {
    val lineTotal: Double
        get() = if (voided) 0.0 else (unitPrice * qty.coerceAtLeast(0))
}

data class PosPayment(
    val method: String = "cash",
    val amount: Double = 0.0
) {
    val bucket: PosPaymentBucket
        get() = PosPaymentBucket.fromRaw(method)
}

data class PosRefund(
    val amount: Double = 0.0,
    val reason: String = "",
    val at: Long? = null
)

data class PosDiscountEntry(
    val kind: String = "Discount",
    val amount: Double = 0.0
)

data class PosOrder(
    val id: String = "",
    val shopId: String = "",
    val number: String = "",
    val type: String = "dine_in",       // "dine_in" | "take_out"
    val status: String = "paid",        // "open" | "sent" | "held" | "paid" | "voided" | "refunded"
    val tableId: String = "",
    val tableName: String = "",
    val items: List<PosOrderItem> = emptyList(),
    val subtotal: Double = 0.0,
    val discountTotal: Double = 0.0,
    val discountKind: String = "",
    val discounts: List<PosDiscountEntry> = emptyList(),
    val serviceCharge: Double = 0.0,
    val vatAmount: Double = 0.0,
    val total: Double = 0.0,
    val payments: List<PosPayment> = emptyList(),
    val tip: Double = 0.0,
    val cashierName: String = "",
    val shiftId: String = "",
    val refund: PosRefund? = null,
    val paidAt: Long? = null,
    val createdAt: Long? = null
) {
    /**
     * Counting rule:
     * A POS order counts as a sale ONLY when status is "paid" or "refunded" and it has paidAt.
     * "voided", "open", "sent", and "held" never count.
     */
    val isCountedSale: Boolean
        get() {
            val normalizedStatus = status.trim().lowercase()
            val statusCounts = normalizedStatus == "paid" || normalizedStatus == "refunded"
            return statusCounts && paidAt != null && paidAt > 0L
        }

    /**
     * Counting rule:
     * POS item sales = subtotal - discountTotal (service charge, tips, and VAT shown separately).
     */
    val itemSales: Double
        get() = (subtotal - discountTotal).coerceAtLeast(0.0)

    val refundAmount: Double
        get() = (refund?.amount ?: 0.0).coerceAtLeast(0.0)

    val netSales: Double
        get() = (itemSales - refundAmount).coerceAtLeast(0.0)

    val nonVoidedItemCount: Int
        get() = items.filter { !it.voided }.sumOf { it.qty.coerceAtLeast(0) }

    val tableLabel: String
        get() = tableName.ifBlank { tableId }.trim()

    val isTakeOut: Boolean
        get() = type.trim().lowercase().replace("-", "_") == "take_out"

    val isDineIn: Boolean
        get() = !isTakeOut

    val orderTypeDisplay: String
        get() = if (isTakeOut) "Take-Out" else "Dine-In"

    val effectiveTimestampMs: Long
        get() = paidAt ?: createdAt ?: 0L
}

data class PosShift(
    val id: String = "",
    val shopId: String = "",
    val cashierName: String = "",
    val openedAt: Long? = null,
    val closedAt: Long? = null,
    val openingCash: Double = 0.0,
    val closingCash: Double = 0.0,
    val expectedCash: Double = 0.0,
    val variance: Double = 0.0,
    val status: String = "open"
) {
    val isClosed: Boolean
        get() = status.trim().lowercase() == "closed" || (closedAt != null && closedAt > 0L)
}

data class PosLog(
    val id: String = "",
    val shopId: String = "",
    val type: String = "",       // "void" | "refund" | "discount" | "price_override" | "cash_movement" | "shift_open" | "shift_close"
    val amount: Double = 0.0,
    val reason: String = "",
    val byName: String = "",
    val approvedBy: String = "",
    val at: Long? = null
)

data class PlatformPosSettings(
    val timezone: String = "Asia/Manila",
    val showTryPosLink: Boolean = false,
    val tryPosUrl: String = ""
)

data class AppLinksSettings(
    val posUrl: String = "",
    val posEnabled: Boolean = false
) {
    val isPosAvailable: Boolean
        get() = posEnabled && isValidHttpsUrl(posUrl)

    companion object {
        fun isValidHttpsUrl(raw: String): Boolean {
            val trimmed = raw.trim()
            if (!trimmed.startsWith("https://", ignoreCase = true)) return false
            if (trimmed.any { it.isWhitespace() }) return false
            return try {
                val uri = java.net.URI(trimmed)
                uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
            } catch (_: Throwable) {
                false
            }
        }
    }
}
