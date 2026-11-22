package com.example.util

import com.example.data.model.NightConfig
import com.example.data.model.Order
import com.example.data.model.PeakConfig
import com.example.data.model.ServiceRate
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToLong

data class InstantQuote(
    val schemaVersion: Int,
    val ratesVersion: Long,
    val serviceKey: String,
    val baseDeliveryFee: Double,
    val deliveryFee: Double,
    val serviceFee: Double,
    val totalFee: Double,
    val isPeakApplied: Boolean,
    val isNightApplied: Boolean
)

data class OrderEarningsBreakdown(
    val itemsSubtotal: Double,
    val vouchers: Double,
    val commissionRate: Double,
    val commissionFee: Double,
    val netEarnings: Double
)

object PricingEngine {

    fun round2(value: Double): Double {
        if (!value.isFinite()) return 0.0
        return (value * 100.0).roundToLong() / 100.0
    }

    fun normalizeCommissionRate(raw: Double?, fallback: Double = 0.15): Double {
        if (raw == null || !raw.isFinite() || raw < 0.0) {
            return if (fallback > 1.0) fallback / 100.0 else fallback.coerceAtLeast(0.0)
        }
        return if (raw > 1.0) raw / 100.0 else raw
    }

    fun formatPercent(fraction: Double): String {
        val pct = round2(fraction * 100.0)
        return if (pct % 1.0 == 0.0) {
            "${pct.toInt()}%"
        } else {
            String.format(Locale.US, "%.1f%%", pct)
        }
    }

    fun isHourInWindow(hour: Int, startHour: Int, endHour: Int): Boolean {
        val normHour = ((hour % 24) + 24) % 24
        val s = ((startHour % 24) + 24) % 24
        val e = ((endHour % 24) + 24) % 24
        return if (s < e) {
            normHour in s until e
        } else {
            normHour >= s || normHour < e
        }
    }

    fun getHourOfDay(nowMs: Long, timezoneId: String): Int {
        val tz = TimeZone.getTimeZone(timezoneId.ifBlank { "Asia/Manila" })
        val cal = Calendar.getInstance(tz).apply { timeInMillis = nowMs }
        return cal.get(Calendar.HOUR_OF_DAY)
    }

    fun isPeakTime(
        nowMs: Long,
        timezoneId: String = "Asia/Manila",
        peakConfig: PeakConfig = PeakConfig()
    ): Boolean {
        val hour = getHourOfDay(nowMs, timezoneId)
        val s = peakConfig.startHour
        val e = peakConfig.endHour
        if (s != null && e != null) {
            return isHourInWindow(hour, s, e)
        }
        // Standard lunch (11:00–14:00) and dinner (17:00–21:00) peak windows
        return (hour in 11 until 14) || (hour in 17 until 21)
    }

    fun isNightTime(
        nowMs: Long,
        timezoneId: String = "Asia/Manila",
        nightConfig: NightConfig = NightConfig()
    ): Boolean {
        val hour = getHourOfDay(nowMs, timezoneId)
        val s = nightConfig.startHour
        val e = nightConfig.endHour
        if (s != null && e != null) {
            return isHourInWindow(hour, s, e)
        }
        // Standard night window: 22:00–06:00
        return hour >= 22 || hour < 6
    }

