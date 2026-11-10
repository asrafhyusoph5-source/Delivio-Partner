package com.example.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.model.Order
import com.example.data.model.ReceiptCopyConfig
import com.example.data.model.ReceiptDesign
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ReceiptPrinterHelper {

    fun getColumnWidth(paperSize: String): Int {
        return if (paperSize.contains("58", ignoreCase = true)) 32 else 42
    }

    private fun formatTwoColumns(left: String, right: String, totalCols: Int): String {
        val spacesNeeded = totalCols - left.length - right.length
        return if (spacesNeeded > 0) {
            left + " ".repeat(spacesNeeded) + right
        } else if (spacesNeeded == 0) {
            left + right
        } else {
            val maxLeft = (totalCols - right.length - 1).coerceAtLeast(1)
            val truncatedLeft = if (left.length > maxLeft) left.take(maxLeft - 1) + "." else left
            val remainingSpaces = (totalCols - truncatedLeft.length - right.length).coerceAtLeast(1)
            truncatedLeft + " ".repeat(remainingSpaces) + right
        }
    }

    private fun alignLine(text: String, align: String, cols: Int): String {
        if (!align.equals("center", ignoreCase = true)) return text
        if (text.length >= cols) return text
        val padding = (cols - text.length) / 2
        return " ".repeat(padding) + text
    }

    private fun resolveSeparator(type: String, cols: Int): String {
        return when (type.lowercase().trim()) {
            "solid" -> "─".repeat(cols)
            "double" -> "=".repeat(cols)
            else -> "-".repeat(cols)
        }
    }

    private fun renderCopy(
        sb: StringBuilder,
        copyConfig: ReceiptCopyConfig,
        order: Order,
        storeName: String,
        cols: Int,
        sep: String,
        design: ReceiptDesign,
        orderedAtLine: String,
        itemsForLine: String,
        effectiveNote: String
    ) {
        val align = design.align
        val brand = design.brandLine.ifBlank { "Delivio" }

        if (copyConfig.title.isNotBlank()) {
            sb.appendLine(alignLine(copyConfig.title, align, cols))
        }
        if (storeName.isNotBlank()) {
            sb.appendLine(alignLine(storeName, align, cols))
        }
        sb.appendLine(alignLine(brand, align, cols))

        // Extra header lines under the brand line
        for (hLine in design.headerLines) {
            if (hLine.isNotBlank()) {
                sb.appendLine(alignLine(hLine, align, cols))
            }
        }

        // Directly under "Delivio" (and extra header lines), add line "Order #<orderNumber>"
        val orderNoDisplay = "Order ${order.formattedOrderNumber}"
        sb.appendLine(alignLine(orderNoDisplay, align, cols))

        if (design.showOrderedAt) {
            sb.appendLine(alignLine(orderedAtLine, align, cols))
        }
        if (order.scheduledFor != null && order.scheduledFor > 0) {
            val schedTimeStr = FormatUtils.formatScheduledTime(order.scheduledFor)
            sb.appendLine(alignLine("Scheduled for $schedTimeStr", align, cols))
        }
        if (design.showCustomerName) {
            sb.appendLine(alignLine(itemsForLine, align, cols))
        } else {
            val totalQty = order.items.sumOf { it.quantity }
            sb.appendLine(alignLine("$totalQty Items", align, cols))
        }
        sb.appendLine(sep)

        var calculatedSubtotal = 0.0

        order.items.forEach { item ->
            val baseLineTotal = item.quantity * item.basePrice
            calculatedSubtotal += baseLineTotal
            val itemLine = "${item.quantity} ${item.name}"
            if (copyConfig.showItemPrices) {
                val itemPriceStr = FormatUtils.formatCurrency(baseLineTotal)
                sb.appendLine(formatTwoColumns(itemLine, itemPriceStr, cols))
            } else {
                sb.appendLine(itemLine)
            }

            if (design.showSetChoices) {
                item.selections.forEach { sel ->
                    val choiceLine = "   + ${sel.choiceName}"
                    if (copyConfig.showItemPrices && sel.price > 0.0) {
                        val choiceTotal = item.quantity * sel.price
                        calculatedSubtotal += choiceTotal
                        val choicePriceStr = FormatUtils.formatCurrency(choiceTotal)
                        sb.appendLine(formatTwoColumns(choiceLine, choicePriceStr, cols))
                    } else {
                        sb.appendLine(choiceLine)
                    }
                }
            }
        }

        sb.appendLine(sep)
        if (design.showNote && effectiveNote.isNotBlank()) {
            sb.appendLine("Note: $effectiveNote")
            sb.appendLine(sep)
        }

        if (copyConfig.showItemPrices) {
            val subtotalToDisplay = if (calculatedSubtotal > 0.0) calculatedSubtotal else order.subtotal
            val subtotalStr = FormatUtils.formatCurrency(subtotalToDisplay)
            sb.appendLine(formatTwoColumns("Subtotal", subtotalStr, cols))
            if (order.promo != null && order.promo.discount > 0.0) {
                val discountStr = "-₱${FormatUtils.formatCurrency(order.promo.discount)}"
                sb.appendLine(formatTwoColumns("Voucher (${order.promo.code})", discountStr, cols))
            }
        }

        if (design.footerText.isNotBlank()) {
            sb.appendLine(sep)
            sb.appendLine(alignLine(design.footerText, align, cols))
        }
    }

    fun generateFormattedReceiptText(
        order: Order,
        storeName: String,
        paperSize: String = "80mm",
        design: ReceiptDesign = ReceiptDesign()
    ): String {
        val cols = getColumnWidth(paperSize)
        val sep = resolveSeparator(design.separator, cols)
        val dividerSep = "_".repeat(cols)

        val timeZone = TimeZone.getTimeZone("Asia/Manila")
        val timeFmt = SimpleDateFormat("h:mm a", Locale.US).apply { this.timeZone = timeZone }
        val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.US).apply { this.timeZone = timeZone }
        val timeStr = timeFmt.format(Date(order.placedAtTimestamp))
        val dateStr = dateFmt.format(Date(order.placedAtTimestamp))
        val orderedAtLine = "Ordered at $timeStr   $dateStr"
        val totalQty = order.items.sumOf { it.quantity }
        val itemsForLine = "$totalQty Items for ${order.customerName}"
        val effectiveNote = order.effectiveMerchantNote

        val sb = StringBuilder()
        val hasMerchant = design.merchantCopy.enabled
        val hasCustomer = design.customerCopy.enabled

        if (hasMerchant) {
            renderCopy(
                sb = sb,
                copyConfig = design.merchantCopy.copy(
                    title = design.merchantCopy.title.ifBlank { "MERCHANT'S COPY" }
                ),
                order = order,
                storeName = storeName,
                cols = cols,
                sep = sep,
                design = design,
                orderedAtLine = orderedAtLine,
                itemsForLine = itemsForLine,
                effectiveNote = effectiveNote
            )
        }

        if (hasMerchant && hasCustomer) {
            sb.appendLine(dividerSep)
        }

        if (hasCustomer) {
            renderCopy(
                sb = sb,
                copyConfig = design.customerCopy.copy(
                    title = design.customerCopy.title.ifBlank { "CUSTOMER'S COPY" }
                ),
                order = order,
                storeName = storeName,
                cols = cols,
                sep = sep,
                design = design,
                orderedAtLine = orderedAtLine,
                itemsForLine = itemsForLine,
                effectiveNote = effectiveNote
            )
        }

        // Cut line after receipt
        val cutLine = if (cols == 32) "- - - - - - [CUT] - - - - - -" else "- - - - - - - - - [CUT HERE] - - - - - - - - -"
        sb.appendLine(cutLine)

        return sb.toString()
    }

    fun generateReceiptHtml(
        order: Order,
        storeName: String,
        paperSize: String = "80mm",
        design: ReceiptDesign = ReceiptDesign()
    ): String {
        val receiptText = generateFormattedReceiptText(order, storeName, paperSize, design)
        val escaped = receiptText
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        val maxWidthPx = if (paperSize.contains("58", ignoreCase = true)) "280px" else "380px"
        val fontSizePx = when (design.fontSize.lowercase().trim()) {
            "small" -> "11px"
            "large" -> "16px"
            else -> "13px"
        }
        val textAlign = if (design.align.equals("center", ignoreCase = true)) "center" else "left"

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <style>
                    @page {
                        margin: 0;
                        size: auto;
                    }
                    body {
                        font-family: 'Courier New', Courier, monospace;
                        font-size: $fontSizePx;
                        color: #000;
                        background: #fff;
                        margin: 0;
                        padding: 10px;
                        line-height: 1.35;
                        text-align: $textAlign;
                    }
                    pre {
                        font-family: 'Courier New', Courier, monospace;
                        font-size: $fontSizePx;
                        color: #000;
                        margin: 0;
                        padding: 0;
                        white-space: pre-wrap;
                        word-break: break-all;
                        max-width: $maxWidthPx;
                        text-align: $textAlign;
                    }
                </style>
            </head>
            <body>
                <pre>$escaped</pre>
            </body>
            </html>
        """.trimIndent()
    }

    fun printOrderReceipt(
        activity: Activity,
        order: Order,
        storeName: String,
        paperSize: String = "80mm",
        design: ReceiptDesign = ReceiptDesign()
    ) {
        try {
            val webView = WebView(activity)
            val html = generateReceiptHtml(order, storeName, paperSize, design)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                    val printAdapter = webView.createPrintDocumentAdapter("Delivio_Receipt_${order.formattedOrderNumber}")
                    val jobName = "Receipt ${order.formattedOrderNumber}"
                    val mediaSize = if (paperSize.contains("58", ignoreCase = true)) {
                        PrintAttributes.MediaSize.ISO_A7
                    } else {
                        PrintAttributes.MediaSize.ISO_A6
                    }
                    val printAttributes = PrintAttributes.Builder()
                        .setMediaSize(mediaSize)
                        .setResolution(PrintAttributes.Resolution("thermal", "Thermal Printer", 203, 203))
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build()
                    printManager?.print(jobName, printAdapter, printAttributes)
                }
            }
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        } catch (e: Exception) {
            shareReceiptAsText(activity, order, storeName, paperSize, design)
        }
    }

    fun shareReceiptAsText(
        context: Context,
        order: Order,
        storeName: String,
        paperSize: String = "80mm",
        design: ReceiptDesign = ReceiptDesign()
    ) {
        val receiptText = generateFormattedReceiptText(order, storeName, paperSize, design)
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, receiptText)
            putExtra(Intent.EXTRA_SUBJECT, "Delivio Receipt - ${order.formattedOrderNumber}")
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share Receipt")
        context.startActivity(shareIntent)
    }
}
