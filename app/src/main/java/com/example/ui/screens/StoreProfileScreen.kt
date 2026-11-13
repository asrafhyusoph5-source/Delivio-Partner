package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.SubcomposeAsyncImage
import com.example.data.model.MerchantSettings
import com.example.ui.components.MerchantLogoView
import com.example.ui.theme.DelivioYellow
import com.example.ui.theme.LocalDelivioColors
import com.example.ui.theme.parseHexColor
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000L

/**
 * Redesigned Store Profile Details:
 * 1. Store identity (large round logo with edit badge, Logo URL, live preview using real loader callbacks, store name locked, description with 0/200 counter)
 * 2. Contact (owner name, phone with Philippine mobile number format hint, login email locked)
 * 3. Location (map preview, address, "Change location" with date it becomes available again if within 30 days)
 * 4. Brand and theme (accent color, dark mode switch styled uniformly)
 *
 * Banner URL is completely removed.
 * Validation only occurs after interaction.
 * Sticky save bar sits above navigation bar (navigationBarsPadding plus 16dp).
 * Leaving with unsaved changes prompts "Discard changes?".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreProfileScreen(
    settings: MerchantSettings,
    onSave: (Map<String, Any>, onResult: (Boolean, String?) -> Unit) -> Unit,
    onBack: () -> Unit,
    onOpenColorPicker: () -> Unit = {},
    onToggleDarkMode: (Boolean) -> Unit = {},
    onOpenMapLocation: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val customColors = LocalDelivioColors.current
    val density = LocalDensity.current
    val isDark = isSystemInDarkTheme() || settings.isDarkMode

    var ownerNameInput by remember(settings.ownerName) { mutableStateOf(settings.ownerName) }
    var phoneInput by remember(settings.phone) { mutableStateOf(settings.phone) }
    var logoUrlInput by remember(settings.logoUrl) { mutableStateOf(settings.logoUrl) }
    var descriptionInput by remember(settings.description) { mutableStateOf(settings.description) }

    // Interaction tracking so errors only appear AFTER the user edits a field or attempts save
    var hasInteractedOwner by remember { mutableStateOf(false) }
    var hasInteractedPhone by remember { mutableStateOf(false) }
    var hasInteractedLogo by remember { mutableStateOf(false) }
    var hasAttemptedSave by remember { mutableStateOf(false) }

    var isSaving by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var failureErrorText by remember { mutableStateOf<String?>(null) }

    // Real Coil image loader callbacks state
    val trimmedLogo = logoUrlInput.trim()
    var isLogoLoading by remember { mutableStateOf(false) }
    var isLogoError by remember { mutableStateOf(false) }
    var isLogoSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(trimmedLogo) {
        if (trimmedLogo.isBlank()) {
            isLogoLoading = false
            isLogoError = false
            isLogoSuccess = false
        } else if (!trimmedLogo.startsWith("https://")) {
            isLogoLoading = false
            isLogoError = true
            isLogoSuccess = false
        } else {
            isLogoLoading = true
            isLogoError = false
            isLogoSuccess = false
        }
    }

    // Philippine Phone Validator
    fun isValidPhilippinePhone(raw: String): Boolean {
        val cleaned = raw.replace("[^0-9+]".toRegex(), "")
        if (cleaned.startsWith("+639") && cleaned.length == 13) return true
        if (cleaned.startsWith("639") && cleaned.length == 12) return true
        if (cleaned.startsWith("09") && cleaned.length == 11) return true
        if (cleaned.startsWith("9") && cleaned.length == 10) return true
        val digits = cleaned.removePrefix("+")
        return digits.length in 10..12 && (digits.startsWith("63") || digits.startsWith("09") || digits.startsWith("9"))
    }

    // Change Detection
    val isOwnerChanged = ownerNameInput.trim() != settings.ownerName.trim()
    val isPhoneChanged = phoneInput.trim() != settings.phone.trim()
    val isLogoChanged = trimmedLogo != settings.logoUrl.trim()
    val isDescriptionChanged = descriptionInput.trim() != settings.description.trim()

    val hasChanges = isOwnerChanged || isPhoneChanged || isLogoChanged || isDescriptionChanged
    val isOwnerValid = ownerNameInput.trim().isNotBlank()
    val isPhoneValid = isValidPhilippinePhone(phoneInput)
    val isLogoValid = trimmedLogo.isBlank() || trimmedLogo.startsWith("https://")

    val canSave = hasChanges && isOwnerValid && isPhoneValid && isLogoValid && !isSaving

    // 30-day location rule check
    val now = remember { System.currentTimeMillis() }
    val isLocationLocked = remember(settings.locationChangedAt, now) {
        if (settings.locationChangedAt == null) false
        else (now - settings.locationChangedAt) < THIRTY_DAYS_MS
    }
    val unlockDateString = remember(settings.locationChangedAt) {
        if (settings.locationChangedAt != null) {
            val unlockDate = Date(settings.locationChangedAt + THIRTY_DAYS_MS)
            SimpleDateFormat("MMMM d, yyyy", Locale.US).format(unlockDate)
        } else {
            ""
        }
    }

    fun handleBack() {
        if (hasChanges) {
            showDiscardDialog = true
        } else {
            onBack()
        }
    }

    BackHandler {
        handleBack()
    }

    fun executeSave() {
        hasAttemptedSave = true
        if (!canSave) return
        isSaving = true
        val changedFields = mutableMapOf<String, Any>()
        if (isOwnerChanged) changedFields["ownerName"] = ownerNameInput.trim()
        if (isPhoneChanged) changedFields["phone"] = phoneInput.trim()
        if (isLogoChanged) changedFields["logoUrl"] = trimmedLogo
        if (isDescriptionChanged) changedFields["description"] = descriptionInput.trim()

        onSave(changedFields) { success, err ->
            isSaving = false
            if (success) {
                showSuccessDialog = true
            } else {
                failureErrorText = err ?: "Failed to update profile"
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("store_profile_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Store Profile Details",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { handleBack() },
                        modifier = Modifier.testTag("store_profile_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp, bottom = 16.dp)
                ) {
                    if (hasAttemptedSave && !isOwnerValid) {
                        Text(
                            text = "Owner name is required",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    } else if (hasAttemptedSave && !isPhoneValid) {
                        Text(
                            text = "Valid Philippine phone number is required",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    } else if (hasAttemptedSave && !isLogoValid) {
                        Text(
                            text = "Logo URL must start with https://",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    Button(
                        onClick = { executeSave() },
                        enabled = canSave,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = customColors.accent,
                            contentColor = customColors.onAccent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("save_profile_changes_btn")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = customColors.onAccent,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Saving Changes...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Profile Changes", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==============================================================
            // 1. STORE IDENTITY CARD
            // ==============================================================
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth().testTag("store_profile_identity_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Store Identity",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Large Round Logo with Edit Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.size(72.dp)) {
                            MerchantLogoView(
                                storeName = settings.storeName,
                                logoUrl = trimmedLogo.ifBlank { settings.logoUrl },
                                imageUrl = settings.imageUrl,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .border(2.dp, customColors.accent, CircleShape),
                                shape = CircleShape,
                                letterSize = 28.sp
                            )
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(customColors.accent)
                                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Logo",
                                    tint = customColors.onAccent,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = settings.storeName.ifBlank { "Merchant Store" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Store logo shown to customers & riders",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Store Name (Locked)
                    OutlinedTextField(
                        value = settings.storeName,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("Store Name") },
                        leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
                        trailingIcon = { Icon(Icons.Default.Lock, contentDescription = "Locked") },
                        supportingText = { Text("Store name is locked per merchant agreement", fontSize = 11.sp) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("store_profile_shop_name_readonly")
                    )

                    if (settings.chainId.isNotBlank() || settings.branchName.isNotBlank() || settings.branchCode.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = settings.branchName.ifBlank { "Not set" },
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            label = { Text("Branch Name") },
                            leadingIcon = { Icon(Icons.Default.Store, contentDescription = null) },
                            trailingIcon = { Icon(Icons.Default.Lock, contentDescription = "Locked") },
                            supportingText = { Text("Only the admin can change these", fontSize = 11.sp) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("store_profile_branch_name_readonly")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = settings.branchCode.ifBlank { "Not set" },
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            label = { Text("Branch Code") },
                            leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null) },
                            trailingIcon = { Icon(Icons.Default.Lock, contentDescription = "Locked") },
                            supportingText = { Text("Only the admin can change these", fontSize = 11.sp) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("store_profile_branch_code_readonly")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Store Logo URL
                    OutlinedTextField(
                        value = logoUrlInput,
                        onValueChange = {
                            logoUrlInput = it
                            hasInteractedLogo = true
                        },
                        label = { Text("Store Logo URL (HTTPS)") },
                        placeholder = { Text("https://example.com/logo.png") },
                        leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                        isError = (hasInteractedLogo || hasAttemptedSave) && !isLogoValid,
                        supportingText = {
                            if ((hasInteractedLogo || hasAttemptedSave) && !isLogoValid) {
                                Text("URL must start with https://", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("store_profile_logo_url_input")
                    )

                    // Live Logo Preview Box (Using real image loader callbacks, no artificial timeout)
                    if (trimmedLogo.isNotBlank() && trimmedLogo.startsWith("https://")) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .padding(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, customColors.accentTint, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                SubcomposeAsyncImage(
                                    model = trimmedLogo,
                                    contentDescription = "Logo preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                    onLoading = {
                                        isLogoLoading = true
                                        isLogoError = false
                                        isLogoSuccess = false
                                    },
                                    onSuccess = {
                                        isLogoLoading = false
                                        isLogoError = false
                                        isLogoSuccess = true
                                    },
                                    onError = {
                                        isLogoLoading = false
                                        isLogoError = true
                                        isLogoSuccess = false
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = "Logo Preview",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                when {
                                    isLogoLoading -> {
                                        Text(
                                            text = "Loading preview...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    isLogoError -> {
                                        Text(
                                            text = "Could not load this image - check the link",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    isLogoSuccess -> {
                                        Text(
                                            text = "Image OK",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF10B981)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Store Description with 0/200 Counter
                    OutlinedTextField(
                        value = descriptionInput,
                        onValueChange = {
                            if (it.length <= 200) {
                                descriptionInput = it
                            }
                        },
                        label = { Text("Store Description") },
                        placeholder = { Text("Tell customers about your kitchen, specialties, and hours...") },
                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                        supportingText = {
                            Text(
                                text = "${descriptionInput.length}/200",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("store_profile_description_input")
                    )
                }
            }

            // ==============================================================
            // 2. CONTACT CARD
            // ==============================================================
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth().testTag("store_profile_contact_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Contact Details",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Owner Name
                    OutlinedTextField(
                        value = ownerNameInput,
                        onValueChange = {
                            ownerNameInput = it
                            hasInteractedOwner = true
                        },
                        label = { Text("Owner Name *") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        isError = (hasInteractedOwner || hasAttemptedSave) && !isOwnerValid,
                        supportingText = if ((hasInteractedOwner || hasAttemptedSave) && !isOwnerValid) {
                            { Text("Owner name is required", color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                        } else null,
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("store_profile_owner_name_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Phone Number with Philippine Mobile format hint
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = {
                            phoneInput = it
                            hasInteractedPhone = true
                        },
                        label = { Text("Phone Number *") },
                        placeholder = { Text("09171234567 or +639171234567") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        isError = (hasInteractedPhone || hasAttemptedSave) && !isPhoneValid,
                        supportingText = {
                            if ((hasInteractedPhone || hasAttemptedSave) && !isPhoneValid) {
                                Text("Enter a valid Philippine mobile number (e.g. 09171234567)", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            } else {
                                Text("Philippine mobile number", fontSize = 11.sp)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("store_profile_phone_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Login Email (Locked)
                    OutlinedTextField(
                        value = settings.email,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("Login Email") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        trailingIcon = { Icon(Icons.Default.Lock, contentDescription = "Locked") },
                        supportingText = { Text("Account login email is permanently fixed", fontSize = 11.sp) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("store_profile_email_readonly")
                    )
                }
            }

            // ==============================================================
            // 3. LOCATION CARD (Once per 30 days)
            // ==============================================================
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth().testTag("store_profile_location_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Store Location (Pickup Point)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Map Preview
                    val lat = if (settings.storeLocation.latitude != 0.0) settings.storeLocation.latitude else 14.5547
                    val lng = if (settings.storeLocation.longitude != 0.0) settings.storeLocation.longitude else 121.0509

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                MapView(ctx).apply {
                                    setTileSource(TileSourceFactory.MAPNIK)
                                    setMultiTouchControls(false)
                                    isTilesScaledToDpi = true
                                    controller.setZoom(16.0)
                                    val point = GeoPoint(lat, lng)
                                    controller.setCenter(point)

                                    val marker = Marker(this).apply {
                                        position = point
                                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                        title = settings.storeName.ifBlank { "Store Location" }
                                    }
                                    overlays.add(marker)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = customColors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = settings.storeLocation.street.ifBlank { "No address set" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "GPS: %.5f, %.5f".format(lat, lng),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Change Location Button & 30-day lock warning
                    if (isLocationLocked) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "You can change your location again on $unlockDateString",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = onOpenMapLocation,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("change_location_btn")
                        ) {
                            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Change location", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ==============================================================
            // 4. BRAND & THEME CARD
            // ==============================================================
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth().testTag("store_profile_brand_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Brand & Theme Appearance",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Theme Accent Color Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .clickable { onOpenColorPicker() }
                            .padding(14.dp)
                            .testTag("choose_merchant_theme_color_btn"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(parseHexColor(settings.themeAccentHex, DelivioYellow))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Merchant Accent Color", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(settings.themeAccentHex.uppercase(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text("Change", fontWeight = FontWeight.Bold, color = customColors.accent, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Dark Mode Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (settings.isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = null,
                                tint = customColors.accent
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Dark Mode", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("High-contrast #121212 merchant palette", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Switch(
                            checked = settings.isDarkMode,
                            onCheckedChange = { onToggleDarkMode(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF1A1A1A),
                                checkedTrackColor = DelivioYellow,
                                checkedBorderColor = Color.Transparent,
                                uncheckedThumbColor = Color(0xFF757575),
                                uncheckedTrackColor = if (isDark) Color(0xFF383838) else Color(0xFFE0E0E0),
                                uncheckedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier.testTag("dark_mode_switch")
                        )
                    }
                }
            }

            // Bottom scroll buffer so last item scrolls clear above sticky save bar
            val navBarBottomDp = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
            Spacer(modifier = Modifier.height(navBarBottomDp + 96.dp))
        }
    }

    // Discard Changes Dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?", fontWeight = FontWeight.Bold) },
            text = { Text("You have unsaved profile changes. Are you sure you want to discard them?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onBack()
                    }
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep Editing")
                }
            }
        )
    }

    // Success Dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                onBack()
            },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = customColors.accent) },
            title = { Text("Profile Updated", fontWeight = FontWeight.Bold) },
            text = { Text("Your store profile details have been saved successfully.") },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = customColors.accent, contentColor = customColors.onAccent)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
