package com.example.util

import com.example.data.model.DayHours
import com.example.data.model.MerchantSettings
import java.util.Calendar
import java.util.TimeZone

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

    private fun calendarDayToKey(calDay: Int): String {
        return when (calDay) {
            Calendar.MONDAY -> "mon"
            Calendar.TUESDAY -> "tue"
            Calendar.WEDNESDAY -> "wed"
            Calendar.THURSDAY -> "thu"
            Calendar.FRIDAY -> "fri"
            Calendar.SATURDAY -> "sat"
            Calendar.SUNDAY -> "sun"
            else -> "mon"
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

    fun isInsideScheduleWindow(
        hours: Map<String, DayHours>,
        timezoneId: String = "Asia/Manila",
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val tz = try {
            TimeZone.getTimeZone(timezoneId.ifBlank { "Asia/Manila" })
        } catch (_: Exception) {
            TimeZone.getTimeZone("Asia/Manila")
        }
        val cal = Calendar.getInstance(tz).apply { timeInMillis = nowMs }
        val currentDayKey = calendarDayToKey(cal.get(Calendar.DAY_OF_WEEK))
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        // 1. Check yesterday's overnight window
        val yesterdayCal = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterdayKey = calendarDayToKey(yesterdayCal.get(Calendar.DAY_OF_WEEK))
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
    fun getNextScheduledOpenTimestamp(
        settings: MerchantSettings,
        nowMs: Long = System.currentTimeMillis()
    ): Long {
        val hours = settings.hours
        if (hours.isEmpty()) return TIMESTAMP_2100_01_01_MS

        val tz = try {
            TimeZone.getTimeZone(settings.timezone.ifBlank { "Asia/Manila" })
        } catch (_: Exception) {
            TimeZone.getTimeZone("Asia/Manila")
        }
        val cal = Calendar.getInstance(tz).apply { timeInMillis = nowMs }
        val currentDayKey = calendarDayToKey(cal.get(Calendar.DAY_OF_WEEK))
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        // Check today if not closed and open time is later today
        val todayHours = hours[currentDayKey]
        if (todayHours != null && !todayHours.closed) {
            val openMinutes = parseTimeToMinutes(todayHours.open)
            if (currentMinutes < openMinutes) {
                return (cal.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, openMinutes / 60)
                    set(Calendar.MINUTE, openMinutes % 60)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
        }

        // Check next 1..7 days
        val currentIdx = DAY_KEYS.indexOf(currentDayKey)
        for (i in 1..7) {
            val nextDayKey = DAY_KEYS[(currentIdx + i) % DAY_KEYS.size]
            val nextHours = hours[nextDayKey]
            if (nextHours != null && !nextHours.closed) {
                val openMinutes = parseTimeToMinutes(nextHours.open)
                val targetCal = (cal.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, i)
                    set(Calendar.HOUR_OF_DAY, openMinutes / 60)
                    set(Calendar.MINUTE, openMinutes % 60)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                return targetCal.timeInMillis
            }
        }

        return TIMESTAMP_2100_01_01_MS
    }

    /**
     * Calculates the timestamp of the next scheduled closing time when opening store
     * while currently outside schedule hours.
     */
    fun getNextClosingTimestamp(
        settings: MerchantSettings,
        nowMs: Long = System.currentTimeMillis()
    ): Long? {
        val hours = settings.hours
        if (hours.isEmpty()) return null

        val tz = try {
            TimeZone.getTimeZone(settings.timezone.ifBlank { "Asia/Manila" })
        } catch (_: Exception) {
            TimeZone.getTimeZone("Asia/Manila")
        }
        val cal = Calendar.getInstance(tz).apply { timeInMillis = nowMs }
        val currentDayKey = calendarDayToKey(cal.get(Calendar.DAY_OF_WEEK))
        val todayHours = hours[currentDayKey]

        if (todayHours != null && !todayHours.closed) {
            val closeMinutes = parseTimeToMinutes(todayHours.close)
            if (todayHours.isOvernight) {
                // Closes tomorrow early morning
                val targetCal = (cal.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, closeMinutes / 60)
                    set(Calendar.MINUTE, closeMinutes % 60)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                return targetCal.timeInMillis
            } else {
                val targetCal = (cal.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, closeMinutes / 60)
                    set(Calendar.MINUTE, closeMinutes % 60)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (targetCal.timeInMillis > nowMs) {
                    return targetCal.timeInMillis
                }
            }
        }
        return null
    }

    /**
     * Formats closedUntil time for display in the red banner.
     * Returns null if closedUntil is 2100-01-01 or not set.
     */
    fun formatClosedUntilTime(
        settings: MerchantSettings,
        nowMs: Long = System.currentTimeMillis()
    ): String? {
        val closedUntil = settings.closedUntil ?: return null
        if (closedUntil >= TIMESTAMP_2100_01_01_MS - 86400000L) {
            return null
        }
        val tz = try {
            TimeZone.getTimeZone(settings.timezone.ifBlank { "Asia/Manila" })
        } catch (_: Exception) {
            TimeZone.getTimeZone("Asia/Manila")
        }
        val cal = Calendar.getInstance(tz).apply { timeInMillis = closedUntil }
        val nowCal = Calendar.getInstance(tz).apply { timeInMillis = nowMs }

        val timeStr = "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        return if (cal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR) &&
            cal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)
        ) {
            timeStr
        } else {
            val dayKey = calendarDayToKey(cal.get(Calendar.DAY_OF_WEEK))
            val dayShort = DAY_SHORT_NAMES[dayKey] ?: ""
            "$dayShort $timeStr"
        }
    }

    /**
     * Returns a human readable string describing the next scheduled state change.
     * e.g., "Opens Mon 08:00" or "Closes 22:00"
     */
    fun getNextScheduleChangeDescription(
        settings: MerchantSettings,
        nowMs: Long = System.currentTimeMillis()
    ): String {
        val hours = settings.hours
        if (hours.isEmpty()) return ""
        val tz = try {
            TimeZone.getTimeZone(settings.timezone.ifBlank { "Asia/Manila" })
        } catch (_: Exception) {
            TimeZone.getTimeZone("Asia/Manila")
        }
        val cal = Calendar.getInstance(tz).apply { timeInMillis = nowMs }
        val isOpenNow = isInsideScheduleWindow(hours, settings.timezone, nowMs)
        val currentDayKey = calendarDayToKey(cal.get(Calendar.DAY_OF_WEEK))
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

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
            val yesterdayCal = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
            val yKey = calendarDayToKey(yesterdayCal.get(Calendar.DAY_OF_WEEK))
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
