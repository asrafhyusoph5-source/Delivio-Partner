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
import kotlin.math.abs

sealed class ReceiptBlock {
    data class BrandHeader(
        val text: String,
        val align: String,
        val cols: Int
    ) : ReceiptBlock()

    data class HeaderLine(
        val text: String,
        val align: String,
        val cols: Int
    ) : ReceiptBlock()

    data class Divider(
        val text: String,
        val style: String
    ) : ReceiptBlock()

    data class CopyTitle(
        val text: String,
        val centeredLine: String
    ) : ReceiptBlock()

    data class KeyValueRow(
        val label: String,
        val value: String,
        val renderedLines: List<String>
    ) : ReceiptBlock()

    data class ItemRow(
        val left: String,
        val right: String,
        val renderedLines: List<String>
    ) : ReceiptBlock()

    data class SubBullet(
        val text: String,
        val renderedLines: List<String>
    ) : ReceiptBlock()

    data class NoteBox(
        val noteText: String,
        val wrappedLines: List<String>,
        val asciiBoxLines: List<String>
    ) : ReceiptBlock()

    data class SummaryRow(
        val label: String,
        val value: String,
        val renderedLine: String,
        val isBold: Boolean = false
    ) : ReceiptBlock()

    data class FooterLine(
        val text: String,
        val centeredLine: String
    ) : ReceiptBlock()

    data class DottedLine(
        val text: String
    ) : ReceiptBlock()

    data class CutHere(
        val text: String,
        val centeredLine: String
    ) : ReceiptBlock()

    data object CopyBreak : ReceiptBlock()
}

object ReceiptPrinterHelper {

    fun getColumnWidth(paperSize: String): Int {
        return if (paperSize.contains("58", ignoreCase = true)) 32 else 48
    }

    fun resolvePaperSize(paperSize: String, design: ReceiptDesign): String {
        return design.paperWidth.ifBlank { paperSize }.ifBlank { "80mm" }
    }

    fun formatReceiptMoney(amount: Double): String {
        return "P%,.2f".format(Locale.US, amount)
    }

    private fun formatSpacedBrand(rawBrand: String, cols: Int): List<String> {
        val upper = rawBrand.ifBlank { "Delivio" }.trim().uppercase(Locale.US)
        val spaced = upper.toCharArray().joinToString(" ")
        return if (spaced.length <= cols) {
            listOf(spaced)
        } else {
            wrapText(upper, cols)
        }
    }

    private fun wrapText(text: String, maxWidth: Int): List<String> {
        val width = maxWidth.coerceAtLeast(8)
        val result = mutableListOf<String>()
        for (rawParagraph in text.replace("\r\n", "\n").split("\n")) {
            val trimmed = rawParagraph.trim()
            if (trimmed.isEmpty()) continue
            var remaining = trimmed
            while (remaining.length > width) {
                var breakIdx = remaining.lastIndexOf(' ', width)
                if (breakIdx <= 0) breakIdx = width
                result.add(remaining.substring(0, breakIdx).trimEnd())
                remaining = remaining.substring(breakIdx).trimStart()
            }
            if (remaining.isNotEmpty()) {
                result.add(remaining)
            }
        }
        return if (result.isEmpty()) listOf("") else result
    }

    private fun formatTwoColumnsSingle(left: String, right: String, totalCols: Int): String {
        if (right.isBlank()) return left.take(totalCols)
        val spacesNeeded = totalCols - left.length - right.length
        return if (spacesNeeded > 0) {
            left + " ".repeat(spacesNeeded) + right
        } else if (spacesNeeded == 0) {
            left + right
        } else {
            val maxLeft = (totalCols - right.length - 1).coerceAtLeast(1)
            val truncatedLeft = if (left.length > maxLeft) left.take((maxLeft - 1).coerceAtLeast(1)) + "." else left
            val remainingSpaces = (totalCols - truncatedLeft.length - right.length).coerceAtLeast(1)
            truncatedLeft + " ".repeat(remainingSpaces) + right
        }
    }

