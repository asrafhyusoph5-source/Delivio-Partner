package com.example

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.example.data.model.BusinessType
import com.example.data.model.MenuItem
import com.example.ui.screens.EditableChoice
import com.example.ui.screens.EditableSet
import com.example.ui.screens.OptionSetEditorScreen
import com.example.ui.screens.ProductEditScreen
import com.example.ui.screens.buildSetLiveHint
import com.example.ui.screens.parseBulkChoicesText
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
class OptionSetEditorUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun parseBulkChoicesText_parsesFreeAndPricedLines() {
        val parsed = parseBulkChoicesText(
            """
            Original
            Spicy +7
            Extra Cheese +₱25.50
            Garlic Rice - +15
            
            """.trimIndent()
        )
        assertEquals(4, parsed.size)
        assertEquals("Original", parsed[0].name)
        assertEquals("0", parsed[0].priceText)
        assertEquals("Spicy", parsed[1].name)
        assertEquals("7", parsed[1].priceText)
        assertEquals("Extra Cheese", parsed[2].name)
        assertEquals("25.50", parsed[2].priceText)
        assertEquals("Garlic Rice", parsed[3].name)
        assertEquals("15", parsed[3].priceText)
    }

    @Test
    fun buildSetLiveHint_matchesAllRequiredAndOptionalFormats() {
        assertEquals("Select 1", buildSetLiveHint(required = true, minSelect = 1, maxSelect = 1))
        assertEquals("Select 2", buildSetLiveHint(required = true, minSelect = 2, maxSelect = 2))
        assertEquals("Select 1 to 3", buildSetLiveHint(required = true, minSelect = 1, maxSelect = 3))
        assertEquals("Optional, up to 3", buildSetLiveHint(required = false, minSelect = 0, maxSelect = 3))
    }

    @Test
    fun foodProductEdit_opensNewSetScreen_usesMinMaxSteppers_savesAndEditsSet() {
        val foodItem = MenuItem(
            id = "f100",
            name = "Chicken Wings",
            price = 199.0,
            categoryId = "mains",
            category = "Mains",
            categoryName = "Mains",
            businessType = BusinessType.FOOD
        )

        var savedProduct: MenuItem? = null

        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                ProductEditScreen(
                    item = foodItem,
                    isNew = false,
                    categories = emptyList(),
                    businessType = BusinessType.FOOD,
                    onSave = { item, cb ->
                        savedProduct = item
                        cb(true, null)
                    },
                    onDelete = {},
                    onDismiss = {}
                )
            }
        }

        // Empty state shows Drink example and "+ Add Set" button
        composeRule.onNodeWithTag("sets_empty_state_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Example: set \"Drink\" with Iced Tea (Free) and Coke (+₱10).", substring = true).assertIsDisplayed()

        // Tap "+ Add Set" to open full-screen "New Set"
        composeRule.onNodeWithTag("add_set_initial_btn").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("option_set_editor_screen").assertIsDisplayed()
        composeRule.onNodeWithText("New Set").assertIsDisplayed()

        // Deleted UI elements must not exist
        composeRule.onAllNodesWithText("How many can customers choose?").assertCountEquals(0)
        composeRule.onAllNodesWithText("Pick 1").assertCountEquals(0)
        composeRule.onAllNodesWithText("Up to 2").assertCountEquals(0)
        composeRule.onAllNodesWithText("Up to 3").assertCountEquals(0)
        composeRule.onAllNodesWithText("Any").assertCountEquals(0)
        composeRule.onAllNodesWithText("Need a higher minimum?").assertCountEquals(0)

        // Bottom CTA disabled initially with "Enter a set name"
        composeRule.onNodeWithTag("save_set_bottom_btn").assertIsNotEnabled()
        composeRule.onNodeWithText("Enter a set name").assertIsDisplayed()
        composeRule.onAllNodesWithText("Set name is required").assertCountEquals(0)

        // Default Required state: min=1, max=1, "Customers see: Select 1"
        composeRule.onNodeWithTag("rules_min_value").performScrollTo().assertTextEquals("1")
        composeRule.onNodeWithTag("rules_max_value").performScrollTo().assertTextEquals("1")
        composeRule.onNodeWithText("Customers see: Select 1").performScrollTo().assertIsDisplayed()

        // Tap top "Save" button -> triggers field validation error
        composeRule.onNodeWithTag("set_editor_top_save_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Set name is required").assertIsDisplayed()

        // Tap suggestion chip "Choice of Flavor"
        composeRule.onNodeWithTag("set_name_suggestion_0").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("16/50").assertIsDisplayed()

        // Bulk add three choices ("Original", "Spicy +7", "Garlic +10") so limit becomes 3
        composeRule.onNodeWithTag("bulk_add_choices_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("bulk_paste_input").performTextReplacement("Original\nSpicy +7\nGarlic +10")
        composeRule.onNodeWithTag("bulk_paste_confirm_btn").performClick()
        composeRule.waitForIdle()

        // Rules limit updates to 3
        composeRule.onNodeWithTag("rules_max_limit_label").performScrollTo().assertTextEquals("Limit: 3 (3 choices in set)")

        // Toggle Optional -> Minimum locked at 0, increase Maximum to 2 -> "Customers see: Optional, up to 2"
        composeRule.onNodeWithTag("rules_optional_btn").performScrollTo().performClick()
        composeRule.onNodeWithTag("rules_min_value").assertTextEquals("0")
        composeRule.onNodeWithTag("rules_min_decrease").assertIsNotEnabled()
        composeRule.onNodeWithTag("rules_min_increase").assertIsNotEnabled()
        composeRule.onNodeWithTag("rules_max_increase").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Customers see: Optional, up to 2").performScrollTo().assertIsDisplayed()

        // Increase Max again -> Optional, up to 3, and + is now disabled at limit 3
        composeRule.onNodeWithTag("rules_max_increase").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Customers see: Optional, up to 3").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("rules_max_increase").assertIsNotEnabled()

        // Toggle Popular on choice 1 (Spicy)
        composeRule.onNodeWithTag("choice_popular_btn_1").performScrollTo().performClick()
        composeRule.waitForIdle()

        // Check Live Preview card renders customer view
        composeRule.onNodeWithTag("set_live_preview_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("How customers will see this").assertIsDisplayed()
        composeRule.onNodeWithText("Optional, up to 3").assertIsDisplayed()

        // Bottom "Save set" should now be enabled
        composeRule.onNodeWithTag("save_set_bottom_btn").assertIsEnabled()
        composeRule.onNodeWithTag("save_set_bottom_btn").performClick()
        composeRule.waitForIdle()

        // Back in ProductEditScreen: compact summary card is shown
        composeRule.onAllNodesWithTag("option_set_editor_screen").assertCountEquals(0)
        composeRule.onNodeWithTag("set_card_0").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("set_summary_rule_0", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Optional - Up to 3").performScrollTo().assertIsDisplayed()

        // Tap Edit on set 0 -> opens "Edit Set" with old values (Optional, min=0, max=3)
        composeRule.onNodeWithTag("edit_set_btn_0").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Edit Set").assertIsDisplayed()
        composeRule.onNodeWithTag("rules_min_value").performScrollTo().assertTextEquals("0")
        composeRule.onNodeWithTag("rules_max_value").performScrollTo().assertTextEquals("3")

        // Switch to Required -> min=1, max=3 -> "Customers see: Select 1 to 3"
        composeRule.onNodeWithTag("rules_required_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Customers see: Select 1 to 3").performScrollTo().assertIsDisplayed()

        // Raise min to 2 and decrease max to 2 -> "Customers see: Select 2"
        composeRule.onNodeWithTag("rules_min_increase").performScrollTo().performClick()
        composeRule.onNodeWithTag("rules_max_decrease").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Customers see: Select 2").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("rules_max_decrease").assertIsNotEnabled()

        composeRule.onNodeWithTag("save_set_bottom_btn").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Required - Select 2").performScrollTo().assertIsDisplayed()

        // Save Product and verify saved MenuItem preserves exact ProductSet schema
        composeRule.onNodeWithTag("save_menu_item_btn").performClick()
        composeRule.waitForIdle()

        assertNotNull(savedProduct)
        assertTrue(savedProduct!!.hasSets)
        assertEquals(1, savedProduct!!.sets.size)
        val savedSet = savedProduct!!.sets[0]
        assertEquals("Choice of Flavor", savedSet.name)
        assertEquals("Choice of Flavor", savedSet.customName)
        assertTrue(savedSet.required)
        assertEquals(2, savedSet.minSelect)
        assertEquals(2, savedSet.maxSelect)
        assertEquals(3, savedSet.choices.size)
        assertEquals("Original", savedSet.choices[0].name)
        assertEquals("Spicy", savedSet.choices[1].name)
        assertEquals(7.0, savedSet.choices[1].price, 0.01)
        assertTrue(savedSet.choices[1].popular)
    }

    @Test
    fun editOldSet_opensWithOldValues_andClampsWhenChoiceDeleted() {
        val oldSet = EditableSet(
            id = "set_old",
            initialName = "Toppings",
            initialRequired = true,
            initialMinSelect = 2,
            initialMaxSelect = 3,
            initialChoices = listOf(
                EditableChoice(initialName = "Cheese", initialPriceText = "10"),
                EditableChoice(initialName = "Bacon", initialPriceText = "20"),
                EditableChoice(initialName = "Mushroom", initialPriceText = "15")
            )
        )

        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                OptionSetEditorScreen(
                    initialSet = oldSet,
                    isNew = false,
                    existingSetNames = emptySet(),
                    onSave = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("Edit Set").assertIsDisplayed()
        composeRule.onNodeWithTag("rules_min_value").performScrollTo().assertTextEquals("2")
        composeRule.onNodeWithTag("rules_max_value").performScrollTo().assertTextEquals("3")
        composeRule.onNodeWithText("Customers see: Select 2 to 3").performScrollTo().assertIsDisplayed()

        // Delete third choice -> max clamps from 3 to 2, min stays 2 -> "Customers see: Select 2"
        composeRule.onNodeWithTag("choice_delete_btn_2").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("rules_max_limit_label").performScrollTo().assertTextEquals("Limit: 2 (2 choices in set)")
        composeRule.onNodeWithTag("rules_max_value").assertTextEquals("2")
        composeRule.onNodeWithText("Customers see: Select 2").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun setEditor_checksDuplicateSetName_andDuplicateChoiceWarning() {
        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                OptionSetEditorScreen(
                    initialSet = null,
                    isNew = true,
                    existingSetNames = setOf("Drink"),
                    onSave = {},
                    onBack = {}
                )
            }
        }

        // Duplicate set name blocks save
        composeRule.onNodeWithTag("set_name_input").performTextReplacement("drink")
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Set name must be unique").assertCountEquals(2)
        composeRule.onNodeWithTag("save_set_bottom_btn").assertIsNotEnabled()

        // Unique name + duplicate choice names show warning banner
        composeRule.onNodeWithTag("set_name_input").performTextReplacement("Sides")
        composeRule.onNodeWithTag("choice_name_input_0").performScrollTo().performTextReplacement("Fries")
        composeRule.onNodeWithTag("add_choice_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("choice_name_input_1").performScrollTo().performTextReplacement("fries")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("duplicate_choice_warning").performScrollTo().assertIsDisplayed()
    }
}
