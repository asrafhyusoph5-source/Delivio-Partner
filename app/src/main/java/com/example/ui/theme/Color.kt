package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

data class ColorPreset(
    val name: String,
    val color: Color,
    val hex: String
)

// Preset swatches for Delivio Merchant
val DelivioYellow = Color(0xFFFFC107)
val DelivioOrange = Color(0xFFFF9800)
val DelivioRed = Color(0xFFE53935)
val DelivioGreen = Color(0xFF4CAF50)
val DelivioBlue = Color(0xFF2196F3)
val DelivioPurple = Color(0xFF9C27B0)
val DelivioPink = Color(0xFFE91E63)
val DelivioTeal = Color(0xFF009688)

val PresetColors = listOf(
    ColorPreset("Yellow", DelivioYellow, "#FFC107"),
    ColorPreset("Orange", DelivioOrange, "#FF9800"),
    ColorPreset("Red", DelivioRed, "#E53935"),
    ColorPreset("Green", DelivioGreen, "#4CAF50"),
    ColorPreset("Blue", DelivioBlue, "#2196F3"),
    ColorPreset("Purple", DelivioPurple, "#9C27B0"),
    ColorPreset("Pink", DelivioPink, "#E91E63"),
    ColorPreset("Teal", DelivioTeal, "#009688")
)

// Dark mode base colors
val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val DarkSurfaceVariant = Color(0xFF282828)
val DarkOutline = Color(0xFF383838)

// Light mode base colors
val LightBackground = Color(0xFFF8F9FA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF1F3F5)
val LightOutline = Color(0xFFE5E7EB)

// Status colors
val StatusOpenGreen = Color(0xFF10B981)
val StatusClosedRed = Color(0xFFEF4444)
val StatusWarningAmber = Color(0xFFF59E0B)
val StatusInfoBlue = Color(0xFF3B82F6)

fun parseHexColor(hexString: String, default: Color = DelivioYellow): Color {
    return try {
        val clean = hexString.trim().removePrefix("#")
        val hexWithHash = if (clean.length == 6 || clean.length == 8) "#$clean" else return default
        Color(android.graphics.Color.parseColor(hexWithHash))
    } catch (_: Exception) {
        default
    }
}

fun getContrastTextColor(color: Color): Color {
    return if (color.luminance() > 0.45f) {
        Color(0xFF1A1A1A)
    } else {
        Color(0xFFFFFFFF)
    }
}
