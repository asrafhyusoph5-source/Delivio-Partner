package com.example.data.model

enum class UserRole {
    CUSTOMER,
    MERCHANT,
    RIDER,
    ADMIN,
    STAFF
}

data class User(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "merchant",
    val merchantId: String = "",
    val status: String = "active"
)

enum class BusinessType {
    FOOD,
    CONVENIENCE;

    companion object {
        fun fromString(value: String?): BusinessType {
            return when (value?.trim()?.lowercase()) {
                "convenience" -> CONVENIENCE
                else -> FOOD
            }
        }
    }
}

enum class OrderStatus(val displayTitle: String) {
    PLACED_ORDER("Placed Order"),
    PREPARING("Preparing"),
    READY("Ready"),
    RIDER_ON_THE_WAY("Rider is on the way"),
    ON_ANOTHER_DELIVERY("On another delivery"),
    DELIVERED("Delivered"),
    COMPLETED("Completed"),
    REJECTED("Rejected"),
    CANCELLED("Cancelled");

    companion object {
        fun fromString(str: String?): OrderStatus {
            if (str == null) return PLACED_ORDER
            val clean = str.trim()
            if (clean.equals("On another delivery", ignoreCase = true) ||
                clean.equals("on_another_delivery", ignoreCase = true) ||
                clean.equals("ON_ANOTHER_DELIVERY", ignoreCase = true)) {
                return ON_ANOTHER_DELIVERY
            }
            return entries.find {
                it.displayTitle.equals(clean, ignoreCase = true) || it.name.equals(clean, ignoreCase = true)
            } ?: PLACED_ORDER
        }
    }
}

const val SCHEDULED_LEAD_TIME_MS: Long = 0L

enum class FulfillmentType(val label: String) {
    DELIVERY("Delivery"),
    PICK_UP("Pick Up")
}

fun fulfillmentOf(order: Order): FulfillmentType {
    val clean = order.fulfillment.trim().lowercase().replace("[\\s_\\-]".toRegex(), "")
    return if (clean == "pickup" || order.hasPickup) {
        FulfillmentType.PICK_UP
    } else {
        FulfillmentType.DELIVERY
    }
}

data class OrderModifier(
    val name: String = "",
    val price: Double = 0.0
)

data class OrderItemSelection(
    val setId: String = "",
    val setName: String = "",
    val choiceId: String = "",
    val choiceName: String = "",
    val price: Double = 0.0
)

data class OrderItem(
    val id: String = "",
    val menuItemId: String = "",
    val name: String = "",
    val quantity: Int = 1,
    val price: Double = 0.0,
    val basePrice: Double = price,
    val unitPrice: Double = price,
    val selectedSize: String? = null,
    val modifiers: List<OrderModifier> = emptyList(),
    val selections: List<OrderItemSelection> = emptyList(),
    val notes: String? = null
) {
    val effectivePrice: Double
        get() = when {
            price > 0.0 -> price
            unitPrice > 0.0 -> unitPrice
            else -> 0.0
        }

    val lineTotal: Double
        get() = effectivePrice * quantity

    val totalPrice: Double
        get() = lineTotal
}

data class AddressLocation(
    val street: String = "",
    val city: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val notes: String = ""
) {
    val formatted: String
        get() = if (street.isNotBlank() && city.isNotBlank()) "$street, $city" else street.ifBlank { city }
}

enum class RiderRequestStatus(val label: String) {
    SEARCHING("Searching for rider..."),
    RIDER_ASSIGNED("Rider Assigned"),
    ARRIVED_AT_MERCHANT("Arrived at Merchant"),
    PICKED_UP("Picked Up"),
    ON_THE_WAY("On the way to customer"),
    DELIVERED("Delivered"),
    CANCELLED("Cancelled")
}

data class Rider(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val vehiclePlate: String = "",
    val vehicleType: String = "Motorcycle",
    val rating: Double = 5.0,
    val etaMinutes: Int = 5,
    val avatarEmoji: String = "🛵"
)

data class ServiceRate(
    val baseFare: Double = 45.0,
    val includedKm: Double = 2.0,
    val perKmRate: Double = 10.0,
    val minFee: Double = 45.0,
    val maxFee: Double = 300.0,
    val serviceFee: Double = 10.0
) {
    fun calculateFee(distanceKm: Double): Double {
        val distFee = if (distanceKm <= includedKm) {
            baseFare
        } else {
            baseFare + (distanceKm - includedKm) * perKmRate
        }
        val total = distFee + serviceFee
        return total.coerceIn(minFee, maxFee)
    }
}

data class RiderRequest(
    val id: String = "",
    val orderId: String = "",
    val status: String = "Searching",
    val requestedAt: Long? = null,
    val requestedBy: String = "",
    val distanceKm: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val note: String = "",
    val feePayer: String = "customer",
    val rider: Rider? = null,
    val pickupAddress: AddressLocation = AddressLocation(),
    val dropoffAddress: AddressLocation = AddressLocation(),
    val progressPercent: Float = 0f
) {
    val isSearching: Boolean get() = status.equals("Searching", ignoreCase = true)
    val isAssigned: Boolean get() = status.equals("Rider assigned", ignoreCase = true) || status.equals("Assigned", ignoreCase = true)
    val isArrived: Boolean get() = status.equals("Arrived at merchant", ignoreCase = true) || status.equals("Arrived", ignoreCase = true)
    val isPickedUp: Boolean get() = status.equals("Picked up", ignoreCase = true)
    val isDelivered: Boolean get() = status.equals("Delivered", ignoreCase = true)
    val isCancelled: Boolean get() = status.equals("Cancelled", ignoreCase = true)
}

