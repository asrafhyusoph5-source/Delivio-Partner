package com.example.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.model.Order
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

    fun generateFormattedReceiptText(order: Order, storeName: String, paperSize: String = "80mm"): String {
        val cols = getColumnWidth(paperSize)
        val hyphenSep = "-".repeat(cols)
        val underscoreSep = "_".repeat(cols)

        val timeZone = TimeZone.getTimeZone("Asia/Manila")
        val timeFmt = SimpleDateFormat("h:mm a", Locale.US).apply { this.timeZone = timeZone }
        val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.US).apply { this.timeZone = timeZone }
        val timeStr = timeFmt.format(Date(order.placedAtTimestamp))
        val dateStr = dateFmt.format(Date(order.placedAtTimestamp))
        val orderedAtLine = "Ordered at $timeStr   $dateStr"
        val totalQty = order.items.sumOf { it.quantity }
        val itemsForLine = "$totalQty Items for ${order.customerName}"

        val effectiveNote = order.effectiveMerchantNote
        var calculatedSubtotal = 0.0

        val sb = StringBuilder()

        // --- 1. MERCHANT'S COPY ---
        sb.appendLine("MERCHANT'S COPY")
        sb.appendLine(storeName)
        sb.appendLine("Delivio")
        sb.appendLine(order.orderNumber)
        sb.appendLine(orderedAtLine)
        sb.appendLine(itemsForLine)
        sb.appendLine(hyphenSep)

        order.items.forEach { item ->
            val baseLineTotal = item.quantity * item.basePrice
            calculatedSubtotal += baseLineTotal
            val itemLine = "${item.quantity} ${item.name}"
            val itemPriceStr = FormatUtils.formatCurrency(baseLineTotal)
            sb.appendLine(formatTwoColumns(itemLine, itemPriceStr, cols))

            item.selections.forEach { sel ->
                val choiceLine = "   + ${sel.choiceName}"
                if (sel.price > 0.0) {
                    val choiceTotal = item.quantity * sel.price
                    calculatedSubtotal += choiceTotal
                    val choicePriceStr = FormatUtils.formatCurrency(choiceTotal)
                    sb.appendLine(formatTwoColumns(choiceLine, choicePriceStr, cols))
                } else {
                    sb.appendLine(choiceLine)
                }
            }
        }

        sb.appendLine(hyphenSep)
        if (effectiveNote.isNotBlank()) {
            sb.appendLine("Note: $effectiveNote")
            sb.appendLine(hyphenSep)
        }

        val subtotalToDisplay = if (calculatedSubtotal > 0.0) calculatedSubtotal else order.subtotal
        val subtotalStr = FormatUtils.formatCurrency(subtotalToDisplay)
        sb.appendLine(formatTwoColumns("Subtotal", subtotalStr, cols))
        sb.appendLine(formatTwoColumns("Total", subtotalStr, cols))

        sb.appendLine(underscoreSep)

        // --- 2. CUSTOMER'S COPY ---
        sb.appendLine("CUSTOMER'S COPY")
        sb.appendLine(storeName)
        sb.appendLine("Delivio")
        sb.appendLine(order.orderNumber)
        sb.appendLine(orderedAtLine)
        sb.appendLine(itemsForLine)
        sb.appendLine(hyphenSep)

        order.items.forEach { item ->
            sb.appendLine("${item.quantity} ${item.name}")
            item.selections.forEach { sel ->
                sb.appendLine("   + ${sel.choiceName}")
            }
        }

        sb.appendLine(hyphenSep)
        if (effectiveNote.isNotBlank()) {
            sb.appendLine("Note: $effectiveNote")
            sb.appendLine(hyphenSep)
        }

        sb.appendLine(formatTwoColumns("Subtotal", subtotalStr, cols))
        sb.appendLine(formatTwoColumns("Total", subtotalStr, cols))

        // Cut line after customer's copy
        val cutLine = if (cols == 32) "- - - - - - [CUT] - - - - - -" else "- - - - - - - - - [CUT HERE] - - - - - - - - -"
        sb.appendLine(cutLine)

        return sb.toString()
    }

    fun generateReceiptHtml(order: Order, storeName: String, paperSize: String = "80mm"): String {
        val receiptText = generateFormattedReceiptText(order, storeName, paperSize)
        val escaped = receiptText
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        val maxWidthPx = if (paperSize.contains("58", ignoreCase = true)) "280px" else "380px"

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
                        font-size: 13px;
                        color: #000;
                        background: #fff;
                        margin: 0;
                        padding: 10px;
                        line-height: 1.35;
                    }
                    pre {
                        font-family: 'Courier New', Courier, monospace;
                        font-size: 13px;
                        color: #000;
                        margin: 0;
                        padding: 0;
                        white-space: pre-wrap;
                        word-break: break-all;
                        max-width: $maxWidthPx;
                    }
                </style>
            </head>
            <body>
                <pre>$escaped</pre>
            </body>
            </html>
        """.trimIndent()
    }

    fun printOrderReceipt(activity: Activity, order: Order, storeName: String, paperSize: String = "80mm") {
        try {
            val webView = WebView(activity)
            val html = generateReceiptHtml(order, storeName, paperSize)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                    val printAdapter = webView.createPrintDocumentAdapter("Delivio_Receipt_${order.orderNumber}")
                    val jobName = "Receipt ${order.orderNumber}"
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
            shareReceiptAsText(activity, order, storeName, paperSize)
        }
    }

    fun shareReceiptAsText(context: Context, order: Order, storeName: String, paperSize: String = "80mm") {
        val receiptText = generateFormattedReceiptText(order, storeName, paperSize)
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, receiptText)
            putExtra(Intent.EXTRA_SUBJECT, "Delivio Receipt - ${order.orderNumber}")
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share Receipt")
        context.startActivity(shareIntent)
    }
}
