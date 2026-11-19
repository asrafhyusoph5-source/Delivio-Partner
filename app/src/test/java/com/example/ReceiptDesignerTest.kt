package com.example

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.data.model.MerchantSettings
import com.example.data.model.Order
import com.example.data.model.OrderItem
import com.example.data.model.OrderItemSelection
import com.example.data.model.OrderModifier
import com.example.data.model.OrderPromo
import com.example.data.model.ReceiptCopyConfig
import com.example.data.model.ReceiptDesign
import com.example.ui.components.ReceiptPreviewDialog
import com.example.ui.theme.DelivioPartnerTheme
import com.example.util.ReceiptBlock
import com.example.util.ReceiptPrinterHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h850dp-xxhdpi")
class ReceiptDesignerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleOrder(): Order = Order(
        id = "ord_123456",
        orderNumber = "7788",
        customerName = "Juan Dela Cruz",
        note = "Extra napkins please",
        placedAtTimestamp = 1762400000000L,
        items = listOf(
            OrderItem(
                id = "i1",
                name = "Spicy Ramen",
                quantity = 1,
                price = 220.0,
                basePrice = 200.0,
                selections = listOf(
                    OrderItemSelection(
                        setId = "s1",
                        setName = "Spicy level",
                        choiceId = "c1",
                        choiceName = "Extra Hot",
                        price = 0.0
                    ),
                    OrderItemSelection(
                        setId = "s2",
                        setName = "Add-on",
                        choiceId = "c2",
                        choiceName = "Raw Egg",
                        price = 20.0
                    )
                ),
                modifiers = listOf(
                    OrderModifier(name = "Chili Oil", price = 15.0)
                )
            ),
            OrderItem(
                id = "i2",
                name = "Running Shoes",
                quantity = 2,
                price = 500.0,
                basePrice = 500.0,
                variantLabel = "Black / 42"
            )
        ),
        subtotal = 1235.0,
        deliveryFee = 45.0,
        serviceFee = 12.0,
        platformFee = 10.0,
        tip = 30.0,
        total = 1282.0,
        promo = OrderPromo(code = "SAVE50", discount = 50.0, fundedBy = "merchant")
    )

    @Test
    fun receiptColumnWidths_match58mm32_and80mm48() {
        assertEquals(32, ReceiptPrinterHelper.getColumnWidth("58mm"))
        assertEquals(48, ReceiptPrinterHelper.getColumnWidth("80mm"))

        val blocks58 = ReceiptPrinterHelper.renderReceiptBlocks(
            order = sampleOrder(),
            storeName = "Noodle House - BGC (BGC1)",
            paperSize = "58mm"
        )
        val lines58 = ReceiptPrinterHelper.blocksToPlainLines(blocks58)
        assertTrue(lines58.any { it == "-".repeat(32) })
        assertTrue(lines58.all { it.length <= 32 })

        val blocks80 = ReceiptPrinterHelper.renderReceiptBlocks(
            order = sampleOrder(),
            storeName = "Noodle House - BGC (BGC1)",
            paperSize = "80mm"
        )
        val lines80 = ReceiptPrinterHelper.blocksToPlainLines(blocks80)
        assertTrue(lines80.any { it == "-".repeat(48) })
        assertTrue(lines80.all { it.length <= 48 })
    }

    @Test
    fun receiptLayout_matchesAdminReceiptDesigner_andVoucherMerchantCopyOnly() {
        val design = ReceiptDesign(
            brandLine = "Delivio",
            headerLines = listOf("Fresh & Fast Delivery", "Fixed POS Policy: Only Subtotal is displayed"),
            footerText = "Thank you for ordering!",
            align = "center",
            separator = "double",
            merchantCopy = ReceiptCopyConfig(enabled = true, title = "MERCHANT'S COPY", showItemPrices = true),
            customerCopy = ReceiptCopyConfig(enabled = true, title = "CUSTOMER'S COPY", showItemPrices = false)
        )

        val blocks = ReceiptPrinterHelper.renderReceiptBlocks(
            order = sampleOrder(),
            storeName = "Noodle House - BGC",
            paperSize = "80mm",
            design = design
        )
        val text = ReceiptPrinterHelper.generateFormattedReceiptText(
            order = sampleOrder(),
            storeName = "Noodle House - BGC",
            paperSize = "80mm",
            design = design
        )
        val html = ReceiptPrinterHelper.generateReceiptHtml(
            order = sampleOrder(),
            storeName = "Noodle House - BGC",
            paperSize = "80mm",
            design = design
        )

        // Exact layout checks
        assertTrue(text.contains("D E L I V I O"))
        assertTrue(text.contains("Fresh & Fast Delivery"))
        assertFalse("Admin-only POS Policy note must NEVER appear", text.contains("Fixed POS Policy"))
        assertTrue(text.contains("=".repeat(48)))
        assertTrue(text.contains("*** MERCHANT'S COPY ***"))
        assertTrue(text.contains("*** CUSTOMER'S COPY ***"))
        assertTrue(text.contains("ORDER:"))
        assertTrue(text.contains("#7788"))
        assertTrue(text.contains("DATE:"))
        assertTrue(text.contains("CUST:"))
        assertTrue(text.contains("Juan Dela Cruz"))
        assertTrue(text.contains("MERCHANT:"))
        assertTrue(text.contains("Noodle House - BGC"))
        assertTrue(text.contains("1x Spicy Ramen"))
        assertTrue(text.contains("• Spicy level: Extra Hot"))
        assertTrue(text.contains("• Add-on: Raw Egg (+P20.00)"))
        assertTrue(text.contains("• Chili Oil (+P15.00)"))
        assertTrue(text.contains("2x Running Shoes"))
        assertTrue(text.contains("• Black / 42"))
        assertTrue(text.contains("NOTE: Extra napkins please"))
        assertTrue(text.contains("+----------------------------------------------+"))
        assertTrue(text.contains("SUBTOTAL"))
        assertTrue(text.contains("Thank you for ordering!"))
        assertTrue(text.contains("CUT HERE"))

        // Voucher appears ONLY on Merchant's Copy (once), not Customer's Copy
        val merchantBlocks = blocks.takeWhile { it != ReceiptBlock.CopyBreak }
        val customerBlocks = blocks.dropWhile { it != ReceiptBlock.CopyBreak }.drop(1)
        assertTrue(merchantBlocks.any { it is ReceiptBlock.SummaryRow && it.label.contains("Voucher (SAVE50)") })
        assertFalse(customerBlocks.any { it is ReceiptBlock.SummaryRow && it.label.contains("Voucher") })

        // Customer's copy with showItemPrices = false has no prices on choices or items
        val custPlain = ReceiptPrinterHelper.blocksToPlainLines(customerBlocks).joinToString("\n")
        assertFalse(custPlain.contains("(+P20.00)"))
        assertFalse(custPlain.contains("SUBTOTAL"))
        assertTrue(custPlain.contains("• Spicy level: Extra Hot"))
        assertTrue(custPlain.contains("• Add-on: Raw Egg"))

        // HTML Print uses same shared blocks (contains copy title, item, note, subtotal, excludes Fixed POS Policy & fees)
        assertTrue(html.contains("D E L I V I O"))
        assertTrue(html.contains("*** MERCHANT'S COPY ***"))
        assertTrue(html.contains("NOTE: Extra napkins please"))
        assertTrue(html.contains("Voucher (SAVE50)"))
        assertFalse(html.contains("Fixed POS Policy"))
        assertFalse(html.contains("1,282.00"))
    }

    @Test
    fun receiptVisibilitySwitches_andCopyToggles_hideExpectedSections() {
        val design = ReceiptDesign(
            showOrderId = false,
            showOrderedAt = false,
            showCustomerName = false,
            showNote = false,
            showSetChoices = false,
            paperWidth = "58mm",
            merchantCopy = ReceiptCopyConfig(enabled = false, title = "MERCHANT'S COPY", showItemPrices = true),
            customerCopy = ReceiptCopyConfig(enabled = true, title = "KITCHEN COPY", showItemPrices = true)
        )

        val text = ReceiptPrinterHelper.generateFormattedReceiptText(
            order = sampleOrder(),
            storeName = "Cafe Uno",
            paperSize = "80mm", // design.paperWidth "58mm" drives live width!
            design = design
        )

        assertFalse(text.contains("ORDER:"))
        assertFalse(text.contains("DATE:"))
        assertFalse(text.contains("CUST:"))
        assertFalse(text.contains("NOTE:"))
        assertFalse(text.contains("Extra Hot"))
        assertFalse(text.contains("Chili Oil"))
        assertFalse(text.contains("MERCHANT'S COPY"))
        assertFalse("Voucher never prints on customer copy", text.contains("Voucher (SAVE50)"))

        assertTrue(text.contains("MERCHANT:"))
        assertTrue(text.contains("Cafe Uno"))
        assertTrue(text.contains("*** KITCHEN COPY ***"))
        assertTrue(text.contains("SUBTOTAL"))
        assertTrue(text.contains("-".repeat(32)))
        assertFalse(text.contains("-".repeat(48)))
    }

    @Test
    fun receiptPreviewDialog_rendersSharedBlocksAndNoteBox() {
        val order = sampleOrder()
        val settings = MerchantSettings(
            storeName = "Noodle House",
            branchName = "BGC",
            branchCode = "BGC1",
            printerPaperSize = "80mm"
        )
        val design = ReceiptDesign(
            brandLine = "Delivio",
            headerLines = listOf("Makati Branch"),
            footerText = "See you again!"
        )

        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                ReceiptPreviewDialog(
                    order = order,
                    settings = settings,
                    receiptDesign = design,
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("receipt_preview_paper").assertIsDisplayed()
        composeRule.onAllNodesWithTag("receipt_preview_note_box").assertCountEquals(2)
        composeRule.onNodeWithTag("share_receipt_pdf_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("print_receipt_action_btn").assertIsDisplayed()
        composeRule.onNodeWithText("Thermal format: 80mm (Merchant & Customer)").assertIsDisplayed()
    }
}
