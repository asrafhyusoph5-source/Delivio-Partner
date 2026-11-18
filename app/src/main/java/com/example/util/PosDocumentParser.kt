package com.example.util

import com.example.data.model.AppLinksSettings
import com.example.data.model.PlatformPosSettings
import com.example.data.model.PosDiscountEntry
import com.example.data.model.PosLog
import com.example.data.model.PosOrder
import com.example.data.model.PosOrderItem
import com.example.data.model.PosPayment
import com.example.data.model.PosRefund
import com.example.data.model.PosShift
import com.google.firebase.firestore.DocumentSnapshot
import java.util.TimeZone

/**
 * Schema-tolerant, defensive parser for Delivio POS Firestore documents (`posOrders`, `posShifts`, `posLogs`)
 * and `settings/platform` POS / timezone configuration.
 *
 * Every field is optional with safe defaults; wrong types and bad timestamps never throw uncaught exceptions;
 * each document is parsed inside its own try/catch so one corrupted record is skipped safely.
 */
object PosDocumentParser {

    fun parsePlatformPosSettings(data: Map<String, Any?>?): PlatformPosSettings {
        if (data == null) return PlatformPosSettings()
        return try {
            val rawTz = OrderDocumentParser.safeString(data["timezone"])?.trim().orEmpty()
            val validTz = if (rawTz.isNotBlank() && TimeZone.getAvailableIDs().contains(rawTz)) {
                rawTz
            } else {
                "Asia/Manila"
            }

            val posSectionMap = OrderDocumentParser.safeMap(data["pos"]).ifEmpty {
                OrderDocumentParser.safeMap(data["delivioPos"])
            }.ifEmpty {
                OrderDocumentParser.safeMap(data["posLink"])
            }.ifEmpty {
                OrderDocumentParser.safeMap(data["links"])
            }

            val directBoolCandidates = listOf(
                data["posLinkEnabled"],
                data["showPosLink"],
                data["tryPosLink"],
                data["tryPosEnabled"],
                data["tryDelivioPosEnabled"],
                data["posPromoEnabled"],
                data["showTryPosLink"],
                data["showTryDelivioPos"],
                posSectionMap["linkEnabled"],
                posSectionMap["showLink"],
                posSectionMap["showPosLink"],
                posSectionMap["tryPosEnabled"],
                posSectionMap["enabled"]
            )

            val explicitUrl = listOf(
                data["posLinkUrl"],
                data["posUrl"],
                data["tryPosUrl"],
                data["delivioPosUrl"],
                data["posDownloadUrl"],
                posSectionMap["url"],
                posSectionMap["linkUrl"],
                posSectionMap["link"],
                posSectionMap["pos"]
            ).firstNotNullOfOrNull { OrderDocumentParser.safeString(it)?.trim()?.takeIf { s -> s.isNotBlank() } }
                ?: (data["posLink"] as? String)?.trim()?.takeIf { it.startsWith("http", ignoreCase = true) }
                ?: ""

            val flagEnabled = directBoolCandidates.any { raw ->
                raw != null && OrderDocumentParser.safeBoolean(raw, default = false)
            }

            PlatformPosSettings(
                timezone = validTz,
                showTryPosLink = flagEnabled || (explicitUrl.isNotBlank() && data["posLinkEnabled"] != false && data["showPosLink"] != false),
                tryPosUrl = explicitUrl
            )
        } catch (_: Throwable) {
            PlatformPosSettings()
        }
    }

    fun parseAppLinksSettings(data: Map<String, Any?>?): AppLinksSettings {
        if (data == null) return AppLinksSettings()
        return try {
            val url = OrderDocumentParser.safeString(data["posUrl"])?.trim().orEmpty()
            val enabled = OrderDocumentParser.safeBoolean(data["posEnabled"], default = false)
            AppLinksSettings(
                posUrl = url,
                posEnabled = enabled
            )
        } catch (_: Throwable) {
            AppLinksSettings()
        }
    }

