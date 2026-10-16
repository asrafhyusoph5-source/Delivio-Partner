package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.LocalDelivioColors
import com.example.ui.theme.StatusClosedRed
import com.example.ui.theme.StatusOpenGreen
import com.example.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiderTrackingModal(
    order: Order,
    merchantLocation: AddressLocation,
    onBookRider: (pickup: AddressLocation, dropoff: AddressLocation, note: String, isSelfDelivery: Boolean) -> Unit,
    onCancelRiderRequest: () -> Unit,
    onCompleteOrder: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val customColors = LocalDelivioColors.current
    val riderRequest = order.riderRequest

    var riderNote by remember { mutableStateOf("") }
    var isSelfDeliveryChoice by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (riderRequest == null) {
                // Booking Form
                Text(
                    text = "Request a Delivery Rider",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )

                Text(
                    text = "Book a nearby Delivio rider to pick up and deliver order ${order.orderNumber}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                // Pickup & Dropoff Cards
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text("📍", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Pickup Location (Your Store)", style = MaterialTheme.typography.labelMedium, color = customColors.accent, fontWeight = FontWeight.Bold)
                                Text(merchantLocation.formatted, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(verticalAlignment = Alignment.Top) {
                            Text("🏁", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Drop-off Location (Customer)", style = MaterialTheme.typography.labelMedium, color = StatusClosedRed, fontWeight = FontWeight.Bold)
                                Text(order.deliveryAddress.formatted, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                if (order.deliveryAddress.notes.isNotBlank()) {
                                    Text("Note: ${order.deliveryAddress.notes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Estimate Fee Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(customColors.accentTint)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Estimated Rider Delivery Fee", style = MaterialTheme.typography.bodySmall)
                        Text(FormatUtils.formatCurrency(order.deliveryFee), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    }
                    Text("Auto-dispatched", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Rider Note Field
                OutlinedTextField(
                    value = riderNote,
                    onValueChange = { riderNote = it },
                    label = { Text("Note for Rider (e.g. Bring insulated bag, pick up at side counter)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Self Delivery Option Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Self-Delivery", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text("Store staff will deliver this directly to customer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = isSelfDeliveryChoice,
                        onCheckedChange = { isSelfDeliveryChoice = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = customColors.accent,
                            checkedTrackColor = customColors.accentTint
                        )
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        onBookRider(merchantLocation, order.deliveryAddress, riderNote, isSelfDeliveryChoice)
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = customColors.accent,
                        contentColor = customColors.onAccent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("book_rider_confirm_btn")
                ) {
                    Text(
                        text = if (isSelfDeliveryChoice) "Start Self-Delivery" else "Book Delivio Rider",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            } else {
                // Active Rider Tracking Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Rider Dispatch Status",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = riderRequest.status.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = when (riderRequest.status) {
                                RiderRequestStatus.SEARCHING -> customColors.accent
                                RiderRequestStatus.DELIVERED -> StatusOpenGreen
                                else -> MaterialTheme.colorScheme.primary
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (riderRequest.status == RiderRequestStatus.SEARCHING || riderRequest.status == RiderRequestStatus.RIDER_ASSIGNED) {
                        TextButton(
                            onClick = onCancelRiderRequest,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Cancel Booking", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Interactive Map View with Animated Progression
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    InteractiveMapCanvas(
                        modifier = Modifier.fillMaxSize(),
                        riderProgress = riderRequest.progressPercent
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Searching State Spinner or Assigned Rider Card
                if (riderRequest.status == RiderRequestStatus.SEARCHING) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = customColors.accent,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Connecting to closest rider...", fontWeight = FontWeight.Bold)
                                Text("Average dispatch time: 5-8 seconds", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else if (riderRequest.rider != null) {
                    val rider = riderRequest.rider
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(customColors.accentTint),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(rider.avatarEmoji, fontSize = 24.sp)
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rider.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${rider.vehiclePlate} • ★ ${rider.rating}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Call Rider Button (enabled)
                                FilledIconButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL).apply {
                                            data = Uri.parse("tel:${rider.phone}")
                                        }
                                        context.startActivity(intent)
                                    },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = StatusOpenGreen,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.testTag("call_rider_btn")
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = "Call Rider")
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("ETA to Delivery:", style = MaterialTheme.typography.bodySmall)
                                Text("${rider.etaMinutes} mins", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = customColors.accent)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Fast Complete Order action (for test/manual override)
                if (riderRequest.status != RiderRequestStatus.DELIVERED) {
                    OutlinedButton(
                        onClick = onCompleteOrder,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("force_delivered_btn")
                    ) {
                        Text("Fast Forward: Mark Delivered", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StatusOpenGreen,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text("Delivery Complete - Close", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
