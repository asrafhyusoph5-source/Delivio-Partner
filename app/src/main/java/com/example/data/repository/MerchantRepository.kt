package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.model.*
import com.example.util.PlatformOrderStatuses
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class MerchantRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("delivio_partner_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val auth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.e("MerchantRepo", "FirebaseAuth not available", e)
            null
        }
    }

    private val firestore: FirebaseFirestore? by lazy {
        try {
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
            storeName = prefs.getString("store_name", "Delivio Partner") ?: "Delivio Partner",
            themeAccentHex = prefs.getString("theme_accent", "#FFC107") ?: "#FFC107",
            isDarkMode = prefs.getBoolean("is_dark_mode", false),
            storeLocation = AddressLocation(
                street = prefs.getString("loc_street", "88 Rizal Avenue") ?: "88 Rizal Avenue",
                city = prefs.getString("loc_city", "Taguig, Metro Manila") ?: "Taguig, Metro Manila",
                latitude = prefs.getFloat("loc_lat", 14.5492f).toDouble(),
                longitude = prefs.getFloat("loc_lng", 121.0503f).toDouble()
            )
        )
    )
    val settings: StateFlow<MerchantSettings> = _settings.asStateFlow()

    // Orders
    private val _orders = MutableStateFlow<List<Order>>(emptyList())
    val orders: StateFlow<List<Order>> = _orders.asStateFlow()

    // New Incoming Order Alert (Heads-up / Pop-up)
    private val _newOrderAlert = MutableStateFlow<Order?>(null)
    val newOrderAlert: StateFlow<Order?> = _newOrderAlert.asStateFlow()

    // Menu Categories & Items
    private val _categories = MutableStateFlow<List<MenuCategory>>(emptyList())
    val categories: StateFlow<List<MenuCategory>> = _categories.asStateFlow()

    private val _menuItems = MutableStateFlow<List<MenuItem>>(emptyList())
    val menuItems: StateFlow<List<MenuItem>> = _menuItems.asStateFlow()

    // Payouts & Promos & Staff
    private val _payouts = MutableStateFlow<List<Payout>>(emptyList())
    val payouts: StateFlow<List<Payout>> = _payouts.asStateFlow()

    private val _promos = MutableStateFlow<List<PromoDeal>>(emptyList())
    val promos: StateFlow<List<PromoDeal>> = _promos.asStateFlow()

    private val _staff = MutableStateFlow<List<StaffMember>>(emptyList())
    val staff: StateFlow<List<StaffMember>> = _staff.asStateFlow()

    // Active Firestore snapshot listeners
    private var ordersListener: ListenerRegistration? = null
    private var settingsListener: ListenerRegistration? = null
    private var menuListener: ListenerRegistration? = null
    private var categoryListener: ListenerRegistration? = null
    private var payoutListener: ListenerRegistration? = null
    private var staffListener: ListenerRegistration? = null

    private var previousOrderIds = mutableSetOf<String>()

    init {
        // Check if there is already an active Firebase Auth user
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

        // Read users/{uid}
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

                    // Regular login
                    if (role == "merchant") {
                        val mId = uid
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
                        // Other role (customer, rider, etc.) -> check merchantApplications
                        checkApplication(uid, email, onComplete)
                    }
                } else {
                    // No users doc found -> check merchantApplications/{uid}
                    checkApplication(uid, email, onComplete)
                }
            }
            .addOnFailureListener { ex ->
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
                    val reason = appDoc.getString("rejectionReason")
                    val app = MerchantApplication(
                        id = uid,
                        ownerUid = appDoc.getString("ownerUid") ?: uid,
                        businessName = appDoc.getString("businessName") ?: "",
                        ownerName = appDoc.getString("ownerName") ?: "",
                        phone = appDoc.getString("phone") ?: "",
                        email = appDoc.getString("email") ?: email,
                        businessType = appDoc.getString("businessType") ?: "food",
                        address = appDoc.getString("address") ?: "",
                        lat = appDoc.getDouble("lat") ?: 14.5492,
                        lng = appDoc.getDouble("lng") ?: 121.0503,
                        cityId = appDoc.getString("cityId") ?: "",
                        logoUrl = appDoc.getString("logoUrl") ?: "",
                        storePhotoUrl = appDoc.getString("storePhotoUrl") ?: "",
                        permitUrl = appDoc.getString("documents.permitUrl") ?: "",
                        dtiSecUrl = appDoc.getString("documents.dtiSecUrl") ?: "",
                        barangayUrl = appDoc.getString("documents.barangayUrl") ?: "",
                        validIdUrl = appDoc.getString("documents.validIdUrl") ?: "",
                        status = status,
                        rejectionReason = reason,
                        adminNotes = appDoc.getString("adminNotes")
                    )
                    _merchantApplication.value = app
                    onComplete(true, null)
                } else {
                    auth?.signOut()
                    onComplete(false, "Not a merchant account")
                }
            }
            .addOnFailureListener {
                auth?.signOut()
                onComplete(false, "Not a merchant account")
            }
    }

    fun sendPasswordReset(email: String, onResult: (Boolean, String) -> Unit) {
        val fbAuth = auth
        if (fbAuth == null) {
            onResult(false, "Auth service unavailable")
            return
        }
        fbAuth.sendPasswordResetEmail(email.trim())
            .addOnSuccessListener {
                onResult(true, "Password reset email sent to $email. Please check your inbox.")
            }
            .addOnFailureListener { ex ->
                onResult(false, ex.localizedMessage ?: "Failed to send reset email.")
            }
    }

    fun registerMerchant(
        application: MerchantApplication,
        pass: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val fbAuth = auth
        val db = firestore
        if (fbAuth == null || db == null) {
            onResult(false, "Backend service unavailable")
            return
        }

        _isAuthLoading.value = true
        fbAuth.createUserWithEmailAndPassword(application.email.trim(), pass.trim())
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
                    val bTypeStr = snapshot.getString("businessType")
                    val bType = BusinessType.fromString(bTypeStr)
                    val locMap = snapshot.get("storeLocation") as? Map<*, *>
                    val loc = if (locMap != null) {
                        AddressLocation(
                            street = (locMap["street"] as? String) ?: "",
                            city = (locMap["city"] as? String) ?: "",
                            latitude = (locMap["latitude"] as? Number)?.toDouble() ?: 14.5492,
                            longitude = (locMap["longitude"] as? Number)?.toDouble() ?: 121.0503,
                            notes = (locMap["notes"] as? String) ?: ""
                        )
                    } else _settings.value.storeLocation

                    _settings.value = _settings.value.copy(
                        storeName = snapshot.getString("storeName") ?: snapshot.getString("businessName") ?: _settings.value.storeName,
                        ownerName = snapshot.getString("ownerName") ?: _settings.value.ownerName,
                        email = snapshot.getString("email") ?: _settings.value.email,
                        phone = snapshot.getString("phone") ?: _settings.value.phone,
                        businessType = bType,
                        isOpen = snapshot.getBoolean("isOpen") ?: true,
                        isPaused = snapshot.getBoolean("isPaused") ?: false,
                        pauseUntilTimestamp = snapshot.getLong("pauseUntilTimestamp"),
                        autoScheduleEnabled = snapshot.getBoolean("autoScheduleEnabled") ?: true,
                        storeLocation = loc,
                        minimumOrder = snapshot.getDouble("minimumOrder") ?: 0.0,
                        defaultPrepTime = snapshot.getLong("defaultPrepTime")?.toInt() ?: 15,
                        soundEnabled = snapshot.getBoolean("soundEnabled") ?: true,
                        vibrationEnabled = snapshot.getBoolean("vibrationEnabled") ?: true,
                        themeAccentHex = snapshot.getString("themeAccentHex") ?: _settings.value.themeAccentHex,
                        isDarkMode = snapshot.getBoolean("isDarkMode") ?: _settings.value.isDarkMode,
                        bankName = snapshot.getString("bankName") ?: "",
                        accountHolder = snapshot.getString("accountHolder") ?: "",
                        accountNumber = snapshot.getString("accountNumber") ?: ""
                    )
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

                        val itemsRaw = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                        val items = itemsRaw.mapIndexed { idx, itm ->
                            val modsRaw = itm["modifiers"] as? List<Map<String, Any>> ?: emptyList()
                            val mods = modsRaw.map { m ->
                                OrderModifier(
                                    name = m["name"] as? String ?: "",
                                    price = (m["price"] as? Number)?.toDouble() ?: 0.0
                                )
                            }
                            OrderItem(
                                id = (itm["id"] as? String) ?: "item_$idx",
                                menuItemId = (itm["menuItemId"] as? String) ?: "",
                                name = (itm["name"] as? String) ?: "Item",
                                quantity = (itm["quantity"] as? Number)?.toInt() ?: 1,
                                unitPrice = (itm["unitPrice"] as? Number)?.toDouble() ?: 0.0,
                                selectedSize = itm["selectedSize"] as? String,
                                modifiers = mods,
                                notes = itm["notes"] as? String
                            )
                        }

                        val addrMap = doc.get("deliveryAddress") as? Map<*, *>
                        val addr = AddressLocation(
                            street = (addrMap?.get("street") as? String) ?: "",
                            city = (addrMap?.get("city") as? String) ?: "",
                            latitude = (addrMap?.get("latitude") as? Number)?.toDouble() ?: 14.5492,
                            longitude = (addrMap?.get("longitude") as? Number)?.toDouble() ?: 121.0503,
                            notes = (addrMap?.get("notes") as? String) ?: ""
                        )

                        val riderMap = doc.get("rider") as? Map<*, *>
                        val rider = if (riderMap != null) {
                            Rider(
                                id = (riderMap["id"] as? String) ?: "",
                                name = (riderMap["name"] as? String) ?: "Rider",
                                phone = (riderMap["phone"] as? String) ?: "",
                                vehiclePlate = (riderMap["vehiclePlate"] as? String) ?: "",
                                vehicleType = (riderMap["vehicleType"] as? String) ?: "Motorcycle"
                            )
                        } else null

                        val order = Order(
                            id = doc.id,
                            merchantId = mId,
                            orderNumber = doc.getString("orderNumber") ?: ("#" + doc.id.takeLast(4).uppercase()),
                            customerName = doc.getString("customerName") ?: "Customer",
                            customerPhone = doc.getString("customerPhone") ?: "",
                            deliveryAddress = addr,
                            items = items,
                            subtotal = doc.getDouble("subtotal") ?: items.sumOf { it.totalPrice },
                            deliveryFee = doc.getDouble("deliveryFee") ?: 0.0,
                            platformFee = doc.getDouble("platformFee") ?: 0.0,
                            total = doc.getDouble("total") ?: items.sumOf { it.totalPrice },
                            paymentMethod = doc.getString("paymentMethod") ?: "Cash on Delivery",
                            status = orderStatus,
                            placedAtTimestamp = doc.getLong("placedAtTimestamp") ?: (doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()),
                            prepTimeMinutes = doc.getLong("prepTimeMinutes")?.toInt(),
                            prepEndTime = doc.getLong("prepEndTime"),
                            rejectionReason = doc.getString("rejectionReason"),
                            rider = rider
                        )
                        list.add(order)
                    }

                    // Sort newest first
                    list.sortByDescending { it.placedAtTimestamp }

                    // Check for newly arrived "Placed Order"
                    val newPlaced = list.filter { it.status == OrderStatus.PLACED_ORDER && !previousOrderIds.contains(it.id) }
                    if (newPlaced.isNotEmpty()) {
                        _newOrderAlert.value = newPlaced.first()
                    }
                    previousOrderIds = list.map { it.id }.toMutableSet()
                    _orders.value = list
                }
            }

        // 3. Menu Items listener: merchants/{merchantId}/menuItems
        menuListener = db.collection("merchants").document(mId).collection("menuItems")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("MerchantRepo", "Menu listener error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val items = snapshot.documents.map { doc ->
                        MenuItem(
                            id = doc.id,
                            merchantId = mId,
                            categoryId = doc.getString("categoryId") ?: "",
                            name = doc.getString("name") ?: "",
                            description = doc.getString("description") ?: "",
                            price = doc.getDouble("price") ?: 0.0,
                            inStock = doc.getBoolean("inStock") ?: true,
                            soldOutReason = doc.getString("soldOutReason"),
                            businessType = BusinessType.fromString(doc.getString("businessType")),
                            iconEmoji = doc.getString("iconEmoji") ?: "🛍️",
                            imageUrl = doc.getString("imageUrl") ?: "",
                            prepTimeMinutes = doc.getLong("prepTimeMinutes")?.toInt() ?: 15,
                            sku = doc.getString("sku") ?: "",
                            unit = doc.getString("unit") ?: "1 pc",
                            stockCount = doc.getLong("stockCount")?.toInt() ?: 50,
                            lowStockAlertThreshold = doc.getLong("lowStockAlertThreshold")?.toInt() ?: 5
                        )
                    }
                    _menuItems.value = items
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
                            displayOrder = doc.getLong("displayOrder")?.toInt() ?: 0,
                            isVisible = doc.getBoolean("isVisible") ?: true,
                            businessType = BusinessType.fromString(doc.getString("businessType"))
                        )
                    }.sortedBy { it.displayOrder }
                    _categories.value = cats
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

        // 6. Staff listener: users where merchantId == mId
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
    }

    // --- Actions ---

    fun confirmAcceptOrder(orderId: String, prepMinutes: Int) {
        val db = firestore ?: return
        val prepEnd = System.currentTimeMillis() + (prepMinutes * 60 * 1000L)
        db.collection("orders").document(orderId).update(
            mapOf(
                "status" to PlatformOrderStatuses.PREPARING,
                "prepTimeMinutes" to prepMinutes,
                "prepEndTime" to prepEnd
            )
        )
        dismissNewOrderAlert()
    }

    fun confirmRejectOrder(orderId: String, reason: String) {
        val db = firestore ?: return
        db.collection("orders").document(orderId).update(
            mapOf(
                "status" to PlatformOrderStatuses.REJECTED,
                "rejectionReason" to reason
            )
        )
        dismissNewOrderAlert()
    }

    fun setOrderReady(orderId: String) {
        val db = firestore ?: return
        db.collection("orders").document(orderId).update(
            mapOf("status" to PlatformOrderStatuses.READY)
        )
    }

    fun requestRiderForOrder(
        orderId: String,
        pickup: AddressLocation,
        dropoff: AddressLocation,
        note: String,
        isSelfDelivery: Boolean
    ) {
        val db = firestore ?: return
        val mId = _merchantId.value ?: ""

        val requestData = hashMapOf(
            "orderId" to orderId,
            "merchantId" to mId,
            "pickup" to mapOf("street" to pickup.street, "city" to pickup.city, "latitude" to pickup.latitude, "longitude" to pickup.longitude),
            "dropoff" to mapOf("street" to dropoff.street, "city" to dropoff.city, "latitude" to dropoff.latitude, "longitude" to dropoff.longitude),
            "note" to note,
            "isSelfDelivery" to isSelfDelivery,
            "status" to "SEARCHING",
            "requestedAt" to System.currentTimeMillis()
        )

        db.collection("riderRequests").document(orderId).set(requestData)
        db.collection("orders").document(orderId).update(
            mapOf(
                "riderRequestStatus" to "SEARCHING",
                "isSelfDelivery" to isSelfDelivery
            )
        )
    }

    fun cancelRiderRequest(orderId: String) {
        val db = firestore ?: return
        db.collection("riderRequests").document(orderId).update(mapOf("status" to "CANCELLED"))
        db.collection("orders").document(orderId).update(mapOf("riderRequestStatus" to "CANCELLED"))
    }

    fun completeOrderManually(orderId: String) {
        val db = firestore ?: return
        db.collection("orders").document(orderId).update(
            mapOf("status" to PlatformOrderStatuses.DELIVERED)
        )
    }

    fun dismissNewOrderAlert() {
        _newOrderAlert.value = null
    }

    // --- Store Settings Updates ---

    fun toggleStoreOpen(isOpen: Boolean) {
        val mId = _merchantId.value
        val db = firestore
        if (mId != null && db != null) {
            db.collection("merchants").document(mId).update(
                mapOf(
                    "isOpen" to isOpen,
                    "isPaused" to false,
                    "pauseUntilTimestamp" to null
                )
            )
        }
        _settings.value = _settings.value.copy(isOpen = isOpen, isPaused = false, pauseUntilTimestamp = null)
        prefs.edit().putBoolean("is_open", isOpen).apply()
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
        toggleStoreOpen(true)
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

    fun saveStoreLocation(loc: AddressLocation) {
        _settings.value = _settings.value.copy(storeLocation = loc)
        prefs.edit()
            .putString("loc_street", loc.street)
            .putString("loc_city", loc.city)
            .putFloat("loc_lat", loc.latitude.toFloat())
            .putFloat("loc_lng", loc.longitude.toFloat())
            .apply()

        val mId = _merchantId.value ?: return
        firestore?.collection("merchants")?.document(mId)?.update(
            "storeLocation", mapOf(
                "street" to loc.street,
                "city" to loc.city,
                "latitude" to loc.latitude,
                "longitude" to loc.longitude,
                "notes" to loc.notes
            )
        )
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
                "storeName" to storeName,
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

    // --- Menu Management Actions ---

    fun saveMenuItem(item: MenuItem) {
        val mId = _merchantId.value ?: return
        val db = firestore ?: return
        val docId = item.id.ifBlank { UUID.randomUUID().toString() }
        val itemMap = hashMapOf(
            "categoryId" to item.categoryId,
            "name" to item.name,
            "description" to item.description,
            "price" to item.price,
            "inStock" to item.inStock,
            "soldOutReason" to (item.soldOutReason ?: ""),
            "businessType" to if (item.businessType == BusinessType.FOOD) "food" else "convenience",
            "iconEmoji" to item.iconEmoji,
            "imageUrl" to item.imageUrl,
            "prepTimeMinutes" to item.prepTimeMinutes,
            "sku" to item.sku,
            "unit" to item.unit,
            "stockCount" to item.stockCount,
            "lowStockAlertThreshold" to item.lowStockAlertThreshold
        )
        db.collection("merchants").document(mId).collection("menuItems").document(docId).set(itemMap)
    }

    fun deleteMenuItem(itemId: String) {
        val mId = _merchantId.value ?: return
        firestore?.collection("merchants")?.document(mId)?.collection("menuItems")?.document(itemId)?.delete()
    }

    fun toggleItemAvailability(itemId: String, inStock: Boolean) {
        val mId = _merchantId.value ?: return
        firestore?.collection("merchants")?.document(mId)?.collection("menuItems")?.document(itemId)?.update(
            "inStock", inStock
        )
    }

    fun addCategory(name: String, businessType: BusinessType) {
        val mId = _merchantId.value ?: return
        val db = firestore ?: return
        val doc = db.collection("merchants").document(mId).collection("categories").document()
        val maxOrder = _categories.value.maxOfOrNull { it.displayOrder } ?: 0
        doc.set(
            hashMapOf(
                "name" to name,
                "displayOrder" to (maxOrder + 1),
                "isVisible" to true,
                "businessType" to if (businessType == BusinessType.FOOD) "food" else "convenience"
            )
        )
    }

    fun deleteCategory(categoryId: String) {
        val mId = _merchantId.value ?: return
        firestore?.collection("merchants")?.document(mId)?.collection("categories")?.document(categoryId)?.delete()
    }

    fun toggleCategoryVisibility(categoryId: String) {
        val mId = _merchantId.value ?: return
        val current = _categories.value.find { it.id == categoryId } ?: return
        firestore?.collection("merchants")?.document(mId)?.collection("categories")?.document(categoryId)?.update(
            "isVisible", !current.isVisible
        )
    }

    fun reorderCategory(categoryId: String, moveUp: Boolean) {
        // Can be persisted by swapping displayOrder in Firestore
    }

    fun addStaff(name: String, role: String, email: String) {
        val mId = _merchantId.value ?: return
        val db = firestore ?: return
        val doc = db.collection("users").document()
        doc.set(
            hashMapOf(
                "name" to name,
                "role" to "staff",
                "staffRole" to role,
                "email" to email,
                "merchantId" to mId,
                "active" to true,
                "createdAt" to System.currentTimeMillis()
            )
        )
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