    fun parsePosOrderSnapshotSafe(
        doc: DocumentSnapshot,
        shopId: String,
        onParseError: (docId: String, documentPath: String, error: Throwable) -> Unit = { _, _, _ -> }
    ): PosOrder? = parsePosOrderDataSafe(
        docId = doc.id,
        shopId = shopId,
        onParseError = onParseError,
        dataProvider = { doc.data }
    )

    inline fun parsePosOrderDataSafe(
        docId: String,
        shopId: String = "",
        onParseError: (docId: String, documentPath: String, error: Throwable) -> Unit = { _, _, _ -> },
        dataProvider: () -> Map<String, Any?>?
    ): PosOrder? {
        return try {
            val data = dataProvider() ?: return null
            parsePosOrderMap(docId = docId, shopId = shopId, data = data)
        } catch (t: Throwable) {
            onParseError(docId, "merchants/$shopId/posOrders/$docId", t)
            null
        }
    }

    fun parsePosOrderMap(
        docId: String,
        shopId: String = "",
        data: Map<String, Any?>
    ): PosOrder {
        val typeRaw = (OrderDocumentParser.safeString(data["type"]) ?: "dine_in").trim().lowercase()
        val normalizedType = when {
            typeRaw.contains("take") -> "take_out"
            else -> "dine_in"
        }

        val statusRaw = (OrderDocumentParser.safeString(data["status"]) ?: "open").trim().lowercase()

        val items = OrderDocumentParser.safeList(data["items"]).mapNotNull { entry ->
            val m = OrderDocumentParser.safeMap(entry)
            if (m.isEmpty() && entry !is Map<*, *>) return@mapNotNull null
            val qtyVal = OrderDocumentParser.safeInt(m["qty"])
                ?: OrderDocumentParser.safeInt(m["quantity"])
                ?: 1
            val priceVal = OrderDocumentParser.safeDouble(m["unitPrice"])
                ?: OrderDocumentParser.safeDouble(m["price"])
                ?: 0.0
            PosOrderItem(
                name = (OrderDocumentParser.safeString(m["name"]) ?: OrderDocumentParser.safeString(m["title"]) ?: "Item").trim().ifBlank { "Item" },
                variantLabel = (OrderDocumentParser.safeString(m["variantLabel"]) ?: "").trim(),
                unitPrice = priceVal.coerceAtLeast(0.0),
                qty = qtyVal.coerceAtLeast(0),
                voided = OrderDocumentParser.safeBoolean(m["voided"], false)
            )
        }

        val computedNonVoidedSubtotal = items.sumOf { it.lineTotal }
        val subtotalVal = OrderDocumentParser.safeDouble(data["subtotal"]) ?: computedNonVoidedSubtotal
        val discountTotalVal = (OrderDocumentParser.safeDouble(data["discountTotal"]) ?: 0.0).coerceAtLeast(0.0)
        val serviceChargeVal = (OrderDocumentParser.safeDouble(data["serviceCharge"]) ?: 0.0).coerceAtLeast(0.0)
        val vatAmountVal = (OrderDocumentParser.safeDouble(data["vatAmount"]) ?: 0.0).coerceAtLeast(0.0)
        val totalVal = OrderDocumentParser.safeDouble(data["total"])
            ?: ((subtotalVal - discountTotalVal).coerceAtLeast(0.0) + serviceChargeVal + vatAmountVal)
        val tipVal = (OrderDocumentParser.safeDouble(data["tip"]) ?: 0.0).coerceAtLeast(0.0)

        val payments = OrderDocumentParser.safeList(data["payments"]).mapNotNull { entry ->
            val m = OrderDocumentParser.safeMap(entry)
            if (m.isEmpty() && entry !is Map<*, *>) return@mapNotNull null
            val method = (OrderDocumentParser.safeString(m["method"]) ?: "other").trim().ifBlank { "other" }
            val amt = (OrderDocumentParser.safeDouble(m["amount"]) ?: 0.0).coerceAtLeast(0.0)
            PosPayment(method = method, amount = amt)
        }

        val discountsList = mutableListOf<PosDiscountEntry>()
        when (val rawDiscounts = data["discounts"]) {
            is List<*> -> {
                rawDiscounts.forEach { dObj ->
                    val dm = OrderDocumentParser.safeMap(dObj)
                    if (dm.isNotEmpty()) {
                        val kind = (OrderDocumentParser.safeString(dm["kind"])
                            ?: OrderDocumentParser.safeString(dm["type"])
                            ?: OrderDocumentParser.safeString(dm["name"])
                            ?: OrderDocumentParser.safeString(dm["label"])
                            ?: "Discount").trim().ifBlank { "Discount" }
                        val amt = (OrderDocumentParser.safeDouble(dm["amount"]) ?: 0.0).coerceAtLeast(0.0)
                        if (amt > 0.0) discountsList.add(PosDiscountEntry(kind = kind, amount = amt))
                    }
                }
            }
            is Map<*, *> -> {
                rawDiscounts.forEach { (k, v) ->
                    val kind = k?.toString()?.trim().orEmpty().ifBlank { "Discount" }
                    val amt = (OrderDocumentParser.safeDouble(v) ?: 0.0).coerceAtLeast(0.0)
                    if (amt > 0.0) discountsList.add(PosDiscountEntry(kind = kind, amount = amt))
                }
            }
        }
        val discountKindVal = (OrderDocumentParser.safeString(data["discountKind"])
            ?: OrderDocumentParser.safeString(data["discountType"])
            ?: OrderDocumentParser.safeString(data["discountName"])
            ?: "").trim()

        val refundMap = OrderDocumentParser.safeMap(data["refund"])
        val refundObj = if (refundMap.isNotEmpty()) {
            val rAmt = (OrderDocumentParser.safeDouble(refundMap["amount"]) ?: 0.0).coerceAtLeast(0.0)
            val rReason = (OrderDocumentParser.safeString(refundMap["reason"]) ?: "").trim()
            val rAt = FormatUtils.parseTimestamp(refundMap["at"])
            PosRefund(amount = rAmt, reason = rReason, at = rAt)
        } else null

        val paidAtVal = FormatUtils.parseTimestamp(data["paidAt"])
        val createdAtVal = FormatUtils.parseTimestamp(data["createdAt"])

        return PosOrder(
            id = docId,
            shopId = (OrderDocumentParser.safeString(data["shopId"]) ?: shopId).ifBlank { shopId },
            number = (OrderDocumentParser.safeString(data["number"]) ?: docId.takeLast(6)).trim(),
            type = normalizedType,
            status = statusRaw,
            tableId = (OrderDocumentParser.safeString(data["tableId"]) ?: "").trim(),
            tableName = (OrderDocumentParser.safeString(data["tableName"]) ?: OrderDocumentParser.safeString(data["table"]) ?: "").trim(),
            items = items,
            subtotal = subtotalVal.coerceAtLeast(0.0),
            discountTotal = discountTotalVal,
            discountKind = discountKindVal,
            discounts = discountsList,
            serviceCharge = serviceChargeVal,
            vatAmount = vatAmountVal,
            total = totalVal.coerceAtLeast(0.0),
            payments = payments,
            tip = tipVal,
            cashierName = (OrderDocumentParser.safeString(data["cashierName"]) ?: "").trim(),
            shiftId = (OrderDocumentParser.safeString(data["shiftId"]) ?: "").trim(),
            refund = refundObj,
            paidAt = paidAtVal,
            createdAt = createdAtVal
        )
    }

