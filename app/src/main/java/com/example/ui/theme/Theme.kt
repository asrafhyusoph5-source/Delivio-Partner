package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class DelivioCustomColors(
    val accent: Color,
    val onAccent: Color,
    val accentTint: Color,
    val openGreen: Color = StatusOpenGreen,
    val closedRed: Color = StatusClosedRed
)

val LocalDelivioColors = staticCompositionLocalOf {
    DelivioCustomColors(
        accent = DelivioYellow,
        onAccent = Color(0xFF1A1A1A),
        accentTint = Color(0xFFFFF8E1)
    )
}

val DelivioShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun DelivioPartnerTheme(
    themeAccentHex: String = "#FFC107",
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val accentColor = parseHexColor(themeAccentHex, DelivioYellow)
    val onAccentColor = getContrastTextColor(accentColor)
    val accentTint = accentColor.copy(alpha = 0.15f)

    val colorScheme = lightColorScheme(
        primary = accentColor,
        onPrimary = onAccentColor,
        primaryContainer = accentTint,
        onPrimaryContainer = if (accentColor == DelivioYellow) Color(0xFF785400) else accentColor,
        secondary = accentColor,
        onSecondary = onAccentColor,
        background = LightBackground,
        onBackground = Color(0xFF1F2937),
        surface = LightSurface,
        onSurface = Color(0xFF111827),
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = Color(0xFF4B5563),
        outline = LightOutline,
        error = StatusClosedRed,
        onError = Color.White
    )

    val customColors = DelivioCustomColors(
        accent = accentColor,
        onAccent = onAccentColor,
        accentTint = accentTint
    )

    CompositionLocalProvider(LocalDelivioColors provides customColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = DelivioShapes,
            content = content
        )
    }
}
