package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object NotificationHelper {

    const val LOW_STOCK_CHANNEL_ID = "low_stock_channel"
    const val LOW_STOCK_CHANNEL_NAME = "Low Stock Alerts"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = NotificationChannel(
                LOW_STOCK_CHANNEL_ID,
                LOW_STOCK_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when inventory for a product reaches or drops below threshold"
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun showLowStockNotification(
        context: Context,
        productName: String,
        remainingStock: Int,
        notificationId: Int = productName.hashCode()
    ) {
        createChannels(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val title = "Low Stock Alert: $productName"
        val message = if (remainingStock <= 0) {
            "\"$productName\" is out of stock and marked Sold Out."
        } else {
            "Only $remainingStock left of \"$productName\"."
        }

        val builder = NotificationCompat.Builder(context, LOW_STOCK_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Permission not granted
        }
    }
}
