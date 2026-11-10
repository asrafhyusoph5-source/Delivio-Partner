package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.LocalDelivioColors
import com.example.util.NominatimResult
import com.example.util.OsmRouteHelper
import com.example.util.ReverseGeocodeResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000L

/**
 * One shared, full-screen location picker screen.
 * Root is a Column with Modifier.fillMaxSize().systemBarsPadding().
 * Children in exact order:
 *  (a) Top bar (back arrow, title)
 *  (b) Search bar
 *  (c) Map in a Box with Modifier.weight(1f), center pin, hint, zoom and my-location buttons
 *  (d) Bottom panel as a normal Column item below the map (not floating over it), with
 *      one-line ellipsized address, small coordinates, and 56dp tall Confirm button.
 */
@Composable
fun MapPickerScreen(
    title: String = "Set Location",
    initialLat: Double,
    initialLng: Double,
    initialAddress: String = "",
    onConfirmLocation: (lat: Double, lng: Double, address: String, areaName: String) -> Unit,
    onBack: () -> Unit,
    locationChangedAt: Long? = null,
    onRequestUrgentChange: ((address: String, lat: Double, lng: Double, reason: String, onResult: (Boolean, String?) -> Unit) -> Unit)? = null
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val customColors = LocalDelivioColors.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current

    // Coordinates state
    val defaultLat = if (initialLat != 0.0) initialLat else 14.5547
    val defaultLng = if (initialLng != 0.0) initialLng else 121.0509

    var currentCenterLat by remember { mutableDoubleStateOf(defaultLat) }
    var currentCenterLng by remember { mutableDoubleStateOf(defaultLng) }
    var currentAddress by remember { mutableStateOf(initialAddress.ifBlank { "Pin Location" }) }
    var currentArea by remember { mutableStateOf("") }
    var isReverseGeocoding by remember { mutableStateOf(false) }
    var geocodeJob by remember { mutableStateOf<Job?>(null) }

    // Search state
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<NominatimResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    // Map reference
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    // 30-day location lock check
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

    var showConfirmSaveDialog by remember { mutableStateOf(false) }
    var showUrgentRequestDialog by remember { mutableStateOf(false) }
    var urgentReason by remember { mutableStateOf("") }
    var isSubmittingUrgent by remember { mutableStateOf(false) }

    // Initialize osmdroid
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("osmdroid_picker", Context.MODE_PRIVATE)
        Configuration.getInstance().load(context, prefs)
        Configuration.getInstance().userAgentValue = context.packageName

        if (initialAddress.isBlank()) {
            isReverseGeocoding = true
            val res = OsmRouteHelper.reverseGeocode(defaultLat, defaultLng)
            currentAddress = res.formattedAddress
            currentArea = res.area
            isReverseGeocoding = false
        }
    }

    // Permission launcher for "My Location"
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            val locMgr = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            @SuppressLint("MissingPermission")
            val lastLoc = try {
                locMgr?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locMgr?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } catch (_: Exception) {
                null
            }
            if (lastLoc != null) {
                val p = GeoPoint(lastLoc.latitude, lastLoc.longitude)
                currentCenterLat = lastLoc.latitude
                currentCenterLng = lastLoc.longitude
                mapViewRef?.controller?.animateTo(p)
                geocodeJob?.cancel()
                geocodeJob = coroutineScope.launch {
                    isReverseGeocoding = true
                    val res = OsmRouteHelper.reverseGeocode(lastLoc.latitude, lastLoc.longitude)
                    currentAddress = res.formattedAddress
                    currentArea = res.area
                    isReverseGeocoding = false
                }
            }
        }
    }

    // Detect keyboard (IME)
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val isKeyboardOpen = imeBottomPx > 0

    // Navigation bar bottom inset calculation
    val navBarBottomPx = WindowInsets.navigationBars.getBottom(density)
    val navigationBarsBottomInset = with(density) { navBarBottomPx.toDp() }
    val bottomInsetPadding = maxOf(navigationBarsBottomInset, 24.dp) + 16.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // (a) Top Bar (back arrow, title)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("map_picker_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // (b) The Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { q ->
                searchQuery = q
                searchJob?.cancel()
                if (q.trim().length >= 3) {
                    searchJob = coroutineScope.launch {
                        isSearching = true
                        delay(350L) // Debounced Nominatim search
                        searchResults = OsmRouteHelper.searchNominatim(q.trim(), countryCode = "ph")
                        isSearching = false
                    }
                } else {
                    searchResults = emptyList()
                    isSearching = false
                }
            },
            placeholder = { Text("Search address, street, landmark...", fontSize = 14.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = customColors.accent
                )
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = {
                        searchQuery = ""
                        searchResults = emptyList()
                        keyboardController?.hide()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("map_picker_search_input")
        )

        // (c) The Map in a Box with Modifier.weight(1f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Interactive Map View
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        isTilesScaledToDpi = true
                        controller.setZoom(17.0)
                        controller.setCenter(GeoPoint(defaultLat, defaultLng))

                        addMapListener(object : MapListener {
                            override fun onScroll(event: ScrollEvent?): Boolean {
                                val c = mapCenter
                                if (c != null) {
                                    currentCenterLat = c.latitude
                                    currentCenterLng = c.longitude

                                    geocodeJob?.cancel()
                                    geocodeJob = coroutineScope.launch {
                                        delay(400L) // debounce
                                        isReverseGeocoding = true
                                        val res = OsmRouteHelper.reverseGeocode(c.latitude, c.longitude)
                                        currentAddress = res.formattedAddress
                                        currentArea = res.area
                                        isReverseGeocoding = false
                                    }
                                }
                                return true
                            }

                            override fun onZoom(event: ZoomEvent?): Boolean = true
                        })

                        mapViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Centered Fixed Pin (map pans underneath it)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 36.dp), // offset so tip points at the exact center
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.wrapContentSize()
                ) {
                    Box(
                        modifier = Modifier
                            .shadow(8.dp, CircleShape)
                            .clip(CircleShape)
                            .background(customColors.accent)
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Pin Center",
                            tint = customColors.onAccent,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(customColors.accent)
                    )
                }
            }

            // Hint: "Pan map under pin to place"
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = customColors.accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pan map under pin to place",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Zoom and My-Location buttons inside map's bottom-right corner (never overlap bottom panel)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                // My Location Button
                FloatingActionButton(
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("map_my_location_btn"),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = customColors.accent,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "My Location", modifier = Modifier.size(20.dp))
                }

                // Zoom In Button
                FloatingActionButton(
                    onClick = { mapViewRef?.controller?.zoomIn() },
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(18.dp))
                }

                // Zoom Out Button
                FloatingActionButton(
                    onClick = { mapViewRef?.controller?.zoomOut() },
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(18.dp))
                }
            }

            // With keyboard open, show search results list over map
            if (isKeyboardOpen && (searchQuery.isNotBlank() || searchResults.isNotEmpty())) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    if (isSearching) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = customColors.accent)
                        }
                    } else if (searchResults.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No matching locations found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(searchResults) { result ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            currentCenterLat = result.latitude
                                            currentCenterLng = result.longitude
                                            currentAddress = result.displayName
                                            currentArea = result.area
                                            val p = GeoPoint(result.latitude, result.longitude)
                                            mapViewRef?.controller?.animateTo(p)
                                            searchQuery = ""
                                            searchResults = emptyList()
                                            keyboardController?.hide()
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = customColors.accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = result.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (result.area.isNotBlank()) {
                                            Text(
                                                text = result.area,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }

        // (d) Bottom panel as a normal Column item BELOW the map, not floating over it
        if (!isKeyboardOpen) {
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
                        .padding(horizontal = 16.dp)
                        .padding(
                            top = 12.dp,
                            bottom = bottomInsetPadding
                        )
                ) {
                    // Address Line (one line, ellipsized)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = customColors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isReverseGeocoding) "Locating address..." else currentAddress.ifBlank { "Selected Pin Location" },
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Coordinates in small text
                    Text(
                        text = String.format(Locale.US, "Lat: %.6f, Lng: %.6f", currentCenterLat, currentCenterLng),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 26.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 30-day lock warning banner if applicable
                    if (isLocked) {
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
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
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    // Confirm Button: 56dp tall and full width
                    Button(
                        onClick = {
                            if (isLocked) return@Button
                            if (locationChangedAt != null) {
                                showConfirmSaveDialog = true
                            } else {
                                onConfirmLocation(currentCenterLat, currentCenterLng, currentAddress, currentArea)
                            }
                        },
                        enabled = !isLocked,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = customColors.accent,
                            contentColor = customColors.onAccent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("confirm_location_picker_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Confirm This Location",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    // Urgent Request link if 30-day locked
                    if (isLocked && onRequestUrgentChange != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = {
                                urgentReason = ""
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
    }

    // Confirm dialog for 30-day location rule
    if (showConfirmSaveDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmSaveDialog = false },
            title = { Text("Confirm Store Location", fontWeight = FontWeight.Bold) },
            text = { Text("You can change your location only once every 30 days. Continue?") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmSaveDialog = false
                        onConfirmLocation(currentCenterLat, currentCenterLng, currentAddress, currentArea)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = customColors.accent, contentColor = customColors.onAccent)
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmSaveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Urgent Request Dialog
    if (showUrgentRequestDialog && onRequestUrgentChange != null) {
        AlertDialog(
            onDismissRequest = { if (!isSubmittingUrgent) showUrgentRequestDialog = false },
            title = { Text("Request Urgent Location Change", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Explain why you need to relocate your store before the 30-day period expires.")
                    OutlinedTextField(
                        value = urgentReason,
                        onValueChange = { urgentReason = it },
                        placeholder = { Text("Reason for relocation (e.g., renovated branch)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (urgentReason.trim().isNotBlank()) {
                            isSubmittingUrgent = true
                            onRequestUrgentChange(
                                currentAddress,
                                currentCenterLat,
                                currentCenterLng,
                                urgentReason.trim()
                            ) { success, _ ->
                                isSubmittingUrgent = false
                                showUrgentRequestDialog = false
                                if (success) {
                                    onBack()
                                }
                            }
                        }
                    },
                    enabled = urgentReason.trim().isNotBlank() && !isSubmittingUrgent,
                    colors = ButtonDefaults.buttonColors(containerColor = customColors.accent, contentColor = customColors.onAccent)
                ) {
                    Text(if (isSubmittingUrgent) "Submitting..." else "Submit Request")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUrgentRequestDialog = false },
                    enabled = !isSubmittingUrgent
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
