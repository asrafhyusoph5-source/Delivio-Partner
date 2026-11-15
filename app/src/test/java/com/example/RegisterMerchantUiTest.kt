package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.model.CityOption
import com.example.ui.screens.RegisterMerchantScreen
import com.example.ui.theme.DelivioPartnerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h850dp-xxhdpi")
class RegisterMerchantUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun registerMerchant_hasNoStorefrontPhotoUrl_showsCityDropdownAndRequiresPinConfirm() {
        val sampleCities = listOf(
            CityOption(id = "city_ppc", name = "Puerto Princesa", lat = 9.7392, lng = 118.7353, active = true),
            CityOption(id = "city_cebu", name = "Cebu City", lat = 10.3157, lng = 123.8854, active = true)
        )
        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                RegisterMerchantScreen(
                    onBackToLogin = {},
                    onSubmit = { _, _ -> },
                    cities = sampleCities,
                    isLoadingCities = false,
                    citiesError = null
                )
            }
        }

        // Storefront Photo URL is completely gone
        assertEquals(0, composeRule.onAllNodesWithText("Storefront Photo URL", substring = true).fetchSemanticsNodes().size)

        // City dropdown is shown
        composeRule.onNodeWithTag("reg_city_dropdown").performScrollTo().assertIsDisplayed()

        // Use current location and Confirm pin buttons exist, Pinned GPS is NOT shown yet
        composeRule.onNodeWithTag("reg_use_current_location_btn").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("reg_confirm_pin_btn").performScrollTo().assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("reg_pinned_gps_text").fetchSemanticsNodes().size)

        // Tapping Confirm pin confirms the pin and shows "Pinned GPS"
        composeRule.onNodeWithTag("reg_confirm_pin_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reg_pinned_gps_text").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun registerMerchant_citiesErrorShowsRetryButton() {
        var retried by mutableStateOf(false)
        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                RegisterMerchantScreen(
                    onBackToLogin = {},
                    onSubmit = { _, _ -> },
                    cities = emptyList(),
                    isLoadingCities = false,
                    citiesError = "Failed to load cities.",
                    onRetryCities = { retried = true }
                )
            }
        }

        composeRule.onNodeWithTag("reg_cities_retry_btn").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(retried)
    }
}
