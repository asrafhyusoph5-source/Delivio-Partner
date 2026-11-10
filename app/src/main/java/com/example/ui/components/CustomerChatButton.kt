package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DelivioYellow

@Composable
fun CustomerChatButton(
    hasUnread: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    orderId: String = ""
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(48.dp)
            .testTag(if (orderId.isNotBlank()) "order_chat_btn_$orderId" else "order_chat_btn"),
        contentAlignment = Alignment.Center
    ) {
        if (hasUnread) {
            // Soft pulsing yellow ring when there is an unread customer message
            val infiniteTransition = rememberInfiniteTransition(label = "chat_glow_transition")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 0.9f,
                targetValue = 1.35f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse_scale"
            )
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.55f,
                targetValue = 0.06f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse_alpha"
            )

            // Outer pulsing halo
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(DelivioYellow.copy(alpha = pulseAlpha))
            )
        }

        // Inner circular button
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (hasUnread) DelivioYellow.copy(alpha = 0.22f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                )
                .then(
                    if (hasUnread) Modifier.border(1.5.dp, DelivioYellow, CircleShape)
                    else Modifier
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true, radius = 20.dp),
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = if (hasUnread) "Chat with customer (unread message)" else "Chat with customer",
                tint = if (hasUnread) Color(0xFFB45309) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )

            // Small indicator dot at top-right
            if (hasUnread) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 1.dp, y = (-1).dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE11D48)) // Vivid red/coral dot
                        .border(1.5.dp, Color.White, CircleShape)
                )
            }
        }
    }
}
