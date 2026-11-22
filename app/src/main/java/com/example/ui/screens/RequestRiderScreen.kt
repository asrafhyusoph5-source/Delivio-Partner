package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.MerchantSettings
import com.example.data.model.ServiceRate
import com.example.data.repository.MerchantRepository
import com.example.ui.components.isValidPhilippinePhone
import com.example.ui.theme.LocalDelivioColors
import com.example.ui.theme.StatusClosedRed
import com.example.ui.theme.StatusOpenGreen
import com.example.util.FormatUtils
import com.example.util.OsmRouteHelper
import com.example.util.RouteResult
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestRiderScreen(
    settings: MerchantSettings,
    isSubmitting: Boolean = false,
    submissionError: String? = null,
    initialDropoffPinnedForTest: Boolean = false,
    onClearError: () -> Unit = {},
    onBookRider: (
        recipientName: String,
        recipientPhone: String,
        deliveryAddress: String,
        dropoffArea: String,
        dropoffLat: Double,
        dropoffLng: Double,
        whatToDeliver: String,
        relatedOrderNumber: String,
        isCod: Boolean,
        codAmount: Double,
        riderNote: String,
        distanceKm: Double,
        deliveryFee: Double
    ) -> Unit,
    onOpenLocationPicker: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val customColors = LocalDelivioColors.current

    // Step state: 1 = Pin Point, 2 = Customer details
    var currentStep by remember { mutableIntStateOf(1) }
    var showFullScreenPicker by remember { mutableStateOf(false) }

    // Step 1 fields
    val hasMerchantLocation = settings.storeLocation.latitude != 0.0 && settings.storeLocation.longitude != 0.0
    val pickupLat = if (hasMerchantLocation) settings.storeLocation.latitude else 14.5492
    val pickupLng = if (hasMerchantLocation) settings.storeLocation.longitude else 121.0503

    var dropoffLat by remember { mutableDoubleStateOf(14.5547) }
    var dropoffLng by remember { mutableDoubleStateOf(121.0509) }
    var hasDropoffPin by remember { mutableStateOf(initialDropoffPinnedForTest) }
    var confirmedPinAddress by remember { mutableStateOf(if (initialDropoffPinnedForTest) "5th Ave, BGC, Taguig" else "") }
    var deliveryAddress by remember { mutableStateOf(if (initialDropoffPinnedForTest) "5th Ave, BGC, Taguig" else "") }
    var dropoffArea by remember { mutableStateOf(if (initialDropoffPinnedForTest) "Taguig" else "") }
    var riderNote by remember { mutableStateOf("") }

    // Step 2 fields
    var recipientName by remember { mutableStateOf("") }
    var recipientPhone by remember { mutableStateOf("") }
    var orderDetails by remember { mutableStateOf("") }
    var relatedOrderNumber by remember { mutableStateOf("") }
    var isCod by remember { mutableStateOf(false) }
    var orderAmountText by remember { mutableStateOf("") }

    // Rate & route state
    var routeResult by remember { mutableStateOf<RouteResult?>(null) }
    var isLoadingRoute by remember { mutableStateOf(false) }
    var deliveryRate by remember { mutableStateOf<ServiceRate?>(null) }
    var rateErrorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(settings.cityId) {
        val repo = MerchantRepository.getInstance(context)
        val cityId = settings.cityId.ifBlank { "taguig" }
        repo.fetchDeliveryRate(cityId, "request_rider") { rate, err ->
            deliveryRate = rate
            rateErrorMessage = err
        }
    }

    LaunchedEffect(pickupLat, pickupLng, dropoffLat, dropoffLng, hasDropoffPin) {
        if (hasDropoffPin) {
            isLoadingRoute = true
            routeResult = OsmRouteHelper.getRoute(pickupLat, pickupLng, dropoffLat, dropoffLng)
            isLoadingRoute = false
        }
    }

    val distanceKm = if (hasDropoffPin) {
        routeResult?.distanceKm ?: OsmRouteHelper.calculateStraightLineKm(
            pickupLat, pickupLng, dropoffLat, dropoffLng
        )
    } else 0.0

    val rate = deliveryRate ?: ServiceRate()
    val rawDeliveryFee = if (distanceKm <= rate.includedKm) {
        rate.baseFare
    } else {
        rate.baseFare + (distanceKm - rate.includedKm) * rate.perKmRate
    }
    val deliveryFee = rawDeliveryFee.coerceIn(rate.minFee, rate.maxFee)

    val isStep1Valid = hasDropoffPin &&
        deliveryAddress.isNotBlank() &&
        hasMerchantLocation &&
        rateErrorMessage == null

    val parsedOrderAmount = orderAmountText.trim().replace(",", "").toDoubleOrNull()
    val isOrderAmountValid = !isCod || (parsedOrderAmount != null && parsedOrderAmount > 0.0)
    val isPhoneValid = isValidPhilippinePhone(recipientPhone)
    val isStep2Valid = isStep1Valid &&
        recipientName.isNotBlank() &&
        isPhoneValid &&
        isOrderAmountValid

    val shopDisplayName = settings.displayName.ifBlank { settings.storeName.ifBlank { "Your Store" } }

    // Full-screen inline MapPickerScreen (no Dialog / ModalBottomSheet wrapper so Confirm button stays cleanly above nav bar)
    if (showFullScreenPicker) {
        MapPickerScreen(
            title = "Set Drop-off Location",
            initialLat = if (hasDropoffPin) dropoffLat else pickupLat,
            initialLng = if (hasDropoffPin) dropoffLng else pickupLng,
            initialAddress = deliveryAddress,
            onConfirmLocation = { lat, lng, addr, area ->
                dropoffLat = lat
                dropoffLng = lng
                hasDropoffPin = true
                val resolvedAddr = addr.trim()
                if (resolvedAddr.isNotBlank()) {
                    confirmedPinAddress = resolvedAddr
                    deliveryAddress = resolvedAddr
                } else {
                    confirmedPinAddress = String.format(java.util.Locale.US, "%.5f, %.5f", lat, lng)
                }
                dropoffArea = area.trim()
                showFullScreenPicker = false
            },
            onBack = {
                showFullScreenPicker = false
            }
        )
        return
    }

    BackHandler {
        if (currentStep == 2) {
            onClearError()
            currentStep = 1
        } else {
            onDismiss()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (currentStep == 2) {
                                onClearError()
                                currentStep = 1
                            } else {
                                onDismiss()
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("request_rider_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (currentStep == 1) "Pin Point" else "Customer details",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("request_rider_screen_title")
                        )
                        Text(
                            text = "Delivio Instant • Request a Rider",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = customColors.accentTint
                    ) {
                        Text(
                            text = if (currentStep == 1) "Step 1 of 2" else "Step 2 of 2",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = customColors.accent,
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("request_rider_step_label")
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (currentStep == 1) {
                        Button(
                            onClick = { currentStep = 2 },
                            enabled = isStep1Valid,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = customColors.accent,
                                contentColor = customColors.onAccent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("request_rider_next_btn")
                        ) {
                            Text(
                                text = "NEXT",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        val submitRequest = {
                            val cashAmount = if (isCod) (parsedOrderAmount ?: 0.0).coerceAtLeast(0.0) else 0.0
                            onBookRider(
                                recipientName.trim(),
                                recipientPhone.trim(),
                                deliveryAddress.trim(),
                                dropoffArea.ifBlank { "Drop-off Location" },
                                dropoffLat,
                                dropoffLng,
                                orderDetails.trim(),
                                relatedOrderNumber.trim(),
                                isCod,
                                cashAmount,
                                riderNote.trim(),
                                distanceKm,
                                deliveryFee
                            )
                        }

                        if (!submissionError.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("manual_booking_error_banner")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = "Request failed",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = submissionError,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilledTonalButton(
                                        onClick = submitRequest,
                                        enabled = isStep2Valid && !isSubmitting,
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("manual_booking_retry_btn")
                                    ) {
                                        Text("Retry", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = submitRequest,
                            enabled = isStep2Valid && !isSubmitting,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = customColors.accent,
                                contentColor = customColors.onAccent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("book_rider_submit_btn")
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = customColors.onAccent,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.DeliveryDining, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Book Rider - ${FormatUtils.formatCurrency(deliveryFee)}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                            }
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (currentStep == 1) {
                // =============================================================
                // SCREEN 1: "Pin Point"
                // =============================================================

                // Card: Pick location (read-only merchant shop display name + full address)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasMerchantLocation) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pick_location_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (hasMerchantLocation) Icons.Default.Storefront else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (hasMerchantLocation) customColors.accent else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pick location",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (hasMerchantLocation) customColors.accent else MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = shopDisplayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        if (hasMerchantLocation) {
                            Text(
                                text = settings.storeLocation.formatted,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Set your store location in Profile first so riders know where to pick up.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FilledTonalButton(
                                onClick = onOpenLocationPicker,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("set_store_location_btn")
                            ) {
                                Icon(Icons.Default.PinDrop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Set Store Location in Profile", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Card: Drop-off Map Pin *
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dropoff_pin_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Map,
                                    contentDescription = null,
                                    tint = customColors.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Drop-off Map Pin *",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }

                            IconButton(
                                onClick = { showFullScreenPicker = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("expand_map_picker_btn")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(customColors.accentTint),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInFull,
                                        contentDescription = "Expand Map Picker",
                                        tint = customColors.accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(170.dp)
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    MapView(ctx).apply {
                                        setTileSource(TileSourceFactory.MAPNIK)
                                        setMultiTouchControls(false)
                                        isTilesScaledToDpi = true
                                        controller.setZoom(16.0)
                                        val p = GeoPoint(dropoffLat, dropoffLng)
                                        controller.setCenter(p)

                                        val marker = Marker(this).apply {
                                            id = "dropoff_preview_pin"
                                            position = p
                                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                            title = "Drop-off Point"
                                        }
                                        overlays.add(marker)
                                    }
                                },
                                update = { mapView ->
                                    val p = GeoPoint(dropoffLat, dropoffLng)
                                    mapView.controller.setCenter(p)
                                    val marker = mapView.overlays.filterIsInstance<Marker>()
                                        .find { it.id == "dropoff_preview_pin" }
                                    marker?.position = p
                                    mapView.invalidate()
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { showFullScreenPicker = true }
                                    .testTag("dropoff_map_preview_overlay")
                            )

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = customColors.accent,
                                shadowElevation = 4.dp,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp)
                                    .heightIn(min = 40.dp)
                                    .clickable { showFullScreenPicker = true }
                                    .testTag("place_pin_btn")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PinDrop,
                                        contentDescription = null,
                                        tint = customColors.onAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (hasDropoffPin) "Adjust Pin" else "Place Pin",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = customColors.onAccent
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (!hasDropoffPin) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = StatusClosedRed,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Tap Place Pin or Expand to set the drop-off location.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusClosedRed
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = StatusOpenGreen,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = confirmedPinAddress.ifBlank { deliveryAddress },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.testTag("confirmed_pin_address")
                                    )
                                    if (dropoffArea.isNotBlank()) {
                                        Text(
                                            text = "Area: $dropoffArea",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = customColors.accent
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Delivery Address *
                OutlinedTextField(
                    value = deliveryAddress,
                    onValueChange = { deliveryAddress = it },
                    label = { Text("Delivery Address *") },
                    placeholder = { Text("Street, building, house number...") },
                    leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("delivery_address_input")
                )

                // Note for the rider (optional)
                OutlinedTextField(
                    value = riderNote,
                    onValueChange = { riderNote = it },
                    label = { Text("Note for the rider (optional)") },
                    placeholder = { Text("e.g. Handle with care, call recipient upon arrival") },
                    leadingIcon = { Icon(Icons.Default.NoteAlt, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rider_note_input")
                )

                // Live Delivery fee line under the fields
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("step1_delivery_fee_row")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Delivery fee",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when {
                                    rateErrorMessage != null -> "Delivery not available: $rateErrorMessage"
                                    !hasDropoffPin -> "Paid by merchant • Set pin to calculate"
                                    isLoadingRoute -> "Paid by merchant • Calculating distance..."
                                    else -> "Paid by merchant • ${"%.1f".format(distanceKm)} km"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (rateErrorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (rateErrorMessage == null && hasDropoffPin) {
                            Text(
                                text = FormatUtils.formatCurrency(deliveryFee),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = customColors.accent,
                                modifier = Modifier.testTag("step1_delivery_fee_amount")
                            )
                        }
                    }
                }
            } else {
                // =============================================================
                // SCREEN 2: "Customer details"
                // =============================================================

                // Recipient Name *
                OutlinedTextField(
                    value = recipientName,
                    onValueChange = { recipientName = it },
                    label = { Text("Recipient Name *") },
                    placeholder = { Text("e.g. Maria Santos") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recipient_name_input")
                )

                // Recipient Phone * (Philippine mobile number check)
                OutlinedTextField(
                    value = recipientPhone,
                    onValueChange = { recipientPhone = it },
                    label = { Text("Recipient Phone *") },
                    placeholder = { Text("e.g. 0917 123 4567") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    isError = recipientPhone.isNotBlank() && !isPhoneValid,
                    supportingText = {
                        if (recipientPhone.isNotBlank() && !isPhoneValid) {
                            Text(
                                text = "Please enter a valid Philippine mobile number (e.g. 09XXXXXXXXX)",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recipient_phone_input")
                )

                // Order details (optional)
                OutlinedTextField(
                    value = orderDetails,
                    onValueChange = { orderDetails = it },
                    label = { Text("Order details (optional)") },
                    placeholder = { Text("e.g. 2 milk tea cups, catering box") },
                    leadingIcon = { Icon(Icons.Default.ShoppingBag, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("order_details_input")
                )

                // Related Order # (optional, for forgotten items)
                OutlinedTextField(
                    value = relatedOrderNumber,
                    onValueChange = { relatedOrderNumber = it },
                    label = { Text("Related Order # (optional, for forgotten items)") },
                    placeholder = { Text("e.g. #1042") },
                    leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("related_order_input")
                )

                // Two-option payment selector: Already paid | Cash on Delivery
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Payment",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        SegmentedButton(
                            selected = !isCod,
                            onClick = { isCod = false },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("payment_option_paid")
                        ) {
                            Text("Already paid", fontWeight = FontWeight.Bold)
                        }
                        SegmentedButton(
                            selected = isCod,
                            onClick = { isCod = true },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("payment_option_cod")
                        ) {
                            Text("Cash on Delivery", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (!isCod) {
                        Text(
                            text = "Rider sees Paid Online with no cash to collect.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = orderAmountText,
                            onValueChange = { orderAmountText = it },
                            label = { Text("Order amount *") },
                            placeholder = { Text("0.00") },
                            leadingIcon = {
                                Text(
                                    text = "₱",
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            isError = orderAmountText.isNotBlank() && (parsedOrderAmount == null || parsedOrderAmount <= 0.0),
                            supportingText = {
                                if (orderAmountText.isNotBlank() && (parsedOrderAmount == null || parsedOrderAmount <= 0.0)) {
                                    Text(
                                        text = "Enter an order amount greater than 0",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("order_amount_input")
                        )
                    }
                }

                // Total Cost card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("total_cost_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Total Cost",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Delivery fee (paid by you)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = FormatUtils.formatCurrency(deliveryFee),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = customColors.accent
                            )
                        }

                        if (isCod) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            val collectDisplay = FormatUtils.formatCurrency((parsedOrderAmount ?: 0.0).coerceAtLeast(0.0))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Payments,
                                    contentDescription = null,
                                    tint = customColors.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Rider collects $collectDisplay from the recipient",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("cod_rider_collects_text")
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
