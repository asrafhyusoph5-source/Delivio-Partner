package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.OrderStatus
import com.example.ui.components.*
import com.example.ui.screens.*
import com.example.ui.theme.DelivioPartnerTheme
import com.example.viewmodel.MerchantTab
import com.example.viewmodel.MerchantViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MerchantViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        viewModel.onAppForeground()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            viewModel.onAppBackground()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {

            val context = androidx.compose.ui.platform.LocalContext.current
            val networkHelper = remember { com.example.util.NetworkConnectivityHelper(context) }
            val isOnline by networkHelper.isOnline.collectAsStateWithLifecycle()

            val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
            val merchantApplication by viewModel.merchantApplication.collectAsStateWithLifecycle()
            val isAuthLoading by viewModel.isAuthLoading.collectAsStateWithLifecycle()
            val authErrorMessage by viewModel.authErrorMessage.collectAsStateWithLifecycle()

            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val receiptDesign by viewModel.receiptDesign.collectAsStateWithLifecycle()
            val orders by viewModel.orders.collectAsStateWithLifecycle()
            val newOrderAlert by viewModel.newOrderAlert.collectAsStateWithLifecycle()
            val categories by viewModel.categories.collectAsStateWithLifecycle()
            val mainCategories by viewModel.mainCategories.collectAsStateWithLifecycle()
            val menuItems by viewModel.menuItems.collectAsStateWithLifecycle()
            val payouts by viewModel.payouts.collectAsStateWithLifecycle()
            val promos by viewModel.promos.collectAsStateWithLifecycle()
            val staff by viewModel.staff.collectAsStateWithLifecycle()
            val changeRequests by viewModel.changeRequests.collectAsStateWithLifecycle()

            val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
            val selectedOrderDetailId by viewModel.selectedOrderDetailId.collectAsStateWithLifecycle()
            val newOrdersCount by viewModel.newOrdersCount.collectAsStateWithLifecycle()
            val orderStatusFilter by viewModel.orderStatusFilter.collectAsStateWithLifecycle()
            val fulfillmentFilter by viewModel.fulfillmentFilter.collectAsStateWithLifecycle()

            val menuSearchQuery by viewModel.menuSearchQuery.collectAsStateWithLifecycle()
            val menuStockFilter by viewModel.menuStockFilter.collectAsStateWithLifecycle()
            val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
            val isMultiSelectMode by viewModel.isMultiSelectMode.collectAsStateWithLifecycle()
            val selectedItemIds by viewModel.selectedItemIds.collectAsStateWithLifecycle()
            val earningsPeriod by viewModel.earningsPeriod.collectAsStateWithLifecycle()

            // Dialog and Sheet states
            val rejectOrderTarget by viewModel.rejectOrderTarget.collectAsStateWithLifecycle()
            val prepTimePickerTarget by viewModel.prepTimePickerTarget.collectAsStateWithLifecycle()
            val requestRiderTarget by viewModel.requestRiderTarget.collectAsStateWithLifecycle()
            val receiptPreviewTarget by viewModel.receiptPreviewTarget.collectAsStateWithLifecycle()
            val editOrderTarget by viewModel.editOrderTarget.collectAsStateWithLifecycle()
            val editOrderError by viewModel.editOrderError.collectAsStateWithLifecycle()
            val showColorPickerSheet by viewModel.showColorPickerSheet.collectAsStateWithLifecycle()
            val showPauseSheet by viewModel.showPauseSheet.collectAsStateWithLifecycle()
            val showCloseConfirmation by viewModel.showCloseConfirmation.collectAsStateWithLifecycle()
            val editingMenuItem by viewModel.editingMenuItem.collectAsStateWithLifecycle()
            val isNewMenuItem by viewModel.isNewMenuItem.collectAsStateWithLifecycle()
            val showMapLocationScreen by viewModel.showMapLocationScreen.collectAsStateWithLifecycle()
            val showPayoutDialog by viewModel.showPayoutDialog.collectAsStateWithLifecycle()
            val showBankDetailsDialog by viewModel.showBankDetailsDialog.collectAsStateWithLifecycle()
            val showBusinessHoursDialog by viewModel.showBusinessHoursDialog.collectAsStateWithLifecycle()
            val showStaffDialog by viewModel.showStaffDialog.collectAsStateWithLifecycle()
            val showSoldOutDurationTarget by viewModel.showSoldOutDurationTarget.collectAsStateWithLifecycle()
            val showChooseOrderDialog by viewModel.showChooseOrderDialog.collectAsStateWithLifecycle()
            val showManualBookingForm by viewModel.showManualBookingForm.collectAsStateWithLifecycle()
            val riderRequestError by viewModel.riderRequestError.collectAsStateWithLifecycle()
            val showWalletScreen by viewModel.showWalletScreen.collectAsStateWithLifecycle()
            val showCashOutComingSoon by viewModel.showCashOutComingSoon.collectAsStateWithLifecycle()
            val walletTransactions by viewModel.walletTransactions.collectAsStateWithLifecycle()
            val sharedWalletBalance by viewModel.sharedWalletBalance.collectAsStateWithLifecycle()
            val isActionLoading by viewModel.isActionLoading.collectAsStateWithLifecycle()
            val currentTime by viewModel.currentTime.collectAsStateWithLifecycle()

            val inboxMessages by viewModel.inboxMessages.collectAsStateWithLifecycle()
            val unreadInboxCount by viewModel.unreadInboxCount.collectAsStateWithLifecycle()
            val inboxBannerMessage by viewModel.inboxBannerMessage.collectAsStateWithLifecycle()
            val showInboxScreen by viewModel.showInboxScreen.collectAsStateWithLifecycle()
            val showStoreProfileScreen by viewModel.showStoreProfileScreen.collectAsStateWithLifecycle()
            val showSettingsScreen by viewModel.showSettingsScreen.collectAsStateWithLifecycle()
            val showPrinterScreen by viewModel.showPrinterScreen.collectAsStateWithLifecycle()

            val isServiceLocked by viewModel.isServiceLocked.collectAsStateWithLifecycle()
            val serviceLockMessage by viewModel.serviceLockMessage.collectAsStateWithLifecycle()
            val reviews by viewModel.reviews.collectAsStateWithLifecycle()
            val averageRating by viewModel.averageRating.collectAsStateWithLifecycle()
            val reviewsCount by viewModel.reviewsCount.collectAsStateWithLifecycle()
            val riderReviews by viewModel.riderReviews.collectAsStateWithLifecycle()
            val riderAverageRating by viewModel.riderAverageRating.collectAsStateWithLifecycle()
            val riderReviewsCount by viewModel.riderReviewsCount.collectAsStateWithLifecycle()
            val showReviewsScreen by viewModel.showReviewsScreen.collectAsStateWithLifecycle()
            val subcategories by viewModel.subcategories.collectAsStateWithLifecycle()
            val selectedSubcategoryId by viewModel.selectedSubcategoryId.collectAsStateWithLifecycle()
            val menus by viewModel.menus.collectAsStateWithLifecycle()
            val showMenuManagementScreen by viewModel.showMenuManagementScreen.collectAsStateWithLifecycle()
            val showPriceDropsScreen by viewModel.showPriceDropsScreen.collectAsStateWithLifecycle()
            val editingShopMenu by viewModel.editingShopMenu.collectAsStateWithLifecycle()
            val isNewShopMenu by viewModel.isNewShopMenu.collectAsStateWithLifecycle()
            val selectedMenuIdFilter by viewModel.selectedMenuIdFilter.collectAsStateWithLifecycle()
            val vouchers by viewModel.vouchers.collectAsStateWithLifecycle()
            val showVouchersScreen by viewModel.showVouchersScreen.collectAsStateWithLifecycle()
            val showReportsScreen by viewModel.showReportsScreen.collectAsStateWithLifecycle()
            val editingVoucher by viewModel.editingVoucher.collectAsStateWithLifecycle()
            val isNewVoucher by viewModel.isNewVoucher.collectAsStateWithLifecycle()
            val orderChats by viewModel.orderChats.collectAsStateWithLifecycle()
            val activeChatOrder by viewModel.activeChatOrder.collectAsStateWithLifecycle()
            val activeChatMessages by viewModel.activeChatMessages.collectAsStateWithLifecycle()
            val hasUnreadCustomerMessages by remember(orderChats) {
                derivedStateOf { orderChats.values.any { it.isUnreadForMerchant() } }
            }

            // Multi-Outlet states
            val activeMerchantId by viewModel.activeMerchantId.collectAsStateWithLifecycle()
            val outlets by viewModel.outlets.collectAsStateWithLifecycle()
            val isMultiOutlet by viewModel.isMultiOutlet.collectAsStateWithLifecycle()
            val otherOutletsNewOrdersCount by viewModel.otherOutletsNewOrdersCount.collectAsStateWithLifecycle()
            val newOrderCountByOutlet by viewModel.newOrderCountByOutlet.collectAsStateWithLifecycle()
            val otherOutletOrderBanner by viewModel.otherOutletOrderBanner.collectAsStateWithLifecycle()
            val branchSetupOutlet by viewModel.branchSetupOutlet.collectAsStateWithLifecycle()
            val outletsSheetMode by viewModel.outletsSheetMode.collectAsStateWithLifecycle()
            val pinModalMode by viewModel.pinModalMode.collectAsStateWithLifecycle()
            val dashboardSelectedOutletIds by viewModel.dashboardSelectedOutletIds.collectAsStateWithLifecycle()
            val dashboardCombinedOrders by viewModel.dashboardCombinedOrders.collectAsStateWithLifecycle()
            val dashboardCombinedReviews by viewModel.dashboardCombinedReviews.collectAsStateWithLifecycle()
            val isDashboardCombinedLoading by viewModel.isDashboardCombinedLoading.collectAsStateWithLifecycle()
            val reportsSelectedOutletIds by viewModel.reportsSelectedOutletIds.collectAsStateWithLifecycle()
            val reportsCombinedOrders by viewModel.reportsCombinedOrders.collectAsStateWithLifecycle()
            val isReportsCombinedLoading by viewModel.isReportsCombinedLoading.collectAsStateWithLifecycle()
            val menuSyncDialogMode by viewModel.menuSyncDialogMode.collectAsStateWithLifecycle()
            val isMenuSyncLoadingSnapshots by viewModel.isMenuSyncLoadingSnapshots.collectAsStateWithLifecycle()
            val menuSyncSnapshots by viewModel.menuSyncSnapshots.collectAsStateWithLifecycle()
            val isMenuSyncRunning by viewModel.isMenuSyncRunning.collectAsStateWithLifecycle()
            val menuSyncProgress by viewModel.menuSyncProgress.collectAsStateWithLifecycle()
            val menuSyncResult by viewModel.menuSyncResult.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                viewModel.newIncomingMessageEvent.collect { thread ->
                    viewModel.showSnackbar("New message on #${thread.orderNumber}")
                }
            }

            val isOwner = currentUser?.role != "staff"
            val userRole = currentUser?.role?.lowercase() ?: "merchant"
            val canAccessInbox = userRole in listOf("merchant", "owner", "partner", "manager")
            val canAccessReviews = userRole in listOf("merchant", "owner", "partner", "manager")

            var isRegistering by remember { mutableStateOf(false) }
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val coroutineScope = rememberCoroutineScope()
            var showHelpDialog by remember { mutableStateOf(false) }

            DelivioPartnerTheme(
                themeAccentHex = settings.themeAccentHex,
                darkTheme = settings.isDarkMode
            ) {
                if (currentUser == null) {
                    if (merchantApplication != null) {
                        // User submitted an application or has non-approved status
                        ApplicationStatusScreen(
                            application = merchantApplication!!,
                            onResubmit = { updatedApp ->
                                viewModel.resubmitApplication(updatedApp.id, updatedApp) { _, _ -> }
                            },
                            onLogout = {
                                viewModel.logout()
                                isRegistering = false
                            }
                        )
                    } else if (isRegistering) {
                        // Merchant Application Registration Form
                        RegisterMerchantScreen(
                            onBackToLogin = { isRegistering = false },
                            onSubmit = { application, pass ->
                                viewModel.registerMerchant(application, pass) { success, _ ->
                                    if (success) {
                                        isRegistering = false
                                    }
                                }
                            },
                            isLoading = isAuthLoading,
                            errorMessage = authErrorMessage
                        )
                    } else {
                        // Login Portal (Merchant / Staff)
                        LoginScreen(
                            onLoginAttempt = { email, pass, isStaff ->
                                viewModel.login(email, pass, isStaff) { success, _ ->
                                    if (success) {
                                        viewModel.selectTab(MerchantTab.DASHBOARD)
                                    }
                                }
                            },
                            onForgotPassword = { email ->
                                viewModel.sendPasswordReset(email) { _, _ -> }
                            },
                            onOpenRegister = { isRegistering = true },
                            isLoading = isAuthLoading,
                            externalErrorMessage = authErrorMessage
                        )
                    }
                } else if (showMapLocationScreen) {
                    // Full-screen Map Location Pin setter
                    MapLocationScreen(
                        currentLocation = settings.storeLocation,
                        locationChangedAt = settings.locationChangedAt,
                        onSaveLocation = { loc, cb -> viewModel.saveStoreLocation(loc, cb) },
                        onRequestUrgentChange = { addr, lat, lng, reason, cb ->
                            viewModel.requestUrgentLocationChange(addr, lat, lng, reason, cb)
                        },
                        onBack = { viewModel.closeMapLocation() }
                    )
                } else if (showWalletScreen) {
                    // Full-screen Delivio Wallet view (Owner only)
                    WalletScreen(
                        settings = settings,
                        sharedWalletBalance = sharedWalletBalance,
                        transactions = walletTransactions,
                        onCashOutClick = { viewModel.openCashOutComingSoon() },
                        onBack = { viewModel.closeWalletScreen() }
                    )
                } else if (showInboxScreen) {
                    // Full-screen Merchant Inbox
                    InboxScreen(
                        messages = inboxMessages,
                        unreadCount = unreadInboxCount,
                        onMarkAsRead = { viewModel.markInboxMessageAsRead(it) },
                        onMarkAllAsRead = { viewModel.markAllInboxMessagesAsRead() },
                        onDismiss = { viewModel.closeInbox() }
                    )
                } else if (showSettingsScreen) {
                    // Full-screen Settings route
                    SettingsScreen(
                        settings = settings,
                        staff = staff,
                        changeRequests = changeRequests,
                        onOpenBusinessHours = { viewModel.openBusinessHoursDialog() },
                        onRequestAdminChange = { kind, requestedValue, reason ->
                            viewModel.submitChangeRequest(kind, requestedValue, reason) { success, err ->
                                if (success) viewModel.showSnackbar("Change request submitted")
                                else viewModel.showSnackbar(err ?: "Failed to submit change request")
                            }
                        },
                        onUpdateStoreSettings = { storeName, phone, minOrder, prep, sound, vibration, paper, autoPrint ->
                            viewModel.updateStoreSettings(
                                storeName, phone, minOrder, prep,
                                sound, vibration, paper, autoPrint
                            )
                        },
                        onOpenPrinter = { viewModel.openPrinterScreen() },
                        onOpenMapLocation = { viewModel.openMapLocation() },
                        onOpenStaffDialog = { viewModel.openStaffDialog() },
                        onToggleDarkMode = { viewModel.toggleDarkMode(it) },
                        onCleanupExpiredChats = { onFinished ->
                            viewModel.cleanupExpiredChats(force = true) { count ->
                                onFinished(count)
                                if (count > 0) {
                                    viewModel.showSnackbar("Deleted $count expired chat item${if (count == 1) "" else "s"}")
                                } else {
                                    viewModel.showSnackbar("No expired chats found to clean")
                                }
                            }
                        },
                        showSecurityPinOption = isMultiOutlet,
                        onChangeOutletsPin = { viewModel.openChangePinDialog() },
                        onBack = { viewModel.closeSettings() }
                    )
                } else if (showPrinterScreen) {
                    // Full-screen Bluetooth Printer route (Profile > Printer)
                    BluetoothPrinterScreen(
                        settings = settings,
                        onSavePrinter = { name, address ->
                            viewModel.saveBluetoothPrinter(name, address)
                        },
                        onBack = { viewModel.closePrinterScreen() }
                    )
                } else if (showStoreProfileScreen) {
                    // Full-screen Store Profile Details route
                    StoreProfileScreen(
                        settings = settings,
                        onSave = { changedFields, onResult ->
                            viewModel.updateMerchantProfile(changedFields, onResult)
                        },
                        onBack = { viewModel.closeStoreProfile() },
                        onOpenColorPicker = { viewModel.openColorPicker() },
                        onToggleDarkMode = { viewModel.toggleDarkMode(it) },
                        onOpenMapLocation = { viewModel.openMapLocation() }
                    )
                } else if (showReviewsScreen) {
                    // Full-screen Ratings & Comments route (Customers & Riders)
                    ReviewsScreen(
                        reviews = reviews,
                        averageRating = averageRating,
                        reviewsCount = reviewsCount,
                        riderReviews = riderReviews,
                        riderAverageRating = riderAverageRating,
                        riderReviewsCount = riderReviewsCount,
                        onBack = { viewModel.closeReviews() }
                    )
                } else if (editingShopMenu != null) {
                    // Full-screen Add / Edit Menu route
                    MenuEditScreen(
                        menu = editingShopMenu!!,
                        isNew = isNewShopMenu,
                        allMenus = menus,
                        menuItems = menuItems,
                        onSave = { updatedMenu, assignedProductIds, onComplete ->
                            viewModel.saveShopMenu(updatedMenu, assignedProductIds, onComplete)
                        },
                        onBack = { viewModel.closeEditShopMenu() }
                    )
                } else if (showPriceDropsScreen) {
                    // Full-screen Price Drops route
                    PriceDropsScreen(
                        menuItems = menuItems,
                        menus = menus,
                        businessType = settings.businessType,
                        currentTime = currentTime,
                        timeZoneId = settings.timezone,
                        onBack = { viewModel.closePriceDrops() },
                        onApplyPriceDrops = { itemSalePrices, startsAt, endsAt, cb ->
                            viewModel.applyPriceDrops(itemSalePrices, startsAt, endsAt, emptyMap(), cb)
                        },
                        onApplyVariationPriceDrops = { itemSalePrices, variantSalePrices, startsAt, endsAt, cb ->
                            viewModel.applyPriceDrops(itemSalePrices, startsAt, endsAt, variantSalePrices, cb)
                        },
                        onEndPriceDrop = { itemId, cb ->
                            viewModel.endPriceDrop(itemId, cb)
                        }
                    )
                } else if (showMenuManagementScreen) {
                    // Full-screen Menu Management route (Menus, Category Classes, Bulk edit)
                    MenuManagementScreen(
                        menus = menus,
                        subcategories = subcategories,
                        menuItems = menuItems,
                        currentTime = currentTime,
                        timeZoneId = settings.timezone,
                        onBack = { viewModel.closeMenuManagement() },
                        onAddMenu = { viewModel.openAddShopMenu() },
                        onEditMenu = { viewModel.openEditShopMenu(it) },
                        onToggleMenuActive = { id, active -> viewModel.toggleShopMenuActive(id, active) },
                        onDuplicateMenu = { viewModel.duplicateShopMenu(it) },
                        onDeleteMenu = { viewModel.deleteShopMenu(it) },
                        onReorderSubcategories = { viewModel.reorderSubcategories(it) },
                        onAddSubcategory = { name, cb -> viewModel.createSubcategory(name, cb) },
                        onRenameSubcategory = { id, name, cb -> viewModel.renameSubcategory(id, name, cb) },
                        onDeleteSubcategory = { id, cb -> viewModel.deleteSubcategory(id, cb) },
                        onBulkMarkStock = { ids, inStock, cb -> viewModel.bulkMarkItemsStock(ids, inStock, cb) },
                        onBulkMoveToMenu = { ids, menuId, cb -> viewModel.bulkMoveItemsToMenu(ids, menuId, cb) },
                        onBulkSetCategoryClass = { ids, subId, subName, cb -> viewModel.bulkSetCategoryClass(ids, subId, subName, cb) },
                        onBulkAdjustPrices = { ids, pct, fix, cb -> viewModel.bulkAdjustPrices(ids, pct, fix, cb) },
                        onBulkDeleteItems = { ids, cb -> viewModel.bulkDeleteItems(ids, cb) },
                        isMultiOutlet = isMultiOutlet,
                        onPushMenu = { viewModel.openPushMenu() },
                        onPullMenu = { viewModel.openPullMenu() },
                        onSyncMenu = { viewModel.openSyncMenu() },
                        onOpenPriceDrops = { viewModel.openPriceDrops() }
                    )
                } else if (editingMenuItem != null) {
                    // Full-screen Add / Edit Product route
                    ProductEditScreen(
                        item = editingMenuItem!!,
                        isNew = isNewMenuItem,
                        categories = categories,
                        subcategories = subcategories,
                        menus = menus,
                        menuItems = menuItems,
                        businessType = settings.businessType,
                        mainCategories = mainCategories,
                        currentTime = currentTime,
                        timeZoneId = settings.timezone,
                        onSave = { updatedItem, onComplete ->
                            viewModel.saveMenuItem(updatedItem, onComplete)
                        },
                        onDelete = { itemId ->
                            viewModel.deleteMenuItem(itemId)
                        },
                        onDismiss = {
                            viewModel.closeEditMenuItem()
                        },
                        onCreateSubcategory = { name, onComplete ->
                            viewModel.createSubcategory(name, onComplete)
                        },
                        onRenameSubcategory = { id, newName, onComplete ->
                            viewModel.renameSubcategory(id, newName, onComplete)
                        },
                        onDeleteSubcategory = { id, onComplete ->
                            viewModel.deleteSubcategory(id, onComplete)
                        },
                        onShowSaleEndedNotice = { msg ->
                            viewModel.showSnackbar(msg)
                        }
                    )
                } else if (editingVoucher != null) {
                    // Full-screen Create / Edit Voucher route
                    VoucherEditScreen(
                        voucher = editingVoucher!!,
                        isNew = isNewVoucher,
                        availableProducts = menuItems.filter { it.businessType == settings.businessType },
                        onCheckCodeExists = { code, cb -> viewModel.checkPromoCodeExists(code, cb) },
                        onSave = { v, isNew, cb -> viewModel.saveVoucher(v, isNew, cb) },
                        onDelete = { code, cb -> viewModel.deleteVoucher(code, cb) },
                        onToggleActive = { code, active -> viewModel.toggleVoucherActive(code, active) },
                        onBack = { viewModel.closeEditVoucher() }
                    )
                } else if (showVouchersScreen) {
                    // Full-screen Vouchers List route
                    VouchersScreen(
                        vouchers = vouchers,
                        onCreateVoucher = { viewModel.openCreateVoucher() },
                        onEditVoucher = { viewModel.openEditVoucher(it) },
                        onToggleActive = { code, active -> viewModel.toggleVoucherActive(code, active) },
                        onBack = { viewModel.closeVouchers() }
                    )
                } else if (showReportsScreen) {
                    // Full-screen Reports route
                    val useCombinedReports = isMultiOutlet && reportsSelectedOutletIds.size > 1
                    val effectiveReportsOrders = if (useCombinedReports) reportsCombinedOrders else orders
                    ReportsScreen(
                        settings = settings,
                        orders = effectiveReportsOrders,
                        menuItems = menuItems,
                        reviews = reviews,
                        vouchers = vouchers,
                        redemptions = emptyList(),
                        averageRating = averageRating,
                        reviewsCount = reviewsCount,
                        currentTime = currentTime,
                        onCheckCustomerPriorDelivered = { customerId, periodStartMs, onResult ->
                            viewModel.checkCustomerHasPriorDeliveredOrder(customerId, periodStartMs, onResult)
                        },
                        onOpenReviews = { viewModel.openReviews() },
                        onCreateVoucher = { viewModel.openCreateVoucher() },
                        onBack = { viewModel.closeReports() },
                        isMultiOutlet = isMultiOutlet,
                        outlets = outlets,
                        selectedOutletIds = if (reportsSelectedOutletIds.isNotEmpty()) reportsSelectedOutletIds else setOfNotNull(activeMerchantId),
                        isCombinedLoading = isReportsCombinedLoading,
                        onOpenOutletsFilter = { viewModel.openReportsOutletsFilter() }
                    )
                } else if (activeChatOrder != null) {
                    // Full-screen Customer Chat view
                    OrderChatScreen(
                        order = activeChatOrder!!,
                        messages = activeChatMessages,
                        onSendMessage = { text ->
                            viewModel.sendCustomerChatMessage(activeChatOrder!!, text) { success, err ->
                                if (!success) {
                                    viewModel.showSnackbar(err ?: "Failed to send message")
                                }
                            }
                        },
                        onBack = { viewModel.closeCustomerChat() },
                        currentTime = currentTime
                    )
                } else if (selectedOrderDetailId != null) {
                    // Full-screen Order Detail view
                    val selectedOrder = orders.find { it.id == selectedOrderDetailId }
                    if (selectedOrder != null) {
                        OrderDetailScreen(
                            order = selectedOrder,
                            settings = settings,
                            onBack = { viewModel.closeOrderDetail() },
                            onAccept = { viewModel.openPrepTimePicker(it) },
                            onReject = { viewModel.openRejectDialog(it) },
                            onSetReady = { viewModel.setOrderReady(it.id) },
                            onRequestRider = { viewModel.openRequestRider(it) },
                            onPrintReceipt = { viewModel.openReceiptPreview(it) },
                            onMarkPickedUp = { viewModel.markOrderPickedUp(it.id) },
                            onEditOrder = { viewModel.openEditOrder(it) },
                            onRequestRiderAgain = { viewModel.requestRiderAgain(it.id) },
                            currentTime = currentTime,
                            orderChats = orderChats,
                            onOpenChat = { viewModel.openCustomerChat(it) }
                        )
                    } else {
                        viewModel.closeOrderDetail()
                    }
                } else {
                    // Back navigation behavior:
                    // 1. If right drawer is open, Back closes the drawer first
                    BackHandler(enabled = drawerState.isOpen) {
                        coroutineScope.launch { drawerState.close() }
                    }

                    // 2. Back from any secondary screen returns to Orders; Back on Orders exits
                    BackHandler(enabled = !drawerState.isOpen && currentTab != MerchantTab.ORDERS) {
                        viewModel.selectTab(MerchantTab.ORDERS)
                    }

                    // Right-sliding Navigation Drawer
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        ModalNavigationDrawer(
                            drawerState = drawerState,
                            drawerContent = {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    MerchantRightDrawer(
                                        settings = settings,
                                        currentTime = currentTime,
                                        isServiceLocked = isServiceLocked,
                                        unreadInboxCount = unreadInboxCount,
                                        newOrdersCount = newOrdersCount,
                                        averageRating = averageRating,
                                        reviewsCount = reviewsCount,
                                        walletBalance = if (!settings.linkedCustomerUid.isNullOrBlank() && sharedWalletBalance != null) sharedWalletBalance!! else settings.walletBalance,
                                        onToggleOpen = {
                                            coroutineScope.launch { drawerState.close() }
                                            viewModel.toggleStoreOpen()
                                        },
                                        onOpenReviews = {
                                            coroutineScope.launch { drawerState.close() }
                                            viewModel.openReviews()
                                        },
                                        onNavigateOrders = {
                                            coroutineScope.launch { drawerState.close() }
                                            viewModel.selectTab(MerchantTab.ORDERS)
                                        },
                                        onNavigateMenu = {
                                            coroutineScope.launch { drawerState.close() }
                                            viewModel.selectTab(MerchantTab.MENU)
                                        },
                                        onNavigateDashboard = {
                                            coroutineScope.launch { drawerState.close() }
                                            viewModel.selectTab(MerchantTab.DASHBOARD)
                                        },
                                        onNavigateEarnings = {
                                            coroutineScope.launch { drawerState.close() }
                                            viewModel.selectTab(MerchantTab.EARNINGS)
                                        },
                                        onOpenReports = {
                                            coroutineScope.launch { drawerState.close() }
                                            viewModel.openReports()
                                        },
                                        onOpenWallet = {
                                            coroutineScope.launch { drawerState.close() }
                                            viewModel.openWalletScreen()
                                        },
                                        onOpenInbox = {
                                            coroutineScope.launch { drawerState.close() }
                                            viewModel.openInbox()
                                        },
                                        onOpenVouchers = {
                                            coroutineScope.launch { drawerState.close() }
                                            viewModel.openVouchers()
                                        },
                                        onRequestRider = {
                                            coroutineScope.launch { drawerState.close() }
                                            viewModel.openManualBookingForm()
                                        },
                                        onOpenStoreProfile = {
                                            coroutineScope.launch { drawerState.close() }
                                            viewModel.openStoreProfile()
                                        },
                                        onOpenSettings = {
                                            coroutineScope.launch { drawerState.close() }
                                            viewModel.openSettings()
                                        },
                                        onOpenHelp = {
                                            coroutineScope.launch { drawerState.close() }
                                            showHelpDialog = true
                                        },
                                        onLogout = {
                                            coroutineScope.launch { drawerState.close() }
                                            viewModel.logout()
                                        },
                                        isMultiOutlet = isMultiOutlet,
                                        otherOutletsNewOrdersCount = otherOutletsNewOrdersCount,
                                        onOpenOutlets = {
                                            coroutineScope.launch { drawerState.close() }
                                            viewModel.openOutletsSwitcher()
                                        }
                                    )
                                }
                            }
                        ) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Scaffold(
                                    topBar = {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            DelivioTopBar(
                                                settings = settings,
                                                onToggleOpen = { viewModel.toggleStoreOpen() },
                                                onOpenDrawer = {
                                                    coroutineScope.launch {
                                                        if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                                    }
                                                },
                                                badgeCount = newOrdersCount + unreadInboxCount + otherOutletsNewOrdersCount,
                                                hasUnreadChat = hasUnreadCustomerMessages,
                                                currentTime = currentTime,
                                                isServiceLocked = isServiceLocked,
                                                serviceLockMessage = serviceLockMessage,
                                                isMultiOutlet = isMultiOutlet,
                                                onOutletClick = { viewModel.openOutletsSwitcher() }
                                            )
                                            OfflineBanner(isOnline = isOnline)
                                            otherOutletOrderBanner?.let { bannerEvent ->
                                                Surface(
                                                    color = com.example.ui.theme.DelivioYellow,
                                                    contentColor = androidx.compose.ui.graphics.Color(0xFF1A1A1A),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { viewModel.switchActiveOutlet(bannerEvent.merchantId) }
                                                        .testTag("other_outlet_order_banner")
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Row(
                                                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Notifications,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text(
                                                                text = "New order ${bannerEvent.orderNumber} at ${bannerEvent.outletLabel} — Switch",
                                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                                fontSize = 13.sp,
                                                                maxLines = 2
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = { viewModel.dismissOtherOutletOrderBanner() },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Close,
                                                                contentDescription = "Dismiss banner",
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    containerColor = MaterialTheme.colorScheme.background
                                ) { innerPadding ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(innerPadding)
                                    ) {
                                        when (currentTab) {
                                            MerchantTab.ORDERS -> {
                                                OrdersScreen(
                                                    orders = orders,
                                                    activeFilter = orderStatusFilter,
                                                    onFilterChanged = { viewModel.setOrderStatusFilter(it) },
                                                    fulfillmentFilter = fulfillmentFilter,
                                                    onFulfillmentFilterChanged = { viewModel.setFulfillmentFilter(it) },
                                                    onOrderClick = { viewModel.openOrderDetail(it.id) },
                                                    onAcceptClick = { viewModel.openPrepTimePicker(it) },
                                                    onRejectClick = { viewModel.openRejectDialog(it) },
                                                    onSetReadyClick = { viewModel.setOrderReady(it.id) },
                                                    onRequestRiderClick = { viewModel.openRequestRider(it) },
                                                    onPrintReceiptClick = { viewModel.openReceiptPreview(it) },
                                                    onMarkPickedUpClick = { viewModel.markOrderPickedUp(it.id) },
                                                    onHandedToRiderClick = { viewModel.markOrderPickedUp(it.id) },
                                                    onCancelOrderClick = { viewModel.cancelOrder(it.id, "Merchant cancelled scheduled order") },
                                                    onEditOrderClick = { viewModel.openEditOrder(it) },
                                                    onRequestRiderAgainClick = { viewModel.requestRiderAgain(it.id) },
                                                    currentTime = currentTime,
                                                    orderChats = orderChats,
                                                    onOpenChat = { viewModel.openCustomerChat(it) }
                                                )
                                            }

                                            MerchantTab.MENU -> {
                                                LaunchedEffect(Unit) {
                                                    viewModel.onMenuOpened()
                                                }
                                                MenuScreen(
                                                    settings = settings,
                                                    categories = categories,
                                                    subcategories = subcategories,
                                                    menus = menus,
                                                    menuItems = menuItems,
                                                    promos = promos,
                                                    businessType = settings.businessType,
                                                    mainCategories = mainCategories,
                                                    currentTime = currentTime,
                                                    isServiceLocked = isServiceLocked,
                                                    serviceLockMessage = serviceLockMessage,
                                                    searchQuery = menuSearchQuery,
                                                    onSearchQueryChange = { viewModel.setMenuSearchQuery(it) },
                                                    stockFilter = menuStockFilter,
                                                    onStockFilterChange = { viewModel.setMenuStockFilter(it) },
                                                    selectedCategoryId = selectedCategoryId,
                                                    onCategorySelect = { viewModel.selectMenuCategory(it) },
                                                    selectedSubcategoryId = selectedSubcategoryId,
                                                    onSubcategorySelect = { viewModel.setMenuSubcategoryFilter(it) },
                                                    selectedMenuIdFilter = selectedMenuIdFilter,
                                                    onMenuFilterChange = { viewModel.setMenuIdFilter(it) },
                                                    isMultiSelectMode = isMultiSelectMode,
                                                    selectedItemIds = selectedItemIds,
                                                    onToggleMultiSelect = { viewModel.toggleMultiSelectMode() },
                                                    onToggleItemSelection = { viewModel.toggleItemSelection(it) },
                                                    onBulkMarkInStock = { viewModel.bulkMarkInStock() },
                                                    onBulkMarkSoldOut = { viewModel.bulkMarkSoldOut() },
                                                    onBulkPriceAdjust = { percent, fixed -> viewModel.bulkAdjustPrice(percent, fixed) },
                                                    onToggleItemAvailability = { viewModel.toggleItemAvailability(it) },
                                                    onAddItem = { viewModel.openAddMenuItem(selectedCategoryId) },
                                                    onEditItem = { viewModel.openEditMenuItem(it) },
                                                    onDuplicateItem = { viewModel.duplicateMenuItem(it) },
                                                    onMoveItemToMenu = { item, menuId -> viewModel.bulkMoveItemsToMenu(setOf(item.id), menuId) { _, _, _ -> } },
                                                    onDeleteItem = { viewModel.deleteMenuItem(it) },
                                                    onOpenMenuManagement = { viewModel.openMenuManagement() }
                                                )
                                            }

                                            MerchantTab.DASHBOARD -> {
                                                val soldOutCount = menuItems.count { it.businessType == settings.businessType && !it.inStock }
                                                val lowStockCount = menuItems.count {
                                                    it.businessType == settings.businessType && (
                                                        (it.variationsEnabled && it.hasAnyLowStockVariant) ||
                                                            (!it.variationsEnabled && it.trackStock && it.stockCount <= it.lowStockAt)
                                                        )
                                                }
                                                val useCombinedDashboard = isMultiOutlet && dashboardSelectedOutletIds.size > 1
                                                val effectiveDashboardOrders = if (useCombinedDashboard) dashboardCombinedOrders else orders
                                                val effectiveDashboardReviewsCount = if (useCombinedDashboard) dashboardCombinedReviews.size else reviewsCount
                                                val effectiveDashboardAvgRating = if (useCombinedDashboard) {
                                                    if (dashboardCombinedReviews.isEmpty()) 0.0
                                                    else dashboardCombinedReviews.map { it.ratingMerchant }.average()
                                                } else averageRating

                                                DashboardScreen(
                                                    settings = settings,
                                                    orders = effectiveDashboardOrders,
                                                    onToggleOpen = { viewModel.toggleStoreOpen() },
                                                    onPauseClick = { viewModel.openPauseSheet() },
                                                    onResumeClick = { viewModel.resumeOrders() },
                                                    onOrderClick = { viewModel.openOrderDetail(it.id) },
                                                    onViewAllOrders = { viewModel.selectTab(MerchantTab.ORDERS) },
                                                    soldOutItemsCount = soldOutCount,
                                                    lowStockCount = lowStockCount,
                                                    currentTime = currentTime,
                                                    isServiceLocked = isServiceLocked,
                                                    serviceLockMessage = serviceLockMessage,
                                                    averageRating = effectiveDashboardAvgRating,
                                                    reviewsCount = effectiveDashboardReviewsCount,
                                                    onOpenReports = { viewModel.openReports() },
                                                    isMultiOutlet = isMultiOutlet,
                                                    outlets = outlets,
                                                    selectedOutletIds = if (dashboardSelectedOutletIds.isNotEmpty()) dashboardSelectedOutletIds else setOfNotNull(activeMerchantId),
                                                    isCombinedLoading = isDashboardCombinedLoading,
                                                    onOpenOutletsFilter = { viewModel.openDashboardOutletsFilter() }
                                                )
                                            }

                                            MerchantTab.EARNINGS -> {
                                                EarningsScreen(
                                                    settings = settings,
                                                    orders = orders,
                                                    payouts = payouts,
                                                    period = earningsPeriod,
                                                    onPeriodChange = { viewModel.setEarningsPeriod(it) },
                                                    onRequestPayoutClick = { viewModel.openPayoutDialog() },
                                                    onEditBankDetailsClick = { viewModel.openBankDetailsDialog() }
                                                )
                                            }

                                            MerchantTab.PROFILE -> {
                                                ProfileScreen(
                                                    settings = settings,
                                                    staff = staff,
                                                    changeRequests = changeRequests,
                                                    onRequestAdminChange = { kind, requestedValue, reason ->
                                                        viewModel.submitChangeRequest(kind, requestedValue, reason) { success, err ->
                                                            if (success) {
                                                                viewModel.showSnackbar("Change request submitted to admin")
                                                            } else {
                                                                viewModel.showSnackbar(err ?: "Failed to submit change request")
                                                            }
                                                        }
                                                    },
                                                    onOpenStoreProfile = { viewModel.openStoreProfile() },
                                                    onUpdateMerchantProfile = { ownerName, phone, logoUrl ->
                                                        viewModel.updateMerchantProfile(ownerName, phone, logoUrl)
                                                    },
                                                    onRequestRiderManual = {
                                                        viewModel.openManualBookingForm()
                                                    },
                                                    onOpenColorPicker = { viewModel.openColorPicker() },
                                                    onToggleDarkMode = { viewModel.toggleDarkMode(it) },
                                                    onOpenMapLocation = { viewModel.openMapLocation() },
                                                    onOpenBusinessHours = { viewModel.openBusinessHoursDialog() },
                                                    onOpenStaffDialog = { viewModel.openStaffDialog() },
                                                    onOpenSettings = { viewModel.openSettings() },
                                                    onOpenPrinter = { viewModel.openPrinterScreen() },
                                                    onLogout = { viewModel.logout() },
                                                    isOwner = isOwner,
                                                    canAccessInbox = canAccessInbox,
                                                    unreadInboxCount = unreadInboxCount,
                                                    onOpenInbox = { viewModel.openInbox() },
                                                    canAccessReviews = canAccessReviews,
                                                    reviewsCount = reviewsCount,
                                                    averageRating = averageRating,
                                                    onOpenReviews = { viewModel.openReviews() },
                                                    walletBalance = if (!settings.linkedCustomerUid.isNullOrBlank() && sharedWalletBalance != null) sharedWalletBalance!! else settings.walletBalance,
                                                    isSharedWallet = !settings.linkedCustomerUid.isNullOrBlank(),
                                                    onOpenWallet = { viewModel.openWalletScreen() },
                                                    onUpdateStoreSettings = { storeName, phone, minOrder, prep, sound, vibration, paper, autoPrint ->
                                                        viewModel.updateStoreSettings(
                                                            storeName, phone, minOrder, prep,
                                                            sound, vibration, paper, autoPrint
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Global Modals & Dialogs

                // 1. New Order Alert with sound/vibration (suppressed when service is locked)
                if (!isServiceLocked) {
                    newOrderAlert?.let { alertOrder ->
                        NewOrderAlertDialog(
                            order = alertOrder,
                            onAccept = { prepMinutes ->
                                viewModel.confirmAcceptOrder(alertOrder.id, prepMinutes)
                            },
                            onReject = {
                                viewModel.openRejectDialog(alertOrder)
                            },
                            onDismiss = {
                                viewModel.dismissNewOrderAlert()
                            }
                        )
                    }
                }

                // 2. Reject Order Dialog
                rejectOrderTarget?.let { target ->
                    RejectOrderDialog(
                        order = target,
                        onConfirm = { reason ->
                            viewModel.confirmRejectOrder(target.id, reason)
                        },
                        onDismiss = {
                            viewModel.closeRejectDialog()
                        }
                    )
                }

                // 3. Prep Time Picker Dialog
                prepTimePickerTarget?.let { target ->
                    PrepTimePickerDialog(
                        order = target,
                        defaultMinutes = settings.defaultPrepTime,
                        onConfirm = { prepMinutes ->
                            viewModel.confirmAcceptOrder(target.id, prepMinutes)
                        },
                        onDismiss = {
                            viewModel.closePrepTimePicker()
                        }
                    )
                }

                // 4. Request Rider / Rider Tracking Modal
                requestRiderTarget?.let { target ->
                    RiderTrackingModal(
                        order = target,
                        merchantLocation = settings.storeLocation,
                        isSubmitting = isActionLoading,
                        onConfirmRequestRider = { distanceKm, deliveryFee, note, dropLat, dropLng, dropAddr ->
                            viewModel.confirmRequestRider(target, distanceKm, deliveryFee, note, dropLat, dropLng, dropAddr)
                        },
                        onRetryRiderRequest = {
                            viewModel.retryRiderRequest(target.id)
                        },
                        onCancelRiderRequest = {
                            viewModel.cancelRiderRequest(target.id)
                        },
                        onOpenLocationPicker = {
                            viewModel.openMapLocation()
                        },
                        onDismiss = {
                            viewModel.closeRequestRider()
                        }
                    )
                }

                // 5. Receipt Preview Dialog
                receiptPreviewTarget?.let { target ->
                    ReceiptPreviewDialog(
                        order = target,
                        settings = settings,
                        receiptDesign = receiptDesign,
                        onDismiss = {
                            viewModel.closeReceiptPreview()
                        }
                    )
                }

                // 6. Color Picker Bottom Sheet
                if (showColorPickerSheet) {
                    ColorPickerSheet(
                        currentColorHex = settings.themeAccentHex,
                        onColorSelected = { hex ->
                            viewModel.setThemeAccent(hex)
                        },
                        onDismiss = {
                            viewModel.closeColorPicker()
                        }
                    )
                }

                // 7. Store Pause Sheet
                if (showPauseSheet) {
                    StorePauseSheet(
                        onPause = { minutes ->
                            viewModel.pauseOrders(minutes)
                        },
                        onDismiss = {
                            viewModel.closePauseSheet()
                        }
                    )
                }

                // 8. Close Store with Active Orders Confirmation Dialog
                if (showCloseConfirmation) {
                    val activeCount = orders.count {
                        it.status == com.example.data.model.OrderStatus.PREPARING ||
                                it.status == com.example.data.model.OrderStatus.READY ||
                                it.status == com.example.data.model.OrderStatus.PLACED_ORDER
                    }
                    CloseConfirmationDialog(
                        activeOrdersCount = activeCount,
                        onConfirmClose = {
                            viewModel.confirmStoreClose()
                        },
                        onDismiss = {
                            viewModel.dismissStoreCloseConfirmation()
                        }
                    )
                }


                // 10. Sold Out Duration Dialog
                showSoldOutDurationTarget?.let { item ->
                    SoldOutDurationDialog(
                        item = item,
                        onConfirm = { reason, durationHours ->
                            viewModel.confirmSoldOutWithDuration(item, reason, durationHours)
                        },
                        onDismiss = {
                            viewModel.dismissSoldOutDuration()
                        }
                    )
                }

                // 11. Request Payout Dialog
                if (showPayoutDialog) {
                    RequestPayoutDialog(
                        availableBalance = 845.20,
                        bankName = settings.bankName,
                        accountNumber = settings.accountNumber,
                        onConfirm = { amount ->
                            viewModel.requestPayout(amount)
                        },
                        onDismiss = {
                            viewModel.closePayoutDialog()
                        }
                    )
                }

                // 12. Bank Details Dialog
                if (showBankDetailsDialog) {
                    BankDetailsDialog(
                        initialBank = settings.bankName,
                        initialHolder = settings.accountHolder,
                        initialNum = settings.accountNumber,
                        onSave = { bank, holder, num ->
                            viewModel.saveBankDetails(bank, holder, num)
                        },
                        onDismiss = {
                            viewModel.closeBankDetailsDialog()
                        }
                    )
                }

                // 13. Business Hours Dialog
                if (showBusinessHoursDialog) {
                    BusinessHoursDialog(
                        initialHours = settings.hours,
                        initialAutoSchedule = settings.autoSchedule,
                        initialHolidays = settings.holidays,
                        onSave = { hours, autoSchedule ->
                            viewModel.saveBusinessHours(hours, autoSchedule)
                        },
                        onSaveHolidays = { holidays ->
                            viewModel.saveHolidays(holidays)
                        },
                        onDismiss = {
                            viewModel.closeBusinessHoursDialog()
                        }
                    )
                }

                // 14. Add Staff Dialog
                if (showStaffDialog) {
                    AddStaffDialog(
                        onAdd = { name, role, email ->
                            viewModel.addStaff(name, role, email)
                            viewModel.closeStaffDialog()
                        },
                        onDismiss = {
                            viewModel.closeStaffDialog()
                        }
                    )
                }

                // 15. Manual Rider Booking Sheet (Pop-up booking form that needs no existing order)
                if (showManualBookingForm) {
                    ManualRiderBookingSheet(
                        settings = settings,
                        isSubmitting = isActionLoading,
                        onBookRider = { name, phone, addr, area, lat, lng, what, relOrder, cod, note, dist, delFee, sFee ->
                            viewModel.bookRiderDirect(
                                recipientName = name,
                                recipientPhone = phone,
                                deliveryAddress = addr,
                                dropoffArea = area,
                                dropoffLat = lat,
                                dropoffLng = lng,
                                whatToDeliver = what,
                                relatedOrderNumber = relOrder,
                                codAmount = cod,
                                riderNote = note,
                                distanceKm = dist,
                                deliveryFee = delFee,
                                serviceFee = sFee
                            )
                        },
                        onOpenLocationPicker = {
                            viewModel.openMapLocation()
                        },
                        onDismiss = {
                            viewModel.closeManualBookingForm()
                        }
                    )
                }

                // 16. Rider Request Error Dialog (Never silent on failure)
                riderRequestError?.let { errInfo ->
                    RiderRequestErrorDialog(
                        errorInfo = errInfo,
                        onDismiss = {
                            viewModel.dismissRiderRequestError()
                        }
                    )
                }

                // 17. Edit Order Dialog while Preparing
                editOrderTarget?.let { target ->
                    key(target.id) {
                        OrderEditDialog(
                            order = target,
                            availableShopItems = menuItems.filter { it.businessType == settings.businessType && it.available && it.inStock },
                            onConfirm = { ord, newItems, reason ->
                                viewModel.confirmEditOrder(ord, newItems, reason)
                            },
                            onDismiss = {
                                viewModel.closeEditOrder()
                            }
                        )
                    }
                }

                // 17b. Edit Order Error Dialog with Copy button
                editOrderError?.let { errText ->
                    EditOrderErrorDialog(
                        errorMessage = errText,
                        onDismiss = {
                            viewModel.dismissEditOrderError()
                        }
                    )
                }

                // 18. Cash Out Coming Soon Dialog
                if (showCashOutComingSoon) {
                    AlertDialog(
                        onDismissRequest = { viewModel.closeCashOutComingSoon() },
                        title = { Text("Cash Out", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                        text = { Text("Coming soon. Payout transfers directly from Delivio Wallet will be available in an upcoming update.") },
                        confirmButton = {
                            TextButton(onClick = { viewModel.closeCashOutComingSoon() }) {
                                Text("OK")
                            }
                        }
                    )
                }

                // 19. In-app Inbox Banner
                inboxBannerMessage?.let { bannerText ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 44.dp, start = 16.dp, end = 16.dp),
                        contentAlignment = androidx.compose.ui.Alignment.TopCenter
                    ) {
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shadowElevation = 8.dp,
                            tonalElevation = 6.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.dismissInboxBanner()
                                    viewModel.openInbox()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.Notifications,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = bannerText,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 2
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.dismissInboxBanner() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }


                // 20a. Outlets Selector / Multi-Select Filter Sheet
                outletsSheetMode?.let { mode ->
                    val currentFilterIds = when (mode) {
                        com.example.viewmodel.OutletsSheetMode.SINGLE_SWITCH -> emptySet()
                        com.example.viewmodel.OutletsSheetMode.MULTI_DASHBOARD_FILTER -> dashboardSelectedOutletIds
                        com.example.viewmodel.OutletsSheetMode.MULTI_REPORTS_FILTER -> reportsSelectedOutletIds
                    }
                    OutletsSelectorSheet(
                        mode = mode,
                        outlets = outlets,
                        activeMerchantId = activeMerchantId ?: settings.merchantId,
                        selectedFilterIds = currentFilterIds,
                        newOrderCountByOutlet = newOrderCountByOutlet,
                        currentTime = currentTime,
                        onDismiss = { viewModel.closeOutletsSheet() },
                        onApplySingle = { targetId -> viewModel.switchActiveOutlet(targetId) },
                        onApplyMulti = { selectedIds ->
                            when (mode) {
                                com.example.viewmodel.OutletsSheetMode.MULTI_DASHBOARD_FILTER -> viewModel.applyDashboardOutletFilter(selectedIds)
                                com.example.viewmodel.OutletsSheetMode.MULTI_REPORTS_FILTER -> viewModel.applyReportsOutletFilter(selectedIds)
                                else -> viewModel.closeOutletsSheet()
                            }
                        }
                    )
                }

                // 20b. Outlets PIN Gate Dialog
                pinModalMode?.let { mode ->
                    OutletsPinDialog(
                        mode = mode,
                        isLockedOutInitially = viewModel.isPinLockedOut(),
                        initialLockoutSeconds = viewModel.remainingPinLockoutSeconds(),
                        onCheckLockoutSeconds = { viewModel.remainingPinLockoutSeconds() },
                        onCreatePin = { pin, confirm, cb -> viewModel.submitCreatePin(pin, confirm, cb) },
                        onEnterPin = { pin -> viewModel.submitEnterPin(pin) },
                        onForgotPinClick = { viewModel.openForgotPinFlow() },
                        onVerifyPasswordReset = { pass, cb -> viewModel.verifyPasswordAndResetPin(pass, cb) },
                        onChangePin = { oldPin, newPin, confirm, cb -> viewModel.submitChangePin(oldPin, newPin, confirm, cb) },
                        onDismiss = { viewModel.closePinModal() }
                    )
                }

                // 20c. One-Time Branch Setup Prompt
                branchSetupOutlet?.let { setupOutlet ->
                    BranchSetupDialog(
                        outlet = setupOutlet,
                        existingOutlets = outlets,
                        onSave = { name, code, cb ->
                            viewModel.submitBranchSetup(
                                merchantId = setupOutlet.merchantId,
                                chainId = setupOutlet.chainId,
                                branchName = name,
                                branchCode = code,
                                onResult = cb
                            )
                        }
                    )
                }

                // 20d. Menu Push / Pull / Sync Dialog
                menuSyncDialogMode?.let { mode ->
                    MenuSyncDialog(
                        mode = mode,
                        activeMerchantId = activeMerchantId ?: settings.merchantId,
                        snapshotsByMerchantId = menuSyncSnapshots,
                        isLoadingSnapshots = isMenuSyncLoadingSnapshots,
                        isRunning = isMenuSyncRunning,
                        progress = menuSyncProgress,
                        result = menuSyncResult,
                        onExecutePush = { source, targets -> viewModel.executePushMenu(source, targets) },
                        onExecutePull = { target, source -> viewModel.executePullMenu(target, source) },
                        onExecuteSync = { mine, theirs, fields, plan, res ->
                            viewModel.executeSyncMenu(mine, theirs, fields, plan, res)
                        },
                        onDismiss = { viewModel.closeMenuSyncDialog() }
                    )
                }

                // 21. Help and Support Dialog
                if (showHelpDialog) {
                    AlertDialog(
                        onDismissRequest = { showHelpDialog = false },
                        title = { Text("Help & Support", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Need assistance with your Delivio merchant account, order issues, or payouts?")
                                Text("• Merchant Hotline: (02) 8888-3354", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                Text("• Partner Support: partner-support@delivio.ph", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                Text("• Operating Hours: 24/7 Operations", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = { showHelpDialog = false },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = com.example.ui.theme.DelivioYellow,
                                    contentColor = androidx.compose.ui.graphics.Color(0xFF1A1A1A)
                                )
                            ) {
                                Text("Close", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            }
                        }
                    )
                }
            }
        }
    }
}
