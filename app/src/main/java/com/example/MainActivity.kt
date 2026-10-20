package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.*
import com.example.ui.screens.*
import com.example.ui.theme.DelivioPartnerTheme
import com.example.data.model.OrderStatus
import com.example.viewmodel.MerchantTab
import com.example.viewmodel.MerchantViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MerchantViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
            val merchantApplication by viewModel.merchantApplication.collectAsStateWithLifecycle()
            val isAuthLoading by viewModel.isAuthLoading.collectAsStateWithLifecycle()
            val authErrorMessage by viewModel.authErrorMessage.collectAsStateWithLifecycle()

            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val orders by viewModel.orders.collectAsStateWithLifecycle()
            val newOrderAlert by viewModel.newOrderAlert.collectAsStateWithLifecycle()
            val categories by viewModel.categories.collectAsStateWithLifecycle()
            val menuItems by viewModel.menuItems.collectAsStateWithLifecycle()
            val payouts by viewModel.payouts.collectAsStateWithLifecycle()
            val promos by viewModel.promos.collectAsStateWithLifecycle()
            val staff by viewModel.staff.collectAsStateWithLifecycle()
            val changeRequests by viewModel.changeRequests.collectAsStateWithLifecycle()

            val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
            val selectedOrderDetailId by viewModel.selectedOrderDetailId.collectAsStateWithLifecycle()
            val newOrdersCount by viewModel.newOrdersCount.collectAsStateWithLifecycle()
            val orderStatusFilter by viewModel.orderStatusFilter.collectAsStateWithLifecycle()

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
            val riderRequestError by viewModel.riderRequestError.collectAsStateWithLifecycle()
            val isActionLoading by viewModel.isActionLoading.collectAsStateWithLifecycle()

            var isRegistering by remember { mutableStateOf(false) }

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
                        onSaveLocation = { viewModel.saveStoreLocation(it) },
                        onBack = { viewModel.closeMapLocation() }
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
                            onPrintReceipt = { viewModel.openReceiptPreview(it) }
                        )
                    } else {
                        viewModel.closeOrderDetail()
                    }
                } else {
                    // Main Merchant Dashboard & Tabs
                    Scaffold(
                        topBar = {
                            DelivioTopBar(
                                settings = settings,
                                onToggleOpen = { viewModel.toggleStoreOpen() }
                            )
                        },
                        bottomBar = {
                            DelivioBottomBar(
                                selectedTab = currentTab,
                                newOrdersBadgeCount = newOrdersCount,
                                onTabSelected = { viewModel.selectTab(it) }
                            )
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
                                        onOrderClick = { viewModel.openOrderDetail(it.id) },
                                        onAcceptClick = { viewModel.openPrepTimePicker(it) },
                                        onRejectClick = { viewModel.openRejectDialog(it) },
                                        onSetReadyClick = { viewModel.setOrderReady(it.id) },
                                        onRequestRiderClick = { viewModel.openRequestRider(it) },
                                        onPrintReceiptClick = { viewModel.openReceiptPreview(it) }
                                    )
                                }

                                MerchantTab.MENU -> {
                                    MenuScreen(
                                        categories = categories,
                                        menuItems = menuItems,
                                        promos = promos,
                                        businessType = settings.businessType,
                                        searchQuery = menuSearchQuery,
                                        onSearchQueryChange = { viewModel.setMenuSearchQuery(it) },
                                        stockFilter = menuStockFilter,
                                        onStockFilterChange = { viewModel.setMenuStockFilter(it) },
                                        selectedCategoryId = selectedCategoryId,
                                        onCategorySelect = { viewModel.selectMenuCategory(it) },
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
                                        onAddCategory = { viewModel.addCategory(it) },
                                        onReorderCategory = { id, up -> viewModel.reorderCategory(id, up) },
                                        onToggleCategoryVisibility = { viewModel.toggleCategoryVisibility(it) },
                                        onDeleteCategory = { viewModel.deleteCategory(it) }
                                    )
                                }

                                MerchantTab.DASHBOARD -> {
                                    val soldOutCount = menuItems.count { it.businessType == settings.businessType && !it.inStock }
                                    val lowStockCount = menuItems.count {
                                        it.businessType == settings.businessType && it.stockCount <= it.lowStockAlertThreshold
                                    }

                                    DashboardScreen(
                                        settings = settings,
                                        orders = orders,
                                        onToggleOpen = { viewModel.toggleStoreOpen() },
                                        onPauseClick = { viewModel.openPauseSheet() },
                                        onResumeClick = { viewModel.resumeOrders() },
                                        onOrderClick = { viewModel.openOrderDetail(it.id) },
                                        onViewAllOrders = { viewModel.selectTab(MerchantTab.ORDERS) },
                                        soldOutItemsCount = soldOutCount,
                                        lowStockCount = lowStockCount
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
                                        onUpdateMerchantProfile = { ownerName, phone, logoUrl ->
                                            viewModel.updateMerchantProfile(ownerName, phone, logoUrl)
                                        },
                                        onRequestRiderManual = {
                                            viewModel.openChooseOrderDialog()
                                        },
                                        onOpenColorPicker = { viewModel.openColorPicker() },
                                        onToggleDarkMode = { viewModel.toggleDarkMode(it) },
                                        onOpenMapLocation = { viewModel.openMapLocation() },
                                        onOpenBusinessHours = { viewModel.openBusinessHoursDialog() },
                                        onOpenStaffDialog = { viewModel.openStaffDialog() },
                                        onLogout = { viewModel.logout() },
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

                // Global Modals & Dialogs

                // 1. New Order Alert with sound/vibration
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

                // 9. Edit / Add Menu Item Dialog
                editingMenuItem?.let { item ->
                    MenuItemEditDialog(
                        item = item,
                        isNew = isNewMenuItem,
                        categories = categories,
                        businessType = settings.businessType,
                        onSave = { updatedItem ->
                            viewModel.saveMenuItem(updatedItem)
                        },
                        onDelete = { itemId ->
                            viewModel.deleteMenuItem(itemId)
                        },
                        onDismiss = {
                            viewModel.closeEditMenuItem()
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
                        schedule = settings.weeklySchedule,
                        autoScheduleEnabled = settings.autoScheduleEnabled,
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

                // 15. Choose Order for Manual Rider Dispatch Dialog
                if (showChooseOrderDialog) {
                    ChooseOrderForDispatchDialog(
                        orders = orders,
                        onSelectOrder = { selectedOrder ->
                            viewModel.closeChooseOrderDialog()
                            viewModel.openRequestRider(selectedOrder)
                        },
                        onDismiss = {
                            viewModel.closeChooseOrderDialog()
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
            }
        }
    }
}
