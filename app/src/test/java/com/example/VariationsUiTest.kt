package com.example

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.example.data.model.BusinessType
import com.example.data.model.MenuItem
import com.example.data.model.MerchantSettings
import com.example.data.model.Order
import com.example.data.model.OrderItem
import com.example.data.model.OrderStatus
import com.example.data.model.ProductSet
import com.example.data.model.ProductSetChoice
import com.example.data.model.ProductVariant
import com.example.data.model.ProductVariation
import com.example.data.model.ShopMenu
import com.example.data.model.VariationOption
import com.example.ui.screens.MenuScreen
import com.example.ui.screens.OrderDetailScreen
import com.example.ui.screens.OrdersScreen
import com.example.ui.screens.PriceDropsScreen
import com.example.ui.screens.ProductEditScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.theme.DelivioPartnerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h850dp-xxhdpi")
class VariationsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun convenienceProductEdit_showsVariationsAndLegacyDeleteOnlySets_foodShowsSets() {
        val convLegacy = MenuItem(
            id = "c1",
            name = "Legacy Snack",
            price = 50.0,
            categoryId = "snacks",
            category = "Snacks",
            categoryName = "Snacks",
            businessType = BusinessType.CONVENIENCE,
            hasSets = true,
            sets = listOf(
                ProductSet(
                    id = "s1",
                    name = "Dip",
                    choices = listOf(ProductSetChoice(id = "ch1", name = "Cheese", price = 10.0))
                )
            )
        )

