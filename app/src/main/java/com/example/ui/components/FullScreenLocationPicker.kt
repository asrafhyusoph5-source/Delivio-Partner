package com.example.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

@Composable
fun FullScreenLocationPicker(
    initialLat: Double,
    initialLng: Double,
    initialAddress: String = "",
    onConfirmLocation: (lat: Double, lng: Double, address: String, areaName: String) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    val context = LocalContext.current
    val customColors = LocalDelivioColors.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<NominatimResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    val startLat = if (initialLat != 0.0) initialLat else 14.5547
    val startLng = if (initialLng != 0.0) initialLng else 121.0509

    var currentCenterLat by remember { mutableDoubleStateOf(startLat) }
    var currentCenterLng by remember { mutableDoubleStateOf(startLng) }
    var reverseResult by remember {
        mutableStateOf(
            ReverseGeocodeResult(
                formattedAddress = initialAddress.ifBlank { "Pin location" },
                area = "Selected Area"
            )
        )
    }
    var isReverseGeocoding by remember { mutableStateOf(false) }
    var geocodeJob by remember { mutableStateOf<Job?>(null) }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    // Initial reverse geocode
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("osmdroid_picker", Context.MODE_PRIVATE)
        Configuration.getInstance().load(context, prefs)
        Configuration.getInstance().userAgentValue = context.packageName

        if (initialAddress.isBlank()) {
            isReverseGeocoding = true
            val res = OsmRouteHelper.reverseGeocode(startLat, startLng)
            reverseResult = res
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
            } catch (e: Exception) {
                null
            }
            if (lastLoc != null) {
                val p = GeoPoint(lastLoc.latitude, lastLoc.longitude)
                mapViewRef?.controller?.animateTo(p)
            } else {
                val p = GeoPoint(14.5547, 121.0509)
                mapViewRef?.controller?.animateTo(p)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 1. Full Screen Map View
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        isTilesScaledToDpi = true
                        controller.setZoom(17.0)
                        val pt = GeoPoint(startLat, startLng)
                        controller.setCenter(pt)

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
                                        reverseResult = res
                                        isReverseGeocoding = false
                                    }
                                }
                                return true
                            }

                            override fun onZoom(event: ZoomEvent?): Boolean {
                                return true
                            }
                        })

                        mapViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // 2. Centered Fixed Pin (map pans beneath it)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 36.dp), // offset so the tip of the pin points precisely at the screen center
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
                            contentDescription = "Drop-off Pin",
                            tint = customColors.onAccent,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    // Pin stem shadow/dot
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.35f))
                    )
                }
            }

            // 3. Top Floating Search & Back Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag("picker_back_btn")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { query ->
                                searchQuery = query
                                searchJob?.cancel()
                                if (query.isNotBlank()) {
                                    searchJob = coroutineScope.launch {
                                        delay(350L) // Debounced Nominatim search
                                        isSearching = true
                                        searchResults = OsmRouteHelper.searchNominatim(query, countryCode = "ph")
                                        isSearching = false
                                    }
                                } else {
                                    searchResults = emptyList()
                                }
                            },
                            placeholder = { Text("Search PH address or landmark...", fontSize = 13.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            trailingIcon = {
                                if (isSearching) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        searchResults = emptyList()
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("picker_search_input")
                        )
                    }
                }

                // Dropdown search results
                if (searchResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(searchResults) { res ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            searchQuery = ""
                                            searchResults = emptyList()
                                            val p = GeoPoint(res.latitude, res.longitude)
                                            mapViewRef?.controller?.animateTo(p)
                                            currentCenterLat = res.latitude
                                            currentCenterLng = res.longitude
                                            reverseResult = ReverseGeocodeResult(
                                                formattedAddress = res.displayName,
                                                area = res.area.ifBlank { "Selected Area" }
                                            )
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = null,
                                        tint = customColors.accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = res.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (res.area.isNotBlank()) {
                                            Text(
                                                text = res.area,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                            }
                        }
                    }
                }
            }

            // 4. Floating Map Controls (+ / - Zoom and My-Location)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Zoom In
                SmallFloatingActionButton(
                    onClick = { mapViewRef?.controller?.zoomIn() },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("zoom_in_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom In")
                }

                // Zoom Out
                SmallFloatingActionButton(
                    onClick = { mapViewRef?.controller?.zoomOut() },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("zoom_out_btn")
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
                }

                // My Location
                FloatingActionButton(
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    containerColor = customColors.accent,
                    contentColor = customColors.onAccent,
                    modifier = Modifier.testTag("my_location_btn")
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "My Location")
                }
            }

            // 5. Bottom Card with Address and Confirm Button
            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 16.dp,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(customColors.accentTint),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PinDrop,
                                contentDescription = null,
                                tint = customColors.accent
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isReverseGeocoding) "Locating address..." else reverseResult.area.ifBlank { "Delivery Pin" },
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                if (isReverseGeocoding) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                }
                            }
                            Text(
                                text = reverseResult.formattedAddress.ifBlank { "Drag map to position drop-off point" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Lat: %.5f  •  Lng: %.5f".format(currentCenterLat, currentCenterLng),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val addr = reverseResult.formattedAddress.ifBlank {
                                "Pinned Location (%.5f, %.5f)".format(currentCenterLat, currentCenterLng)
                            }
                            val area = reverseResult.area.ifBlank { "Customer Drop-off" }
                            onConfirmLocation(currentCenterLat, currentCenterLng, addr, area)
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = customColors.accent,
                            contentColor = customColors.onAccent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("confirm_this_location_btn")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Confirm This Location", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
