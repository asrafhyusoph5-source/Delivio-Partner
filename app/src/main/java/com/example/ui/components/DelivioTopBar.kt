package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BusinessType
import com.example.data.model.MerchantSettings
import com.example.ui.theme.LocalDelivioColors
import com.example.ui.theme.StatusClosedRed
import com.example.ui.theme.StatusOpenGreen
import com.example.ui.theme.StatusWarningAmber
import com.example.util.FormatUtils

@Composable
fun DelivioTopBar(
    settings: MerchantSettings,
    onToggleOpen: () -> Unit,
    currentTime: Long = System.currentTimeMillis(),
    isServiceLocked: Boolean = false,
    serviceLockMessage: String? = null,
    modifier: Modifier = Modifier
) {
    val customColors = LocalDelivioColors.current
    val effectiveOpen = com.example.util.BusinessHoursHelper.isEffectiveOpen(settings, currentTime, isServiceLocked)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Store Identity & Status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                MerchantLogoView(
                    storeName = settings.storeName,
                    logoUrl = settings.logoUrl,
                    bannerUrl = settings.bannerUrl,
                    imageUrl = settings.imageUrl,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    shape = RoundedCornerShape(10.dp),
                    letterSize = 18.sp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = settings.storeName.ifBlank { "Merchant Store" },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Global Open/Close Status Chip (showing effectiveOpen)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (effectiveOpen) StatusOpenGreen.copy(alpha = 0.15f)
                                    else StatusClosedRed.copy(alpha = 0.15f)
                                )
                                .then(
                                    if (!isServiceLocked) Modifier.clickable { onToggleOpen() }
                                    else Modifier
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .testTag("topbar_status_chip")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (effectiveOpen) StatusOpenGreen else StatusClosedRed)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (effectiveOpen) "Open" else "Closed",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (effectiveOpen) StatusOpenGreen else StatusClosedRed
                            )
                        }

                        if (settings.autoSchedule) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Auto",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = customColors.accent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(customColors.accentTint)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }

                        if (settings.isPaused && settings.pauseUntilTimestamp != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Paused (${FormatUtils.formatRemainingTime(settings.pauseUntilTimestamp)})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = StatusWarningAmber,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(StatusWarningAmber.copy(alpha = 0.15f))
                            )
                        }
                    }
                }
            }
        }
    }
}
