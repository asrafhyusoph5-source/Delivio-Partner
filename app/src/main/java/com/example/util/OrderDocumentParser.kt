package com.example.util

import com.example.data.model.AddressLocation
import com.example.data.model.Order
import com.example.data.model.OrderItem
import com.example.data.model.OrderItemSelection
import com.example.data.model.OrderModifier
import com.example.data.model.OrderPromo
import com.example.data.model.OrderStatus
import com.example.data.model.Rider
import com.example.data.model.RiderRequest
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException

/**
 * Defensive, schema-tolerant parser for Firestore order documents.
 * - Every field is optional with safe defaults (strings "", numbers 0, lists/maps empty).
 * - Numbers accept Long, Double, other Number subtypes, or numeric text Strings.
 * - Timestamps accept Timestamp, Date, Number, Map {seconds, nanoseconds}, ISO/numeric Strings, or missing.
 * - Unknown or null fields (variantKey, variantLabel, variantImageUrl, unitPrice, regularUnitPrice,
 *   promo, branchName, branchCode, chainId, scheduledFor, fees, etc.) never throw.
 */
object OrderDocumentParser {

    fun safeString(value: Any?): String? {
        return when (value) {
            null -> null
            is String -> value
            is Number, is Boolean -> value.toString()
            else -> null
        }
    }

    fun safeDouble(value: Any?): Double? = FormatUtils.parseDouble(value)

    fun safeInt(value: Any?): Int? = FormatUtils.parseInt(value)

    fun safeLong(value: Any?): Long? {
        return when (value) {
            is Number -> value.toDouble().takeIf { it.isFinite() }?.toLong()
            is String -> {
                val clean = value.trim().replace(",", "")
                clean.toLongOrNull() ?: clean.toDoubleOrNull()?.takeIf { it.isFinite() }?.toLong()
            }
            else -> FormatUtils.parseTimestamp(value)
        }
    }

    fun safeBoolean(value: Any?, default: Boolean = false): Boolean {
        return when (value) {
            is Boolean -> value
            is Number -> value.toDouble() != 0.0
            is String -> when (value.trim().lowercase()) {
                "true", "1", "yes" -> true
                "false", "0", "no", "" -> false
                else -> default
            }
            else -> default
        }
    }

    fun safeMap(value: Any?): Map<String, Any?> {
        val raw = value as? Map<*, *> ?: return emptyMap()
        val out = LinkedHashMap<String, Any?>(raw.size)
        for ((k, v) in raw) {
            val key = k?.toString() ?: continue
            out[key] = v
        }
        return out
    }

    fun safeList(value: Any?): List<Any?> {
        return when (value) {
            is List<*> -> value
            is Collection<*> -> value.toList()
            else -> emptyList()
        }
    }

    fun formatFirestoreError(ex: Throwable): String {
        val fsEx = ex as? FirebaseFirestoreException
        val codeStr = fsEx?.code?.name?.lowercase()?.replace('_', '-')
        val rawMsg = ex.localizedMessage ?: ex.message ?: "Firestore request failed"
        return when {
            codeStr != null && !rawMsg.contains(codeStr, ignoreCase = true) -> "$codeStr: $rawMsg"
            else -> rawMsg
        }
    }

    fun parseOrderSnapshotSafe(
        doc: DocumentSnapshot,
        fallbackMerchantId: String,
        storeLocation: AddressLocation = AddressLocation(),
        onParseError: (docId: String, documentPath: String, error: Throwable) -> Unit = { _, _, _ -> }
    ): Order? = parseOrderDataSafe(
        docId = doc.id,
        fallbackMerchantId = fallbackMerchantId,
        storeLocation = storeLocation,
        onParseError = onParseError,
        dataProvider = { doc.data }
    )

    inline fun parseOrderDataSafe(
        docId: String,
        fallbackMerchantId: String = "",
        storeLocation: AddressLocation = AddressLocation(),
        onParseError: (docId: String, documentPath: String, error: Throwable) -> Unit = { _, _, _ -> },
        dataProvider: () -> Map<String, Any?>?
    ): Order? {
        return try {
            val data = dataProvider() ?: return null
            parseOrderMap(
                docId = docId,
                data = data,
                fallbackMerchantId = fallbackMerchantId,
                storeLocation = storeLocation
            )
        } catch (t: Throwable) {
            onParseError(docId, "orders/$docId", t)
            null
        }
    }