data class Order(
    val id: String = "",
    val merchantId: String = "",
    val orderNumber: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val deliveryAddress: AddressLocation = AddressLocation(),
    val dropoffLat: Double? = null,
    val dropoffLng: Double? = null,
    val items: List<OrderItem> = emptyList(),
    val subtotal: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val serviceFee: Double = 0.0,
    val discount: Double = 0.0,
    val platformFee: Double = 0.0,
    val total: Double = 0.0,
    val paymentMethod: String = "COD",
    val status: OrderStatus = OrderStatus.PLACED_ORDER,
    val placedAtTimestamp: Long = System.currentTimeMillis(),
    val prepMinutes: Int? = null,
    val prepEndTime: Long? = null,
    val rejectReason: String? = null,
    val cancelReason: String? = null,
    val riderRequest: RiderRequest? = null,
    val rider: Rider? = null,
    val riderId: String = "",
    val riderName: String = "",
    val riderPhone: String = "",
    val riderLocation: AddressLocation? = null,
    val isTestOrder: Boolean = false,
    val cityId: String = "",
    val serviceType: String = "food",
    val hasPickup: Boolean = false,
    val tip: Double = 0.0,
    val fulfillment: String = "delivery",
    val scheduledFor: Long? = null,
    val isEdited: Boolean = false,
    val editedAt: Long? = null,
    val editedBy: String = "",
    val editNote: String = "",
    val originalItems: List<OrderItem> = emptyList(),
    val originalTotal: Double? = null,
    val cashToCollect: Double? = null,
    val walletAdjustDue: Double? = null,
    val riderVehicle: String = "",
    val note: String = "",
    val feesRaw: Map<String, Any?> = emptyMap(),
    val rawJson: String = "",
    val acceptedAt: Long? = null,
    val readyAt: Long? = null,
    val deliveryOption: String = "Priority",
    val dispatchHoldMinutes: Int = 0,
    val isSplitChild: Boolean = false,
    val splitOut: Boolean = false,
    val promo: OrderPromo? = null,
    val customerId: String = "",
    val rejectedAt: Long? = null,
    val statusHistory: List<Map<String, Any?>> = emptyList(),
    val updatedAt: Long = 0L
) {
    fun isChatOpen(): Boolean {
        val s = status.name
        return !s.equals("DELIVERED", true) &&
               !s.equals("COMPLETED", true) &&
               !s.equals("CANCELLED", true) &&
               !s.equals("REJECTED", true)
    }

    fun getOrderClosedTimeMs(): Long? {
        if (isChatOpen()) return null
        val finalHistoryEntry = statusHistory.lastOrNull { entry ->
            val st = (entry["status"] as? String) ?: ""
            st.equals("Delivered", true) || st.equals("Completed", true) ||
            st.equals("Cancelled", true) || st.equals("Rejected", true) ||
            st.equals("DELIVERED", true) || st.equals("COMPLETED", true) ||
            st.equals("CANCELLED", true) || st.equals("REJECTED", true)
        }
        if (finalHistoryEntry != null) {
            val t = finalHistoryEntry["timestamp"]
            val ms = com.example.util.FormatUtils.parseTimestamp(t)
            if (ms != null && ms > 0L) return ms
        }
        if (rejectedAt != null && rejectedAt > 0L) return rejectedAt
        if (updatedAt > 0L) return updatedAt
        return placedAtTimestamp
    }

    fun isChatVisible(currentTime: Long = System.currentTimeMillis()): Boolean {
        if (serviceType.equals("request_rider", ignoreCase = true)) return false
        if (isScheduled && !isScheduledDue(currentTime)) return false
        if (isChatOpen()) return true
        val closedTime = getOrderClosedTimeMs() ?: return true
        return (currentTime - closedTime) < (24 * 3600000L)
    }

    fun isChatDeleted(currentTime: Long = System.currentTimeMillis()): Boolean {
        if (isChatOpen()) return false
        val closedTime = getOrderClosedTimeMs() ?: return false
        return (currentTime - closedTime) >= (24 * 3600000L)
    }

    val effectiveMerchantPromoDiscount: Double
        get() = if (promo != null && promo.fundedBy.equals("merchant", ignoreCase = true)) promo.discount else 0.0

    val netSales: Double
        get() = (subtotal - effectiveMerchantPromoDiscount).coerceAtLeast(0.0)

    val prepDurationMinutes: Double?
        get() = if (readyAt != null && acceptedAt != null && readyAt >= acceptedAt) {
            (readyAt - acceptedAt) / 60000.0
        } else null

    val isDeliveredOrCompleted: Boolean
        get() = status == OrderStatus.DELIVERED || status == OrderStatus.COMPLETED

    val effectiveCustomerId: String
        get() = customerId.ifBlank { customerPhone.ifBlank { customerName.trim().lowercase() } }

    val effectiveMerchantNote: String
        get() {
            if (note.isNotBlank()) return note.trim()
            val fNote = (feesRaw["notes"] as? String)
                ?: (feesRaw["specialInstructions"] as? String)
                ?: (feesRaw["merchantNote"] as? String)
                ?: (feesRaw["note"] as? String)
                ?: ""
            return fNote.trim()
        }

    val totalItemCount: Int
        get() = items.sumOf { it.quantity }

    val fulfillmentType: FulfillmentType
        get() = fulfillmentOf(this)

    val isPickUp: Boolean
        get() = fulfillmentType == FulfillmentType.PICK_UP

    val isDelivery: Boolean
        get() = fulfillmentType == FulfillmentType.DELIVERY

    val isScheduled: Boolean
        get() = scheduledFor != null && scheduledFor > 0

    fun isScheduledDue(now: Long = System.currentTimeMillis(), leadMinutes: Int = 0): Boolean {
        val target = scheduledFor ?: return true
        if (target <= 0) return true
        val leadMs = leadMinutes * 60 * 1000L
        return now >= (target - leadMs)
    }

    val scheduledForFormatted: String
        get() = scheduledFor?.let { com.example.util.FormatUtils.formatScheduledDateTime(it) } ?: ""

    val scheduledTimeFormatted: String
        get() = scheduledFor?.let { com.example.util.FormatUtils.formatScheduledTime(it) } ?: ""

    val receiptTotal: Double
        get() = if (isPickUp) (subtotal + serviceFee - discount).coerceAtLeast(0.0) else total

    val fulfillmentChipLabel: String
        get() = when (fulfillmentType) {
            FulfillmentType.PICK_UP -> "Pick Up"
            FulfillmentType.DELIVERY -> "Delivery"
        }

    fun toDebugJson(): String {
        return try {
            val json = org.json.JSONObject()
            json.put("id", id)
            json.put("orderNumber", orderNumber)
            json.put("customerName", customerName)
            json.put("customerPhone", customerPhone)
            json.put("status", status.name)
            json.put("fulfillment", fulfillment)
            json.put("fulfillmentType", fulfillmentType.name)
            json.put("scheduledFor", scheduledFor)
            json.put("paymentMethod", paymentMethod)
            json.put("subtotal", subtotal)
            json.put("deliveryFee", deliveryFee)
            json.put("serviceFee", serviceFee)
            json.put("discount", discount)
            json.put("tip", tip)
            json.put("total", total)
            json.put("cashToCollect", cashToCollect)
            json.put("walletAdjustDue", walletAdjustDue)
            json.put("isEdited", isEdited)
            json.put("editedAt", editedAt)
            json.put("editedBy", editedBy)
            json.put("editNote", editNote)
            json.put("acceptedAt", acceptedAt)
            json.put("readyAt", readyAt)
            json.put("deliveryOption", deliveryOptionDisplay)
            json.put("dispatchHoldMinutes", dispatchHoldMinutes)
            json.put("isSplitChild", isSplitChild)
            json.put("splitOut", splitOut)
            val itemsArr = org.json.JSONArray()
            items.forEach { itm ->
                val itmJson = org.json.JSONObject()
                itmJson.put("id", itm.id)
                itmJson.put("name", itm.name)
                itmJson.put("quantity", itm.quantity)
                itmJson.put("price", itm.effectivePrice)
                itemsArr.put(itmJson)
            }
            json.put("items", itemsArr)
            json.toString(2)
        } catch (_: Exception) {
            "{ \"id\": \"$id\", \"orderNumber\": \"$orderNumber\" }"
        }
    }

    val formattedOrderNumber: String
        get() {
            val num = orderNumber.trim()
            val clean = if (num.startsWith("#")) num.removePrefix("#").trim() else num
            return if (clean.isNotBlank()) {
                "#$clean"
            } else {
                val fallback = if (id.length >= 6) id.takeLast(6).uppercase() else id.uppercase().ifBlank { "000000" }
                "#$fallback"
            }
        }

    val effectivePaymentLabel: String
        get() {
            val pm = paymentMethod.trim().lowercase()
            val isCash = (pm.contains("cod") || pm.contains("cash") || paymentMethod.equals("COD", ignoreCase = true)) && !pm.contains("gcash")
            val overallTotal = if (total > 0.0) total else (if (cashToCollect != null && cashToCollect > 0.0) cashToCollect else subtotal)
            return when {
                isPickUp && isCash -> "Pay at pickup - collect ${com.example.util.FormatUtils.formatCurrency(overallTotal)}"
                isPickUp -> "Paid online - nothing to collect"
                isCash -> "Cash on Delivery"
                else -> "Paid online"
            }
        }

    val formattedPaymentMethod: String
        get() = effectivePaymentLabel

    val effectiveDropoffLat: Double
        get() = dropoffLat ?: deliveryAddress.latitude

    val effectiveDropoffLng: Double
        get() = dropoffLng ?: deliveryAddress.longitude

    val hasDropoffCoordinates: Boolean
        get() = (dropoffLat != null && dropoffLat != 0.0) ||
                (deliveryAddress.latitude != 0.0 && deliveryAddress.latitude != 14.5492)

    val isSearchingRider: Boolean
        get() = riderRequest?.status?.equals("Searching", ignoreCase = true) == true

    val isRiderAssigned: Boolean
        get() = riderId.isNotBlank() ||
                riderRequest?.status?.equals("Rider assigned", ignoreCase = true) == true ||
                riderRequest?.status?.equals("Assigned", ignoreCase = true) == true

    val deliveryOptionDisplay: String
        get() = when {
            deliveryOption.contains("saver", ignoreCase = true) -> "Saver"
            deliveryOption.contains("standard", ignoreCase = true) -> "Standard"
            else -> "Priority" // missing or anything else defaults to Priority
        }

    val effectiveHoldMinutes: Int
        get() = if (deliveryOptionDisplay.equals("Standard", ignoreCase = true) || deliveryOptionDisplay.equals("Saver", ignoreCase = true)) {
            dispatchHoldMinutes.coerceAtLeast(0)
        } else {
            0
        }

    fun riderHoldRemainingMs(now: Long = System.currentTimeMillis()): Long {
        val start = acceptedAt ?: placedAtTimestamp
        val holdMs = effectiveHoldMinutes * 60 * 1000L
        val holdEnd = start + holdMs
        return (holdEnd - now).coerceAtLeast(0L)
    }

    fun riderHoldRemainingFormatted(now: Long = System.currentTimeMillis()): String {
        val totalSec = riderHoldRemainingMs(now) / 1000L
        val minutes = totalSec / 60
        val seconds = totalSec % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    fun isRiderBatchHoldActive(now: Long = System.currentTimeMillis()): Boolean {
        if (isPickUp) return false
        val opt = deliveryOptionDisplay
        if (opt != "Standard" && opt != "Saver") return false
        if (status == OrderStatus.RIDER_ON_THE_WAY || status == OrderStatus.DELIVERED ||
            status == OrderStatus.COMPLETED || status == OrderStatus.CANCELLED || status == OrderStatus.REJECTED) {
            return false
        }
        val start = acceptedAt ?: placedAtTimestamp
        val holdMs = effectiveHoldMinutes * 60 * 1000L
        if (holdMs <= 0) return false

        return if (opt == "Standard") {
            // Standard orders: show until earlier of acceptedAt + dispatch.holdMinutes or marked Ready
            (now < start + holdMs) && status != OrderStatus.READY
        } else {
            // Saver orders: show until acceptedAt + dispatch.holdMinutes exactly, even if marked Ready earlier
            now < start + holdMs
        }
    }

    fun riderBatchHoldMessage(now: Long = System.currentTimeMillis()): String {
        return if (deliveryOptionDisplay == "Saver") {
            "Rider search starts in ${riderHoldRemainingFormatted(now)}"
        } else {
            "Rider may be matched with another order"
        }
    }

    fun isNoRiderYet(now: Long = System.currentTimeMillis()): Boolean {
        if (isPickUp) return false
        val startBase = acceptedAt ?: riderRequest?.requestedAt ?: placedAtTimestamp
        val holdMs = effectiveHoldMinutes * 60 * 1000L
        val opt = deliveryOptionDisplay

        val holdEndsAt = when (opt) {
            "Standard" -> {
                // earlier of acceptedAt + dispatch.holdMinutes or the moment the order is marked Ready
                val nominalEnd = startBase + holdMs
                val effectiveReadyAt = readyAt ?: if (status == OrderStatus.READY) now else null
                if (effectiveReadyAt != null && effectiveReadyAt < nominalEnd) {
                    effectiveReadyAt
                } else {
                    nominalEnd
                }
            }
            "Saver" -> {
                // exactly acceptedAt + dispatch.holdMinutes, even if marked Ready earlier
                startBase + holdMs
            }
            else -> {
                startBase
            }
        }

        val threshold = holdEndsAt + (5 * 60 * 1000L)
        return now >= threshold
    }

    fun canRequestRiderAgain(now: Long = System.currentTimeMillis()): Boolean {
        if (isPickUp) return false
        if (status != OrderStatus.PREPARING && status != OrderStatus.READY) return false
        if (riderId.isNotBlank()) return false
        val req = riderRequest ?: return false
        val reqStatus = req.status.lowercase()
        if (reqStatus in listOf("cancelled", "released", "rejected")) return true
        if (reqStatus == "searching") {
            return isNoRiderYet(now)
        }
        return false
    }

    val canRequestRider: Boolean
        get() = !isPickUp &&
                (status == OrderStatus.PREPARING || status == OrderStatus.READY) &&
                riderId.isBlank() &&
                !isSearchingRider &&
                !isRiderAssigned
}

data class RiderRequestErrorInfo(
    val orderNumber: String = "",
    val errorMessage: String = "",
    val fieldsWritten: String = ""
)

data class MenuItemModifier(
    val id: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val isDefault: Boolean = false
)

data class MenuCategory(
    val id: String = "",
    val name: String = "",
    val sortOrder: Int = 0,
    val visible: Boolean = true,
    val businessType: BusinessType = BusinessType.FOOD,
    val merchantId: String = ""
) {
    val displayOrder: Int get() = sortOrder
    val isVisible: Boolean get() = visible
}

data class ChangeRequest(
    val id: String = "",
    val merchantId: String = "",
    val kind: String = "shopName", // "type" | "shopName" | "email"
    val requestedValue: String = "",
    val reason: String = "",
    val status: String = "Pending", // "Pending" | "Approved" | "Rejected"
    val createdAt: Long = System.currentTimeMillis()
)

data class Subcategory(
    val id: String = "",
    val name: String = "",
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ProductSetChoice(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val price: Double = 0.0,
    val available: Boolean = true,
    val popular: Boolean = false
)

data class ProductSet(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val subcategoryId: String? = null,
    val subcategoryName: String? = null,
    val customName: String = "",
    val required: Boolean = true,
    val minSelect: Int = 1,
    val maxSelect: Int = 1,
    val sortOrder: Int = 0,
    val choices: List<ProductSetChoice> = emptyList()
)

data class MenuSchedule(
    val days: List<String> = emptyList(), // subset of ["mon", "tue", "wed", "thu", "fri", "sat", "sun"]
    val start: String = "00:00", // "HH:mm" 24h
    val end: String = "23:59" // "HH:mm" 24h
) {
    fun isOvernight(): Boolean {
        if (start.isBlank() || end.isBlank()) return false
        val (sH, sM) = start.split(":").mapNotNull { it.toIntOrNull() }.let { if (it.size == 2) it[0] to it[1] else return false }
        val (eH, eM) = end.split(":").mapNotNull { it.toIntOrNull() }.let { if (it.size == 2) it[0] to it[1] else return false }
        return (eH * 60 + eM) < (sH * 60 + sM)
    }

    fun isAllDay(): Boolean {
        return (start == "00:00" && (end == "23:59" || end == "24:00")) || (start.isBlank() && end.isBlank())
    }

    fun scheduleSummary(): String {
        if (days.isEmpty()) return "Always available"
        val dayLabel = when {
            days.size == 7 -> "Every day"
            days.size == 5 && days.containsAll(listOf("mon", "tue", "wed", "thu", "fri")) -> "Weekdays"
            days.size == 2 && days.containsAll(listOf("sat", "sun")) -> "Weekends"
            else -> days.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }
        }
        if (isAllDay()) return "$dayLabel · All day"
        return "$dayLabel · ${formatTime12h(start)} - ${formatTime12h(end)}"
    }

    companion object {
        fun formatTime12h(time24: String): String {
            if (time24.isBlank()) return ""
            val parts = time24.split(":")
            if (parts.size < 2) return time24
            val h = parts[0].toIntOrNull() ?: return time24
            val m = parts[1].toIntOrNull() ?: return time24
            val ampm = if (h >= 12) "PM" else "AM"
            val h12 = when {
                h == 0 -> 12
                h > 12 -> h - 12
                else -> h
            }
            return "%d:%02d %s".format(h12, m, ampm)
        }
    }
}

data class ShopMenu(
    val id: String = "main",
    val merchantId: String = "",
    val name: String = "Main Menu",
    val active: Boolean = true,
    val sortOrder: Int = 0,
    val schedule: MenuSchedule? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isMainMenu: Boolean get() = id == "main"

    fun scheduleSummary(): String {
        return schedule?.scheduleSummary() ?: "Always available"
    }

    fun isAvailableNow(currentTimeMillis: Long = System.currentTimeMillis(), timeZoneId: String = "Asia/Manila"): Boolean {
        if (!active) return false
        val sched = schedule ?: return true
        if (sched.days.isEmpty()) return true

        val tz = try {
            java.util.TimeZone.getTimeZone(timeZoneId)
        } catch (_: Exception) {
            java.util.TimeZone.getTimeZone("Asia/Manila")
        }
        val cal = java.util.Calendar.getInstance(tz).apply {
            timeInMillis = currentTimeMillis
        }

        fun dayToKey(calDay: Int): String = when (calDay) {
            java.util.Calendar.MONDAY -> "mon"
            java.util.Calendar.TUESDAY -> "tue"
            java.util.Calendar.WEDNESDAY -> "wed"
            java.util.Calendar.THURSDAY -> "thu"
            java.util.Calendar.FRIDAY -> "fri"
            java.util.Calendar.SATURDAY -> "sat"
            java.util.Calendar.SUNDAY -> "sun"
            else -> "mon"
        }

        val todayKey = dayToKey(cal.get(java.util.Calendar.DAY_OF_WEEK))
        val currentMinutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)

        val (sH, sM) = sched.start.split(":").mapNotNull { it.toIntOrNull() }.let { if (it.size == 2) it[0] to it[1] else 0 to 0 }
        val (eH, eM) = sched.end.split(":").mapNotNull { it.toIntOrNull() }.let { if (it.size == 2) it[0] to it[1] else 23 to 59 }
        val startMin = sH * 60 + sM
        val endMin = eH * 60 + eM

        return if (!sched.isOvernight()) {
            todayKey in sched.days && currentMinutes in startMin..endMin
        } else {
            // Overnight schedule: e.g. 20:00 to 04:00
            // 1. Started today and hasn't passed midnight
            val matchToday = todayKey in sched.days && currentMinutes >= startMin
            // 2. Started yesterday and is currently before endMin
            val calYesterday = (cal.clone() as java.util.Calendar).apply {
                add(java.util.Calendar.DATE, -1)
            }
            val yesterdayKey = dayToKey(calYesterday.get(java.util.Calendar.DAY_OF_WEEK))
            val matchYesterday = yesterdayKey in sched.days && currentMinutes <= endMin
            matchToday || matchYesterday
        }
    }

    fun nextOpenStatusDescription(currentTimeMillis: Long = System.currentTimeMillis(), timeZoneId: String = "Asia/Manila"): String {
        if (!active) return "Inactive"
        if (isAvailableNow(currentTimeMillis, timeZoneId)) {
            return "Available now"
        }
        val sched = schedule ?: return "Available now"
        return "Opens at ${MenuSchedule.formatTime12h(sched.start)}"
    }
}

