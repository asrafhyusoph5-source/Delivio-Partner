package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.model.*
import com.example.util.FormatUtils
import com.example.util.PlatformOrderStatuses
import com.example.util.FOOD_CATEGORIES
import com.example.util.CONVENIENCE_CATEGORIES
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _menuItems = MutableStateFlow<List<MenuItem>>(emptyList())
    val menuItems: StateFlow<List<MenuItem>> = _menuItems.asStateFlow()

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
    private var payoutListener: ListenerRegistration? = null
    private var staffListener: ListenerRegistration? = null
    private var changeRequestsListener: ListenerRegistration? = null
    private var walletMerchantListener: ListenerRegistration? = null
    private var walletCustomerListener: ListenerRegistration? = null
    private var linkedUserListener: ListenerRegistration? = null

    private var rawMerchantTxList = listOf<WalletTransaction>()
    private var rawCustomerTxList = listOf<WalletTransaction>()
    private var listenedLinkedCustomerId: String? = null

    private var previousOrderIds = mutableSetOf<String>()

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
                        _currentUser.value = user
                        _merchantId.value = mId
                        attachFirestoreListeners(mId)
                        onComplete(true, null)
                        return@addOnSuccessListener
                    }

                    if (role == "merchant" || role == "owner" || role == "partner") {
                        val mId = userDoc.getString("merchantId")?.takeIf { it.isNotBlank() } ?: uid
                        val user = User(
                            id = uid,
                            name = userDoc.getString("name") ?: userDoc.getString("ownerName") ?: "Merchant",
                            email = email,
                            role = "merchant",
                            merchantId = mId
                        )
                        _currentUser.value = user
                        _merchantId.value = mId
                        attachFirestoreListeners(mId)
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
        detachFirestoreListeners()
        auth?.signOut()
        _currentUser.value = null
        _merchantId.value = null
        _merchantApplication.value = null
        _orders.value = emptyList()
        _categories.value = emptyList()
        _menuItems.value = emptyList()
        _payouts.value = emptyList()
        _staff.value = emptyList()
        _changeRequests.value = emptyList()
    }

    // --- Firestore Snapshot Listeners ---

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
                    val nameFromDoc = snapshot.getString("name") ?: snapshot.getString("storeName") ?: snapshot.getString("businessName") ?: _settings.value.storeName
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
                        _settings.value.hours
                    }

                    val closedUntilVal: Long? = when (val cu = snapshot.get("closedUntil")) {
                        is Timestamp -> cu.toDate().time
                        is java.util.Date -> cu.time
                        is Number -> cu.toLong()
                        else -> null
                    }
                    val openUntilVal: Long? = when (val ou = snapshot.get("openUntil")) {
                        is Timestamp -> ou.toDate().time
                        is java.util.Date -> ou.time
                        is Number -> ou.toLong()
                        else -> null
                    }
                    val closedAtVal: Long? = when (val ca = snapshot.get("closedAt")) {
                        is Timestamp -> ca.toDate().time
                        is java.util.Date -> ca.time
                        is Number -> ca.toLong()
                        else -> null
                    }
                    val pauseUntilVal: Long? = when (val pu = snapshot.get("pauseUntilTime") ?: snapshot.get("pauseUntilTimestamp")) {
                        is Timestamp -> pu.toDate().time
                        is java.util.Date -> pu.time
                        is Number -> pu.toLong()
                        else -> null
                    }

                    _settings.value = _settings.value.copy(
                        storeName = nameFromDoc,
                        ownerName = snapshot.getString("ownerName") ?: _settings.value.ownerName,
                        email = snapshot.getString("email") ?: _settings.value.email,
                        phone = snapshot.getString("phone") ?: _settings.value.phone,
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
                        themeAccentHex = snapshot.getString("themeAccentHex") ?: _settings.value.themeAccentHex,
                        isDarkMode = snapshot.getBoolean("isDarkMode") ?: _settings.value.isDarkMode,
                        logoUrl = snapshot.getString("logoUrl")?.trim() ?: "",
                        bannerUrl = snapshot.getString("bannerUrl")?.trim() ?: "",
                        imageUrl = snapshot.getString("imageUrl")?.trim() ?: "",
                        bankName = snapshot.getString("bankName") ?: "",
                        accountHolder = snapshot.getString("accountHolder") ?: "",
                        accountNumber = snapshot.getString("accountNumber") ?: "",
                        commissionRate = commRate,
                        cityId = snapshot.getString("cityId") ?: snapshot.getString("city") ?: _settings.value.cityId.ifBlank { "taguig" },
                        linkedCustomerUid = linkedCust,
                        walletBalance = wBalance,
                        status = shopStatus,
                        timezone = shopTz,
                        closedUntil = closedUntilVal,
                        openUntil = openUntilVal,
                        closedAt = closedAtVal
                    )

                    syncWalletListeners(mId, linkedCust)
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
                    val list = mutableListOf<Order>()
                    for (doc in snapshot.documents) {
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

                            // 1. READ PRICES CORRECTLY
                            // Item price: items[i].price, falling back to unitPrice, then itemPrice, then 0.
                            // Quantity: items[i].qty, falling back to quantity, then 1.
                            // Accept numbers or numeric strings, and never crash on a missing value.
                            val priceVal = FormatUtils.parseDouble(itm["price"])
                                ?: FormatUtils.parseDouble(itm["unitPrice"])
                                ?: FormatUtils.parseDouble(itm["itemPrice"])
                                ?: 0.0

                            val qtyVal = FormatUtils.parseInt(itm["qty"])
                                ?: FormatUtils.parseInt(itm["quantity"])
                                ?: 1

                            val finalQty = if (qtyVal > 0) qtyVal else 1

                            OrderItem(
                                id = (itm["id"] as? String) ?: "item_$idx",
                                menuItemId = (itm["menuItemId"] as? String) ?: "",
                                name = (itm["name"] as? String) ?: (itm["title"] as? String) ?: "Item",
                                quantity = finalQty,
                                price = priceVal,
                                unitPrice = priceVal,
                                selectedSize = itm["selectedSize"] as? String,
                                modifiers = mods,
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

                        // Rider details
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

                        // Rider request sub-map
                        val reqMap = doc.get("riderRequest") as? Map<*, *>
                        val reqStatusStr = (reqMap?.get("status") as? String) ?: doc.getString("riderRequestStatus")
                        val reqTimestamp = when (val t = reqMap?.get("requestedAt")) {
                            is Timestamp -> t.toDate().time
                            is Number -> t.toLong()
                            else -> null
                        }

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

                        // Items subtotal: fees.subtotal, or the sum of line totals
                        val rawSubtotal = FormatUtils.parseDouble(feesMap?.get("subtotal"))
                            ?: FormatUtils.parseDouble(doc.get("subtotal"))
                            ?: lineTotalsSum
                        val finalSubtotal = if (rawSubtotal <= 0.0 && lineTotalsSum > 0.0) lineTotalsSum else rawSubtotal

                        // Delivery fee: fees.deliveryFee
                        val deliveryFeeVal = FormatUtils.parseDouble(feesMap?.get("deliveryFee"))
                            ?: FormatUtils.parseDouble(doc.get("deliveryFee"))
                            ?: 0.0

                        // Service fee: fees.serviceFee, if any
                        val serviceFeeVal = FormatUtils.parseDouble(feesMap?.get("serviceFee"))
                            ?: FormatUtils.parseDouble(doc.get("serviceFee"))
                            ?: 0.0

                        // Discount: if any
                        val discountVal = FormatUtils.parseDouble(feesMap?.get("discount"))
                            ?: FormatUtils.parseDouble(doc.get("discount"))
                            ?: 0.0

                        val platformFeeVal = FormatUtils.parseDouble(feesMap?.get("platformFee"))
                            ?: FormatUtils.parseDouble(doc.get("platformFee"))
                            ?: 0.0

                        // Order total: fees.total, falling back to total, then totalAmount, then subtotal + deliveryFee + serviceFee - discount, then the sum of line totals.
                        val calculatedTotal = (finalSubtotal + deliveryFeeVal + serviceFeeVal - discountVal).coerceAtLeast(0.0)

                        val parsedFeesTotal = FormatUtils.parseDouble(feesMap?.get("total"))
                        val parsedDocTotal = FormatUtils.parseDouble(doc.get("total"))
                        val parsedTotalAmount = FormatUtils.parseDouble(doc.get("totalAmount"))

                        var finalTotal = parsedFeesTotal
                            ?: parsedDocTotal
                            ?: parsedTotalAmount
                            ?: if (calculatedTotal > 0.0) calculatedTotal else lineTotalsSum

                        // Self-check guard: no value shows $0.00 when the order has prices
                        if (finalTotal <= 0.0) {
                            if (calculatedTotal > 0.0) {
                                finalTotal = calculatedTotal
                            } else if (lineTotalsSum > 0.0) {
                                finalTotal = lineTotalsSum
                            }
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

                        val rawFulfillment = (doc.getString("fulfillment")
                            ?: (doc.get("fulfillment") as? String)
                            ?: "").trim()
                        val hasPickup = doc.getBoolean("hasPickup") ?: false

                        val scheduledForVal: Long? = when (val sf = doc.get("scheduledFor")) {
                            is Timestamp -> sf.toDate().time
                            is java.util.Date -> sf.time
                            is Number -> sf.toLong()
                            is String -> sf.toLongOrNull()
                            else -> null
                        }

                        val editedAtVal: Long? = when (val t = doc.get("editedAt")) {
                            is Timestamp -> t.toDate().time
                            is Number -> t.toLong()
                            else -> null
                        }
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

                        val order = Order(
                            id = doc.id,
                            merchantId = mId,
                            orderNumber = doc.getString("orderNumber") ?: ("#" + doc.id.takeLast(4).uppercase()),
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
                            feesRaw = feesRawMap,
                            rawJson = rawJsonStr
                        )
                        list.add(order)
                    }

                    // Sort newest first
                    list.sortByDescending { it.placedAtTimestamp }

                    // Alert on incoming Placed Order (scheduled orders only alert once due; closed store suppresses alerts for orders after close)
                    val isShopEffectiveOpen = com.example.util.BusinessHoursHelper.isEffectiveOpen(_settings.value)
                    val closedAtTimestamp = _settings.value.closedAt
                    val newPlaced = list.filter {
                        it.status == OrderStatus.PLACED_ORDER &&
                        !previousOrderIds.contains(it.id) &&
                        (!it.isScheduled || it.isScheduledDue()) &&
                        (isShopEffectiveOpen || closedAtTimestamp == null || it.placedAtTimestamp <= closedAtTimestamp)
                    }
                    if (newPlaced.isNotEmpty()) {
                        _newOrderAlert.value = newPlaced.first()
                    }
                    previousOrderIds = list.map { it.id }.toMutableSet()
                    _orders.value = list
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
                    val items = snapshot.documents.map { doc ->
                        val isAvail = doc.getBoolean("available") ?: doc.getBoolean("inStock") ?: true
                        MenuItem(
                            id = doc.id,
                            merchantId = mId,
                            categoryId = doc.getString("categoryId") ?: "",
                            categoryName = doc.getString("categoryName") ?: "",
                            name = doc.getString("name") ?: "",
                            description = doc.getString("description") ?: "",
                            price = doc.getDouble("price") ?: 0.0,
                            inStock = isAvail,
                            soldOutReason = doc.getString("soldOutReason"),
                            businessType = BusinessType.fromString(doc.getString("businessType")),
                            iconEmoji = doc.getString("iconEmoji") ?: "🛍️",
                            imageUrl = doc.getString("imageUrl") ?: "",
                            prepTimeMinutes = doc.getLong("prepTime")?.toInt() ?: doc.getLong("prepTimeMinutes")?.toInt() ?: 15,
                            sku = doc.getString("sku") ?: "",
                            unit = doc.getString("unit") ?: "1 pc",
                            stockCount = doc.getLong("stockCount")?.toInt() ?: 50,
                            lowStockAlertThreshold = doc.getLong("lowStockAlertThreshold")?.toInt() ?: 5
                        )
                    }
                    _menuItems.value = items
                    syncCuisineTags(mId, items)
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
                        OrderItem(
                            id = (itm["id"] as? String) ?: "item_$idx",
                            menuItemId = (itm["menuItemId"] as? String) ?: "",
                            name = (itm["name"] as? String) ?: (itm["title"] as? String) ?: "Item",
                            quantity = if (qtyVal > 0) qtyVal else 1,
                            price = priceVal,
                            unitPrice = priceVal,
                            selectedSize = itm["selectedSize"] as? String,
                            modifiers = mods,
                            notes = (itm["notes"] as? String) ?: (itm["note"] as? String)
                        )
                    }

                    val existing = _orders.value.find { it.id == orderId }
                    val updated = (existing ?: Order(
                        id = doc.id,
                        merchantId = doc.getString("merchantId") ?: _merchantId.value ?: "",
                        orderNumber = doc.getString("orderNumber") ?: ("#" + doc.id.takeLast(4).uppercase()),
                        status = orderStatus
                    )).copy(
                        items = freshItems,
                        status = orderStatus
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
        if (isOpen) {
            openStore()
        } else {
            closeStore()
        }
    }

    fun closeStore(onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
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

    fun checkAndUpdateEffectiveOpen(nowMs: Long = System.currentTimeMillis()) {
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

        val data = mapOf(
            "address" to loc.formatted,
            "lat" to loc.latitude,
            "lng" to loc.longitude,
            "storeLocation" to mapOf(
                "street" to loc.street,
                "city" to loc.city,
                "latitude" to loc.latitude,
                "longitude" to loc.longitude,
                "notes" to loc.notes
            )
        )

        db.collection("merchants").document(mId).update(data)
            .addOnSuccessListener {
                _settings.value = _settings.value.copy(storeLocation = loc)
                prefs.edit()
                    .putString("loc_street", loc.street)
                    .putString("loc_city", loc.city)
                    .putFloat("loc_lat", loc.latitude.toFloat())
                    .putFloat("loc_lng", loc.longitude.toFloat())
                    .apply()
                onResult(true, null)
            }
            .addOnFailureListener { ex ->
                val err = ex.localizedMessage ?: ex.message ?: "Failed to save location"
                Log.e("MerchantRepo", "Save location failed: $err", ex)
                onResult(false, err)
            }
    }

    fun updateMerchantProfile(
        ownerName: String,
        phone: String,
        logoUrl: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val mId = _merchantId.value ?: return onResult(false, "No active merchant session")
        val db = firestore ?: return onResult(false, "Firestore unavailable")

        val data = mapOf(
            "ownerName" to ownerName.trim(),
            "phone" to phone.trim(),
            "logoUrl" to logoUrl.trim()
        )

        db.collection("merchants").document(mId).update(data)
            .addOnSuccessListener {
                _settings.value = _settings.value.copy(
                    ownerName = ownerName.trim(),
                    phone = phone.trim(),
                    logoUrl = logoUrl.trim()
                )
                onResult(true, null)
            }
            .addOnFailureListener { ex ->
                val err = ex.localizedMessage ?: ex.message ?: "Failed to update profile"
                Log.e("MerchantRepo", "Update profile failed: $err", ex)
                onResult(false, err)
            }
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

    fun saveMenuItem(item: MenuItem) {
        val mId = _merchantId.value ?: return
        val db = firestore ?: return
        val docId = item.id.ifBlank { UUID.randomUUID().toString() }
        val categoryName = item.categoryName.ifBlank {
            _categories.value.find { it.id == item.categoryId }?.name ?: ""
        }

        val itemMap = hashMapOf<String, Any>(
            "name" to item.name,
            "description" to item.description,
            "price" to item.price,
            "imageUrl" to item.imageUrl,
            "categoryId" to item.categoryId,
            "categoryName" to categoryName,
            "available" to item.inStock,
            "inStock" to item.inStock,
            "soldOutReason" to (item.soldOutReason ?: ""),
            "businessType" to if (item.businessType == BusinessType.FOOD) "food" else "convenience",
            "prepTime" to item.prepTimeMinutes,
            "tags" to item.tags,
            "sku" to item.sku,
            "barcode" to item.barcode,
            "unit" to item.unit,
            "stockCount" to item.stockCount,
            "lowStockAlertThreshold" to item.lowStockAlertThreshold
        )
        db.collection("merchants").document(mId).collection("items").document(docId).set(itemMap)
            .addOnSuccessListener {
                syncCuisineTags(mId)
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
