package com.example.util

import android.annotation.SuppressLint
import com.example.data.model.DayHours
import com.example.data.model.MerchantSettings
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object BusinessHoursHelper {

    const val TIMESTAMP_2100_01_01_MS: Long = 4102444800000L

    val DAY_KEYS = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
    val DAY_NAMES = mapOf(
        "mon" to "Monday",
        "tue" to "Tuesday",
        "wed" to "Wednesday",
        "thu" to "Thursday",
        "fri" to "Friday",
        "sat" to "Saturday",
        "sun" to "Sunday"
    )
    val DAY_SHORT_NAMES = mapOf(
        "mon" to "Mon",
        "tue" to "Tue",
        "wed" to "Wed",
        "thu" to "Thu",
        "fri" to "Fri",
        "sat" to "Sat",
        "sun" to "Sun"
    )

    @SuppressLint("NewApi")
    fun dayOfWeekToKey(dayOfWeek: DayOfWeek): String {
        return when (dayOfWeek) {
            DayOfWeek.MONDAY -> "mon"
            DayOfWeek.TUESDAY -> "tue"
            DayOfWeek.WEDNESDAY -> "wed"
            DayOfWeek.THURSDAY -> "thu"
            DayOfWeek.FRIDAY -> "fri"
            DayOfWeek.SATURDAY -> "sat"
            DayOfWeek.SUNDAY -> "sun"
        }
    }

    @SuppressLint("NewApi")
    private fun resolveZoneId(timezoneId: String): ZoneId {
        val cleaned = timezoneId.trim().ifBlank { "Asia/Manila" }
        return try {
            ZoneId.of(cleaned)
        } catch (_: Exception) {
            ZoneId.of("Asia/Manila")
        }
    }

    private fun parseTimeToMinutes(timeStr: String): Int {
        val parts = timeStr.trim().split(":")
        if (parts.size >= 2) {
            val h = parts[0].toIntOrNull() ?: 0
            val m = parts[1].toIntOrNull() ?: 0
            return (h * 60 + m).coerceIn(0, 1439)
        }
        return 0
    }

    const val MANILA_TIMEZONE_ID: String = "Asia/Manila"

    /**
     * Canonical single source of truth for store open/closed status.
     * Priority order:
     * 1. Merchant suspended (status != "active"): closed.
     * 2. adminLock true: closed until admin removes it ("Closed by Delivio").
     * 3. manualOverride {state: "open" | "closed", until: Timestamp} while now < until: use its state.
     * 4. Auto-schedule on: open only inside today's hours in Asia/Manila (overnight windows supported),
     *    otherwise closed with the next opening time.
     * 5. Auto-schedule off: the manual switch (isOpen) decides.
     */
    fun storeStatus(
        merchant: MerchantSettings,
        now: Long = System.currentTimeMillis()
    ): com.example.data.model.StoreStatusResult {
        // 1. Merchant suspended
        if (!merchant.status.equals("active", ignoreCase = true)) {
            return com.example.data.model.StoreStatusResult(
                open = false,
                reason = "Suspended",
                nextChange = null
            )
        }

        // 2. Admin lock
        if (merchant.adminLock) {
            return com.example.data.model.StoreStatusResult(
                open = false,
                reason = "Closed by Delivio",
                nextChange = null
            )
        }

        // 3. Manual override (while now is before until)
        val mo = merchant.manualOverride
        if (mo != null && mo.until != null && now < mo.until) {
            when (mo.state.trim().lowercase()) {
                "open" -> return com.example.data.model.StoreStatusResult(
                    open = true,
                    reason = "Manual override",
                    nextChange = mo.until
                )
                "closed" -> return com.example.data.model.StoreStatusResult(
                    open = false,
                    reason = "Manual override",
                    nextChange = mo.until
                )
            }
        }

        // 4. Auto-schedule on (evaluated in Asia/Manila with overnight support)
        if (merchant.autoSchedule) {
            val holiday = getActiveHoliday(merchant, now)
            if (holiday != null) {
                return com.example.data.model.StoreStatusResult(
                    open = false,
                    reason = if (holiday.note.isNotBlank()) "Closed for ${holiday.note}" else "Closed for holiday",
                    nextChange = holiday.to
                )
            }
            val inside = if (merchant.hours.isNotEmpty()) {
                isInsideScheduleWindow(merchant.hours, MANILA_TIMEZONE_ID, now)
            } else {
                false
            }
            return if (inside) {
                com.example.data.model.StoreStatusResult(
                    open = true,
                    reason = "Open (Auto)",
                    nextChange = getNextClosingTimestamp(merchant, now, MANILA_TIMEZONE_ID)
                )
            } else {
                com.example.data.model.StoreStatusResult(
                    open = false,
                    reason = "Outside operating hours",
                    nextChange = getNextScheduledOpenTimestampOrNull(merchant, now, MANILA_TIMEZONE_ID)
                )
            }
        }

        // 5. Auto-schedule off: manual switch decides
        return com.example.data.model.StoreStatusResult(
            open = merchant.isOpen,
            reason = if (merchant.isOpen) "Open" else "Closed",
            nextChange = null
        )
    }

    fun getActiveHoliday(settings: MerchantSettings, nowMs: Long = System.currentTimeMillis()): com.example.data.model.HolidaySchedule? {
        return settings.holidays.firstOrNull { h ->
            h.from > 0L && h.to > 0L && nowMs in h.from..h.to
        }
    }

    fun isEffectiveOpen(
        settings: MerchantSettings,
        nowMs: Long = System.currentTimeMillis(),
        isServiceLocked: Boolean = false
    ): Boolean {
        if (isServiceLocked) {
            return false
        }
        return storeStatus(settings, nowMs).open
    }

    @SuppressLint("NewApi")
    fun isInsideScheduleWindow(
        hours: Map<String, DayHours>,
        timezoneId: String = MANILA_TIMEZONE_ID,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val zone = resolveZoneId(timezoneId)
        val zdt = Instant.ofEpochMilli(nowMs).atZone(zone)
        val currentDayKey = dayOfWeekToKey(zdt.dayOfWeek)
        val currentMinutes = zdt.hour * 60 + zdt.minute

        // 1. Check yesterday's overnight window
        val yesterdayKey = dayOfWeekToKey(zdt.minusDays(1).dayOfWeek)
        val yesterdayHours = hours[yesterdayKey]
        if (yesterdayHours != null && !yesterdayHours.closed && yesterdayHours.isOvernight) {
            val closeMinutes = parseTimeToMinutes(yesterdayHours.close)
            if (currentMinutes < closeMinutes) {
                return true
            }
        }

        // 2. Check today's window
        val todayHours = hours[currentDayKey] ?: return false
        if (todayHours.closed) return false
        if (todayHours.isOpen24Hours) return true

        val openMinutes = parseTimeToMinutes(todayHours.open)
        val closeMinutes = parseTimeToMinutes(todayHours.close)

        return if (todayHours.isOvernight) {
            // e.g. open 18:00, close 02:00 -> currentMinutes >= 18:00
            currentMinutes >= openMinutes
        } else {
            // normal day window e.g. 08:00 to 22:00
            currentMinutes in openMinutes until closeMinutes
        }
    }

    /**
     * Calculates the timestamp of the next scheduled opening time (in Manila or specified timezone),
     * or null if hours are empty / all days are closed.
     */
    @SuppressLint("NewApi")
    fun getNextScheduledOpenTimestampOrNull(
        settings: MerchantSettings,
        nowMs: Long = System.currentTimeMillis(),
        timezoneId: String = MANILA_TIMEZONE_ID
    ): Long? {
        val hours = settings.hours
        if (hours.isEmpty()) return null

        val zone = resolveZoneId(timezoneId)
        val zdt = Instant.ofEpochMilli(nowMs).atZone(zone)
        val currentDayKey = dayOfWeekToKey(zdt.dayOfWeek)
        val currentMinutes = zdt.hour * 60 + zdt.minute

        // Check today if not closed and open time is later today
        val todayHours = hours[currentDayKey]
        if (todayHours != null && !todayHours.closed) {
            val openMinutes = parseTimeToMinutes(todayHours.open)
            if (currentMinutes < openMinutes) {
                return zdt.toLocalDate()
                    .atTime(openMinutes / 60, openMinutes % 60, 0, 0)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
            }
        }

        // Check next 1..7 days
        val currentIdx = DAY_KEYS.indexOf(currentDayKey)
        for (i in 1..7) {
            val nextDayKey = DAY_KEYS[(currentIdx + i) % DAY_KEYS.size]
            val nextHours = hours[nextDayKey]
            if (nextHours != null && !nextHours.closed) {
                val openMinutes = parseTimeToMinutes(nextHours.open)
                return zdt.toLocalDate().plusDays(i.toLong())
                    .atTime(openMinutes / 60, openMinutes % 60, 0, 0)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
            }
        }

        return null
    }

    /**
     * Calculates the timestamp of the next scheduled opening time (in shop timezone).
     * If none found or hours empty, returns TIMESTAMP_2100_01_01_MS.
     */
    fun getNextScheduledOpenTimestamp(
        settings: MerchantSettings,
        nowMs: Long = System.currentTimeMillis(),
        timezoneId: String = MANILA_TIMEZONE_ID
    ): Long {
        return getNextScheduledOpenTimestampOrNull(settings, nowMs, timezoneId) ?: TIMESTAMP_2100_01_01_MS
    }

    /**
     * Calculates the timestamp of the next scheduled closing time (handling yesterday's overnight shift,
     * today's normal/overnight shift, and upcoming days).
     */
    @SuppressLint("NewApi")
    fun getNextClosingTimestamp(
        settings: MerchantSettings,
        nowMs: Long = System.currentTimeMillis(),
        timezoneId: String = MANILA_TIMEZONE_ID
    ): Long? {
        val hours = settings.hours
        if (hours.isEmpty()) return null

        val zone = resolveZoneId(timezoneId)
        val zdt = Instant.ofEpochMilli(nowMs).atZone(zone)
        val currentDayKey = dayOfWeekToKey(zdt.dayOfWeek)
        val currentMinutes = zdt.hour * 60 + zdt.minute

        // 1. Inside yesterday's overnight closing window -> closes today at yesterdayHours.close
        val yesterdayKey = dayOfWeekToKey(zdt.minusDays(1).dayOfWeek)
        val yesterdayHours = hours[yesterdayKey]
        if (yesterdayHours != null && !yesterdayHours.closed && yesterdayHours.isOvernight) {
            val closeMinutes = parseTimeToMinutes(yesterdayHours.close)
            if (currentMinutes < closeMinutes) {
                val targetMs = zdt.toLocalDate()
                    .atTime(closeMinutes / 60, closeMinutes % 60, 0, 0)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
                if (targetMs > nowMs) return targetMs
            }
        }

        // 2. Check today's hours
        val todayHours = hours[currentDayKey]
        if (todayHours != null && !todayHours.closed) {
            if (todayHours.isOpen24Hours) {
                val endTodayMs = zdt.toLocalDate()
                    .atTime(23, 59, 59, 0)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
                return if (endTodayMs > nowMs) endTodayMs else {
                    zdt.toLocalDate().plusDays(1)
                        .atTime(23, 59, 59, 0)
                        .atZone(zone)
                        .toInstant()
                        .toEpochMilli()
                }
            }
            val closeMinutes = parseTimeToMinutes(todayHours.close)
            if (todayHours.isOvernight) {
                // Closes tomorrow early morning
                val targetMs = zdt.toLocalDate().plusDays(1)
                    .atTime(closeMinutes / 60, closeMinutes % 60, 0, 0)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
                if (targetMs > nowMs) return targetMs
            } else {
                val targetMs = zdt.toLocalDate()
                    .atTime(closeMinutes / 60, closeMinutes % 60, 0, 0)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
                if (targetMs > nowMs) {
                    return targetMs
                }
            }
        }

        // 3. Look ahead 1..7 days for the next open day's closing time
        val currentIdx = DAY_KEYS.indexOf(currentDayKey)
        for (i in 1..7) {
            val nextDayKey = DAY_KEYS[(currentIdx + i) % DAY_KEYS.size]
            val nextHours = hours[nextDayKey]
            if (nextHours != null && !nextHours.closed) {
                if (nextHours.isOpen24Hours) {
                    return zdt.toLocalDate().plusDays(i.toLong())
                        .atTime(23, 59, 59, 0)
                        .atZone(zone)
                        .toInstant()
                        .toEpochMilli()
                }
                val closeMinutes = parseTimeToMinutes(nextHours.close)
                val closeDateOffset = if (nextHours.isOvernight) i.toLong() + 1L else i.toLong()
                return zdt.toLocalDate().plusDays(closeDateOffset)
                    .atTime(closeMinutes / 60, closeMinutes % 60, 0, 0)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
            }
        }
        return null
    }

    /**
     * Always returns a concrete future timestamp for Switch ON manualOverride.until
     * (falls back to end of current Manila day if no hours are configured).
     */
    @SuppressLint("NewApi")
    fun getNextClosingTimestampForOverride(
        settings: MerchantSettings,
        nowMs: Long = System.currentTimeMillis(),
        timezoneId: String = MANILA_TIMEZONE_ID
    ): Long {
        getNextClosingTimestamp(settings, nowMs, timezoneId)?.let { return it }
        val zone = resolveZoneId(timezoneId)
        val zdt = Instant.ofEpochMilli(nowMs).atZone(zone)
        val endTodayMs = zdt.toLocalDate()
            .atTime(23, 59, 59, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        return if (endTodayMs > nowMs) {
            endTodayMs
        } else {
            zdt.toLocalDate().plusDays(1)
                .atTime(23, 59, 59, 0)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        }
    }

    /**
     * Formats closedUntil / nextChange time for display in banners.
     * Returns null if timestamp is 2100-01-01 or not set.
     */
    @SuppressLint("NewApi")
    fun formatTimestampShort(
        timestampMs: Long?,
        timezoneId: String = MANILA_TIMEZONE_ID,
        nowMs: Long = System.currentTimeMillis()
    ): String? {
        if (timestampMs == null || timestampMs >= TIMESTAMP_2100_01_01_MS - 86400000L) {
            return null
        }
        val zone = resolveZoneId(timezoneId)
        val targetZdt = Instant.ofEpochMilli(timestampMs).atZone(zone)
        val nowZdt = Instant.ofEpochMilli(nowMs).atZone(zone)

        val timeStr = "%02d:%02d".format(targetZdt.hour, targetZdt.minute)
        return if (targetZdt.toLocalDate() == nowZdt.toLocalDate()) {
            timeStr
        } else {
            val dayKey = dayOfWeekToKey(targetZdt.dayOfWeek)
            val dayShort = DAY_SHORT_NAMES[dayKey] ?: ""
            "$dayShort $timeStr"
        }
    }

    fun formatClosedUntilTime(
        settings: MerchantSettings,
        nowMs: Long = System.currentTimeMillis()
    ): String? {
        val status = storeStatus(settings, nowMs)
        val untilMs = if (!status.open) {
            status.nextChange ?: settings.closedUntil
        } else {
            settings.closedUntil
        }
        return formatTimestampShort(untilMs, MANILA_TIMEZONE_ID, nowMs)
    }

    /**
     * Returns a human readable string describing the next scheduled state change.
     * e.g., "Opens Mon 08:00" or "Closes 22:00"
     */
    @SuppressLint("NewApi")
    fun getNextScheduleChangeDescription(
        settings: MerchantSettings,
        nowMs: Long = System.currentTimeMillis()
    ): String {
        val hours = settings.hours
        if (hours.isEmpty()) return ""
        val zone = resolveZoneId(MANILA_TIMEZONE_ID)
        val zdt = Instant.ofEpochMilli(nowMs).atZone(zone)
        val isOpenNow = isInsideScheduleWindow(hours, MANILA_TIMEZONE_ID, nowMs)
        val currentDayKey = dayOfWeekToKey(zdt.dayOfWeek)
        val currentMinutes = zdt.hour * 60 + zdt.minute

        val holiday = getActiveHoliday(settings, nowMs)
        if (holiday != null) {
            val holidayZdt = Instant.ofEpochMilli(holiday.to).atZone(zone)
            val toDateStr = holidayZdt.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))
            val notePart = if (holiday.note.isNotBlank()) " for ${holiday.note}" else ""
            return "Closed$notePart until $toDateStr"
        }

        if (isOpenNow) {
            // Check when it closes
            val todayHours = hours[currentDayKey]
            if (todayHours != null && !todayHours.closed) {
                if (todayHours.isOpen24Hours) return "Open 24 hours"
                if (todayHours.isOvernight) {
                    val nextDayIdx = (DAY_KEYS.indexOf(currentDayKey) + 1) % DAY_KEYS.size
                    val nextDayShort = DAY_SHORT_NAMES[DAY_KEYS[nextDayIdx]] ?: ""
                    return "Closes $nextDayShort ${todayHours.close}"
                }
                return "Closes ${todayHours.close}"
            }
            // Or yesterday's overnight closing
            val yKey = dayOfWeekToKey(zdt.minusDays(1).dayOfWeek)
            val yHours = hours[yKey]
            if (yHours != null && !yHours.closed && yHours.isOvernight) {
                return "Closes ${yHours.close}"
            }
            return "Closes soon"
        } else {
            // Closed: check when it opens next
            val todayHours = hours[currentDayKey]
            if (todayHours != null && !todayHours.closed) {
                val openMinutes = parseTimeToMinutes(todayHours.open)
                if (currentMinutes < openMinutes) {
                    return "Opens ${todayHours.open}"
                }
            }

            // Check following days
            val currentIdx = DAY_KEYS.indexOf(currentDayKey)
            for (i in 1..7) {
                val nextDayKey = DAY_KEYS[(currentIdx + i) % DAY_KEYS.size]
                val nextHours = hours[nextDayKey]
                if (nextHours != null && !nextHours.closed) {
                    val dayShort = DAY_SHORT_NAMES[nextDayKey] ?: ""
                    return "Opens $dayShort ${nextHours.open}"
                }
            }
            return "Closed"
        }
    }
}