    fun parsePosShiftSnapshotSafe(
        doc: DocumentSnapshot,
        shopId: String,
        onParseError: (docId: String, documentPath: String, error: Throwable) -> Unit = { _, _, _ -> }
    ): PosShift? = parsePosShiftDataSafe(
        docId = doc.id,
        shopId = shopId,
        onParseError = onParseError,
        dataProvider = { doc.data }
    )

    inline fun parsePosShiftDataSafe(
        docId: String,
        shopId: String = "",
        onParseError: (docId: String, documentPath: String, error: Throwable) -> Unit = { _, _, _ -> },
        dataProvider: () -> Map<String, Any?>?
    ): PosShift? {
        return try {
            val data = dataProvider() ?: return null
            parsePosShiftMap(docId = docId, shopId = shopId, data = data)
        } catch (t: Throwable) {
            onParseError(docId, "merchants/$shopId/posShifts/$docId", t)
            null
        }
    }

    fun parsePosShiftMap(
        docId: String,
        shopId: String = "",
        data: Map<String, Any?>
    ): PosShift {
        val openedAtVal = FormatUtils.parseTimestamp(data["openedAt"])
        val closedAtVal = FormatUtils.parseTimestamp(data["closedAt"])
        val openingCashVal = OrderDocumentParser.safeDouble(data["openingCash"]) ?: 0.0
        val closingCashVal = OrderDocumentParser.safeDouble(data["closingCash"]) ?: 0.0
        val expectedCashVal = OrderDocumentParser.safeDouble(data["expectedCash"]) ?: openingCashVal
        val varianceVal = OrderDocumentParser.safeDouble(data["variance"])
            ?: if (closedAtVal != null) (closingCashVal - expectedCashVal) else 0.0
        val statusVal = (OrderDocumentParser.safeString(data["status"])
            ?: if (closedAtVal != null) "closed" else "open").trim().lowercase()

        return PosShift(
            id = docId,
            shopId = shopId,
            cashierName = (OrderDocumentParser.safeString(data["cashierName"]) ?: "").trim().ifBlank { "Cashier" },
            openedAt = openedAtVal,
            closedAt = closedAtVal,
            openingCash = openingCashVal,
            closingCash = closingCashVal,
            expectedCash = expectedCashVal,
            variance = varianceVal,
            status = statusVal
        )
    }

