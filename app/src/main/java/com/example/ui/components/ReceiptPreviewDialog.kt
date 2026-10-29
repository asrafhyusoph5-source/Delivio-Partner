package com.example.ui.components

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.MerchantSettings
import com.example.data.model.Order
import com.example.ui.theme.LocalDelivioColors
import com.example.util.FormatUtils
import com.example.util.ReceiptPrinterHelper

@Composable
fun ReceiptPreviewDialog(
    order: Order,
    settings: MerchantSettings,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val customColors = LocalDelivioColors.current
    val activity = context as? Activity

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Thermal Receipt Preview",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Format: ${settings.printerPaperSize} Standard Thermal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_receipt_preview_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Realistic Monospace Thermal Paper Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFFDF7) // Warm thermal paper tint
                    ),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = settings.storeName.uppercase(),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = Color(0xFF1A1A1A),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = settings.storeLocation.formatted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF4A4A4A),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "TEL: ${settings.phone}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF4A4A4A),
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "--------------------------------",
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF888888),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("ORDER:", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFF1A1A1A), fontWeight = FontWeight.Bold)
                            Text(order.orderNumber, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFF1A1A1A), fontWeight = FontWeight.Bold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("DATE:", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF4A4A4A))
                            Text(FormatUtils.formatDateTime(order.placedAtTimestamp), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF4A4A4A))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("CUSTOMER:", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF4A4A4A))
                            Text(order.customerName, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF4A4A4A))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("PAYMENT:", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF4A4A4A))
                            Text(order.paymentMethod, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF4A4A4A))
                        }

                        Text(
                            text = "--------------------------------",
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF888888),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        // Items
                        order.items.forEach { item ->
                            val sizeStr = if (item.selectedSize != null) " (${item.selectedSize})" else ""
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${item.quantity}x ${item.name}$sizeStr",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1A1A1A),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = FormatUtils.formatCurrency(item.lineTotal),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = Color(0xFF1A1A1A),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            item.modifiers.forEach { mod ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("  + ${mod.name}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF555555))
                                    Text(FormatUtils.formatCurrency(mod.price), fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF555555))
                                }
                            }
                            if (!item.notes.isNullOrBlank()) {
                                Text("  Note: ${item.notes}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFFB71C1C), modifier = Modifier.fillMaxWidth())
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        Text(
                            text = "--------------------------------",
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF888888),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        // Totals
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("ITEMS SUBTOTAL:", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF4A4A4A))
                            Text(FormatUtils.formatCurrency(order.subtotal), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF4A4A4A))
                        }
                        if (order.tip > 0.0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("RIDER TIP:", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF4A4A4A))
                                Text(FormatUtils.formatCurrency(order.tip), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF4A4A4A))
                            }
                        }
                        if (order.serviceFee > 0.0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("SERVICE FEE:", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF4A4A4A))
                                Text(FormatUtils.formatCurrency(order.serviceFee), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF4A4A4A))
                            }
                        }
                        if (order.discount > 0.0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("DISCOUNT:", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFFB71C1C))
                                Text("- ${FormatUtils.formatCurrency(order.discount)}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFFB71C1C))
                            }
                        }

                        Text(
                            text = "================================",
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF1A1A1A),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("TOTAL:", fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A1A1A))
                            Text(FormatUtils.formatCurrency(order.receiptTotal), fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A1A1A))
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("PAYMENT METHOD:", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                            Text(order.formattedPaymentMethod, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "* * * THANK YOU FOR ORDERING * * *",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF444444)
                        )
                        Text(
                            text = "Delivio Delivery Platform • Merchant Copy",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = Color(0xFF777777)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions: Print Receipt & Share Receipt as PDF / Text
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            ReceiptPrinterHelper.shareReceiptAsText(
                                context,
                                order,
                                settings.storeName,
                                settings.storeLocation.formatted
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("share_receipt_pdf_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share Receipt", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            if (activity != null) {
                                ReceiptPrinterHelper.printOrderReceipt(
                                    activity,
                                    order,
                                    settings.storeName,
                                    settings.storeLocation.formatted
                                )
                            } else {
                                ReceiptPrinterHelper.shareReceiptAsText(
                                    context,
                                    order,
                                    settings.storeName,
                                    settings.storeLocation.formatted
                                )
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = customColors.accent,
                            contentColor = customColors.onAccent
                        ),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(50.dp)
                            .testTag("print_receipt_action_btn")
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Print Receipt", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