    /**
     * Computes Delivio Instant (Request a Rider) quote matching the admin simulator:
     * - Schema 2:
     *   deliveryFee = clamp(baseFare + max(0, km - includedKm) * perKm, minFee, maxFee)
     *                 * (peak.multiplier if peak.enabled && isPeakTime)
     *                 + (night.surcharge if night.enabled && isNightTime)
     *   serviceFee  = flat value OR percent of deliveryFee
     *   total       = deliveryFee + serviceFee
     * - Legacy (schema < 2):
     *   deliveryFee = clamp(baseFare + max(0, km - includedKm) * perKmRate, minFee, maxFee)
     *   serviceFee  = rate.serviceFee
     *   total       = deliveryFee + serviceFee
     */
    fun calculateInstantQuote(
        rate: ServiceRate,
        distanceKm: Double,
        nowMs: Long = System.currentTimeMillis(),
        timezoneId: String = "Asia/Manila",
        forcePeak: Boolean? = null,
        forceNight: Boolean? = null
    ): InstantQuote {
        val safeKm = distanceKm.coerceAtLeast(0.0)
        val extraKm = (safeKm - rate.includedKm).coerceAtLeast(0.0)
        val rawDist = rate.baseFare + extraKm * rate.perKmRate
        val clampedBase = round2(rawDist.coerceIn(rate.minFee, rate.maxFee))

        if (rate.schemaVersion >= 2) {
            val peakActive = rate.peakConfig.enabled && (forcePeak ?: isPeakTime(nowMs, timezoneId, rate.peakConfig))
            var running = clampedBase
            if (peakActive && rate.peakConfig.multiplier > 0.0) {
                running *= rate.peakConfig.multiplier
            }
            val nightActive = rate.nightConfig.enabled && (forceNight ?: isNightTime(nowMs, timezoneId, rate.nightConfig))
            if (nightActive && rate.nightConfig.surcharge != 0.0) {
                running += rate.nightConfig.surcharge
            }
            val deliveryFee = round2(running.coerceAtLeast(0.0))

            val sf = if (rate.serviceFeeConfig.mode.equals("percent", ignoreCase = true)) {
                round2(deliveryFee * (rate.serviceFeeConfig.value.coerceAtLeast(0.0) / 100.0))
            } else {
                round2(rate.serviceFeeConfig.value.coerceAtLeast(0.0))
            }

            return InstantQuote(
                schemaVersion = rate.schemaVersion,
                ratesVersion = rate.ratesVersion,
                serviceKey = rate.serviceKey.ifBlank { "instant" },
                baseDeliveryFee = clampedBase,
                deliveryFee = deliveryFee,
                serviceFee = sf,
                totalFee = round2(deliveryFee + sf),
                isPeakApplied = peakActive,
                isNightApplied = nightActive
            )
        } else {
            val deliveryFee = clampedBase
            val sf = round2(rate.serviceFee.coerceAtLeast(0.0))
            return InstantQuote(
                schemaVersion = rate.schemaVersion,
                ratesVersion = rate.ratesVersion,
                serviceKey = rate.serviceKey.ifBlank { "instant" },
                baseDeliveryFee = clampedBase,
                deliveryFee = deliveryFee,
                serviceFee = sf,
                totalFee = round2(deliveryFee + sf),
                isPeakApplied = false,
                isNightApplied = false
            )
        }
    }

    /**
     * Computes merchant earnings for a single regular order:
     * - Uses order.pricing.merchantCommissionRate when saved on the order snapshot; falls back to merchant's current rate.
     * - Commission Fee = commissionableSubtotal (items subtotal - merchant-funded vouchers) * rateUsed
     * - Net = itemsSubtotal - commissionFee - merchantFundedVouchers
     */
    fun calculateOrderEarnings(order: Order, fallbackMerchantRate: Double): OrderEarningsBreakdown {
        val itemsSubtotal = order.effectiveItemsSubtotal.coerceAtLeast(0.0)
        val vouchers = order.effectiveMerchantPromoDiscount.coerceAtLeast(0.0)
        val commissionable = (itemsSubtotal - vouchers).coerceAtLeast(0.0)
        val rateUsed = order.effectiveCommissionRate(fallbackMerchantRate)
        val commissionFee = round2(commissionable * rateUsed)
        val net = round2((itemsSubtotal - commissionFee - vouchers).coerceAtLeast(0.0))
        return OrderEarningsBreakdown(
            itemsSubtotal = round2(itemsSubtotal),
            vouchers = round2(vouchers),
            commissionRate = rateUsed,
            commissionFee = commissionFee,
            netEarnings = net
        )
    }
}