data class ProductAvailability(
    val days: List<String> = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun"),
    val start: String = "00:00",
    val end: String = "23:59",
    val dateFrom: String? = null,
    val dateTo: String? = null
) {
    fun isOvernight(): Boolean {
        if (start.isBlank() || end.isBlank()) return false
        val (sH, sM) = start.split(":").mapNotNull { it.toIntOrNull() }.let { if (it.size == 2) it[0] to it[1] else return false }
        val (eH, eM) = end.split(":").mapNotNull { it.toIntOrNull() }.let { if (it.size == 2) it[0] to it[1] else return false }
        return (eH * 60 + eM) < (sH * 60 + sM)
    }

    fun isAllDay(): Boolean {
        return (start == "00:00" && (end == "23:59" || end == "24:00")) || (start.isBlank() && end.isBlank())
    }

    fun isAvailableNow(currentTimeMillis: Long = System.currentTimeMillis(), timeZoneId: String = "Asia/Manila"): Boolean {
        if (days.isEmpty()) return false

        val tz = try {
            java.util.TimeZone.getTimeZone(timeZoneId)
        } catch (_: Exception) {
            java.util.TimeZone.getTimeZone("Asia/Manila")
        }
        val cal = java.util.Calendar.getInstance(tz).apply {
            timeInMillis = currentTimeMillis
        }

        // Check date range limits if set
        val todayStr = "%04d-%02d-%02d".format(
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
        if (!dateFrom.isNullOrBlank() && todayStr < dateFrom) return false
        if (!dateTo.isNullOrBlank() && todayStr > dateTo) return false

        fun dayToKey(calDay: Int): String = when (calDay) {
            java.util.Calendar.MONDAY -> "mon"
            java.util.Calendar.TUESDAY -> "tue"
            java.util.Calendar.WEDNESDAY -> "wed"
            java.util.Calendar.THURSDAY -> "thu"
            java.util.Calendar.FRIDAY -> "fri"
            java.util.Calendar.SATURDAY -> "sat"
            java.util.Calendar.SUNDAY -> "sun"
            else -> "mon"
        }

        val todayKey = dayToKey(cal.get(java.util.Calendar.DAY_OF_WEEK))
        val currentMinutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)

        val (sH, sM) = start.split(":").mapNotNull { it.toIntOrNull() }.let { if (it.size == 2) it[0] to it[1] else 0 to 0 }
        val (eH, eM) = end.split(":").mapNotNull { it.toIntOrNull() }.let { if (it.size == 2) it[0] to it[1] else 23 to 59 }
        val startMin = sH * 60 + sM
        val endMin = eH * 60 + eM

        return if (!isOvernight()) {
            todayKey in days && currentMinutes in startMin..endMin
        } else {
            // Overnight: e.g. 20:00 to 04:00
            val matchToday = todayKey in days && currentMinutes >= startMin
            val calYesterday = (cal.clone() as java.util.Calendar).apply {
                add(java.util.Calendar.DATE, -1)
            }
            val yesterdayKey = dayToKey(calYesterday.get(java.util.Calendar.DAY_OF_WEEK))
            val matchYesterday = yesterdayKey in days && currentMinutes <= endMin
            matchToday || matchYesterday
        }
    }

    fun summary(): String {
        val dayLabel = when {
            days.size == 7 -> "Every day"
            days.size == 5 && days.containsAll(listOf("mon", "tue", "wed", "thu", "fri")) -> "Weekdays"
            days.size == 2 && days.containsAll(listOf("sat", "sun")) -> "Weekends"
            days.isEmpty() -> "No days selected"
            else -> days.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }
        }
        val timeLabel = if (isAllDay()) "All day" else "${MenuSchedule.formatTime12h(start)} - ${MenuSchedule.formatTime12h(end)}"
        val dateLabel = if (!dateFrom.isNullOrBlank() && !dateTo.isNullOrBlank()) {
            ", $dateFrom to $dateTo"
        } else if (!dateFrom.isNullOrBlank()) {
            ", from $dateFrom"
        } else if (!dateTo.isNullOrBlank()) {
            ", until $dateTo"
        } else ""

        return "Available $dayLabel, $timeLabel$dateLabel"
    }
}

