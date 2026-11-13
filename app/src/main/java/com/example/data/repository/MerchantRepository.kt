package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.model.*
import com.example.util.FormatUtils
import com.example.util.MenuSyncEngine
import com.example.util.MenuSyncResult
import com.example.util.OutletMenuSnapshot
import com.example.util.OutletsPinHelper
import com.example.util.OutletsPinSessionManager
import com.example.util.OutletsUtils
import com.example.util.PinVerifyResult
import com.example.util.PlatformOrderStatuses
import com.example.util.FOOD_CATEGORIES
import com.example.util.CONVENIENCE_CATEGORIES
import com.example.util.SyncConflict
import com.example.util.SyncField
import com.example.util.SyncPlanPreview
import com.example.util.SyncResolution
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.Date
import java.util.UUID

class MerchantRepository private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("delivio_partner_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private fun ensureFirebaseInitialized(ctx: Context) {
        if (FirebaseApp.getApps(ctx).isEmpty()) {
            try {
                FirebaseApp.initializeApp(ctx)
            } catch (e: Exception) {
                Log.w("MerchantRepo", "Default FirebaseApp resource init failed, applying fallback", e)
            }
            if (FirebaseApp.getApps(ctx).isEmpty()) {
                try {
                    val options = FirebaseOptions.Builder()
                        .setApplicationId("1:490983557590:android:a1f1b3c89045b584467c77")
                        .setProjectId("delivio-371d3")
                        .setApiKey("AIzaSyB4ztWjU56WNWX9UKoKC1Icrm5eXxdMxjU")
                        .setStorageBucket("delivio-371d3.firebasestorage.app")
                        .build()
                    FirebaseApp.initializeApp(ctx, options)
                    Log.i("MerchantRepo", "Initialized FirebaseApp with project delivio-371d3 options")
                } catch (e2: Exception) {
                    Log.e("MerchantRepo", "Fallback FirebaseApp init failed", e2)
                }
            }
        }
    }

    private val auth: FirebaseAuth? by lazy {
        try {
            ensureFirebaseInitialized(appContext)
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.e("MerchantRepo", "FirebaseAuth not available", e)
            null
        }
    }

    private val firestore: FirebaseFirestore? by lazy {
        try {
            ensureFirebaseInitialized(appContext)
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e("MerchantRepo", "FirebaseFirestore not available", e)
            null
        }
    }

    // Active session
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _merchantId = MutableStateFlow<String?>(null)
    val merchantId: StateFlow<String?> = _merchantId.asStateFlow()

    private val _merchantApplication = MutableStateFlow<MerchantApplication?>(null)
    val merchantApplication: StateFlow<MerchantApplication?> = _merchantApplication.asStateFlow()

    private val _authErrorMessage = MutableStateFlow<String?>(null)
    val authErrorMessage: StateFlow<String?> = _authErrorMessage.asStateFlow()

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    // Settings
    private val _settings = MutableStateFlow(
        MerchantSettings(
            storeName = prefs.getString("store_name", "") ?: "",
            themeAccentHex = prefs.getString("theme_accent", "#FFC107") ?: "#FFC107",
            isDarkMode = prefs.getBoolean("is_dark_mode", false),
            storeLocation = AddressLocation(
                street = prefs.getString("loc_street", "") ?: "",
                city = prefs.getString("loc_city", "") ?: "",
                latitude = prefs.getFloat("loc_lat", 0f).toDouble(),
                longitude = prefs.getFloat("loc_lng", 0f).toDouble()
            )
        )
    )
    val settings: StateFlow<MerchantSettings> = _settings.asStateFlow()

    // Orders
    private val _orders = MutableStateFlow<List<Order>>(emptyList())
    val orders: StateFlow<List<Order>> = _orders.asStateFlow()

    // New Incoming Order Alert
    private val _newOrderAlert = MutableStateFlow<Order?>(null)
    val newOrderAlert: StateFlow<Order?> = _newOrderAlert.asStateFlow()

    // Menu Categories & Items
    private val _categories = MutableStateFlow<List<MenuCategory>>(emptyList())
    val categories: StateFlow<List<MenuCategory>> = _categories.asStateFlow()

    private val _subcategories = MutableStateFlow<List<Subcategory>>(emptyList())
    val subcategories: StateFlow<List<Subcategory>> = _subcategories.asStateFlow()

    private val _menuItems = MutableStateFlow<List<MenuItem>>(emptyList())
    val menuItems: StateFlow<List<MenuItem>> = _menuItems.asStateFlow()

    private val _menus = MutableStateFlow<List<ShopMenu>>(emptyList())
    val menus: StateFlow<List<ShopMenu>> = _menus.asStateFlow()

    // Payouts & Promos & Staff & Change Requests
    private val _payouts = MutableStateFlow<List<Payout>>(emptyList())
    val payouts: StateFlow<List<Payout>> = _payouts.asStateFlow()

    private val _promos = MutableStateFlow<List<PromoDeal>>(emptyList())
    val promos: StateFlow<List<PromoDeal>> = _promos.asStateFlow()

    private val _staff = MutableStateFlow<List<StaffMember>>(emptyList())
    val staff: StateFlow<List<StaffMember>> = _staff.asStateFlow()

    private val _changeRequests = MutableStateFlow<List<ChangeRequest>>(emptyList())
    val changeRequests: StateFlow<List<ChangeRequest>> = _changeRequests.asStateFlow()

    // Wallet Transactions & Shared Wallet
    private val _walletTransactions = MutableStateFlow<List<WalletTransaction>>(emptyList())
    val walletTransactions: StateFlow<List<WalletTransaction>> = _walletTransactions.asStateFlow()

    private val _sharedWalletBalance = MutableStateFlow<Double?>(null)
    val sharedWalletBalance: StateFlow<Double?> = _sharedWalletBalance.asStateFlow()

    // Active Firestore snapshot listeners
    private var ordersListener: ListenerRegistration? = null
    private var settingsListener: ListenerRegistration? = null
    private var menuListener: ListenerRegistration? = null
    private var categoryListener: ListenerRegistration? = null
    private var subcategoryListener: ListenerRegistration? = null
    private var menusListener: ListenerRegistration? = null
    private var payoutListener: ListenerRegistration? = null
    private var staffListener: ListenerRegistration? = null
    private var changeRequestsListener: ListenerRegistration? = null
    private var walletMerchantListener: ListenerRegistration? = null
    private var walletCustomerListener: ListenerRegistration? = null
    private var linkedUserListener: ListenerRegistration? = null
    private var inboxMerchantListener: ListenerRegistration? = null
    private var inboxAudienceListener: ListenerRegistration? = null
    private var inboxReadsListener: ListenerRegistration? = null
    private var platformSettingsListener: ListenerRegistration? = null
    private var cityListener: ListenerRegistration? = null
    private var reviewsListener: ListenerRegistration? = null
    private var riderReviewsListener: ListenerRegistration? = null
    private var receiptSettingsListener: ListenerRegistration? = null
    private var vouchersListener: ListenerRegistration? = null
    private var redemptionsListener: ListenerRegistration? = null
    private var orderChatsListener: ListenerRegistration? = null
    private var bestSellersJob: Job? = null

    // Multi-outlet state & listeners
    val pinSessionManager = OutletsPinSessionManager(prefs)
    private val _outlets = MutableStateFlow<List<MerchantSettings>>(emptyList())
    val outlets: StateFlow<List<MerchantSettings>> = _outlets.asStateFlow()

    private val _userOutletsPinHash = MutableStateFlow<String?>(null)
    val userOutletsPinHash: StateFlow<String?> = _userOutletsPinHash.asStateFlow()

    private val _userOutletsPinSalt = MutableStateFlow<String?>(null)
    val userOutletsPinSalt: StateFlow<String?> = _userOutletsPinSalt.asStateFlow()

    private val _newOrderCountByOutlet = MutableStateFlow<Map<String, Int>>(emptyMap())
    val newOrderCountByOutlet: StateFlow<Map<String, Int>> = _newOrderCountByOutlet.asStateFlow()

    private val _otherOutletOrderBanner = MutableStateFlow<OtherOutletOrderEvent?>(null)
    val otherOutletOrderBanner: StateFlow<OtherOutletOrderEvent?> = _otherOutletOrderBanner.asStateFlow()

    private var ownerOutletsListener: ListenerRegistration? = null
    private var userPinListener: ListenerRegistration? = null
    private val allOutletOrderListeners = mutableMapOf<String, ListenerRegistration>()
    private val otherOutletKnownOrderIds = mutableMapOf<String, MutableSet<String>>()
    private val otherOutletInitialDone = mutableSetOf<String>()

    // Merchant Vouchers & Redemptions State
    private val _vouchers = MutableStateFlow<List<MerchantVoucher>>(emptyList())
    val vouchers: StateFlow<List<MerchantVoucher>> = _vouchers.asStateFlow()

    private val _redemptions = MutableStateFlow<List<PromoRedemption>>(emptyList())
    val redemptions: StateFlow<List<PromoRedemption>> = _redemptions.asStateFlow()

    // Customer Order Chats State: mapped by orderId -> OrderChatThread
    private val _orderChats = MutableStateFlow<Map<String, OrderChatThread>>(emptyMap())
    val orderChats: StateFlow<Map<String, OrderChatThread>> = _orderChats.asStateFlow()

    private val _newIncomingMessageEvent = MutableSharedFlow<OrderChatThread>(extraBufferCapacity = 10)
    val newIncomingMessageEvent: Flow<OrderChatThread> = _newIncomingMessageEvent.asSharedFlow()

    private var rawVouchers: List<MerchantVoucher> = emptyList()

    // Live Receipt Design from settings/receipt
    private val _receiptDesign = MutableStateFlow(ReceiptDesign())
    val receiptDesign: StateFlow<ReceiptDesign> = _receiptDesign.asStateFlow()

    // Service Lock & City State
    private val _isServiceLocked = MutableStateFlow(false)
    val isServiceLocked: StateFlow<Boolean> = _isServiceLocked.asStateFlow()

    private val _serviceLockMessage = MutableStateFlow<String?>(null)
    val serviceLockMessage: StateFlow<String?> = _serviceLockMessage.asStateFlow()

    private var currentCityId: String = "taguig"
    private var currentCityName: String = "Taguig"
    private var currentServiceKey: String = "food"
    private var platformServicesMap: Map<String, Any?> = emptyMap()
    private var cityServicesMap: Map<String, Any?> = emptyMap()

    // Customer Reviews State
    private val _reviews = MutableStateFlow<List<CustomerReview>>(emptyList())
    val reviews: StateFlow<List<CustomerReview>> = _reviews.asStateFlow()

    private val _averageRating = MutableStateFlow(5.0)
    val averageRating: StateFlow<Double> = _averageRating.asStateFlow()

    private val _reviewsCount = MutableStateFlow(0)
    val reviewsCount: StateFlow<Int> = _reviewsCount.asStateFlow()

    // Rider Reviews State
    private val _riderReviews = MutableStateFlow<List<AnonymousRating>>(emptyList())
    val riderReviews: StateFlow<List<AnonymousRating>> = _riderReviews.asStateFlow()

    private val _riderAverageRating = MutableStateFlow(5.0)
    val riderAverageRating: StateFlow<Double> = _riderAverageRating.asStateFlow()

    private val _riderReviewsCount = MutableStateFlow(0)
    val riderReviewsCount: StateFlow<Int> = _riderReviewsCount.asStateFlow()

    // Inbox state
    private val _inboxMessages = MutableStateFlow<List<InboxMessage>>(emptyList())
    val inboxMessages: StateFlow<List<InboxMessage>> = _inboxMessages.asStateFlow()

    private val _unreadInboxCount = MutableStateFlow(0)
    val unreadInboxCount: StateFlow<Int> = _unreadInboxCount.asStateFlow()

    private val _inboxBannerMessage = MutableStateFlow<String?>(null)
    val inboxBannerMessage: StateFlow<String?> = _inboxBannerMessage.asStateFlow()

    private var rawMerchantInboxList = listOf<InboxMessage>()
    private var rawAudienceInboxList = listOf<InboxMessage>()
    private var rawInboxReadIds = setOf<String>()
    private val shownInboxBannerIds = mutableSetOf<String>()
    private var isInitialInboxLoad = true

    private var rawMerchantTxList = listOf<WalletTransaction>()
    private var rawCustomerTxList = listOf<WalletTransaction>()
    private var listenedLinkedCustomerId: String? = null

    private var previousOrderIds = mutableSetOf<String>()
    private var rawOrders = listOf<Order>()
    private val alertedOrderIds = mutableSetOf<String>()
    private val _scheduledReleaseLeadMinutes = MutableStateFlow<Int>(0)
    val scheduledReleaseLeadMinutes: StateFlow<Int> = _scheduledReleaseLeadMinutes.asStateFlow()

    fun isOrderVisible(order: Order, now: Long = System.currentTimeMillis()): Boolean {
        val sched = order.scheduledFor ?: return true
        if (sched <= 0) return true
        val leadMs = _scheduledReleaseLeadMinutes.value * 60 * 1000L
        val releaseTime = sched - leadMs
        return now >= releaseTime
    }

    fun evaluateAndPublishOrders(now: Long = System.currentTimeMillis()) {
        val isShopEffectiveOpen = com.example.util.BusinessHoursHelper.isEffectiveOpen(_settings.value, isServiceLocked = _isServiceLocked.value)
        val closedAtTimestamp = _settings.value.closedAt

        val visibleList = rawOrders.filter { isOrderVisible(it, now) }

        // Alert on incoming Placed Order (scheduled orders alert once they become visible/due)
        val newPlaced = visibleList.filter {
            it.status == OrderStatus.PLACED_ORDER &&
            !alertedOrderIds.contains(it.id) &&
            (isShopEffectiveOpen || closedAtTimestamp == null || it.placedAtTimestamp <= closedAtTimestamp)
        }
        if (newPlaced.isNotEmpty()) {
            newPlaced.forEach { alertedOrderIds.add(it.id) }
            if (!_isServiceLocked.value) {
                _newOrderAlert.value = newPlaced.first()
            }
        }

        alertedOrderIds.retainAll(rawOrders.map { it.id }.toSet())
        _orders.value = visibleList

        val activeId = _merchantId.value
        if (!activeId.isNullOrBlank()) {
            val activeNewCount = visibleList.count { it.status == OrderStatus.PLACED_ORDER }
            _newOrderCountByOutlet.value = _newOrderCountByOutlet.value.toMutableMap().apply {
                put(activeId, activeNewCount)
            }
        }
    }

    fun recheckScheduledOrders(now: Long = System.currentTimeMillis()) {
        evaluateAndPublishOrders(now)
    }

    init {
        ensureFirebaseInitialized(appContext)
        checkExistingAuth()
    }

    private fun checkExistingAuth() {
        val currentFirebaseUser = auth?.currentUser
        if (currentFirebaseUser != null) {
            handleAuthenticatedUser(currentFirebaseUser.uid, currentFirebaseUser.email ?: "", isStaffAttempt = false) { _, _ -> }
        }
    }

    // --- Authentication ---

    fun login(email: String, pass: String, isStaff: Boolean, onResult: (Boolean, String?) -> Unit) {
        val fbAuth = auth
        if (fbAuth == null) {
            val msg = "Firebase Auth is not available. Please verify backend setup."
            _authErrorMessage.value = msg
            onResult(false, msg)
            return
        }

        _isAuthLoading.value = true
        _authErrorMessage.value = null

        fbAuth.signInWithEmailAndPassword(email.trim(), pass.trim())
            .addOnSuccessListener { authResult ->
                val uid = authResult.user?.uid ?: ""
                val userEmail = authResult.user?.email ?: email
                handleAuthenticatedUser(uid, userEmail, isStaff) { success, msg ->
                    _isAuthLoading.value = false
                    if (!success) {
                        _authErrorMessage.value = msg
                    }
                    onResult(success, msg)
                }
            }
            .addOnFailureListener { ex ->
                _isAuthLoading.value = false
                val msg = ex.localizedMessage ?: "Invalid email or password"
                _authErrorMessage.value = msg
                onResult(false, msg)
            }
    }

    fun handleAuthenticatedUser(
        uid: String,
        email: String,
        isStaffAttempt: Boolean,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val db = firestore
        if (db == null) {
            onComplete(false, "Firestore database not available")
            return
        }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                if (userDoc.exists()) {
                    val role = userDoc.getString("role")?.trim()?.lowercase() ?: ""
                    val isSuspended = userDoc.getString("status")?.equals("suspended", ignoreCase = true) == true ||
                            userDoc.getBoolean("suspended") == true ||
                            userDoc.getBoolean("active") == false

                    if (isSuspended) {
                        auth?.signOut()
                        val err = if (role == "staff") "Your staff account has been suspended." else "Your merchant account has been suspended."
                        onComplete(false, err)
                        return@addOnSuccessListener
                    }

                    if (isStaffAttempt) {
                        if (role != "staff") {
                            auth?.signOut()
                            onComplete(false, "Not a staff account")
                            return@addOnSuccessListener
                        }
                        val mId = userDoc.getString("merchantId") ?: ""
                        if (mId.isBlank()) {
                            auth?.signOut()
                            onComplete(false, "No merchant assigned to this staff account")
                            return@addOnSuccessListener
                        }
                        val user = User(
                            id = uid,
                            name = userDoc.getString("name") ?: email.substringBefore("@"),
                            email = email,
                            role = "staff",
                            merchantId = mId
                        )
                        detachAccountListeners()
                        _outlets.value = emptyList()
                        _currentUser.value = user
                        _merchantId.value = mId
                        attachFirestoreListeners(mId)
                        onComplete(true, null)
                        return@addOnSuccessListener
                    }

                    if (role == "merchant" || role == "owner" || role == "partner") {
                        val fallbackMid = userDoc.getString("merchantId")?.takeIf { it.isNotBlank() } ?: uid
                        val user = User(
                            id = uid,
                            name = userDoc.getString("name") ?: userDoc.getString("ownerName") ?: "Merchant",
                            email = email,
                            role = "merchant",
                            merchantId = fallbackMid
                        )
                        _currentUser.value = user
                        _userOutletsPinHash.value = userDoc.getString("outletsPinHash")?.takeIf { it.isNotBlank() }
                        _userOutletsPinSalt.value = userDoc.getString("outletsPinSalt")?.takeIf { it.isNotBlank() }
                        pinSessionManager.bindOwner(uid)
                        attachOwnerAccountListeners(uid, fallbackMid)
                        onComplete(true, null)
                        return@addOnSuccessListener
                    } else if (role == "staff") {
                        val mId = userDoc.getString("merchantId") ?: ""
                        val user = User(
                            id = uid,
                            name = userDoc.getString("name") ?: "Staff",
                            email = email,
                            role = "staff",
                            merchantId = mId
                        )
                        detachAccountListeners()
                        _outlets.value = emptyList()
                        _currentUser.value = user
                        _merchantId.value = mId
                        attachFirestoreListeners(mId)
                        onComplete(true, null)
                        return@addOnSuccessListener
                    } else {
                        checkApplication(uid, email, onComplete)
                    }
                } else {
                    checkApplication(uid, email, onComplete)
                }
            }
            .addOnFailureListener {
                checkApplication(uid, email, onComplete)
            }
    }

    private fun checkApplication(uid: String, email: String, onComplete: (Boolean, String?) -> Unit) {
        val db = firestore
        if (db == null) {
            onComplete(false, "Database not available")
            return
        }

        db.collection("merchantApplications").document(uid).get()
            .addOnSuccessListener { appDoc ->
                if (appDoc.exists()) {
                    val status = appDoc.getString("status") ?: "Pending"
                    val docsMap = appDoc.get("documents") as? Map<*, *>
                    val app = MerchantApplication(
                        id = uid,
                        ownerUid = uid,
                        businessName = appDoc.getString("businessName") ?: "",
                        ownerName = appDoc.getString("ownerName") ?: "",
                        phone = appDoc.getString("phone") ?: "",
                        email = email,
                        businessType = appDoc.getString("businessType") ?: "food",
                        address = appDoc.getString("address") ?: "",
                        lat = (appDoc.get("lat") as? Number)?.toDouble() ?: 14.5492,
                        lng = (appDoc.get("lng") as? Number)?.toDouble() ?: 121.0503,
                        cityId = appDoc.getString("cityId") ?: "",
                        logoUrl = appDoc.getString("logoUrl") ?: "",
                        storePhotoUrl = appDoc.getString("storePhotoUrl") ?: "",
                        permitUrl = (docsMap?.get("permitUrl") as? String) ?: "",
                        dtiSecUrl = (docsMap?.get("dtiSecUrl") as? String) ?: "",
                        barangayUrl = (docsMap?.get("barangayUrl") as? String) ?: "",
                        validIdUrl = (docsMap?.get("validIdUrl") as? String) ?: "",
                        status = status,
                        rejectionReason = appDoc.getString("rejectionReason")
                    )
                    _merchantApplication.value = app
                    onComplete(false, "Application Status: $status")
                } else {
                    auth?.signOut()
                    onComplete(false, "Not a merchant account")
                }
            }
            .addOnFailureListener { ex ->
                auth?.signOut()
                onComplete(false, ex.localizedMessage ?: "Not a merchant account")
            }
    }

    fun sendPasswordReset(email: String, onResult: (Boolean, String?) -> Unit) {
        val fbAuth = auth ?: return onResult(false, "Auth unavailable")
        fbAuth.sendPasswordResetEmail(email.trim())
            .addOnSuccessListener { onResult(true, "Reset link sent to $email") }
            .addOnFailureListener { ex -> onResult(false, ex.localizedMessage ?: "Failed to send reset link") }
    }

    fun registerMerchant(
        application: MerchantApplication,
        pass: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val fbAuth = auth ?: return onResult(false, "Auth unavailable")
        val db = firestore ?: return onResult(false, "Database unavailable")

        _isAuthLoading.value = true
        _authErrorMessage.value = null

        fbAuth.createUserWithEmailAndPassword(application.email.trim(), pass)
            .addOnSuccessListener { authResult ->
                val uid = authResult.user?.uid ?: ""
                val appMap = hashMapOf<String, Any>(
                    "ownerUid" to uid,
                    "businessName" to application.businessName,
                    "ownerName" to application.ownerName,
                    "phone" to application.phone,
                    "email" to application.email,
                    "businessType" to application.businessType,
                    "address" to application.address,
                    "lat" to application.lat,
                    "lng" to application.lng,
                    "cityId" to application.cityId,
                    "logoUrl" to application.logoUrl,
                    "storePhotoUrl" to application.storePhotoUrl,
                    "documents" to mapOf(
                        "permitUrl" to application.permitUrl,
                        "dtiSecUrl" to application.dtiSecUrl,
                        "barangayUrl" to application.barangayUrl,
                        "validIdUrl" to application.validIdUrl
                    ),
                    "status" to "Pending",
                    "submittedAt" to System.currentTimeMillis()
                )

                db.collection("merchantApplications").document(uid).set(appMap)
                    .addOnSuccessListener {
                        _isAuthLoading.value = false
                        val savedApp = application.copy(id = uid, ownerUid = uid, status = "Pending")
                        _merchantApplication.value = savedApp
                        onResult(true, "Application submitted. Wait for admin approval.")
                    }
                    .addOnFailureListener { ex ->
                        _isAuthLoading.value = false
                        onResult(false, "Failed to submit application: ${ex.localizedMessage}")
                    }
            }
            .addOnFailureListener { ex ->
                _isAuthLoading.value = false
                onResult(false, ex.localizedMessage ?: "Registration failed")
            }
    }

    fun resubmitApplication(
        uid: String,
        updated: MerchantApplication,
        onResult: (Boolean, String?) -> Unit
    ) {
        val db = firestore ?: return onResult(false, "Database unavailable")
        val appMap = hashMapOf<String, Any>(
            "businessName" to updated.businessName,
            "ownerName" to updated.ownerName,
            "phone" to updated.phone,
            "address" to updated.address,
            "lat" to updated.lat,
            "lng" to updated.lng,
            "logoUrl" to updated.logoUrl,
            "storePhotoUrl" to updated.storePhotoUrl,
            "documents" to mapOf(
                "permitUrl" to updated.permitUrl,
                "dtiSecUrl" to updated.dtiSecUrl,
                "barangayUrl" to updated.barangayUrl,
                "validIdUrl" to updated.validIdUrl
            ),
            "status" to "Pending",
            "submittedAt" to System.currentTimeMillis()
        )

        db.collection("merchantApplications").document(uid).update(appMap)
            .addOnSuccessListener {
                _merchantApplication.value = updated.copy(status = "Pending")
                onResult(true, "Application resubmitted successfully")
            }
            .addOnFailureListener { ex ->
                onResult(false, ex.localizedMessage ?: "Resubmission failed")
            }
    }

    fun logout() {
        detachAccountListeners()
        detachFirestoreListeners()
        auth?.signOut()
        pinSessionManager.resetSession()
        _currentUser.value = null
        _merchantId.value = null
        _merchantApplication.value = null
        _outlets.value = emptyList()
        _userOutletsPinHash.value = null
        _userOutletsPinSalt.value = null
        _newOrderCountByOutlet.value = emptyMap()
        _otherOutletOrderBanner.value = null
        _orders.value = emptyList()
        _categories.value = emptyList()
        _menuItems.value = emptyList()
        _payouts.value = emptyList()
        _staff.value = emptyList()
        _changeRequests.value = emptyList()
        _inboxMessages.value = emptyList()
        _unreadInboxCount.value = 0
        _inboxBannerMessage.value = null
        shownInboxBannerIds.clear()
        isInitialInboxLoad = true
        _isServiceLocked.value = false
        _serviceLockMessage.value = null
        _reviews.value = emptyList()
        _reviewsCount.value = 0
        _averageRating.value = 5.0
    }

    private fun parseMerchantSettingsDoc(
        snapshot: DocumentSnapshot,
        fallback: MerchantSettings = MerchantSettings()
    ): MerchantSettings {
        val nameFromDoc = snapshot.getString("name")
            ?: snapshot.getString("storeName")
            ?: snapshot.getString("businessName")
            ?: fallback.storeName
        val typeRaw = snapshot.getString("type") ?: snapshot.getString("businessType") ?: "Food & Dining"
        val displayType = when {
            typeRaw.contains("conv", ignoreCase = true) -> "Convenience Store"
            typeRaw.contains("food", ignoreCase = true) || typeRaw.contains("din", ignoreCase = true) -> "Food & Dining"
            else -> typeRaw
        }
        val bType = if (displayType == "Convenience Store") BusinessType.CONVENIENCE else BusinessType.FOOD

        val rawAddr = snapshot.getString("address") ?: ""
        val rawLat = snapshot.getDouble("lat") ?: snapshot.getDouble("latitude")
        val rawLng = snapshot.getDouble("lng") ?: snapshot.getDouble("longitude")
        val locMap = snapshot.get("storeLocation") as? Map<*, *>
        val loc = AddressLocation(
            street = rawAddr.ifBlank { (locMap?.get("street") as? String) ?: "" },
            city = (locMap?.get("city") as? String) ?: "",
            latitude = rawLat ?: (locMap?.get("latitude") as? Number)?.toDouble() ?: (locMap?.get("lat") as? Number)?.toDouble() ?: 0.0,
            longitude = rawLng ?: (locMap?.get("longitude") as? Number)?.toDouble() ?: (locMap?.get("lng") as? Number)?.toDouble() ?: 0.0,
            notes = (locMap?.get("notes") as? String) ?: ""
        )

        val commRate = when (val cr = snapshot.get("commissionRate")) {
            is Number -> {
                val d = cr.toDouble()
                if (d > 1.0) d / 100.0 else d
            }
            is String -> {
                val d = cr.toDoubleOrNull() ?: 0.15
                if (d > 1.0) d / 100.0 else d
            }
            else -> 0.15
        }

        val autoSched = snapshot.getBoolean("autoSchedule") ?: snapshot.getBoolean("autoScheduleEnabled") ?: true
        val linkedCust = snapshot.getString("linkedCustomerUid") ?: snapshot.getString("linkedCustomerId")
        val wBalance = FormatUtils.parseDouble(snapshot.get("walletBalance")) ?: 0.0
        val shopStatus = snapshot.getString("status") ?: "active"
        val shopTz = snapshot.getString("timezone") ?: "Asia/Manila"

        val hoursRaw = snapshot.get("hours") as? Map<*, *>
        val hoursParsed = if (hoursRaw != null && hoursRaw.isNotEmpty()) {
            hoursRaw.mapNotNull { (k, v) ->
                val dayKey = (k as? String)?.trim()?.lowercase() ?: return@mapNotNull null
                val vMap = v as? Map<*, *> ?: return@mapNotNull null
                val op = (vMap["open"] as? String) ?: "08:00"
                val cl = (vMap["close"] as? String) ?: "22:00"
                val isCl = (vMap["closed"] as? Boolean) ?: false
                dayKey to DayHours(open = op, close = cl, closed = isCl)
            }.toMap()
        } else {
            fallback.hours
        }

        val closedUntilVal = FormatUtils.parseTimestamp(snapshot.get("closedUntil"))
        val openUntilVal = FormatUtils.parseTimestamp(snapshot.get("openUntil"))
        val closedAtVal = FormatUtils.parseTimestamp(snapshot.get("closedAt"))
        val pauseUntilVal = FormatUtils.parseTimestamp(snapshot.get("pauseUntilTime") ?: snapshot.get("pauseUntilTimestamp"))
        val locationChangedAtVal = FormatUtils.parseTimestamp(snapshot.get("locationChangedAt"))

        val chainIdVal = snapshot.getString("chainId")?.trim() ?: ""
        val chainNameVal = snapshot.getString("chainName")?.trim() ?: ""
        val isBranchVal = snapshot.getBoolean("isBranch") ?: false
        val branchNameVal = snapshot.getString("branchName")?.trim() ?: ""
        val branchCodeVal = snapshot.getString("branchCode")?.trim() ?: ""
        val branchSetupDoneVal = snapshot.getBoolean("branchSetupDone")
            ?: (chainIdVal.isBlank() || (branchNameVal.isNotBlank() && branchCodeVal.isNotBlank()))

        return fallback.copy(
            merchantId = snapshot.id,
            ownerUid = snapshot.getString("ownerUid")?.trim() ?: fallback.ownerUid,
            chainId = chainIdVal,
            chainName = chainNameVal,
            isBranch = isBranchVal,
            branchName = branchNameVal,
            branchCode = branchCodeVal,
            branchSetupDone = branchSetupDoneVal,
            storeName = nameFromDoc,
            ownerName = snapshot.getString("ownerName") ?: fallback.ownerName,
            email = snapshot.getString("email") ?: fallback.email,
            phone = snapshot.getString("phone") ?: fallback.phone,
            businessType = bType,
            type = displayType,
            isOpen = snapshot.getBoolean("isOpen") ?: true,
            isPaused = snapshot.getBoolean("isPaused") ?: false,
            pauseUntilTimestamp = pauseUntilVal,
            autoScheduleEnabled = autoSched,
            autoSchedule = autoSched,
            hours = hoursParsed,
            storeLocation = loc,
            minimumOrder = snapshot.getDouble("minimumOrder") ?: 0.0,
            defaultPrepTime = snapshot.getLong("defaultPrepTime")?.toInt() ?: 15,
            soundEnabled = snapshot.getBoolean("soundEnabled") ?: true,
            vibrationEnabled = snapshot.getBoolean("vibrationEnabled") ?: true,
            themeAccentHex = snapshot.getString("themeAccentHex") ?: fallback.themeAccentHex,
            isDarkMode = snapshot.getBoolean("isDarkMode") ?: fallback.isDarkMode,
            logoUrl = snapshot.getString("logoUrl")?.trim() ?: "",
            bannerUrl = snapshot.getString("bannerUrl")?.trim() ?: "",
            imageUrl = snapshot.getString("imageUrl")?.trim() ?: "",
            description = snapshot.getString("description")?.trim() ?: "",
            bankName = snapshot.getString("bankName") ?: "",
            accountHolder = snapshot.getString("accountHolder") ?: "",
            accountNumber = snapshot.getString("accountNumber") ?: "",
            commissionRate = commRate,
            cityId = snapshot.getString("cityId") ?: snapshot.getString("city") ?: fallback.cityId.ifBlank { "taguig" },
            linkedCustomerUid = linkedCust,
            walletBalance = wBalance,
            status = shopStatus,
            timezone = shopTz,
            closedUntil = closedUntilVal,
            openUntil = openUntilVal,
            closedAt = closedAtVal,
            locationChangedAt = locationChangedAtVal,
            holidays = run {
                val rawHolidays = snapshot.get("holidays") as? List<*> ?: emptyList<Any>()
                rawHolidays.mapNotNull { hObj ->
                    val hMap = hObj as? Map<*, *> ?: return@mapNotNull null
                    val fromVal = FormatUtils.parseTimestamp(hMap["from"]) ?: 0L
                    val toVal = FormatUtils.parseTimestamp(hMap["to"]) ?: 0L
                    com.example.data.model.HolidaySchedule(
                        id = (hMap["id"] as? String) ?: java.util.UUID.randomUUID().toString(),
                        from = fromVal,
                        to = toVal,
                        note = (hMap["note"] as? String) ?: ""
                    )
                }
            },
            bluetoothPrinterName = snapshot.getString("bluetoothPrinterName") ?: fallback.bluetoothPrinterName,
            bluetoothPrinterAddress = snapshot.getString("bluetoothPrinterAddress") ?: fallback.bluetoothPrinterAddress
        )
    }

    private fun parseOrderDoc(doc: DocumentSnapshot, fallbackMerchantId: String): Order? {
        val rawSplitChild = doc.get("isSplitChild") ?: doc.get("splitChild")
        val isSplitChildVal = when (rawSplitChild) {
            is Boolean -> rawSplitChild
            is String -> rawSplitChild.equals("true", ignoreCase = true) || rawSplitChild == "1"
            is Number -> rawSplitChild.toInt() == 1
            else -> false
        }
        if (isSplitChildVal) return null

        val statusStr = doc.getString("status") ?: PlatformOrderStatuses.PLACED_ORDER
        val orderStatus = OrderStatus.fromString(statusStr)

        val itemsRaw = (doc.get("items") as? List<*>) ?: emptyList<Any>()
        val items = itemsRaw.mapIndexedNotNull { idx, itemObj ->
            val itm = itemObj as? Map<*, *> ?: return@mapIndexedNotNull null
            val modsRaw = (itm["modifiers"] as? List<*>) ?: emptyList<Any>()
            val mods = modsRaw.mapNotNull { mObj ->
                val m = mObj as? Map<*, *> ?: return@mapNotNull null
                OrderModifier(
                    name = (m["name"] as? String) ?: (m["title"] as? String) ?: "",
                    price = FormatUtils.parseDouble(m["price"]) ?: 0.0
                )
            }
            val priceVal = FormatUtils.parseDouble(itm["price"])
                ?: FormatUtils.parseDouble(itm["unitPrice"])
                ?: FormatUtils.parseDouble(itm["itemPrice"])
                ?: 0.0
            val qtyVal = FormatUtils.parseInt(itm["qty"])
                ?: FormatUtils.parseInt(itm["quantity"])
                ?: 1
            val finalQty = if (qtyVal > 0) qtyVal else 1
            val selRaw = (itm["selections"] as? List<*>) ?: emptyList<Any>()
            val selections = selRaw.mapNotNull { sObj ->
                val sm = sObj as? Map<*, *> ?: return@mapNotNull null
                OrderItemSelection(
                    setId = (sm["setId"] as? String) ?: "",
                    setName = (sm["setName"] as? String) ?: "",
                    choiceId = (sm["choiceId"] as? String) ?: "",
                    choiceName = (sm["choiceName"] as? String) ?: (sm["name"] as? String) ?: "",
                    price = FormatUtils.parseDouble(sm["price"]) ?: 0.0
                )
            }
            val basePriceVal = FormatUtils.parseDouble(itm["basePrice"]) ?: priceVal

            OrderItem(
                id = (itm["id"] as? String) ?: "item_$idx",
                menuItemId = (itm["menuItemId"] as? String) ?: "",
                name = (itm["name"] as? String) ?: (itm["title"] as? String) ?: "Item",
                quantity = finalQty,
                price = priceVal,
                basePrice = basePriceVal,
                unitPrice = priceVal,
                selectedSize = itm["selectedSize"] as? String,
                modifiers = mods,
                selections = selections,
                notes = (itm["notes"] as? String) ?: (itm["note"] as? String)
            )
        }

        val addrMap = doc.get("deliveryAddress") as? Map<*, *>
        val dropLat = doc.getDouble("dropoffLat") ?: doc.getDouble("dropoffLatitude")
            ?: ((doc.get("dropoff") as? Map<*, *>)?.get("lat") as? Number)?.toDouble()
            ?: ((addrMap?.get("latitude") as? Number)?.toDouble())
        val dropLng = doc.getDouble("dropoffLng") ?: doc.getDouble("dropoffLongitude")
            ?: ((doc.get("dropoff") as? Map<*, *>)?.get("lng") as? Number)?.toDouble()
            ?: ((addrMap?.get("longitude") as? Number)?.toDouble())

        val addr = AddressLocation(
            street = (addrMap?.get("street") as? String) ?: (doc.get("dropoffAddress") as? String) ?: "",
            city = (addrMap?.get("city") as? String) ?: "",
            latitude = dropLat ?: 14.5492,
            longitude = dropLng ?: 121.0503,
            notes = (addrMap?.get("notes") as? String) ?: ""
        )

        val rId = doc.getString("riderId") ?: ""
        val rName = doc.getString("riderName") ?: ""
        val rPhone = doc.getString("riderPhone") ?: ""
        val riderLocMap = doc.get("riderLocation") as? Map<*, *>
        val riderLoc = if (riderLocMap != null) {
            AddressLocation(
                latitude = (riderLocMap["latitude"] as? Number)?.toDouble() ?: (riderLocMap["lat"] as? Number)?.toDouble() ?: 0.0,
                longitude = (riderLocMap["longitude"] as? Number)?.toDouble() ?: (riderLocMap["lng"] as? Number)?.toDouble() ?: 0.0
            )
        } else null

        val reqMap = doc.get("riderRequest") as? Map<*, *>
        val reqStatusStr = (reqMap?.get("status") as? String) ?: doc.getString("riderRequestStatus")
        val reqTimestamp = FormatUtils.parseTimestamp(reqMap?.get("requestedAt"))

        val rider = if (rName.isNotBlank() || rId.isNotBlank()) {
            Rider(
                id = rId,
                name = rName.ifBlank { "Rider" },
                phone = rPhone,
                vehiclePlate = doc.getString("riderVehiclePlate") ?: "Motorcycle",
                vehicleType = "Motorcycle"
            )
        } else null

        val riderRequest = if (reqMap != null || reqStatusStr != null) {
            RiderRequest(
                id = doc.id,
                orderId = doc.id,
                status = reqStatusStr ?: "Searching",
                requestedAt = reqTimestamp,
                requestedBy = (reqMap?.get("requestedBy") as? String) ?: "",
                distanceKm = (reqMap?.get("distanceKm") as? Number)?.toDouble() ?: 0.0,
                deliveryFee = (reqMap?.get("deliveryFee") as? Number)?.toDouble() ?: 0.0,
                note = (reqMap?.get("note") as? String) ?: "",
                feePayer = (reqMap?.get("feePayer") as? String) ?: "customer",
                rider = rider,
                pickupAddress = _settings.value.storeLocation,
                dropoffAddress = addr
            )
        } else null

        val feesMap = doc.get("fees") as? Map<*, *>
        val lineTotalsSum = items.sumOf { it.lineTotal }

        val rawSubtotal = FormatUtils.parseDouble(feesMap?.get("subtotal"))
            ?: FormatUtils.parseDouble(doc.get("subtotal"))
            ?: lineTotalsSum
        val finalSubtotal = if (rawSubtotal <= 0.0 && lineTotalsSum > 0.0) lineTotalsSum else rawSubtotal

        val deliveryFeeVal = FormatUtils.parseDouble(feesMap?.get("deliveryFee"))
            ?: FormatUtils.parseDouble(doc.get("deliveryFee"))
            ?: 0.0
        val serviceFeeVal = FormatUtils.parseDouble(feesMap?.get("serviceFee"))
            ?: FormatUtils.parseDouble(doc.get("serviceFee"))
            ?: 0.0
        val discountVal = FormatUtils.parseDouble(feesMap?.get("discount"))
            ?: FormatUtils.parseDouble(doc.get("discount"))
            ?: 0.0
        val platformFeeVal = FormatUtils.parseDouble(feesMap?.get("platformFee"))
            ?: FormatUtils.parseDouble(doc.get("platformFee"))
            ?: 0.0

        val calculatedTotal = (finalSubtotal + deliveryFeeVal + serviceFeeVal - discountVal).coerceAtLeast(0.0)
        val parsedFeesTotal = FormatUtils.parseDouble(feesMap?.get("total"))
        val parsedDocTotal = FormatUtils.parseDouble(doc.get("total"))
        val parsedTotalAmount = FormatUtils.parseDouble(doc.get("totalAmount"))

        var finalTotal = parsedFeesTotal
            ?: parsedDocTotal
            ?: parsedTotalAmount
            ?: if (calculatedTotal > 0.0) calculatedTotal else lineTotalsSum
        if (finalTotal <= 0.0) {
            if (calculatedTotal > 0.0) finalTotal = calculatedTotal
            else if (lineTotalsSum > 0.0) finalTotal = lineTotalsSum
        }

        val rawPaymentMethod = (doc.getString("paymentMethod")
            ?: (doc.get("paymentMethod") as? String)
            ?: (doc.get("payment") as? Map<*, *>)?.get("method") as? String
            ?: "COD").trim()

        val prepMins = doc.getLong("prepMinutes")?.toInt() ?: doc.getLong("prepTimeMinutes")?.toInt()
        val prepEnd = doc.getLong("prepEndTime") ?: if (prepMins != null) {
            (doc.getLong("placedAtTimestamp") ?: System.currentTimeMillis()) + (prepMins * 60 * 1000L)
        } else null

        val tipVal = FormatUtils.parseDouble(feesMap?.get("tip"))
            ?: FormatUtils.parseDouble(doc.get("tip"))
            ?: 0.0
        val rawFulfillment = (doc.getString("fulfillment") ?: "").trim()
        val hasPickup = doc.getBoolean("hasPickup") ?: false
        val scheduledForVal = FormatUtils.parseTimestamp(doc.get("scheduledFor"))
        val editedAtVal = FormatUtils.parseTimestamp(doc.get("editedAt"))
        val isEditedVal = doc.getBoolean("isEdited") ?: (editedAtVal != null)
        val editedByVal = doc.getString("editedBy") ?: ""
        val editNoteVal = doc.getString("editNote") ?: ""
        val origTotalVal = FormatUtils.parseDouble(doc.get("originalTotal"))
        val cashCollectVal = FormatUtils.parseDouble(doc.get("cashToCollect"))
        val walletAdjustVal = FormatUtils.parseDouble(doc.get("walletAdjustDue"))
        val riderVeh = doc.getString("riderVehicle") ?: doc.getString("riderVehiclePlate") ?: (rider?.vehiclePlate ?: "")

        val origItemsRaw = (doc.get("originalItems") as? List<*>) ?: emptyList<Any>()
        val origItems = origItemsRaw.mapIndexedNotNull { idx, itemObj ->
            val itm = itemObj as? Map<*, *> ?: return@mapIndexedNotNull null
            val pVal = FormatUtils.parseDouble(itm["price"]) ?: FormatUtils.parseDouble(itm["unitPrice"]) ?: 0.0
            val qVal = FormatUtils.parseInt(itm["qty"]) ?: FormatUtils.parseInt(itm["quantity"]) ?: 1
            OrderItem(
                id = (itm["id"] as? String) ?: "orig_$idx",
                name = (itm["name"] as? String) ?: (itm["title"] as? String) ?: "Item",
                quantity = if (qVal > 0) qVal else 1,
                price = pVal,
                unitPrice = pVal
            )
        }

        val feesRawMap: Map<String, Any?> = feesMap?.entries?.associate {
            (it.key?.toString() ?: "") to it.value
        } ?: emptyMap()

        val rawJsonStr = try {
            org.json.JSONObject(doc.data ?: emptyMap<String, Any>()).toString(2)
        } catch (_: Exception) {
            ""
        }

        val rawSplitOut = doc.get("splitOut")
        val hasSplitOut = when (rawSplitOut) {
            null -> false
            is Boolean -> rawSplitOut
            is String -> rawSplitOut.isNotBlank() && !rawSplitOut.equals("false", ignoreCase = true) && rawSplitOut != "0"
            is Collection<*> -> rawSplitOut.isNotEmpty()
            is Map<*, *> -> rawSplitOut.isNotEmpty()
            is Number -> rawSplitOut.toInt() != 0
            else -> true
        }

        val rawHistory = (doc.get("statusHistory") as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
        fun extractHistoryTimestamp(vararg targetStatuses: String): Long? {
            for (entry in rawHistory) {
                val s = (entry["status"] as? String)?.lowercase() ?: ""
                if (targetStatuses.any { it.equals(s, ignoreCase = true) }) {
                    val t = FormatUtils.parseTimestamp(entry["at"])
                        ?: FormatUtils.parseTimestamp(entry["timestamp"])
                    if (t != null) return t
                }
            }
            return null
        }

        val acceptedAtVal = FormatUtils.parseTimestamp(doc.get("acceptedAt"))
            ?: extractHistoryTimestamp("preparing", PlatformOrderStatuses.PREPARING)
        val readyAtVal = FormatUtils.parseTimestamp(doc.get("readyAt"))
            ?: extractHistoryTimestamp("ready", PlatformOrderStatuses.READY)
        val rejectedAtVal = FormatUtils.parseTimestamp(doc.get("rejectedAt"))
            ?: extractHistoryTimestamp("rejected", PlatformOrderStatuses.REJECTED)

        val customerIdVal = (doc.getString("customerId")
            ?: doc.getString("customerUid")
            ?: doc.getString("userId")
            ?: ((doc.get("customer") as? Map<*, *>)?.get("id") as? String)
            ?: ((doc.get("customer") as? Map<*, *>)?.get("uid") as? String)
            ?: doc.getString("customerPhone")
            ?: doc.getString("customerName")
            ?: "").trim()

        val delOptRaw = (doc.getString("deliveryOption")
            ?: ((doc.get("delivery") as? Map<*, *>)?.get("option") as? String)
            ?: "").trim()
        val delOptVal = when {
            delOptRaw.contains("saver", ignoreCase = true) -> "Saver"
            delOptRaw.contains("standard", ignoreCase = true) -> "Standard"
            delOptRaw.contains("priority", ignoreCase = true) -> "Priority"
            else -> "Priority"
        }

        val dispatchMap = doc.get("dispatch") as? Map<*, *>
        val holdMinsParsed = FormatUtils.parseInt(dispatchMap?.get("holdMinutes"))
            ?: FormatUtils.parseInt(doc.get("dispatchHoldMinutes"))
            ?: FormatUtils.parseInt(doc.get("holdMinutes"))
        val dispatchHoldMinutesVal = holdMinsParsed ?: if (delOptVal == "Standard" || delOptVal == "Saver") 5 else 0

        val rawOrderNum = doc.getString("orderNumber") ?: doc.getString("orderRef")
        val orderNum = if (!rawOrderNum.isNullOrBlank()) {
            val clean = if (rawOrderNum.startsWith("#")) rawOrderNum.removePrefix("#").trim() else rawOrderNum.trim()
            "#$clean"
        } else {
            "#" + (if (doc.id.length >= 6) doc.id.takeLast(6).uppercase() else doc.id.uppercase())
        }

        val promoMap = (doc.get("promo") as? Map<*, *>) ?: (feesRawMap["promo"] as? Map<*, *>)
        val promoObj = promoMap?.let { p ->
            val pCode = (p["code"] as? String) ?: ""
            val pDiscount = FormatUtils.parseDouble(p["discount"]) ?: 0.0
            val pFundedBy = (p["fundedBy"] as? String) ?: "merchant"
            val pMerchantId = (p["merchantId"] as? String) ?: ""
            if (pCode.isNotBlank() && pDiscount > 0.0) {
                OrderPromo(code = pCode, discount = pDiscount, fundedBy = pFundedBy, merchantId = pMerchantId)
            } else null
        }

        return Order(
            id = doc.id,
            merchantId = doc.getString("merchantId")?.takeIf { it.isNotBlank() } ?: fallbackMerchantId,
            orderNumber = orderNum,
            customerName = doc.getString("customerName") ?: ((doc.get("customer") as? Map<*, *>)?.get("name") as? String) ?: "Customer",
            customerPhone = doc.getString("customerPhone") ?: ((doc.get("customer") as? Map<*, *>)?.get("phone") as? String) ?: "",
            deliveryAddress = addr,
            dropoffLat = dropLat,
            dropoffLng = dropLng,
            items = items,
            subtotal = finalSubtotal,
            deliveryFee = deliveryFeeVal,
            serviceFee = serviceFeeVal,
            discount = discountVal,
            platformFee = platformFeeVal,
            total = finalTotal,
            paymentMethod = rawPaymentMethod,
            status = orderStatus,
            placedAtTimestamp = doc.getLong("placedAtTimestamp") ?: (doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()),
            prepMinutes = prepMins,
            prepEndTime = prepEnd,
            rejectReason = doc.getString("rejectReason") ?: doc.getString("rejectionReason"),
            cancelReason = doc.getString("cancelReason"),
            riderRequest = riderRequest,
            rider = rider,
            riderId = rId,
            riderName = rName,
            riderPhone = rPhone,
            riderLocation = riderLoc,
            cityId = doc.getString("cityId") ?: "",
            serviceType = doc.getString("serviceType") ?: "food",
            hasPickup = hasPickup,
            tip = tipVal,
            fulfillment = rawFulfillment,
            scheduledFor = scheduledForVal,
            isEdited = isEditedVal,
            editedAt = editedAtVal,
            editedBy = editedByVal,
            editNote = editNoteVal,
            originalItems = origItems,
            originalTotal = origTotalVal,
            cashToCollect = cashCollectVal,
            walletAdjustDue = walletAdjustVal,
            riderVehicle = riderVeh,
            note = (doc.getString("note") ?: doc.getString("notes") ?: "").trim(),
            feesRaw = feesRawMap,
            rawJson = rawJsonStr,
            acceptedAt = acceptedAtVal,
            readyAt = readyAtVal,
            deliveryOption = delOptVal,
            dispatchHoldMinutes = dispatchHoldMinutesVal,
            isSplitChild = false,
            splitOut = hasSplitOut,
            promo = promoObj,
            customerId = customerIdVal,
            rejectedAt = rejectedAtVal,
            statusHistory = rawHistory,
            updatedAt = FormatUtils.parseTimestamp(doc.get("updatedAt")) ?: 0L,
            merchantName = doc.getString("merchantName") ?: "",
            branchName = doc.getString("branchName") ?: "",
            branchCode = doc.getString("branchCode") ?: "",
            chainId = doc.getString("chainId") ?: ""
        )
    }

    private fun parseMenuItemDoc(doc: DocumentSnapshot, mId: String): MenuItem {
        val isAvail = doc.getBoolean("available") ?: doc.getBoolean("inStock") ?: true
        val hasSetsVal = doc.getBoolean("hasSets") ?: false
        val rawSets = (doc.get("sets") as? List<*>) ?: emptyList<Any>()
        val parsedSets = rawSets.mapIndexedNotNull { sIdx, sObj ->
            val sMap = sObj as? Map<*, *> ?: return@mapIndexedNotNull null
            val rawChoices = (sMap["choices"] as? List<*>) ?: emptyList<Any>()
            val choices = rawChoices.mapNotNull { cObj ->
                val cMap = cObj as? Map<*, *> ?: return@mapNotNull null
                ProductSetChoice(
                    id = (cMap["id"] as? String) ?: UUID.randomUUID().toString(),
                    name = (cMap["name"] as? String) ?: "",
                    price = FormatUtils.parseDouble(cMap["price"]) ?: 0.0,
                    available = (cMap["available"] as? Boolean) ?: (cMap["inStock"] as? Boolean) ?: true,
                    popular = (cMap["popular"] as? Boolean) ?: false
                )
            }
            val rawCustom = sMap["customName"]?.toString()?.takeIf { it.isNotBlank() && it != "true" && it != "false" }
            val rawSubcatName = sMap["subcategoryName"]?.toString()?.takeIf { it.isNotBlank() }
            val resolvedName = ((sMap["name"] as? String)?.takeIf { it.isNotBlank() })
                ?: rawCustom
                ?: rawSubcatName
                ?: "Set ${sIdx + 1}"

            ProductSet(
                id = (sMap["id"] as? String) ?: UUID.randomUUID().toString(),
                name = resolvedName,
                subcategoryId = null,
                subcategoryName = null,
                customName = resolvedName,
                required = (sMap["required"] as? Boolean) ?: true,
                minSelect = FormatUtils.parseInt(sMap["minSelect"]) ?: if ((sMap["required"] as? Boolean) == false) 0 else 1,
                maxSelect = FormatUtils.parseInt(sMap["maxSelect"]) ?: 1,
                sortOrder = FormatUtils.parseInt(sMap["sortOrder"]) ?: sIdx,
                choices = choices
            )
        }
        val rawMenuIds = (doc.get("menuIds") as? List<*>)?.mapNotNull { it?.toString() }?.filter { it.isNotBlank() }
        val parsedMenuIds = if (!rawMenuIds.isNullOrEmpty()) rawMenuIds else listOf("main")

        val rawAvailMap = doc.get("availability") as? Map<*, *>
        val parsedAvail = if (rawAvailMap != null) {
            val daysList = (rawAvailMap["days"] as? List<*>)
                ?.mapNotNull { it?.toString()?.trim()?.lowercase()?.takeIf { d -> d.isNotBlank() } }
                ?: listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
            val startStr = (rawAvailMap["start"] as? String) ?: "00:00"
            val endStr = (rawAvailMap["end"] as? String) ?: "23:59"
            val dateFromStr = rawAvailMap["dateFrom"] as? String
            val dateToStr = rawAvailMap["dateTo"] as? String
            ProductAvailability(
                days = daysList,
                start = startStr,
                end = endStr,
                dateFrom = dateFromStr,
                dateTo = dateToStr
            )
        } else null

        val parsedTags = (doc.get("tags") as? List<*>)
            ?.mapNotNull { it?.toString()?.trim()?.takeIf { t -> t.isNotBlank() } }
            ?: emptyList()

        return MenuItem(
            id = doc.id,
            merchantId = mId,
            categoryId = doc.getString("categoryId") ?: "",
            categoryName = doc.getString("categoryName") ?: "",
            subcategoryId = doc.getString("subcategoryId"),
            subcategoryName = doc.getString("subcategoryName"),
            menuIds = parsedMenuIds,
            name = doc.getString("name") ?: "",
            description = doc.getString("description") ?: "",
            price = FormatUtils.parseDouble(doc.get("price")) ?: 0.0,
            inStock = isAvail,
            soldOutReason = doc.getString("soldOutReason"),
            businessType = BusinessType.fromString(doc.getString("businessType")),
            iconEmoji = doc.getString("iconEmoji") ?: "🛍️",
            imageUrl = doc.getString("imageUrl") ?: "",
            hasSets = hasSetsVal || parsedSets.isNotEmpty(),
            sets = parsedSets,
            prepTimeMinutes = doc.getLong("prepTime")?.toInt() ?: doc.getLong("prepTimeMinutes")?.toInt() ?: 15,
            tags = parsedTags,
            sku = doc.getString("sku") ?: "",
            unit = doc.getString("unit") ?: "1 pc",
            trackStock = doc.getBoolean("trackStock") ?: false,
            stockCount = doc.getLong("stockCount")?.toInt() ?: 50,
            lowStockAt = doc.getLong("lowStockAt")?.toInt() ?: doc.getLong("lowStockAlertThreshold")?.toInt() ?: 5,
            itemSize = doc.getString("itemSize") ?: "",
            availability = parsedAvail,
            sortOrder = doc.getLong("sortOrder")?.toInt() ?: 0,
            copiedFromMerchantId = doc.getString("copiedFromMerchantId") ?: "",
            copiedFromItemId = doc.getString("copiedFromItemId") ?: "",
            copiedAt = FormatUtils.parseTimestamp(doc.get("copiedAt"))
        )
    }

    private fun parseSubcategoryDoc(doc: DocumentSnapshot): Subcategory? {
        val subName = doc.getString("name") ?: return null
        val sortOrder = doc.getLong("sortOrder")?.toInt() ?: 0
        val createdAt = FormatUtils.parseTimestamp(doc.get("createdAt")) ?: 0L
        val updatedAt = FormatUtils.parseTimestamp(doc.get("updatedAt")) ?: 0L
        return Subcategory(
            id = doc.id,
            name = subName,
            sortOrder = sortOrder,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun parseShopMenuDoc(doc: DocumentSnapshot, mId: String): ShopMenu? {
        val menuName = doc.getString("name") ?: (if (doc.id == "main") "Main Menu" else return null)
        val active = doc.getBoolean("active") ?: true
        val sortOrder = doc.getLong("sortOrder")?.toInt() ?: 0
        val schedMap = doc.get("schedule") as? Map<*, *>
        val parsedSchedule = if (schedMap != null) {
            val daysList = (schedMap["days"] as? List<*>)
                ?.mapNotNull { it?.toString()?.trim()?.lowercase()?.takeIf { d -> d.isNotBlank() } }
                ?: emptyList()
            val startStr = (schedMap["start"] as? String) ?: "00:00"
            val endStr = (schedMap["end"] as? String) ?: "23:59"
            MenuSchedule(days = daysList, start = startStr, end = endStr)
        } else null
        val createdAt = FormatUtils.parseTimestamp(doc.get("createdAt")) ?: System.currentTimeMillis()
        val updatedAt = FormatUtils.parseTimestamp(doc.get("updatedAt")) ?: System.currentTimeMillis()
        return ShopMenu(
            id = doc.id,
            merchantId = mId,
            name = menuName,
            active = active,
            sortOrder = sortOrder,
            schedule = parsedSchedule,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    // --- Firestore Snapshot Listeners ---

    private fun detachAccountListeners() {
        ownerOutletsListener?.remove()
        ownerOutletsListener = null
        userPinListener?.remove()
        userPinListener = null
        allOutletOrderListeners.values.forEach { it.remove() }
        allOutletOrderListeners.clear()
        otherOutletKnownOrderIds.clear()
        otherOutletInitialDone.clear()
    }

    private fun attachOwnerAccountListeners(ownerUid: String, fallbackMerchantId: String) {
        detachAccountListeners()
        val db = firestore ?: return

        userPinListener = db.collection("users").document(ownerUid)
            .addSnapshotListener { doc, _ ->
                if (doc != null && doc.exists()) {
                    _userOutletsPinHash.value = doc.getString("outletsPinHash")?.takeIf { it.isNotBlank() }
                    _userOutletsPinSalt.value = doc.getString("outletsPinSalt")?.takeIf { it.isNotBlank() }
                }
            }

        var fallbackAttached = false
        ownerOutletsListener = db.collection("merchants")
            .whereEqualTo("ownerUid", ownerUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("MerchantRepo", "Owner outlets listener error", error)
                    if (!fallbackAttached && ordersListener == null) {
                        fallbackAttached = true
                        _merchantId.value = fallbackMerchantId
                        attachFirestoreListeners(fallbackMerchantId)
                    }
                    return@addSnapshotListener
                }
                val parsed = snapshot?.documents
                    ?.map { parseMerchantSettingsDoc(it) }
                    ?.sortedWith(
                        compareBy<MerchantSettings>(
                            { if (it.isBranch && it.merchantId != it.chainId) 1 else 0 },
                            { it.displayName.lowercase() }
                        )
                    )
                    ?: emptyList()

                _outlets.value = parsed

                if (parsed.isEmpty()) {
                    allOutletOrderListeners.values.forEach { it.remove() }
                    allOutletOrderListeners.clear()
                    if (!fallbackAttached && ordersListener == null) {
                        fallbackAttached = true
                        _merchantId.value = fallbackMerchantId
                        attachFirestoreListeners(fallbackMerchantId)
                    }
                } else {
                    val savedId = prefs.getString("active_outlet_$ownerUid", null)
                    val currentId = _merchantId.value
                    val resolvedId = when {
                        currentId != null && parsed.any { it.merchantId == currentId } -> currentId
                        !savedId.isNullOrBlank() && parsed.any { it.merchantId == savedId } -> savedId
                        else -> {
                            val mainOutlet = parsed.firstOrNull { !it.isBranch || it.merchantId == ownerUid || it.merchantId == it.chainId }
                            (mainOutlet ?: parsed.first()).merchantId
                        }
                    }
                    prefs.edit().putString("active_outlet_$ownerUid", resolvedId).apply()
                    if (_merchantId.value != resolvedId || ordersListener == null) {
                        _merchantId.value = resolvedId
                        attachFirestoreListeners(resolvedId)
                    }
                    if (parsed.size >= 2) {
                        syncAllOutletOrderListeners(parsed, resolvedId)
                    } else {
                        allOutletOrderListeners.values.forEach { it.remove() }
                        allOutletOrderListeners.clear()
                        otherOutletKnownOrderIds.clear()
                        otherOutletInitialDone.clear()
                    }
                }
            }
    }

    private fun syncAllOutletOrderListeners(outletsList: List<MerchantSettings>, activeMerchantId: String) {
        val db = firestore ?: return
        val cappedOutlets = outletsList.take(10)
        val targetIds = cappedOutlets.map { it.merchantId }.toSet()

        // Remove listeners for outlets no longer in the list or that became active (active already has ordersListener)
        val toRemove = allOutletOrderListeners.keys.filter { it !in targetIds || it == activeMerchantId }
        toRemove.forEach { oid ->
            allOutletOrderListeners.remove(oid)?.remove()
            otherOutletKnownOrderIds.remove(oid)
            otherOutletInitialDone.remove(oid)
        }

        for (outlet in cappedOutlets) {
            val oid = outlet.merchantId
            if (oid == activeMerchantId || allOutletOrderListeners.containsKey(oid)) continue

            allOutletOrderListeners[oid] = db.collection("orders")
                .whereEqualTo("merchantId", oid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val now = System.currentTimeMillis()
                    val parsedOrders = snapshot.documents.mapNotNull { parseOrderDoc(it, oid) }
                    val placedVisible = parsedOrders.filter {
                        it.status == OrderStatus.PLACED_ORDER && isOrderVisible(it, now)
                    }

                    _newOrderCountByOutlet.value = _newOrderCountByOutlet.value.toMutableMap().apply {
                        put(oid, placedVisible.size)
                    }

                    val known = otherOutletKnownOrderIds.getOrPut(oid) { mutableSetOf() }
                    val isInitial = !otherOutletInitialDone.contains(oid)
                    if (isInitial) {
                        otherOutletInitialDone.add(oid)
                        known.addAll(placedVisible.map { it.id })
                    } else {
                        val newlyArrived = placedVisible.filter { !known.contains(it.id) }
                        known.clear()
                        known.addAll(placedVisible.map { it.id })
                        if (newlyArrived.isNotEmpty() && _merchantId.value != oid) {
                            val latest = newlyArrived.maxByOrNull { it.placedAtTimestamp } ?: newlyArrived.first()
                            val currentOutletMeta = _outlets.value.find { it.merchantId == oid } ?: outlet
                            val label = currentOutletMeta.branchName.ifBlank { currentOutletMeta.displayName }
                            _otherOutletOrderBanner.value = OtherOutletOrderEvent(
                                merchantId = oid,
                                outletLabel = label,
                                displayName = currentOutletMeta.displayName,
                                orderId = latest.id,
                                orderNumber = latest.orderNumber
                            )
                        }
                    }
                }
        }
    }

    private fun attachFirestoreListeners(mId: String) {
        detachFirestoreListeners()
        val db = firestore ?: return

        // 1. Settings listener: merchants/{merchantId}
        settingsListener = db.collection("merchants").document(mId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("MerchantRepo", "Settings listener error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val parsedSettings = parseMerchantSettingsDoc(snapshot, _settings.value)
                    _settings.value = parsedSettings

                    val resolvedCityId = parsedSettings.cityId.ifBlank { "taguig" }
                    val resolvedService = if (parsedSettings.type.lowercase().contains("conv") || parsedSettings.businessType == BusinessType.CONVENIENCE) "convenience" else "food"

                    val cityChanged = resolvedCityId != currentCityId
                    currentCityId = resolvedCityId
                    currentServiceKey = resolvedService

                    if (cityChanged || cityListener == null) {
                        syncCityListener(resolvedCityId)
                    }
                    evaluateServiceLock()
                    syncWalletListeners(mId, parsedSettings.linkedCustomerUid)
                }
            }

        // 2. Orders listener: orders where merchantId == mId
        ordersListener = db.collection("orders")
            .whereEqualTo("merchantId", mId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("MerchantRepo", "Orders listener error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { parseOrderDoc(it, mId) }
                        .sortedByDescending { it.placedAtTimestamp }
                    rawOrders = list
                    evaluateAndPublishOrders(System.currentTimeMillis())
                }
            }

        // 3. Menu Items listener: merchants/{merchantId}/items
        menuListener = db.collection("merchants").document(mId).collection("items")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("MerchantRepo", "Menu items listener error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val items = snapshot.documents.map { parseMenuItemDoc(it, mId) }
                    _menuItems.value = items
                    syncCuisineTags(mId, items)

                    items.forEach { itm ->
                        if (itm.trackStock && itm.stockCount <= itm.lowStockAt) {
                            com.example.util.NotificationHelper.showLowStockNotification(
                                context = appContext,
                                productName = itm.name,
                                remainingStock = itm.stockCount
                            )
                        }
                    }
                }
            }

        // 4. Categories listener: merchants/{merchantId}/categories
        categoryListener = db.collection("merchants").document(mId).collection("categories")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("MerchantRepo", "Categories listener error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val cats = snapshot.documents.map { doc ->
                        MenuCategory(
                            id = doc.id,
                            merchantId = mId,
                            name = doc.getString("name") ?: "",
                            sortOrder = doc.getLong("sortOrder")?.toInt() ?: doc.getLong("displayOrder")?.toInt() ?: 0,
                            visible = doc.getBoolean("visible") ?: doc.getBoolean("isVisible") ?: true,
                            businessType = BusinessType.fromString(doc.getString("businessType"))
                        )
                    }.sortedBy { it.sortOrder }
                    _categories.value = cats
                    ensureCategoriesConfigured(mId)
                }
            }

        // 4b. Subcategories listener: merchants/{merchantId}/subcategories
        subcategoryListener = db.collection("merchants").document(mId).collection("subcategories")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("MerchantRepo", "Subcategories listener error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val subcats = snapshot.documents.mapNotNull { parseSubcategoryDoc(it) }
                        .sortedWith(compareBy({ it.sortOrder }, { it.name.lowercase() }))
                    _subcategories.value = subcats
                }
            }

        // 4c. Menus listener: merchants/{merchantId}/menus
        menusListener = db.collection("merchants").document(mId).collection("menus")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("MerchantRepo", "Menus listener error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val parsedMenus = snapshot.documents.mapNotNull { parseShopMenuDoc(it, mId) }.toMutableList()
                    val hasMain = parsedMenus.any { it.id == "main" }
                    if (!hasMain) {
                        val mainDoc = ShopMenu(id = "main", merchantId = mId, name = "Main Menu", sortOrder = 0)
                        parsedMenus.add(0, mainDoc)
                        db.collection("merchants").document(mId).collection("menus").document("main").set(
                            mapOf(
                                "name" to "Main Menu",
                                "active" to true,
                                "sortOrder" to 0,
                                "schedule" to null,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                    _menus.value = parsedMenus.sortedWith(compareBy({ if (it.id == "main") 0 else 1 }, { it.sortOrder }, { it.name.lowercase() }))
                }
            }

        // 5. Payouts listener: merchants/{merchantId}/payouts
        payoutListener = db.collection("merchants").document(mId).collection("payouts")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("MerchantRepo", "Payouts listener error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.map { doc ->
                        Payout(
                            id = doc.id,
                            merchantId = mId,
                            reference = doc.getString("reference") ?: ("PAY-" + doc.id.take(6).uppercase()),
                            amount = doc.getDouble("amount") ?: 0.0,
                            status = doc.getString("status") ?: "Completed",
                            date = doc.getString("date") ?: "",
                            bankName = doc.getString("bankName") ?: "",
                            accountNumber = doc.getString("accountNumber") ?: ""
                        )
                    }
                    _payouts.value = list
                }
            }

        // 6. Staff listener: users where merchantId == mId (merchant owner only)
        if (_currentUser.value?.role == "merchant") {
            staffListener = db.collection("users")
                .whereEqualTo("merchantId", mId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("MerchantRepo", "Staff listener error", error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val list = snapshot.documents.map { doc ->
                            StaffMember(
                                id = doc.id,
                                merchantId = mId,
                                name = doc.getString("name") ?: "Staff",
                                role = doc.getString("staffRole") ?: doc.getString("role") ?: "Staff",
                                email = doc.getString("email") ?: "",
                                active = doc.getBoolean("active") ?: true
                            )
                        }
                        _staff.value = list
                    }
                }
        }

        // 7. Change Requests listener: changeRequests where merchantId == mId
        changeRequestsListener = db.collection("changeRequests")
            .whereEqualTo("merchantId", mId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("MerchantRepo", "Change requests listener error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.map { doc ->
                        ChangeRequest(
                            id = doc.id,
                            merchantId = mId,
                            kind = doc.getString("kind") ?: "shopName",
                            requestedValue = doc.getString("requestedValue") ?: "",
                            reason = doc.getString("reason") ?: "",
                            status = doc.getString("status") ?: "Pending",
                            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                        )
                    }.sortedByDescending { it.createdAt }
                    _changeRequests.value = list
                }
            }

        // 7. Inbox query 1: whereEqualTo("merchantId", mId)
        inboxMerchantListener = db.collection("inbox")
            .whereEqualTo("merchantId", mId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("MerchantRepo", "Inbox merchant query error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    rawMerchantInboxList = snapshot.documents.mapNotNull { parseInboxMessage(it) }
                    updateMergedInbox()
                }
            }

        // 8. Inbox query 2: whereEqualTo("audience", "all_merchants")
        inboxAudienceListener = db.collection("inbox")
            .whereEqualTo("audience", "all_merchants")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("MerchantRepo", "Inbox audience query error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    rawAudienceInboxList = snapshot.documents.mapNotNull { parseInboxMessage(it) }
                    updateMergedInbox()
                }
            }

        // 9. Inbox reads: merchants/{mId}/inboxReads
        inboxReadsListener = db.collection("merchants").document(mId).collection("inboxReads")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("MerchantRepo", "Inbox reads query error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    rawInboxReadIds = snapshot.documents.map { it.id }.toSet()
                    updateMergedInbox()
                }
            }

        // 10. Platform Settings listener: settings/platform (for live platform service switches)
        platformSettingsListener = db.collection("settings").document("platform")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("MerchantRepo", "Platform settings listener error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    platformServicesMap = (snapshot.get("services") as? Map<*, *>)?.mapNotNull { (k, v) ->
                        (k as? String)?.let { it to v }
                    }?.toMap() ?: emptyMap()
                    val leadMins = FormatUtils.parseInt(snapshot.get("scheduledReleaseLeadMinutes")) ?: 0
                    _scheduledReleaseLeadMinutes.value = leadMins
                } else {
                    platformServicesMap = emptyMap()
                    _scheduledReleaseLeadMinutes.value = 0
                }
                evaluateServiceLock()
                evaluateAndPublishOrders(System.currentTimeMillis())
            }

        // 11. Customer Reviews listener: reviews where merchantId == mId (latest 200, sorted in code)
        reviewsListener = db.collection("reviews")
            .whereEqualTo("merchantId", mId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("MerchantRepo", "Reviews query error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.map { parseCustomerReview(it) }
                        .sortedByDescending { it.createdAt }
                        .take(200)
                    _reviews.value = list
                    _reviewsCount.value = list.size
                    _averageRating.value = if (list.isNotEmpty()) {
                        list.map { it.ratingMerchant }.average()
                    } else {
                        5.0
                    }
                }
            }

        // 12. Rider Reviews listener: riderReviews where merchantId == mId (latest 200, sorted in code)
        riderReviewsListener = db.collection("riderReviews")
            .whereEqualTo("merchantId", mId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("MerchantRepo", "Rider reviews query error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.map { parseRiderReview(it) }
                        .sortedByDescending { it.createdAt }
                        .take(200)
                    _riderReviews.value = list
                    _riderReviewsCount.value = list.size
                    _riderAverageRating.value = if (list.isNotEmpty()) {
                        list.map { it.rating }.average()
                    } else {
                        5.0
                    }
                }
            }

        // 13. Receipt Settings listener: settings/receipt (live design)
        receiptSettingsListener = db.collection("settings").document("receipt")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("MerchantRepo", "Receipt settings listener error", error)
                    return@addSnapshotListener
                }
                _receiptDesign.value = parseReceiptDesign(snapshot)
            }

        // 14. Best Sellers: on start and every 6 hours
        startBestSellersPeriodic(mId)

        // 15. Promo Codes (Merchant Vouchers): promoCodes where merchantId == mId
        vouchersListener = db.collection("promoCodes")
            .whereEqualTo("merchantId", mId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("MerchantRepo", "Promo codes query error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    rawVouchers = snapshot.documents.map { parseMerchantVoucher(it) }
                    computeCombinedVouchers()
                }
            }

        // 16. Promo Redemptions: promoRedemptions where merchantId == mId
        redemptionsListener = db.collection("promoRedemptions")
            .whereEqualTo("merchantId", mId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("MerchantRepo", "Promo redemptions query error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.map { parsePromoRedemption(it) }
                    _redemptions.value = list
                    computeCombinedVouchers()
                }
            }

        // 17. Order Chats: orderChats where merchantId == mId and channel == "merchant"
        orderChatsListener?.remove()
        orderChatsListener = db.collection("orderChats")
            .whereEqualTo("merchantId", mId)
            .whereEqualTo("channel", "merchant")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("MerchantRepo", "Order chats listener error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val map = mutableMapOf<String, OrderChatThread>()
                    val previousMap = _orderChats.value
                    snapshot.documents.forEach { doc ->
                        val oId = doc.getString("orderId") ?: ""
                        val cId = doc.id
                        val thread = OrderChatThread(
                            chatId = cId,
                            orderId = oId,
                            orderNumber = doc.getString("orderNumber") ?: "",
                            channel = doc.getString("channel") ?: "merchant",
                            customerId = doc.getString("customerId") ?: "",
                            customerName = doc.getString("customerName") ?: "",
                            merchantId = doc.getString("merchantId") ?: "",
                            merchantName = doc.getString("merchantName") ?: "",
                            riderId = doc.getString("riderId") ?: "",
                            riderName = doc.getString("riderName") ?: "",
                            createdAt = FormatUtils.parseTimestamp(doc.get("createdAt")) ?: 0L,
                            lastMessageAt = FormatUtils.parseTimestamp(doc.get("lastMessageAt")) ?: 0L,
                            lastMessageText = doc.getString("lastMessageText") ?: "",
                            lastSenderRole = doc.getString("lastSenderRole") ?: "",
                            customerReadAt = FormatUtils.parseTimestamp(doc.get("customerReadAt")),
                            partnerReadAt = FormatUtils.parseTimestamp(doc.get("partnerReadAt")),
                            expireAt = FormatUtils.parseTimestamp(doc.get("expireAt"))
                        )
                        if (oId.isNotBlank()) {
                            map[oId] = thread
                        }
                        val prev = previousMap[oId]
                        if (thread.isUnreadForMerchant() && (prev == null || thread.lastMessageAt > prev.lastMessageAt)) {
                            _newIncomingMessageEvent.tryEmit(thread)
                        }
                    }
                    _orderChats.value = map
                    triggerExpiredChatCleanup(force = false)
                }
            }
    }

    fun ensureCategoriesConfigured(customMid: String? = null) {
        val mId = customMid ?: _merchantId.value ?: return
        val db = firestore ?: return
        val isFood = _settings.value.businessType == BusinessType.FOOD ||
                _settings.value.type.contains("food", ignoreCase = true)
        val allowedList = if (isFood) FOOD_CATEGORIES else CONVENIENCE_CATEGORIES

        db.collection("merchants").document(mId).collection("categories").get()
            .addOnSuccessListener { snapshot ->
                val existingDocs = snapshot.documents
                // 1. Create or ensure each allowed category has one document (name, sortOrder, visible true)
                allowedList.forEachIndexed { index, catName ->
                    val existingDoc = existingDocs.find {
                        it.getString("name")?.equals(catName, ignoreCase = true) == true
                    }
                    val expectedOrder = index + 1
                    val docData = mapOf(
                        "name" to catName,
                        "sortOrder" to expectedOrder,
                        "visible" to true,
                        "businessType" to if (isFood) "food" else "convenience"
                    )
                    if (existingDoc == null) {
                        val docId = catName.lowercase().trim().replace("&", "and").replace("[^a-z0-9]+".toRegex(), "_").trim('_')
                        db.collection("merchants").document(mId).collection("categories").document(docId).set(docData)
                    } else {
                        val currentVisible = existingDoc.getBoolean("visible") ?: true
                        val currentOrder = existingDoc.getLong("sortOrder")?.toInt() ?: 0
                        if (!currentVisible || currentOrder != expectedOrder) {
                            db.collection("merchants").document(mId).collection("categories").document(existingDoc.id)
                                .update(mapOf("visible" to true, "sortOrder" to expectedOrder))
                        }
                    }
                }

                // 2. Set visible false on old category documents that are not in the list
                existingDocs.forEach { doc ->
                    val name = doc.getString("name") ?: ""
                    val isAllowed = allowedList.any { it.equals(name, ignoreCase = true) }
                    val currentVisible = doc.getBoolean("visible") ?: true
                    if (!isAllowed && currentVisible) {
                        db.collection("merchants").document(mId).collection("categories").document(doc.id)
                            .update("visible", false)
                    }
                }
            }
            .addOnFailureListener { ex ->
                Log.w("MerchantRepo", "Failed to ensure categories", ex)
            }
    }

    fun syncCuisineTags(customMid: String? = null, customItems: List<MenuItem>? = null) {
        val mId = customMid ?: _merchantId.value ?: return
        val db = firestore ?: return
        val items = customItems ?: _menuItems.value
        val cats = _categories.value

        // distinct categoryName values of items where available == true, for both types
        val cuisineTags = items
            .filter { it.inStock }
            .map { item ->
                val name = if (item.categoryName.isNotBlank()) {
                    item.categoryName
                } else {
                    cats.find { it.id == item.categoryId }?.name ?: ""
                }
                name.trim()
            }
            .filter { it.isNotBlank() }
            .distinct()

        db.collection("merchants").document(mId).update("cuisineTags", cuisineTags)
            .addOnFailureListener { ex ->
                Log.w("MerchantRepo", "Failed to sync cuisineTags", ex)
            }
    }

    private fun detachFirestoreListeners() {
        ordersListener?.remove()
        ordersListener = null
        settingsListener?.remove()
        settingsListener = null
        menuListener?.remove()
        menuListener = null
        categoryListener?.remove()
        categoryListener = null
        subcategoryListener?.remove()
        subcategoryListener = null
        _subcategories.value = emptyList()
        menusListener?.remove()
        menusListener = null
        _menus.value = emptyList()
        payoutListener?.remove()
        payoutListener = null
        staffListener?.remove()
        staffListener = null
        changeRequestsListener?.remove()
        changeRequestsListener = null
        walletMerchantListener?.remove()
        walletMerchantListener = null
        walletCustomerListener?.remove()
        walletCustomerListener = null
        linkedUserListener?.remove()
        linkedUserListener = null
        inboxMerchantListener?.remove()
        inboxMerchantListener = null
        inboxAudienceListener?.remove()
        inboxAudienceListener = null
        inboxReadsListener?.remove()
        inboxReadsListener = null
        platformSettingsListener?.remove()
        platformSettingsListener = null
        cityListener?.remove()
        cityListener = null
        reviewsListener?.remove()
        reviewsListener = null
        riderReviewsListener?.remove()
        riderReviewsListener = null
        receiptSettingsListener?.remove()
        receiptSettingsListener = null
        vouchersListener?.remove()
        vouchersListener = null
        redemptionsListener?.remove()
        redemptionsListener = null
        orderChatsListener?.remove()
        orderChatsListener = null
        _orderChats.value = emptyMap()
        bestSellersJob?.cancel()
        bestSellersJob = null
        rawOrders = emptyList()
        rawVouchers = emptyList()
        _vouchers.value = emptyList()
        _redemptions.value = emptyList()
        _orders.value = emptyList()
        _menuItems.value = emptyList()
        _categories.value = emptyList()
        _reviews.value = emptyList()
        _reviewsCount.value = 0
        _averageRating.value = 5.0
        _riderReviews.value = emptyList()
        _riderReviewsCount.value = 0
        _riderAverageRating.value = 5.0
        _payouts.value = emptyList()
        _staff.value = emptyList()
        _changeRequests.value = emptyList()
        rawMerchantTxList = emptyList()
        rawCustomerTxList = emptyList()
        listenedLinkedCustomerId = null
        _walletTransactions.value = emptyList()
        _sharedWalletBalance.value = null
        rawMerchantInboxList = emptyList()
        rawAudienceInboxList = emptyList()
        rawInboxReadIds = emptySet()
        shownInboxBannerIds.clear()
        isInitialInboxLoad = true
        _inboxMessages.value = emptyList()
        _unreadInboxCount.value = 0
        _inboxBannerMessage.value = null
        _newOrderAlert.value = null
        alertedOrderIds.clear()
    }

    private fun computeCombinedVouchers() {
        val reds = _redemptions.value
        val enriched = rawVouchers.map { v ->
            val matching = reds.filter { it.promoCode.equals(v.code, ignoreCase = true) }
            val totalDiscount = matching.sumOf { it.discount }
            val redCount = matching.size
            val ordCount = matching.map { it.orderId }.filter { it.isNotBlank() }.distinct().size
            v.copy(
                totalDiscountGiven = totalDiscount,
                redemptionsCount = redCount,
                ordersCount = ordCount,
                usedCount = if (redCount > v.usedCount) redCount else v.usedCount
            )
        }.sortedByDescending { it.createdAt }
        _vouchers.value = enriched
    }

    private fun parseMerchantVoucher(doc: DocumentSnapshot): MerchantVoucher {
        val code = doc.id.uppercase()
        val source = doc.getString("source") ?: "merchant"
        val fundedBy = doc.getString("fundedBy") ?: "merchant"
        val merchantId = doc.getString("merchantId") ?: ""
        val title = doc.getString("title") ?: ""
        val type = doc.getString("type") ?: "percent"
        val value = FormatUtils.parseDouble(doc.get("value")) ?: 0.0
        val maxDiscount = FormatUtils.parseDouble(doc.get("maxDiscount"))
        val minSubtotal = FormatUtils.parseDouble(doc.get("minSubtotal")) ?: 0.0
        val appliesTo = doc.getString("appliesTo") ?: "store"
        val itemIds = (doc.get("itemIds") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        val fulfillment = doc.getString("fulfillment") ?: "both"
        val services = (doc.get("services") as? List<*>)?.mapNotNull { it?.toString() } ?: listOf("food")
        val startsAt = FormatUtils.parseTimestamp(doc.get("startsAt"))
        val expiresAt = FormatUtils.parseTimestamp(doc.get("expiresAt"))
        val usageLimit = FormatUtils.parseInt(doc.get("usageLimit"))
        val perUserLimit = FormatUtils.parseInt(doc.get("perUserLimit")) ?: 1
        val usedCount = FormatUtils.parseInt(doc.get("usedCount")) ?: 0
        val active = doc.getBoolean("active") ?: true
        val visibility = doc.getString("visibility") ?: "public"
        val createdAt = FormatUtils.parseTimestamp(doc.get("createdAt")) ?: System.currentTimeMillis()
        val updatedAt = FormatUtils.parseTimestamp(doc.get("updatedAt")) ?: System.currentTimeMillis()

        return MerchantVoucher(
            code = code,
            source = source,
            fundedBy = fundedBy,
            merchantId = merchantId,
            title = title,
            type = type,
            value = value,
            maxDiscount = maxDiscount,
            minSubtotal = minSubtotal,
            appliesTo = appliesTo,
            itemIds = itemIds,
            fulfillment = fulfillment,
            services = services,
            startsAt = startsAt,
            expiresAt = expiresAt,
            usageLimit = usageLimit,
            perUserLimit = perUserLimit,
            usedCount = usedCount,
            active = active,
            visibility = visibility,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun parsePromoRedemption(doc: DocumentSnapshot): PromoRedemption {
        val id = doc.id
        val promoCode = (doc.getString("promoCode") ?: doc.getString("code") ?: "").uppercase()
        val discount = FormatUtils.parseDouble(doc.get("discount")) ?: 0.0
        val orderId = doc.getString("orderId") ?: ""
        val merchantId = doc.getString("merchantId") ?: ""
        val customerUid = doc.getString("customerUid") ?: doc.getString("userId") ?: ""
        val redeemedAt = FormatUtils.parseTimestamp(doc.get("redeemedAt"))
            ?: FormatUtils.parseTimestamp(doc.get("createdAt"))
            ?: System.currentTimeMillis()
        return PromoRedemption(
            id = id,
            promoCode = promoCode,
            discount = discount,
            orderId = orderId,
            merchantId = merchantId,
            customerUid = customerUid,
            redeemedAt = redeemedAt
        )
    }

    private fun parseReceiptDesign(doc: DocumentSnapshot?): ReceiptDesign {
        if (doc == null || !doc.exists()) return ReceiptDesign()
        val brandLine = doc.getString("brandLine") ?: "Delivio"
        val showOrderId = doc.getBoolean("showOrderId") ?: true
        val showOrderedAt = doc.getBoolean("showOrderedAt") ?: true
        val showCustomerName = doc.getBoolean("showCustomerName") ?: true
        val showNote = doc.getBoolean("showNote") ?: true
        val showSetChoices = doc.getBoolean("showSetChoices") ?: true
        val fontSize = doc.getString("fontSize") ?: "medium"
        val align = doc.getString("align") ?: "left"
        val separator = doc.getString("separator") ?: "dashes"

        val headerLinesRaw = doc.get("headerLines")
        val headerLines: List<String> = when (headerLinesRaw) {
            is List<*> -> headerLinesRaw.mapNotNull { it as? String }
            is String -> headerLinesRaw.lines().map { it.trim() }.filter { it.isNotBlank() }
            else -> emptyList()
        }

        val footerText = doc.getString("footerText") ?: ""

        val merchantCopyRaw = doc.get("merchantCopy") as? Map<*, *>
        val merchantCopy = if (merchantCopyRaw != null) {
            ReceiptCopyConfig(
                enabled = (merchantCopyRaw["enabled"] as? Boolean) ?: true,
                title = (merchantCopyRaw["title"] as? String)?.ifBlank { "MERCHANT'S COPY" } ?: "MERCHANT'S COPY",
                showItemPrices = (merchantCopyRaw["showItemPrices"] as? Boolean) ?: true
            )
        } else {
            ReceiptCopyConfig(enabled = true, title = "MERCHANT'S COPY", showItemPrices = true)
        }

        val customerCopyRaw = doc.get("customerCopy") as? Map<*, *>
        val customerCopy = if (customerCopyRaw != null) {
            ReceiptCopyConfig(
                enabled = (customerCopyRaw["enabled"] as? Boolean) ?: true,
                title = (customerCopyRaw["title"] as? String)?.ifBlank { "CUSTOMER'S COPY" } ?: "CUSTOMER'S COPY",
                showItemPrices = (customerCopyRaw["showItemPrices"] as? Boolean) ?: false
            )
        } else {
            ReceiptCopyConfig(enabled = true, title = "CUSTOMER'S COPY", showItemPrices = false)
        }

        return ReceiptDesign(
            brandLine = brandLine,
            showOrderId = showOrderId,
            showOrderedAt = showOrderedAt,
            showCustomerName = showCustomerName,
            showNote = showNote,
            showSetChoices = showSetChoices,
            fontSize = fontSize,
            align = align,
            separator = separator,
            headerLines = headerLines,
            footerText = footerText,
            merchantCopy = merchantCopy,
            customerCopy = customerCopy
        )
    }

    private fun startBestSellersPeriodic(shopId: String) {
        if (shopId.isBlank()) return
        bestSellersJob?.cancel()
        bestSellersJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    computeAndWriteBestSellers(shopId)
                } catch (e: Exception) {
                    Log.w("MerchantRepo", "Best sellers calculation failed: ${e.message}")
                }
                // 6 hours interval
                delay(6L * 60 * 60 * 1000L)
            }
        }
    }

    private fun computeAndWriteBestSellers(shopId: String) {
        if (shopId.isBlank()) return
        val db = firestore ?: return

        db.collection("orders")
            .whereEqualTo("merchantId", shopId)
            .limit(500)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot == null || snapshot.isEmpty) return@addOnSuccessListener
                try {
                    val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000L)
                    val qtyMap = mutableMapOf<String, Int>()

                    for (doc in snapshot.documents) {
                        val statusStr = doc.getString("status") ?: ""
                        val isDeliveredOrCompleted = statusStr.equals("Delivered", ignoreCase = true) ||
                                statusStr.equals("Completed", ignoreCase = true) ||
                                statusStr.equals("DELIVERED", ignoreCase = true) ||
                                statusStr.equals("COMPLETED", ignoreCase = true)
                        if (!isDeliveredOrCompleted) continue

                        val timestamp = doc.getLong("placedAtTimestamp")
                            ?: (doc.get("createdAt") as? Timestamp)?.toDate()?.time
                            ?: (doc.get("placedAt") as? Timestamp)?.toDate()?.time
                            ?: doc.getLong("createdAt")
                            ?: doc.getLong("timestamp")
                            ?: System.currentTimeMillis()
                        if (timestamp < thirtyDaysAgo) continue

                        val itemsRaw = (doc.get("items") as? List<*>) ?: emptyList<Any>()
                        for (itemObj in itemsRaw) {
                            val itemMap = itemObj as? Map<*, *> ?: continue
                            val rawItemId = (itemMap["itemId"] as? String)
                                ?: (itemMap["menuItemId"] as? String)
                                ?: (itemMap["id"] as? String)
                                ?: ""
                            val itemId = rawItemId.trim()
                            if (itemId.isBlank()) continue
                            val qty = (itemMap["quantity"] as? Number)?.toInt()
                                ?: (itemMap["qty"] as? Number)?.toInt()
                                ?: (itemMap["count"] as? Number)?.toInt()
                                ?: 1
                            qtyMap[itemId] = (qtyMap[itemId] ?: 0) + qty
                        }
                    }

                    val top20 = qtyMap.entries
                        .sortedByDescending { it.value }
                        .take(20)
                        .map { mapOf("itemId" to it.key, "qty" to it.value) }

                    val payload = hashMapOf<String, Any>(
                        "items" to top20,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )

                    db.collection("merchants").document(shopId)
                        .collection("stats").document("bestSellers")
                        .set(payload, com.google.firebase.firestore.SetOptions.merge())
                        .addOnFailureListener { ex ->
                            Log.w("MerchantRepo", "Failed to write bestSellers", ex)
                        }
                } catch (e: Exception) {
                    Log.w("MerchantRepo", "Error computing bestSellers", e)
                }
            }
            .addOnFailureListener { ex ->
                Log.w("MerchantRepo", "Failed to query orders for bestSellers", ex)
            }
    }

    private fun parseInboxMessage(doc: DocumentSnapshot): InboxMessage? {
        val title = doc.getString("title") ?: return null
        val body = doc.getString("body") ?: ""
        val category = doc.getString("category") ?: "update"
        val imageUrl = doc.getString("imageUrl") ?: ""
        val merchantId = doc.getString("merchantId") ?: ""
        val audience = doc.getString("audience") ?: ""
        val createdAt = when (val t = doc.get("createdAt")) {
            is Timestamp -> t.toDate().time
            is java.util.Date -> t.time
            is Number -> t.toLong()
            else -> System.currentTimeMillis()
        }
        val sendAt = when (val s = doc.get("sendAt")) {
            is Timestamp -> s.toDate().time
            is java.util.Date -> s.time
            is Number -> s.toLong()
            else -> null
        }
        return InboxMessage(
            id = doc.id,
            merchantId = merchantId,
            audience = audience,
            title = title,
            body = body,
            category = category,
            imageUrl = imageUrl,
            createdAt = createdAt,
            sendAt = sendAt
        )
    }

    fun refreshInboxVisibility() {
        updateMergedInbox()
    }

    private fun updateMergedInbox() {
        val now = System.currentTimeMillis()
        val mergedMap = LinkedHashMap<String, InboxMessage>()
        (rawMerchantInboxList + rawAudienceInboxList)
            .filter { msg -> msg.sendAt == null || msg.sendAt <= now }
            .forEach { msg ->
                mergedMap[msg.id] = msg.copy(isRead = rawInboxReadIds.contains(msg.id))
            }
        val sorted = mergedMap.values.sortedByDescending { it.createdAt }
        _inboxMessages.value = sorted
        val unreadCount = sorted.count { !it.isRead }
        _unreadInboxCount.value = unreadCount

        sorted.forEach { msg ->
            if (!msg.isRead && !shownInboxBannerIds.contains(msg.id)) {
                shownInboxBannerIds.add(msg.id)
                if (!isInitialInboxLoad) {
                    _inboxBannerMessage.value = "New message: ${msg.title}"
                }
            }
        }
        isInitialInboxLoad = false
    }

    fun markInboxMessageAsRead(messageId: String) {
        val mId = _merchantId.value ?: return
        val db = firestore ?: return
        db.collection("merchants").document(mId).collection("inboxReads").document(messageId)
            .set(mapOf("readAt" to FieldValue.serverTimestamp()))
    }

    fun markAllInboxMessagesAsRead() {
        val mId = _merchantId.value ?: return
        val db = firestore ?: return
        val unread = _inboxMessages.value.filter { !it.isRead }
        unread.forEach { msg ->
            db.collection("merchants").document(mId).collection("inboxReads").document(msg.id)
                .set(mapOf("readAt" to FieldValue.serverTimestamp()))
        }
    }

    fun dismissInboxBanner() {
        _inboxBannerMessage.value = null
    }

    private fun syncCityListener(cityId: String) {
        cityListener?.remove()
        val db = firestore ?: return
        cityListener = db.collection("cities").document(cityId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("MerchantRepo", "City listener error: $cityId", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    currentCityName = snapshot.getString("name") ?: snapshot.getString("cityName") ?: cityId.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                    cityServicesMap = (snapshot.get("services") as? Map<*, *>)?.mapNotNull { (k, v) ->
                        (k as? String)?.let { it to v }
                    }?.toMap() ?: emptyMap()
                } else {
                    cityServicesMap = emptyMap()
                }
                evaluateServiceLock()
            }
    }

    private fun evaluateServiceLock() {
        val service = currentServiceKey
        val cityId = currentCityId

        // settings/platform.services[service] is not false (missing means on)
        val platformServiceVal = platformServicesMap[service]
        val isPlatformOn = platformServiceVal != false

        // cities/{cityId}.services[service] is not false (missing means on)
        val cityServiceVal = cityServicesMap[service]
        val isCityOn = cityServiceVal != false

        val serviceOn = isPlatformOn && isCityOn
        _isServiceLocked.value = !serviceOn

        val serviceLabel = if (service == "convenience") "Convenience" else "Food"
        val cityLabel = currentCityName.ifBlank { cityId.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() } }

        if (!serviceOn) {
            _serviceLockMessage.value = "$serviceLabel is disabled by admin in $cityLabel. Your store is closed automatically."
            _newOrderAlert.value = null
        } else {
            _serviceLockMessage.value = null
        }
    }

    private fun parseCustomerReview(doc: DocumentSnapshot): CustomerReview {
        val ratingVal = when (val r = doc.get("ratingMerchant") ?: doc.get("merchantRating") ?: doc.get("rating")) {
            is Number -> r.toDouble()
            is String -> r.toDoubleOrNull() ?: 5.0
            else -> 5.0
        }

        val createdTime = when (val t = doc.get("createdAt") ?: doc.get("timestamp") ?: doc.get("date")) {
            is Timestamp -> t.toDate().time
            is Number -> t.toLong()
            else -> System.currentTimeMillis()
        }

        val orderNo = doc.getString("orderNumber")
            ?: doc.getString("orderRef")
            ?: doc.getString("orderId")?.let { "ORD-" + it.takeLast(6).uppercase() }
            ?: ("ORD-" + doc.id.take(6).uppercase())

        val summary = doc.getString("itemsSummary") ?: doc.getString("itemsText") ?: run {
            val itemsList = doc.get("items") as? List<*>
            if (itemsList != null && itemsList.isNotEmpty()) {
                itemsList.mapNotNull { itm ->
                    when (itm) {
                        is String -> itm
                        is Map<*, *> -> {
                            val q = itm["qty"] ?: itm["quantity"] ?: 1
                            val n = itm["name"] ?: itm["title"] ?: ""
                            "${q}x $n"
                        }
                        else -> null
                    }
                }.joinToString(", ")
            } else {
                ""
            }
        }

        return CustomerReview(
            id = doc.id,
            merchantId = doc.getString("merchantId") ?: "",
            orderId = doc.getString("orderId") ?: "",
            orderNumber = orderNo,
            ratingMerchant = ratingVal.coerceIn(1.0, 5.0),
            comment = doc.getString("comment") ?: doc.getString("merchantComment") ?: doc.getString("review") ?: "",
            customerName = doc.getString("customerName") ?: doc.getString("userName") ?: doc.getString("author") ?: "Customer",
            itemsSummary = summary,
            createdAt = createdTime
        )
    }

    private fun parseRiderReview(doc: DocumentSnapshot): AnonymousRating {
        val ratingVal = when (val r = doc.get("ratingMerchant") ?: doc.get("merchantRating") ?: doc.get("rating") ?: doc.get("stars")) {
            is Number -> r.toDouble()
            is String -> r.toDoubleOrNull() ?: 5.0
            else -> 5.0
        }
        val createdTime = when (val t = doc.get("createdAt") ?: doc.get("timestamp") ?: doc.get("date")) {
            is Timestamp -> t.toDate().time
            is Number -> t.toLong()
            else -> System.currentTimeMillis()
        }
        val comment = doc.getString("comment") ?: doc.getString("merchantComment") ?: doc.getString("review") ?: doc.getString("feedback") ?: ""
        return AnonymousRating(
            id = doc.id,
            merchantId = doc.getString("merchantId") ?: "",
            rating = ratingVal.coerceIn(1.0, 5.0),
            comment = comment,
            createdAt = createdTime
        )
    }

    private fun syncWalletListeners(mId: String, linkedCustomerUid: String?) {
        val db = firestore ?: return

        // 1. Merchant wallet transactions: where merchantId == mId
        if (walletMerchantListener == null) {
            walletMerchantListener = db.collection("walletTransactions")
                .whereEqualTo("merchantId", mId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("MerchantRepo", "Wallet merchant listener error", error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        rawMerchantTxList = snapshot.documents.mapNotNull { parseWalletTx(it) }
                        updateMergedWalletTransactions()
                    }
                }
        }

        // 2. Customer linked wallet
        if (!linkedCustomerUid.isNullOrBlank() && linkedCustomerUid != listenedLinkedCustomerId) {
            listenedLinkedCustomerId = linkedCustomerUid
            walletCustomerListener?.remove()
            linkedUserListener?.remove()

            // Listen to customer wallet transactions
            walletCustomerListener = db.collection("walletTransactions")
                .whereEqualTo("customerId", linkedCustomerUid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("MerchantRepo", "Wallet customer listener error", error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        rawCustomerTxList = snapshot.documents.mapNotNull { parseWalletTx(it) }
                        updateMergedWalletTransactions()
                    }
                }

            // Listen to user document for live balance
            linkedUserListener = db.collection("users").document(linkedCustomerUid)
                .addSnapshotListener { doc, _ ->
                    if (doc != null && doc.exists()) {
                        val bal = FormatUtils.parseDouble(doc.get("walletBalance")) ?: 0.0
                        _sharedWalletBalance.value = bal
                    }
                }
        } else if (linkedCustomerUid.isNullOrBlank()) {
            listenedLinkedCustomerId = null
            walletCustomerListener?.remove()
            walletCustomerListener = null
            linkedUserListener?.remove()
            linkedUserListener = null
            rawCustomerTxList = emptyList()
            _sharedWalletBalance.value = null
            updateMergedWalletTransactions()
        }
    }

    private fun parseWalletTx(doc: com.google.firebase.firestore.DocumentSnapshot): WalletTransaction? {
        val amount = FormatUtils.parseDouble(doc.get("amount")) ?: return null
        val ts = when (val t = doc.get("createdAt") ?: doc.get("timestamp") ?: doc.get("date")) {
            is Timestamp -> t.toDate().time
            is Number -> t.toLong()
            else -> System.currentTimeMillis()
        }
        return WalletTransaction(
            id = doc.id,
            title = doc.getString("title") ?: doc.getString("type") ?: "Wallet Transaction",
            description = doc.getString("description") ?: doc.getString("note") ?: "",
            amount = amount,
            type = doc.getString("type") ?: if (amount >= 0) "credit" else "debit",
            dateTimestamp = ts,
            reference = doc.getString("reference") ?: ("TX-" + doc.id.take(6).uppercase()),
            merchantId = doc.getString("merchantId") ?: "",
            customerId = doc.getString("customerId") ?: ""
        )
    }

    private fun updateMergedWalletTransactions() {
        val combined = (rawMerchantTxList + rawCustomerTxList)
            .distinctBy { it.id }
            .sortedByDescending { it.dateTimestamp }
        _walletTransactions.value = combined
    }

    // --- Order Status Actions (Writes only exact fields, waits for Firestore success) ---

    fun confirmAcceptOrder(orderId: String, prepMinutes: Int, isPickUp: Boolean = false, onResult: (Boolean, String?) -> Unit) {
        val db = firestore ?: return onResult(false, "Firestore unavailable")
        val data = mutableMapOf<String, Any>(
            "status" to PlatformOrderStatuses.PREPARING,
            "statusHistory" to FieldValue.arrayUnion(
                mapOf(
                    "status" to PlatformOrderStatuses.PREPARING,
                    "at" to Timestamp.now()
                )
            ),
            "prepMinutes" to prepMinutes,
            "acceptedAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        // For Delivery and Scheduled orders, Accept writes riderRequest {status "Searching", requestedAt, auto true, feePayer "customer"} in one update. Pick Up orders get no riderRequest.
        if (!isPickUp) {
            data["riderRequest"] = mapOf(
                "status" to "Searching",
                "requestedAt" to Timestamp.now(),
                "auto" to true,
                "feePayer" to "customer"
            )
        }

        db.collection("orders").document(orderId).update(data)
            .addOnSuccessListener {
                dismissNewOrderAlert()
                onResult(true, null)
            }
            .addOnFailureListener { ex ->
                val err = ex.localizedMessage ?: ex.message ?: "Failed to accept order"
                Log.e("MerchantRepo", "Accept order failed: $err", ex)
                onResult(false, err)
            }
    }

    fun confirmAcceptOrder(orderId: String, prepMinutes: Int, onResult: (Boolean, String?) -> Unit) {
        val order = _orders.value.find { it.id == orderId }
        val isPickUp = order?.isPickUp ?: false
        confirmAcceptOrder(orderId, prepMinutes, isPickUp, onResult)
    }

    fun requestRiderAgain(orderId: String, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        val db = firestore
        val data = mapOf(
            "riderRequest.status" to "Searching",
            "riderRequest.requestedAt" to Timestamp.now(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val idx = _orders.value.indexOfFirst { it.id == orderId }
        if (idx >= 0) {
            val updated = _orders.value[idx].copy(
                riderRequest = _orders.value[idx].riderRequest?.copy(
                    status = "Searching",
                    requestedAt = System.currentTimeMillis()
                )
            )
            val list = _orders.value.toMutableList()
            list[idx] = updated
            _orders.value = list
        }
        if (db == null) {
            onResult(true, null)
            return
        }
        db.collection("orders").document(orderId).update(data)
            .addOnSuccessListener {
                onResult(true, null)
            }
            .addOnFailureListener { ex ->
                val err = ex.localizedMessage ?: ex.message ?: "Failed to request rider again"
                Log.e("MerchantRepo", "Request rider again failed: $err", ex)
                onResult(false, err)
            }
    }

    fun fetchOrderDirect(orderId: String, onResult: (Order?) -> Unit) {
        val db = firestore
        if (db == null) {
            onResult(_orders.value.find { it.id == orderId })
            return
        }
        db.collection("orders").document(orderId).get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    val statusStr = doc.getString("status") ?: PlatformOrderStatuses.PLACED_ORDER
                    val orderStatus = OrderStatus.fromString(statusStr)

                    val itemsRaw = (doc.get("items") as? List<*>) ?: emptyList<Any>()
                    val freshItems = itemsRaw.mapIndexedNotNull { idx, itemObj ->
                        val itm = itemObj as? Map<*, *> ?: return@mapIndexedNotNull null
                        val modsRaw = (itm["modifiers"] as? List<*>) ?: emptyList<Any>()
                        val mods = modsRaw.mapNotNull { mObj ->
                            val m = mObj as? Map<*, *> ?: return@mapNotNull null
                            OrderModifier(
                                name = (m["name"] as? String) ?: (m["title"] as? String) ?: "",
                                price = FormatUtils.parseDouble(m["price"]) ?: 0.0
                            )
                        }
                        val priceVal = FormatUtils.parseDouble(itm["price"])
                            ?: FormatUtils.parseDouble(itm["unitPrice"])
                            ?: FormatUtils.parseDouble(itm["itemPrice"])
                            ?: 0.0
                        val qtyVal = FormatUtils.parseInt(itm["qty"])
                            ?: FormatUtils.parseInt(itm["quantity"])
                            ?: 1
                        val finalQty = if (qtyVal > 0) qtyVal else 1

                        val selRaw = (itm["selections"] as? List<*>) ?: emptyList<Any>()
                        val selections = selRaw.mapNotNull { sObj ->
                            val sm = sObj as? Map<*, *> ?: return@mapNotNull null
                            OrderItemSelection(
                                setId = (sm["setId"] as? String) ?: "",
                                setName = (sm["setName"] as? String) ?: "",
                                choiceId = (sm["choiceId"] as? String) ?: "",
                                choiceName = (sm["choiceName"] as? String) ?: (sm["name"] as? String) ?: "",
                                price = FormatUtils.parseDouble(sm["price"]) ?: 0.0
                            )
                        }
                        val basePriceVal = FormatUtils.parseDouble(itm["basePrice"]) ?: priceVal

                        OrderItem(
                            id = (itm["id"] as? String) ?: "item_$idx",
                            menuItemId = (itm["menuItemId"] as? String) ?: "",
                            name = (itm["name"] as? String) ?: (itm["title"] as? String) ?: "Item",
                            quantity = finalQty,
                            price = priceVal,
                            basePrice = basePriceVal,
                            unitPrice = priceVal,
                            selectedSize = itm["selectedSize"] as? String,
                            modifiers = mods,
                            selections = selections,
                            notes = (itm["notes"] as? String) ?: (itm["note"] as? String)
                        )
                    }

                    val rawSplitChild = doc.get("isSplitChild") ?: doc.get("splitChild")
                    val isSplitChildVal = when (rawSplitChild) {
                        is Boolean -> rawSplitChild
                        is String -> rawSplitChild.equals("true", ignoreCase = true) || rawSplitChild == "1"
                        is Number -> rawSplitChild.toInt() == 1
                        else -> false
                    }
                    if (isSplitChildVal) {
                        onResult(null)
                        return@addOnSuccessListener
                    }

                    val rawSplitOut = doc.get("splitOut")
                    val hasSplitOut = when (rawSplitOut) {
                        null -> false
                        is Boolean -> rawSplitOut
                        is String -> rawSplitOut.isNotBlank() && !rawSplitOut.equals("false", ignoreCase = true) && rawSplitOut != "0"
                        is Collection<*> -> rawSplitOut.isNotEmpty()
                        is Map<*, *> -> rawSplitOut.isNotEmpty()
                        is Number -> rawSplitOut.toInt() != 0
                        else -> true
                    }

                    val acceptedAtVal: Long? = when (val a = doc.get("acceptedAt")) {
                        is Timestamp -> a.toDate().time
                        is java.util.Date -> a.time
                        is Number -> a.toLong()
                        is String -> a.toLongOrNull()
                        else -> null
                    }

                    val readyAtVal: Long? = when (val r = doc.get("readyAt")) {
                        is Timestamp -> r.toDate().time
                        is java.util.Date -> r.time
                        is Number -> r.toLong()
                        is String -> r.toLongOrNull()
                        else -> null
                    }

                    val delOptRaw = (doc.getString("deliveryOption")
                        ?: ((doc.get("delivery") as? Map<*, *>)?.get("option") as? String)
                        ?: (doc.get("deliveryOption") as? String)
                        ?: "").trim()
                    val delOptVal = when {
                        delOptRaw.contains("saver", ignoreCase = true) -> "Saver"
                        delOptRaw.contains("standard", ignoreCase = true) -> "Standard"
                        delOptRaw.contains("priority", ignoreCase = true) -> "Priority"
                        else -> "Priority"
                    }

                    val dispatchMap = doc.get("dispatch") as? Map<*, *>
                    val holdMinsParsed = FormatUtils.parseInt(dispatchMap?.get("holdMinutes"))
                        ?: FormatUtils.parseInt(doc.get("dispatchHoldMinutes"))
                        ?: FormatUtils.parseInt(doc.get("holdMinutes"))
                    val dispatchHoldMinutesVal = holdMinsParsed ?: if (delOptVal == "Standard" || delOptVal == "Saver") 5 else 0

                    val rawOrderNum = doc.getString("orderNumber") ?: doc.getString("orderRef")
                    val orderNum = if (!rawOrderNum.isNullOrBlank()) {
                        val clean = if (rawOrderNum.startsWith("#")) rawOrderNum.removePrefix("#").trim() else rawOrderNum.trim()
                        "#$clean"
                    } else {
                        "#" + (if (doc.id.length >= 6) doc.id.takeLast(6).uppercase() else doc.id.uppercase())
                    }

                    val existing = _orders.value.find { it.id == orderId }
                    val updated = (existing ?: Order(
                        id = doc.id,
                        merchantId = doc.getString("merchantId") ?: _merchantId.value ?: "",
                        orderNumber = orderNum,
                        status = orderStatus
                    )).copy(
                        items = freshItems,
                        status = orderStatus,
                        acceptedAt = acceptedAtVal ?: existing?.acceptedAt,
                        readyAt = readyAtVal ?: existing?.readyAt,
                        deliveryOption = delOptVal,
                        dispatchHoldMinutes = dispatchHoldMinutesVal,
                        isSplitChild = false,
                        splitOut = hasSplitOut
                    )

                    val currentList = _orders.value.toMutableList()
                    val idx = currentList.indexOfFirst { it.id == orderId }
                    if (idx >= 0) {
                        currentList[idx] = updated
                        _orders.value = currentList
                    }
                    onResult(updated)
                } else {
                    onResult(_orders.value.find { it.id == orderId })
                }
            }
            .addOnFailureListener {
                onResult(_orders.value.find { it.id == orderId })
            }
    }

    fun editOrderPreparing(
        order: Order,
        newItems: List<OrderItem>,
        reason: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val db = firestore
        val currentUid = auth?.currentUser?.uid ?: _merchantId.value ?: "merchant"

        val newSubtotal = newItems.sumOf { it.effectivePrice * it.quantity }
        val delFee = order.deliveryFee
        val discount = order.discount
        val tip = order.tip

        val feesRaw = order.feesRaw
        val serviceFeeType = (feesRaw["serviceFeeType"] as? String) ?: ""
        val serviceFeeRate = FormatUtils.parseDouble(feesRaw["serviceFeeRate"])
            ?: FormatUtils.parseDouble(feesRaw["serviceFeePercent"]) ?: 0.0

        val newServiceFee = if (serviceFeeType.equals("percent", ignoreCase = true) && serviceFeeRate > 0.0) {
            val rate = if (serviceFeeRate > 1.0) serviceFeeRate / 100.0 else serviceFeeRate
            rate * (newSubtotal + delFee)
        } else {
            order.serviceFee
        }

        val vatPercent = FormatUtils.parseDouble(feesRaw["vatPercent"]) ?: 0.0
        val vatMode = (feesRaw["vatMode"] as? String) ?: "exclusive"
        val vatBase = (feesRaw["vatBase"] as? String) ?: "subtotal"

        val vatBaseAmount = if (vatBase.contains("service", ignoreCase = true)) {
            newSubtotal + newServiceFee
        } else {
            newSubtotal
        }

        val newVat = if (vatPercent > 0.0) {
            val rate = if (vatPercent > 1.0) vatPercent / 100.0 else vatPercent
            if (vatMode.equals("inclusive", ignoreCase = true)) {
                vatBaseAmount - (vatBaseAmount / (1.0 + rate))
            } else {
                vatBaseAmount * rate
            }
        } else {
            FormatUtils.parseDouble(feesRaw["vat"]) ?: 0.0
        }

        val vatToAddToTotal = if (vatMode.equals("exclusive", ignoreCase = true)) newVat else 0.0
        val newTotal = (newSubtotal + delFee + newServiceFee + vatToAddToTotal + tip - discount).coerceAtLeast(0.0)

        val updatedFeesMap = HashMap<String, Any>()
        feesRaw.forEach { (k, v) -> if (v != null) updatedFeesMap[k] = v }
        updatedFeesMap["subtotal"] = newSubtotal
        updatedFeesMap["deliveryFee"] = delFee
        updatedFeesMap["serviceFee"] = newServiceFee
        updatedFeesMap["discount"] = discount
        updatedFeesMap["tip"] = tip
        if (vatPercent > 0.0) {
            updatedFeesMap["vat"] = newVat
        }
        updatedFeesMap["total"] = newTotal

        val isCash = order.paymentMethod.trim().lowercase().let { it.contains("cod") || it.contains("cash") }
        val isWallet = order.paymentMethod.trim().lowercase().contains("wallet")

        val cashToCollect = if (isCash) newTotal else 0.0
        val walletAdjustDue = if (isWallet) (order.total - newTotal).coerceAtLeast(0.0) else 0.0

        val itemsSerialized = newItems.map { itm ->
            mapOf(
                "id" to itm.id,
                "menuItemId" to itm.menuItemId,
                "name" to itm.name,
                "price" to itm.effectivePrice,
                "unitPrice" to itm.effectivePrice,
                "qty" to itm.quantity,
                "quantity" to itm.quantity,
                "selectedSize" to (itm.selectedSize ?: ""),
                "notes" to (itm.notes ?: "")
            )
        }

        val originalItemsToSave = if (order.originalItems.isNotEmpty()) order.originalItems else order.items
        val originalItemsSerialized = originalItemsToSave.map { itm ->
            mapOf(
                "id" to itm.id,
                "name" to itm.name,
                "price" to itm.effectivePrice,
                "qty" to itm.quantity
            )
        }
        val originalTotalToSave = order.originalTotal ?: order.total

        val updateData = mapOf(
            "items" to itemsSerialized,
            "fees" to updatedFeesMap,
            "cashToCollect" to cashToCollect,
            "walletAdjustDue" to walletAdjustDue,
            "editedAt" to FieldValue.serverTimestamp(),
            "editedBy" to currentUid,
            "editNote" to reason.trim(),
            "originalItems" to originalItemsSerialized,
            "originalTotal" to originalTotalToSave,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        if (db == null) {
            val idx = _orders.value.indexOfFirst { it.id == order.id }
            if (idx >= 0) {
                val updated = _orders.value[idx].copy(
                    items = newItems,
                    subtotal = newSubtotal,
                    serviceFee = newServiceFee,
                    total = newTotal,
                    isEdited = true,
                    editedAt = System.currentTimeMillis(),
                    editedBy = currentUid,
                    editNote = reason.trim(),
                    originalItems = originalItemsToSave,
                    originalTotal = originalTotalToSave,
                    cashToCollect = cashToCollect,
                    walletAdjustDue = walletAdjustDue,
                    feesRaw = updatedFeesMap
                )
                val list = _orders.value.toMutableList()
                list[idx] = updated
                _orders.value = list
            }
            onResult(true, null)
            return
        }

        db.collection("orders").document(order.id).update(updateData)
            .addOnSuccessListener {
                onResult(true, null)
            }
            .addOnFailureListener { ex ->
                val err = ex.localizedMessage ?: ex.message ?: "Failed to update edited order"
                Log.e("MerchantRepo", "Edit order failed: $err", ex)
                onResult(false, err)
            }
    }

    fun confirmRejectOrder(orderId: String, reason: String, onResult: (Boolean, String?) -> Unit) {
        val db = firestore ?: return onResult(false, "Firestore unavailable")
        val data = mapOf(
            "status" to PlatformOrderStatuses.REJECTED,
            "rejectReason" to reason.trim(),
            "rejectedAt" to FieldValue.serverTimestamp(),
            "statusHistory" to FieldValue.arrayUnion(
                mapOf(
                    "status" to PlatformOrderStatuses.REJECTED,
                    "at" to Timestamp.now()
                )
            ),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("orders").document(orderId).update(data)
            .addOnSuccessListener {
                dismissNewOrderAlert()
                onResult(true, null)
            }
            .addOnFailureListener { ex ->
                val err = ex.localizedMessage ?: ex.message ?: "Failed to reject order"
                Log.e("MerchantRepo", "Reject order failed: $err", ex)
                onResult(false, err)
            }
    }

    fun setOrderReady(orderId: String, onResult: (Boolean, String?) -> Unit) {
        val db = firestore ?: return onResult(false, "Firestore unavailable")
        val data = mapOf(
            "status" to PlatformOrderStatuses.READY,
            "statusHistory" to FieldValue.arrayUnion(
                mapOf(
                    "status" to PlatformOrderStatuses.READY,
                    "at" to Timestamp.now()
                )
            ),
            "readyAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("orders").document(orderId).update(data)
            .addOnSuccessListener {
                onResult(true, null)
            }
            .addOnFailureListener { ex ->
                val err = ex.localizedMessage ?: ex.message ?: "Failed to mark ready"
                Log.e("MerchantRepo", "Mark ready failed: $err", ex)
                onResult(false, err)
            }
    }

    fun cancelOrder(orderId: String, reason: String, onResult: (Boolean, String?) -> Unit) {
        val db = firestore ?: return onResult(false, "Firestore unavailable")
        val data = mapOf(
            "status" to PlatformOrderStatuses.CANCELLED,
            "cancelReason" to reason.trim(),
            "statusHistory" to FieldValue.arrayUnion(
                mapOf(
                    "status" to PlatformOrderStatuses.CANCELLED,
                    "at" to Timestamp.now()
                )
            ),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("orders").document(orderId).update(data)
            .addOnSuccessListener {
                onResult(true, null)
            }
            .addOnFailureListener { ex ->
                val err = ex.localizedMessage ?: ex.message ?: "Failed to cancel order"
                Log.e("MerchantRepo", "Cancel order failed: $err", ex)
                onResult(false, err)
            }
    }

    fun markOrderPickedUp(orderId: String, onResult: (Boolean, String?) -> Unit) {
        val db = firestore ?: return onResult(false, "Firestore unavailable")
        val data = mapOf(
            "status" to PlatformOrderStatuses.COMPLETED,
            "statusHistory" to FieldValue.arrayUnion(
                mapOf(
                    "status" to PlatformOrderStatuses.COMPLETED,
                    "at" to Timestamp.now()
                )
            ),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("orders").document(orderId).update(data)
            .addOnSuccessListener {
                onResult(true, null)
            }
            .addOnFailureListener { ex ->
                val err = ex.localizedMessage ?: ex.message ?: "Failed to mark order as picked up"
                Log.e("MerchantRepo", "Mark picked up failed: $err", ex)
                onResult(false, err)
            }
    }

    fun requestRiderForOrder(
        order: Order,
        distanceKm: Double,
        deliveryFee: Double,
        note: String,
        dropoffLat: Double? = null,
        dropoffLng: Double? = null,
        dropoffAddress: String? = null,
        onResult: (Boolean, String?, Map<String, Any>) -> Unit
    ) {
        val currentUid = auth?.currentUser?.uid ?: _merchantId.value ?: ""
        val riderRequestData = mapOf(
            "status" to "Searching",
            "requestedAt" to Timestamp.now(),
            "requestedBy" to currentUid,
            "distanceKm" to distanceKm,
            "deliveryFee" to deliveryFee,
            "note" to note.trim(),
            "feePayer" to "customer"
        )

        val updateData = mutableMapOf<String, Any>(
            "status" to PlatformOrderStatuses.READY,
            "readyAt" to FieldValue.serverTimestamp(),
            "statusHistory" to FieldValue.arrayUnion(
                mapOf(
                    "status" to PlatformOrderStatuses.READY,
                    "at" to Timestamp.now()
                )
            ),
            "updatedAt" to FieldValue.serverTimestamp(),
            "riderRequest" to riderRequestData
        )

        // If the order has no pickup, also write pickup {label (shop name), address, lat, lng, contactName, contactPhone} from this merchant so the rider app can show the pickup pin.
        if (!order.hasPickup) {
            val storeLoc = _settings.value.storeLocation
            updateData["pickup"] = mapOf(
                "label" to _settings.value.storeName.ifBlank { "Merchant Store" },
                "address" to storeLoc.formatted.ifBlank { "Store Location" },
                "lat" to storeLoc.latitude,
                "lng" to storeLoc.longitude,
                "contactName" to _settings.value.ownerName.ifBlank { _settings.value.storeName },
                "contactPhone" to _settings.value.phone
            )
        }

        // If drop-off coordinates were missing on order and set in form
        if (dropoffLat != null && dropoffLng != null && !order.hasDropoffCoordinates) {
            updateData["dropoffLat"] = dropoffLat
            updateData["dropoffLng"] = dropoffLng
            updateData["dropoff"] = mapOf(
                "lat" to dropoffLat,
                "lng" to dropoffLng,
                "address" to (dropoffAddress ?: order.deliveryAddress.formatted)
            )
        }

        val db = firestore
        if (db == null) {
            // Offline / mock update
            val idx = _orders.value.indexOfFirst { it.id == order.id }
            if (idx >= 0) {
                val updated = _orders.value[idx].copy(
                    status = OrderStatus.READY,
                    hasPickup = true,
                    riderRequest = RiderRequest(
                        id = "rr_${order.id}",
                        orderId = order.id,
                        status = "Searching",
                        requestedAt = System.currentTimeMillis(),
                        requestedBy = currentUid,
                        distanceKm = distanceKm,
                        deliveryFee = deliveryFee,
                        note = note.trim(),
                        feePayer = "customer"
                    )
                )
                val newList = _orders.value.toMutableList()
                newList[idx] = updated
                _orders.value = newList
            }
            onResult(true, null, updateData)
            return
        }

        db.collection("orders").document(order.id).update(updateData)
            .addOnSuccessListener {
                onResult(true, null, updateData)
            }
            .addOnFailureListener { ex ->
                val err = ex.localizedMessage ?: ex.message ?: "Failed to request rider"
                Log.e("MerchantRepo", "Request rider failed: $err", ex)
                onResult(false, err, updateData)
            }
    }

    fun bookRiderDirect(
        recipientName: String,
        recipientPhone: String,
        deliveryAddress: String,
        dropoffArea: String,
        dropoffLat: Double,
        dropoffLng: Double,
        whatToDeliver: String,
        relatedOrderNumber: String,
        codAmount: Double,
        riderNote: String,
        distanceKm: Double,
        deliveryFee: Double,
        serviceFee: Double,
        onResult: (Boolean, String?, Map<String, Any>, String?) -> Unit
    ) {
        val mId = _merchantId.value ?: return onResult(false, "No active merchant logged in", emptyMap(), null)
        val storeLoc = _settings.value.storeLocation
        val cId = _settings.value.cityId.ifBlank { "taguig" }
        val orderNum = "#R" + (1000..9999).random().toString()

        val itemsList = if (whatToDeliver.isNotBlank()) {
            listOf(mapOf("name" to whatToDeliver.trim(), "qty" to 1, "price" to 0.0, "unitPrice" to 0.0))
        } else {
            emptyList<Map<String, Any>>()
        }

        val totalFee = deliveryFee + serviceFee

        val orderData = hashMapOf<String, Any?>(
            "serviceType" to "request_rider",
            "merchantId" to mId,
            "merchantName" to _settings.value.storeName.ifBlank { "Merchant Store" },
            "customerId" to null,
            "customerName" to recipientName.trim(),
            "customerPhone" to recipientPhone.trim(),
            "status" to "Ready",
            "statusHistory" to listOf(
                mapOf("status" to "Ready", "at" to Timestamp.now())
            ),
            "riderId" to null,
            "riderName" to null,
            "riderPhone" to null,
            "cityId" to cId,
            "items" to itemsList,
            "relatedOrderNumber" to relatedOrderNumber.trim(),
            "note" to riderNote.trim(),
            "paymentMethod" to "merchant",
            "codAmount" to codAmount,
            "fees" to mapOf(
                "subtotal" to 0.0,
                "deliveryFee" to deliveryFee,
                "serviceFee" to serviceFee,
                "discount" to 0.0,
                "total" to totalFee,
                "feePayer" to "merchant"
            ),
            "pickup" to mapOf(
                "label" to _settings.value.storeName.ifBlank { "Merchant Store" },
                "address" to storeLoc.formatted,
                "lat" to storeLoc.latitude,
                "lng" to storeLoc.longitude,
                "contactName" to _settings.value.ownerName.ifBlank { _settings.value.storeName },
                "contactPhone" to _settings.value.phone
            ),
            "dropoff" to mapOf(
                "address" to deliveryAddress.trim(),
                "area" to dropoffArea.trim(),
                "lat" to dropoffLat,
                "lng" to dropoffLng,
                "contactName" to recipientName.trim(),
                "contactPhone" to recipientPhone.trim()
            ),
            "riderRequest" to mapOf(
                "status" to "Searching",
                "requestedAt" to Timestamp.now(),
                "requestedBy" to mId,
                "distanceKm" to distanceKm,
                "deliveryFee" to deliveryFee,
                "note" to riderNote.trim(),
                "feePayer" to "merchant",
                "manual" to true
            ),
            "orderNumber" to orderNum,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val nonNullMap = orderData.filterValues { it != null }.mapValues { it.value as Any }

        val db = firestore
        val tempId = "order_${System.currentTimeMillis()}"

        val localOrder = Order(
            id = tempId,
            merchantId = mId,
            orderNumber = orderNum,
            customerName = recipientName.trim(),
            customerPhone = recipientPhone.trim(),
            deliveryAddress = AddressLocation(
                street = deliveryAddress.trim(),
                city = dropoffArea.trim(),
                latitude = dropoffLat,
                longitude = dropoffLng
            ),
            dropoffLat = dropoffLat,
            dropoffLng = dropoffLng,
            items = if (whatToDeliver.isNotBlank()) listOf(OrderItem(id = "item_1", name = whatToDeliver.trim(), quantity = 1, price = 0.0, unitPrice = 0.0)) else emptyList(),
            subtotal = 0.0,
            deliveryFee = deliveryFee,
            serviceFee = serviceFee,
            discount = 0.0,
            platformFee = 0.0,
            total = totalFee,
            paymentMethod = "merchant",
            status = OrderStatus.READY,
            placedAtTimestamp = System.currentTimeMillis(),
            riderRequest = RiderRequest(
                id = "rr_$tempId",
                orderId = tempId,
                status = "Searching",
                requestedAt = System.currentTimeMillis(),
                requestedBy = mId,
                distanceKm = distanceKm,
                deliveryFee = deliveryFee,
                note = riderNote.trim(),
                feePayer = "merchant"
            ),
            cityId = cId,
            serviceType = "request_rider",
            hasPickup = true
        )

        if (db == null) {
            _orders.value = listOf(localOrder) + _orders.value
            onResult(true, null, nonNullMap, tempId)
            return
        }

        val docRef = db.collection("orders").document()
        docRef.set(orderData)
            .addOnSuccessListener {
                val realOrder = localOrder.copy(
                    id = docRef.id,
                    riderRequest = localOrder.riderRequest?.copy(id = "rr_${docRef.id}", orderId = docRef.id)
                )
                _orders.value = listOf(realOrder) + _orders.value.filter { it.id != docRef.id }
                onResult(true, null, nonNullMap, docRef.id)
            }
            .addOnFailureListener { ex ->
                val err = ex.localizedMessage ?: ex.message ?: "Failed to write rider request to Firestore"
                Log.e("MerchantRepo", "Book rider failed: $err", ex)
                onResult(false, err, nonNullMap, null)
            }
    }

    fun retryRiderRequest(orderId: String, onResult: (Boolean, String?) -> Unit) {
        val db = firestore
        val data = mapOf(
            "riderRequest.status" to "Searching",
            "riderRequest.requestedAt" to Timestamp.now(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val idx = _orders.value.indexOfFirst { it.id == orderId }
        if (idx >= 0) {
            val updated = _orders.value[idx].copy(
                riderRequest = _orders.value[idx].riderRequest?.copy(
                    status = "Searching",
                    requestedAt = System.currentTimeMillis()
                )
            )
            val list = _orders.value.toMutableList()
            list[idx] = updated
            _orders.value = list
        }
        if (db == null) {
            onResult(true, null)
            return
        }
        db.collection("orders").document(orderId).update(data)
            .addOnSuccessListener {
                onResult(true, null)
            }
            .addOnFailureListener { ex ->
                val err = ex.localizedMessage ?: ex.message ?: "Failed to retry rider request"
                Log.e("MerchantRepo", "Retry rider failed: $err", ex)
                onResult(false, err)
            }
    }

    fun cancelRiderRequest(orderId: String, reason: String = "Merchant cancelled rider request", onResult: (Boolean, String?) -> Unit) {
        val db = firestore
        val cancelReasonText = reason.ifBlank { "Merchant cancelled rider request" }
        val data = mapOf(
            "status" to "Cancelled",
            "cancelReason" to cancelReasonText,
            "riderRequest.status" to "Cancelled",
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val idx = _orders.value.indexOfFirst { it.id == orderId }
        if (idx >= 0) {
            val updated = _orders.value[idx].copy(
                status = OrderStatus.CANCELLED,
                cancelReason = cancelReasonText,
                riderRequest = _orders.value[idx].riderRequest?.copy(status = "Cancelled")
            )
            val list = _orders.value.toMutableList()
            list[idx] = updated
            _orders.value = list
        }
        if (db == null) {
            onResult(true, null)
            return
        }
        db.collection("orders").document(orderId).update(data)
            .addOnSuccessListener {
                onResult(true, null)
            }
            .addOnFailureListener { ex ->
                val err = ex.localizedMessage ?: ex.message ?: "Failed to cancel rider request"
                Log.e("MerchantRepo", "Cancel rider request failed: $err", ex)
                onResult(false, err)
            }
    }

    fun fetchDeliveryRate(
        cityId: String,
        serviceType: String,
        onResult: (ServiceRate?, String?) -> Unit
    ) {
        val db = firestore ?: return onResult(null, "Firestore unavailable")
        val cId = cityId.ifBlank { "taguig" }
        db.collection("deliveryRates").document(cId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    onResult(null, "Delivery not available in $cId")
                    return@addOnSuccessListener
                }
                val servicesMap = doc.get("services") as? Map<*, *>
                val sType = if (serviceType.isBlank()) "food" else serviceType.lowercase()
                val serviceMap = (servicesMap?.get(sType) as? Map<*, *>) ?: (servicesMap?.get("food") as? Map<*, *>)
                if (serviceMap == null) {
                    onResult(ServiceRate(), null)
                } else {
                    val rate = ServiceRate(
                        baseFare = (serviceMap["baseFare"] as? Number)?.toDouble() ?: 45.0,
                        includedKm = (serviceMap["includedKm"] as? Number)?.toDouble() ?: 2.0,
                        perKmRate = (serviceMap["perKmRate"] as? Number)?.toDouble() ?: 10.0,
                        minFee = (serviceMap["minFee"] as? Number)?.toDouble() ?: 45.0,
                        maxFee = (serviceMap["maxFee"] as? Number)?.toDouble() ?: 300.0,
                        serviceFee = (serviceMap["serviceFee"] as? Number)?.toDouble() ?: 10.0
                    )
                    onResult(rate, null)
                }
            }
            .addOnFailureListener { ex ->
                Log.w("MerchantRepo", "Failed to fetch rate: ${ex.localizedMessage}")
                // Fallback to standard rates if deliveryRates doc isn't populated
                onResult(ServiceRate(), null)
            }
    }

    fun updateStorePhotos(
        logoUrl: String,
        bannerUrl: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val mId = _merchantId.value ?: return onResult(false, "No active merchant")
        val db = firestore ?: return onResult(false, "Firestore unavailable")
        val data = mapOf(
            "logoUrl" to logoUrl.trim(),
            "bannerUrl" to bannerUrl.trim()
        )
        db.collection("merchants").document(mId).update(data)
            .addOnSuccessListener {
                _settings.value = _settings.value.copy(
                    logoUrl = logoUrl.trim(),
                    bannerUrl = bannerUrl.trim()
                )
                onResult(true, null)
            }
            .addOnFailureListener { ex ->
                val err = ex.localizedMessage ?: ex.message ?: "Failed to update store images"
                Log.e("MerchantRepo", "Store photo update failed: $err", ex)
                onResult(false, err)
            }
    }

    fun dismissNewOrderAlert() {
        _newOrderAlert.value = null
    }

    // --- Store Settings Updates ---

    fun toggleStoreOpen(isOpen: Boolean) {
        if (_isServiceLocked.value) return
        if (isOpen) {
            openStore()
        } else {
            closeStore()
        }
    }

    fun closeStore(onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        if (_isServiceLocked.value) {
            onResult(false, _serviceLockMessage.value ?: "Service is disabled by admin")
            return
        }
        val mId = _merchantId.value ?: return onResult(false, "No active merchant session")
        val db = firestore
        val now = System.currentTimeMillis()
        val nextOpen = if (_settings.value.autoSchedule) {
            com.example.util.BusinessHoursHelper.getNextScheduledOpenTimestamp(_settings.value, now)
        } else {
            com.example.util.BusinessHoursHelper.TIMESTAMP_2100_01_01_MS
        }
        val updateData = mutableMapOf<String, Any?>(
            "isOpen" to false,
            "closedUntil" to nextOpen,
            "openUntil" to null,
            "isPaused" to false,
            "pauseUntilTimestamp" to null,
            "closedAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (db != null) {
            db.collection("merchants").document(mId).update(updateData)
                .addOnSuccessListener {
                    _settings.value = _settings.value.copy(
                        isOpen = false,
                        closedUntil = nextOpen,
                        openUntil = null,
                        isPaused = false,
                        pauseUntilTimestamp = null,
                        closedAt = now
                    )
                    prefs.edit().putBoolean("is_open", false).apply()
                    onResult(true, null)
                }
                .addOnFailureListener { ex ->
                    val err = ex.localizedMessage ?: "Failed to close store"
                    Log.e("MerchantRepo", "Close store failed: $err", ex)
                    onResult(false, err)
                }
        } else {
            _settings.value = _settings.value.copy(
                isOpen = false,
                closedUntil = nextOpen,
                openUntil = null,
                isPaused = false,
                pauseUntilTimestamp = null,
                closedAt = now
            )
            prefs.edit().putBoolean("is_open", false).apply()
            onResult(true, null)
        }
    }

    fun openStore(onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        if (_isServiceLocked.value) {
            onResult(false, _serviceLockMessage.value ?: "Service is disabled by admin")
            return
        }
        val mId = _merchantId.value ?: return onResult(false, "No active merchant session")
        val db = firestore
        val now = System.currentTimeMillis()
        val isInside = com.example.util.BusinessHoursHelper.isInsideScheduleWindow(_settings.value.hours, _settings.value.timezone, now)
        val nextClose = if (_settings.value.autoSchedule && !isInside) {
            com.example.util.BusinessHoursHelper.getNextClosingTimestamp(_settings.value, now)
        } else {
            null
        }
        val updateData = mutableMapOf<String, Any?>(
            "isOpen" to true,
            "closedUntil" to null,
            "openUntil" to nextClose,
            "isPaused" to false,
            "pauseUntilTimestamp" to null,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (db != null) {
            db.collection("merchants").document(mId).update(updateData)
                .addOnSuccessListener {
                    _settings.value = _settings.value.copy(
                        isOpen = true,
                        closedUntil = null,
                        openUntil = nextClose,
                        isPaused = false,
                        pauseUntilTimestamp = null
                    )
                    prefs.edit().putBoolean("is_open", true).apply()
                    onResult(true, null)
                }
                .addOnFailureListener { ex ->
                    val err = ex.localizedMessage ?: "Failed to open store"
                    Log.e("MerchantRepo", "Open store failed: $err", ex)
                    onResult(false, err)
                }
        } else {
            _settings.value = _settings.value.copy(
                isOpen = true,
                closedUntil = null,
                openUntil = nextClose,
                isPaused = false,
                pauseUntilTimestamp = null
            )
            prefs.edit().putBoolean("is_open", true).apply()
            onResult(true, null)
        }
    }

    fun pauseOrders(minutes: Int) {
        val pauseUntil = System.currentTimeMillis() + (minutes * 60 * 1000L)
        val mId = _merchantId.value
        val db = firestore
        if (mId != null && db != null) {
            db.collection("merchants").document(mId).update(
                mapOf(
                    "isOpen" to false,
                    "isPaused" to true,
                    "pauseUntilTimestamp" to pauseUntil
                )
            )
        }
        _settings.value = _settings.value.copy(isOpen = false, isPaused = true, pauseUntilTimestamp = pauseUntil)
    }

    fun resumeOrders() {
        openStore()
    }

    fun saveBusinessHours(
        hours: Map<String, DayHours>,
        autoSchedule: Boolean,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        val mId = _merchantId.value ?: return onResult(false, "No active merchant session")
        val db = firestore ?: return onResult(false, "Firestore unavailable")

        val hoursData = hours.mapValues { (_, dh) ->
            mapOf(
                "open" to dh.open,
                "close" to dh.close,
                "closed" to dh.closed
            )
        }

        val updateData = mapOf(
            "hours" to hoursData,
            "autoSchedule" to autoSchedule,
            "autoScheduleEnabled" to autoSchedule
        )

        db.collection("merchants").document(mId).update(updateData)
            .addOnSuccessListener {
                _settings.value = _settings.value.copy(
                    hours = hours,
                    autoSchedule = autoSchedule,
                    autoScheduleEnabled = autoSchedule
                )
                onResult(true, null)
            }
            .addOnFailureListener { ex ->
                onResult(false, ex.localizedMessage ?: "Failed to save business hours")
            }
    }

    fun saveHolidays(
        holidays: List<com.example.data.model.HolidaySchedule>,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        val mId = _merchantId.value ?: return onResult(false, "No active merchant session")
        val db = firestore ?: return onResult(false, "Firestore unavailable")

        val holidaysData = holidays.map { h ->
            mapOf(
                "from" to java.util.Date(h.from),
                "to" to java.util.Date(h.to),
                "note" to h.note
            )
        }

        db.collection("merchants").document(mId).update("holidays", holidaysData)
            .addOnSuccessListener {
                _settings.value = _settings.value.copy(holidays = holidays)
                onResult(true, null)
            }
            .addOnFailureListener { ex ->
                onResult(false, ex.localizedMessage ?: "Failed to save holidays")
            }
    }

    fun saveBluetoothPrinter(
        printerName: String,
        printerAddress: String,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        val mId = _merchantId.value
        val db = firestore
        _settings.value = _settings.value.copy(
            bluetoothPrinterName = printerName,
            bluetoothPrinterAddress = printerAddress
        )
        prefs.edit()
            .putString("bt_printer_name", printerName)
            .putString("bt_printer_address", printerAddress)
            .apply()

        if (mId != null && db != null) {
            db.collection("merchants").document(mId).update(
                mapOf(
                    "bluetoothPrinterName" to printerName,
                    "bluetoothPrinterAddress" to printerAddress
                )
            ).addOnSuccessListener {
                onResult(true, null)
            }.addOnFailureListener { ex ->
                onResult(false, ex.localizedMessage)
            }
        } else {
            onResult(true, null)
        }
    }

    fun checkAndUpdateEffectiveOpen(nowMs: Long = System.currentTimeMillis()) {
        if (_isServiceLocked.value) return
        val mId = _merchantId.value ?: return
        val currentSettings = _settings.value
        val db = firestore

        val updates = mutableMapOf<String, Any?>()
        var updatedClosedUntil = currentSettings.closedUntil
        var updatedOpenUntil = currentSettings.openUntil

        // Clear expired closedUntil
        if (currentSettings.closedUntil != null && nowMs >= currentSettings.closedUntil) {
            updates["closedUntil"] = null
            updatedClosedUntil = null
        }

        // Clear expired openUntil
        if (currentSettings.openUntil != null && nowMs >= currentSettings.openUntil) {
            updates["openUntil"] = null
            updatedOpenUntil = null
        }

        val settingsWithCleared = currentSettings.copy(
            closedUntil = updatedClosedUntil,
            openUntil = updatedOpenUntil
        )

        val effective = com.example.util.BusinessHoursHelper.isEffectiveOpen(settingsWithCleared, nowMs)
        if (effective != currentSettings.isOpen) {
            updates["isOpen"] = effective
        }

        if (updates.isNotEmpty()) {
            updates["updatedAt"] = FieldValue.serverTimestamp()
            if (db != null) {
                db.collection("merchants").document(mId).update(updates)
                    .addOnSuccessListener {
                        _settings.value = settingsWithCleared.copy(isOpen = effective)
                        prefs.edit().putBoolean("is_open", effective).apply()
                    }
                    .addOnFailureListener { ex ->
                        Log.w("MerchantRepo", "Failed to auto-sync effectiveOpen", ex)
                    }
            } else {
                _settings.value = settingsWithCleared.copy(isOpen = effective)
                prefs.edit().putBoolean("is_open", effective).apply()
            }
        }
    }

    fun updateThemeAccent(hex: String) {
        _settings.value = _settings.value.copy(themeAccentHex = hex)
        prefs.edit().putString("theme_accent", hex).apply()
        val mId = _merchantId.value
        firestore?.collection("merchants")?.document(mId ?: return)?.update("themeAccentHex", hex)
    }

    fun toggleDarkMode(isDark: Boolean) {
        _settings.value = _settings.value.copy(isDarkMode = isDark)
        prefs.edit().putBoolean("is_dark_mode", isDark).apply()
        val mId = _merchantId.value
        firestore?.collection("merchants")?.document(mId ?: return)?.update("isDarkMode", isDark)
    }

    fun saveStoreLocation(loc: AddressLocation, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        val mId = _merchantId.value ?: return onResult(false, "No active merchant session")
        val db = firestore ?: return onResult(false, "Firestore unavailable")

        val nowMs = System.currentTimeMillis()
        val data = hashMapOf<String, Any>(
            "address" to loc.formatted,
            "lat" to loc.latitude,
            "lng" to loc.longitude,
            "storeLocation" to mapOf(
                "street" to loc.street,
                "city" to loc.city,
                "latitude" to loc.latitude,
                "longitude" to loc.longitude,
                "notes" to loc.notes
            ),
            "locationChangedAt" to FieldValue.serverTimestamp()
        )

        db.collection("merchants").document(mId).update(data)
            .addOnSuccessListener {
                _settings.value = _settings.value.copy(
                    storeLocation = loc,
                    locationChangedAt = nowMs
                )
                prefs.edit()
                    .putString("loc_street", loc.street)
                    .putString("loc_city", loc.city)
                    .putFloat("loc_lat", loc.latitude.toFloat())
                    .putFloat("loc_lng", loc.longitude.toFloat())
                    .putLong("loc_changed_at", nowMs)
                    .apply()
                onResult(true, null)
            }
            .addOnFailureListener { ex ->
                val err = ex.localizedMessage ?: ex.message ?: "Failed to save location"
                Log.e("MerchantRepo", "Save location failed: $err", ex)
                onResult(false, err)
            }
    }

    fun requestUrgentLocationChange(
        address: String,
        lat: Double,
        lng: Double,
        reason: String,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        val mId = _merchantId.value ?: return onResult(false, "No active merchant session")
        val db = firestore ?: return onResult(false, "Firestore unavailable")

        val trimmedReason = reason.trim()
        if (trimmedReason.isBlank()) {
            return onResult(false, "Reason is required for an urgent location change request")
        }

        val reqId = UUID.randomUUID().toString()
        val data = hashMapOf<String, Any>(
            "id" to reqId,
            "merchantId" to mId,
            "kind" to "location",
            "requestedValue" to mapOf(
                "address" to address.trim(),
                "lat" to lat,
                "lng" to lng
            ),
            "reason" to trimmedReason,
            "status" to "Pending",
            "createdAt" to FieldValue.serverTimestamp()
        )

        db.collection("changeRequests").document(reqId).set(data)
            .addOnSuccessListener {
                onResult(true, null)
            }
            .addOnFailureListener { ex ->
                val err = ex.localizedMessage ?: ex.message ?: "Failed to submit urgent change request"
                Log.e("MerchantRepo", "Urgent location change request failed: $err", ex)
                onResult(false, err)
            }
    }

    fun updateMerchantProfile(
        changedFields: Map<String, Any>,
        onResult: (Boolean, String?) -> Unit
    ) {
        val mId = _merchantId.value ?: return onResult(false, "No active merchant session")
        val db = firestore ?: return onResult(false, "Firestore unavailable")

        val allowedKeys = setOf("ownerName", "phone", "logoUrl", "bannerUrl", "description")
        val filteredData = changedFields.filterKeys { it in allowedKeys }.toMutableMap()

        if (filteredData.isEmpty()) {
            onResult(true, null)
            return
        }

        filteredData["updatedAt"] = FieldValue.serverTimestamp()

        db.collection("merchants").document(mId).update(filteredData)
            .addOnSuccessListener {
                onResult(true, null)
            }
            .addOnFailureListener { ex ->
                val err = ex.localizedMessage ?: ex.message ?: "Failed to update profile"
                Log.e("MerchantRepo", "Update profile failed: $err", ex)
                onResult(false, err)
            }
    }

    fun updateMerchantProfile(
        ownerName: String,
        phone: String,
        logoUrl: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val fields = mutableMapOf<String, Any>()
        if (ownerName.trim() != _settings.value.ownerName.trim()) fields["ownerName"] = ownerName.trim()
        if (phone.trim() != _settings.value.phone.trim()) fields["phone"] = phone.trim()
        if (logoUrl.trim() != _settings.value.logoUrl.trim()) fields["logoUrl"] = logoUrl.trim()
        updateMerchantProfile(fields, onResult)
    }

    fun updateStoreSettings(
        storeName: String,
        phone: String,
        minOrder: Double,
        prep: Int,
        sound: Boolean,
        vibration: Boolean,
        paper: String,
        autoPrint: Boolean
    ) {
        _settings.value = _settings.value.copy(
            storeName = storeName,
            phone = phone,
            minimumOrder = minOrder,
            defaultPrepTime = prep,
            soundEnabled = sound,
            vibrationEnabled = vibration,
            printerPaperSize = paper,
            autoPrintOrders = autoPrint
        )
        val mId = _merchantId.value ?: return
        firestore?.collection("merchants")?.document(mId)?.update(
            mapOf(
                "phone" to phone,
                "minimumOrder" to minOrder,
                "defaultPrepTime" to prep,
                "soundEnabled" to sound,
                "vibrationEnabled" to vibration,
                "printerPaperSize" to paper,
                "autoPrintOrders" to autoPrint
            )
        )
    }

    fun setBusinessType(type: BusinessType) {
        _settings.value = _settings.value.copy(businessType = type)
        val mId = _merchantId.value ?: return
        firestore?.collection("merchants")?.document(mId)?.update("businessType", if (type == BusinessType.FOOD) "food" else "convenience")
    }

    fun saveBankDetails(bank: String, holder: String, num: String) {
        _settings.value = _settings.value.copy(bankName = bank, accountHolder = holder, accountNumber = num)
        val mId = _merchantId.value ?: return
        firestore?.collection("merchants")?.document(mId)?.update(
            mapOf("bankName" to bank, "accountHolder" to holder, "accountNumber" to num)
        )
    }

    fun requestPayout(amount: Double) {
        val mId = _merchantId.value ?: return
        val db = firestore ?: return
        val docRef = db.collection("merchants").document(mId).collection("payouts").document()
        val data = hashMapOf(
            "reference" to ("PAY-" + UUID.randomUUID().toString().take(6).uppercase()),
            "amount" to amount,
            "status" to "Processing",
            "date" to "Today",
            "bankName" to _settings.value.bankName,
            "accountNumber" to _settings.value.accountNumber,
            "createdAt" to System.currentTimeMillis()
        )
        docRef.set(data)
    }

    // --- Menu Management Actions (Items live in merchants/{id}/items) ---

    fun saveMenuItem(item: MenuItem, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        val mId = _merchantId.value ?: return onResult(false, "No active merchant session")
        val db = firestore ?: return onResult(false, "Firestore unavailable")
        val docId = item.id.ifBlank { UUID.randomUUID().toString() }
        val categoryName = item.categoryName.ifBlank {
            _categories.value.find { it.id == item.categoryId }?.name ?: ""
        }

        val setsList = item.sets.mapIndexed { idx, s ->
            val typedName = s.name.trim()
            mutableMapOf<String, Any?>(
                "id" to s.id.ifBlank { UUID.randomUUID().toString() },
                "name" to typedName,
                "customName" to typedName,
                "subcategoryId" to null,
                "subcategoryName" to null,
                "required" to s.required,
                "minSelect" to if (s.required) s.minSelect.coerceIn(1, s.maxSelect) else 0,
                "maxSelect" to s.maxSelect,
                "sortOrder" to idx,
                "choices" to s.choices.map { c ->
                    mapOf(
                        "id" to c.id.ifBlank { UUID.randomUUID().toString() },
                        "name" to c.name.trim(),
                        "price" to c.price,
                        "available" to c.available,
                        "popular" to c.popular
                    )
                }
            )
        }

        val effectiveInStock = if (item.trackStock && item.stockCount <= 0) false else item.inStock

        val itemMap = hashMapOf<String, Any?>(
            "name" to item.name,
            "description" to item.description,
            "price" to item.price,
            "imageUrl" to item.imageUrl,
            "categoryId" to item.categoryId,
            "categoryName" to categoryName,
            "subcategoryId" to item.subcategoryId,
            "subcategoryName" to item.subcategoryName,
            "menuIds" to item.getEffectiveMenuIds(),
            "available" to effectiveInStock,
            "inStock" to effectiveInStock,
            "soldOutReason" to (if (item.trackStock && item.stockCount <= 0) "Out of stock" else (item.soldOutReason ?: "")),
            "businessType" to if (item.businessType == BusinessType.FOOD) "food" else "convenience",
            "prepTime" to item.prepTimeMinutes,
            "tags" to item.tags,
            "sku" to item.sku,
            "barcode" to item.barcode,
            "unit" to item.unit,
            "trackStock" to item.trackStock,
            "stockCount" to item.stockCount,
            "lowStockAt" to item.lowStockAt,
            "itemSize" to item.itemSize.trim(),
            "availability" to item.availability?.let { avail ->
                mapOf(
                    "days" to avail.days,
                    "start" to avail.start,
                    "end" to avail.end,
                    "dateFrom" to avail.dateFrom,
                    "dateTo" to avail.dateTo
                )
            },
            "hasSets" to item.sets.isNotEmpty(),
            "sets" to setsList,
            "sortOrder" to item.sortOrder
        )
        if (!item.copiedFromMerchantId.isNullOrBlank()) {
            itemMap["copiedFromMerchantId"] = item.copiedFromMerchantId
        }
        if (!item.copiedFromItemId.isNullOrBlank()) {
            itemMap["copiedFromItemId"] = item.copiedFromItemId
        }
        if (item.copiedAt != null) {
            itemMap["copiedAt"] = item.copiedAt
        }
        db.collection("merchants").document(mId).collection("items").document(docId).set(itemMap)
            .addOnSuccessListener {
                syncCuisineTags(mId)
                onResult(true, null)
            }
            .addOnFailureListener { ex ->
                val err = ex.localizedMessage ?: ex.message ?: "Failed to save item"
                Log.e("MerchantRepo", "Save item failed: $err", ex)
                onResult(false, err)
            }
    }

    fun deleteMenuItem(itemId: String) {
        val mId = _merchantId.value ?: return
        firestore?.collection("merchants")?.document(mId)?.collection("items")?.document(itemId)?.delete()
            ?.addOnSuccessListener {
                syncCuisineTags(mId)
            }
    }

    fun toggleItemAvailability(itemId: String, inStock: Boolean) {
        val mId = _merchantId.value ?: return
        firestore?.collection("merchants")?.document(mId)?.collection("items")?.document(itemId)?.update(
            mapOf("available" to inStock, "inStock" to inStock)
        )?.addOnSuccessListener {
            syncCuisineTags(mId)
        }
    }

    fun assignItemCategory(itemId: String, categoryId: String, categoryName: String) {
        val mId = _merchantId.value ?: return
        val db = firestore ?: return
        db.collection("merchants").document(mId).collection("items").document(itemId).update(
            mapOf(
                "categoryId" to categoryId,
                "categoryName" to categoryName
            )
        ).addOnSuccessListener {
            syncCuisineTags(mId)
        }
    }

    // --- Category Management Actions (Categories live in merchants/{id}/categories) ---

    fun addCategory(name: String, businessType: BusinessType) {
        val mId = _merchantId.value ?: return
        val db = firestore ?: return
        val docId = name.lowercase().trim().replace(" ", "_").ifBlank { UUID.randomUUID().toString() }
        val newOrder = (_categories.value.maxOfOrNull { it.sortOrder } ?: 0) + 1
        db.collection("merchants").document(mId).collection("categories").document(docId).set(
            mapOf(
                "name" to name.trim(),
                "sortOrder" to newOrder,
                "visible" to true,
                "businessType" to if (businessType == BusinessType.FOOD) "food" else "convenience"
            )
        )
    }

    fun deleteCategory(catId: String) {
        val mId = _merchantId.value ?: return
        firestore?.collection("merchants")?.document(mId)?.collection("categories")?.document(catId)?.delete()
    }

    fun reorderCategory(catId: String, moveUp: Boolean) {
        val mId = _merchantId.value ?: return
        val db = firestore ?: return
        val currentList = _categories.value.sortedBy { it.sortOrder }.toMutableList()
        val index = currentList.indexOfFirst { it.id == catId }
        if (index == -1) return
        val targetIndex = if (moveUp) index - 1 else index + 1
        if (targetIndex in 0 until currentList.size) {
            val item1 = currentList[index]
            val item2 = currentList[targetIndex]
            val order1 = item1.sortOrder
            val order2 = item2.sortOrder
            db.collection("merchants").document(mId).collection("categories").document(item1.id)
                .update("sortOrder", order2)
            db.collection("merchants").document(mId).collection("categories").document(item2.id)
                .update("sortOrder", order1)
        }
    }

    fun toggleCategoryVisibility(catId: String) {
        val mId = _merchantId.value ?: return
        val current = _categories.value.find { it.id == catId } ?: return
        firestore?.collection("merchants")?.document(mId)?.collection("categories")?.document(catId)
            ?.update("visible", !current.visible)
    }

    // --- Subcategories (Category Class) Management Actions: merchants/{shopId}/subcategories/{subId} ---

    fun createSubcategory(name: String, onResult: (Boolean, Subcategory?, String?) -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > 40) {
            onResult(false, null, "Category name must be between 1 and 40 characters")
            return
        }
        if (_subcategories.value.size >= 30) {
            onResult(false, null, "Maximum of 30 categories allowed per shop")
            return
        }
        if (_subcategories.value.any { it.name.trim().equals(trimmed, ignoreCase = true) }) {
            onResult(false, null, "Category \"$trimmed\" already exists")
            return
        }
        val mId = _merchantId.value ?: run {
            onResult(false, null, "No active merchant session")
            return
        }
        val db = firestore
        val subId = UUID.randomUUID().toString()
        val nextOrder = (_subcategories.value.maxOfOrNull { it.sortOrder } ?: 0) + 1
        val nowMs = System.currentTimeMillis()
        val subcat = Subcategory(
            id = subId,
            name = trimmed,
            sortOrder = nextOrder,
            createdAt = nowMs,
            updatedAt = nowMs
        )

        if (db != null) {
            val docData = mapOf(
                "name" to trimmed,
                "sortOrder" to nextOrder,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("merchants").document(mId).collection("subcategories").document(subId).set(docData)
                .addOnSuccessListener {
                    if (!_subcategories.value.any { it.id == subId }) {
                        _subcategories.value = (_subcategories.value + subcat).sortedWith(compareBy({ it.sortOrder }, { it.name.lowercase() }))
                    }
                    onResult(true, subcat, null)
                }
                .addOnFailureListener { ex ->
                    val err = ex.localizedMessage ?: "Failed to create category"
                    Log.e("MerchantRepo", "Create subcategory failed: $err", ex)
                    onResult(false, null, err)
                }
        } else {
            _subcategories.value = (_subcategories.value + subcat).sortedWith(compareBy({ it.sortOrder }, { it.name.lowercase() }))
            onResult(true, subcat, null)
        }
    }

    fun renameSubcategory(subId: String, newName: String, onResult: (Boolean, String?) -> Unit) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed.length > 40) {
            onResult(false, "Category name must be between 1 and 40 characters")
            return
        }
        if (_subcategories.value.any { it.id != subId && it.name.trim().equals(trimmed, ignoreCase = true) }) {
            onResult(false, "Category \"$trimmed\" already exists")
            return
        }
        val mId = _merchantId.value ?: run {
            onResult(false, "No active merchant session")
            return
        }
        val db = firestore

        _subcategories.value = _subcategories.value.map {
            if (it.id == subId) it.copy(name = trimmed, updatedAt = System.currentTimeMillis()) else it
        }

        if (db != null) {
            db.collection("merchants").document(mId).collection("subcategories").document(subId)
                .update(
                    mapOf(
                        "name" to trimmed,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .addOnSuccessListener {
                    // Update products' subcategoryName in batches of 400
                    db.collection("merchants").document(mId).collection("items")
                        .whereEqualTo("subcategoryId", subId)
                        .get()
                        .addOnSuccessListener { snap ->
                            val docs = snap.documents
                            if (docs.isNotEmpty()) {
                                val chunks = docs.chunked(400)
                                for (chunk in chunks) {
                                    val batch = db.batch()
                                    for (d in chunk) {
                                        batch.update(d.reference, "subcategoryName", trimmed)
                                    }
                                    batch.commit()
                                }
                            }
                            onResult(true, null)
                        }
                        .addOnFailureListener {
                            onResult(true, null)
                        }
                }
                .addOnFailureListener { ex ->
                    val err = ex.localizedMessage ?: "Failed to rename category"
                    onResult(false, err)
                }
        } else {
            _menuItems.value = _menuItems.value.map {
                if (it.subcategoryId == subId) it.copy(subcategoryName = trimmed) else it
            }
            onResult(true, null)
        }
    }

    fun deleteSubcategory(subId: String, onResult: (Boolean, String?) -> Unit) {
        val mId = _merchantId.value ?: run {
            onResult(false, "No active merchant session")
            return
        }
        val db = firestore

        _subcategories.value = _subcategories.value.filter { it.id != subId }
        _menuItems.value = _menuItems.value.map {
            if (it.subcategoryId == subId) it.copy(subcategoryId = null, subcategoryName = null) else it
        }

        if (db != null) {
            db.collection("merchants").document(mId).collection("subcategories").document(subId).delete()
                .addOnSuccessListener {
                    // Set those products' subcategoryId and subcategoryName to null (batches of 400)
                    db.collection("merchants").document(mId).collection("items")
                        .whereEqualTo("subcategoryId", subId)
                        .get()
                        .addOnSuccessListener { snap ->
                            val docs = snap.documents
                            if (docs.isNotEmpty()) {
                                val chunks = docs.chunked(400)
                                for (chunk in chunks) {
                                    val batch = db.batch()
                                    for (d in chunk) {
                                        batch.update(d.reference, mapOf("subcategoryId" to null, "subcategoryName" to null))
                                    }
                                    batch.commit()
                                }
                            }
                            onResult(true, null)
                        }
                        .addOnFailureListener {
                            onResult(true, null)
                        }
                }
                .addOnFailureListener { ex ->
                    val err = ex.localizedMessage ?: "Failed to delete category"
                    onResult(false, err)
                }
        } else {
            onResult(true, null)
        }
    }

    fun reorderSubcategories(reorderedList: List<Subcategory>, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        val mId = _merchantId.value ?: run {
            onResult(false, "No active merchant session")
            return
        }
        val db = firestore
        val updated = reorderedList.mapIndexed { idx, sub -> sub.copy(sortOrder = idx, updatedAt = System.currentTimeMillis()) }
        _subcategories.value = updated

        if (db != null) {
            val batch = db.batch()
            updated.forEach { sub ->
                val ref = db.collection("merchants").document(mId).collection("subcategories").document(sub.id)
                batch.update(ref, mapOf("sortOrder" to sub.sortOrder, "updatedAt" to FieldValue.serverTimestamp()))
            }
            batch.commit()
                .addOnSuccessListener { onResult(true, null) }
                .addOnFailureListener { ex -> onResult(false, ex.localizedMessage ?: "Failed to reorder categories") }
        } else {
            onResult(true, null)
        }
    }

    // --- Menus Management (merchants/{shopId}/menus/{menuId}) ---

    fun saveMenu(
        menu: ShopMenu,
        assignedProductIds: Set<String>? = null,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        val mId = _merchantId.value ?: run {
            onResult(false, "No active merchant session")
            return
        }
        val db = firestore ?: run {
            onResult(false, "Firestore unavailable")
            return
        }
        val trimmedName = menu.name.trim()
        if (trimmedName.length < 2 || trimmedName.length > 40) {
            onResult(false, "Menu name must be between 2 and 40 characters")
            return
        }
        if (_menus.value.any { it.id != menu.id && it.name.trim().equals(trimmedName, ignoreCase = true) }) {
            onResult(false, "A menu with the name \"$trimmedName\" already exists")
            return
        }

        val menuId = if (menu.id.isNotBlank()) menu.id else UUID.randomUUID().toString()
        val schedMap: Map<String, Any?>? = menu.schedule?.let { s ->
            mapOf(
                "days" to s.days,
                "start" to s.start,
                "end" to s.end
            )
        }

        val data = hashMapOf<String, Any?>(
            "name" to trimmedName,
            "active" to menu.active,
            "sortOrder" to menu.sortOrder,
            "schedule" to schedMap,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (menu.id.isBlank()) {
            data["createdAt"] = FieldValue.serverTimestamp()
        }

        db.collection("merchants").document(mId).collection("menus").document(menuId).set(data, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                if (assignedProductIds != null) {
                    // Update menuIds on products: add menuId to assignedProductIds, remove from unassigned (for this menu)
                    val allItems = _menuItems.value
                    val batch = db.batch()
                    var opCount = 0
                    for (item in allItems) {
                        val currIds = item.getEffectiveMenuIds().toMutableSet()
                        val shouldHave = assignedProductIds.contains(item.id)
                        var changed = false
                        if (shouldHave && !currIds.contains(menuId)) {
                            currIds.add(menuId)
                            changed = true
                        } else if (!shouldHave && currIds.contains(menuId)) {
                            currIds.remove(menuId)
                            if (currIds.isEmpty()) currIds.add("main")
                            changed = true
                        }
                        if (changed) {
                            val ref = db.collection("merchants").document(mId).collection("items").document(item.id)
                            batch.update(ref, "menuIds", currIds.toList())
                            opCount++
                        }
                    }
                    if (opCount > 0) {
                        batch.commit()
                    }
                }
                onResult(true, null)
            }
            .addOnFailureListener { ex ->
                onResult(false, ex.localizedMessage ?: "Failed to save menu")
            }
    }

    fun toggleMenuActive(menuId: String, active: Boolean) {
        val mId = _merchantId.value ?: return
        val db = firestore ?: return
        db.collection("merchants").document(mId).collection("menus").document(menuId)
            .update(mapOf("active" to active, "updatedAt" to FieldValue.serverTimestamp()))
    }

    fun duplicateMenu(menuId: String, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        val target = _menus.value.find { it.id == menuId } ?: run {
            onResult(false, "Menu not found")
            return
        }
        var newName = "${target.name} (Copy)"
        var counter = 2
        while (_menus.value.any { it.name.trim().equals(newName, ignoreCase = true) }) {
            newName = "${target.name} (Copy $counter)"
            counter++
        }
        val newMenu = target.copy(
            id = "",
            name = newName,
            sortOrder = (_menus.value.maxOfOrNull { it.sortOrder } ?: 0) + 1
        )
        // Products in original menu are also included in copy
        val productIds = _menuItems.value.filter { it.getEffectiveMenuIds().contains(menuId) }.map { it.id }.toSet()
        saveMenu(newMenu, assignedProductIds = productIds, onResult = onResult)
    }

    fun deleteMenu(menuId: String, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        if (menuId == "main") {
            onResult(false, "Main Menu cannot be deleted")
            return
        }
        val mId = _merchantId.value ?: run {
            onResult(false, "No active merchant session")
            return
        }
        val db = firestore ?: run {
            onResult(false, "Firestore unavailable")
            return
        }

        db.collection("merchants").document(mId).collection("menus").document(menuId).delete()
            .addOnSuccessListener {
                // Products in this menu move to Main Menu (menuIds without menuId; if empty, ["main"])
                db.collection("merchants").document(mId).collection("items")
                    .whereArrayContains("menuIds", menuId)
                    .get()
                    .addOnSuccessListener { snap ->
                        val docs = snap.documents
                        if (docs.isNotEmpty()) {
                            val chunks = docs.chunked(400)
                            for (chunk in chunks) {
                                val batch = db.batch()
                                for (d in chunk) {
                                    val curr = (d.get("menuIds") as? List<*>)?.mapNotNull { it?.toString() }?.filter { it != menuId } ?: emptyList()
                                    val updated = if (curr.isEmpty()) listOf("main") else curr
                                    batch.update(d.reference, "menuIds", updated)
                                }
                                batch.commit()
                            }
                        }
                        onResult(true, null)
                    }
                    .addOnFailureListener {
                        onResult(true, null)
                    }
            }
            .addOnFailureListener { ex ->
                onResult(false, ex.localizedMessage ?: "Failed to delete menu")
            }
    }

    // --- Bulk Edit Item Actions ---

    fun bulkMarkItemsStock(itemIds: Set<String>, inStock: Boolean, onResult: (Boolean, Int, String?) -> Unit) {
        val mId = _merchantId.value ?: return onResult(false, 0, "No active merchant session")
        val db = firestore ?: return onResult(false, 0, "Firestore unavailable")
        if (itemIds.isEmpty()) return onResult(true, 0, null)

        val batch = db.batch()
        itemIds.forEach { id ->
            val ref = db.collection("merchants").document(mId).collection("items").document(id)
            batch.update(ref, mapOf("available" to inStock, "inStock" to inStock))
        }
        batch.commit()
            .addOnSuccessListener {
                syncCuisineTags(mId)
                onResult(true, itemIds.size, null)
            }
            .addOnFailureListener { ex ->
                onResult(false, 0, ex.localizedMessage ?: "Bulk update failed")
            }
    }

    fun bulkMoveItemsToMenu(itemIds: Set<String>, targetMenuId: String, onResult: (Boolean, Int, String?) -> Unit) {
        val mId = _merchantId.value ?: return onResult(false, 0, "No active merchant session")
        val db = firestore ?: return onResult(false, 0, "Firestore unavailable")
        if (itemIds.isEmpty()) return onResult(true, 0, null)

        val batch = db.batch()
        itemIds.forEach { id ->
            val ref = db.collection("merchants").document(mId).collection("items").document(id)
            batch.update(ref, "menuIds", listOf(targetMenuId))
        }
        batch.commit()
            .addOnSuccessListener { onResult(true, itemIds.size, null) }
            .addOnFailureListener { ex -> onResult(false, 0, ex.localizedMessage ?: "Failed to move products to menu") }
    }

    fun bulkSetCategoryClass(itemIds: Set<String>, subcategoryId: String?, subcategoryName: String?, onResult: (Boolean, Int, String?) -> Unit) {
        val mId = _merchantId.value ?: return onResult(false, 0, "No active merchant session")
        val db = firestore ?: return onResult(false, 0, "Firestore unavailable")
        if (itemIds.isEmpty()) return onResult(true, 0, null)

        val batch = db.batch()
        itemIds.forEach { id ->
            val ref = db.collection("merchants").document(mId).collection("items").document(id)
            batch.update(ref, mapOf(
                "subcategoryId" to subcategoryId,
                "subcategoryName" to subcategoryName
            ))
        }
        batch.commit()
            .addOnSuccessListener { onResult(true, itemIds.size, null) }
            .addOnFailureListener { ex -> onResult(false, 0, ex.localizedMessage ?: "Failed to update category class") }
    }

    fun bulkAdjustPrices(itemIds: Set<String>, percentDelta: Double, fixedDelta: Double, onResult: (Boolean, Int, String?) -> Unit) {
        val mId = _merchantId.value ?: return onResult(false, 0, "No active merchant session")
        val db = firestore ?: return onResult(false, 0, "Firestore unavailable")
        if (itemIds.isEmpty()) return onResult(true, 0, null)

        val targetItems = _menuItems.value.filter { it.id in itemIds }
        val batch = db.batch()
        targetItems.forEach { item ->
            val oldPrice = item.price
            val newPrice = if (percentDelta != 0.0) {
                (oldPrice * (1.0 + (percentDelta / 100.0))).coerceAtLeast(0.0)
            } else {
                (oldPrice + fixedDelta).coerceAtLeast(0.0)
            }
            val ref = db.collection("merchants").document(mId).collection("items").document(item.id)
            batch.update(ref, "price", (Math.round(newPrice * 100.0) / 100.0))
        }
        batch.commit()
            .addOnSuccessListener { onResult(true, targetItems.size, null) }
            .addOnFailureListener { ex -> onResult(false, 0, ex.localizedMessage ?: "Failed to adjust prices") }
    }

    fun bulkDeleteItems(itemIds: Set<String>, onResult: (Boolean, Int, String?) -> Unit) {
        val mId = _merchantId.value ?: return onResult(false, 0, "No active merchant session")
        val db = firestore ?: return onResult(false, 0, "Firestore unavailable")
        if (itemIds.isEmpty()) return onResult(true, 0, null)

        val batch = db.batch()
        itemIds.forEach { id ->
            val ref = db.collection("merchants").document(mId).collection("items").document(id)
            batch.delete(ref)
        }
        batch.commit()
            .addOnSuccessListener {
                syncCuisineTags(mId)
                onResult(true, itemIds.size, null)
            }
            .addOnFailureListener { ex -> onResult(false, 0, ex.localizedMessage ?: "Failed to delete products") }
    }

    // --- Staff Management (Uses staffCreator FirebaseApp instance) ---

    fun addStaff(
        name: String,
        phone: String,
        email: String,
        tempPass: String,
        role: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val mId = _merchantId.value ?: return onResult(false, "No active merchant session")
        val db = firestore ?: return onResult(false, "Firestore unavailable")

        try {
            ensureFirebaseInitialized(appContext)
            val defaultApp = FirebaseApp.getInstance()
            val staffApp = try {
                FirebaseApp.getInstance("staffCreator")
            } catch (e: Exception) {
                FirebaseApp.initializeApp(appContext, defaultApp.options, "staffCreator")
            }
            val staffAuth = FirebaseAuth.getInstance(staffApp)

            staffAuth.createUserWithEmailAndPassword(email.trim(), tempPass)
                .addOnSuccessListener { authResult ->
                    val staffUid = authResult.user?.uid ?: ""
                    staffAuth.signOut()

                    val staffData = hashMapOf(
                        "role" to "staff",
                        "merchantId" to mId,
                        "name" to name.trim(),
                        "phone" to phone.trim(),
                        "email" to email.trim(),
                        "staffRole" to role,
                        "status" to "active",
                        "active" to true,
                        "createdAt" to System.currentTimeMillis()
                    )

                    db.collection("users").document(staffUid).set(staffData)
                        .addOnSuccessListener {
                            onResult(true, null)
                        }
                        .addOnFailureListener { ex ->
                            onResult(false, ex.localizedMessage ?: "Failed to save staff record")
                        }
                }
                .addOnFailureListener { ex ->
                    onResult(false, ex.localizedMessage ?: "Failed to create staff login")
                }
        } catch (e: Exception) {
            onResult(false, e.localizedMessage ?: "Staff creation error")
        }
    }

    // --- Change Requests (Profile Read-Only Fields) ---

    fun submitChangeRequest(
        kind: String, // "type" | "shopName" | "email"
        requestedValue: String,
        reason: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val mId = _merchantId.value ?: return onResult(false, "No active merchant")
        val db = firestore ?: return onResult(false, "Database unavailable")

        val reqId = UUID.randomUUID().toString()
        val data = hashMapOf(
            "merchantId" to mId,
            "kind" to kind,
            "requestedValue" to requestedValue.trim(),
            "reason" to reason.trim(),
            "status" to "Pending",
            "createdAt" to System.currentTimeMillis()
        )

        db.collection("changeRequests").document(reqId).set(data)
            .addOnSuccessListener { onResult(true, "Change request submitted to admin") }
            .addOnFailureListener { ex -> onResult(false, ex.localizedMessage ?: "Failed to submit request") }
    }

    // --- Merchant Vouchers (promoCodes/{CODE}) ---

    fun checkPromoCodeExists(
        code: String,
        onResult: (exists: Boolean, error: String?) -> Unit
    ) {
        val db = firestore ?: return onResult(false, "Database unavailable")
        val cleanCode = code.trim().uppercase()
        if (cleanCode.length < 4 || cleanCode.length > 16) {
            return onResult(false, "Code must be between 4 and 16 characters")
        }
        db.collection("promoCodes").document(cleanCode).get()
            .addOnSuccessListener { doc ->
                onResult(doc.exists(), null)
            }
            .addOnFailureListener { ex ->
                onResult(false, ex.localizedMessage ?: "Failed to verify promo code")
            }
    }

    fun saveVoucher(
        voucher: MerchantVoucher,
        isNew: Boolean,
        onResult: (success: Boolean, error: String?) -> Unit
    ) {
        val mId = _merchantId.value ?: return onResult(false, "No active merchant")
        val db = firestore ?: return onResult(false, "Database unavailable")
        val cleanCode = voucher.code.trim().uppercase()
        if (cleanCode.length < 4 || cleanCode.length > 16) {
            return onResult(false, "Code must be between 4 and 16 characters")
        }
        if (voucher.title.trim().isBlank()) {
            return onResult(false, "Title is required")
        }
        if (voucher.value <= 0) {
            return onResult(false, "Discount value must be greater than 0")
        }
        if (voucher.type.equals("percent", ignoreCase = true) && voucher.value > 100) {
            return onResult(false, "Percent discount cannot exceed 100%")
        }

        val isFood = _settings.value.businessType == BusinessType.FOOD ||
                _settings.value.type.contains("food", ignoreCase = true)
        val defaultService = if (isFood) "food" else "convenience"
        val servicesList = if (voucher.services.isNotEmpty()) voucher.services else listOf(defaultService)

        val docRef = db.collection("promoCodes").document(cleanCode)

        fun writeVoucherData(targetUsedCount: Int, targetCreatedAt: Long) {
            val data = hashMapOf<String, Any?>(
                "source" to "merchant",
                "fundedBy" to "merchant",
                "merchantId" to mId,
                "title" to voucher.title.trim(),
                "type" to if (voucher.type.equals("fixed", ignoreCase = true)) "fixed" else "percent",
                "value" to voucher.value,
                "maxDiscount" to if (voucher.type.equals("percent", ignoreCase = true)) voucher.maxDiscount else null,
                "minSubtotal" to voucher.minSubtotal.coerceAtLeast(0.0),
                "appliesTo" to if (voucher.appliesTo.equals("items", ignoreCase = true)) "items" else "store",
                "itemIds" to if (voucher.appliesTo.equals("items", ignoreCase = true)) voucher.itemIds else emptyList<String>(),
                "fulfillment" to when (voucher.fulfillment.lowercase()) {
                    "delivery" -> "delivery"
                    "pickup" -> "pickup"
                    else -> "both"
                },
                "services" to servicesList,
                "startsAt" to voucher.startsAt,
                "expiresAt" to voucher.expiresAt,
                "usageLimit" to voucher.usageLimit,
                "perUserLimit" to voucher.perUserLimit.coerceAtLeast(1),
                "usedCount" to targetUsedCount,
                "active" to voucher.active,
                "visibility" to if (voucher.visibility.equals("private", ignoreCase = true)) "private" else "public",
                "createdAt" to targetCreatedAt,
                "updatedAt" to System.currentTimeMillis()
            )

            docRef.set(data, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    onResult(true, null)
                }
                .addOnFailureListener { ex ->
                    onResult(false, ex.localizedMessage ?: "Failed to save voucher")
                }
        }

        if (isNew) {
            // Confirm code uniqueness before saving
            docRef.get().addOnSuccessListener { existingDoc ->
                if (existingDoc.exists()) {
                    onResult(false, "Voucher code '$cleanCode' already exists. Please choose a different code.")
                } else {
                    writeVoucherData(targetUsedCount = 0, targetCreatedAt = System.currentTimeMillis())
                }
            }.addOnFailureListener { ex ->
                onResult(false, ex.localizedMessage ?: "Failed to verify code uniqueness")
            }
        } else {
            // Keep existing usedCount and createdAt
            docRef.get().addOnSuccessListener { existingDoc ->
                val existingUsed = FormatUtils.parseInt(existingDoc.get("usedCount")) ?: voucher.usedCount
                val existingCreated = FormatUtils.parseTimestamp(existingDoc.get("createdAt")) ?: voucher.createdAt
                writeVoucherData(targetUsedCount = existingUsed, targetCreatedAt = existingCreated)
            }.addOnFailureListener {
                writeVoucherData(targetUsedCount = voucher.usedCount, targetCreatedAt = voucher.createdAt)
            }
        }
    }

    fun toggleVoucherActive(
        code: String,
        active: Boolean,
        onResult: (Boolean, String?) -> Unit
    ) {
        val db = firestore ?: return onResult(false, "Database unavailable")
        val cleanCode = code.trim().uppercase()
        db.collection("promoCodes").document(cleanCode)
            .update("active", active, "updatedAt", System.currentTimeMillis())
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { ex -> onResult(false, ex.localizedMessage ?: "Failed to update voucher status") }
    }

    fun deleteVoucher(
        code: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val db = firestore ?: return onResult(false, "Database unavailable")
        val cleanCode = code.trim().uppercase()
        val docRef = db.collection("promoCodes").document(cleanCode)
        docRef.get().addOnSuccessListener { doc ->
            if (!doc.exists()) {
                return@addOnSuccessListener onResult(true, null)
            }
            val usedCount = FormatUtils.parseInt(doc.get("usedCount")) ?: 0
            if (usedCount > 0) {
                return@addOnSuccessListener onResult(false, "Voucher has already been redeemed $usedCount times and cannot be deleted. Deactivate it instead.")
            }
            docRef.delete()
                .addOnSuccessListener { onResult(true, null) }
                .addOnFailureListener { ex -> onResult(false, ex.localizedMessage ?: "Failed to delete voucher") }
        }.addOnFailureListener { ex ->
            onResult(false, ex.localizedMessage ?: "Failed to check voucher before delete")
        }
    }

    // Memory cache for historical customer status: key = "$merchantId:$customerId:$periodStartMs" -> Boolean
    private val customerPriorDeliveredCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    fun checkCustomerHasPriorDeliveredOrder(
        merchantId: String,
        customerId: String,
        periodStartMs: Long,
        onResult: (hasPriorDelivered: Boolean, missingIndexLink: String?) -> Unit
    ) {
        if (customerId.isBlank()) {
            onResult(false, null)
            return
        }

        val cacheKey = "$merchantId:$customerId:$periodStartMs"
        val cached = customerPriorDeliveredCache[cacheKey]
        if (cached != null) {
            onResult(cached, null)
            return
        }

        // First check in-memory loaded orders if any order exists before periodStartMs
        val localPrior = _orders.value.any { ord ->
            ord.merchantId == merchantId &&
            ord.effectiveCustomerId == customerId &&
            ord.placedAtTimestamp < periodStartMs &&
            ord.isDeliveredOrCompleted
        }
        if (localPrior) {
            customerPriorDeliveredCache[cacheKey] = true
            onResult(true, null)
            return
        }

        val db = firestore
        if (db == null) {
            onResult(false, null)
            return
        }

        // Query Firestore: orders where merchantId == shop and customerId == X and createdAt < periodStart, ordered by createdAt descending, limit 5
        val periodDate = java.util.Date(periodStartMs)
        val periodTimestamp = Timestamp(periodDate)

        db.collection("orders")
            .whereEqualTo("merchantId", merchantId)
            .whereEqualTo("customerId", customerId)
            .whereLessThan("createdAt", periodTimestamp)
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(5)
            .get()
            .addOnSuccessListener { snapshot ->
                val hasDelivered = snapshot.documents.any { doc ->
                    val statusStr = doc.getString("status") ?: ""
                    statusStr.equals("Delivered", ignoreCase = true) ||
                    statusStr.equals("Completed", ignoreCase = true) ||
                    statusStr.equals(PlatformOrderStatuses.DELIVERED, ignoreCase = true) ||
                    statusStr.equals(PlatformOrderStatuses.COMPLETED, ignoreCase = true)
                }
                customerPriorDeliveredCache[cacheKey] = hasDelivered
                onResult(hasDelivered, null)
            }
            .addOnFailureListener { ex ->
                val msg = ex.message ?: ""
                var indexLink: String? = null
                if (msg.contains("index", ignoreCase = true) || msg.contains("FAILED_PRECONDITION", ignoreCase = true)) {
                    Log.w("MerchantRepo", "Firestore composite index missing: $msg")
                    val match = Regex("""https://console\.firebase\.google\.com[^\s]+""").find(msg)
                    indexLink = match?.value
                }
                // Fallback: query without inequality/orderBy to avoid index requirement
                db.collection("orders")
                    .whereEqualTo("merchantId", merchantId)
                    .whereEqualTo("customerId", customerId)
                    .limit(20)
                    .get()
                    .addOnSuccessListener { fallbackSnap ->
                        val hasDelivered = fallbackSnap.documents.any { doc ->
                            val createdMs = FormatUtils.parseTimestamp(doc.get("createdAt"))
                                ?: FormatUtils.parseTimestamp(doc.get("placedAtTimestamp")) ?: 0L
                            val statusStr = doc.getString("status") ?: ""
                            createdMs < periodStartMs && (
                                statusStr.equals("Delivered", ignoreCase = true) ||
                                statusStr.equals("Completed", ignoreCase = true) ||
                                statusStr.equals(PlatformOrderStatuses.DELIVERED, ignoreCase = true) ||
                                statusStr.equals(PlatformOrderStatuses.COMPLETED, ignoreCase = true)
                            )
                        }
                        customerPriorDeliveredCache[cacheKey] = hasDelivered
                        onResult(hasDelivered, indexLink)
                    }
                    .addOnFailureListener {
                        customerPriorDeliveredCache[cacheKey] = false
                        onResult(false, indexLink)
                    }
            }
    }

    fun listenToChatMessages(chatId: String): Flow<List<ChatMessage>> = callbackFlow {
        val db = firestore ?: run {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = db.collection("orderChats").document(chatId).collection("chatMessages")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("MerchantRepo", "Chat messages error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val nowMs = System.currentTimeMillis()
                    val msgs = snapshot.documents.mapNotNull { doc ->
                        val expireAt = FormatUtils.parseTimestamp(doc.get("expireAt"))
                        if (expireAt != null && expireAt > 0L && expireAt < nowMs) {
                            null
                        } else {
                            ChatMessage(
                                id = doc.id,
                                senderId = doc.getString("senderId") ?: "",
                                senderRole = doc.getString("senderRole") ?: "merchant",
                                type = doc.getString("type") ?: "text",
                                text = doc.getString("text") ?: "",
                                image = doc.getString("image"),
                                source = doc.getString("source"),
                                createdAt = FormatUtils.parseTimestamp(doc.get("createdAt")) ?: 0L,
                                expireAt = expireAt
                            )
                        }
                    }.sortedBy { it.createdAt }
                    trySend(msgs)
                }
            }
        awaitClose { registration.remove() }
    }

    fun sendCustomerMessage(
        order: Order,
        text: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            onResult(false, "Message cannot be empty")
            return
        }
        if (trimmed.length > 1000) {
            onResult(false, "Message too long (max 1000 characters)")
            return
        }
        val db = firestore ?: run {
            onResult(false, "Database unavailable")
            return
        }
        val mId = _merchantId.value ?: run {
            onResult(false, "Merchant not logged in")
            return
        }

        val chatId = "${order.id}_merchant"
        val threadRef = db.collection("orderChats").document(chatId)

        threadRef.get().addOnSuccessListener { threadDoc ->
            val batch = db.batch()
            val nowMs = System.currentTimeMillis()
            val expireAtTimestamp = Timestamp(Date(nowMs + 10 * 86400000L))

            if (!threadDoc.exists()) {
                val firstName = order.customerName.trim().split(" ").firstOrNull()?.ifBlank { "Customer" } ?: "Customer"
                val ordNum = if (order.orderNumber.isNotBlank()) order.orderNumber else order.formattedOrderNumber
                val threadData = hashMapOf<String, Any?>(
                    "orderId" to order.id,
                    "orderNumber" to ordNum,
                    "channel" to "merchant",
                    "customerId" to order.effectiveCustomerId,
                    "customerName" to firstName,
                    "merchantId" to mId,
                    "merchantName" to (_settings.value.storeName.ifBlank { "Store" }),
                    "riderId" to order.riderId,
                    "riderName" to order.riderName,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "lastMessageText" to trimmed.take(80),
                    "lastSenderRole" to "merchant",
                    "partnerReadAt" to FieldValue.serverTimestamp(),
                    "expireAt" to expireAtTimestamp
                )
                batch.set(threadRef, threadData)
            } else {
                batch.update(
                    threadRef,
                    mapOf(
                        "lastMessageAt" to FieldValue.serverTimestamp(),
                        "lastMessageText" to trimmed.take(80),
                        "lastSenderRole" to "merchant",
                        "partnerReadAt" to FieldValue.serverTimestamp(),
                        "expireAt" to expireAtTimestamp
                    )
                )
            }

            val msgRef = threadRef.collection("chatMessages").document()
            val msgData = hashMapOf(
                "senderId" to mId,
                "senderRole" to "merchant",
                "type" to "text",
                "text" to trimmed,
                "createdAt" to FieldValue.serverTimestamp(),
                "expireAt" to expireAtTimestamp
            )
            batch.set(msgRef, msgData)

            batch.commit()
                .addOnSuccessListener { onResult(true, null) }
                .addOnFailureListener { ex -> onResult(false, ex.localizedMessage ?: "Failed to send message") }
        }.addOnFailureListener { ex ->
            onResult(false, ex.localizedMessage ?: "Failed to check chat thread")
        }
    }

    fun markChatAsRead(orderId: String) {
        val db = firestore ?: return
        val chatId = "${orderId}_merchant"
        val threadRef = db.collection("orderChats").document(chatId)
        threadRef.get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                threadRef.update("partnerReadAt", FieldValue.serverTimestamp())
                    .addOnFailureListener { ex ->
                        Log.w("MerchantRepo", "Failed to mark chat as read: ${ex.message}")
                    }
            }
        }
    }

    private var lastChatCleanupTimestamp = 0L

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { result ->
                if (continuation.isActive) continuation.resume(result)
            }
            addOnFailureListener { exception ->
                if (continuation.isActive) continuation.resumeWithException(exception)
            }
            addOnCanceledListener {
                if (continuation.isActive) continuation.cancel()
            }
        }

    fun triggerExpiredChatCleanup(force: Boolean = false, onComplete: ((Int) -> Unit)? = null) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastChatCleanupTimestamp < 30 * 60 * 1000L)) {
            onComplete?.invoke(0)
            return
        }
        lastChatCleanupTimestamp = now

        val mId = _merchantId.value ?: run {
            onComplete?.invoke(0)
            return
        }
        val db = firestore ?: run {
            onComplete?.invoke(0)
            return
        }

        scope.launch(Dispatchers.IO) {
            var totalDeleted = 0
            try {
                val nowTimestamp = Timestamp(Date(now))
                val isAdmin = _currentUser.value?.role.equals("admin", true)

                // 1. Gather threads belonging to the signed-in merchant
                val currentThreads = _orderChats.value.values.filter {
                    it.merchantId == mId && it.channel == "merchant"
                }

                val threadsToProcess = if (currentThreads.isNotEmpty()) {
                    currentThreads
                } else {
                    try {
                        val snapshot = db.collection("orderChats")
                            .whereEqualTo("merchantId", mId)
                            .whereEqualTo("channel", "merchant")
                            .get()
                            .awaitTask()
                        snapshot.documents.mapNotNull { doc ->
                            val ordId = doc.getString("orderId") ?: ""
                            val merchant = doc.getString("merchantId") ?: ""
                            val channel = doc.getString("channel") ?: "merchant"
                            if (merchant == mId && channel == "merchant") {
                                OrderChatThread(
                                    chatId = doc.id,
                                    orderId = ordId,
                                    merchantId = merchant,
                                    channel = channel,
                                    expireAt = FormatUtils.parseTimestamp(doc.get("expireAt"))
                                )
                            } else null
                        }
                    } catch (e: Exception) {
                        emptyList()
                    }
                }

                // Process only threads that belong to the signed-in user
                for (thread in threadsToProcess) {
                    if (thread.merchantId != mId) continue

                    try {
                        // Query its chatMessages where expireAt < now (limit 100)
                        val msgSnapshot = db.collection("orderChats")
                            .document(thread.chatId)
                            .collection("chatMessages")
                            .whereLessThan("expireAt", nowTimestamp)
                            .limit(100)
                            .get()
                            .awaitTask()

                        val isThreadExpired = thread.expireAt != null && thread.expireAt > 0L && thread.expireAt < now
                        val hasExpiredMessages = !msgSnapshot.isEmpty

                        if (hasExpiredMessages || isThreadExpired) {
                            val batch = db.batch()
                            var batchItems = 0

                            for (msgDoc in msgSnapshot.documents) {
                                batch.delete(msgDoc.reference)
                                batchItems++
                            }

                            // Delete the thread itself if its own expireAt < now
                            if (isThreadExpired) {
                                val threadRef = db.collection("orderChats").document(thread.chatId)
                                batch.delete(threadRef)
                                batchItems++
                            }

                            if (batchItems > 0) {
                                batch.commit().awaitTask()
                                totalDeleted += batchItems
                            }
                        }
                    } catch (e: Exception) {
                        // Silent ignore
                    }
                }

                // If ADMIN: platform-wide cleanup up to 300 documents per session
                if (isAdmin) {
                    try {
                        val expiredThreads = db.collection("orderChats")
                            .whereLessThan("expireAt", nowTimestamp)
                            .limit(50)
                            .get()
                            .awaitTask()

                        var adminCount = 0
                        val adminBatch = db.batch()

                        for (doc in expiredThreads.documents) {
                            if (adminCount >= 300) break
                            adminBatch.delete(doc.reference)
                            adminCount++

                            try {
                                val subMsgs = doc.reference.collection("chatMessages")
                                    .limit(50)
                                    .get()
                                    .awaitTask()
                                for (subDoc in subMsgs.documents) {
                                    if (adminCount >= 300) break
                                    adminBatch.delete(subDoc.reference)
                                    adminCount++
                                }
                            } catch (e: Exception) {
                                // Silent
                            }
                        }

                        if (adminCount > 0) {
                            adminBatch.commit().awaitTask()
                            totalDeleted += adminCount
                        }
                    } catch (e: Exception) {
                        // Silent
                    }
                }
            } catch (e: Exception) {
                // Ignore any errors silently
            } finally {
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(totalDeleted)
                }
            }
        }
    }

    // =========================================================================
    // MULTI-OUTLET: SWITCHING, PIN, BRANCH SETUP, COMBINED ANALYTICS & MENU SYNC
    // =========================================================================

    fun switchActiveOutlet(targetMerchantId: String) {
        if (targetMerchantId.isBlank() || targetMerchantId == _merchantId.value) return
        val uid = _currentUser.value?.id ?: return
        prefs.edit().putString("active_outlet_$uid", targetMerchantId).apply()
        val matched = _outlets.value.find { it.merchantId == targetMerchantId }
        if (matched != null) {
            _settings.value = matched
        }
        _merchantId.value = targetMerchantId
        attachFirestoreListeners(targetMerchantId)
        val currentList = _outlets.value
        if (currentList.size >= 2) {
            syncAllOutletOrderListeners(currentList, targetMerchantId)
        }
        if (_otherOutletOrderBanner.value?.merchantId == targetMerchantId) {
            _otherOutletOrderBanner.value = null
        }
    }

    fun dismissOtherOutletOrderBanner() {
        _otherOutletOrderBanner.value = null
    }

    fun onAppBackground() {
        pinSessionManager.onAppBackground()
    }

    fun onAppForeground() {
        pinSessionManager.onAppForeground()
    }

    fun hasOutletsPin(): Boolean {
        return !_userOutletsPinHash.value.isNullOrBlank() && !_userOutletsPinSalt.value.isNullOrBlank()
    }

    fun isOutletsPinUnlocked(): Boolean = pinSessionManager.isUnlocked()

    fun isPinLockedOut(): Boolean = pinSessionManager.isLockedOut()

    fun remainingPinLockoutSeconds(): Long = pinSessionManager.remainingLockoutSeconds()

    fun saveOutletsPin(pin: String, onResult: (Boolean, String?) -> Unit) {
        if (!OutletsPinHelper.isValidPinFormat(pin)) {
            onResult(false, "PIN must be 4 to 6 digits")
            return
        }
        val uid = _currentUser.value?.id ?: return onResult(false, "Not signed in")
        val db = firestore ?: return onResult(false, "Firestore unavailable")
        val salt = OutletsPinHelper.generateSalt()
        val hash = OutletsPinHelper.hashPin(salt, pin)
        db.collection("users").document(uid)
            .set(
                mapOf(
                    "outletsPinHash" to hash,
                    "outletsPinSalt" to salt,
                    "outletsPinUpdatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .addOnSuccessListener {
                _userOutletsPinHash.value = hash
                _userOutletsPinSalt.value = salt
                pinSessionManager.bindOwner(uid)
                pinSessionManager.unlockDirectlyAfterCreateOrReset()
                onResult(true, null)
            }
            .addOnFailureListener { ex ->
                onResult(false, ex.localizedMessage ?: "Failed to save PIN")
            }
    }

    fun verifyOutletsPin(pin: String): PinVerifyResult {
        val hash = _userOutletsPinHash.value ?: ""
        val salt = _userOutletsPinSalt.value ?: ""
        return pinSessionManager.verifyPin(pin, salt, hash)
    }

    fun verifyAccountPasswordForPinReset(password: String, onResult: (Boolean, String?) -> Unit) {
        val fbUser = auth?.currentUser ?: return onResult(false, "Not signed in")
        val email = fbUser.email ?: _currentUser.value?.email
        if (email.isNullOrBlank()) {
            onResult(false, "Email not found on account")
            return
        }
        if (password.isBlank()) {
            onResult(false, "Enter your account password")
            return
        }
        val cred = EmailAuthProvider.getCredential(email, password)
        fbUser.reauthenticate(cred)
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { ex ->
                onResult(false, ex.localizedMessage ?: "Incorrect account password")
            }
    }

    fun changeOutletsPin(oldPin: String, newPin: String, onResult: (Boolean, String?) -> Unit) {
        val hash = _userOutletsPinHash.value ?: ""
        val salt = _userOutletsPinSalt.value ?: ""
        if (!OutletsPinHelper.verifyPin(oldPin, salt, hash)) {
            onResult(false, "Current PIN is incorrect")
            return
        }
        if (!OutletsPinHelper.isValidPinFormat(newPin)) {
            onResult(false, "New PIN must be 4 to 6 digits")
            return
        }
        saveOutletsPin(newPin, onResult)
    }

    fun checkAndSaveBranchSetup(
        merchantId: String,
        chainId: String,
        branchName: String,
        branchCode: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val cleanName = branchName.trim()
        val cleanCode = OutletsUtils.sanitizeBranchCodeInput(branchCode)
        if (!OutletsUtils.isValidBranchName(cleanName)) {
            onResult(false, "Branch name must be 2 to 40 characters")
            return
        }
        if (!OutletsUtils.isValidBranchCode(cleanCode)) {
            onResult(false, "Branch code must be 2 to 10 letters or numbers (A-Z, 0-9)")
            return
        }
        val db = firestore ?: return onResult(false, "Firestore unavailable")

        val checkUniqueTask = if (chainId.isNotBlank()) {
            db.collection("merchants")
                .whereEqualTo("chainId", chainId)
                .whereEqualTo("branchCode", cleanCode)
                .get()
        } else {
            null
        }

        val proceedSave = {
            db.collection("merchants").document(merchantId)
                .update(
                    mapOf(
                        "branchName" to cleanName,
                        "branchCode" to cleanCode,
                        "branchSetupDone" to true
                    )
                )
                .addOnSuccessListener { onResult(true, null) }
                .addOnFailureListener { ex ->
                    onResult(false, ex.localizedMessage ?: "Failed to save branch details")
                }
        }

        if (checkUniqueTask != null) {
            checkUniqueTask.addOnSuccessListener { snap ->
                val duplicate = snap.documents.any { it.id != merchantId }
                if (duplicate) {
                    onResult(false, "Branch code \"$cleanCode\" is already used by another outlet in this chain.")
                } else {
                    proceedSave()
                }
            }.addOnFailureListener {
                // Fallback: verify against already loaded outlets in chain
                val localDup = _outlets.value.any {
                    it.merchantId != merchantId &&
                        it.chainId == chainId &&
                        it.branchCode.equals(cleanCode, ignoreCase = true)
                }
                if (localDup) {
                    onResult(false, "Branch code \"$cleanCode\" is already used by another outlet in this chain.")
                } else {
                    proceedSave()
                }
            }
        } else {
            proceedSave()
        }
    }

    fun fetchCombinedOutletOrders(
        outletIds: List<String>,
        onResult: (List<Order>, String?) -> Unit
    ) {
        val distinctIds = outletIds.filter { it.isNotBlank() }.distinct()
        if (distinctIds.isEmpty()) {
            onResult(emptyList(), null)
            return
        }
        val db = firestore ?: run {
            onResult(_orders.value.filter { it.merchantId in distinctIds }, null)
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                val merged = mutableListOf<Order>()
                for (oid in distinctIds) {
                    val snap = db.collection("orders")
                        .whereEqualTo("merchantId", oid)
                        .get()
                        .awaitTask()
                    for (doc in snap.documents) {
                        val ord = parseOrderDoc(doc, oid)
                        if (ord != null && isOrderVisible(ord)) {
                            merged.add(ord)
                        }
                    }
                }
                val sorted = merged.distinctBy { it.id }.sortedByDescending { it.placedAtTimestamp }
                withContext(Dispatchers.Main) {
                    onResult(sorted, null)
                }
            } catch (ex: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(emptyList(), ex.localizedMessage ?: "Failed to load combined outlet orders")
                }
            }
        }
    }

    fun fetchCombinedOutletReviews(
        outletIds: List<String>,
        onResult: (List<CustomerReview>) -> Unit
    ) {
        val distinctIds = outletIds.filter { it.isNotBlank() }.distinct()
        val db = firestore ?: run {
            onResult(_reviews.value)
            return
        }
        if (distinctIds.isEmpty()) {
            onResult(emptyList())
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                val merged = mutableListOf<CustomerReview>()
                for (oid in distinctIds) {
                    val snap = db.collection("reviews")
                        .whereEqualTo("merchantId", oid)
                        .get()
                        .awaitTask()
                    for (doc in snap.documents) {
                        merged.add(parseCustomerReview(doc))
                    }
                }
                withContext(Dispatchers.Main) {
                    onResult(merged.distinctBy { it.id }.sortedByDescending { it.createdAt })
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(_reviews.value)
                }
            }
        }
    }

    suspend fun loadOutletMenuSnapshot(outlet: MerchantSettings): OutletMenuSnapshot = withContext(Dispatchers.IO) {
        val mId = outlet.merchantId
        if (mId == _merchantId.value) {
            return@withContext OutletMenuSnapshot(
                merchantId = mId,
                displayName = outlet.displayName,
                branchCode = outlet.branchCode,
                businessType = outlet.businessType,
                status = outlet.status,
                items = _menuItems.value,
                subcategories = _subcategories.value,
                menus = _menus.value
            )
        }
        val db = firestore ?: return@withContext OutletMenuSnapshot(
            merchantId = mId,
            displayName = outlet.displayName,
            branchCode = outlet.branchCode,
            businessType = outlet.businessType,
            status = outlet.status,
            items = emptyList(),
            subcategories = emptyList(),
            menus = listOf(ShopMenu(id = "main", merchantId = mId, name = "Main Menu"))
        )
        val itemsSnap = db.collection("merchants").document(mId).collection("items").get().awaitTask()
        val subSnap = db.collection("merchants").document(mId).collection("subcategories").get().awaitTask()
        val menusSnap = db.collection("merchants").document(mId).collection("menus").get().awaitTask()
        val items = itemsSnap.documents.map { parseMenuItemDoc(it, mId) }
        val subcats = subSnap.documents.mapNotNull { parseSubcategoryDoc(it) }
        val menus = menusSnap.documents.mapNotNull { parseShopMenuDoc(it, mId) }.toMutableList()
        if (menus.none { it.id == "main" }) {
            menus.add(0, ShopMenu(id = "main", merchantId = mId, name = "Main Menu", sortOrder = 0))
        }
        OutletMenuSnapshot(
            merchantId = mId,
            displayName = outlet.displayName,
            branchCode = outlet.branchCode,
            businessType = outlet.businessType,
            status = outlet.status,
            items = items,
            subcategories = subcats,
            menus = menus
        )
    }

    private fun buildFirestoreMenuItemMap(item: MenuItem): MutableMap<String, Any?> {
        val setsList = item.sets.mapIndexed { idx, s ->
            val typedName = s.name.trim()
            mutableMapOf<String, Any?>(
                "id" to s.id.ifBlank { UUID.randomUUID().toString() },
                "name" to typedName,
                "customName" to typedName,
                "subcategoryId" to null,
                "subcategoryName" to null,
                "required" to s.required,
                "minSelect" to if (s.required) s.minSelect.coerceIn(1, s.maxSelect) else 0,
                "maxSelect" to s.maxSelect,
                "sortOrder" to idx,
                "choices" to s.choices.map { c ->
                    mapOf(
                        "id" to c.id.ifBlank { UUID.randomUUID().toString() },
                        "name" to c.name.trim(),
                        "price" to c.price,
                        "available" to c.available,
                        "popular" to c.popular
                    )
                }
            )
        }
        val map = hashMapOf<String, Any?>(
            "name" to item.name,
            "description" to item.description,
            "price" to item.price,
            "imageUrl" to item.imageUrl,
            "categoryId" to item.categoryId,
            "categoryName" to item.categoryName,
            "subcategoryId" to item.subcategoryId,
            "subcategoryName" to item.subcategoryName,
            "menuIds" to item.getEffectiveMenuIds(),
            "available" to item.inStock,
            "inStock" to item.inStock,
            "soldOutReason" to (item.soldOutReason ?: ""),
            "businessType" to if (item.businessType == BusinessType.FOOD) "food" else "convenience",
            "prepTime" to item.prepTimeMinutes,
            "tags" to item.tags,
            "sku" to item.sku,
            "barcode" to item.barcode,
            "unit" to item.unit,
            "trackStock" to item.trackStock,
            "stockCount" to item.stockCount,
            "lowStockAt" to item.lowStockAt,
            "itemSize" to item.itemSize.trim(),
            "availability" to item.availability?.let { avail ->
                mapOf(
                    "days" to avail.days,
                    "start" to avail.start,
                    "end" to avail.end,
                    "dateFrom" to avail.dateFrom,
                    "dateTo" to avail.dateTo
                )
            },
            "hasSets" to item.sets.isNotEmpty(),
            "sets" to setsList,
            "sortOrder" to item.sortOrder
        )
        if (!item.copiedFromMerchantId.isNullOrBlank()) map["copiedFromMerchantId"] = item.copiedFromMerchantId
        if (!item.copiedFromItemId.isNullOrBlank()) map["copiedFromItemId"] = item.copiedFromItemId
        if (item.copiedAt != null) map["copiedAt"] = item.copiedAt
        return map
    }

    private fun prepareCopyWriteOperations(
        db: FirebaseFirestore,
        source: OutletMenuSnapshot,
        target: OutletMenuSnapshot,
        items: List<MenuItem>,
        subcatCache: MutableMap<String, Pair<String, String>>,
        menuCache: MutableMap<String, ShopMenu>,
        nextSubcatOrder: MutableMap<String, Int>,
        nextMenuOrder: MutableMap<String, Int>
    ): List<(WriteBatch) -> Unit> {
        val ops = mutableListOf<(WriteBatch) -> Unit>()
        val targetMid = target.merchantId

        if (subcatCache.isEmpty()) {
            target.subcategories.forEach { sc ->
                subcatCache[OutletsUtils.menuMatchingKey(sc.name)] = sc.id to sc.name
            }
            nextSubcatOrder[targetMid] = (target.subcategories.maxOfOrNull { it.sortOrder } ?: 0) + 1
        }
        if (menuCache.isEmpty()) {
            target.menus.forEach { mn ->
                val k = if (mn.id == "main") "main" else OutletsUtils.menuMatchingKey(mn.name)
                menuCache[k] = mn
            }
            nextMenuOrder[targetMid] = (target.menus.maxOfOrNull { it.sortOrder } ?: 0) + 1
        }

        for (srcItem in items) {
            // Resolve or create target subcategory (category class)
            val srcSubName = MenuSyncEngine.resolveSubcategoryName(srcItem, source.subcategories)
            var resolvedSubId: String? = null
            var resolvedSubName: String? = null
            if (srcSubName.isNotBlank()) {
                val subKey = OutletsUtils.menuMatchingKey(srcSubName)
                val existingSub = subcatCache[subKey]
                if (existingSub != null) {
                    resolvedSubId = existingSub.first
                    resolvedSubName = existingSub.second
                } else {
                    val newSubId = UUID.randomUUID().toString()
                    val order = nextSubcatOrder.getOrDefault(targetMid, 1)
                    nextSubcatOrder[targetMid] = order + 1
                    subcatCache[subKey] = newSubId to srcSubName
                    resolvedSubId = newSubId
                    resolvedSubName = srcSubName
                    val subRef = db.collection("merchants").document(targetMid).collection("subcategories").document(newSubId)
                    ops.add { batch ->
                        batch.set(
                            subRef,
                            mapOf(
                                "name" to srcSubName,
                                "sortOrder" to order,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
            }

            // Resolve or create target menus
            val resolvedMenuIds = mutableListOf<String>()
            for (srcMenuId in srcItem.getEffectiveMenuIds()) {
                if (srcMenuId == "main") {
                    resolvedMenuIds.add("main")
                } else {
                    val srcMenu = source.menus.find { it.id == srcMenuId }
                    val menuName = srcMenu?.name?.trim().orEmpty()
                    if (menuName.isNotBlank()) {
                        val mKey = OutletsUtils.menuMatchingKey(menuName)
                        val existingMenu = menuCache[mKey]
                        if (existingMenu != null) {
                            resolvedMenuIds.add(existingMenu.id)
                        } else {
                            val newMenuId = UUID.randomUUID().toString()
                            val mOrder = nextMenuOrder.getOrDefault(targetMid, 1)
                            nextMenuOrder[targetMid] = mOrder + 1
                            val createdMenu = ShopMenu(
                                id = newMenuId,
                                merchantId = targetMid,
                                name = menuName,
                                active = srcMenu?.active ?: true,
                                sortOrder = mOrder,
                                schedule = srcMenu?.schedule
                            )
                            menuCache[mKey] = createdMenu
                            resolvedMenuIds.add(newMenuId)
                            val menuRef = db.collection("merchants").document(targetMid).collection("menus").document(newMenuId)
                            ops.add { batch ->
                                batch.set(
                                    menuRef,
                                    mapOf(
                                        "name" to createdMenu.name,
                                        "active" to createdMenu.active,
                                        "sortOrder" to createdMenu.sortOrder,
                                        "schedule" to createdMenu.schedule?.let {
                                            mapOf("days" to it.days, "start" to it.start, "end" to it.end)
                                        },
                                        "createdAt" to FieldValue.serverTimestamp(),
                                        "updatedAt" to FieldValue.serverTimestamp()
                                    )
                                )
                            }
                        }
                    }
                }
            }

            val cloned = MenuSyncEngine.buildCopiedItem(
                sourceItem = srcItem,
                sourceMerchantId = source.merchantId,
                targetMerchantId = targetMid,
                targetSubcategoryId = resolvedSubId,
                targetSubcategoryName = resolvedSubName,
                targetMenuIds = resolvedMenuIds.distinct().ifEmpty { listOf("main") }
            )
            val itemRef = db.collection("merchants").document(targetMid).collection("items").document(cloned.id)
            val payload = buildFirestoreMenuItemMap(cloned).apply {
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            ops.add { batch -> batch.set(itemRef, payload) }
        }
        return ops
    }

    fun executePushMenu(
        source: OutletMenuSnapshot,
        targetsWithItems: List<Pair<OutletMenuSnapshot, List<MenuItem>>>,
        totalSkipped: Int,
        onProgress: (Int, Int) -> Unit,
        onComplete: (MenuSyncResult) -> Unit
    ) {
        val db = firestore ?: return onComplete(MenuSyncResult("Push Menu", 0, 0, totalSkipped, targetsWithItems.sumOf { it.second.size }, listOf("Firestore unavailable")))
        scope.launch(Dispatchers.IO) {
            var addedCount = 0
            var failedCount = 0
            val details = mutableListOf<String>()

            // Keep item count accurate separately from helper subcategory/menu doc ops
            val opUnits = mutableListOf<Pair<List<(WriteBatch) -> Unit>, Int>>() // (ops, itemAddedDelta)
            val batchGroups = mutableListOf<Pair<String, List<(WriteBatch) -> Unit>>>()

            for ((target, items) in targetsWithItems) {
                if (items.isEmpty()) continue
                val ops = prepareCopyWriteOperations(
                    db = db,
                    source = source,
                    target = target,
                    items = items,
                    subcatCache = mutableMapOf(),
                    menuCache = mutableMapOf(),
                    nextSubcatOrder = mutableMapOf(),
                    nextMenuOrder = mutableMapOf()
                )
                batchGroups.add("${target.displayName}: ${items.size} product(s)" to ops)
                opUnits.add(ops to items.size)
            }

            val allOps = opUnits.flatMap { it.first }
            val chunks = MenuSyncEngine.chunkForFirestoreBatches(allOps)
            val totalBatches = chunks.size.coerceAtLeast(1)
            withContext(Dispatchers.Main) { onProgress(0, totalBatches) }

            for ((idx, chunk) in chunks.withIndex()) {
                try {
                    val batch = db.batch()
                    chunk.forEach { it(batch) }
                    batch.commit().awaitTask()
                } catch (ex: Exception) {
                    failedCount += chunk.size
                    details.add("Batch ${idx + 1} failed: ${ex.localizedMessage ?: "Write error"}")
                }
                withContext(Dispatchers.Main) { onProgress(idx + 1, totalBatches) }
            }

            val plannedItems = targetsWithItems.sumOf { it.second.size }
            addedCount = (plannedItems - failedCount).coerceAtLeast(0)
            details.addAll(0, batchGroups.map { it.first })

            withContext(Dispatchers.Main) {
                onComplete(
                    MenuSyncResult(
                        operationName = "Push Menu",
                        added = addedCount,
                        updated = 0,
                        skipped = totalSkipped,
                        failed = failedCount,
                        details = details
                    )
                )
            }
        }
    }

    fun executePullMenu(
        target: OutletMenuSnapshot,
        source: OutletMenuSnapshot,
        itemsToCopy: List<MenuItem>,
        skippedCount: Int,
        onProgress: (Int, Int) -> Unit,
        onComplete: (MenuSyncResult) -> Unit
    ) {
        val db = firestore ?: return onComplete(MenuSyncResult("Pull Menu", 0, 0, skippedCount, itemsToCopy.size, listOf("Firestore unavailable")))
        scope.launch(Dispatchers.IO) {
            val ops = prepareCopyWriteOperations(
                db = db,
                source = source,
                target = target,
                items = itemsToCopy,
                subcatCache = mutableMapOf(),
                menuCache = mutableMapOf(),
                nextSubcatOrder = mutableMapOf(),
                nextMenuOrder = mutableMapOf()
            )
            val chunks = MenuSyncEngine.chunkForFirestoreBatches(ops)
            val totalBatches = chunks.size.coerceAtLeast(1)
            var failed = 0
            val details = mutableListOf<String>()
            withContext(Dispatchers.Main) { onProgress(0, totalBatches) }

            for ((idx, chunk) in chunks.withIndex()) {
                try {
                    val batch = db.batch()
                    chunk.forEach { it(batch) }
                    batch.commit().awaitTask()
                } catch (ex: Exception) {
                    failed += chunk.size
                    details.add("Batch ${idx + 1} failed: ${ex.localizedMessage ?: "Write error"}")
                }
                withContext(Dispatchers.Main) { onProgress(idx + 1, totalBatches) }
            }

            val added = (itemsToCopy.size - failed).coerceAtLeast(0)
            details.add(0, "Pulled $added item(s) from ${source.displayName} into ${target.displayName}")
            withContext(Dispatchers.Main) {
                onComplete(
                    MenuSyncResult(
                        operationName = "Pull Menu",
                        added = added,
                        updated = 0,
                        skipped = skippedCount,
                        failed = failed,
                        details = details
                    )
                )
            }
        }
    }

    fun executeSyncMenu(
        mine: OutletMenuSnapshot,
        theirs: OutletMenuSnapshot,
        fieldsToSync: Set<SyncField>,
        plan: SyncPlanPreview,
        resolutions: Map<String, SyncResolution>,
        onProgress: (Int, Int) -> Unit,
        onComplete: (MenuSyncResult) -> Unit
    ) {
        val totalPlanned = plan.addHereItems.size + plan.addThereItems.size + plan.conflicts.size
        val db = firestore ?: return onComplete(MenuSyncResult("Sync Menu", 0, 0, plan.skippedInSyncCount, totalPlanned, listOf("Firestore unavailable")))
        scope.launch(Dispatchers.IO) {
            val allOps = mutableListOf<(WriteBatch) -> Unit>()
            val details = mutableListOf<String>()

            val mineSubCache = mine.subcategories.associate { OutletsUtils.menuMatchingKey(it.name) to (it.id to it.name) }.toMutableMap()
            val theirSubCache = theirs.subcategories.associate { OutletsUtils.menuMatchingKey(it.name) to (it.id to it.name) }.toMutableMap()
            val mineMenuCache = mine.menus.associateBy { if (it.id == "main") "main" else OutletsUtils.menuMatchingKey(it.name) }.toMutableMap()
            val theirMenuCache = theirs.menus.associateBy { if (it.id == "main") "main" else OutletsUtils.menuMatchingKey(it.name) }.toMutableMap()
            val nextSubOrder = mutableMapOf(
                mine.merchantId to ((mine.subcategories.maxOfOrNull { it.sortOrder } ?: 0) + 1),
                theirs.merchantId to ((theirs.subcategories.maxOfOrNull { it.sortOrder } ?: 0) + 1)
            )
            val nextMenuOrder = mutableMapOf(
                mine.merchantId to ((mine.menus.maxOfOrNull { it.sortOrder } ?: 0) + 1),
                theirs.merchantId to ((theirs.menus.maxOfOrNull { it.sortOrder } ?: 0) + 1)
            )

            // 1. Add missing items both ways
            if (plan.addThereItems.isNotEmpty()) {
                allOps.addAll(
                    prepareCopyWriteOperations(db, mine, theirs, plan.addThereItems, theirSubCache, theirMenuCache, nextSubOrder, nextMenuOrder)
                )
            }
            if (plan.addHereItems.isNotEmpty()) {
                allOps.addAll(
                    prepareCopyWriteOperations(db, theirs, mine, plan.addHereItems, mineSubCache, mineMenuCache, nextSubOrder, nextMenuOrder)
                )
            }

            // 2. Apply conflict resolutions (selected fields only)
            var updatedCount = 0
            for (conflict in plan.conflicts) {
                val res = resolutions[conflict.normalizedKey] ?: SyncResolution.USE_MINE
                val srcSnap = if (res == SyncResolution.USE_MINE) mine else theirs
                val dstSnap = if (res == SyncResolution.USE_MINE) theirs else mine
                val srcItem = if (res == SyncResolution.USE_MINE) conflict.myItem else conflict.theirItem
                val dstItem = if (res == SyncResolution.USE_MINE) conflict.theirItem else conflict.myItem
                val dstSubCache = if (res == SyncResolution.USE_MINE) theirSubCache else mineSubCache
                val dstMenuCache = if (res == SyncResolution.USE_MINE) theirMenuCache else mineMenuCache

                val updateMap = mutableMapOf<String, Any?>(
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (SyncField.NAME_DESC in fieldsToSync) {
                    updateMap["name"] = srcItem.name.trim()
                    updateMap["description"] = srcItem.description.trim()
                }
                if (SyncField.PRICE in fieldsToSync) {
                    updateMap["price"] = srcItem.price
                }
                if (SyncField.PHOTO in fieldsToSync) {
                    updateMap["imageUrl"] = srcItem.imageUrl.trim()
                }
                if (SyncField.ITEM_SIZE in fieldsToSync) {
                    updateMap["itemSize"] = srcItem.itemSize.trim()
                }
                if (SyncField.CATEGORY_MENUS in fieldsToSync) {
                    updateMap["categoryId"] = srcItem.categoryId
                    updateMap["categoryName"] = srcItem.categoryName
                    val srcSubName = MenuSyncEngine.resolveSubcategoryName(srcItem, srcSnap.subcategories)
                    if (srcSubName.isNotBlank()) {
                        val subKey = OutletsUtils.menuMatchingKey(srcSubName)
                        val ex = dstSubCache[subKey]
                        if (ex != null) {
                            updateMap["subcategoryId"] = ex.first
                            updateMap["subcategoryName"] = ex.second
                        } else {
                            val newId = UUID.randomUUID().toString()
                            val order = nextSubOrder.getOrDefault(dstSnap.merchantId, 1)
                            nextSubOrder[dstSnap.merchantId] = order + 1
                            dstSubCache[subKey] = newId to srcSubName
                            updateMap["subcategoryId"] = newId
                            updateMap["subcategoryName"] = srcSubName
                            val subRef = db.collection("merchants").document(dstSnap.merchantId).collection("subcategories").document(newId)
                            allOps.add { b ->
                                b.set(subRef, mapOf("name" to srcSubName, "sortOrder" to order, "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()))
                            }
                        }
                    } else {
                        updateMap["subcategoryId"] = null
                        updateMap["subcategoryName"] = null
                    }
                    val targetMenus = mutableListOf<String>()
                    for (mId in srcItem.getEffectiveMenuIds()) {
                        if (mId == "main") targetMenus.add("main")
                        else {
                            val srcMenu = srcSnap.menus.find { it.id == mId }
                            val mName = srcMenu?.name?.trim().orEmpty()
                            if (mName.isNotBlank()) {
                                val mKey = OutletsUtils.menuMatchingKey(mName)
                                val exMenu = dstMenuCache[mKey]
                                if (exMenu != null) targetMenus.add(exMenu.id)
                                else {
                                    val newMId = UUID.randomUUID().toString()
                                    val mOrd = nextMenuOrder.getOrDefault(dstSnap.merchantId, 1)
                                    nextMenuOrder[dstSnap.merchantId] = mOrd + 1
                                    val newMenu = ShopMenu(id = newMId, merchantId = dstSnap.merchantId, name = mName, active = srcMenu?.active ?: true, sortOrder = mOrd, schedule = srcMenu?.schedule)
                                    dstMenuCache[mKey] = newMenu
                                    targetMenus.add(newMId)
                                    val menuRef = db.collection("merchants").document(dstSnap.merchantId).collection("menus").document(newMId)
                                    allOps.add { b ->
                                        b.set(menuRef, mapOf("name" to mName, "active" to newMenu.active, "sortOrder" to mOrd, "schedule" to newMenu.schedule?.let { mapOf("days" to it.days, "start" to it.start, "end" to it.end) }, "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()))
                                    }
                                }
                            }
                        }
                    }
                    updateMap["menuIds"] = targetMenus.distinct().ifEmpty { listOf("main") }
                }
                if (SyncField.OPTIONS in fieldsToSync) {
                    val setsList = srcItem.sets.mapIndexed { idx, s ->
                        mapOf(
                            "id" to UUID.randomUUID().toString(),
                            "name" to s.name.trim(),
                            "customName" to s.name.trim(),
                            "subcategoryId" to null,
                            "subcategoryName" to null,
                            "required" to s.required,
                            "minSelect" to if (s.required) s.minSelect.coerceIn(1, s.maxSelect) else 0,
                            "maxSelect" to s.maxSelect,
                            "sortOrder" to idx,
                            "choices" to s.choices.map { c ->
                                mapOf("id" to UUID.randomUUID().toString(), "name" to c.name.trim(), "price" to c.price, "available" to c.available, "popular" to c.popular)
                            }
                        )
                    }
                    updateMap["hasSets"] = srcItem.sets.isNotEmpty()
                    updateMap["sets"] = setsList
                }
                if (SyncField.AVAILABILITY in fieldsToSync) {
                    updateMap["availability"] = srcItem.availability?.let {
                        mapOf("days" to it.days, "start" to it.start, "end" to it.end, "dateFrom" to it.dateFrom, "dateTo" to it.dateTo)
                    }
                }
                if (SyncField.PREP_TAGS in fieldsToSync) {
                    updateMap["prepTime"] = srcItem.prepTimeMinutes
                    updateMap["tags"] = srcItem.tags
                }

                val dstRef = db.collection("merchants").document(dstSnap.merchantId).collection("items").document(dstItem.id)
                allOps.add { b -> b.update(dstRef, updateMap) }
                updatedCount++
            }

            val chunks = MenuSyncEngine.chunkForFirestoreBatches(allOps)
            val totalBatches = chunks.size.coerceAtLeast(1)
            var failed = 0
            withContext(Dispatchers.Main) { onProgress(0, totalBatches) }

            for ((idx, chunk) in chunks.withIndex()) {
                try {
                    val batch = db.batch()
                    chunk.forEach { it(batch) }
                    batch.commit().awaitTask()
                } catch (ex: Exception) {
                    failed++
                    details.add("Batch ${idx + 1} failed: ${ex.localizedMessage ?: "Write error"}")
                }
                withContext(Dispatchers.Main) { onProgress(idx + 1, totalBatches) }
            }

            val addedTotal = plan.addHereItems.size + plan.addThereItems.size
            details.add(0, "Synced ${mine.displayName} <-> ${theirs.displayName}")
            withContext(Dispatchers.Main) {
                onComplete(
                    MenuSyncResult(
                        operationName = "Sync Menu",
                        added = addedTotal,
                        updated = updatedCount,
                        skipped = plan.skippedInSyncCount,
                        failed = failed,
                        details = details
                    )
                )
            }
        }
    }

    companion object {
        @Volatile
        private var instance: MerchantRepository? = null

        fun getInstance(context: Context): MerchantRepository {
            return instance ?: synchronized(this) {
                instance ?: MerchantRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
