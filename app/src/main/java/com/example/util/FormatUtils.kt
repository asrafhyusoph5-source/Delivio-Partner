package com.example.util

import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object FormatUtils {
    fun formatCurrency(amount: Double): String {
        return "${CurrencyConstants.SYMBOL}%.2f".format(Locale.US, amount)
    }

    fun formatTimeAgo(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
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

    fun formatRemainingTime(endTime: Long): String {
        val remaining = endTime - System.currentTimeMillis()
        if (remaining <= 0) return "Ready!"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    fun parseDouble(value: Any?): Double? {
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().toDoubleOrNull()
            else -> null
        }
    }

    fun parseInt(value: Any?): Int? {
        return when (value) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
            else -> null
        }
    }
}