data class MenuItem(
    val id: String = "",
    val merchantId: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val subcategoryId: String? = null,
    val subcategoryName: String? = null,
    val menuIds: List<String> = listOf("main"),
    val name: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val inStock: Boolean = true,
    val soldOutReason: String? = null,
    val soldOutUntilTimestamp: Long? = null,
    val businessType: BusinessType = BusinessType.FOOD,
    val iconEmoji: String = "🍔",
    val imageUrl: String = "",
    val hasSets: Boolean = false,
    val sets: List<ProductSet> = emptyList(),
    // Food specific
    val prepTimeMinutes: Int = 15,
    val tags: List<String> = emptyList(),
    val sizes: List<String> = emptyList(),
    val modifiers: List<MenuItemModifier> = emptyList(),
    val maxQuantity: Int = 20,
    // Convenience specific
    val sku: String = "",
    val barcode: String = "",
    val unit: String = "1 pc",
    val trackStock: Boolean = false,
    val stockCount: Int = 50,
    val lowStockAt: Int = 5,
    val discountPercentage: Double? = null,
    // Item size (convenience shops only, e.g. "190ml", "225g")
    val itemSize: String = "",
    // Per-product custom availability schedule (days, hours, date window)
    val availability: ProductAvailability? = null
) {
    val available: Boolean get() = inStock

    val lowStockAlertThreshold: Int get() = lowStockAt

    val isLowStock: Boolean
        get() = inStock && trackStock && stockCount > 0 && stockCount <= lowStockAt

    fun getEffectiveMenuIds(): List<String> {
        val filtered = menuIds.filter { it.isNotBlank() }
        return if (filtered.isEmpty()) listOf("main") else filtered
    }

    fun isAvailableNow(allMenus: List<ShopMenu>, currentTimeMillis: Long = System.currentTimeMillis(), timeZoneId: String = "Asia/Manila"): Boolean {
        if (!inStock) return false

        // Check product's own availability schedule if set
        if (availability != null && !availability.isAvailableNow(currentTimeMillis, timeZoneId)) {
            return false
        }

        val effIds = getEffectiveMenuIds()
        val relevantMenus = allMenus.filter { it.id in effIds }
        if (relevantMenus.isEmpty()) {
            // Default to main menu availability
            val main = allMenus.find { it.id == "main" }
            return main?.isAvailableNow(currentTimeMillis, timeZoneId) ?: true
        }
        return relevantMenus.any { it.isAvailableNow(currentTimeMillis, timeZoneId) }
    }

    fun getEffectiveCategoryName(categories: List<MenuCategory>): String {
        if (categoryName.isNotBlank()) return categoryName
        return categories.find { it.id == categoryId }?.name ?: ""
    }

    fun isCategoryInAllowedList(allowedCategories: List<String>, categories: List<MenuCategory>): Boolean {
        val eff = getEffectiveCategoryName(categories)
        return allowedCategories.any { it.equals(eff, ignoreCase = true) }
    }
}

