package com.example

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.example.data.model.BusinessType
import com.example.data.model.MenuItem
import com.example.data.model.ShopMenu
import com.example.ui.screens.MenuEditScreen
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
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xxhdpi")
class DayChipsAndAvailabilityUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val dayTags = listOf(
        "day_chip_mon",
        "day_chip_tue",
        "day_chip_wed",
        "day_chip_thu",
        "day_chip_fri",
        "day_chip_sat",
        "day_chip_sun"
    )

    @Test
    fun addProduct_allSevenDayChipsVisibleOn360dpAndLargeFont_andSaveReloadSatSun() {
        var savedItem by mutableStateOf<MenuItem?>(null)
        var editorOpen by mutableStateOf(true)
        var currentItem by mutableStateOf(
            MenuItem(
                id = "prod_1",
                name = "Weekend Burger",
                price = 150.0,
                categoryName = "Burgers",
                categoryId = "burgers"
            )
        )

        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = LocalDensity.current.density, fontScale = 2.0f)
            ) {
                DelivioPartnerTheme(darkTheme = false) {
                    if (editorOpen) {
                        ProductEditScreen(
                            item = currentItem,
                            isNew = savedItem == null,
                            categories = emptyList(),
                            menus = listOf(ShopMenu(id = "main", name = "Main Menu", active = true)),
                            businessType = BusinessType.FOOD,
                            onSave = { updated, onComplete ->
                                savedItem = updated
                                currentItem = updated
                                editorOpen = false
                                onComplete(true, null)
                            },
                            onDelete = {},
                            onDismiss = { editorOpen = false }
                        )
                    }
                }
            }
        }

        // Switch to "Set days and times"
        composeRule.onNodeWithText("Set days and times").performScrollTo().performClick()
        composeRule.waitForIdle()

        val rootBounds = composeRule.onNodeWithTag("product_edit_screen").fetchSemanticsNode().boundsInRoot

        // Assert all 7 day chips (Mon..Sun) are visible and not clipped off screen
        dayTags.forEach { tag ->
            val node = composeRule.onNodeWithTag(tag)
            node.performScrollTo()
            node.assertIsDisplayed()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue("$tag right edge clipped: ${bounds.right} > ${rootBounds.right}", bounds.right <= rootBounds.right + 1f)
            assertTrue("$tag left edge clipped: ${bounds.left} < ${rootBounds.left}", bounds.left >= rootBounds.left - 1f)
        }

        // Quick chip Every day starts selected; tapping Sun deselects ONLY Sun
        composeRule.onNodeWithTag("quick_chip_every_day").assertIsSelected()
        composeRule.onNodeWithTag("day_chip_sun").performScrollTo().performClick()
        composeRule.onNodeWithTag("day_chip_sun").assertIsNotSelected()
        composeRule.onNodeWithTag("day_chip_sat").assertIsSelected()
        composeRule.onNodeWithTag("quick_chip_every_day").assertIsNotSelected()

        // Deselect remaining days -> Save disabled and error text shown
        listOf("mon", "tue", "wed", "thu", "fri", "sat").forEach { key ->
            composeRule.onNodeWithTag("day_chip_$key").performScrollTo().performClick()
        }
        composeRule.onNodeWithTag("product_avail_days_error").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Choose at least one day").assertCountEquals(2)
        composeRule.onNodeWithTag("save_menu_item_btn").assertIsNotEnabled()

        // Select ONLY Sat and Sun
        composeRule.onNodeWithTag("day_chip_sat").performScrollTo().performClick()
        composeRule.onNodeWithTag("day_chip_sun").performScrollTo().performClick()
        composeRule.onNodeWithTag("quick_chip_weekends").assertIsSelected()

        // Save
        composeRule.onNodeWithTag("save_menu_item_btn").assertIsEnabled().performClick()
        composeRule.waitForIdle()

        val saved = savedItem
        assertNotNull(saved)
        assertEquals(listOf("sat", "sun"), saved!!.availability?.days)

        // Reopen with saved item
        editorOpen = true
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("day_chip_sat").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("day_chip_sun").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("quick_chip_weekends").assertIsSelected()
        listOf("mon", "tue", "wed", "thu", "fri").forEach { key ->
            composeRule.onNodeWithTag("day_chip_$key").assertIsNotSelected()
        }
    }

    @Test
    fun addNewMenu_allSevenDayChipsVisibleOn360dpAndLargeFont_andSaveReloadSatSun() {
        var savedMenu by mutableStateOf<ShopMenu?>(null)
        var editorOpen by mutableStateOf(true)
        var currentMenu by mutableStateOf(
            ShopMenu(
                id = "weekend_menu",
                name = "Weekend Specials",
                active = true,
                schedule = null
            )
        )

        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = LocalDensity.current.density, fontScale = 2.0f)
            ) {
                DelivioPartnerTheme(darkTheme = false) {
                    if (editorOpen) {
                        MenuEditScreen(
                            menu = currentMenu,
                            isNew = savedMenu == null,
                            allMenus = emptyList(),
                            menuItems = emptyList(),
                            onSave = { updatedMenu, _, onComplete ->
                                savedMenu = updatedMenu
                                currentMenu = updatedMenu
                                editorOpen = false
                                onComplete(true, null)
                            },
                            onBack = { editorOpen = false }
                        )
                    }
                }
            }
        }

        // Enable schedule switch
        composeRule.onNodeWithTag("menu_schedule_toggle").performClick()
        composeRule.waitForIdle()

        val rootBounds = composeRule.onNodeWithTag("menu_edit_screen").fetchSemanticsNode().boundsInRoot

        // Assert all 7 day chips (Mon..Sun) are visible and not clipped off screen
        dayTags.forEach { tag ->
            val node = composeRule.onNodeWithTag(tag)
            node.performScrollTo()
            node.assertIsDisplayed()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue("$tag right edge clipped: ${bounds.right} > ${rootBounds.right}", bounds.right <= rootBounds.right + 1f)
            assertTrue("$tag left edge clipped: ${bounds.left} < ${rootBounds.left}", bounds.left >= rootBounds.left - 1f)
        }

        // Deselect all days -> Save disabled and "Choose at least one day" shown
        listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun").forEach { key ->
            composeRule.onNodeWithTag("day_chip_$key").performScrollTo().performClick()
        }
        composeRule.onNodeWithTag("menu_avail_days_error").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("save_menu_top_btn").assertIsNotEnabled()

        // Select only Sat & Sun
        composeRule.onNodeWithTag("day_chip_sat").performScrollTo().performClick()
        composeRule.onNodeWithTag("day_chip_sun").performScrollTo().performClick()
        composeRule.onNodeWithTag("quick_chip_weekends").assertIsSelected()

        // Save
        composeRule.onNodeWithTag("save_menu_top_btn").assertIsEnabled().performClick()
        composeRule.waitForIdle()

        val saved = savedMenu
        assertNotNull(saved)
        assertEquals(listOf("sat", "sun"), saved!!.schedule?.days)

        // Reopen with saved menu
        editorOpen = true
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("day_chip_sat").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("day_chip_sun").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("quick_chip_weekends").assertIsSelected()
        listOf("mon", "tue", "wed", "thu", "fri").forEach { key ->
            composeRule.onNodeWithTag("day_chip_$key").assertIsNotSelected()
        }
    }
}
