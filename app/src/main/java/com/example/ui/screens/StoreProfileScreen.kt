package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.example.data.model.MerchantSettings
import com.example.ui.theme.LocalDelivioColors
import kotlinx.coroutines.delay

private enum class LogoImageStatus {
    IDLE,
    LOADING,
    OK,
    FAILED,
    TIMEOUT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreProfileScreen(
    settings: MerchantSettings,
    onSave: (Map<String, Any>, onResult: (Boolean, String?) -> Unit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val customColors = LocalDelivioColors.current
    val clipboardManager = LocalClipboardManager.current

    var ownerNameInput by remember(settings.ownerName) { mutableStateOf(settings.ownerName) }
    var phoneInput by remember(settings.phone) { mutableStateOf(settings.phone) }
    var logoUrlInput by remember(settings.logoUrl) { mutableStateOf(settings.logoUrl) }
    var bannerUrlInput by remember(settings.bannerUrl) { mutableStateOf(settings.bannerUrl) }
    var descriptionInput by remember(settings.description) { mutableStateOf(settings.description) }

    var isSaving by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showImageFailedConfirmDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var failureErrorText by remember { mutableStateOf<String?>(null) }

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

    val trimmedLogo = logoUrlInput.trim()
    val trimmedBanner = bannerUrlInput.trim()

    // Logo image loading status with 8s timeout
    var logoStatus by remember(trimmedLogo) {
        mutableStateOf(
            if (trimmedLogo.isBlank()) LogoImageStatus.IDLE
            else if (!trimmedLogo.startsWith("https://")) LogoImageStatus.FAILED
            else LogoImageStatus.LOADING
        )
    }

    LaunchedEffect(trimmedLogo) {
        if (trimmedLogo.startsWith("https://")) {
            logoStatus = LogoImageStatus.LOADING
            // 8-second timeout timer
            delay(8000L)
            if (logoStatus == LogoImageStatus.LOADING) {
                logoStatus = LogoImageStatus.TIMEOUT
            }
        } else if (trimmedLogo.isNotBlank()) {
            logoStatus = LogoImageStatus.FAILED
        } else {
            logoStatus = LogoImageStatus.IDLE
        }
    }

    // Change Detection
    val isOwnerChanged = ownerNameInput.trim() != settings.ownerName.trim()
    val isPhoneChanged = phoneInput.trim() != settings.phone.trim()
    val isLogoChanged = trimmedLogo != settings.logoUrl.trim()
    val isBannerChanged = trimmedBanner != settings.bannerUrl.trim()
    val isDescriptionChanged = descriptionInput.trim() != settings.description.trim()

    val hasChanges = isOwnerChanged || isPhoneChanged || isLogoChanged || isBannerChanged || isDescriptionChanged
    val isOwnerValid = ownerNameInput.trim().isNotBlank()
    val isPhoneValid = isValidPhilippinePhone(phoneInput)
    val isLogoValid = trimmedLogo.isBlank() || trimmedLogo.startsWith("https://")
    val isBannerValid = trimmedBanner.isBlank() || trimmedBanner.startsWith("https://")

    val canSave = hasChanges && isOwnerValid && isPhoneValid && isLogoValid && isBannerValid && !isSaving

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
        if (!canSave) return
        isSaving = true
        val changedFields = mutableMapOf<String, Any>()
        if (isOwnerChanged) changedFields["ownerName"] = ownerNameInput.trim()
        if (isPhoneChanged) changedFields["phone"] = phoneInput.trim()
        if (isLogoChanged) changedFields["logoUrl"] = trimmedLogo
        if (isBannerChanged) changedFields["bannerUrl"] = trimmedBanner
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
                        .padding(16.dp)
                ) {
                    if (!hasChanges) {
                        Text(
                            text = "No changes made yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    } else if (!isOwnerValid) {
                        Text(
                            text = "Owner name is required",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    } else if (!isPhoneValid) {
                        Text(
                            text = "Valid Philippine phone number is required",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    } else if (!isLogoValid) {
                        Text(
                            text = "Store logo URL must start with https://",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    } else if (!isBannerValid) {
                        Text(
                            text = "Banner URL must start with https://",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    Button(
                        onClick = {
                            if (trimmedLogo.isNotBlank() && (logoStatus == LogoImageStatus.FAILED || logoStatus == LogoImageStatus.TIMEOUT)) {
                                showImageFailedConfirmDialog = true
                            } else {
                                executeSave()
                            }
                        },
                        enabled = canSave,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = customColors.accent,
                            contentColor = customColors.onAccent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_profile_changes_btn")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = customColors.onAccent,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Saving Changes...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Profile Changes", fontWeight = FontWeight.Bold)
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Shop Name (Read-Only)
            OutlinedTextField(
                value = settings.storeName,
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text("Shop Name") },
                leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
                trailingIcon = { Icon(Icons.Default.Lock, contentDescription = "Locked") },
                supportingText = { Text("Shop name cannot be edited directly", fontSize = 11.sp) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("store_profile_shop_name_readonly")
            )

            // 2. Login Email (Read-Only)
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

            // 3. Owner Name (Editable)
            OutlinedTextField(
                value = ownerNameInput,
                onValueChange = { ownerNameInput = it },
                label = { Text("Owner Name *") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                isError = !isOwnerValid,
                supportingText = if (!isOwnerValid) {
                    { Text("Owner name is required", color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                } else null,
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("store_profile_owner_name_input")
            )

            // 4. Phone Number (Editable)
            OutlinedTextField(
                value = phoneInput,
                onValueChange = { phoneInput = it },
                label = { Text("Phone Number *") },
                placeholder = { Text("09171234567 or +639171234567") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = !isPhoneValid,
                supportingText = {
                    if (!isPhoneValid) {
                        Text("Enter a valid PH mobile number (e.g. 09171234567)", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
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

            // 5. Store Logo URL + Live Preview with Status Line
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = logoUrlInput,
                    onValueChange = { logoUrlInput = it },
                    label = { Text("Store Logo URL (HTTPS)") },
                    placeholder = { Text("https://example.com/logo.png") },
                    leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                    isError = !isLogoValid,
                    supportingText = {
                        if (!isLogoValid) {
                            Text("URL must start with https://", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                        } else {
                            Text("Direct image link (PNG, JPG, or WebP)", fontSize = 11.sp)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("store_profile_logo_url_input")
                )

                // Live Preview & Status
                if (trimmedLogo.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.5.dp, customColors.accentTint, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            SubcomposeAsyncImage(
                                model = trimmedLogo,
                                contentDescription = "Logo preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                onSuccess = {
                                    logoStatus = LogoImageStatus.OK
                                },
                                onError = {
                                    if (logoStatus != LogoImageStatus.TIMEOUT) {
                                        logoStatus = LogoImageStatus.FAILED
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "Logo Preview",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(2.dp))

                            when (logoStatus) {
                                LogoImageStatus.LOADING -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 1.5.dp,
                                            color = customColors.accent
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Loading...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                LogoImageStatus.OK -> {
                                    Text(
                                        text = "Image OK",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10B981)
                                    )
                                }
                                LogoImageStatus.TIMEOUT -> {
                                    Text(
                                        text = "Could not load this image - check the link",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                LogoImageStatus.FAILED -> {
                                    Text(
                                        text = "Failed",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                LogoImageStatus.IDLE -> {}
                            }
                        }
                    }
                }
            }

            // 6. Banner URL (Optional)
            OutlinedTextField(
                value = bannerUrlInput,
                onValueChange = { bannerUrlInput = it },
                label = { Text("Banner URL (Optional, HTTPS)") },
                placeholder = { Text("https://example.com/banner.jpg") },
                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                isError = !isBannerValid,
                supportingText = if (!isBannerValid) {
                    { Text("URL must start with https://", color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                } else null,
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("store_profile_banner_url_input")
            )

            // 7. Store Description (Optional)
            OutlinedTextField(
                value = descriptionInput,
                onValueChange = { descriptionInput = it },
                label = { Text("Store Description (Optional)") },
                placeholder = { Text("Tell customers about your kitchen, specialties, and hours...") },
                leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("store_profile_description_input")
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Confirm dialog when logo image failed to load
    if (showImageFailedConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showImageFailedConfirmDialog = false },
            title = { Text("Image Load Warning", fontWeight = FontWeight.Bold) },
            text = { Text("The image did not load. Save anyway?") },
            confirmButton = {
                Button(
                    onClick = {
                        showImageFailedConfirmDialog = false
                        executeSave()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = customColors.accent)
                ) {
                    Text("Save Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImageFailedConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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

    // Success Dialog with Green Check
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                onBack()
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    text = "Profile Updated",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("Profile updated successfully")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = customColors.accent)
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Failure Dialog with Copy Button
    failureErrorText?.let { errMsg ->
        AlertDialog(
            onDismissRequest = { failureErrorText = null },
            title = {
                Text(
                    "Failed to Update Profile",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Column {
                    Text(
                        "An error occurred while saving your store profile:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(errMsg))
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Error")
                }
            },
            dismissButton = {
                TextButton(onClick = { failureErrorText = null }) {
                    Text("OK")
                }
            }
        )
    }
}
