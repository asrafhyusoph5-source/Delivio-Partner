package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

data class SalePriceValidation(
    val isValid: Boolean,
    val parsedPrice: Double?,
    val errorMessage: String? = null
)

object PriceDropUtils {

    fun calculateDiscountPercent(regularPrice: Double, salePrice: Double): Int {
        if (regularPrice <= 0.0 || salePrice <= 0.0 || salePrice >= regularPrice) return 0
        return (((regularPrice - salePrice) / regularPrice) * 100.0).roundToInt()
    }

    fun formatDiscountPercent(regularPrice: Double, salePrice: Double): String {
        if (regularPrice <= 0.0 || salePrice <= 0.0 || salePrice >= regularPrice) return ""
        val pct = calculateDiscountPercent(regularPrice, salePrice)
        return "-$pct%"
    }

    fun validateSalePrice(regularPrice: Double, rawInput: String): SalePriceValidation {
        val trimmed = rawInput.trim()
        if (regularPrice <= 0.0) {
            return SalePriceValidation(
                isValid = false,
                parsedPrice = null,
                errorMessage = "Regular price must be above 0"
            )
        }
        if (trimmed.isEmpty()) {
            return SalePriceValidation(
                isValid = false,
                parsedPrice = null,
                errorMessage = "Enter a new price"
            )
        }
        val parsed = trimmed.toDoubleOrNull()
            ?: return SalePriceValidation(
                isValid = false,
                parsedPrice = null,
                errorMessage = "Enter a valid number"
            )
        if (parsed <= 0.0) {
            return SalePriceValidation(
                isValid = false,
                parsedPrice = parsed,
                errorMessage = "Must be above 0"
            )
        }
        if (parsed >= regularPrice) {
            return SalePriceValidation(
                isValid = false,
                parsedPrice = parsed,
                errorMessage = "Must be below ${FormatUtils.formatCurrency(regularPrice)}"
            )
        }
        val rounded = kotlin.math.round(parsed * 100.0) / 100.0
        if (rounded <= 0.0 || rounded >= regularPrice) {
            return SalePriceValidation(
                isValid = false,
                parsedPrice = parsed,
                errorMessage = "Must be above 0 and below ${FormatUtils.formatCurrency(regularPrice)}"
            )
        }
        return SalePriceValidation(
            isValid = true,
            parsedPrice = rounded,
            errorMessage = null
        )
    }

    /**
     * Exact data contract:
     * A sale is in effect when saleActive is true and 0 < salePrice < price
     * and now is inside saleStartsAt to saleEndsAt (either may be null).
     */
    fun isSaleInEffect(
        price: Double,
        salePrice: Double?,
        saleActive: Boolean,
        saleStartsAt: Long?,
        saleEndsAt: Long?,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (!saleActive) return false
        if (salePrice == null || salePrice <= 0.0 || price <= 0.0 || salePrice >= price) return false
        if (saleStartsAt != null && nowMs < saleStartsAt) return false
        if (saleEndsAt != null && nowMs > saleEndsAt) return false
        return true
    }

    /**
     * Returns true when a valid sale is active and scheduled to start in the future.
     */
    fun isSaleScheduled(
        price: Double,
        salePrice: Double?,
        saleActive: Boolean,
        saleStartsAt: Long?,
        saleEndsAt: Long?,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (!saleActive) return false
        if (salePrice == null || salePrice <= 0.0 || price <= 0.0 || salePrice >= price) return false
        if (saleStartsAt == null || nowMs >= saleStartsAt) return false
        if (saleEndsAt != null && saleEndsAt <= saleStartsAt) return false
        return true
    }

    fun formatDateTimeLabel(timestampMs: Long, timeZoneId: String = "Asia/Manila"): String {
        val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.US).apply {
            timeZone = TimeZone.getTimeZone(timeZoneId.ifBlank { "Asia/Manila" })
        }
        return sdf.format(Date(timestampMs))
    }

    fun formatEndSummary(
        saleStartsAt: Long?,
        saleEndsAt: Long?,
        nowMs: Long = System.currentTimeMillis(),
        timeZoneId: String = "Asia/Manila"
    ): String {
        val isScheduled = saleStartsAt != null && nowMs < saleStartsAt
        val endText = if (saleEndsAt != null) {
            "Ends ${formatDateTimeLabel(saleEndsAt, timeZoneId)}"
        } else {
            "Until you end it"
        }
        return if (isScheduled && saleStartsAt != null) {
            "Starts ${formatDateTimeLabel(saleStartsAt, timeZoneId)} · $endText"
        } else {
            endText
        }
    }
}
