package com.example

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.example.data.model.BusinessType
import com.example.data.model.MenuItem
import com.example.ui.screens.OptionSetEditorScreen
import com.example.ui.screens.ProductEditScreen
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
    fun foodProductEdit_opensNewSetScreen_savesCompactSummaryCard_supportsEditDuplicateMoveDelete() {
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

        // Bottom CTA disabled initially with reason, and errors do NOT show in fields yet
        composeRule.onNodeWithTag("save_set_bottom_btn").assertIsNotEnabled()
        composeRule.onNodeWithTag("set_editor_missing_reason").assertIsDisplayed()
        composeRule.onAllNodesWithText("Set name is required").assertCountEquals(0)

        // Tap top "Save" button -> triggers field validation errors after interaction
        composeRule.onNodeWithTag("set_editor_top_save_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Set name is required").assertIsDisplayed()

        // Tap suggestion chip "Choice of Flavor"
        composeRule.onNodeWithTag("set_name_suggestion_0").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("16/50").assertIsDisplayed()

        // Rules: toggle Optional + Up to 2 + reveal higher minimum
        composeRule.onNodeWithTag("rules_optional_btn").performScrollTo().performClick()
        composeRule.onNodeWithTag("rule_chip_up_to_2").performScrollTo().performClick()
        composeRule.onNodeWithText("Customers see: Select up to 2").performScrollTo().assertIsDisplayed()

        // Bulk add two choices ("Original" and "Spicy +7")
        composeRule.onNodeWithTag("bulk_add_choices_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("bulk_paste_input").performTextReplacement("Original\nSpicy +7")
        composeRule.onNodeWithTag("bulk_paste_confirm_btn").performClick()
        composeRule.waitForIdle()

        // Toggle Popular on choice 1 (Spicy)
        composeRule.onNodeWithTag("choice_popular_btn_1").performScrollTo().performClick()
        composeRule.waitForIdle()

        // Check Live Preview card renders customer view
        composeRule.onNodeWithTag("set_live_preview_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("How customers will see this").assertIsDisplayed()
        composeRule.onNodeWithText("Select up to 2").assertIsDisplayed()

        // Bottom "Save set" should now be enabled
        composeRule.onNodeWithTag("save_set_bottom_btn").assertIsEnabled()
        composeRule.onNodeWithTag("save_set_bottom_btn").performClick()
        composeRule.waitForIdle()

        // Back in ProductEditScreen: compact summary card is shown (not full inline editor)
        composeRule.onAllNodesWithTag("option_set_editor_screen").assertCountEquals(0)
        composeRule.onNodeWithTag("set_card_0").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("set_summary_rule_0", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Optional - Select up to 2").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("add_another_set_btn").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("+ Add Set (1/12)").performScrollTo().assertIsDisplayed()

        // Duplicate set -> creates Copy as set 1, test Move Up / Delete confirmation
        composeRule.onNodeWithTag("duplicate_set_btn_0").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("set_card_1").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Choice of Flavor (Copy)").performScrollTo().assertIsDisplayed()

        // Delete copy with confirmation dialog
        composeRule.onNodeWithTag("delete_set_btn_1").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Delete Set 2?").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("set_card_1").assertCountEquals(0)

        // Tap Edit on set 0 -> opens "Edit Set"
        composeRule.onNodeWithTag("edit_set_btn_0").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Edit Set").assertIsDisplayed()
        composeRule.onNodeWithTag("rules_required_btn").performScrollTo().performClick()
        composeRule.onNodeWithTag("rule_chip_pick_1").performScrollTo().performClick()
        composeRule.onNodeWithTag("save_set_bottom_btn").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("set_summary_rule_0", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Required - Pick 1").performScrollTo().assertIsDisplayed()

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
        assertEquals(1, savedSet.minSelect)
        assertEquals(1, savedSet.maxSelect)
        assertEquals(2, savedSet.choices.size)
        assertEquals("Original", savedSet.choices[0].name)
        assertEquals(0.0, savedSet.choices[0].price, 0.01)
        assertFalse(savedSet.choices[0].popular)
        assertEquals("Spicy", savedSet.choices[1].name)
        assertEquals(7.0, savedSet.choices[1].price, 0.01)
        assertTrue(savedSet.choices[1].popular)
    }

    @Test
    fun setEditor_checksDuplicateSetName_andDuplicateChoiceWarning_andHigherMinimum() {
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

        // Unique name + Up to 3 + higher minimum (2) -> "Customers see: Select 2 to 3"
        composeRule.onNodeWithTag("set_name_input").performTextReplacement("Sides")
        composeRule.onNodeWithTag("rule_chip_up_to_3").performScrollTo().performClick()
        composeRule.onNodeWithTag("toggle_higher_min_btn").performScrollTo().performClick()
        composeRule.onNodeWithTag("rules_min_increase").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Customers see: Select 2 to 3").performScrollTo().assertIsDisplayed()

        // Duplicate choice names show warning banner
        composeRule.onNodeWithTag("choice_name_input_0").performScrollTo().performTextReplacement("Fries")
        composeRule.onNodeWithTag("add_choice_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("choice_name_input_1").performScrollTo().performTextReplacement("fries")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("duplicate_choice_warning").performScrollTo().assertIsDisplayed()
    }
}
