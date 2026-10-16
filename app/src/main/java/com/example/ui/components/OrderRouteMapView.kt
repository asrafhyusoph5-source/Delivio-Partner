package com.example.ui.components

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
fun OrderRouteMapView(
    pickupLat: Double,
    pickupLng: Double,
    dropoffLat: Double,
    dropoffLng: Double,
    routePoints: List<GeoPoint> = emptyList(),
    riderLat: Double? = null,
    riderLng: Double? = null,
    isDropoffEditable: Boolean = false,
    onDropoffMoved: ((lat: Double, lng: Double) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val prefs = context.getSharedPreferences("osmdroid_route_prefs", Context.MODE_PRIVATE)
        Configuration.getInstance().load(context, prefs)
        Configuration.getInstance().userAgentValue = context.packageName
        onDispose { }
    }

    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                isTilesScaledToDpi = true

                val pickupPoint = GeoPoint(pickupLat, pickupLng)
                val dropoffPoint = GeoPoint(dropoffLat, dropoffLng)

                // 1. Pickup Marker (Store)
                val pickupMarker = Marker(this).apply {
                    id = "pickup_marker"
                    position = pickupPoint
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "📍 Store Pickup"
                }
                overlays.add(pickupMarker)

                // 2. Dropoff Marker (Customer)
                val dropoffMarker = Marker(this).apply {
                    id = "dropoff_marker"
                    position = dropoffPoint
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "🏁 Customer Drop-off"
                    if (isDropoffEditable) {
                        isDraggable = true
                        setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                            override fun onMarkerDrag(marker: Marker?) {}
                            override fun onMarkerDragStart(marker: Marker?) {}
                            override fun onMarkerDragEnd(marker: Marker?) {
                                marker?.position?.let { p ->
                                    onDropoffMoved?.invoke(p.latitude, p.longitude)
                                }
                            }
                        })
                    }
                }
                overlays.add(dropoffMarker)

                // 3. Route Polyline
                val polyline = Polyline(this).apply {
                    id = "route_polyline"
                    outlinePaint.color = AndroidColor.parseColor("#4F46E5")
                    outlinePaint.strokeWidth = 10f
                    val pts = if (routePoints.isNotEmpty()) routePoints else listOf(pickupPoint, dropoffPoint)
                    setPoints(pts)
                }
                overlays.add(polyline)

                // 4. Rider Marker (if available)
                if (riderLat != null && riderLng != null && riderLat != 0.0 && riderLng != 0.0) {
                    val riderPoint = GeoPoint(riderLat, riderLng)
                    val riderMarker = Marker(this).apply {
                        id = "rider_marker"
                        position = riderPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "🛵 Rider Location"
                    }
                    overlays.add(riderMarker)
                }

                // 5. Map Click for Editable Dropoff
                if (isDropoffEditable && onDropoffMoved != null) {
                    val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            dropoffMarker.position = p
                            invalidate()
                            onDropoffMoved(p.latitude, p.longitude)
                            return true
                        }

                        override fun longPressHelper(p: GeoPoint): Boolean {
                            dropoffMarker.position = p
                            invalidate()
                            onDropoffMoved(p.latitude, p.longitude)
                            return true
                        }
                    })
                    overlays.add(0, eventsOverlay)
                }

                // Initial Center & Zoom
                post {
                    try {
                        val minLat = minOf(pickupLat, dropoffLat)
                        val maxLat = maxOf(pickupLat, dropoffLat)
                        val minLng = minOf(pickupLng, dropoffLng)
                        val maxLng = maxOf(pickupLng, dropoffLng)
                        val box = BoundingBox(maxLat + 0.005, maxLng + 0.005, minLat - 0.005, minLng - 0.005)
                        zoomToBoundingBox(box, true, 80)
                    } catch (e: Exception) {
                        controller.setZoom(15.0)
                        controller.setCenter(pickupPoint)
                    }
                }
            }
        },
        update = { mapView ->
            val pickupPoint = GeoPoint(pickupLat, pickupLng)
            val dropoffPoint = GeoPoint(dropoffLat, dropoffLng)

            val pMarker = mapView.overlays.filterIsInstance<Marker>().find { it.id == "pickup_marker" }
            pMarker?.position = pickupPoint

            val dMarker = mapView.overlays.filterIsInstance<Marker>().find { it.id == "dropoff_marker" }
            dMarker?.position = dropoffPoint

            val polyline = mapView.overlays.filterIsInstance<Polyline>().find { it.id == "route_polyline" }
            if (polyline != null) {
                val pts = if (routePoints.isNotEmpty()) routePoints else listOf(pickupPoint, dropoffPoint)
                polyline.setPoints(pts)
            }

            var rMarker = mapView.overlays.filterIsInstance<Marker>().find { it.id == "rider_marker" }
            if (riderLat != null && riderLng != null && riderLat != 0.0 && riderLng != 0.0) {
                val riderPoint = GeoPoint(riderLat, riderLng)
                if (rMarker == null) {
                    rMarker = Marker(mapView).apply {
                        id = "rider_marker"
                        position = riderPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "🛵 Rider Location"
                    }
                    mapView.overlays.add(rMarker)
                } else {
                    rMarker.position = riderPoint
                }
            } else if (rMarker != null) {
                mapView.overlays.remove(rMarker)
            }

            mapView.invalidate()
        },
        modifier = modifier.fillMaxSize()
    )
}