    private fun formatTwoColumnsWrappedLeft(
        left: String,
        right: String,
        totalCols: Int,
        continuationIndent: String = "  "
    ): List<String> {
        if (right.isBlank()) {
            return wrapText(left, totalCols).mapIndexed { idx, line ->
                if (idx == 0) line else (continuationIndent + line).take(totalCols)
            }
        }
        if (left.length + 1 + right.length <= totalCols) {
            return listOf(formatTwoColumnsSingle(left, right, totalCols))
        }
        val firstLineMaxLeft = (totalCols - right.length - 1).coerceAtLeast(6)
        val wrapped = wrapText(left, firstLineMaxLeft)
        return wrapped.mapIndexed { idx, part ->
            if (idx == 0) {
                formatTwoColumnsSingle(part, right, totalCols)
            } else {
                (continuationIndent + part).take(totalCols)
            }
        }
    }

    private fun formatKeyValueRows(label: String, value: String, totalCols: Int): List<String> {
        val cleanVal = value.trim()
        if (label.length + 1 + cleanVal.length <= totalCols) {
            return listOf(formatTwoColumnsSingle(label, cleanVal, totalCols))
        }
        val valueWidth = (totalCols - label.length - 1).coerceAtLeast(10)
        val wrapped = wrapText(cleanVal, valueWidth)
        return wrapped.mapIndexed { idx, part ->
            if (idx == 0) {
                formatTwoColumnsSingle(label, part, totalCols)
            } else {
                " ".repeat((totalCols - part.length).coerceAtLeast(0)) + part
            }
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

    private fun resolveDottedLine(cols: Int): String {
        val pattern = ". ".repeat((cols + 1) / 2)
        return pattern.take(cols).trimEnd().let {
            if (it.length < cols && cols % 2 == 1) "$it." else it
        }
    }

    private fun isAdminPolicyLine(line: String): Boolean {
        return line.contains("Fixed POS Policy", ignoreCase = true)
    }

    private fun appendCopyBlocks(
        blocks: MutableList<ReceiptBlock>,
        copyConfig: ReceiptCopyConfig,
        isMerchantCopy: Boolean,
        order: Order,
        storeName: String,
        cols: Int,
        sep: String,
        design: ReceiptDesign,
        formattedDateTime: String,
        effectiveNote: String
    ) {
        val headerAlign = if (design.align.equals("center", ignoreCase = true)) "center" else "left"

        // 1. Brand line in CAPITALS with letter spacing
        val brandLines = formatSpacedBrand(design.brandLine, cols)
        brandLines.forEach { bLine ->
            blocks.add(
                ReceiptBlock.BrandHeader(
                    text = alignLine(bLine, headerAlign, cols),
                    align = headerAlign,
                    cols = cols
                )
            )
        }

        // 2. Extra header lines
        for (rawHeader in design.headerLines) {
            val cleaned = rawHeader.trim()
            if (cleaned.isNotEmpty() && !isAdminPolicyLine(cleaned)) {
                wrapText(cleaned, cols).forEach { wLine ->
                    blocks.add(
                        ReceiptBlock.HeaderLine(
                            text = alignLine(wLine, headerAlign, cols),
                            align = headerAlign,
                            cols = cols
                        )
                    )
                }
            }
        }

        // 3. Divider
        blocks.add(ReceiptBlock.Divider(sep, design.separator))

        // 4. Framed copy title "*** MERCHANT'S COPY ***"
        val defaultTitle = if (isMerchantCopy) "MERCHANT'S COPY" else "CUSTOMER'S COPY"
        val cleanTitle = copyConfig.title.trim().ifBlank { defaultTitle }.uppercase(Locale.US)
        val framedTitle = "*** $cleanTitle ***"
        blocks.add(
            ReceiptBlock.CopyTitle(
                text = framedTitle,
                centeredLine = alignLine(framedTitle, "center", cols)
            )
        )

        // 5. Divider
        blocks.add(ReceiptBlock.Divider(sep, design.separator))

        // 6. Label & value rows: ORDER:, DATE:, SCHED:, CUST:, MERCHANT:
        var addedMetaRow = false
        if (design.showOrderId) {
            val orderNo = order.formattedOrderNumber
            blocks.add(
                ReceiptBlock.KeyValueRow(
                    label = "ORDER:",
                    value = orderNo,
                    renderedLines = formatKeyValueRows("ORDER:", orderNo, cols)
                )
            )
            addedMetaRow = true
        }
        if (design.showOrderedAt) {
            blocks.add(
                ReceiptBlock.KeyValueRow(
                    label = "DATE:",
                    value = formattedDateTime,
                    renderedLines = formatKeyValueRows("DATE:", formattedDateTime, cols)
                )
            )
            if (order.scheduledFor != null && order.scheduledFor > 0L) {
                val schedStr = "Scheduled for ${FormatUtils.formatScheduledTime(order.scheduledFor)}"
                blocks.add(
                    ReceiptBlock.KeyValueRow(
                        label = "SCHED:",
                        value = schedStr,
                        renderedLines = formatKeyValueRows("SCHED:", schedStr, cols)
                    )
                )
            }
            addedMetaRow = true
        }
        if (design.showCustomerName) {
            val custName = order.customerName.trim().ifBlank { "Customer" }
            blocks.add(
                ReceiptBlock.KeyValueRow(
                    label = "CUST:",
                    value = custName,
                    renderedLines = formatKeyValueRows("CUST:", custName, cols)
                )
            )
            addedMetaRow = true
        }
        val effectiveShopName = storeName.trim().ifBlank { order.merchantName.trim() }
        if (effectiveShopName.isNotBlank()) {
            blocks.add(
                ReceiptBlock.KeyValueRow(
                    label = "MERCHANT:",
                    value = effectiveShopName,
                    renderedLines = formatKeyValueRows("MERCHANT:", effectiveShopName, cols)
                )
            )
            addedMetaRow = true
        }

        // 7. Divider
        if (addedMetaRow) {
            blocks.add(ReceiptBlock.Divider(sep, design.separator))
        }

        // 8. Items
        var calculatedSubtotal = 0.0
        val orderItemsTotalMatchesSubtotal = order.subtotal > 0.0 &&
            abs(order.subtotal - order.items.sumOf { it.lineTotal }) < 0.05

        order.items.forEach { item ->
            val selectionsPriceSum = item.selections.sumOf { it.price }
            val modifiersPriceSum = item.modifiers.sumOf { it.price }
            val extrasSum = selectionsPriceSum + modifiersPriceSum

            val itemDisplayTotal = when {
                extrasSum > 0.0 && item.basePrice > 0.0 && item.effectivePrice > item.basePrice &&
                    abs(item.effectivePrice - (item.basePrice + extrasSum)) < 0.05 -> {
                    item.quantity * item.basePrice
                }
                orderItemsTotalMatchesSubtotal -> item.lineTotal
                item.basePrice > 0.0 -> item.quantity * item.basePrice
                else -> item.lineTotal
            }
            calculatedSubtotal += itemDisplayTotal + (item.quantity * extrasSum)

            val itemLeft = "${item.quantity}x ${item.name.trim()}"
            val itemRight = if (copyConfig.showItemPrices) formatReceiptMoney(itemDisplayTotal) else ""
            blocks.add(
                ReceiptBlock.ItemRow(
                    left = itemLeft,
                    right = itemRight,
                    renderedLines = formatTwoColumnsWrappedLeft(itemLeft, itemRight, cols)
                )
            )

            // Variation line
            if (item.variantLabel.isNotBlank()) {
                val bulletText = "  • ${item.variantLabel.trim()}"
                val wrappedBullet = wrapText(bulletText.trimStart(), (cols - 2).coerceAtLeast(10))
                    .mapIndexed { idx, line -> if (idx == 0) "  $line" else "    $line" }
                blocks.add(ReceiptBlock.SubBullet(text = bulletText, renderedLines = wrappedBullet))
            }

            // Set choices & modifiers
            if (design.showSetChoices) {
                item.selections.forEach { sel ->
                    val labelPart = if (sel.setName.isNotBlank()) {
                        "${sel.setName.trim()}: ${sel.choiceName.trim()}"
                    } else {
                        sel.choiceName.trim()
                    }
                    val pricePart = if (copyConfig.showItemPrices && sel.price > 0.0) {
                        " (+${formatReceiptMoney(sel.price)})"
                    } else {
                        ""
                    }
                    val bulletText = "  • $labelPart$pricePart"
                    val wrappedBullet = wrapText(bulletText.trimStart(), (cols - 2).coerceAtLeast(10))
                        .mapIndexed { idx, line -> if (idx == 0) "  $line" else "    $line" }
                    blocks.add(ReceiptBlock.SubBullet(text = bulletText, renderedLines = wrappedBullet))
                }

                if (design.showModifiers) {
                    item.modifiers.forEach { mod ->
                        val pricePart = if (copyConfig.showItemPrices && mod.price > 0.0) {
                            " (+${formatReceiptMoney(mod.price)})"
                        } else {
                            ""
                        }
                        val bulletText = "  • ${mod.name.trim()}$pricePart"
                        val wrappedBullet = wrapText(bulletText.trimStart(), (cols - 2).coerceAtLeast(10))
                            .mapIndexed { idx, line -> if (idx == 0) "  $line" else "    $line" }
                        blocks.add(ReceiptBlock.SubBullet(text = bulletText, renderedLines = wrappedBullet))
                    }
                }

                if (!item.selectedSize.isNullOrBlank()) {
                    val bulletText = "  • Size: ${item.selectedSize.trim()}"
                    blocks.add(ReceiptBlock.SubBullet(text = bulletText, renderedLines = listOf(bulletText.take(cols))))
                }
            }

            if (!item.notes.isNullOrBlank()) {
                val bulletText = "  • Item note: ${item.notes.trim()}"
                val wrappedBullet = wrapText(bulletText.trimStart(), (cols - 2).coerceAtLeast(10))
                    .mapIndexed { idx, line -> if (idx == 0) "  $line" else "    $line" }
                blocks.add(ReceiptBlock.SubBullet(text = bulletText, renderedLines = wrappedBullet))
            }
        }

        // 9. Divider after items
        blocks.add(ReceiptBlock.Divider(sep, design.separator))

        // 10. Bordered NOTE: box (if enabled & non-blank) + Divider
        val showNoteBox = design.showNote && effectiveNote.isNotBlank()
        if (showNoteBox) {
            val innerWidth = (cols - 4).coerceAtLeast(8)
            val noteWrapped = wrapText("NOTE: $effectiveNote", innerWidth)
            val borderH = "+${"-".repeat((cols - 2).coerceAtLeast(4))}+"
            val asciiLines = buildList {
                add(borderH)
                noteWrapped.forEach { line ->
                    add("| ${line.padEnd(innerWidth)} |")
                }
                add(borderH)
            }
            blocks.add(
                ReceiptBlock.NoteBox(
                    noteText = effectiveNote,
                    wrappedLines = noteWrapped,
                    asciiBoxLines = asciiLines
                )
            )
            if (copyConfig.showItemPrices || design.footerText.isNotBlank()) {
                blocks.add(ReceiptBlock.Divider(sep, design.separator))
            }
        }

        // 11. SUBTOTAL (only Subtotal; Voucher shown on Merchant's Copy only; never fees/tips/VAT/total)
        if (copyConfig.showItemPrices) {
            val subtotalToDisplay = if (order.subtotal > 0.0) order.subtotal else calculatedSubtotal
            val subtotalStr = formatReceiptMoney(subtotalToDisplay)
            blocks.add(
                ReceiptBlock.SummaryRow(
                    label = "SUBTOTAL",
                    value = subtotalStr,
                    renderedLine = formatTwoColumnsSingle("SUBTOTAL", subtotalStr, cols),
                    isBold = true
                )
            )

            if (isMerchantCopy && order.promo != null && order.promo.discount > 0.0) {
                val code = order.promo.code.trim().ifBlank { "VOUCHER" }
                val discountStr = "-${formatReceiptMoney(order.promo.discount)}"
                val vLabel = "Voucher ($code)"
                blocks.add(
                    ReceiptBlock.SummaryRow(
                        label = vLabel,
                        value = discountStr,
                        renderedLine = formatTwoColumnsSingle(vLabel, discountStr, cols),
                        isBold = false
                    )
                )
            }

            blocks.add(ReceiptBlock.Divider(sep, design.separator))
        }

        // 12. Footer text
        if (design.footerText.isNotBlank()) {
            val footerLines = wrapText(design.footerText, cols).filter { !isAdminPolicyLine(it) }
            footerLines.forEach { fLine ->
                blocks.add(
                    ReceiptBlock.FooterLine(
                        text = fLine,
                        centeredLine = alignLine(fLine, "center", cols)
                    )
                )
            }
        }

        // 13. Dotted line & CUT HERE
        val dotted = resolveDottedLine(cols)
        blocks.add(ReceiptBlock.DottedLine(dotted))
        blocks.add(
            ReceiptBlock.CutHere(
                text = "CUT HERE",
                centeredLine = alignLine("CUT HERE", "center", cols)
            )
        )
    }

    /**
     * SINGLE SHARED RECEIPT RENDERER:
     * Produces the exact receipt blocks consumed identically by Preview, Share, Print (HTML), and Bluetooth ESC/POS.
     */
    fun renderReceiptBlocks(
        order: Order,
        storeName: String,
        paperSize: String = "80mm",
        design: ReceiptDesign = ReceiptDesign()
    ): List<ReceiptBlock> {
        val effectivePaperSize = resolvePaperSize(paperSize, design)
        val cols = getColumnWidth(effectivePaperSize)
        val sep = resolveSeparator(design.separator, cols)

        val timeZone = TimeZone.getTimeZone("Asia/Manila")
        val dateTimeFmt = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US).apply { this.timeZone = timeZone }
        val formattedDateTime = dateTimeFmt.format(Date(order.placedAtTimestamp))
        val effectiveNote = order.effectiveMerchantNote

        val blocks = mutableListOf<ReceiptBlock>()
        val hasMerchant = design.merchantCopy.enabled
        val hasCustomer = design.customerCopy.enabled

        if (hasMerchant) {
            appendCopyBlocks(
                blocks = blocks,
                copyConfig = design.merchantCopy.copy(
                    title = design.merchantCopy.title.ifBlank { "MERCHANT'S COPY" }
                ),
                isMerchantCopy = true,
                order = order,
                storeName = storeName,
                cols = cols,
                sep = sep,
                design = design,
                formattedDateTime = formattedDateTime,
                effectiveNote = effectiveNote
            )
        }

        if (hasMerchant && hasCustomer) {
            blocks.add(ReceiptBlock.CopyBreak)
        }

        if (hasCustomer) {
            appendCopyBlocks(
                blocks = blocks,
                copyConfig = design.customerCopy.copy(
                    title = design.customerCopy.title.ifBlank { "CUSTOMER'S COPY" }
                ),
                isMerchantCopy = false,
                order = order,
                storeName = storeName,
                cols = cols,
                sep = sep,
                design = design,
                formattedDateTime = formattedDateTime,
                effectiveNote = effectiveNote
            )
        }

        return blocks
    }

    fun blocksToPlainLines(blocks: List<ReceiptBlock>): List<String> {
        val lines = mutableListOf<String>()
        for (block in blocks) {
            when (block) {
                is ReceiptBlock.BrandHeader -> lines.add(block.text)
                is ReceiptBlock.HeaderLine -> lines.add(block.text)
                is ReceiptBlock.Divider -> lines.add(block.text)
                is ReceiptBlock.CopyTitle -> lines.add(block.centeredLine)
                is ReceiptBlock.KeyValueRow -> lines.addAll(block.renderedLines)
                is ReceiptBlock.ItemRow -> lines.addAll(block.renderedLines)
                is ReceiptBlock.SubBullet -> lines.addAll(block.renderedLines)
                is ReceiptBlock.NoteBox -> lines.addAll(block.asciiBoxLines)
                is ReceiptBlock.SummaryRow -> lines.add(block.renderedLine)
                is ReceiptBlock.FooterLine -> lines.add(block.centeredLine)
                is ReceiptBlock.DottedLine -> lines.add(block.text)
                is ReceiptBlock.CutHere -> lines.add(block.centeredLine)
                ReceiptBlock.CopyBreak -> lines.add("")
            }
        }
        return lines
    }

    fun generateFormattedReceiptText(
        order: Order,
        storeName: String,
        paperSize: String = "80mm",
        design: ReceiptDesign = ReceiptDesign()
    ): String {
        val blocks = renderReceiptBlocks(order, storeName, paperSize, design)
        val lines = blocksToPlainLines(blocks)
        return buildString {
            lines.forEach { appendLine(it) }
        }
    }

    private fun escapeHtml(raw: String): String {
        return raw
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    fun generateReceiptHtml(
        order: Order,
        storeName: String,
        paperSize: String = "80mm",
        design: ReceiptDesign = ReceiptDesign()
    ): String {
        val effectivePaperSize = resolvePaperSize(paperSize, design)
        val cols = getColumnWidth(effectivePaperSize)
        val blocks = renderReceiptBlocks(order, storeName, paperSize, design)

        val maxWidthPx = if (cols == 32) "280px" else "400px"
        val fontSizePx = when (design.fontSize.lowercase().trim()) {
            "small" -> "11px"
            "large" -> "15px"
            else -> "13px"
        }

        val bodyHtml = buildString {
            for (block in blocks) {
                when (block) {
                    is ReceiptBlock.BrandHeader -> {
                        val alignCss = if (block.align == "center") "center" else "left"
                        appendLine("<div class='brand' style='text-align:$alignCss;'>${escapeHtml(block.text.trim())}</div>")
                    }
                    is ReceiptBlock.HeaderLine -> {
                        val alignCss = if (block.align == "center") "center" else "left"
                        appendLine("<div class='line' style='text-align:$alignCss;'>${escapeHtml(block.text.trim())}</div>")
                    }
                    is ReceiptBlock.Divider -> {
                        appendLine("<div class='line'>${escapeHtml(block.text)}</div>")
                    }
                    is ReceiptBlock.CopyTitle -> {
                        appendLine("<div class='copy-title'>${escapeHtml(block.text)}</div>")
                    }
                    is ReceiptBlock.KeyValueRow -> {
                        block.renderedLines.forEach { line ->
                            appendLine("<pre class='row'>${escapeHtml(line)}</pre>")
                        }
                    }
                    is ReceiptBlock.ItemRow -> {
                        block.renderedLines.forEach { line ->
                            appendLine("<pre class='row'>${escapeHtml(line)}</pre>")
                        }
                    }
                    is ReceiptBlock.SubBullet -> {
                        block.renderedLines.forEach { line ->
                            appendLine("<pre class='row sub'>${escapeHtml(line)}</pre>")
                        }
                    }
                    is ReceiptBlock.NoteBox -> {
                        appendLine("<div class='note-box'>")
                        block.wrappedLines.forEach { line ->
                            appendLine("<div class='note-line'>${escapeHtml(line)}</div>")
                        }
                        appendLine("</div>")
                    }
                    is ReceiptBlock.SummaryRow -> {
                        val cls = if (block.isBold) "row bold" else "row"
                        appendLine("<pre class='$cls'>${escapeHtml(block.renderedLine)}</pre>")
                    }
                    is ReceiptBlock.FooterLine -> {
                        appendLine("<div class='footer'>${escapeHtml(block.text)}</div>")
                    }
                    is ReceiptBlock.DottedLine -> {
                        appendLine("<div class='dotted'>${escapeHtml(block.text)}</div>")
                    }
                    is ReceiptBlock.CutHere -> {
                        appendLine("<div class='cut'>${escapeHtml(block.text)}</div>")
                    }
                    ReceiptBlock.CopyBreak -> {
                        appendLine("<div class='copy-break'></div>")
                    }
                }
            }
        }

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
                    }
                    .sheet {
                        max-width: $maxWidthPx;
                        width: ${cols}ch;
                    }
                    .brand {
                        font-weight: bold;
                        letter-spacing: 0.5px;
                    }
                    .line, .row {
                        font-family: 'Courier New', Courier, monospace;
                        font-size: $fontSizePx;
                        margin: 0;
                        white-space: pre;
                    }
                    .sub {
                        color: #222;
                    }
                    .bold {
                        font-weight: bold;
                    }
                    .copy-title {
                        text-align: center;
                        font-weight: bold;
                        margin: 2px 0;
                    }
                    .note-box {
                        border: 1px solid #000;
                        padding: 4px 6px;
                        margin: 4px 0;
                    }
                    .note-line {
                        white-space: pre-wrap;
                        word-break: break-word;
                    }
                    .footer {
                        text-align: center;
                        margin: 2px 0;
                    }
                    .dotted {
                        text-align: center;
                        margin-top: 6px;
                        white-space: pre;
                    }
                    .cut {
                        text-align: center;
                        font-weight: bold;
                        margin-top: 2px;
                    }
                    .copy-break {
                        height: 16px;
                    }
                </style>
            </head>
            <body>
                <div class='sheet'>
                    $bodyHtml
                </div>
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
        val effectivePaperSize = resolvePaperSize(paperSize, design)
        try {
            val webView = WebView(activity)
            val html = generateReceiptHtml(order, storeName, effectivePaperSize, design)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                    val printAdapter = webView.createPrintDocumentAdapter("Delivio_Receipt_${order.formattedOrderNumber}")
                    val jobName = "Receipt ${order.formattedOrderNumber}"
                    val mediaSize = if (effectivePaperSize.contains("58", ignoreCase = true)) {
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
            shareReceiptAsText(activity, order, storeName, effectivePaperSize, design)
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
