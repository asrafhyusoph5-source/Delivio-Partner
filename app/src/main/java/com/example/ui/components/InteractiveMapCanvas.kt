package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DelivioYellow
import com.example.ui.theme.LocalDelivioColors
import com.example.ui.theme.StatusClosedRed
import com.example.ui.theme.StatusOpenGreen

@Composable
fun InteractiveMapCanvas(
    modifier: Modifier = Modifier,
    isDraggablePin: Boolean = false,
    initialPinNormalized: Offset = Offset(0.5f, 0.5f),
    onPinPositionChanged: ((lat: Double, lng: Double, normalizedOffset: Offset) -> Unit)? = null,
    riderProgress: Float? = null, // 0f to 1f for rider tracking
    merchantLabel: String = "Store Pickup",
    customerLabel: String = "Customer Drop-off"
) {
    var pinPos by remember { mutableStateOf(initialPinNormalized) }
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    val customColors = LocalDelivioColors.current

    // Base coordinates for simulation around BGC Metro Manila
    val baseLat = 14.5492
    val baseLng = 121.0503

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(isDraggablePin) {
                if (isDraggablePin) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val newX = ((pinPos.x * size.width + dragAmount.x) / size.width).coerceIn(0.1f, 0.9f)
                        val newY = ((pinPos.y * size.height + dragAmount.y) / size.height).coerceIn(0.1f, 0.9f)
                        pinPos = Offset(newX, newY)
                        val simLat = baseLat + (0.5 - newY) * 0.04
                        val simLng = baseLng + (newX - 0.5) * 0.04
                        onPinPositionChanged?.invoke(simLat, simLng, pinPos)
                    }
                }
            }
            .pointerInput(isDraggablePin) {
                if (isDraggablePin) {
                    detectTapGestures { tapOffset ->
                        val newX = (tapOffset.x / size.width).coerceIn(0.1f, 0.9f)
                        val newY = (tapOffset.y / size.height).coerceIn(0.1f, 0.9f)
                        pinPos = Offset(newX, newY)
                        val simLat = baseLat + (0.5 - newY) * 0.04
                        val simLng = baseLng + (newX - 0.5) * 0.04
                        onPinPositionChanged?.invoke(simLat, simLng, pinPos)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Background land
            val mapBgColor = if (isDark) Color(0xFF1E242B) else Color(0xFFE8ECEF)
            drawRect(color = mapBgColor)

            // Parks & Greenery zones
            val greenColor = if (isDark) Color(0xFF1C2B24) else Color(0xFFD4E9D7)
            drawRoundRect(
                color = greenColor,
                topLeft = Offset(width * 0.08f, height * 0.12f),
                size = androidx.compose.ui.geometry.Size(width * 0.28f, height * 0.26f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f)
            )
            drawRoundRect(
                color = greenColor,
                topLeft = Offset(width * 0.65f, height * 0.55f),
                size = androidx.compose.ui.geometry.Size(width * 0.28f, height * 0.35f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(20f, 20f)
            )

            // Water River line
            val riverColor = if (isDark) Color(0xFF1B2F3D) else Color(0xFFBCE0FD)
            val riverPath = Path().apply {
                moveTo(0f, height * 0.75f)
                cubicTo(
                    width * 0.3f, height * 0.85f,
                    width * 0.6f, height * 0.65f,
                    width, height * 0.7f
                )
            }
            drawPath(
                path = riverPath,
                color = riverColor,
                style = Stroke(width = 24f, cap = StrokeCap.Round)
            )

            // City Road Grid
            val secondaryRoadColor = if (isDark) Color(0xFF2A3440) else Color(0xFFFBFBFB)
            val majorRoadColor = if (isDark) Color(0xFF384656) else Color(0xFFFFFFFF)

            // Horizontal roads
            listOf(0.2f, 0.38f, 0.55f, 0.72f, 0.88f).forEach { fraction ->
                drawLine(
                    color = secondaryRoadColor,
                    start = Offset(0f, height * fraction),
                    end = Offset(width, height * fraction),
                    strokeWidth = 10f
                )
            }

            // Vertical roads
            listOf(0.18f, 0.38f, 0.62f, 0.82f).forEach { fraction ->
                drawLine(
                    color = secondaryRoadColor,
                    start = Offset(width * fraction, 0f),
                    end = Offset(width * fraction, height),
                    strokeWidth = 10f
                )
            }

            // Main Diagonal Avenue
            val mainRoad = Path().apply {
                moveTo(width * 0.1f, height * 0.9f)
                lineTo(width * 0.85f, height * 0.15f)
            }
            drawPath(
                path = mainRoad,
                color = majorRoadColor,
                style = Stroke(width = 18f, cap = StrokeCap.Round)
            )

            // Route Polyline (Merchant to Customer) if not in pure pin drag mode
            val startPoint = Offset(width * 0.25f, height * 0.7f)
            val endPoint = Offset(width * 0.75f, height * 0.28f)
            val midPoint1 = Offset(width * 0.45f, height * 0.7f)
            val midPoint2 = Offset(width * 0.45f, height * 0.28f)

            if (riderProgress != null || !isDraggablePin) {
                val routePath = Path().apply {
                    moveTo(startPoint.x, startPoint.y)
                    lineTo(midPoint1.x, midPoint1.y)
                    lineTo(midPoint2.x, midPoint2.y)
                    lineTo(endPoint.x, endPoint.y)
                }

                // Route shadow & active path
                drawPath(
                    path = routePath,
                    color = customColors.accent.copy(alpha = 0.4f),
                    style = Stroke(width = 12f, cap = StrokeCap.Round)
                )
                drawPath(
                    path = routePath,
                    color = customColors.accent,
                    style = Stroke(
                        width = 6f,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 12f), 0f)
                    )
                )

                // Merchant Store Pin
                drawStorePin(startPoint, customColors.accent)

                // Customer Dropoff Pin
                drawCustomerPin(endPoint, StatusClosedRed)

                // Rider Motorcycle Marker along route
                if (riderProgress != null) {
                    val progress = riderProgress.coerceIn(0f, 1f)
                    val riderCurrentPos = when {
                        progress < 0.33f -> {
                            val subProg = progress / 0.33f
                            lerp(startPoint, midPoint1, subProg)
                        }
                        progress < 0.66f -> {
                            val subProg = (progress - 0.33f) / 0.33f
                            lerp(midPoint1, midPoint2, subProg)
                        }
                        else -> {
                            val subProg = (progress - 0.66f) / 0.34f
                            lerp(midPoint2, endPoint, subProg)
                        }
                    }

                    drawRiderMarker(riderCurrentPos, isDark)
                }
            }

            // Draggable Pin for Setting Location
            if (isDraggablePin) {
                val targetPinPx = Offset(width * pinPos.x, height * pinPos.y)

                // Pulse ring under pin
                drawCircle(
                    color = customColors.accent.copy(alpha = 0.25f),
                    radius = 32f,
                    center = targetPinPx
                )
                drawCircle(
                    color = customColors.accent.copy(alpha = 0.5f),
                    radius = 18f,
                    center = targetPinPx
                )

                // Pin stem & head
                val pinPath = Path().apply {
                    moveTo(targetPinPx.x, targetPinPx.y)
                    lineTo(targetPinPx.x - 14f, targetPinPx.y - 36f)
                    arcTo(
                        androidx.compose.ui.geometry.Rect(
                            targetPinPx.x - 18f, targetPinPx.y - 54f,
                            targetPinPx.x + 18f, targetPinPx.y - 18f
                        ),
                        180f, 180f, false
                    )
                    lineTo(targetPinPx.x, targetPinPx.y)
                }
                drawPath(path = pinPath, color = customColors.accent)
                drawCircle(
                    color = Color.White,
                    radius = 7f,
                    center = Offset(targetPinPx.x, targetPinPx.y - 36f)
                )
            }
        }
    }
}