        var savedLegacy: MenuItem? = null
        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                ProductEditScreen(
                    item = convLegacy,
                    isNew = false,
                    categories = emptyList(),
                    businessType = BusinessType.CONVENIENCE,
                    onSave = { updated, cb ->
                        savedLegacy = updated
                        cb(true, null)
                    },
                    onDelete = {},
                    onDismiss = {}
                )
            }
        }

        // Convenience shows Variations card and legacy collapsed Old options (sets) card, NEVER Add Set
        composeRule.onNodeWithTag("add_variation_initial_btn").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithTag("add_set_initial_btn").assertCountEquals(0)
        composeRule.onNodeWithTag("legacy_old_sets_card").performScrollTo().assertIsDisplayed()

        // Expand legacy sets and delete the old set
        composeRule.onNodeWithText("Old options (sets)").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("remove_legacy_set_0").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("legacy_old_sets_card").assertCountEquals(0)

        composeRule.onNodeWithTag("save_menu_item_btn").performClick()
        composeRule.waitForIdle()
        assertNotNull(savedLegacy)
        assertFalse(savedLegacy!!.hasSets)
        assertTrue(savedLegacy!!.sets.isEmpty())
    }

    @Test
    fun foodProductEdit_stillShowsOptionsSetsNeverVariations() {
        val foodItem = MenuItem(
            id = "f1",
            name = "Burger",
            price = 120.0,
            categoryId = "burgers",
            category = "Burgers",
            categoryName = "Burgers",
            businessType = BusinessType.FOOD
        )

        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                ProductEditScreen(
                    item = foodItem,
                    isNew = true,
                    categories = emptyList(),
                    businessType = BusinessType.FOOD,
                    onSave = { _, cb -> cb(true, null) },
                    onDelete = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("add_set_initial_btn").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithTag("add_variation_initial_btn").assertCountEquals(0)
    }

    @Test
    fun convenienceFullVariationWizardFlow_savesVariationsVariantsAndDerivedFields() {
        val item = MenuItem(
            id = "new1",
            name = "",
            price = 0.0,
            categoryId = "fashion",
            category = "Fashion",
            categoryName = "Fashion",
            businessType = BusinessType.CONVENIENCE
        )
        var saved: MenuItem? = null

        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                ProductEditScreen(
                    item = item,
                    isNew = true,
                    categories = emptyList(),
                    businessType = BusinessType.CONVENIENCE,
                    onSave = { updated, cb ->
                        saved = updated
                        cb(true, null)
                    },
                    onDelete = {},
                    onDismiss = {}
                )
            }
        }

        // Fill product name first
        composeRule.onNodeWithTag("menu_item_name_input").performScrollTo().performTextInput("Running Shoes")

        // Open Add Variation wizard
        composeRule.onNodeWithTag("add_variation_initial_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add_variation_screen").assertIsDisplayed()

        // Tap Color suggestion chip
        composeRule.onNodeWithTag("variation_suggestion_0_color").performScrollTo().performClick()
        composeRule.waitForIdle()

        // Add Black and White options inline
        composeRule.onNodeWithTag("add_option_chip_0").performScrollTo().performClick()
        composeRule.onNodeWithTag("inline_option_input_0").performScrollTo().performTextReplacement("Black")
        composeRule.onNodeWithTag("inline_option_confirm_btn_0").performScrollTo().performClick()
        composeRule.onNodeWithTag("inline_option_input_0").performScrollTo().performTextReplacement("White")
        composeRule.onNodeWithTag("inline_option_confirm_btn_0").performScrollTo().performClick()
        composeRule.waitForIdle()

        // Toggle Variation 1 images ON -> Next must be disabled until https URLs provided
        composeRule.onNodeWithTag("variation_image_switch").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("next_set_stock_price_btn").assertIsNotEnabled()

        composeRule.onNodeWithTag("variation_option_url_0").performScrollTo().performTextReplacement("https://example.com/blk.jpg")
        composeRule.onNodeWithTag("variation_option_url_1").performScrollTo().performTextReplacement("https://example.com/wht.jpg")
        composeRule.waitForIdle()

        // Add Variation 2: Size -> 42, 43
        composeRule.onNodeWithTag("add_variation_group_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("variation_card_1").performScrollTo()
        composeRule.onNodeWithTag("variation_suggestion_1_size").performScrollTo().performClick()
        composeRule.onNodeWithTag("add_option_chip_1").performScrollTo().performClick()
        composeRule.onNodeWithTag("inline_option_input_1").performScrollTo().performTextReplacement("42")
        composeRule.onNodeWithTag("inline_option_confirm_btn_1").performScrollTo().performClick()
        composeRule.onNodeWithTag("inline_option_input_1").performScrollTo().performTextReplacement("43")
        composeRule.onNodeWithTag("inline_option_confirm_btn_1").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("variation_combinations_count").performScrollTo().assertTextContains("4 combinations (maximum 200)")
        composeRule.onAllNodesWithTag("add_variation_error_banner").assertCountEquals(0)
        composeRule.onNodeWithTag("next_set_stock_price_btn").assertIsEnabled().performClick()
        composeRule.waitForIdle()

        // Screen 3: Set Stock And Price
        composeRule.onNodeWithTag("set_stock_price_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Color: Black").assertIsDisplayed()
        composeRule.onNodeWithText("Color: White").assertIsDisplayed()

        // Use Batch Edit to fill all 4 rows cleanly
        composeRule.onNodeWithTag("batch_edit_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("batch_edit_price_input").performTextInput("199")
        composeRule.onNodeWithTag("batch_edit_stock_input").performTextInput("10")
        composeRule.onNodeWithTag("batch_apply_all_btn").performClick()
        composeRule.waitForIdle()

        // Save variants back to ProductEditScreen
        composeRule.onNodeWithTag("set_stock_price_save_btn").assertIsEnabled().performClick()
        composeRule.waitForIdle()

        // Back on ProductEditScreen: manual Price & Stock fields are hidden, read-only cards + summary are shown
        composeRule.onNodeWithTag("product_edit_screen").assertIsDisplayed()
        composeRule.onAllNodesWithTag("menu_item_price_input").assertCountEquals(0)
        composeRule.onNodeWithTag("variations_price_readonly").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("variations_summary_text").performScrollTo().assertTextContains("4 combinations", substring = true)
        composeRule.onNodeWithTag("variations_stock_readonly").performScrollTo().assertIsDisplayed()

        // Save Product
        composeRule.onNodeWithTag("save_menu_item_btn").assertIsEnabled().performClick()
        composeRule.waitForIdle()

        assertNotNull(saved)
        assertTrue(saved!!.variationsEnabled)
        assertEquals(2, saved!!.variations.size)
        assertTrue(saved!!.variations[0].imageEnabled)
        assertFalse(saved!!.variations[1].imageEnabled)
        assertEquals(4, saved!!.variants.size)
        assertEquals(199.0, saved!!.price, 0.001)
        assertEquals(199.0, saved!!.priceMax!!, 0.001)
        assertEquals(40, saved!!.stockCount)
        assertTrue(saved!!.inStock)
    }

    @Test
    fun menuScreen_andPriceDropsScreen_renderVariationsAndPerVariantSales() {
        val now = 1_700_000_000_000L
        val varItem = MenuItem(
            id = "v_shoe",
            name = "Running Shoes",
            price = 199.0,
            priceMax = 249.0,
            stockCount = 12,
            lowStockAt = 5,
            inStock = true,
            variationsEnabled = true,
            variations = listOf(
                ProductVariation(
                    id = "g1",
                    name = "Color",
                    options = listOf(
                        VariationOption(id = "blk", name = "Black"),
                        VariationOption(id = "wht", name = "White")
                    )
                )
            ),
            variants = listOf(
                ProductVariant(key = "blk", optionIds = listOf("blk"), label = "Black", price = 199.0, stock = 10, salePrice = 149.0),
                ProductVariant(key = "wht", optionIds = listOf("wht"), label = "White", price = 249.0, stock = 2)
            ),
            saleActive = true,
            businessType = BusinessType.CONVENIENCE
        )

        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                MenuScreen(
                    settings = MerchantSettings(businessType = BusinessType.CONVENIENCE),
                    categories = emptyList(),
                    menuItems = listOf(varItem),
                    promos = emptyList(),
                    businessType = BusinessType.CONVENIENCE,
                    currentTime = now,
                    searchQuery = "",
                    onSearchQueryChange = {},
                    stockFilter = "All",
                    onStockFilterChange = {},
                    selectedCategoryId = null,
                    onCategorySelect = {},
                    onEditItem = {},
                    onAddItem = {},
                    onToggleItemAvailability = {},
                    onDuplicateItem = {},
                    onDeleteItem = {}
                )
            }
        }

        composeRule.onNodeWithTag("menu_products_list").performScrollToNode(hasTestTag("product_card_v_shoe"))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("product_variations_chip_v_shoe", useUnmergedTree = true).assertTextContains("2 variations")
        composeRule.onNodeWithTag("product_low_stock_chip_v_shoe", useUnmergedTree = true).assertTextContains("Low stock")
        composeRule.onNodeWithTag("product_sale_badge_v_shoe", useUnmergedTree = true).assertTextContains("-25%")
    }

    @Test
    fun priceDropsScreen_appliesPerVariantSalePrice() {
        val now = 1_700_000_000_000L
        val varItem = MenuItem(
            id = "v_shoe",
            name = "Running Shoes",
            price = 200.0,
            priceMax = 240.0,
            inStock = true,
            variationsEnabled = true,
            variants = listOf(
                ProductVariant(key = "blk", optionIds = listOf("blk"), label = "Black / 42", price = 200.0, stock = 5),
                ProductVariant(key = "wht", optionIds = listOf("wht"), label = "White / 43", price = 240.0, stock = 5)
            ),
            businessType = BusinessType.CONVENIENCE
        )

        var capturedVariantMap: Map<String, Map<String, Double>>? = null

        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                PriceDropsScreen(
                    menuItems = listOf(varItem),
                    menus = listOf(ShopMenu(id = "main", name = "Main")),
                    businessType = BusinessType.CONVENIENCE,
                    currentTime = now,
                    timeZoneId = "Asia/Manila",
                    onBack = {},
                    onApplyPriceDrops = { _, _, _, cb -> cb(true, 0, null) },
                    onEndPriceDrop = { _, cb -> cb(true, null) },
                    onApplyVariationPriceDrops = { _, vMap, _, _, cb ->
                        capturedVariantMap = vMap
                        cb(true, vMap.size, null)
                    }
                )
            }
        }

        composeRule.onNodeWithTag("price_drops_tab_new").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("price_drop_row_v_shoe").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("price_drop_variant_input_v_shoe_blk").performScrollTo().performTextReplacement("150")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("price_drop_variant_pct_v_shoe_blk").assertTextContains("-25%")

        composeRule.onNodeWithTag("price_drop_save_btn").performClick()
        composeRule.waitForIdle()

        assertNotNull(capturedVariantMap)
        assertEquals(150.0, capturedVariantMap!!["v_shoe"]!!["blk"]!!, 0.001)
    }

    @Test
    fun ordersDetailAndReports_displayVariantLabelAndVariantBreakdownDialog() {
        val now = 1_700_000_000_000L
        val order = Order(
            id = "ord1",
            orderNumber = "101",
            customerName = "Alex",
            status = OrderStatus.DELIVERED,
            placedAtTimestamp = now - 60_000L,
            subtotal = 398.0,
            total = 398.0,
            items = listOf(
                OrderItem(
                    id = "l1",
                    menuItemId = "v_shoe",
                    name = "Running Shoes",
                    quantity = 2,
                    basePrice = 199.0,
                    unitPrice = 199.0,
                    regularUnitPrice = 199.0,
                    variantKey = "blk|42",
                    variantLabel = "Black / 42"
                )
            )
        )

        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                ReportsScreen(
                    settings = MerchantSettings(businessType = BusinessType.CONVENIENCE),
                    orders = listOf(order),
                    menuItems = emptyList(),
                    reviews = emptyList(),
                    vouchers = emptyList(),
                    redemptions = emptyList(),
                    averageRating = 4.9,
                    reviewsCount = 1,
                    currentTime = now,
                    onCheckCustomerPriorDelivered = { _, _, cb -> cb(false, null) },
                    onOpenReviews = {},
                    onCreateVoucher = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("top_product_row_0").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reports_variant_breakdown_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Black / 42").assertIsDisplayed()
    }

    @Test
    fun orderDetailScreen_displaysVariantLabelAndQuantity() {
        val now = 1_700_000_000_000L
        val order = Order(
            id = "ord2",
            orderNumber = "202",
            customerName = "Sam",
            status = OrderStatus.PREPARING,
            placedAtTimestamp = now,
            subtotal = 398.0,
            total = 398.0,
            items = listOf(
                OrderItem(
                    id = "l1",
                    menuItemId = "v_shoe",
                    name = "Running Shoes",
                    quantity = 2,
                    basePrice = 199.0,
                    unitPrice = 199.0,
                    variantKey = "blk|42",
                    variantLabel = "Black / 42"
                )
            )
        )

        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                OrderDetailScreen(
                    order = order,
                    settings = MerchantSettings(businessType = BusinessType.CONVENIENCE),
                    onBack = {},
                    onAccept = {},
                    onReject = {},
                    onSetReady = {},
                    onPrintReceipt = {},
                    currentTime = now
                )
            }
        }

        composeRule.onNodeWithText("Black / 42 x2").performScrollTo().assertIsDisplayed()
    }
}