    fun parsePosLogSnapshotSafe(
        doc: DocumentSnapshot,
        shopId: String,
        onParseError: (docId: String, documentPath: String, error: Throwable) -> Unit = { _, _, _ -> }
    ): PosLog? = parsePosLogDataSafe(
        docId = doc.id,
        shopId = shopId,
        onParseError = onParseError,
        dataProvider = { doc.data }
    )

    inline fun parsePosLogDataSafe(
        docId: String,
        shopId: String = "",
        onParseError: (docId: String, documentPath: String, error: Throwable) -> Unit = { _, _, _ -> },
        dataProvider: () -> Map<String, Any?>?
    ): PosLog? {
        return try {
            val data = dataProvider() ?: return null
            parsePosLogMap(docId = docId, shopId = shopId, data = data)
        } catch (t: Throwable) {
            onParseError(docId, "merchants/$shopId/posLogs/$docId", t)
            null
        }
    }

    fun parsePosLogMap(
        docId: String,
        shopId: String = "",
        data: Map<String, Any?>
    ): PosLog {
        return PosLog(
            id = docId,
            shopId = shopId,
            type = (OrderDocumentParser.safeString(data["type"]) ?: "").trim().lowercase(),
            amount = (OrderDocumentParser.safeDouble(data["amount"]) ?: 0.0).coerceAtLeast(0.0),
            reason = (OrderDocumentParser.safeString(data["reason"]) ?: "").trim(),
            byName = (OrderDocumentParser.safeString(data["byName"]) ?: "").trim(),
            approvedBy = (OrderDocumentParser.safeString(data["approvedBy"]) ?: "").trim(),
            at = FormatUtils.parseTimestamp(data["at"])
        )
    }
}
