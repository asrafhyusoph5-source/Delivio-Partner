package com.example.util

import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object FormatUtils {
    fun formatCurrency(amount: Double): String {
        return "${CurrencyConstants.SYMBOL}%,.2f".format(Locale.US, amount)
    }

    fun formatTimeAgo(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes} min ago"
            hours < 24 -> "${hours} hr ago"
            else -> {
                val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }

    fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Manila")
        }
        return sdf.format(Date(timestamp))
    }

    fun formatScheduledDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatScheduledTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Manila")
        }
        return sdf.format(Date(timestamp))
    }

    fun formatRemainingTime(endTime: Long): String {
        val remaining = endTime - System.currentTimeMillis()
        if (remaining <= 0) return "Ready!"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    fun parseDouble(value: Any?): Double? {
        return when (value) {
            is Number -> value.toDouble().takeIf { it.isFinite() }
            is String -> value.trim().replace(",", "").toDoubleOrNull()?.takeIf { it.isFinite() }
            else -> null
        }
    }

    fun parseInt(value: Any?): Int? {
        return when (value) {
            is Number -> value.toDouble().takeIf { it.isFinite() }?.toInt()
            is String -> {
                val cleaned = value.trim().replace(",", "")
                cleaned.toIntOrNull() ?: cleaned.toDoubleOrNull()?.takeIf { it.isFinite() }?.toInt()
            }
            else -> null
        }
    }

    fun formatDateOnly(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Manila")
        }
        return sdf.format(Date(timestamp))
    }

    fun parseTimestamp(value: Any?): Long? {
        return when (value) {
            is com.google.firebase.Timestamp -> value.toDate().time
            is Date -> value.time
            is Number -> value.toDouble().takeIf { it.isFinite() }?.toLong()
            is Map<*, *> -> {
                val sec = parseDouble(value["seconds"] ?: value["_seconds"]) ?: return null
                val nano = parseDouble(value["nanoseconds"] ?: value["_nanoseconds"]) ?: 0.0
                (sec * 1000.0 + nano / 1_000_000.0).toLong()
            }
            is String -> {
                val trimmed = value.trim()
                if (trimmed.isEmpty()) return null
                trimmed.toLongOrNull()
                    ?: trimmed.toDoubleOrNull()?.takeIf { it.isFinite() }?.toLong()
                    ?: run {
                        val patterns = listOf(
                            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                            "yyyy-MM-dd'T'HH:mm:ss'Z'",
                            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                            "yyyy-MM-dd'T'HH:mm:ssZ",
                            "yyyy-MM-dd HH:mm:ss"
                        )
                        for (pattern in patterns) {
                            try {
                                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                                    timeZone = TimeZone.getTimeZone("UTC")
                                }
                                val parsed = sdf.parse(trimmed)
                                if (parsed != null) return@run parsed.time
                            } catch (_: Exception) {
                            }
                        }
                        null
                    }
            }
            else -> null
        }
    }
}
