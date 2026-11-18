package com.example.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.example.data.model.AppLinksSettings

/**
 * Helper to open the Delivio POS web URL cleanly in a Chrome Custom Tab (falling back to the default browser)
 * and remember the one-time "Don't show again" preference for the sign-in notice sheet.
 *
 * Security rule: Never appends passwords, credentials, or auth tokens to the URL.
 */
object PosBrowserLauncher {
    private const val PREFS_NAME = "delivio_partner_pos_prefs"
    private const val KEY_SKIP_POS_NOTICE = "skip_pos_launch_notice"

    fun shouldSkipLaunchSheet(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SKIP_POS_NOTICE, false)
    }

    fun setSkipLaunchSheet(context: Context, skip: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SKIP_POS_NOTICE, skip)
            .apply()
    }

    fun openPosUrl(context: Context, rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        if (!AppLinksSettings.isValidHttpsUrl(trimmed)) return false
        val uri = Uri.parse(trimmed)

        return try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            if (context !is Activity) {
                customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            customTabsIntent.launchUrl(context, uri)
            true
        } catch (_: Throwable) {
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(fallbackIntent)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }
}
