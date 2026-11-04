package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AddressLocation
import com.example.ui.theme.LocalDelivioColors
import com.example.ui.theme.StatusOpenGreen
import com.example.ui.theme.StatusWarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000L

@Composable
fun MapLocationScreen(
    currentLocation: AddressLocation,
    locationChangedAt: Long? = null,
    onSaveLocation: (AddressLocation, onResult: (Boolean, String?) -> Unit) -> Unit,
    onRequestUrgentChange: (address: String, lat: Double, lng: Double, reason: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val customColors = LocalDelivioColors.current
    val clipboardManager = LocalClipboardManager.current

    var streetAddress by remember { mutableStateOf(currentLocation.street) }
    var cityAddress by remember { mutableStateOf(currentLocation.city) }
    var lat by remember { mutableDoubleStateOf(currentLocation.latitude) }
    var lng by remember { mutableDoubleStateOf(currentLocation.longitude) }
    var notes by remember { mutableStateOf(currentLocation.notes) }
    var locationPermissionGranted by remember { mutableStateOf(false) }

    // 30-day lock check
    val now = remember { System.currentTimeMillis() }
    val isLocked = remember(locationChangedAt, now) {
        if (locationChangedAt == null) false
        else (now - locationChangedAt) < THIRTY_DAYS_MS
    }
    val unlockDateString = remember(locationChangedAt) {
        if (locationChangedAt != null) {
            val unlockDate = Date(locationChangedAt + THIRTY_DAYS_MS)
            SimpleDateFormat("MMMM d, yyyy", Locale.US).format(unlockDate)
        } else {
            ""
        }
    }

    // Dialog states
    var showConfirmSaveDialog by remember { mutableStateOf(false) }
    var showUrgentRequestDialog by remember { mutableStateOf(false) }
    var urgentReason by remember { mutableStateOf("") }
    var urgentReasonError by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    // Error Dialog with Copy button
    var errorMessageToDisplay by remember { mutableStateOf<String?>(null) }
    var successMessageToDisplay by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            locationPermissionGranted = true
            lat = 14.5547
            lng = 121.0509
            streetAddress = "Level 1, Bonifacio High Street Central"
            cityAddress = "Taguig, Metro Manila"
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("map_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "Store Map Location (Pickup Point)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Drag pin or tap map to set exact rider pickup point",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isLocked) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "You can change your location again on $unlockDateString",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (!isLocked) {
                                showConfirmSaveDialog = true
                            }
                        },
                        enabled = !isLocked && !isSubmitting,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = customColors.accent,
                            contentColor = customColors.onAccent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("confirm_save_location_btn")
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = customColors.onAccent,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Saving...")
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Set Location", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        }
                    }

                    if (isLocked) {
                        TextButton(
                            onClick = {
                                urgentReason = ""
                                urgentReasonError = false
                                showUrgentRequestDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("request_urgent_change_btn")
                        ) {
                            Text(
                                text = "Request an urgent change",
                                fontWeight = FontWeight.Bold,
                                color = customColors.accent
                            )
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
        ) {
            // Interactive Map Canvas with Draggable Pin
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                com.example.ui.components.OsmMapView(
                    latitude = lat,
                    longitude = lng,
                    isPicker = true,
                    markerTitle = streetAddress.ifBlank { "Store Location" },
                    onLocationPicked = { newLat, newLng ->
                        lat = newLat
                        lng = newLng
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // "Use My Current Location" floating chip
                FilledTonalButton(
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    elevation = ButtonDefaults.filledTonalButtonElevation(defaultElevation = 4.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .testTag("use_my_location_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Current Location",
                        tint = customColors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Use My Current Location", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Address Details Form Card
            Card(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Selected Coordinates",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Lat: %.4f • Lng: %.4f".format(lat, lng),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = customColors.accent
                        )
                    }

                    OutlinedTextField(
                        value = streetAddress,
                        onValueChange = { streetAddress = it },
                        label = { Text("Street / Building / Unit *") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("map_street_input")
                    )

                    OutlinedTextField(
                        value = cityAddress,
                        onValueChange = { cityAddress = it },
                        label = { Text("City / Region") },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Pickup Instructions for Riders (e.g. Ground floor lobby, back gate)") },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // 1. Confirm 30-day save dialog
    if (showConfirmSaveDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showConfirmSaveDialog = false },
            title = {
                Text("Confirm Location Change", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("You can change your location only once every 30 days. Continue?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSubmitting = true
                        val newLoc = AddressLocation(
                            street = streetAddress.ifBlank { "Store Location" },
                            city = cityAddress.ifBlank { "Metro Manila" },
                            latitude = lat,
                            longitude = lng,
                            notes = notes
                        )
                        onSaveLocation(newLoc) { success, error ->
                            isSubmitting = false
                            showConfirmSaveDialog = false
                            if (!success) {
                                errorMessageToDisplay = error ?: "Unknown error occurred while saving location."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = customColors.accent,
                        contentColor = customColors.onAccent
                    ),
                    modifier = Modifier.testTag("confirm_30_days_continue_btn")
                ) {
                    Text("Continue", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmSaveDialog = false },
                    enabled = !isSubmitting
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // 2. Urgent change request dialog
    if (showUrgentRequestDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showUrgentRequestDialog = false },
            title = {
                Text("Request an Urgent Change", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Your store location is locked under the 30-day policy. Provide a reason to request an urgent update from the admin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "New Location: ${streetAddress.ifBlank { "Pin location" }} (Lat: %.4f, Lng: %.4f)".format(lat, lng),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = urgentReason,
                        onValueChange = {
                            urgentReason = it
                            if (it.isNotBlank()) urgentReasonError = false
                        },
                        label = { Text("Reason for urgent change *") },
                        placeholder = { Text("e.g. Relocated to a new branch due to store renovation") },
                        isError = urgentReasonError,
                        supportingText = if (urgentReasonError) {
                            { Text("Reason is required", color = MaterialTheme.colorScheme.error) }
                        } else null,
                        minLines = 3,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("urgent_change_reason_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (urgentReason.isBlank()) {
                            urgentReasonError = true
                            return@Button
                        }
                        isSubmitting = true
                        val formattedAddress = streetAddress.ifBlank { "Store Location" }.let {
                            if (cityAddress.isNotBlank()) "$it, $cityAddress" else it
                        }
                        onRequestUrgentChange(formattedAddress, lat, lng, urgentReason) { success, error ->
                            isSubmitting = false
                            showUrgentRequestDialog = false
                            if (success) {
                                successMessageToDisplay = "Urgent change request submitted to admin for review."
                            } else {
                                errorMessageToDisplay = error ?: "Failed to submit urgent change request."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = customColors.accent,
                        contentColor = customColors.onAccent
                    ),
                    modifier = Modifier.testTag("submit_urgent_change_btn")
                ) {
                    Text("Submit Request", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUrgentRequestDialog = false },
                    enabled = !isSubmitting
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // 3. Error Dialog with Copy button
    errorMessageToDisplay?.let { errText ->
        AlertDialog(
            onDismissRequest = { errorMessageToDisplay = null },
            icon = {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text("Location Update Failed", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "The server reported the following error:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = errText,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(errText))
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("copy_error_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Error")
                }
            },
            dismissButton = {
                TextButton(onClick = { errorMessageToDisplay = null }) {
                    Text("Close")
                }
            }
        )
    }

    // 4. Success dialog for urgent request
    successMessageToDisplay?.let { succText ->
        AlertDialog(
            onDismissRequest = { successMessageToDisplay = null },
            icon = {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = StatusOpenGreen,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text("Request Submitted", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(succText)
            },
            confirmButton = {
                Button(
                    onClick = { successMessageToDisplay = null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = customColors.accent,
                        contentColor = customColors.onAccent
                    )
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
