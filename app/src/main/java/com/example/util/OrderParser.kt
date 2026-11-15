package com.example.util

import com.example.data.model.AddressLocation
import com.example.data.model.FailedOrderReport
import com.example.data.model.Order
import com.example.data.model.OrderItem
import com.example.data.model.OrderItemSelection
import com.example.data.model.OrderModifier
import com.example.data.model.OrderPromo
import com.example.data.model.OrderStatus
import com.example.data.model.Rider
import com.example.data.model.RiderRequest

data class BatchOrderParseResult(
    val orders: List<Order>,
    val failedReports: List<FailedOrderReport>
)

fun interface NonFatalReporter {
    fun record(orderId: String, throwable: Throwable)
}

object OrderParser {

    fun safeString(value: Any?, default: String = ""): String {
        return when (value) {
            null -> default
            is String -> value
            is Number, is Boolean -> value.toString()
            else -> default
        }
    }

    fun safeNullableString(value: Any?): String? {
        return when (value) {
            null -> null
            is String -> value
            is Number, is Boolean -> value.toString()
            else -> null
        }
    }

    fun safeDouble(value: Any?, default: Double = 0.0): Double {
        return FormatUtils.parseDouble(value) ?: default
    }

    fun safeNullableDouble(value: Any?): Double? {
        return FormatUtils.parseDouble(value)
    }

    fun safeInt(value: Any?, default: Int = 0): Int {
        return FormatUtils.parseInt(value) ?: default
    }

    fun safeNullableInt(value: Any?): Int? {
        return FormatUtils.parseInt(value)
    }

    fun safeLong(value: Any?, default: Long = 0L): Long {
        return FormatUtils.parseLong(value) ?: default
    }

    fun safeNullableLong(value: Any?): Long? {
        return FormatUtils.parseLong(value)
    }

