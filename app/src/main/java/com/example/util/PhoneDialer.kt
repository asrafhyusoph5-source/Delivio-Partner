package com.example.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri

object PhoneDialer {
    fun dial(context: Context, phoneNumber: String) {
        val cleaned = phoneNumber.trim().replace(Regex("[^0-9+#*]"), "")
        if (cleaned.isBlank()) return
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleaned")).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }
}
