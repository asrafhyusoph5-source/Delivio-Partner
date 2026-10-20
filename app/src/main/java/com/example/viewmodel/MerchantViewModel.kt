package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.MerchantRepository
import com.example.util.PlatformOrderStatuses
import com.example.util.SoundVibrationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

class MerchantViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MerchantRepository.getInstance(application)

    val currentUser = repository.currentUser
    val merchantApplication = repository.merchantApplication
    val isAuthLoading = repository.isAuthLoading
    val authErrorMessage = repository.authErrorMessage

    val settings = repository.settings
    val orders = repository.orders
    val newOrderAlert = repository.newOrderAlert
    val categories = repository.categories
    val menuItems = repository.menuItems
    val payouts = repository.payouts
    val promos = repository.promos
    val staff = repository.staff
    val changeRequests = repository.changeRequests

    // Navigation and Tab States
    private val _currentTab = MutableStateFlow(MerchantTab.DASHBOARD)
    val currentTab: StateFlow<MerchantTab> = _currentTab.asStateFlow()

    private val _selectedOrderDetailId = MutableStateFlow<String?>(null)
    val selectedOrderDetailId: StateFlow<String?> = _selectedOrderDetailId.asStateFlow()

    // Orders tab filter
    private val _orderStatusFilter = MutableStateFlow<String>("New")
    val orderStatusFilter: StateFlow<String> = _orderStatusFilter.asStateFlow()

    // Unaccepted (New) orders count for badge
    val newOrdersCount: StateFlow<Int> = orders.combine(_orderStatusFilter) { orderList, _ ->
        orderList.count { it.status == OrderStatus.PLACED_ORDER }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Menu tab states
    private val _menuSearchQuery = MutableStateFlow("")
    val menuSearchQuery: StateFlow<String> = _menuSearchQuery.asStateFlow()

    private val _menuStockFilter = MutableStateFlow("All") // "All", "In stock", "Sold out"
    val menuStockFilter: StateFlow<String> = _menuStockFilter.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

    private val _isMultiSelectMode = MutableStateFlow(false)
    val isMultiSelectMode: StateFlow<Boolean> = _isMultiSelectMode.asStateFlow()

    private val _selectedItemIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedItemIds: StateFlow<Set<String>> = _selectedItemIds.asStateFlow()

    // Earnings tab filter
    private val _earningsPeriod = MutableStateFlow("Today") // "Today", "This week", "This month"
    val earningsPeriod: StateFlow<String> = _earningsPeriod.asStateFlow()

    // Dialog & Sheet States
    private val _rejectOrderTarget = MutableStateFlow<Order?>(null)
    val rejectOrderTarget: StateFlow<Order?> = _rejectOrderTarget.asStateFlow()

    private val _prepTimePickerTarget = MutableStateFlow<Order?>(null)
    val prepTimePickerTarget: StateFlow<Order?> = _prepTimePickerTarget.asStateFlow()

    private val _requestRiderTarget = MutableStateFlow<Order?>(null)
    val requestRiderTarget: StateFlow<Order?> = _requestRiderTarget.asStateFlow()

    private val _showChooseOrderDialog = MutableStateFlow(false)
    val showChooseOrderDialog: StateFlow<Boolean> = _showChooseOrderDialog.asStateFlow()

    private val _riderRequestError = MutableStateFlow<RiderRequestErrorInfo?>(null)
    val riderRequestError: StateFlow<RiderRequestErrorInfo?> = _riderRequestError.asStateFlow()

    private val _receiptPreviewTarget = MutableStateFlow<Order?>(null)
    val receiptPreviewTarget: StateFlow<Order?> = _receiptPreviewTarget.asStateFlow()

    private val _showColorPickerSheet = MutableStateFlow(false)
    val showColorPickerSheet: StateFlow<Boolean> = _showColorPickerSheet.asStateFlow()

    private val _showPauseSheet = MutableStateFlow(false)
    val showPauseSheet: StateFlow<Boolean> = _showPauseSheet.asStateFlow()

    private val _showCloseConfirmation = MutableStateFlow(false)
    val showCloseConfirmation: StateFlow<Boolean> = _showCloseConfirmation.asStateFlow()

    private val _editingMenuItem = MutableStateFlow<MenuItem?>(null)
    val editingMenuItem: StateFlow<MenuItem?> = _editingMenuItem.asStateFlow()
    private val _isNewMenuItem = MutableStateFlow(false)
    val isNewMenuItem: StateFlow<Boolean> = _isNewMenuItem.asStateFlow()

    private val _showMapLocationScreen = MutableStateFlow(false)
    val showMapLocationScreen: StateFlow<Boolean> = _showMapLocationScreen.asStateFlow()

    private val _showPayoutDialog = MutableStateFlow(false)
    val showPayoutDialog: StateFlow<Boolean> = _showPayoutDialog.asStateFlow()

    private val _showBankDetailsDialog = MutableStateFlow(false)
    val showBankDetailsDialog: StateFlow<Boolean> = _showBankDetailsDialog.asStateFlow()

    private val _showBusinessHoursDialog = MutableStateFlow(false)
    val showBusinessHoursDialog: StateFlow<Boolean> = _showBusinessHoursDialog.asStateFlow()

    private val _showStaffDialog = MutableStateFlow(false)
    val showStaffDialog: StateFlow<Boolean> = _showStaffDialog.asStateFlow()

    private val _showSoldOutDurationTarget = MutableStateFlow<MenuItem?>(null)
    val showSoldOutDurationTarget: StateFlow<MenuItem?> = _showSoldOutDurationTarget.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _isActionLoading = MutableStateFlow(false)
    val isActionLoading: StateFlow<Boolean> = _isActionLoading.asStateFlow()

    fun showSnackbar(message: String) {
        _snackbarMessage.value = message
    }

    fun clearSnackbarMessage() {
        _snackbarMessage.value = null
    }

    // --- Navigation ---

    fun selectTab(tab: MerchantTab) {
        _currentTab.value = tab
        _selectedOrderDetailId.value = null
    }

    fun openOrderDetail(orderId: String) {
        _selectedOrderDetailId.value = orderId
    }

    fun closeOrderDetail() {
        _selectedOrderDetailId.value = null
    }

    // --- Authentication ---

    fun login(email: String, pass: String, isStaff: Boolean = false, onResult: (Boolean, String?) -> Unit) {
        repository.login(email, pass, isStaff, onResult)
    }

    fun sendPasswordReset(email: String, onResult: (Boolean, String?) -> Unit) {
        repository.sendPasswordReset(email, onResult)
    }

    fun registerMerchant(
        application: MerchantApplication,
        pass: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        repository.registerMerchant(application, pass, onResult)
    }

    fun resubmitApplication(
        uid: String,
        updated: MerchantApplication,
        onResult: (Boolean, String?) -> Unit
    ) {
        repository.resubmitApplication(uid, updated, onResult)
    }

    fun logout() {
        repository.logout()
        _currentTab.value = MerchantTab.DASHBOARD
        _selectedOrderDetailId.value = null
    }

    // --- Theme & Appearance ---

    fun setThemeAccent(hex: String) {
        repository.updateThemeAccent(hex)
        _showColorPickerSheet.value = false
    }

    fun toggleDarkMode(isDark: Boolean) {
        repository.toggleDarkMode(isDark)
    }

    fun openColorPicker() {
        _showColorPickerSheet.value = true
    }

    fun closeColorPicker() {
        _showColorPickerSheet.value = false
    }

    // --- Business Controls ---

    fun setBusinessType(type: BusinessType) {
        repository.setBusinessType(type)
    }

    fun toggleStoreOpen() {
        val currentOpen = settings.value.isOpen
        if (currentOpen) {
            val activeOrders = orders.value.filter {
                it.status == OrderStatus.PREPARING || it.status == OrderStatus.READY || it.status == OrderStatus.PLACED_ORDER
            }
            if (activeOrders.isNotEmpty()) {
                _showCloseConfirmation.value = true
            } else {
                repository.toggleStoreOpen(false)
            }
        } else {
            repository.toggleStoreOpen(true)
        }
    }

    fun confirmStoreClose() {
        repository.toggleStoreOpen(false)
        _showCloseConfirmation.value = false
    }

    fun dismissStoreCloseConfirmation() {
        _showCloseConfirmation.value = false
    }

    fun openPauseSheet() {
        _showPauseSheet.value = true
    }

    fun closePauseSheet() {
        _showPauseSheet.value = false
    }

    fun pauseOrders(minutes: Int) {
        repository.pauseOrders(minutes)
        _showPauseSheet.value = false
    }

    fun resumeOrders() {
        repository.resumeOrders()
    }

    fun openMapLocation() {
        _showMapLocationScreen.value = true
    }

    fun closeMapLocation() {
        _showMapLocationScreen.value = false
    }

    fun saveStoreLocation(location: AddressLocation) {
        _isActionLoading.value = true
        repository.saveStoreLocation(location) { success, error ->
            _isActionLoading.value = false
            if (success) {
                _showMapLocationScreen.value = false
                showSnackbar("Store location saved to Firestore")
            } else {
                showSnackbar(error ?: "Failed to save location")
            }
        }
    }

    fun updateMerchantProfile(
        ownerName: String,
        phone: String,
        logoUrl: String,
        onDone: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        _isActionLoading.value = true
        repository.updateMerchantProfile(ownerName, phone, logoUrl) { success, error ->
            _isActionLoading.value = false
            if (success) {
                showSnackbar("Profile updated successfully")
            } else {
                showSnackbar(error ?: "Failed to update profile")
            }
            onDone(success, error)
        }
    }

    fun updateStoreSettings(
        storeName: String,
        phone: String,
        minOrder: Double,
        defaultPrep: Int,
        sound: Boolean,
        vibration: Boolean,
        paperSize: String,
        autoPrint: Boolean
    ) {
        repository.updateStoreSettings(
            storeName, phone, minOrder, defaultPrep,
            sound, vibration, paperSize, autoPrint
        )
    }

    // --- Order Controls ---

    fun setOrderStatusFilter(filter: String) {
        _orderStatusFilter.value = filter
    }

    fun openPrepTimePicker(order: Order) {
        _prepTimePickerTarget.value = order
    }

    fun closePrepTimePicker() {
        _prepTimePickerTarget.value = null
    }

    fun confirmAcceptOrder(orderId: String, prepMinutes: Int) {
        _isActionLoading.value = true
        repository.confirmAcceptOrder(orderId, prepMinutes) { success, error ->
            _isActionLoading.value = false
            if (success) {
                _prepTimePickerTarget.value = null
                SoundVibrationHelper.playClickHaptic(getApplication())
            } else {
                showSnackbar(error ?: "Failed to accept order")
            }
        }
    }

    fun openRejectDialog(order: Order) {
        _rejectOrderTarget.value = order
    }

    fun closeRejectDialog() {
        _rejectOrderTarget.value = null
    }

    fun confirmRejectOrder(orderId: String, reason: String) {
        _isActionLoading.value = true
        repository.confirmRejectOrder(orderId, reason) { success, error ->
            _isActionLoading.value = false
            if (success) {
                _rejectOrderTarget.value = null
                SoundVibrationHelper.playClickHaptic(getApplication())
            } else {
                showSnackbar(error ?: "Failed to reject order")
            }
        }
    }

    fun setOrderReady(orderId: String) {
        _isActionLoading.value = true
        repository.setOrderReady(orderId) { success, error ->
            _isActionLoading.value = false
            if (success) {
                SoundVibrationHelper.playClickHaptic(getApplication())
            } else {
                showSnackbar(error ?: "Failed to mark ready")
            }
        }
    }

    fun cancelOrder(orderId: String, reason: String) {
        _isActionLoading.value = true
        repository.cancelOrder(orderId, reason) { success, error ->
            _isActionLoading.value = false
            if (!success) {
                showSnackbar(error ?: "Failed to cancel order")
            }
        }
    }

    fun openChooseOrderDialog() {
        _showChooseOrderDialog.value = true
    }

    fun closeChooseOrderDialog() {
        _showChooseOrderDialog.value = false
    }

    fun dismissRiderRequestError() {
        _riderRequestError.value = null
    }

    fun openRequestRider(order: Order) {
        _requestRiderTarget.value = order
    }

    fun closeRequestRider() {
        _requestRiderTarget.value = null
    }

    fun confirmRequestRider(
        order: Order,
        distanceKm: Double,
        deliveryFee: Double,
        note: String,
        dropoffLat: Double? = null,
        dropoffLng: Double? = null,
        dropoffAddress: String? = null
    ) {
        _isActionLoading.value = true
        repository.requestRiderForOrder(
            order = order,
            distanceKm = distanceKm,
            deliveryFee = deliveryFee,
            note = note,
            dropoffLat = dropoffLat,
            dropoffLng = dropoffLng,
            dropoffAddress = dropoffAddress
        ) { success, error, fieldsWritten ->
            _isActionLoading.value = false
            if (success) {
                _requestRiderTarget.value = null
                _currentTab.value = MerchantTab.ORDERS
                _orderStatusFilter.value = "Ready"
                SoundVibrationHelper.playClickHaptic(getApplication())
                showSnackbar("Rider requested. Order moved to Ready tab.")
            } else {
                val formattedFields = formatFieldsToString(fieldsWritten)
                _riderRequestError.value = RiderRequestErrorInfo(
                    orderNumber = order.orderNumber,
                    errorMessage = error ?: "Failed to write rider request to Firestore.",
                    fieldsWritten = formattedFields
                )
            }
        }
    }

    private fun formatFieldsToString(map: Map<String, Any>): String {
        val sb = StringBuilder()
        for ((key, value) in map) {
            when (value) {
                is Map<*, *> -> {
                    sb.appendLine("$key: {")
                    for ((subKey, subVal) in value) {
                        sb.appendLine("  $subKey: $subVal")
                    }
                    sb.appendLine("}")
                }
                else -> {
                    sb.appendLine("$key: $value")
                }
            }
        }
        return sb.toString().trimEnd()
    }

    fun retryRiderRequest(orderId: String) {
        repository.retryRiderRequest(orderId) { success, error ->
            if (success) {
                SoundVibrationHelper.playClickHaptic(getApplication())
            } else {
                showSnackbar(error ?: "Failed to retry rider request")
            }
        }
    }

    fun cancelRiderRequest(orderId: String) {
        repository.cancelRiderRequest(orderId) { success, error ->
            if (!success) {
                showSnackbar(error ?: "Failed to cancel rider request")
            }
        }
    }

    fun updateStorePhotos(logoUrl: String, bannerUrl: String) {
        repository.updateStorePhotos(logoUrl, bannerUrl) { success, error ->
            if (success) {
                showSnackbar("Store images updated successfully")
            } else {
                showSnackbar(error ?: "Failed to update store images")
            }
        }
    }

    fun openReceiptPreview(order: Order) {
        _receiptPreviewTarget.value = order
    }

    fun closeReceiptPreview() {
        _receiptPreviewTarget.value = null
    }

    fun dismissNewOrderAlert() {
        repository.dismissNewOrderAlert()
    }

    // --- Menu Controls ---

    fun setMenuSearchQuery(query: String) {
        _menuSearchQuery.value = query
    }

    fun setMenuStockFilter(filter: String) {
        _menuStockFilter.value = filter
    }

    fun selectMenuCategory(catId: String?) {
        _selectedCategoryId.value = catId
    }

    fun toggleMultiSelectMode() {
        _isMultiSelectMode.value = !_isMultiSelectMode.value
        _selectedItemIds.value = emptySet()
    }

    fun toggleItemSelection(itemId: String) {
        val current = _selectedItemIds.value
        _selectedItemIds.value = if (current.contains(itemId)) current - itemId else current + itemId
    }

    fun clearItemSelection() {
        _selectedItemIds.value = emptySet()
        _isMultiSelectMode.value = false
    }

    fun toggleItemAvailability(item: MenuItem) {
        if (item.inStock) {
            _showSoldOutDurationTarget.value = item
        } else {
            repository.toggleItemAvailability(item.id, true)
            SoundVibrationHelper.playClickHaptic(getApplication())
        }
    }

    fun confirmSoldOutWithDuration(item: MenuItem, reason: String, durationHours: Int?) {
        repository.toggleItemAvailability(item.id, false)
        _showSoldOutDurationTarget.value = null
        SoundVibrationHelper.playClickHaptic(getApplication())
    }

    fun dismissSoldOutDuration() {
        _showSoldOutDurationTarget.value = null
    }

    fun bulkMarkInStock() {
        _selectedItemIds.value.forEach { id ->
            repository.toggleItemAvailability(id, true)
        }
        clearItemSelection()
    }

    fun bulkMarkSoldOut() {
        _selectedItemIds.value.forEach { id ->
            repository.toggleItemAvailability(id, false)
        }
        clearItemSelection()
    }

    fun bulkAdjustPrice(percentDelta: Double, fixedDelta: Double) {
        // adjust prices in memory/firebase
        clearItemSelection()
    }

    fun openAddMenuItem(categoryId: String? = null) {
        val defaultCat = categoryId ?: categories.value.firstOrNull { it.businessType == settings.value.businessType }?.id ?: ""
        _editingMenuItem.value = MenuItem(
            id = "",
            categoryId = defaultCat,
            name = "",
            description = "",
            price = 5.00,
            businessType = settings.value.businessType
        )
        _isNewMenuItem.value = true
    }

    fun openEditMenuItem(item: MenuItem) {
        _editingMenuItem.value = item
        _isNewMenuItem.value = false
    }

    fun closeEditMenuItem() {
        _editingMenuItem.value = null
    }

    fun saveMenuItem(item: MenuItem) {
        repository.saveMenuItem(item)
        _editingMenuItem.value = null
        SoundVibrationHelper.playClickHaptic(getApplication())
    }

    fun deleteMenuItem(itemId: String) {
        repository.deleteMenuItem(itemId)
        _editingMenuItem.value = null
    }

    fun duplicateMenuItem(itemId: String) {
        val item = menuItems.value.find { it.id == itemId } ?: return
        val duplicate = item.copy(id = "", name = "${item.name} (Copy)")
        repository.saveMenuItem(duplicate)
    }

    fun addCategory(name: String) {
        repository.addCategory(name, settings.value.businessType)
    }

    fun deleteCategory(catId: String) {
        repository.deleteCategory(catId)
    }

    fun reorderCategory(catId: String, moveUp: Boolean) {
        repository.reorderCategory(catId, moveUp)
    }

    fun toggleCategoryVisibility(catId: String) {
        repository.toggleCategoryVisibility(catId)
    }

    // --- Earnings & Finance ---

    fun setEarningsPeriod(period: String) {
        _earningsPeriod.value = period
    }

    fun openPayoutDialog() {
        _showPayoutDialog.value = true
    }

    fun closePayoutDialog() {
        _showPayoutDialog.value = false
    }

    fun requestPayout(amount: Double): Boolean {
        repository.requestPayout(amount)
        _showPayoutDialog.value = false
        SoundVibrationHelper.playClickHaptic(getApplication())
        return true
    }

    fun openBankDetailsDialog() {
        _showBankDetailsDialog.value = true
    }

    fun closeBankDetailsDialog() {
        _showBankDetailsDialog.value = false
    }

    fun saveBankDetails(bankName: String, accountHolder: String, accountNumber: String) {
        repository.saveBankDetails(bankName, accountHolder, accountNumber)
        _showBankDetailsDialog.value = false
    }

    // --- Profile Dialogs ---

    fun openBusinessHoursDialog() {
        _showBusinessHoursDialog.value = true
    }

    fun closeBusinessHoursDialog() {
        _showBusinessHoursDialog.value = false
    }

    fun openStaffDialog() {
        _showStaffDialog.value = true
    }

    fun closeStaffDialog() {
        _showStaffDialog.value = false
    }

    fun addStaff(name: String, role: String, email: String) {
        repository.addStaff(name, "", email, "Staff1234!", role) { _, _ -> }
    }

    fun addStaff(
        name: String,
        phone: String,
        email: String,
        tempPass: String,
        role: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        repository.addStaff(name, phone, email, tempPass, role, onResult)
    }

    fun submitChangeRequest(
        kind: String,
        requestedValue: String,
        reason: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        repository.submitChangeRequest(kind, requestedValue, reason, onResult)
    }
}

enum class MerchantTab(val label: String) {
    ORDERS("Orders"),
    MENU("Menu"),
    DASHBOARD("Dashboard"),
    EARNINGS("Earnings"),
    PROFILE("Profile")
}