    fun safeBoolean(value: Any?, default: Boolean = false): Boolean {
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> when (value.trim().lowercase()) {
                "true", "1", "yes" -> true
                "false", "0", "no" -> false
                else -> default
            }
            else -> default
        }
    }

    fun safeNullableBoolean(value: Any?): Boolean? {
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> when (value.trim().lowercase()) {
                "true", "1", "yes" -> true
                "false", "0", "no" -> false
                else -> null
            }
            else -> null
        }
    }

    fun safeMap(value: Any?): Map<String, Any?> {
        val raw = value as? Map<*, *> ?: return emptyMap()
        if (raw.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, Any?>(raw.size)
        for ((k, v) in raw) {
            val key = k?.toString() ?: continue
            result[key] = v
        }
        return result
    }

    fun safeList(value: Any?): List<Any?> {
        return (value as? Iterable<*>)?.toList() ?: emptyList()
    }

    fun safeTimestamp(value: Any?): Long? {
        return FormatUtils.parseTimestamp(value)
    }

    fun <T> parseDocumentsSafely(
        documents: List<T>,
        idProvider: (T) -> String,
        dataProvider: (T) -> Map<String, Any?>,
        fallbackMerchantId: String = "",
        fallbackStoreLocation: AddressLocation = AddressLocation(),
        nonFatalReporter: NonFatalReporter? = null
    ): BatchOrderParseResult {
        val parsed = mutableListOf<Order>()
        val failed = mutableListOf<FailedOrderReport>()
        for (doc in documents) {
            var docId = "unknown"
            try {
                docId = idProvider(doc).ifBlank { "unknown" }
                val data = dataProvider(doc)
                val order = parseOrderData(
                    docId = docId,
                    data = data,
                    fallbackMerchantId = fallbackMerchantId,
                    fallbackStoreLocation = fallbackStoreLocation
                )
                if (order != null) {
                    parsed.add(order)
                }
            } catch (t: Throwable) {
                nonFatalReporter?.record(docId, t)
                failed.add(
                    FailedOrderReport(
                        orderId = docId,
                        errorMessage = t.message ?: t.javaClass.simpleName,
                        stackTrace = t.stackTraceToString().take(2000)
                    )
                )
            }
        }
        return BatchOrderParseResult(orders = parsed, failedReports = failed)
    }

    fun parseOrderData(
        docId: String,
        data: Map<String, Any?>,
        fallbackMerchantId: String = "",
        fallbackStoreLocation: AddressLocation = AddressLocation()
    ): Order? {
        val isSplitChildVal = safeBoolean(data["isSplitChild"] ?: data["splitChild"], false)
        if (isSplitChildVal) return null

        val statusStr = safeNullableString(data["status"]) ?: PlatformOrderStatuses.PLACED_ORDER
        val orderStatus = OrderStatus.fromString(statusStr)

        val itemsRaw = safeList(data["items"])
        val items = itemsRaw.mapIndexedNotNull { idx, itemObj ->
            val itm = safeMap(itemObj)
            if (itm.isEmpty()) return@mapIndexedNotNull null

            val modsRaw = safeList(itm["modifiers"])
            val mods = modsRaw.mapNotNull { mObj ->
                val m = safeMap(mObj)
                if (m.isEmpty()) return@mapNotNull null
                OrderModifier(
                    name = safeString(m["name"] ?: m["title"]),
                    price = safeDouble(m["price"], 0.0)
                )
            }

            val unitPriceExplicit = safeNullableDouble(itm["unitPrice"])
            val priceExplicit = safeNullableDouble(itm["price"]) ?: safeNullableDouble(itm["itemPrice"])
            val priceVal = priceExplicit ?: unitPriceExplicit ?: 0.0
            val unitPriceVal = unitPriceExplicit ?: priceVal
            val regularUnitPriceVal = safeNullableDouble(itm["regularUnitPrice"]) ?: unitPriceVal
            val basePriceVal = safeNullableDouble(itm["basePrice"]) ?: priceVal

            val qtyVal = safeNullableInt(itm["qty"]) ?: safeNullableInt(itm["quantity"]) ?: 1
            val finalQty = if (qtyVal > 0) qtyVal else 1

            val selRaw = safeList(itm["selections"])
            val selections = selRaw.mapNotNull { sObj ->
                val sm = safeMap(sObj)
                if (sm.isEmpty()) return@mapNotNull null
                OrderItemSelection(
                    setId = safeString(sm["setId"]),
                    setName = safeString(sm["setName"]),
                    choiceId = safeString(sm["choiceId"]),
                    choiceName = safeString(sm["choiceName"] ?: sm["name"]),
                    price = safeDouble(sm["price"], 0.0)
                )
            }

            OrderItem(
                id = safeString(itm["id"]).ifBlank { "item_$idx" },
                menuItemId = safeString(itm["menuItemId"] ?: itm["itemId"]),
                name = safeString(itm["name"] ?: itm["title"]).ifBlank { "Item" },
                quantity = finalQty,
                price = priceVal,
                basePrice = basePriceVal,
                unitPrice = unitPriceVal,
                regularUnitPrice = regularUnitPriceVal,
                variantKey = safeString(itm["variantKey"]),
                variantLabel = safeString(itm["variantLabel"]),
                variantImageUrl = safeString(itm["variantImageUrl"]),
                selectedSize = safeNullableString(itm["selectedSize"]),
                modifiers = mods,
                selections = selections,
                notes = safeNullableString(itm["notes"] ?: itm["note"])
            )
        }

        val addrMap = safeMap(data["deliveryAddress"])
        val addrAsString = if (addrMap.isEmpty()) safeString(data["deliveryAddress"]) else ""
        val dropoffMap = safeMap(data["dropoff"])

        val dropLat = safeNullableDouble(data["dropoffLat"])
            ?: safeNullableDouble(data["dropoffLatitude"])
            ?: safeNullableDouble(dropoffMap["lat"] ?: dropoffMap["latitude"])
            ?: safeNullableDouble(addrMap["latitude"] ?: addrMap["lat"])
        val dropLng = safeNullableDouble(data["dropoffLng"])
            ?: safeNullableDouble(data["dropoffLongitude"])
            ?: safeNullableDouble(dropoffMap["lng"] ?: dropoffMap["longitude"])
            ?: safeNullableDouble(addrMap["longitude"] ?: addrMap["lng"])

        val streetVal = safeString(addrMap["street"] ?: addrMap["address"] ?: addrMap["formatted"])
            .ifBlank { safeString(dropoffMap["address"]) }
            .ifBlank { safeString(data["dropoffAddress"]) }
            .ifBlank { addrAsString }
        val cityVal = safeString(addrMap["city"] ?: dropoffMap["area"] ?: data["dropoffArea"])
        val addrNotes = safeString(addrMap["notes"] ?: addrMap["note"] ?: dropoffMap["notes"])

        val addr = AddressLocation(
            street = streetVal,
            city = cityVal,
            latitude = dropLat ?: 0.0,
            longitude = dropLng ?: 0.0,
            notes = addrNotes
        )

        val rId = safeString(data["riderId"])
        val rNameTop = safeString(data["riderName"])
        val rPhoneTop = safeString(data["riderPhone"])
        val riderLocMap = safeMap(data["riderLocation"])
        val riderLoc = if (riderLocMap.isNotEmpty()) {
            AddressLocation(
                latitude = safeDouble(riderLocMap["latitude"] ?: riderLocMap["lat"], 0.0),
                longitude = safeDouble(riderLocMap["longitude"] ?: riderLocMap["lng"], 0.0)
            )
        } else null

        val reqMap = safeMap(data["riderRequest"])
        val topRiderMap = safeMap(data["rider"])
        val reqRiderMap = safeMap(reqMap["rider"])
        val effectiveRiderMap = if (reqRiderMap.isNotEmpty()) reqRiderMap else topRiderMap

        val rName = rNameTop.ifBlank { safeString(effectiveRiderMap["name"] ?: reqMap["riderName"]) }
        val rPhone = rPhoneTop.ifBlank { safeString(effectiveRiderMap["phone"] ?: reqMap["riderPhone"]) }
        val rVehicleVal = safeString(
            data["riderVehicle"]
                ?: data["riderVehicleType"]
                ?: reqMap["riderVehicle"]
                ?: effectiveRiderMap["vehicleType"]
                ?: effectiveRiderMap["vehicle"]
        )
        val rPlateVal = safeString(
            data["riderPlate"]
                ?: data["riderVehiclePlate"]
                ?: data["plateNumber"]
                ?: reqMap["riderPlate"]
                ?: effectiveRiderMap["vehiclePlate"]
                ?: effectiveRiderMap["plate"]
        )

        val rider = if (rName.isNotBlank() || rId.isNotBlank() || effectiveRiderMap.isNotEmpty()) {
            Rider(
                id = rId.ifBlank { safeString(effectiveRiderMap["id"]) },
                name = rName.ifBlank { "Rider" },
                phone = rPhone,
                vehiclePlate = rPlateVal,
                vehicleType = rVehicleVal.ifBlank { "Motorcycle" }
            )
        } else null

        val reqStatusStr = safeNullableString(reqMap["status"]) ?: safeNullableString(data["riderRequestStatus"])
        val reqCreatedAt = safeTimestamp(reqMap["createdAt"])
        val reqRequestedAt = safeTimestamp(reqMap["requestedAt"]) ?: reqCreatedAt
        val reqRadiusKm = safeDouble(reqMap["radiusKm"], 3.0)
        val reqAlertsSent = safeNullableInt(reqMap["alertsSent"] ?: reqMap["notifiedCount"] ?: reqMap["riderAlertsSent"])

        val riderRequest = if (reqMap.isNotEmpty() || reqStatusStr != null) {
            RiderRequest(
                id = docId,
                orderId = docId,
                status = reqStatusStr ?: "Searching",
                requestedAt = reqRequestedAt,
                createdAt = reqCreatedAt ?: reqRequestedAt,
                radiusKm = reqRadiusKm,
                alertsSent = reqAlertsSent,
                requestedBy = safeString(reqMap["requestedBy"]),
                distanceKm = safeDouble(reqMap["distanceKm"], 0.0),
                deliveryFee = safeDouble(reqMap["deliveryFee"], 0.0),
                note = safeString(reqMap["note"]),
                feePayer = safeString(reqMap["feePayer"], "customer"),
                rider = rider,
                pickupAddress = fallbackStoreLocation,
                dropoffAddress = addr
            )
        } else null

        val feesRawMap = safeMap(data["fees"])
        val lineTotalsSum = items.sumOf { it.lineTotal }

        val rawSubtotal = safeNullableDouble(feesRawMap["subtotal"])
            ?: safeNullableDouble(data["subtotal"])
            ?: lineTotalsSum
        val finalSubtotal = if (rawSubtotal <= 0.0 && lineTotalsSum > 0.0) lineTotalsSum else rawSubtotal

        val deliveryFeeVal = safeNullableDouble(feesRawMap["deliveryFee"])
            ?: safeNullableDouble(data["deliveryFee"])
            ?: 0.0
        val serviceFeeVal = safeNullableDouble(feesRawMap["serviceFee"])
            ?: safeNullableDouble(data["serviceFee"])
            ?: 0.0
        val discountVal = safeNullableDouble(feesRawMap["discount"])
            ?: safeNullableDouble(data["discount"])
            ?: 0.0
        val platformFeeVal = safeNullableDouble(feesRawMap["platformFee"])
            ?: safeNullableDouble(data["platformFee"])
            ?: 0.0

        val calculatedTotal = (finalSubtotal + deliveryFeeVal + serviceFeeVal - discountVal).coerceAtLeast(0.0)
        val parsedFeesTotal = safeNullableDouble(feesRawMap["total"])
        val parsedDocTotal = safeNullableDouble(data["total"])
        val parsedTotalAmount = safeNullableDouble(data["totalAmount"])

        var finalTotal = parsedFeesTotal
            ?: parsedDocTotal
            ?: parsedTotalAmount
            ?: if (calculatedTotal > 0.0) calculatedTotal else lineTotalsSum
        if (finalTotal <= 0.0) {
            if (calculatedTotal > 0.0) finalTotal = calculatedTotal
            else if (lineTotalsSum > 0.0) finalTotal = lineTotalsSum
        }

        val paymentMap = safeMap(data["payment"])
        val rawPaymentMethod = safeString(
            data["paymentMethod"] ?: paymentMap["method"],
            default = "COD"
        ).trim().ifBlank { "COD" }

        val placedAtTs = safeTimestamp(data["placedAtTimestamp"])
            ?: safeTimestamp(data["createdAt"])
            ?: safeTimestamp(data["placedAt"])
            ?: safeTimestamp(data["timestamp"])
            ?: 0L

        val prepMins = safeNullableInt(data["prepMinutes"]) ?: safeNullableInt(data["prepTimeMinutes"])
        val prepEnd = safeTimestamp(data["prepEndTime"]) ?: if (prepMins != null && placedAtTs > 0L) {
            placedAtTs + (prepMins * 60 * 1000L)
        } else null

        val tipVal = safeNullableDouble(feesRawMap["tip"])
            ?: safeNullableDouble(data["tip"])
            ?: 0.0
        val rawFulfillment = safeString(data["fulfillment"]).trim()
        val hasPickup = safeBoolean(data["hasPickup"], false)
        val scheduledForVal = safeTimestamp(data["scheduledFor"])
        val editedAtVal = safeTimestamp(data["editedAt"])
        val isEditedVal = safeNullableBoolean(data["isEdited"]) ?: (editedAtVal != null)
        val editedByVal = safeString(data["editedBy"])
        val editNoteVal = safeString(data["editNote"])
        val origTotalVal = safeNullableDouble(data["originalTotal"])
        val cashCollectVal = safeNullableDouble(data["cashToCollect"] ?: data["codAmount"])
        val walletAdjustVal = safeNullableDouble(data["walletAdjustDue"])

        val origItemsRaw = safeList(data["originalItems"])
        val origItems = origItemsRaw.mapIndexedNotNull { idx, itemObj ->
            val itm = safeMap(itemObj)
            if (itm.isEmpty()) return@mapIndexedNotNull null
            val pVal = safeNullableDouble(itm["price"]) ?: safeNullableDouble(itm["unitPrice"]) ?: 0.0
            val qVal = safeNullableInt(itm["qty"]) ?: safeNullableInt(itm["quantity"]) ?: 1
            OrderItem(
                id = safeString(itm["id"]).ifBlank { "orig_$idx" },
                menuItemId = safeString(itm["menuItemId"] ?: itm["itemId"]),
                name = safeString(itm["name"] ?: itm["title"]).ifBlank { "Item" },
                quantity = if (qVal > 0) qVal else 1,
                price = pVal,
                unitPrice = safeNullableDouble(itm["unitPrice"]) ?: pVal,
                regularUnitPrice = safeNullableDouble(itm["regularUnitPrice"]) ?: pVal,
                variantKey = safeString(itm["variantKey"]),
                variantLabel = safeString(itm["variantLabel"]),
                variantImageUrl = safeString(itm["variantImageUrl"])
            )
        }

        val rawJsonStr = try {
            org.json.JSONObject(data).toString(2)
        } catch (_: Exception) {
            ""
        }

        val rawSplitOut = data["splitOut"]
        val hasSplitOut = when (rawSplitOut) {
            null -> false
            is Boolean -> rawSplitOut
            is String -> rawSplitOut.isNotBlank() && !rawSplitOut.equals("false", ignoreCase = true) && rawSplitOut != "0"
            is Collection<*> -> rawSplitOut.isNotEmpty()
            is Map<*, *> -> rawSplitOut.isNotEmpty()
            is Number -> rawSplitOut.toInt() != 0
            else -> false
        }

        val rawHistory: List<Map<String, Any?>> = safeList(data["statusHistory"])
            .mapNotNull { entry -> safeMap(entry).takeIf { it.isNotEmpty() } }

        fun extractHistoryTimestamp(vararg targetStatuses: String): Long? {
            for (entry in rawHistory) {
                val s = safeString(entry["status"]).lowercase()
                if (targetStatuses.any { it.equals(s, ignoreCase = true) }) {
                    val t = safeTimestamp(entry["at"]) ?: safeTimestamp(entry["timestamp"])
                    if (t != null) return t
                }
            }
            return null
        }

        val acceptedAtVal = safeTimestamp(data["acceptedAt"])
            ?: extractHistoryTimestamp("preparing", PlatformOrderStatuses.PREPARING)
        val readyAtVal = safeTimestamp(data["readyAt"])
            ?: extractHistoryTimestamp("ready", PlatformOrderStatuses.READY)
        val rejectedAtVal = safeTimestamp(data["rejectedAt"])
            ?: extractHistoryTimestamp("rejected", PlatformOrderStatuses.REJECTED)

        val customerMap = safeMap(data["customer"])
        val recipientNameVal = safeString(data["recipientName"] ?: dropoffMap["contactName"])
        val recipientPhoneVal = safeString(data["recipientPhone"] ?: dropoffMap["contactPhone"])
        val customerNameVal = safeString(data["customerName"] ?: customerMap["name"])
            .ifBlank { recipientNameVal }
        val customerPhoneVal = safeString(data["customerPhone"] ?: customerMap["phone"])
            .ifBlank { recipientPhoneVal }

        val customerIdVal = safeString(
            data["customerId"]
                ?: data["customerUid"]
                ?: data["userId"]
                ?: customerMap["id"]
                ?: customerMap["uid"]
                ?: customerPhoneVal.ifBlank { null }
                ?: customerNameVal.ifBlank { null }
        ).trim()

        val deliveryMap = safeMap(data["delivery"])
        val delOptRaw = safeString(data["deliveryOption"] ?: deliveryMap["option"]).trim()
        val delOptVal = when {
            delOptRaw.contains("saver", ignoreCase = true) -> "Saver"
            delOptRaw.contains("standard", ignoreCase = true) -> "Standard"
            delOptRaw.contains("priority", ignoreCase = true) -> "Priority"
            else -> "Priority"
        }

        val dispatchMap = safeMap(data["dispatch"])
        val holdMinsParsed = safeNullableInt(dispatchMap["holdMinutes"])
            ?: safeNullableInt(data["dispatchHoldMinutes"])
            ?: safeNullableInt(data["holdMinutes"])
        val dispatchHoldMinutesVal = holdMinsParsed ?: if (delOptVal == "Standard" || delOptVal == "Saver") 5 else 0

        val rawOrderNum = safeNullableString(data["orderNumber"]) ?: safeNullableString(data["orderRef"])
        val orderNum = if (!rawOrderNum.isNullOrBlank()) {
            val clean = if (rawOrderNum.startsWith("#")) rawOrderNum.removePrefix("#").trim() else rawOrderNum.trim()
            "#$clean"
        } else {
            "#" + (if (docId.length >= 6) docId.takeLast(6).uppercase() else docId.uppercase())
        }

        val promoMap = safeMap(data["promo"]).ifEmpty { safeMap(feesRawMap["promo"]) }
        val promoObj = if (promoMap.isNotEmpty()) {
            val pCode = safeString(promoMap["code"])
            val pDiscount = safeDouble(promoMap["discount"], 0.0)
            val pFundedBy = safeString(promoMap["fundedBy"], "merchant")
            val pMerchantId = safeString(promoMap["merchantId"])
            if (pCode.isNotBlank() && pDiscount > 0.0) {
                OrderPromo(code = pCode, discount = pDiscount, fundedBy = pFundedBy, merchantId = pMerchantId)
            } else null
        } else null

        val serviceTypeVal = safeString(data["serviceType"], "food").ifBlank { "food" }

        return Order(
            id = docId,
            merchantId = safeString(data["merchantId"]).ifBlank { fallbackMerchantId },
            orderNumber = orderNum,
            customerName = customerNameVal,
            customerPhone = customerPhoneVal,
            deliveryAddress = addr,
            dropoffLat = dropLat,
            dropoffLng = dropLng,
            items = items,
            subtotal = finalSubtotal,
            deliveryFee = deliveryFeeVal,
            serviceFee = serviceFeeVal,
            discount = discountVal,
            platformFee = platformFeeVal,
            total = finalTotal,
            paymentMethod = rawPaymentMethod,
            status = orderStatus,
            placedAtTimestamp = placedAtTs,
            prepMinutes = prepMins,
            prepEndTime = prepEnd,
            rejectReason = safeNullableString(data["rejectReason"] ?: data["rejectionReason"]),
            cancelReason = safeNullableString(data["cancelReason"]),
            riderRequest = riderRequest,
            rider = rider,
            riderId = rId,
            riderName = rName,
            riderPhone = rPhone,
            riderLocation = riderLoc,
            isTestOrder = safeBoolean(data["isTestOrder"], false),
            cityId = safeString(data["cityId"]),
            serviceType = serviceTypeVal,
            hasPickup = hasPickup,
            tip = tipVal,
            fulfillment = rawFulfillment,
            scheduledFor = scheduledForVal,
            isEdited = isEditedVal,
            editedAt = editedAtVal,
            editedBy = editedByVal,
            editNote = editNoteVal,
            originalItems = origItems,
            originalTotal = origTotalVal,
            cashToCollect = cashCollectVal,
            walletAdjustDue = walletAdjustVal,
            riderVehicle = rVehicleVal.ifBlank { rider?.vehicleType ?: "" },
            note = safeString(data["note"] ?: data["notes"]).trim(),
            feesRaw = feesRawMap,
            rawJson = rawJsonStr,
            acceptedAt = acceptedAtVal,
            readyAt = readyAtVal,
            deliveryOption = delOptVal,
            dispatchHoldMinutes = dispatchHoldMinutesVal,
            isSplitChild = false,
            splitOut = hasSplitOut,
            promo = promoObj,
            customerId = customerIdVal,
            rejectedAt = rejectedAtVal,
            statusHistory = rawHistory,
            updatedAt = safeTimestamp(data["updatedAt"]) ?: 0L,
            merchantName = safeString(data["merchantName"]),
            branchName = safeString(data["branchName"]),
            branchCode = safeString(data["branchCode"]),
            chainId = safeString(data["chainId"]),
            recipientName = recipientNameVal,
            recipientPhone = recipientPhoneVal,
            riderPlate = rPlateVal
        )
    }
}
