package com.example.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.model.Order

object ReceiptPrinterHelper {

    fun generateFormattedReceiptText(order: Order, storeName: String, storeAddress: String): String {
        val sb = StringBuilder()
        sb.appendLine("========================================")
        sb.appendLine("            $storeName")
        sb.appendLine("      $storeAddress")
        sb.appendLine("========================================")
        sb.appendLine("Order Number : ${order.orderNumber}")
        sb.appendLine("Date & Time  : ${FormatUtils.formatDateTime(order.placedAtTimestamp)}")
        sb.appendLine("Customer     : ${order.customerName}")
        sb.appendLine("Phone        : ${order.customerPhone}")
        sb.appendLine("Address      : ${order.deliveryAddress.formatted}")
        sb.appendLine("----------------------------------------")
        sb.appendLine("ITEMS ORDERED:")
        order.items.forEach { item ->
            val sizeStr = if (item.selectedSize != null) " (${item.selectedSize})" else ""
            sb.appendLine("${item.quantity}x ${item.name}$sizeStr")
            sb.appendLine("    Line Total   : ${FormatUtils.formatCurrency(item.lineTotal)}")
            if (item.modifiers.isNotEmpty()) {
                item.modifiers.forEach { mod ->
                    sb.appendLine("    + ${mod.name} (${FormatUtils.formatCurrency(mod.price)})")
                }
            }
            if (!item.notes.isNullOrBlank()) {
                sb.appendLine("    Note: ${item.notes}")
            }
        }
        sb.appendLine("----------------------------------------")
        sb.appendLine("Items Subtotal  : ${FormatUtils.formatCurrency(order.subtotal)}")
        sb.appendLine("Delivery Fee    : ${FormatUtils.formatCurrency(order.deliveryFee)}")
        if (order.serviceFee > 0.0) {
            sb.appendLine("Service Fee     : ${FormatUtils.formatCurrency(order.serviceFee)}")
        }
        if (order.discount > 0.0) {
            sb.appendLine("Discount        : -${FormatUtils.formatCurrency(order.discount)}")
        }
        sb.appendLine("TOTAL           : ${FormatUtils.formatCurrency(order.total)}")
        sb.appendLine("Payment Method  : ${order.formattedPaymentMethod}")
        sb.appendLine("Note: Delivery fee goes to the rider.")
        sb.appendLine("Status          : ${order.status.displayTitle}")
        sb.appendLine("========================================")
        sb.appendLine("      THANK YOU FOR YOUR BUSINESS!")
        sb.appendLine("      DELIVIO MERCHANT PLATFORM")
        sb.appendLine("========================================")
        return sb.toString()
    }