data class PromoDeal(
    val id: String = "",
    val merchantId: String = "",
    val code: String = "",
    val title: String = "",
    val discountPercentage: Int = 10,
    val minOrder: Double = 0.0,
    val startDate: String = "",
    val endDate: String = "",
    val isActive: Boolean = true
)

data class OrderPromo(
    val code: String = "",
    val discount: Double = 0.0,
    val fundedBy: String = "merchant",
    val merchantId: String = ""
)

data class PromoRedemption(
    val id: String = "",
    val promoCode: String = "",
    val discount: Double = 0.0,
    val orderId: String = "",
    val merchantId: String = "",
    val customerUid: String = "",
    val redeemedAt: Long = System.currentTimeMillis()
)

data class MerchantVoucher(
    val code: String = "",
    val source: String = "merchant",
    val fundedBy: String = "merchant",
    val merchantId: String = "",
    val title: String = "",
    val type: String = "percent", // "percent" | "fixed"
    val value: Double = 10.0,
    val maxDiscount: Double? = null,
    val minSubtotal: Double = 0.0,
    val appliesTo: String = "store", // "store" | "items"
    val itemIds: List<String> = emptyList(),
    val fulfillment: String = "both", // "both" | "delivery" | "pickup"
    val services: List<String> = listOf("food"),
    val startsAt: Long? = null,
    val expiresAt: Long? = null,
    val usageLimit: Int? = null,
    val perUserLimit: Int = 1,
    val usedCount: Int = 0,
    val active: Boolean = true,
    val visibility: String = "public", // "public" | "private"
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val totalDiscountGiven: Double = 0.0,
    val redemptionsCount: Int = 0,
    val ordersCount: Int = 0
) {
    val isPublic: Boolean get() = visibility.equals("public", ignoreCase = true)
    val isExpired: Boolean get() = expiresAt != null && expiresAt > 0 && expiresAt <= System.currentTimeMillis()
    val isEffectiveActive: Boolean get() = active && !isExpired

    val discountDisplay: String
        get() = if (type.equals("percent", ignoreCase = true)) {
            val maxStr = if (maxDiscount != null && maxDiscount > 0.0) " (max ₱${com.example.util.FormatUtils.formatCurrency(maxDiscount)})" else ""
            "${value.toInt()}% off$maxStr"
        } else {
            "₱${com.example.util.FormatUtils.formatCurrency(value)} off"
        }

    val usesDisplay: String
        get() = if (usageLimit != null && usageLimit > 0) {
            "$usedCount / $usageLimit"
        } else {
            "$usedCount used"
        }

    val expiryDisplay: String
        get() = if (expiresAt != null && expiresAt > 0) {
            "Expires ${com.example.util.FormatUtils.formatDateOnly(expiresAt)}"
        } else {
            "No expiry"
        }
}

