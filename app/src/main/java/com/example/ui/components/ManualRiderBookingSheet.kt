package com.example.ui.components

import android.content.Context
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.AddressLocation
import com.example.data.model.MerchantSettings
import com.example.data.model.ServiceRate
import com.example.data.repository.MerchantRepository
import com.example.ui.theme.LocalDelivioColors
import com.example.ui.theme.StatusClosedRed
import com.example.ui.theme.StatusOpenGreen
import com.example.util.FormatUtils
import com.example.util.OsmRouteHelper
import com.example.util.RouteResult
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import android.graphics.Color as AndroidColor

fun isValidPhilippinePhone(phone: String): Boolean {
    val clean = phone.replace(Regex("[^0-9+]"), "")
    return (clean.startsWith("+639") && clean.length == 13) ||
            (clean.startsWith("639") && clean.length == 12) ||
            (clean.startsWith("09") && clean.length == 11) ||
            (clean.startsWith("9") && clean.length == 10)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualRiderBookingSheet(
    settings: MerchantSettings,
    isSubmitting: Boolean = false,
    onBookRider: (
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
        serviceFee: Double
    ) -> Unit,
    onOpenLocationPicker: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    val context = LocalContext.current
    val customColors = LocalDelivioColors.current
    val coroutineScope = rememberCoroutineScope()

    // Form Fields
    var recipientName by remember { mutableStateOf("") }
    var recipientPhone by remember { mutableStateOf("") }
    var deliveryAddress by remember { mutableStateOf("") }
    var dropoffArea by remember { mutableStateOf("") }
    var whatToDeliver by remember { mutableStateOf("") }
    var relatedOrderNumber by remember { mutableStateOf("") }
    var codAmountText by remember { mutableStateOf("") }
    var riderNote by remember { mutableStateOf("") }

    // Coordinates: Pickup (Store) and Dropoff (Recipient)
    val hasMerchantLocation = settings.storeLocation.latitude != 0.0 && settings.storeLocation.longitude != 0.0
    val pickupLat = if (hasMerchantLocation) settings.storeLocation.latitude else 14.5492
    val pickupLng = if (hasMerchantLocation) settings.storeLocation.longitude else 121.0503

    var dropoffLat by remember { mutableDoubleStateOf(14.5547) }
    var dropoffLng by remember { mutableDoubleStateOf(121.0509) }
    var hasDropoffPin by remember { mutableStateOf(false) }

    // Full screen location picker toggle
    var showFullScreenPicker by remember { mutableStateOf(false) }

    // Routing and Rate calculation
    var routeResult by remember { mutableStateOf<RouteResult?>(null) }
    var isLoadingRoute by remember { mutableStateOf(false) }
    var deliveryRate by remember { mutableStateOf<ServiceRate?>(null) }
    var rateErrorMessage by remember { mutableStateOf<String?>(null) }

    // Fetch delivery rates from deliveryRates/{cityId}.services.request_rider (or fallback services.food)
    LaunchedEffect(settings.cityId) {
        val repo = MerchantRepository.getInstance(context)
        val cityId = settings.cityId.ifBlank { "taguig" }
        repo.fetchDeliveryRate(cityId, "request_rider") { rate, err ->
            deliveryRate = rate
            rateErrorMessage = err
        }
    }

    // Recompute route when dropoff pin or pickup changes
    LaunchedEffect(pickupLat, pickupLng, dropoffLat, dropoffLng, hasDropoffPin) {
        if (hasDropoffPin) {
            isLoadingRoute = true
            val res = OsmRouteHelper.getRoute(pickupLat, pickupLng, dropoffLat, dropoffLng)
            routeResult = res
            isLoadingRoute = false
        }
    }

    val distanceKm = if (hasDropoffPin) {
        routeResult?.distanceKm ?: OsmRouteHelper.calculateStraightLineKm(
            pickupLat, pickupLng, dropoffLat, dropoffLng
        )
    } else 0.0

    // Compute delivery fee and service fee according to formula:
    // Base fare covers included km. Extra km: (distance - includedKm) * perKmRate. Clamped between minFee and maxFee.
    val rate = deliveryRate ?: ServiceRate()
    val rawDeliveryFee = if (distanceKm <= rate.includedKm) {
        rate.baseFare
    } else {
        rate.baseFare + (distanceKm - rate.includedKm) * rate.perKmRate
    }
    val deliveryFee = rawDeliveryFee.coerceIn(rate.minFee, rate.maxFee)
    val serviceFee = rate.serviceFee
    val totalFee = deliveryFee + serviceFee

    val isPhoneValid = isValidPhilippinePhone(recipientPhone)
    val isFormComplete = recipientName.isNotBlank() &&
            isPhoneValid &&
            deliveryAddress.isNotBlank() &&
            hasDropoffPin &&
            hasMerchantLocation &&
            rateErrorMessage == null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Sheet Header with Close Button
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(customColors.accentTint),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TwoWheeler,
                                contentDescription = null,
                                tint = customColors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Request a Rider",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                            )
                            Text(
                                text = "Direct courier dispatch for quick delivery",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_booking_form_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }

            // Scrollable Form Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. PICKUP STORE BANNER / WARNING
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasMerchantLocation) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (hasMerchantLocation) "📍" else "⚠️", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pickup Location: ${settings.storeName.ifBlank { "Your Store" }}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
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
                                modifier = Modifier.testTag("set_store_location_btn")
                            ) {
                                Icon(Icons.Default.PinDrop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Set Store Location in Profile", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 2. RECIPIENT INFORMATION (Required)
                Text(
                    text = "Recipient Details",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )

                // Recipient Name
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

                // Recipient Phone
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
                            Text("Please enter a valid Philippine mobile number (e.g. 09XXXXXXXXX)", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recipient_phone_input")
                )

                // Delivery Address
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

                // 3. DROP-OFF MAP WITH SMALL PREVIEW & EXPAND ICON
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Map, contentDescription = null, tint = customColors.accent, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Drop-off Map Pin *",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }

                            // Expand Button -> Opens FullScreenLocationPicker
                            IconButton(
                                onClick = { showFullScreenPicker = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(customColors.accentTint)
                                    .testTag("expand_map_picker_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInFull,
                                    contentDescription = "Expand Map Picker",
                                    tint = customColors.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Small Map Preview (Disallows parent scrolling during touch interaction)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    MapView(ctx).apply {
                                        setTileSource(TileSourceFactory.MAPNIK)
                                        setMultiTouchControls(true)
                                        isTilesScaledToDpi = true
                                        controller.setZoom(16.0)
                                        val p = GeoPoint(dropoffLat, dropoffLng)
                                        controller.setCenter(p)

                                        // Disallow parent verticalScroll intercepting touches on small map
                                        setOnTouchListener { v, event ->
                                            when (event.action) {
                                                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                                                    v.parent?.requestDisallowInterceptTouchEvent(true)
                                                }
                                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                                    v.parent?.requestDisallowInterceptTouchEvent(false)
                                                }
                                            }
                                            false
                                        }

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
                                    val marker = mapView.overlays.filterIsInstance<Marker>().find { it.id == "dropoff_preview_pin" }
                                    marker?.position = p
                                    mapView.invalidate()
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Tap to Expand overlay button
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                shadowElevation = 3.dp,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .clickable { showFullScreenPicker = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Fullscreen, contentDescription = null, modifier = Modifier.size(16.dp), tint = customColors.accent)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (hasDropoffPin) "Adjust Pin" else "Place Pin",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = customColors.accent
                                    )
                                }
                            }
                        }

                        if (!hasDropoffPin) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "⚠️ Tap Expand above to place and confirm the drop-off pin.",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusClosedRed
                            )
                        } else if (dropoffArea.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "📍 Area: $dropoffArea",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = customColors.accent
                            )
                        }
                    }
                }

                // 4. OPTIONAL ORDER & ITEM DETAILS
                Text(
                    text = "Delivery Items & Notes (Optional)",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )

                // What to deliver (e.g. "1 cup of drinks")
                OutlinedTextField(
                    value = whatToDeliver,
                    onValueChange = { whatToDeliver = it },
                    label = { Text("What to deliver (optional)") },
                    placeholder = { Text("e.g. 1 cup of drinks, catering box") },
                    leadingIcon = { Icon(Icons.Default.ShoppingBag, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Related order number (optional, for forgotten items)
                OutlinedTextField(
                    value = relatedOrderNumber,
                    onValueChange = { relatedOrderNumber = it },
                    label = { Text("Related Order # (optional, for forgotten items)") },
                    placeholder = { Text("e.g. #1042") },
                    leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Cash to collect from recipient (optional amount)
                OutlinedTextField(
                    value = codAmountText,
                    onValueChange = { codAmountText = it },
                    label = { Text("Cash to collect from recipient (optional)") },
                    placeholder = { Text("0.00") },
                    leadingIcon = { Text("₱", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Note for rider
                OutlinedTextField(
                    value = riderNote,
                    onValueChange = { riderNote = it },
                    label = { Text("Note for the rider (optional)") },
                    placeholder = { Text("e.g. Handle with care, call recipient upon arrival") },
                    leadingIcon = { Icon(Icons.Default.NoteAlt, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // 5. FEE CARD
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Estimated Delivery Fee",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = customColors.accentTint
                            ) {
                                Text(
                                    text = "Paid by you (merchant)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = customColors.accent,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (rateErrorMessage != null) {
                            Text(
                                text = "Delivery not available in this area (${rateErrorMessage})",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else if (!hasDropoffPin) {
                            Text(
                                text = "Set drop-off pin to compute distance and fee.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            // Road distance
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Road Distance:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    if (isLoadingRoute) "Calculating route..." else "%.1f km".format(distanceKm),
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Base Fare & Per-km
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Delivery Fee (${rate.includedKm}km base + rate):",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    FormatUtils.formatCurrency(deliveryFee),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Service Fee
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Service Fee:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    FormatUtils.formatCurrency(serviceFee),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(8.dp))

                            // Total
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Total Rider Cost",
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = FormatUtils.formatCurrency(totalFee),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = customColors.accent
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // 6. BOTTOM BAR WITH "BOOK RIDER" BUTTON
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = {
                            val cod = codAmountText.toDoubleOrNull() ?: 0.0
                            onBookRider(
                                recipientName.trim(),
                                recipientPhone.trim(),
                                deliveryAddress.trim(),
                                dropoffArea.ifBlank { "Drop-off Location" },
                                dropoffLat,
                                dropoffLng,
                                whatToDeliver.trim(),
                                relatedOrderNumber.trim(),
                                cod,
                                riderNote.trim(),
                                distanceKm,
                                deliveryFee,
                                serviceFee
                            )
                        },
                        enabled = isFormComplete && !isSubmitting,
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
                                text = "Book Rider (${FormatUtils.formatCurrency(totalFee)})",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Full Screen Location Picker Modal
    if (showFullScreenPicker) {
        FullScreenLocationPicker(
            initialLat = dropoffLat,
            initialLng = dropoffLng,
            initialAddress = deliveryAddress,
            onConfirmLocation = { lat, lng, addr, area ->
                dropoffLat = lat
                dropoffLng = lng
                hasDropoffPin = true
                deliveryAddress = addr
                dropoffArea = area
                showFullScreenPicker = false
            },
            onDismiss = {
                showFullScreenPicker = false
            }
        )
    }
}
