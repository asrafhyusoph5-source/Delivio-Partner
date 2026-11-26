package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.model.DayHours
import com.example.data.model.ManualOverride
import com.example.data.model.MerchantSettings
import com.example.ui.components.DelivioTopBar
import com.example.ui.components.MerchantRightDrawer
import com.example.ui.theme.DelivioPartnerTheme
import com.example.util.BusinessHoursHelper
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h850dp-xxhdpi")
class StoreStatusTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val manilaZone = ZoneId.of("Asia/Manila")

    // Sunday 2026-11-08 11:00 Asia/Manila
    private val sun11am = ZonedDateTime.of(2026, 11, 8, 11, 0, 0, 0, manilaZone).toInstant().toEpochMilli()
    // Sunday 2026-11-08 02:00 Asia/Manila (inside Saturday overnight 20:00..04:00)
    private val sun2am = ZonedDateTime.of(2026, 11, 8, 2, 0, 0, 0, manilaZone).toInstant().toEpochMilli()
    // Sunday 2026-11-08 06:00 Asia/Manila (outside both Saturday 20:00..04:00 and Sunday 09:00..21:00)
    private val sun6am = ZonedDateTime.of(2026, 11, 8, 6, 0, 0, 0, manilaZone).toInstant().toEpochMilli()

    private val sampleHours = mapOf(
        "mon" to DayHours("08:00", "22:00", false),
        "tue" to DayHours("08:00", "22:00", false),
        "wed" to DayHours("08:00", "22:00", false),
        "thu" to DayHours("08:00", "22:00", false),
        "fri" to DayHours("08:00", "22:00", false),
        "sat" to DayHours("20:00", "04:00", false), // overnight
        "sun" to DayHours("09:00", "21:00", false)
    )

    @Test
    fun storeStatus_priorityOrder_suspendedAndAdminLockOverrideEverything() {
        val openOverride = ManualOverride(state = "open", until = sun11am + 3_600_000L)

        // 1. Suspended wins even when adminLock is false and manualOverride is open
        val suspendedMerchant = MerchantSettings(
            status = "suspended",
            adminLock = true,
            manualOverride = openOverride,
            autoSchedule = true,
            isOpen = true,
            hours = sampleHours
        )
        val suspendedResult = BusinessHoursHelper.storeStatus(suspendedMerchant, sun11am)
        assertFalse(suspendedResult.open)
        assertEquals("Suspended", suspendedResult.reason)
        assertNull(suspendedResult.nextChange)

        // 2. adminLock wins over active manualOverride("open"), autoSchedule, and isOpen
        val lockedMerchant = MerchantSettings(
            status = "active",
            adminLock = true,
            manualOverride = openOverride,
            autoSchedule = true,
            isOpen = true,
            hours = sampleHours
        )
        val lockedResult = BusinessHoursHelper.storeStatus(lockedMerchant, sun11am)
        assertFalse(lockedResult.open)
        assertEquals("Closed by Delivio", lockedResult.reason)
        assertNull(lockedResult.nextChange)
        assertFalse(BusinessHoursHelper.isEffectiveOpen(lockedMerchant, sun11am))
    }

    @Test
    fun storeStatus_manualOverrideAppliesBeforeUntilAndFallsThroughAfterExpiry() {
        val futureUntil = sun11am + 1_800_000L
        val pastUntil = sun11am - 1_000L

        // Active manualOverride("closed") during open scheduled hours -> Closed
        val forcedClosed = MerchantSettings(
            status = "active",
            adminLock = false,
            manualOverride = ManualOverride(state = "closed", until = futureUntil),
            autoSchedule = true,
            hours = sampleHours
        )
        val closedRes = BusinessHoursHelper.storeStatus(forcedClosed, sun11am)
        assertFalse(closedRes.open)
        assertEquals(futureUntil, closedRes.nextChange)

        // Active manualOverride("open") outside scheduled hours -> Open
        val forcedOpen = MerchantSettings(
            status = "active",
            adminLock = false,
            manualOverride = ManualOverride(state = "open", until = sun6am + 3_600_000L),
            autoSchedule = true,
            hours = sampleHours
        )
        val openRes = BusinessHoursHelper.storeStatus(forcedOpen, sun6am)
        assertTrue(openRes.open)
        assertEquals(sun6am + 3_600_000L, openRes.nextChange)

        // Expired manualOverride("closed") falls through to Step 4 autoSchedule (open at sun11am)
        val expiredOverride = forcedClosed.copy(
            manualOverride = ManualOverride(state = "closed", until = pastUntil)
        )
        val fallthroughRes = BusinessHoursHelper.storeStatus(expiredOverride, sun11am)
        assertTrue(fallthroughRes.open)
    }

    @Test
    fun storeStatus_autoScheduleOvernightAndNextOpenAndManualFallback() {
        val autoMerchant = MerchantSettings(
            status = "active",
            autoSchedule = true,
            isOpen = false,
            hours = sampleHours
        )

        // Inside yesterday's overnight shift (Sunday 02:00 AM inside Sat 20:00..04:00)
        val overnightRes = BusinessHoursHelper.storeStatus(autoMerchant, sun2am)
        assertTrue(overnightRes.open)
        val expectedSun4am = ZonedDateTime.of(2026, 11, 8, 4, 0, 0, 0, manilaZone).toInstant().toEpochMilli()
        assertEquals(expectedSun4am, overnightRes.nextChange)

        // Outside schedule at Sunday 06:00 AM -> closed with next opening = Sunday 09:00 AM
        val beforeOpenRes = BusinessHoursHelper.storeStatus(autoMerchant, sun6am)
        assertFalse(beforeOpenRes.open)
        val expectedSun9am = ZonedDateTime.of(2026, 11, 8, 9, 0, 0, 0, manilaZone).toInstant().toEpochMilli()
        assertEquals(expectedSun9am, beforeOpenRes.nextChange)

        // Step 5: autoSchedule = false -> manual switch (isOpen) decides
        val manualOpen = MerchantSettings(
            status = "active",
            autoSchedule = false,
            isOpen = true,
            hours = sampleHours
        )
        assertTrue(BusinessHoursHelper.storeStatus(manualOpen, sun6am).open)
        assertFalse(BusinessHoursHelper.storeStatus(manualOpen.copy(isOpen = false), sun11am).open)
    }

    @Test
    fun nextClosingTimestampForOverride_alwaysReturnsFutureTimestamp() {
        val noHoursMerchant = MerchantSettings(
            status = "active",
            autoSchedule = false,
            hours = emptyMap()
        )
        val nextClose = BusinessHoursHelper.getNextClosingTimestampForOverride(noHoursMerchant, sun11am)
        assertTrue(nextClose > sun11am)
    }

    @Test
    fun topBarAndDrawer_liveUpdateAndAdminLockBehavior() {
        var liveSettings by mutableStateOf(
            MerchantSettings(
                merchantId = "shop_1",
                storeName = "Kape Delivio",
                status = "active",
                adminLock = false,
                autoSchedule = true,
                hours = sampleHours
            )
        )
        var toggleClicks = 0
        var contactClicks = 0

        composeRule.setContent {
            DelivioPartnerTheme {
                DelivioTopBar(
                    settings = liveSettings,
                    onToggleOpen = { toggleClicks++ },
                    currentTime = sun11am,
                    onContactSupport = { contactClicks++ }
                )
            }
        }

        // 1. Open + Auto initially
        composeRule.onNodeWithTag("topbar_status_chip").assertIsEnabled()
        composeRule.onNodeWithText("Open").assertIsDisplayed()
        composeRule.onNodeWithTag("topbar_auto_chip").assertIsDisplayed()
        composeRule.onNodeWithTag("topbar_status_chip").performClick()
        assertEquals(1, toggleClicks)

        // 2. Live admin override (manualOverride = closed) updates UI without restart
        liveSettings = liveSettings.copy(
            manualOverride = ManualOverride(state = "closed", until = sun11am + 3_600_000L)
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Closed").assertIsDisplayed()

        // 3. Admin sets adminLock = true -> "Closed by Delivio", toggle disabled, "Contact support" shown
        liveSettings = liveSettings.copy(adminLock = true, manualOverride = null)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Closed by Delivio").assertIsDisplayed()
        composeRule.onNodeWithTag("topbar_status_chip").assertIsNotEnabled()
        composeRule.onNodeWithTag("topbar_contact_support_btn").assertIsDisplayed().performClick()
        assertEquals(1, contactClicks)
    }

    @Test
    fun drawer_disablesSwitchAndShowsContactSupportOnAdminLock() {
        var liveSettings by mutableStateOf(
            MerchantSettings(
                merchantId = "shop_1",
                storeName = "Kape Delivio",
                status = "active",
                adminLock = false,
                autoSchedule = true,
                hours = sampleHours
            )
        )
        var toggleCalls = 0
        var helpCalls = 0

        composeRule.setContent {
            DelivioPartnerTheme {
                MerchantRightDrawer(
                    settings = liveSettings,
                    currentTime = sun11am,
                    isServiceLocked = false,
                    unreadInboxCount = 0,
                    newOrdersCount = 0,
                    averageRating = 4.9,
                    reviewsCount = 12,
                    walletBalance = 500.0,
                    onToggleOpen = { toggleCalls++ },
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
                    onOpenHelp = { helpCalls++ },
                    onLogout = {}
                )
            }
        }

        composeRule.onNodeWithText("Store: Open").assertIsDisplayed()
        composeRule.onNodeWithTag("drawer_auto_chip").assertIsDisplayed()
        composeRule.onNodeWithTag("drawer_open_switch").assertIsEnabled().assertIsOn()

        liveSettings = liveSettings.copy(adminLock = true)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Closed by Delivio").assertIsDisplayed()
        composeRule.onNodeWithTag("drawer_open_switch").assertIsNotEnabled().assertIsOff()
        composeRule.onNodeWithTag("drawer_contact_support_btn").assertIsDisplayed().performClick()
        assertEquals(1, helpCalls)
        assertEquals(0, toggleCalls)
    }
}
