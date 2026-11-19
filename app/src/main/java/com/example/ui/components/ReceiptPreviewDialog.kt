package com.example.ui.components

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.*
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
import com.example.data.model.ReceiptDesign
import com.example.ui.theme.LocalDelivioColors
import com.example.util.ReceiptBlock
import com.example.util.ReceiptPrinterHelper
import kotlinx.coroutines.launch

@Composable
fun ReceiptPreviewDialog(
    order: Order,
    settings: MerchantSettings,
    receiptDesign: ReceiptDesign = ReceiptDesign(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val customColors = LocalDelivioColors.current
    val activity = context as? Activity

    val effectivePaperSize = ReceiptPrinterHelper.resolvePaperSize(
        paperSize = settings.printerPaperSize.ifBlank { "80mm" },
        design = receiptDesign
    )
    val receiptShopName = settings.displayNameWithCode.ifBlank { settings.storeName }
    val receiptBlocks = remember(order, receiptShopName, effectivePaperSize, receiptDesign) {
        ReceiptPrinterHelper.renderReceiptBlocks(order, receiptShopName, effectivePaperSize, receiptDesign)
    }

    val activeCopyLabels = buildList {
        if (receiptDesign.merchantCopy.enabled) add("Merchant")
        if (receiptDesign.customerCopy.enabled) add("Customer")
    }.joinToString(" & ").ifBlank { "No copy enabled" }

    val baseFontSize = when (receiptDesign.fontSize.lowercase().trim()) {
        "small" -> if (effectivePaperSize.contains("58")) 10.sp else 10.5.sp
        "large" -> if (effectivePaperSize.contains("58")) 12.5.sp else 13.5.sp
        else -> if (effectivePaperSize.contains("58")) 11.sp else 12.sp
    }
    val lineHeightSp = (baseFontSize.value + 4f).sp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f)
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Receipt Preview",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Thermal format: $effectivePaperSize ($activeCopyLabels)",
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

                // Realistic Monospace Thermal Paper Card driven directly by the shared ReceiptBlock list
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("receipt_preview_paper"),
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
                            .padding(16.dp)
                    ) {
                        val inkColor = Color(0xFF111111)
                        receiptBlocks.forEach { block ->
                            when (block) {
                                is ReceiptBlock.BrandHeader -> {
                                    Text(
                                        text = block.text,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = baseFontSize,
                                        lineHeight = lineHeightSp,
                                        fontWeight = FontWeight.Bold,
                                        color = inkColor,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                is ReceiptBlock.HeaderLine -> {
                                    Text(
                                        text = block.text,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = baseFontSize,
                                        lineHeight = lineHeightSp,
                                        color = inkColor,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                is ReceiptBlock.Divider -> {
                                    Text(
                                        text = block.text,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = baseFontSize,
                                        lineHeight = lineHeightSp,
                                        color = inkColor,
                                        maxLines = 1,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                is ReceiptBlock.CopyTitle -> {
                                    Text(
                                        text = block.centeredLine,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = baseFontSize,
                                        lineHeight = lineHeightSp,
                                        fontWeight = FontWeight.Bold,
                                        color = inkColor,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                is ReceiptBlock.KeyValueRow -> {
                                    block.renderedLines.forEach { line ->
                                        Text(
                                            text = line,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = baseFontSize,
                                            lineHeight = lineHeightSp,
                                            color = inkColor,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                                is ReceiptBlock.ItemRow -> {
                                    block.renderedLines.forEach { line ->
                                        Text(
                                            text = line,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = baseFontSize,
                                            lineHeight = lineHeightSp,
                                            fontWeight = FontWeight.Medium,
                                            color = inkColor,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                                is ReceiptBlock.SubBullet -> {
                                    block.renderedLines.forEach { line ->
                                        Text(
                                            text = line,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = baseFontSize,
                                            lineHeight = lineHeightSp,
                                            color = Color(0xFF333333),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                                is ReceiptBlock.NoteBox -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .border(BorderStroke(1.dp, inkColor), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                            .testTag("receipt_preview_note_box")
                                    ) {
                                        Column {
                                            block.wrappedLines.forEach { line ->
                                                Text(
                                                    text = line,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = baseFontSize,
                                                    lineHeight = lineHeightSp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = inkColor
                                                )
                                            }
                                        }
                                    }
                                }
                                is ReceiptBlock.SummaryRow -> {
                                    Text(
                                        text = block.renderedLine,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = baseFontSize,
                                        lineHeight = lineHeightSp,
                                        fontWeight = if (block.isBold) FontWeight.Bold else FontWeight.Normal,
                                        color = inkColor,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                is ReceiptBlock.FooterLine -> {
                                    Text(
                                        text = block.centeredLine,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = baseFontSize,
                                        lineHeight = lineHeightSp,
                                        color = inkColor,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                is ReceiptBlock.DottedLine -> {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = block.text,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = baseFontSize,
                                        lineHeight = lineHeightSp,
                                        color = inkColor,
                                        maxLines = 1,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                is ReceiptBlock.CutHere -> {
                                    Text(
                                        text = block.centeredLine,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = baseFontSize,
                                        lineHeight = lineHeightSp,
                                        fontWeight = FontWeight.Bold,
                                        color = inkColor,
                                        textAlign = TextAlign.Start,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                ReceiptBlock.CopyBreak -> {
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Actions: Print Receipt & Share Receipt
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            ReceiptPrinterHelper.shareReceiptAsText(
                                context,
                                order,
                                receiptShopName,
                                effectivePaperSize,
                                receiptDesign
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

                    val coroutineScope = rememberCoroutineScope()
                    var isPrintingBt by remember { mutableStateOf(false) }

                    val btPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
                    ) { grants ->
                        val allGranted = grants.values.all { it }
                        if (allGranted && settings.bluetoothPrinterAddress.isNotBlank()) {
                            isPrintingBt = true
                            coroutineScope.launch {
                                val res = com.example.util.BluetoothPrinterHelper.printOrder(
                                    context = context,
                                    deviceAddress = settings.bluetoothPrinterAddress,
                                    order = order,
                                    storeName = receiptShopName,
                                    design = receiptDesign,
                                    paperSize = effectivePaperSize
                                )
                                isPrintingBt = false
                                if (res.isSuccess) {
                                    Toast.makeText(context, "Printed to Bluetooth printer!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Bluetooth print failed: ${res.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (settings.bluetoothPrinterAddress.isNotBlank()) {
                                if (!com.example.util.BluetoothPrinterHelper.hasBluetoothPermissions(context)) {
                                    btPermissionLauncher.launch(com.example.util.BluetoothPrinterHelper.getRequiredPermissions())
                                } else {
                                    isPrintingBt = true
                                    coroutineScope.launch {
                                        val res = com.example.util.BluetoothPrinterHelper.printOrder(
                                            context = context,
                                            deviceAddress = settings.bluetoothPrinterAddress,
                                            order = order,
                                            storeName = receiptShopName,
                                            design = receiptDesign,
                                            paperSize = effectivePaperSize
                                        )
                                        isPrintingBt = false
                                        if (res.isSuccess) {
                                            Toast.makeText(context, "Printed to Bluetooth printer!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Bluetooth print failed: ${res.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            } else {
                                if (activity != null) {
                                    ReceiptPrinterHelper.printOrderReceipt(
                                        activity,
                                        order,
                                        receiptShopName,
                                        effectivePaperSize,
                                        receiptDesign
                                    )
                                } else {
                                    ReceiptPrinterHelper.shareReceiptAsText(
                                        context,
                                        order,
                                        receiptShopName,
                                        effectivePaperSize,
                                        receiptDesign
                                    )
                                }
                            }
                        },
                        enabled = !isPrintingBt,
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
                        if (isPrintingBt) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = customColors.onAccent,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (settings.bluetoothPrinterAddress.isNotBlank()) "Print (Bluetooth)" else "Print Receipt",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