data class Payout(
    val id: String = "",
    val merchantId: String = "",
    val reference: String = "",
    val amount: Double = 0.0,
    val status: String = "Completed",
    val date: String = "",
    val bankName: String = "",
    val accountNumber: String = ""
)

data class DayHours(
    val open: String = "08:00",
    val close: String = "22:00",
    val closed: Boolean = false
) {
    val isOvernight: Boolean
        get() = !closed && close < open && close != "00:00"

    val isOpen24Hours: Boolean
        get() = !closed && ((open == "00:00" && close == "23:59") || (open == "00:00" && close == "00:00"))
}

data class WalletTransaction(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val type: String = "credit", // "credit" or "debit"
    val dateTimestamp: Long = System.currentTimeMillis(),
    val reference: String = "",
    val merchantId: String = "",
    val customerId: String = ""
)

data class DaySchedule(
    val day: String = "",
    val isOpen: Boolean = true,
    val openTime: String = "08:00",
    val closeTime: String = "22:00"
)

data class StaffMember(
    val id: String = "",
    val merchantId: String = "",
    val name: String = "",
    val role: String = "Staff",
    val email: String = "",
    val active: Boolean = true
)

data class MerchantApplication(
    val id: String = "",
    val ownerUid: String = "",
    val businessName: String = "",
    val ownerName: String = "",
    val phone: String = "",
    val email: String = "",
    val businessType: String = "food", // "food" | "convenience"
    val address: String = "",
    val lat: Double = 14.5492,
    val lng: Double = 121.0503,
    val cityId: String = "",
    val logoUrl: String = "",
    val storePhotoUrl: String = "",
    val permitUrl: String = "",
    val dtiSecUrl: String = "",
    val barangayUrl: String = "",
    val validIdUrl: String = "",
    val status: String = "Pending", // "Pending", "Under Review", "Needs Info", "Rejected", "Approved"
    val submittedAt: Long = System.currentTimeMillis(),
    val rejectionReason: String? = null,
    val adminNotes: String? = null
)

