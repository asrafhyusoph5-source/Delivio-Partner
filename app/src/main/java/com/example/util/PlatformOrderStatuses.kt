package com.example.util

import com.example.data.model.OrderStatus

object PlatformOrderStatuses {
    const val PLACED_ORDER = "Placed Order"
    const val PREPARING = "Preparing"
    const val READY = "Ready"
    const val RIDER_ON_THE_WAY = "Rider is on the way"
    const val DELIVERED = "Delivered"
    const val REJECTED = "Rejected"
    const val CANCELLED = "Cancelled"

    enum class MerchantOrderTab(val label: String) {
        NEW("New"),
        PREPARING("Preparing"),
        READY("Ready"),
        COMPLETED("Completed"),
        CANCELLED("Cancelled")
    }

    fun matchesTab(statusStr: String, tab: MerchantOrderTab): Boolean {
        return when (tab) {
            MerchantOrderTab.NEW -> statusStr.equals(PLACED_ORDER, ignoreCase = true)
            MerchantOrderTab.PREPARING -> statusStr.equals(PREPARING, ignoreCase = true)
            MerchantOrderTab.READY -> statusStr.equals(READY, ignoreCase = true) || statusStr.equals(RIDER_ON_THE_WAY, ignoreCase = true)
            MerchantOrderTab.COMPLETED -> statusStr.equals(DELIVERED, ignoreCase = true)
            MerchantOrderTab.CANCELLED -> statusStr.equals(REJECTED, ignoreCase = true) || statusStr.equals(CANCELLED, ignoreCase = true)
        }
    }

    fun matchesTab(status: OrderStatus, tab: MerchantOrderTab): Boolean {
        return matchesTab(status.displayTitle, tab)
    }
}