    fun parseOrderMap(
        docId: String,
        data: Map<String, Any?>,
        fallbackMerchantId: String = "",
        storeLocation: AddressLocation = AddressLocation()
    ): Order? {
        val rawSplitChild = data["isSplitChild"] ?: data["splitChild"]
        if (safeBoolean(rawSplitChild, false)) return null

        val statusStr = safeString(data["status"]) ?: PlatformOrderStatuses.PLACED_ORDER
        val orderStatus = OrderStatus.fromString(statusStr)

        val itemsRaw = safeList(data["items"])
        val items = itemsRaw.mapIndexedNotNull { idx, itemObj ->
            val itm = safeMap(itemObj)
            if (itm.isEmpty() && itemObj !is Map<*, *>) return@mapIndexedNotNull null

            val modsRaw = safeList(itm["modifiers"])
            val mods = modsRaw.mapNotNull { mObj ->
                val m = safeMap(mObj)
                if (m.isEmpty() && mObj !is Map<*, *>) return@mapNotNull null
                OrderModifier(
                    name = safeString(m["name"]) ?: safeString(m["title"]) ?: "",
                    price = safeDouble(m["price"]) ?: 0.0
                )
            }

            val priceVal = safeDouble(itm["price"])
                ?: safeDouble(itm["unitPrice"])
                ?: safeDouble(itm["itemPrice"])
                ?: 0.0
            val qtyVal = safeInt(itm["qty"])
                ?: safeInt(itm["quantity"])
                ?: 1
            val finalQty = if (qtyVal > 0) qtyVal else 1

            val selRaw = safeList(itm["selections"])
            val selections = selRaw.mapNotNull { sObj ->
                val sm = safeMap(sObj)
                if (sm.isEmpty() && sObj !is Map<*, *>) return@mapNotNull null
                OrderItemSelection(
                    setId = safeString(sm["setId"]) ?: "",
                    setName = safeString(sm["setName"]) ?: "",
                    choiceId = safeString(sm["choiceId"]) ?: "",
                    choiceName = safeString(sm["choiceName"]) ?: safeString(sm["name"]) ?: "",
                    price = safeDouble(sm["price"]) ?: 0.0
                )
            }

            val unitPriceVal = safeDouble(itm["unitPrice"]) ?: priceVal
            val regularUnitPriceVal = safeDouble(itm["regularUnitPrice"]) ?: unitPriceVal
            val basePriceVal = safeDouble(itm["basePrice"]) ?: priceVal

            OrderItem(
                id = safeString(itm["id"]) ?: "item_$idx",
                menuItemId = safeString(itm["menuItemId"]) ?: safeString(itm["itemId"]) ?: "",
                name = safeString(itm["name"]) ?: safeString(itm["title"]) ?: "Item",
                quantity = finalQty,
                price = priceVal,
                basePrice = basePriceVal,
                unitPrice = unitPriceVal,
                regularUnitPrice = regularUnitPriceVal,
                variantKey = safeString(itm["variantKey"]) ?: "",
                variantLabel = safeString(itm["variantLabel"]) ?: "",
                variantImageUrl = safeString(itm["variantImageUrl"]) ?: "",
                selectedSize = safeString(itm["selectedSize"]),
                modifiers = mods,
                selections = selections,
                notes = safeString(itm["notes"]) ?: safeString(itm["note"])
            )
        }

        val rawDeliveryAddr = data["deliveryAddress"]
        val addrMap = safeMap(rawDeliveryAddr)
        val dropoffMap = safeMap(data["dropoff"])
        val dropLat = safeDouble(data["dropoffLat"])
            ?: safeDouble(data["dropoffLatitude"])
            ?: safeDouble(dropoffMap["lat"])
            ?: safeDouble(dropoffMap["latitude"])
            ?: safeDouble(addrMap["latitude"])
            ?: safeDouble(addrMap["lat"])
        val dropLng = safeDouble(data["dropoffLng"])
            ?: safeDouble(data["dropoffLongitude"])
            ?: safeDouble(dropoffMap["lng"])
            ?: safeDouble(dropoffMap["longitude"])
            ?: safeDouble(addrMap["longitude"])
            ?: safeDouble(addrMap["lng"])

        val streetStr = safeString(addrMap["street"])
            ?: safeString(addrMap["formatted"])
            ?: safeString(addrMap["address"])
            ?: safeString(rawDeliveryAddr)
            ?: safeString(dropoffMap["address"])
            ?: safeString(data["dropoffAddress"])
            ?: ""
        val cityStr = safeString(addrMap["city"])
            ?: safeString(dropoffMap["area"])
            ?: ""

        val addr = AddressLocation(
            street = streetStr,
            city = cityStr,
            latitude = dropLat ?: 14.5492,
            longitude = dropLng ?: 121.0503,
            notes = safeString(addrMap["notes"]) ?: safeString(addrMap["note"]) ?: safeString(dropoffMap["note"]) ?: ""
        )

        val riderMap = safeMap(data["rider"])
        val rId = (safeString(data["riderId"]) ?: safeString(riderMap["id"]) ?: "").trim()
        val rName = (safeString(data["riderName"]) ?: safeString(riderMap["name"]) ?: "").trim()
        val rPhone = (safeString(data["riderPhone"]) ?: safeString(riderMap["phone"]) ?: "").trim()
        val rPlate = (safeString(data["riderVehiclePlate"])
            ?: safeString(riderMap["vehiclePlate"])
            ?: safeString(riderMap["plateNumber"])
            ?: safeString(riderMap["plate"])
            ?: "").trim()
        val rVehType = (safeString(data["riderVehicle"])
            ?: safeString(riderMap["vehicleType"])
            ?: safeString(riderMap["vehicle"])
            ?: "Motorcycle").trim()

        val riderLocMap = safeMap(data["riderLocation"])
        val riderLoc = if (riderLocMap.isNotEmpty()) {
            AddressLocation(
                latitude = safeDouble(riderLocMap["latitude"]) ?: safeDouble(riderLocMap["lat"]) ?: 0.0,
                longitude = safeDouble(riderLocMap["longitude"]) ?: safeDouble(riderLocMap["lng"]) ?: 0.0
            )
        } else null

        val reqMap = safeMap(data["riderRequest"])
        val reqStatusStr = safeString(reqMap["status"]) ?: safeString(data["riderRequestStatus"])
        val reqCreatedAt = FormatUtils.parseTimestamp(reqMap["createdAt"])
            ?: FormatUtils.parseTimestamp(reqMap["requestedAt"])
        val reqRequestedAt = FormatUtils.parseTimestamp(reqMap["requestedAt"]) ?: reqCreatedAt

        val reqRiderMap = safeMap(reqMap["rider"])
        val effRiderId = rId.ifBlank { (safeString(reqRiderMap["id"]) ?: "").trim() }
        val effRiderName = rName.ifBlank { (safeString(reqRiderMap["name"]) ?: "").trim() }
        val effRiderPhone = rPhone.ifBlank { (safeString(reqRiderMap["phone"]) ?: "").trim() }
        val effRiderPlate = rPlate.ifBlank {
            (safeString(reqRiderMap["vehiclePlate"])
                ?: safeString(reqRiderMap["plate"])
                ?: safeString(reqRiderMap["plateNumber"])
                ?: "").trim()
        }
        val effRiderVehicleType = rVehType.ifBlank {
            (safeString(reqRiderMap["vehicleType"]) ?: safeString(reqRiderMap["vehicle"]) ?: "Motorcycle").trim()
        }

        val rider = if (effRiderName.isNotBlank() || effRiderId.isNotBlank()) {
            Rider(
                id = effRiderId,
                name = effRiderName.ifBlank { "Rider" },
                phone = effRiderPhone,
                vehiclePlate = effRiderPlate.ifBlank { effRiderVehicleType },
                vehicleType = effRiderVehicleType.ifBlank { "Motorcycle" }
            )
        } else null

        val riderRequest = if (reqMap.isNotEmpty() || reqStatusStr != null) {
            val alertsSentVal = safeInt(reqMap["alertsSent"])
                ?: safeInt(reqMap["notifiedCount"])
                ?: safeInt(reqMap["ridersNotified"])
                ?: safeInt(reqMap["notifiedRidersCount"])
            RiderRequest(
                id = docId,
                orderId = docId,
                status = reqStatusStr ?: "Searching",
                requestedAt = reqRequestedAt,
                createdAt = reqCreatedAt,
                requestedBy = safeString(reqMap["requestedBy"]) ?: "",
                distanceKm = safeDouble(reqMap["distanceKm"]) ?: 0.0,
                radiusKm = safeDouble(reqMap["radiusKm"]) ?: 3.0,
                alertsSent = alertsSentVal,
                deliveryFee = safeDouble(reqMap["deliveryFee"]) ?: 0.0,
                note = safeString(reqMap["note"]) ?: "",
                feePayer = safeString(reqMap["feePayer"]) ?: "customer",
                rider = rider,
                pickupAddress = storeLocation,
                dropoffAddress = addr
            )
        } else null

        val feesMap = safeMap(data["fees"])
        val lineTotalsSum = items.sumOf { it.lineTotal }

        val rawSubtotal = safeDouble(feesMap["subtotal"])
            ?: safeDouble(data["subtotal"])
            ?: lineTotalsSum
        val finalSubtotal = if (rawSubtotal <= 0.0 && lineTotalsSum > 0.0) lineTotalsSum else rawSubtotal

        val deliveryFeeVal = safeDouble(feesMap["deliveryFee"])
            ?: safeDouble(data["deliveryFee"])
            ?: 0.0
        val serviceFeeVal = safeDouble(feesMap["serviceFee"])
            ?: safeDouble(data["serviceFee"])
            ?: 0.0
        val discountVal = safeDouble(feesMap["discount"])
            ?: safeDouble(data["discount"])
            ?: 0.0
        val platformFeeVal = safeDouble(feesMap["platformFee"])
            ?: safeDouble(data["platformFee"])
            ?: 0.0

        val calculatedTotal = (finalSubtotal + deliveryFeeVal + serviceFeeVal - discountVal).coerceAtLeast(0.0)
        val parsedFeesTotal = safeDouble(feesMap["total"])
        val parsedDocTotal = safeDouble(data["total"])
        val parsedTotalAmount = safeDouble(data["totalAmount"])

        var finalTotal = parsedFeesTotal
            ?: parsedDocTotal
            ?: parsedTotalAmount
            ?: if (calculatedTotal > 0.0) calculatedTotal else lineTotalsSum
        if (finalTotal <= 0.0) {
            if (calculatedTotal > 0.0) finalTotal = calculatedTotal
            else if (lineTotalsSum > 0.0) finalTotal = lineTotalsSum
        }

        val paymentMap = safeMap(data["payment"])
        val rawPaymentMethod = (safeString(data["paymentMethod"])
            ?: safeString(paymentMap["method"])
            ?: "COD").trim()

        val placedAtMs = FormatUtils.parseTimestamp(data["placedAtTimestamp"])
            ?: FormatUtils.parseTimestamp(data["createdAt"])
            ?: FormatUtils.parseTimestamp(data["placedAt"])
            ?: System.currentTimeMillis()

        val prepMins = safeInt(data["prepMinutes"]) ?: safeInt(data["prepTimeMinutes"])
        val prepEnd = FormatUtils.parseTimestamp(data["prepEndTime"]) ?: if (prepMins != null) {
            placedAtMs + (prepMins * 60 * 1000L)
        } else null

        val tipVal = safeDouble(feesMap["tip"])
            ?: safeDouble(data["tip"])
            ?: 0.0
        val rawFulfillment = (safeString(data["fulfillment"]) ?: "").trim()
        val hasPickup = safeBoolean(data["hasPickup"], false)
        val scheduledForVal = FormatUtils.parseTimestamp(data["scheduledFor"])
        val editedAtVal = FormatUtils.parseTimestamp(data["editedAt"])
        val isEditedVal = safeBoolean(data["isEdited"], editedAtVal != null)
        val editedByVal = safeString(data["editedBy"]) ?: ""
        val editNoteVal = safeString(data["editNote"]) ?: ""
        val origTotalVal = safeDouble(data["originalTotal"])
        val cashCollectVal = safeDouble(data["cashToCollect"]) ?: safeDouble(data["codAmount"])
        val walletAdjustVal = safeDouble(data["walletAdjustDue"])
        val riderVehDisplay = effRiderPlate.ifBlank { effRiderVehicleType }.ifBlank { rVehType }

        val origItemsRaw = safeList(data["originalItems"])
        val origItems = origItemsRaw.mapIndexedNotNull { idx, itemObj ->
            val itm = safeMap(itemObj)
            if (itm.isEmpty() && itemObj !is Map<*, *>) return@mapIndexedNotNull null
            val pVal = safeDouble(itm["price"]) ?: safeDouble(itm["unitPrice"]) ?: 0.0
            val qVal = safeInt(itm["qty"]) ?: safeInt(itm["quantity"]) ?: 1
            OrderItem(
                id = safeString(itm["id"]) ?: "orig_$idx",
                name = safeString(itm["name"]) ?: safeString(itm["title"]) ?: "Item",
                quantity = if (qVal > 0) qVal else 1,
                price = pVal,
                unitPrice = pVal
            )
        }

        val rawJsonStr = try {
            org.json.JSONObject(data).toString(2)
        } catch (_: Throwable) {
            ""
        }

        val rawSplitOut = data["splitOut"]
        val hasSplitOut = when (rawSplitOut) {
            null -> false
            is Boolean -> rawSplitOut
            is String -> rawSplitOut.isNotBlank() && !rawSplitOut.equals("false", ignoreCase = true) && rawSplitOut != "0"
            is Collection<*> -> rawSplitOut.isNotEmpty()
            is Map<*, *> -> rawSplitOut.isNotEmpty()
            is Number -> rawSplitOut.toDouble() != 0.0
            else -> true
        }

        val rawHistory = safeList(data["statusHistory"]).mapNotNull { entryObj ->
            val m = safeMap(entryObj)
            if (m.isEmpty()) null else m
        }

        fun extractHistoryTimestamp(vararg targetStatuses: String): Long? {
            for (entry in rawHistory) {
                val s = safeString(entry["status"])?.lowercase() ?: ""
                if (targetStatuses.any { it.equals(s, ignoreCase = true) }) {
                    val t = FormatUtils.parseTimestamp(entry["at"])
                        ?: FormatUtils.parseTimestamp(entry["timestamp"])
                    if (t != null) return t
                }
            }
            return null
        }

        val acceptedAtVal = FormatUtils.parseTimestamp(data["acceptedAt"])
            ?: extractHistoryTimestamp("preparing", PlatformOrderStatuses.PREPARING)
        val readyAtVal = FormatUtils.parseTimestamp(data["readyAt"])
            ?: extractHistoryTimestamp("ready", PlatformOrderStatuses.READY)
        val rejectedAtVal = FormatUtils.parseTimestamp(data["rejectedAt"])
            ?: extractHistoryTimestamp("rejected", PlatformOrderStatuses.REJECTED)

        val customerMap = safeMap(data["customer"])
        val customerNameVal = (safeString(data["customerName"])
            ?: safeString(data["recipientName"])
            ?: safeString(customerMap["name"])
            ?: safeString(dropoffMap["name"])
            ?: safeString(dropoffMap["contactName"])
            ?: "Customer").ifBlank { "Customer" }
        val customerPhoneVal = safeString(data["customerPhone"])
            ?: safeString(data["recipientPhone"])
            ?: safeString(customerMap["phone"])
            ?: safeString(dropoffMap["phone"])
            ?: safeString(dropoffMap["contactPhone"])
            ?: ""

        val customerIdVal = (safeString(data["customerId"])
            ?: safeString(data["customerUid"])
            ?: safeString(data["userId"])
            ?: safeString(customerMap["id"])
            ?: safeString(customerMap["uid"])
            ?: customerPhoneVal
            ?: customerNameVal).trim()

        val deliveryMap = safeMap(data["delivery"])
        val delOptRaw = (safeString(data["deliveryOption"])
            ?: safeString(deliveryMap["option"])
            ?: "").trim()
        val delOptVal = when {
            delOptRaw.contains("saver", ignoreCase = true) -> "Saver"
            delOptRaw.contains("standard", ignoreCase = true) -> "Standard"
            delOptRaw.contains("priority", ignoreCase = true) -> "Priority"
            else -> "Priority"
        }

        val dispatchMap = safeMap(data["dispatch"])
        val holdMinsParsed = safeInt(dispatchMap["holdMinutes"])
            ?: safeInt(data["dispatchHoldMinutes"])
            ?: safeInt(data["holdMinutes"])
        val dispatchHoldMinutesVal = holdMinsParsed ?: if (delOptVal == "Standard" || delOptVal == "Saver") 5 else 0

        val rawOrderNum = safeString(data["orderNumber"]) ?: safeString(data["orderRef"])
        val orderNum = if (!rawOrderNum.isNullOrBlank()) {
            val clean = if (rawOrderNum.startsWith("#")) rawOrderNum.removePrefix("#").trim() else rawOrderNum.trim()
            "#$clean"
        } else {
            "#" + (if (docId.length >= 6) docId.takeLast(6).uppercase() else docId.uppercase())
        }

        val promoMapRaw = safeMap(data["promo"]).ifEmpty { safeMap(feesMap["promo"]) }
        val promoObj = if (promoMapRaw.isNotEmpty()) {
            val pCode = safeString(promoMapRaw["code"]) ?: ""
            val pDiscount = safeDouble(promoMapRaw["discount"]) ?: 0.0
            val pFundedBy = safeString(promoMapRaw["fundedBy"]) ?: "merchant"
            val pMerchantId = safeString(promoMapRaw["merchantId"]) ?: ""
            if (pCode.isNotBlank() && pDiscount > 0.0) {
                OrderPromo(code = pCode, discount = pDiscount, fundedBy = pFundedBy, merchantId = pMerchantId)
            } else null
        } else null

        val testOrderFlag = safeBoolean(data["isTestOrder"], false)
        val merchantIdVal = safeString(data["merchantId"])?.takeIf { it.isNotBlank() } ?: fallbackMerchantId

        return Order(
            id = docId,
            merchantId = merchantIdVal,
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
            placedAtTimestamp = placedAtMs,
            prepMinutes = prepMins,
            prepEndTime = prepEnd,
            rejectReason = safeString(data["rejectReason"]) ?: safeString(data["rejectionReason"]),
            cancelReason = safeString(data["cancelReason"]),
            riderRequest = riderRequest,
            rider = rider,
            riderId = effRiderId,
            riderName = effRiderName,
            riderPhone = effRiderPhone,
            riderLocation = riderLoc,
            isTestOrder = testOrderFlag,
            cityId = safeString(data["cityId"]) ?: "",
            serviceType = safeString(data["serviceType"]) ?: "food",
            hasPickup = hasPickup,
            tip = tipVal,
            fulfillment = rawFulfillment.ifBlank { "delivery" },
            scheduledFor = scheduledForVal,
            isEdited = isEditedVal,
            editedAt = editedAtVal,
            editedBy = editedByVal,
            editNote = editNoteVal,
            originalItems = origItems,
            originalTotal = origTotalVal,
            cashToCollect = cashCollectVal,
            walletAdjustDue = walletAdjustVal,
            riderVehicle = riderVehDisplay,
            note = (safeString(data["note"]) ?: safeString(data["riderNote"]) ?: safeString(dropoffMap["note"]) ?: safeString(data["notes"]) ?: "").trim(),
            feesRaw = feesMap,
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
            updatedAt = FormatUtils.parseTimestamp(data["updatedAt"]) ?: 0L,
            merchantName = safeString(data["merchantName"]) ?: "",
            branchName = safeString(data["branchName"]) ?: "",
            branchCode = safeString(data["branchCode"]) ?: "",
            chainId = safeString(data["chainId"]) ?: ""
        )
    }
}