data class MerchantSettings(
    val storeName: String = "",
    val ownerName: String = "",
    val email: String = "",
    val phone: String = "",
    val businessType: BusinessType = BusinessType.FOOD,
    val type: String = "Food & Dining",
    val isOpen: Boolean = true,
    val isPaused: Boolean = false,
    val pauseUntilTimestamp: Long? = null,
    val autoScheduleEnabled: Boolean = true,
    val autoSchedule: Boolean = true,
    val hours: Map<String, DayHours> = mapOf(
        "mon" to DayHours("08:00", "22:00", false),
        "tue" to DayHours("08:00", "22:00", false),
        "wed" to DayHours("08:00", "22:00", false),
        "thu" to DayHours("08:00", "22:00", false),
        "fri" to DayHours("08:00", "23:00", false),
        "sat" to DayHours("09:00", "23:00", false),
        "sun" to DayHours("09:00", "21:00", false)
    ),
    val weeklySchedule: List<DaySchedule> = listOf(
        DaySchedule("Monday", true, "08:00", "22:00"),
        DaySchedule("Tuesday", true, "08:00", "22:00"),
        DaySchedule("Wednesday", true, "08:00", "22:00"),
        DaySchedule("Thursday", true, "08:00", "22:00"),
        DaySchedule("Friday", true, "08:00", "23:00"),
        DaySchedule("Saturday", true, "09:00", "23:00"),
        DaySchedule("Sunday", true, "09:00", "21:00")
    ),
    val storeLocation: AddressLocation = AddressLocation(
        street = "",
        city = "",
        latitude = 0.0,
        longitude = 0.0,
        notes = ""
    ),
    val minimumOrder: Double = 0.00,
    val defaultPrepTime: Int = 15,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val pushNotificationsEnabled: Boolean = true,
    val printerPaperSize: String = "80mm",
    val autoPrintOrders: Boolean = true,
    val themeAccentHex: String = "#FFC107",
    val isDarkMode: Boolean = false,
    val logoUrl: String = "",
    val bannerUrl: String = "",
    val imageUrl: String = "",
    val description: String = "",
    val bankName: String = "",
    val accountHolder: String = "",
    val accountNumber: String = "",
    val commissionRate: Double = 0.15,
    val cityId: String = "taguig",
    val linkedCustomerUid: String? = null,
    val walletBalance: Double = 0.0,
    val status: String = "active",
    val timezone: String = "Asia/Manila",
    val closedUntil: Long? = null,
    val openUntil: Long? = null,
    val closedAt: Long? = null,
    val locationChangedAt: Long? = null,
    val holidays: List<HolidaySchedule> = emptyList(),
    val bluetoothPrinterName: String = "",
    val bluetoothPrinterAddress: String = ""
)

