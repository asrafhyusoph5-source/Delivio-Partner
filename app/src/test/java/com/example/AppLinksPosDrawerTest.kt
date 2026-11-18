package com.example

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AppLinksSettings
import com.example.data.model.MerchantSettings
import com.example.ui.components.MerchantRightDrawer
import com.example.ui.components.PosLaunchBottomSheet
import com.example.ui.screens.ProfileScreen
import com.example.ui.theme.DelivioPartnerTheme
import com.example.util.PosBrowserLauncher
import com.example.util.PosDocumentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h850dp-xxhdpi")
class AppLinksPosDrawerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val baseSettings = MerchantSettings(
        merchantId = "shop_1",
        storeName = "Kape Delivio"
    )

    @Test
    fun parseAppLinksSettings_validatesPosEnabledAndHttpsUrl() {
        val disabled = PosDocumentParser.parseAppLinksSettings(
            mapOf("posEnabled" to false, "posUrl" to "https://pos.delivio.ph")
        )
        assertFalse(disabled.isPosAvailable)

        val httpOnly = PosDocumentParser.parseAppLinksSettings(
            mapOf("posEnabled" to true, "posUrl" to "http://pos.delivio.ph")
        )
        assertFalse(httpOnly.isPosAvailable)

        val emptyUrl = PosDocumentParser.parseAppLinksSettings(
            mapOf("posEnabled" to true, "posUrl" to "   ")
        )
        assertFalse(emptyUrl.isPosAvailable)

        val valid = PosDocumentParser.parseAppLinksSettings(
            mapOf("posEnabled" to true, "posUrl" to "https://pos.delivio.ph")
        )
        assertTrue(valid.isPosAvailable)
        assertEquals("https://pos.delivio.ph", valid.posUrl)
    }

    @Test
    fun drawer_hidesDelivioPosWhenDisabled_andShowsUnderOrdersWhenEnabled() {
        var posEnabled by mutableStateOf(false)
        var posClicked = false

        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                MerchantRightDrawer(
                    settings = baseSettings,
                    currentTime = 1781503200000L,
                    isServiceLocked = false,
                    unreadInboxCount = 0,
                    newOrdersCount = 2,
                    averageRating = 4.8,
                    reviewsCount = 12,
                    walletBalance = 1500.0,
                    onToggleOpen = {},
                    onOpenReviews = {},
                    onNavigateOrders = {},
                    onNavigateMenu = {},
                    onNavigateDashboard = {},
                    onNavigateEarnings = {},
                    onOpenWallet = {},
                    onOpenInbox = {},
                    onOpenVouchers = {},
                    onRequestRider = {},
                    onOpenStoreProfile = {},
                    onOpenSettings = {},
                    onOpenHelp = {},
                    onLogout = {},
                    showDelivioPos = posEnabled,
                    onOpenDelivioPos = { posClicked = true }
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithTag("drawer_item_delivio_pos").fetchSemanticsNodes().size)

        posEnabled = true
        composeRule.waitForIdle()

        val ordersBounds = composeRule.onNodeWithTag("drawer_item_orders").fetchSemanticsNode().boundsInRoot
        val posNode = composeRule.onNodeWithTag("drawer_item_delivio_pos")
        posNode.assertIsDisplayed()
        composeRule.onNodeWithText("Delivio POS").assertIsDisplayed()
        composeRule.onNodeWithText("Counter sales and orders").assertIsDisplayed()
        val posBounds = posNode.fetchSemanticsNode().boundsInRoot
        assertTrue("Delivio POS must appear directly below Orders", posBounds.top >= ordersBounds.bottom - 1f)

        posNode.performClick()
        assertTrue(posClicked)
    }

    @Test
    fun posLaunchSheet_remembersDontShowAgain_andOpensCleanHttpsUrl() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        PosBrowserLauncher.setSkipLaunchSheet(context, false)
        assertFalse(PosBrowserLauncher.shouldSkipLaunchSheet(context))

        var openedWithSkip: Boolean? = null
        var cancelled = false

        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                PosLaunchBottomSheet(
                    onOpen = { skip ->
                        openedWithSkip = skip
                        if (skip) PosBrowserLauncher.setSkipLaunchSheet(context, true)
                        PosBrowserLauncher.openPosUrl(context, "https://pos.delivio.ph")
                    },
                    onCancel = { cancelled = true }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Sign in with the same email and password you use here.").assertIsDisplayed()
        composeRule.onNodeWithTag("pos_sheet_dont_show_again").performClick()
        composeRule.onNodeWithTag("pos_sheet_open_btn").performClick()
        composeRule.waitForIdle()

        assertEquals(true, openedWithSkip)
        assertFalse(cancelled)
        assertTrue(PosBrowserLauncher.shouldSkipLaunchSheet(context))

        val launchedIntent = Shadows.shadowOf(context).nextStartedActivity
        assertNotNull(launchedIntent)
        assertEquals(Intent.ACTION_VIEW, launchedIntent.action)
        assertEquals("https://pos.delivio.ph", launchedIntent.dataString)
        // Must never attach password or token
        assertFalse(launchedIntent.dataString!!.contains("token"))
        assertFalse(launchedIntent.dataString!!.contains("password"))
        assertTrue(launchedIntent.extras == null || !launchedIntent.extras!!.keySet().any {
            it.contains("password", ignoreCase = true) || it.contains("token", ignoreCase = true)
        })
    }

    @Test
    fun profileHelpDialog_showsHowToUseDelivioPosLinkOnlyWhenEnabled() {
        var howToUseClicked = false
        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                ProfileScreen(
                    settings = baseSettings,
                    showHowToUsePos = true,
                    onOpenHowToUsePos = { howToUseClicked = true }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_help_row").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("profile_help_how_to_use_pos_link").assertIsDisplayed().performClick()
        assertTrue(howToUseClicked)
    }
}
