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

    /**
     * effectiveOpen(shop):
     * - false when status != "active"
     * - false when closedUntil is set and now < closedUntil
     * - false when isPaused and now < pauseUntilTime
     * - true when openUntil is set and now < openUntil
     * - else when autoSchedule is true and hours exist:
     *     true only inside today's window (settings/platform.timezone, default "Asia/Manila"; overnight windows check yesterday)
     * - else isOpen
     */
    fun getActiveHoliday(settings: MerchantSettings, nowMs: Long = System.currentTimeMillis()): com.example.data.model.HolidaySchedule? {
        return settings.holidays.firstOrNull { h ->
            h.from > 0L && h.to > 0L && nowMs in h.from..h.to
        }
    }

    /**
     * effectiveOpen(shop):
     * - false when status != "active"
     * - false when within any holiday date range (settings.holidays)
     * - false when closedUntil is set and now < closedUntil
     * - false when isPaused and now < pauseUntilTime
     * - true when openUntil is set and now < openUntil
     * - else when autoSchedule is true and hours exist:
     *     true only inside today's window (settings/platform.timezone, default "Asia/Manila"; overnight windows check yesterday)
     * - else isOpen
     */
    fun isEffectiveOpen(
        settings: MerchantSettings,
        nowMs: Long = System.currentTimeMillis(),
        isServiceLocked: Boolean = false
    ): Boolean {
        if (isServiceLocked) {
            return false
        }
        if (!settings.status.equals("active", ignoreCase = true)) {
            return false
        }

        // Check if currently closed for a holiday
        val holiday = getActiveHoliday(settings, nowMs)
        if (holiday != null) {
            return false
        }

        // false when closedUntil is set and now < closedUntil
        val closedUntil = settings.closedUntil
        if (closedUntil != null && nowMs < closedUntil) {
            return false
        }

        // false when isPaused and now < pauseUntilTime
        if (settings.isPaused) {
            val pauseUntil = settings.pauseUntilTimestamp
            if (pauseUntil != null && nowMs < pauseUntil) {
                return false
            }
        }

        // true when openUntil is set and now < openUntil
        val openUntil = settings.openUntil
        if (openUntil != null && nowMs < openUntil) {
            return true
        }

        // else when autoSchedule is true and hours exist, true only inside today's window
        if (settings.autoSchedule && settings.hours.isNotEmpty()) {
            return isInsideScheduleWindow(settings.hours, settings.timezone, nowMs)
        }

        // else isOpen
        return settings.isOpen
    }

    @SuppressLint("NewApi")
    fun isInsideScheduleWindow(
        hours: Map<String, DayHours>,
        timezoneId: String = "Asia/Manila",
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
     * Calculates the timestamp of the next scheduled opening time (in shop timezone)
     * when autoSchedule is on. If none found or hours empty, returns TIMESTAMP_2100_01_01_MS.
     */
    @SuppressLint("NewApi")
    fun getNextScheduledOpenTimestamp(
        settings: MerchantSettings,
        nowMs: Long = System.currentTimeMillis()
    ): Long {
        val hours = settings.hours
        if (hours.isEmpty()) return TIMESTAMP_2100_01_01_MS

        val zone = resolveZoneId(settings.timezone)
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

        return TIMESTAMP_2100_01_01_MS
    }

    /**
     * Calculates the timestamp of the next scheduled closing time when opening store
     * while currently outside schedule hours.
     */
    @SuppressLint("NewApi")
    fun getNextClosingTimestamp(
        settings: MerchantSettings,
        nowMs: Long = System.currentTimeMillis()
    ): Long? {
        val hours = settings.hours
        if (hours.isEmpty()) return null

        val zone = resolveZoneId(settings.timezone)
        val zdt = Instant.ofEpochMilli(nowMs).atZone(zone)
        val currentDayKey = dayOfWeekToKey(zdt.dayOfWeek)
        val todayHours = hours[currentDayKey]

        if (todayHours != null && !todayHours.closed) {
            val closeMinutes = parseTimeToMinutes(todayHours.close)
            if (todayHours.isOvernight) {
                // Closes tomorrow early morning
                return zdt.toLocalDate().plusDays(1)
                    .atTime(closeMinutes / 60, closeMinutes % 60, 0, 0)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
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
        return null
    }

    /**
     * Formats closedUntil time for display in the red banner.
     * Returns null if closedUntil is 2100-01-01 or not set.
     */
    @SuppressLint("NewApi")
    fun formatClosedUntilTime(
        settings: MerchantSettings,
        nowMs: Long = System.currentTimeMillis()
    ): String? {
        val closedUntil = settings.closedUntil ?: return null
        if (closedUntil >= TIMESTAMP_2100_01_01_MS - 86400000L) {
            return null
        }
        val zone = resolveZoneId(settings.timezone)
        val closedZdt = Instant.ofEpochMilli(closedUntil).atZone(zone)
        val nowZdt = Instant.ofEpochMilli(nowMs).atZone(zone)

        val timeStr = "%02d:%02d".format(closedZdt.hour, closedZdt.minute)
        return if (closedZdt.toLocalDate() == nowZdt.toLocalDate()) {
            timeStr
        } else {
            val dayKey = dayOfWeekToKey(closedZdt.dayOfWeek)
            val dayShort = DAY_SHORT_NAMES[dayKey] ?: ""
            "$dayShort $timeStr"
        }
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
        val zone = resolveZoneId(settings.timezone)
        val zdt = Instant.ofEpochMilli(nowMs).atZone(zone)
        val isOpenNow = isInsideScheduleWindow(hours, settings.timezone, nowMs)
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