private fun DrawScope.drawStorePin(center: Offset, accentColor: Color) {
    drawCircle(color = Color.Black.copy(alpha = 0.2f), radius = 18f, center = Offset(center.x, center.y + 4f))
    drawCircle(color = accentColor, radius = 16f, center = center)
    drawCircle(color = Color.White, radius = 7f, center = center)
}

private fun DrawScope.drawCustomerPin(center: Offset, pinColor: Color) {
    drawCircle(color = Color.Black.copy(alpha = 0.2f), radius = 18f, center = Offset(center.x, center.y + 4f))
    drawCircle(color = pinColor, radius = 16f, center = center)
    drawCircle(color = Color.White, radius = 7f, center = center)
}

private fun DrawScope.drawRiderMarker(center: Offset, isDark: Boolean) {
    // Pulse animation aura
    drawCircle(
        color = StatusOpenGreen.copy(alpha = 0.35f),
        radius = 24f,
        center = center
    )
    drawCircle(
        color = if (isDark) Color(0xFF1F2937) else Color.White,
        radius = 18f,
        center = center
    )
    drawCircle(
        color = StatusOpenGreen,
        radius = 14f,
        center = center
    )
    drawCircle(
        color = Color.White,
        radius = 6f,
        center = center
    )
}

private fun lerp(start: Offset, stop: Offset, fraction: Float): Offset {
    return Offset(
        start.x + (stop.x - start.x) * fraction,
        start.y + (stop.y - start.y) * fraction
    )
}
