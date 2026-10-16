package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.data.repository.MerchantRepository
import com.example.ui.theme.LocalDelivioColors
import com.example.ui.theme.StatusClosedRed
import com.example.ui.theme.StatusOpenGreen
import com.example.ui.theme.StatusWarningAmber
import com.example.util.FormatUtils
import com.example.util.NominatimResult
import com.example.util.OsmRouteHelper
import com.example.util.RouteResult
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiderTrackingModal(
    order: Order,
    merchantLocation: AddressLocation,
    isSubmitting: Boolean = false,
    onConfirmRequestRider: (distanceKm: Double, deliveryFee: Double, note: String) -> Unit,
    onRetryRiderRequest: () -> Unit,
    onCancelRiderRequest: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val customColors = LocalDelivioColors.current
    val coroutineScope = rememberCoroutineScope()
    val riderRequest = order.riderRequest

    // Modal state for Request Form
    var riderNote by remember { mutableStateOf(riderRequest?.note ?: "") }

    // Coordinates: Pickup & Dropoff
    val pickupLat = merchantLocation.latitude.takeIf { it != 0.0 } ?: 14.5492
    val pickupLng = merchantLocation.longitude.takeIf { it != 0.0 } ?: 121.0503

    val initialDropoffLat = order.dropoffLat ?: order.deliveryAddress.latitude.takeIf { it != 0.0 } ?: 14.5547
    val initialDropoffLng = order.dropoffLng ?: order.deliveryAddress.longitude.takeIf { it != 0.0 } ?: 121.0509
    val hasOriginalCoordinates = order.hasDropoffCoordinates

    var currentDropoffLat by remember { mutableDoubleStateOf(initialDropoffLat) }
    var currentDropoffLng by remember { mutableDoubleStateOf(initialDropoffLng) }
    var dropoffAddressText by remember { mutableStateOf(order.deliveryAddress.formatted.ifBlank { "Customer Address" }) }

    // Nominatim Search States (enabled when original order has no drop-off coordinates)
    var searchQuery by remember { mutableStateOf("") }
    var isSearchingNominatim by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<NominatimResult>>(emptyList()) }

    // Route & Distance state
    var routeResult by remember { mutableStateOf<RouteResult?>(null) }
    var isLoadingRoute by remember { mutableStateOf(true) }

    // Delivery Fee calculation
    var deliveryRate by remember { mutableStateOf<ServiceRate?>(null) }
    var rateErrorMessage by remember { mutableStateOf<String?>(null) }

    // Fetch delivery rate for merchant/order city
    LaunchedEffect(order.cityId, order.serviceType) {
        val repo = MerchantRepository.getInstance(context)
        val cityId = order.cityId.ifBlank { "taguig" }
        repo.fetchDeliveryRate(cityId, order.serviceType) { rate, err ->
            deliveryRate = rate
            rateErrorMessage = err
        }
    }

    // Compute route via OSRM with straight-line fallback whenever dropoff changes
    LaunchedEffect(pickupLat, pickupLng, currentDropoffLat, currentDropoffLng) {
        isLoadingRoute = true
        val result = OsmRouteHelper.getRoute(pickupLat, pickupLng, currentDropoffLat, currentDropoffLng)
        routeResult = result
        isLoadingRoute = false
    }

    val distanceKm = routeResult?.distanceKm ?: OsmRouteHelper.calculateStraightLineKm(
        pickupLat, pickupLng, currentDropoffLat, currentDropoffLng
    )

    // Compute fee breakdown
    val computedFee = remember(order.deliveryFee, distanceKm, deliveryRate) {
        if (order.deliveryFee > 0.0) {
            order.deliveryFee
        } else {
            deliveryRate?.calculateFee(distanceKm) ?: (45.0 + (distanceKm * 10.0))
        }
    }

    // Live elapsed timer for Searching state
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(riderRequest?.requestedAt) {
        val requestedAt = riderRequest?.requestedAt
        if (requestedAt != null) {
            while (true) {
                elapsedSeconds = (System.currentTimeMillis() - requestedAt) / 1000L
                kotlinx.coroutines.delay(1000L)
            }
        }
    }
    val elapsedMinutes = elapsedSeconds / 60
    val showNoRiderYet = riderRequest?.isSearching == true && elapsedMinutes >= 5

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (riderRequest == null) {
                // ==============================
                // FORM: REQUEST A RIDER
                // ==============================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Request a Delivery Rider",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                        )
                        Text(
                            text = "Order ${order.orderNumber} • ${order.serviceType.replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(customColors.accentTint)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "OSM & OSRM",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = customColors.accent
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Map View: OpenStreetMap with Pickup, Drop-off, and Route line
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        OrderRouteMapView(
                            pickupLat = pickupLat,
                            pickupLng = pickupLng,
                            dropoffLat = currentDropoffLat,
                            dropoffLng = currentDropoffLng,
                            routePoints = routeResult?.waypoints ?: emptyList(),
                            isDropoffEditable = !hasOriginalCoordinates,
                            onDropoffMoved = { newLat, newLng ->
                                currentDropoffLat = newLat
                                currentDropoffLng = newLng
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Floating Road/Distance badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isLoadingRoute) "Routing..." else "$distanceKm km",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Nominatim Search bar (only if customer order had no drop-off coordinates)
                if (!hasOriginalCoordinates) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(customColors.accentTint.copy(alpha = 0.5f))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "📍 Drop-off coordinates missing — search or drag pin on map:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = customColors.accent
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search address / landmark...", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("nominatim_search_input")
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            FilledTonalButton(
                                onClick = {
                                    isSearchingNominatim = true
                                    coroutineScope.launch {
                                        searchResults = OsmRouteHelper.searchNominatim(searchQuery)
                                        isSearchingNominatim = false
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                if (isSearchingNominatim) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Find", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }

                        // Search result dropdown list
                        if (searchResults.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(6.dp)) {
                                    searchResults.forEach { res ->
                                        Text(
                                            text = res.displayName,
                                            fontSize = 11.sp,
                                            maxLines = 2,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    currentDropoffLat = res.latitude
                                                    currentDropoffLng = res.longitude
                                                    dropoffAddressText = res.displayName
                                                    searchResults = emptyList()
                                                }
                                                .padding(vertical = 4.dp, horizontal = 6.dp)
                                        )
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Locations & Customer Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Pickup
                        Row(verticalAlignment = Alignment.Top) {
                            Text("📍", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Pickup Location (Your Store)", style = MaterialTheme.typography.labelSmall, color = customColors.accent, fontWeight = FontWeight.Bold)
                                Text(merchantLocation.formatted.ifBlank { "Store Location" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        // Dropoff
                        Row(verticalAlignment = Alignment.Top) {
                            Text("🏁", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Drop-off Location (Customer)", style = MaterialTheme.typography.labelSmall, color = StatusClosedRed, fontWeight = FontWeight.Bold)
                                Text(dropoffAddressText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                if (order.deliveryAddress.notes.isNotBlank()) {
                                    Text("Note: ${order.deliveryAddress.notes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        // Customer Contact
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("👤", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(order.customerName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text(order.customerPhone.ifBlank { "No phone provided" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            if (order.customerPhone.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL).apply {
                                            data = Uri.parse("tel:${order.customerPhone}")
                                        }
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(StatusOpenGreen)
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = "Call Customer", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Distance & Fee Breakdown
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Calculated Distance:", style = MaterialTheme.typography.bodySmall)
                            Text("$distanceKm km", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }

                        if (deliveryRate != null && order.deliveryFee <= 0.0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Base Fare (incl. ${deliveryRate!!.includedKm} km):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(FormatUtils.formatCurrency(deliveryRate!!.baseFare), style = MaterialTheme.typography.bodySmall)
                            }
                            if (distanceKm > deliveryRate!!.includedKm) {
                                val extraKm = distanceKm - deliveryRate!!.includedKm
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Extra Distance (${"%.1f".format(extraKm)} km @ ${FormatUtils.formatCurrency(deliveryRate!!.perKmRate)}/km):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(FormatUtils.formatCurrency(extraKm * deliveryRate!!.perKmRate), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Service Fee:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(FormatUtils.formatCurrency(deliveryRate!!.serviceFee), style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Total Delivery Fee", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Charged to customer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = FormatUtils.formatCurrency(computedFee),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = customColors.accent
                            )
                        }
                    }
                }

                if (rateErrorMessage != null && order.deliveryFee <= 0.0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Delivery not available: $rateErrorMessage",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Optional Rider Note Field
                OutlinedTextField(
                    value = riderNote,
                    onValueChange = { riderNote = it },
                    label = { Text("Note for Rider (optional)") },
                    placeholder = { Text("e.g. Bring insulated bag, pick up at side counter") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Confirm Button
                Button(
                    onClick = {
                        onConfirmRequestRider(distanceKm, computedFee, riderNote)
                    },
                    enabled = !isSubmitting,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = customColors.accent,
                        contentColor = customColors.onAccent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("confirm_request_rider_btn")
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = customColors.onAccent, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.TwoWheeler, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Confirm & Request Rider", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            } else {
                // ==============================
                // ACTIVE RIDER TRACKING VIEW
                // ==============================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Rider Dispatch Status",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Order ${order.orderNumber}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Live Status Badge
                    val statusText = riderRequest.status
                    val badgeColor = when (statusText.lowercase()) {
                        "rider assigned", "assigned" -> customColors.accent
                        "arrived at merchant", "arrived" -> StatusWarningAmber
                        "picked up" -> StatusOpenGreen
                        else -> customColors.accent
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = statusText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = badgeColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Live Map with Pickup, Drop-off, and Rider location if assigned
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    OrderRouteMapView(
                        pickupLat = pickupLat,
                        pickupLng = pickupLng,
                        dropoffLat = currentDropoffLat,
                        dropoffLng = currentDropoffLng,
                        routePoints = routeResult?.waypoints ?: emptyList(),
                        riderLat = order.riderLocation?.latitude,
                        riderLng = order.riderLocation?.longitude,
                        isDropoffEditable = false,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (riderRequest.isSearching) {
                    // SEARCHING STATE CARD
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (showNoRiderYet) StatusWarningAmber.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!showNoRiderYet) {
                                    CircularProgressIndicator(
                                        color = customColors.accent,
                                        modifier = Modifier.size(32.dp),
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column {
                                        Text("Searching for nearby rider...", fontWeight = FontWeight.Bold)
                                        Text("Dispatched ${elapsedMinutes}m ago • Waiting for response", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = StatusWarningAmber,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column {
                                        Text("No rider yet", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = StatusWarningAmber)
                                        Text("Over 5 minutes passed with no rider assigned.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            // Show Retry & Cancel request buttons if 5 min passed, or Cancel button
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onCancelRiderRequest,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Cancel Request", fontWeight = FontWeight.Bold)
                                }

                                if (showNoRiderYet) {
                                    Button(
                                        onClick = onRetryRiderRequest,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = customColors.accent,
                                            contentColor = customColors.onAccent
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Retry", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // ASSIGNED RIDER CARD
                    val rider = riderRequest.rider ?: order.rider
                    val rName = order.riderName.ifBlank { rider?.name ?: "Assigned Rider" }
                    val rPhone = order.riderPhone.ifBlank { rider?.phone ?: "" }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(customColors.accentTint),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("🛵", fontSize = 24.sp)
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Status: ${riderRequest.status}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = customColors.accent,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                if (rPhone.isNotBlank()) {
                                    FilledIconButton(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                                data = Uri.parse("tel:$rPhone")
                                            }
                                            context.startActivity(intent)
                                        },
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = StatusOpenGreen,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.testTag("call_rider_btn")
                                    ) {
                                        Icon(Icons.Default.Phone, contentDescription = "Call Rider")
                                    }
                                }
                            }

                            if (order.riderLocation != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Live Rider Location:", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "%.4f, %.4f".format(order.riderLocation.latitude, order.riderLocation.longitude),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = customColors.accent
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
