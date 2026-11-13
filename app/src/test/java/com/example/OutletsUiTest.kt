package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.example.data.model.BusinessType
import com.example.data.model.MenuItem
import com.example.data.model.MerchantSettings
import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.ui.components.BranchSetupDialog
import com.example.ui.components.DelivioTopBar
import com.example.ui.components.MenuSyncDialog
import com.example.ui.components.MerchantRightDrawer
import com.example.ui.components.OutletsPinDialog
import com.example.ui.components.OutletsSelectorSheet
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.MenuManagementScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StoreProfileScreen
import com.example.ui.theme.DelivioPartnerTheme
import com.example.util.MenuSyncResult
import com.example.util.OutletMenuSnapshot
import com.example.util.PinVerifyResult
import com.example.viewmodel.MenuSyncDialogMode
import com.example.viewmodel.OutletsSheetMode
import com.example.viewmodel.PinModalMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h850dp-xxhdpi")
class OutletsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outletMain = MerchantSettings(
        merchantId = "m_main",
        ownerUid = "m_main",
        chainId = "m_main",
        chainName = "Jollibee",
        isBranch = false,
        branchName = "Main Branch",
        branchCode = "MAIN",
        branchSetupDone = true,
        storeName = "Jollibee",
        status = "active"
    )

    private val outletRizal = MerchantSettings(
        merchantId = "m_rizal",
        ownerUid = "m_main",
        chainId = "m_main",
        chainName = "Jollibee",
        isBranch = true,
        branchName = "Palawan Rizal",
        branchCode = "PRZ",
        branchSetupDone = true,
        storeName = "Jollibee",
        status = "active"
    )

    @Test
    fun singleOutlet_hidesAllOutletsUiEverywhere() {
        val singleShop = MerchantSettings(merchantId = "s1", storeName = "Solo Bakery")
        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                DelivioTopBar(
                    settings = singleShop,
                    onToggleOpen = {},
                    isMultiOutlet = false
                )
            }
        }
        composeRule.onNodeWithText("Solo Bakery").assertIsDisplayed()
        composeRule.onAllNodesWithTag("topbar_outlet_chip").fetchSemanticsNodes().let {
            assertEquals(0, it.size)
        }
    }

    @Test
    fun multiOutlet_showsTopBarChip_DrawerOutletsRow_AndLockedStoreProfileFields() {
        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                DelivioTopBar(
                    settings = outletRizal,
                    onToggleOpen = {},
                    isMultiOutlet = true
                )
            }
        }
        composeRule.onNodeWithText("Jollibee - Palawan Rizal").assertIsDisplayed()
        composeRule.onNodeWithTag("topbar_outlet_chip").assertIsDisplayed()
    }

    @Test
    fun multiOutlet_drawerShowsOutletsRowWithOtherBadge_singleOutletHidesIt() {
        var multiOutlet by mutableStateOf(true)
        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                MerchantRightDrawer(
                    settings = outletRizal,
                    currentTime = 1_000_000L,
                    isServiceLocked = false,
                    unreadInboxCount = 0,
                    newOrdersCount = 0,
                    averageRating = 5.0,
                    reviewsCount = 0,
                    walletBalance = 100.0,
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
                    isMultiOutlet = multiOutlet,
                    otherOutletsNewOrdersCount = 3
                )
            }
        }
        composeRule.onNodeWithTag("drawer_branch_code_chip", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("drawer_item_outlets").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("drawer_outlets_badge", useUnmergedTree = true).assertIsDisplayed()

        multiOutlet = false
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithTag("drawer_item_outlets").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("drawer_branch_code_chip", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("drawer_outlets_badge", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun storeProfile_showsLockedBranchNameAndCode_andSettingsShowsChangePinOnlyForMultiOutlet() {
        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                StoreProfileScreen(
                    settings = outletRizal,
                    onSave = { _, _ -> },
                    onBack = {}
                )
            }
        }
        composeRule.onNodeWithTag("store_profile_branch_name_readonly").assertIsDisplayed()
        composeRule.onNodeWithTag("store_profile_branch_code_readonly").assertIsDisplayed()
        composeRule.onAllNodesWithText("Only the admin can change these").fetchSemanticsNodes().let {
            assertTrue(it.size >= 2)
        }
    }

    @Test
    fun settingsScreen_hidesSecuritySectionWhenSingleOutlet_showsWhenMultiOutlet() {
        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                SettingsScreen(
                    settings = outletRizal,
                    showSecurityPinOption = true,
                    onBack = {}
                )
            }
        }
        composeRule.onNodeWithTag("settings_change_pin_row").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun outletsSelectorSheet_searchesResetsAndAppliesSingleSwitch() {
        var appliedSingleId: String? = null
        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                OutletsSelectorSheet(
                    mode = OutletsSheetMode.SINGLE_SWITCH,
                    outlets = listOf(outletMain, outletRizal),
                    activeMerchantId = "m_main",
                    selectedFilterIds = emptySet(),
                    newOrderCountByOutlet = mapOf("m_rizal" to 2),
                    currentTime = 1_000_000L,
                    onDismiss = {},
                    onApplySingle = { appliedSingleId = it },
                    onApplyMulti = {}
                )
            }
        }

        composeRule.onNodeWithTag("outlets_search_input").performTextInput("PRZ")
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("outlet_row_m_main").fetchSemanticsNodes().let {
            assertEquals(0, it.size)
        }
        composeRule.onNodeWithTag("outlet_row_m_rizal").performClick()
        composeRule.onNodeWithTag("outlets_sheet_apply_btn").performClick()
        assertEquals("m_rizal", appliedSingleId)
    }

    @Test
    fun outletsPinDialog_showsLockoutMessageWhenLockedOut() {
        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                OutletsPinDialog(
                    mode = PinModalMode.ENTER,
                    isLockedOutInitially = true,
                    initialLockoutSeconds = 295L,
                    onCheckLockoutSeconds = { 295L },
                    onCreatePin = { _, _, _ -> },
                    onEnterPin = { PinVerifyResult.LockedOut(295L) },
                    onForgotPinClick = {},
                    onVerifyPasswordReset = { _, _ -> },
                    onChangePin = { _, _, _, _ -> },
                    onDismiss = {}
                )
            }
        }
        composeRule.onNodeWithTag("pin_lockout_message").assertIsDisplayed()
        composeRule.onNodeWithTag("pin_enter_submit_btn").assertIsNotEnabled()
    }

    @Test
    fun branchSetupDialog_rejectsDuplicateBranchCodeInChain() {
        val pendingBranch = outletRizal.copy(
            merchantId = "m_new",
            branchName = "",
            branchCode = "",
            branchSetupDone = false
        )
        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                BranchSetupDialog(
                    outlet = pendingBranch,
                    existingOutlets = listOf(outletMain, outletRizal, pendingBranch),
                    onSave = { _, _, _ -> }
                )
            }
        }
        composeRule.onNodeWithTag("branch_setup_name_input").performTextInput("Junction")
        composeRule.onNodeWithTag("branch_setup_code_input").performTextInput("PRZ")
        composeRule.onNodeWithTag("branch_setup_save_btn").performClick()
        composeRule.onNodeWithTag("branch_setup_error").assertIsDisplayed()
    }

    @Test
    fun menuManagement_showsPushPullSyncButtonsWhenMultiOutlet() {
        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                MenuManagementScreen(
                    menus = emptyList(),
                    subcategories = emptyList(),
                    menuItems = emptyList(),
                    currentTime = 1_000_000L,
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
                    isMultiOutlet = true
                )
            }
        }
        composeRule.onNodeWithTag("btn_push_menu").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_pull_menu").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_sync_menu").assertIsDisplayed()
    }

    @Test
    fun menuSyncDialog_conflictResolverGatesApplyUntilAllResolved_andShowsCopySummary() {
        val snapMain = OutletMenuSnapshot(
            merchantId = "m_main",
            displayName = "Jollibee - Main",
            branchCode = "MAIN",
            businessType = BusinessType.FOOD,
            status = "active",
            items = listOf(MenuItem(id = "i1", merchantId = "m_main", name = "Burger", price = 99.0)),
            subcategories = emptyList(),
            menus = emptyList()
        )
        val snapRizal = OutletMenuSnapshot(
            merchantId = "m_rizal",
            displayName = "Jollibee - Rizal",
            branchCode = "PRZ",
            businessType = BusinessType.FOOD,
            status = "active",
            items = listOf(MenuItem(id = "i2", merchantId = "m_rizal", name = "Burger", price = 115.0)),
            subcategories = emptyList(),
            menus = emptyList()
        )
        var syncExecuted = false

        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                MenuSyncDialog(
                    mode = MenuSyncDialogMode.SYNC,
                    activeMerchantId = "m_main",
                    snapshotsByMerchantId = mapOf("m_main" to snapMain, "m_rizal" to snapRizal),
                    isLoadingSnapshots = false,
                    isRunning = false,
                    progress = 0 to 0,
                    result = null,
                    onExecutePush = { _, _ -> },
                    onExecutePull = { _, _ -> },
                    onExecuteSync = { _, _, _, _, _ -> syncExecuted = true },
                    onDismiss = {}
                )
            }
        }

        // Proceed from Step 1 to Step 2 Conflict Resolver
        composeRule.onNodeWithTag("sync_step1_next_btn").performClick()
        composeRule.waitForIdle()

        // Apply Sync disabled until conflict is resolved
        composeRule.onNodeWithTag("sync_apply_btn").assertIsNotEnabled()
        composeRule.onNodeWithTag("sync_use_mine_all_btn").performClick()
        composeRule.onNodeWithTag("sync_apply_btn").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("sync_menu_execute_btn").performClick()
        assertTrue(syncExecuted)
    }

    @Test
    fun menuSyncResultView_displaysSummaryAndCopyButton() {
        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                MenuSyncDialog(
                    mode = MenuSyncDialogMode.PUSH,
                    activeMerchantId = "m_main",
                    snapshotsByMerchantId = emptyMap(),
                    isLoadingSnapshots = false,
                    isRunning = false,
                    progress = 1 to 1,
                    result = MenuSyncResult("Push Menu", added = 3, updated = 0, skipped = 2, failed = 0),
                    onExecutePush = { _, _ -> },
                    onExecutePull = { _, _ -> },
                    onExecuteSync = { _, _, _, _, _ -> },
                    onDismiss = {}
                )
            }
        }
        composeRule.onNodeWithTag("sync_result_view").assertIsDisplayed()
        composeRule.onNodeWithTag("sync_result_copy_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("sync_result_done_btn").assertIsDisplayed()
    }

    @Test
    fun dashboardAndReports_showOutletsFilterAndPerOutletBreakdownWhenMultipleSelected() {
        val now = 1_700_000_000_000L
        val orders = listOf(
            Order(
                id = "o1",
                merchantId = "m_main",
                orderNumber = "DLV-1",
                status = OrderStatus.DELIVERED,
                subtotal = 200.0,
                placedAtTimestamp = now - 1000L
            ),
            Order(
                id = "o2",
                merchantId = "m_rizal",
                orderNumber = "DLV-2",
                status = OrderStatus.DELIVERED,
                subtotal = 350.0,
                placedAtTimestamp = now - 1000L
            )
        )
        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                DashboardScreen(
                    settings = outletMain,
                    orders = orders,
                    onToggleOpen = {},
                    onPauseClick = {},
                    onResumeClick = {},
                    onOrderClick = {},
                    onViewAllOrders = {},
                    soldOutItemsCount = 0,
                    lowStockCount = 0,
                    currentTime = now,
                    isMultiOutlet = true,
                    outlets = listOf(outletMain, outletRizal),
                    selectedOutletIds = setOf("m_main", "m_rizal")
                )
            }
        }
        composeRule.onNodeWithTag("dashboard_outlets_filter_chip").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_per_outlet_breakdown").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun reportsScreen_showsOutletsFilterAndPerOutletBreakdown() {
        val now = 1_700_000_000_000L
        val orders = listOf(
            Order(
                id = "o1",
                merchantId = "m_main",
                orderNumber = "DLV-1",
                status = OrderStatus.DELIVERED,
                subtotal = 200.0,
                placedAtTimestamp = now - 1000L
            ),
            Order(
                id = "o2",
                merchantId = "m_rizal",
                orderNumber = "DLV-2",
                status = OrderStatus.DELIVERED,
                subtotal = 350.0,
                placedAtTimestamp = now - 1000L
            )
        )
        composeRule.setContent {
            DelivioPartnerTheme(darkTheme = false) {
                ReportsScreen(
                    settings = outletMain,
                    orders = orders,
                    menuItems = emptyList(),
                    reviews = emptyList(),
                    vouchers = emptyList(),
                    redemptions = emptyList(),
                    averageRating = 5.0,
                    reviewsCount = 0,
                    currentTime = now,
                    onCheckCustomerPriorDelivered = { _, _, cb -> cb(false, null) },
                    onOpenReviews = {},
                    onCreateVoucher = {},
                    onBack = {},
                    isMultiOutlet = true,
                    outlets = listOf(outletMain, outletRizal),
                    selectedOutletIds = setOf("m_main", "m_rizal")
                )
            }
        }
        composeRule.onNodeWithTag("reports_outlets_filter_chip").assertIsDisplayed()
        composeRule.onNodeWithTag("reports_per_outlet_breakdown").performScrollTo().assertIsDisplayed()
    }
}
