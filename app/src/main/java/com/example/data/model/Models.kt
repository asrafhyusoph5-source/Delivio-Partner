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

data class OrderModifier(
    val name: String = "",
    val price: Double = 0.0
)

data class OrderItem(
    val id: String = "",
    val menuItemId: String = "",
    val name: String = "",
    val quantity: Int = 1,
    val unitPrice: Double = 0.0,
    val selectedSize: String? = null,
    val modifiers: List<OrderModifier> = emptyList(),
    val notes: String? = null
) {
    val totalPrice: Double
        get() = (unitPrice + modifiers.sumOf { it.price }) * quantity
}

data class AddressLocation(
    val street: String = "",
    val city: String = "",
    val latitude: Double = 14.5492,
    val longitude: Double = 121.0503,
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

data class RiderRequest(
    val id: String = "",
    val orderId: String = "",
    val status: RiderRequestStatus = RiderRequestStatus.SEARCHING,
    val rider: Rider? = null,
    val pickupAddress: AddressLocation = AddressLocation(),
    val dropoffAddress: AddressLocation = AddressLocation(),
    val deliveryFeeEstimate: Double = 3.50,
    val riderNote: String = "",
    val isSelfDelivery: Boolean = false,
    val progressPercent: Float = 0f
)

data class Order(
    val id: String = "",
    val merchantId: String = "",
    val orderNumber: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val deliveryAddress: AddressLocation = AddressLocation(),
    val items: List<OrderItem> = emptyList(),
    val subtotal: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val platformFee: Double = 0.0,
    val total: Double = 0.0,
    val paymentMethod: String = "Cash on Delivery",
    val status: OrderStatus = OrderStatus.PLACED_ORDER,
    val placedAtTimestamp: Long = System.currentTimeMillis(),
    val prepTimeMinutes: Int? = null,
    val prepEndTime: Long? = null,
    val rejectionReason: String? = null,
    val riderRequest: RiderRequest? = null,
    val rider: Rider? = null,
    val riderId: String = "",
    val riderName: String = "",
    val riderPhone: String = "",
    val riderLocation: AddressLocation? = null,
    val isTestOrder: Boolean = false
) {
    val totalItemCount: Int
        get() = items.sumOf { it.quantity }
}

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
)

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
    val storeName: String = "Delivio Partner",
    val ownerName: String = "",
    val email: String = "",
    val phone: String = "",
    val businessType: BusinessType = BusinessType.FOOD,
    val isOpen: Boolean = true,
    val isPaused: Boolean = false,
    val pauseUntilTimestamp: Long? = null,
    val autoScheduleEnabled: Boolean = true,
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
        street = "88 Rizal Avenue",
        city = "Taguig, Metro Manila",
        latitude = 14.5492,
        longitude = 121.0503,
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
    val bankName: String = "",
    val accountHolder: String = "",
    val accountNumber: String = ""
)
