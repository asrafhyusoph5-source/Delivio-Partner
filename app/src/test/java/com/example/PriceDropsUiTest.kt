package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.example.data.model.BusinessType
import com.example.data.model.MenuItem
import com.example.data.model.MerchantSettings
import com.example.data.model.ShopMenu
import com.example.ui.screens.MenuManagementScreen
import com.example.ui.screens.MenuScreen
import com.example.ui.screens.PriceDropsScreen
import com.example.ui.screens.ProductEditScreen
import com.example.ui.theme.DelivioPartnerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h850dp-xxhdpi")
class PriceDropsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun menuManagementScreen_priceDropsButton_opensPriceDrops() {
        var opened = false
        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                MenuManagementScreen(
                    menus = listOf(ShopMenu(id = "main", name = "Main Menu")),
                    subcategories = emptyList(),
                    menuItems = emptyList(),
                    currentTime = 1_700_000_000_000L,
                    timeZoneId = "Asia/Manila",
                    onBack = {},
                    onAddMenu = {},
                    onEditMenu = {},
                    onToggleMenuActive = { _, _ -> },
                    onDuplicateMenu = {},
                    onDeleteMenu = {},
                    onReorderSubcategories = {},
                    onAddSubcategory = { _, _ -> },
                    onRenameSubcategory = { _, _, _ -> },
                    onDeleteSubcategory = { _, _ -> },
                    onBulkMarkStock = { _, _, _ -> },
                    onBulkMoveToMenu = { _, _, _ -> },
                    onBulkSetCategoryClass = { _, _, _, _ -> },
                    onBulkAdjustPrices = { _, _, _, _ -> },
                    onBulkDeleteItems = { _, _ -> },
                    onOpenPriceDrops = { opened = true }
                )
            }
        }

        composeRule.onNodeWithTag("price_drops_btn").assertIsDisplayed().performClick()
        assertTrue(opened)
    }

    @Test
    fun priceDropsScreen_newDropShowsLivePercent_validatesInvalidPrice_andSavesBatch() {
        val now = 1_700_000_000_000L
        val items = listOf(
            MenuItem(
                id = "i1",
                name = "Classic Burger",
                price = 100.0,
                menuIds = listOf("main", "lunch"),
                businessType = BusinessType.FOOD
            ),
            MenuItem(
                id = "i2",
                name = "Iced Tea",
                price = 50.0,
                menuIds = listOf("main"),
                businessType = BusinessType.FOOD
            )
        )
        val menus = listOf(
            ShopMenu(id = "main", name = "Main Menu"),
            ShopMenu(id = "lunch", name = "Lunch Specials")
        )

        var appliedMap: Map<String, Double>? = null
        var appliedStartsAt: Long? = -1L
        var appliedEndsAt: Long? = -1L

        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                PriceDropsScreen(
                    menuItems = items,
                    menus = menus,
                    businessType = BusinessType.FOOD,
                    currentTime = now,
                    timeZoneId = "Asia/Manila",
                    onBack = {},
                    onApplyPriceDrops = { prices, starts, ends, cb ->
                        appliedMap = prices
                        appliedStartsAt = starts
                        appliedEndsAt = ends
                        cb(true, prices.size, null)
                    },
                    onEndPriceDrop = { _, cb -> cb(true, null) }
                )
            }
        }

        // Switch to New price drop tab
        composeRule.onNodeWithTag("price_drops_tab_new").performClick()
        composeRule.waitForIdle()

        // Preselect Lunch Specials menu group (selects Classic Burger)
        composeRule.onNodeWithTag("price_drop_menu_group_lunch").performScrollTo().performClick()
        composeRule.waitForIdle()

        // Type invalid price >= regular price (120 for 100)
        composeRule.onNodeWithTag("price_drop_input_i1").performScrollTo().performTextReplacement("120")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("price_drop_error_i1").assertIsDisplayed()

        // Clicking Save must NOT submit invalid price
        composeRule.onNodeWithTag("price_drop_save_btn").performClick()
        composeRule.waitForIdle()
        assertNull(appliedMap)

        // Type valid price 38 -> live discount badge shows "-62%"
        composeRule.onNodeWithTag("price_drop_input_i1").performScrollTo().performTextReplacement("38")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("price_drop_pct_i1").assertTextContains("-62%")

        // Save submits exact price and null timestamps (Start now + Until I end it)
        composeRule.onNodeWithTag("price_drop_save_btn").performClick()
        composeRule.waitForIdle()

        assertNotNull(appliedMap)
        assertEquals(38.0, appliedMap!!["i1"]!!, 0.001)
        assertNull(appliedStartsAt)
        assertNull(appliedEndsAt)
    }

    @Test
    fun priceDropsScreen_activeTab_listsCurrentDropsAndEndsOnClick() {
        val now = 1_700_000_000_000L
        var endedItemId: String? = null
        val activeItem = MenuItem(
            id = "sale1",
            name = "Crispy Chicken",
            price = 100.0,
            salePrice = 38.0,
            saleStartsAt = null,
            saleEndsAt = null,
            saleActive = true,
            businessType = BusinessType.FOOD
        )

        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                PriceDropsScreen(
                    menuItems = listOf(activeItem),
                    menus = emptyList(),
                    businessType = BusinessType.FOOD,
                    currentTime = now,
                    timeZoneId = "Asia/Manila",
                    onBack = {},
                    onApplyPriceDrops = { _, _, _, cb -> cb(true, 0, null) },
                    onEndPriceDrop = { id, cb ->
                        endedItemId = id
                        cb(true, null)
                    }
                )
            }
        }

        composeRule.onNodeWithTag("active_drop_card_sale1").assertIsDisplayed()
        composeRule.onNodeWithText("-62%").assertIsDisplayed()
        composeRule.onNodeWithText("Until you end it").assertIsDisplayed()

        composeRule.onNodeWithTag("end_price_drop_btn_sale1").performClick()
        composeRule.waitForIdle()

        assertEquals("sale1", endedItemId)
    }

    @Test
    fun menuScreen_productCard_showsSalePriceStruckRegularAndPercent() {
        val now = 1_700_000_000_000L
        val onSaleItem = MenuItem(
            id = "on_sale",
            name = "Spicy Wings",
            price = 100.0,
            salePrice = 38.0,
            saleActive = true,
            inStock = true,
            businessType = BusinessType.FOOD
        )
        val expiredSaleItem = MenuItem(
            id = "expired_sale",
            name = "Garlic Fries",
            price = 80.0,
            salePrice = 40.0,
            saleStartsAt = now - 10_000_000L,
            saleEndsAt = now - 5_000L,
            saleActive = true,
            inStock = true,
            businessType = BusinessType.FOOD
        )

        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                MenuScreen(
                    settings = MerchantSettings(businessType = BusinessType.FOOD),
                    categories = emptyList(),
                    menuItems = listOf(onSaleItem, expiredSaleItem),
                    promos = emptyList(),
                    businessType = BusinessType.FOOD,
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

        composeRule.onNodeWithTag("menu_products_list").performScrollToNode(hasTestTag("product_card_on_sale"))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("product_sale_price_on_sale", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("product_regular_price_struck_on_sale", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("product_sale_badge_on_sale", useUnmergedTree = true).assertTextContains("-62%")

        assertEquals(
            0,
            composeRule.onAllNodesWithTag("product_sale_badge_expired_sale", useUnmergedTree = true).fetchSemanticsNodes().size
        )
    }

    @Test
    fun productEditScreen_livePercentAndAutoEndSaleWhenRegularPriceLowered() {
        val existingOnSale = MenuItem(
            id = "edit_sale",
            name = "Matcha Drink",
            price = 150.0,
            salePrice = 100.0,
            saleStartsAt = null,
            saleEndsAt = 1_800_000_000_000L,
            saleActive = true,
            categoryId = "drinks",
            category = "Drinks",
            categoryName = "Drinks",
            businessType = BusinessType.CONVENIENCE
        )

        var savedItem by mutableStateOf<MenuItem?>(null)
        var noticeText by mutableStateOf<String?>(null)

        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                ProductEditScreen(
                    item = existingOnSale,
                    isNew = false,
                    categories = emptyList(),
                    businessType = BusinessType.CONVENIENCE,
                    currentTime = 1_700_000_000_000L,
                    onSave = { updated, cb ->
                        savedItem = updated
                        cb(true, null)
                    },
                    onDelete = {},
                    onDismiss = {},
                    onShowSaleEndedNotice = { msg -> noticeText = msg }
                )
            }
        }

        // Initial discount for 150 -> 100 is -33%
        composeRule.onNodeWithTag("product_edit_sale_pct").performScrollTo().assertTextContains("-33%")

        // Edit sale price to 60 -> live discount updates to -60%
        composeRule.onNodeWithTag("product_edit_sale_price_input").performScrollTo().performTextReplacement("60")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_edit_sale_pct").assertTextContains("-60%")

        // Now lower regular price below active sale price (60 -> set regular price to 50)
        composeRule.onNodeWithTag("menu_item_price_input").performScrollTo().performTextReplacement("50")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sale_auto_end_warning").performScrollTo().assertIsDisplayed()

        // Tap Save Product -> saves regular price 50, ends sale, clears timestamps, and notifies merchant
        composeRule.onNodeWithTag("save_menu_item_btn").assertIsEnabled().performClick()
        composeRule.waitForIdle()

        assertNotNull(savedItem)
        assertEquals(50.0, savedItem!!.price, 0.001)
        assertFalse(savedItem!!.saleActive)
        assertNull(savedItem!!.saleStartsAt)
        assertNull(savedItem!!.saleEndsAt)
        assertNotNull(noticeText)
    }
}