data class HolidaySchedule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val from: Long = 0L,
    val to: Long = 0L,
    val note: String = ""
)

data class InboxMessage(
    val id: String = "",
    val merchantId: String = "",
    val audience: String = "",
    val title: String = "",
    val body: String = "",
    val category: String = "update", // "update" | "order" | "wallet" | "promo" | "system" | "admin"
    val imageUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val sendAt: Long? = null,
    val isRead: Boolean = false
) {
    val isAvailableNow: Boolean
        get() = sendAt == null || sendAt <= System.currentTimeMillis()
}

data class CustomerReview(
    val id: String = "",
    val merchantId: String = "",
    val orderId: String = "",
    val orderNumber: String = "",
    val ratingMerchant: Double = 5.0,
    val comment: String = "",
    val customerName: String = "Customer",
    val itemsSummary: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class AnonymousRating(
    val id: String = "",
    val merchantId: String = "",
    val rating: Double = 5.0,
    val comment: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class ReceiptCopyConfig(
    val enabled: Boolean = true,
    val title: String = "",
    val showItemPrices: Boolean = true
)

data class ReceiptDesign(
    val brandLine: String = "Delivio",
    val showOrderId: Boolean = true,
    val showOrderedAt: Boolean = true,
    val showCustomerName: Boolean = true,
    val showNote: Boolean = true,
    val showSetChoices: Boolean = true,
    val fontSize: String = "medium", // "small" | "medium" | "large"
    val align: String = "left",      // "center" | "left"
    val separator: String = "dashes", // "dashes" | "solid" | "double"
    val headerLines: List<String> = emptyList(),
    val footerText: String = "",
    val merchantCopy: ReceiptCopyConfig = ReceiptCopyConfig(
        enabled = true,
        title = "MERCHANT'S COPY",
        showItemPrices = true
    ),
    val customerCopy: ReceiptCopyConfig = ReceiptCopyConfig(
        enabled = true,
        title = "CUSTOMER'S COPY",
        showItemPrices = false
    )
)

data class OrderChatThread(
    val chatId: String = "",
    val orderId: String = "",
    val orderNumber: String = "",
    val channel: String = "merchant",
    val customerId: String = "",
    val customerName: String = "",
    val merchantId: String = "",
    val merchantName: String = "",
    val riderId: String = "",
    val riderName: String = "",
    val createdAt: Long = 0L,
    val lastMessageAt: Long = 0L,
    val lastMessageText: String = "",
    val lastSenderRole: String = "",
    val customerReadAt: Long? = null,
    val partnerReadAt: Long? = null,
    val expireAt: Long? = null
) {
    fun isUnreadForMerchant(): Boolean {
        if (lastSenderRole.equals("merchant", ignoreCase = true)) return false
        if (lastMessageText.isBlank() && lastMessageAt == 0L) return false
        val myReadAt = partnerReadAt ?: 0L
        return lastMessageAt > myReadAt
    }
}

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val senderRole: String = "merchant",
    val type: String = "text",
    val text: String = "",
    val image: String? = null,
    val source: String? = null,
    val createdAt: Long = 0L,
    val expireAt: Long? = null
)

