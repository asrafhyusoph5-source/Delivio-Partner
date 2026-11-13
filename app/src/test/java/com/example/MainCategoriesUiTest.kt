package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.example.data.model.BusinessType
import com.example.data.model.MainCategoryOption
import com.example.data.model.MenuItem
import com.example.data.model.MerchantSettings
import com.example.ui.screens.MenuScreen
import com.example.ui.screens.ProductEditScreen
import com.example.ui.theme.DelivioPartnerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h850dp-xxhdpi")
class MainCategoriesUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun productEdit_liveMainCategoriesChange_updatesDropdownAndHandlesDeletedCategory() {
        var liveCategories by mutableStateOf(
            listOf(
                MainCategoryOption(id = "tacos", name = "Tacos", active = true, sortOrder = 1),
                MainCategoryOption(id = "burgers", name = "Burgers", active = true, sortOrder = 2),
                MainCategoryOption(id = "inactive_cat", name = "Hidden Cat", active = false, sortOrder = 3),
                MainCategoryOption(id = "others", name = "Others", active = true, sortOrder = 0)
            )
        )
        var savedItem by mutableStateOf<MenuItem?>(null)

        val item = MenuItem(
            id = "item_taco",
            name = "Beef Taco",
            price = 99.0,
            categoryId = "tacos",
            category = "Tacos",
            categoryName = "Tacos",
            businessType = BusinessType.FOOD
        )

        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                ProductEditScreen(
                    item = item,
                    isNew = false,
                    categories = emptyList(),
                    businessType = BusinessType.FOOD,
                    mainCategories = liveCategories,
                    onSave = { updated, cb ->
                        savedItem = updated
                        cb(true, null)
                    },
                    onDelete = {},
                    onDismiss = {}
                )
            }
        }

        // Initially matches active "Tacos"
        composeRule.onNodeWithText("Tacos").performScrollTo().assertIsDisplayed()

        // Admin edits mainCategories: deletes "Tacos" and adds "Rice Bowls"
        liveCategories = listOf(
            MainCategoryOption(id = "rice_bowls", name = "Rice Bowls", active = true, sortOrder = 1),
            MainCategoryOption(id = "burgers", name = "Burgers", active = true, sortOrder = 2),
            MainCategoryOption(id = "others", name = "Others", active = true, sortOrder = 99)
        )
        composeRule.waitForIdle()

        // ProductEditScreen should now show "Others (was: Tacos)"
        composeRule.onNodeWithText("Others (was: Tacos)").performScrollTo().assertIsDisplayed()

        // Saving other edits without picking a new category leaves stored category and categoryId unchanged
        composeRule.onNodeWithTag("menu_item_name_input").performScrollTo().performTextReplacement("Spicy Beef Taco")
        composeRule.onNodeWithTag("save_menu_item_btn").assertIsEnabled().performClick()
        composeRule.waitForIdle()

        assertNotNull(savedItem)
        assertEquals("Spicy Beef Taco", savedItem!!.name)
        assertEquals("Tacos", savedItem!!.category)
        assertEquals("tacos", savedItem!!.categoryId)

        // Open Main Category dropdown: verifies active list updates live, inactive hidden, and Others present
        savedItem = null
        composeRule.onNodeWithTag("menu_item_category_dropdown").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Rice Bowls").assertIsDisplayed()
        composeRule.onNodeWithText("Burgers").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("Hidden Cat").fetchSemanticsNodes().size)

        // Merchant picks a new category "Rice Bowls" and saves -> category & categoryId update
        composeRule.onNodeWithText("Rice Bowls").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("save_menu_item_btn").assertIsEnabled().performClick()
        composeRule.waitForIdle()

        assertNotNull(savedItem)
        assertEquals("Rice Bowls", savedItem!!.category)
        assertEquals("Rice Bowls", savedItem!!.categoryName)
        assertEquals("rice_bowls", savedItem!!.categoryId)
    }

    @Test
    fun menuScreen_filterShowsLiveMainCategoriesAndDeletedCategoryItemsUnderOthers() {
        var liveCategories by mutableStateOf(
            listOf(
                MainCategoryOption(id = "ramen", name = "Ramen", active = true, sortOrder = 1),
                MainCategoryOption(id = "inactive_sushi", name = "Sushi", active = false, sortOrder = 2),
                MainCategoryOption(id = "others", name = "Others", active = true, sortOrder = 3)
            )
        )
        var selectedCatId by mutableStateOf<String?>(null)

        val items = listOf(
            MenuItem(
                id = "i1",
                name = "Tonkotsu Ramen",
                price = 250.0,
                categoryId = "ramen",
                category = "Ramen",
                categoryName = "Ramen",
                businessType = BusinessType.FOOD
            ),
            MenuItem(
                id = "i2",
                name = "Crunchy Taco",
                price = 120.0,
                categoryId = "deleted_tacos",
                category = "Tacos",
                categoryName = "Tacos",
                businessType = BusinessType.FOOD
            )
        )

        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                MenuScreen(
                    settings = MerchantSettings(storeName = "Test Kitchen", businessType = BusinessType.FOOD),
                    categories = emptyList(),
                    menuItems = items,
                    promos = emptyList(),
                    businessType = BusinessType.FOOD,
                    mainCategories = liveCategories,
                    currentTime = System.currentTimeMillis(),
                    searchQuery = "",
                    onSearchQueryChange = {},
                    stockFilter = "All",
                    onStockFilterChange = {},
                    selectedCategoryId = selectedCatId,
                    onCategorySelect = { selectedCatId = it },
                    onToggleItemAvailability = {},
                    onAddItem = {},
                    onEditItem = {},
                    onDuplicateItem = {}
                )
            }
        }

        // Open filter bottom sheet
        composeRule.onNodeWithTag("menu_filter_btn").performClick()
        composeRule.waitForIdle()

        // Active categories shown; inactive "Sushi" hidden
        composeRule.onNodeWithTag("main_cat_filter_ramen").assertIsDisplayed()
        composeRule.onNodeWithTag("main_cat_filter_others").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("Sushi").fetchSemanticsNodes().size)

        // Select "Others" and apply filter -> deleted-category item ("Crunchy Taco") shows, Ramen does not
        composeRule.onNodeWithTag("main_cat_filter_others").performClick()
        composeRule.onNodeWithTag("apply_menu_filters_btn").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Crunchy Taco").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Tonkotsu Ramen").fetchSemanticsNodes().isEmpty())
    }
}