    fun generateReceiptHtml(order: Order, storeName: String, storeAddress: String): String {
        val itemsHtml = StringBuilder()
        order.items.forEach { item ->
            val sizeStr = if (item.selectedSize != null) "<small>(${item.selectedSize})</small>" else ""
            val modsStr = if (item.modifiers.isNotEmpty()) {
                "<br/><small style='color:#666;'>" + item.modifiers.joinToString("<br/>") { "+ ${it.name} (${FormatUtils.formatCurrency(it.price)})" } + "</small>"
            } else ""
            val noteStr = if (!item.notes.isNullOrBlank()) {
                "<br/><small style='color:#d32f2f;'>Note: ${item.notes}</small>"
            } else ""

            itemsHtml.append("""
                <tr>
                    <td style="padding:6px 0; border-bottom:1px dashed #ddd;">
                        <strong>${item.quantity}x ${item.name}</strong> $sizeStr $modsStr $noteStr
                    </td>
                    <td style="padding:6px 0; text-align:right; border-bottom:1px dashed #ddd; vertical-align:top;">
                        <strong>${FormatUtils.formatCurrency(item.lineTotal)}</strong>
                    </td>
                </tr>
            """.trimIndent())
        }

        val serviceFeeRow = if (order.serviceFee > 0.0) {
            "<tr><td>Service Fee</td><td style='text-align:right;'>${FormatUtils.formatCurrency(order.serviceFee)}</td></tr>"
        } else ""

        val discountRow = if (order.discount > 0.0) {
            "<tr style='color:#d32f2f;'><td>Discount</td><td style='text-align:right;'>-${FormatUtils.formatCurrency(order.discount)}</td></tr>"
        } else ""

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <style>
                    body {
                        font-family: 'Courier New', monospace;
                        font-size: 13px;
                        color: #111;
                        padding: 16px;
                        max-width: 360px;
                        margin: 0 auto;
                        line-height: 1.4;
                    }
                    .center { text-align: center; }
                    .dashed { border-top: 1px dashed #555; margin: 12px 0; }
                    table { width: 100%; border-collapse: collapse; }
                    .bold { font-weight: bold; }
                    .header-title { font-size: 18px; font-weight: bold; }
                    .total-row { font-size: 16px; font-weight: bold; }
                </style>
            </head>
            <body>
                <div class="center">
                    <div class="header-title">$storeName</div>
                    <div>$storeAddress</div>
                    <div style="margin-top:4px; font-size:11px; color:#555;">DELIVIO PARTNER RECEIPT</div>
                </div>
                <div class="dashed"></div>
                <div><strong>Order:</strong> ${order.orderNumber}</div>
                <div><strong>Date:</strong> ${FormatUtils.formatDateTime(order.placedAtTimestamp)}</div>
                <div><strong>Customer:</strong> ${order.customerName}</div>
                <div><strong>Phone:</strong> ${order.customerPhone}</div>
                <div><strong>Delivery:</strong> ${order.deliveryAddress.formatted}</div>
                <div class="dashed"></div>
                <table>
                    $itemsHtml
                </table>
                <div class="dashed"></div>
                <table>
                    <tr><td>Items Subtotal</td><td style="text-align:right;">${FormatUtils.formatCurrency(order.subtotal)}</td></tr>
                    <tr><td>Delivery Fee</td><td style="text-align:right;">${FormatUtils.formatCurrency(order.deliveryFee)}</td></tr>
                    $serviceFeeRow
                    $discountRow
                    <tr class="total-row" style="border-top:1px solid #111;">
                        <td style="padding-top:6px;">TOTAL</td>
                        <td style="text-align:right; padding-top:6px;">${FormatUtils.formatCurrency(order.total)}</td>
                    </tr>
                </table>
                <div style="margin-top:8px;"><strong>Payment:</strong> ${order.formattedPaymentMethod}</div>
                <div style="margin-top:4px; font-size:11px; font-style:italic; color:#555;">Delivery fee goes to the rider.</div>
                <div class="dashed"></div>
                <div class="center" style="font-size:11px; margin-top:12px;">
                    Thank you for choosing Delivio!<br/>
                    Powered by Delivio Merchant OS
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    fun printOrderReceipt(activity: Activity, order: Order, storeName: String, storeAddress: String) {
        try {
            val webView = WebView(activity)
            val html = generateReceiptHtml(order, storeName, storeAddress)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                    val printAdapter = webView.createPrintDocumentAdapter("Delivio_Receipt_${order.orderNumber}")
                    val jobName = "Receipt ${order.orderNumber}"
                    val printAttributes = PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A6)
                        .setResolution(PrintAttributes.Resolution("thermal", "Thermal Printer", 203, 203))
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build()
                    printManager?.print(jobName, printAdapter, printAttributes)
                }
            }
            webView.loadDataWithBaseURL(null, html, "text/HTML", "UTF-8", null)
        } catch (e: Exception) {
            shareReceiptAsText(activity, order, storeName, storeAddress)
        }
    }

    fun shareReceiptAsText(context: Context, order: Order, storeName: String, storeAddress: String) {
        val receiptText = generateFormattedReceiptText(order, storeName, storeAddress)
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
