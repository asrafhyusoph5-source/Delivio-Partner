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
    DELIVERED("Delivered"),
    COMPLETED("Completed"),
    REJECTED("Rejected"),
    CANCELLED("Cancelled");

    companion object {
        fun fromString(str: String?): OrderStatus {
            if (str == null) return PLACED_ORDER
            return entries.find {
                it.displayTitle.equals(str, ignoreCase = true) || it.name.equals(str, ignoreCase = true)
            } ?: PLACED_ORDER
        }
    }
}

const val SCHEDULED_LEAD_TIME_MS: Long = 30 * 60 * 1000L

enum class FulfillmentType(val label: String) {
    PICK_UP("Pick Up"),
    SCHEDULED("Scheduled"),
    DELIVERY("Delivery")
}

fun fulfillmentOf(order: Order): FulfillmentType {
    val clean = order.fulfillment.trim().lowercase().replace("[\\s_\\-]".toRegex(), "")
    return when (clean) {
        "pickup" -> FulfillmentType.PICK_UP
        "scheduled", "scheduleddelivery" -> FulfillmentType.SCHEDULED
        "delivery" -> FulfillmentType.DELIVERY
        else -> {
            // If missing: Scheduled when scheduledFor exists, otherwise Delivery.
            // Never infer Pick Up from a missing dropoff or a zero delivery fee.
            if (order.scheduledFor != null && order.scheduledFor > 0) {
                FulfillmentType.SCHEDULED
            } else {
                FulfillmentType.DELIVERY
            }
        }
    }
}

data class OrderModifier(
    val name: String = "",
    val price: Double = 0.0
)

data class OrderItem(
    val id: String = "",
    val menuItemId: String = "",
    val name: String = "",
    val quantity: Int = 1,
    val price: Double = 0.0,
    val unitPrice: Double = price,
    val selectedSize: String? = null,
    val modifiers: List<OrderModifier> = emptyList(),
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
    val feesRaw: Map<String, Any?> = emptyMap(),
    val rawJson: String = ""
) {
    val totalItemCount: Int
        get() = items.sumOf { it.quantity }

    val fulfillmentType: FulfillmentType
        get() = fulfillmentOf(this)

    val isPickUp: Boolean
        get() = fulfillmentType == FulfillmentType.PICK_UP

    val isScheduled: Boolean
        get() = fulfillmentType == FulfillmentType.SCHEDULED

    val isDelivery: Boolean
        get() = fulfillmentType == FulfillmentType.DELIVERY

    fun isScheduledDue(now: Long = System.currentTimeMillis()): Boolean {
        val target = scheduledFor ?: return true
        return now >= (target - SCHEDULED_LEAD_TIME_MS)
    }

    val scheduledForFormatted: String
        get() = scheduledFor?.let { com.example.util.FormatUtils.formatScheduledDateTime(it) } ?: ""

    val receiptTotal: Double
        get() = if (isPickUp) (subtotal + serviceFee - discount).coerceAtLeast(0.0) else total

    val fulfillmentChipLabel: String
        get() = when (fulfillmentType) {
            FulfillmentType.PICK_UP -> "Pick Up"
            FulfillmentType.SCHEDULED -> {
                val timeStr = scheduledForFormatted
                if (timeStr.isNotBlank()) "Scheduled: $timeStr" else "Scheduled"
            }
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

    val effectivePaymentLabel: String
        get() {
            val pm = paymentMethod.trim().lowercase()
            val isCash = pm.contains("cod") || pm.contains("cash") || paymentMethod.equals("COD", ignoreCase = true)
            val isWallet = pm.contains("wallet")
            return when {
                isPickUp && isCash -> {
                    val collectAmount = if (cashToCollect != null && cashToCollect > 0.0) cashToCollect else total
                    "Pay at pickup - collect ${com.example.util.FormatUtils.formatCurrency(collectAmount)}"
                }
                !isPickUp && isCash -> "Cash on Delivery (rider collects)"
                isWallet -> "Paid with Delivio Wallet"
                else -> {
                    val provider = when {
                        pm.contains("gcash") -> "GCash"
                        pm.contains("maya") -> "Maya"
                        pm.contains("card") -> "Card"
                        else -> paymentMethod.ifBlank { "online" }
                    }
                    "Paid online ($provider)"
                }
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

    fun canRequestRiderAgain(now: Long = System.currentTimeMillis()): Boolean {
        if (isPickUp) return false
        if (status != OrderStatus.PREPARING && status != OrderStatus.READY) return false
        if (riderId.isNotBlank()) return false
        val req = riderRequest ?: return false
        val reqStatus = req.status.lowercase()
        if (reqStatus in listOf("cancelled", "released", "rejected")) return true
        if (reqStatus == "searching") {
            val reqAt = req.requestedAt ?: return false
            return (now - reqAt) >= 5 * 60 * 1000L
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

data class MenuItem(
    val id: String = "",
    val merchantId: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val name: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val inStock: Boolean = true,
    val soldOutReason: String? = null,
    val soldOutUntilTimestamp: Long? = null,
    val businessType: BusinessType = BusinessType.FOOD,
    val iconEmoji: String = "🍔",
    val imageUrl: String = "",
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
    val stockCount: Int = 50,
    val lowStockAlertThreshold: Int = 5,
    val discountPercentage: Double? = null
) {
    val available: Boolean get() = inStock

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
    val closedAt: Long? = null
)
