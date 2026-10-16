package com.example.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

@Composable
fun OsmMapView(
    latitude: Double,
    longitude: Double,
    zoomLevel: Double = 16.0,
    isInteractive: Boolean = true,
    isPicker: Boolean = false,
    markerTitle: String = "Location",
    onLocationPicked: ((lat: Double, lng: Double) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val prefs = context.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE)
        Configuration.getInstance().load(context, prefs)
        Configuration.getInstance().userAgentValue = context.packageName
        onDispose { }
    }

    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(isInteractive)
                controller.setZoom(zoomLevel)
                val startPoint = GeoPoint(latitude, longitude)
                controller.setCenter(startPoint)

                val marker = Marker(this).apply {
                    position = startPoint
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = markerTitle
                }
                overlays.add(marker)

                if (isPicker && onLocationPicked != null) {
                    val overlayEvents = MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            marker.position = p
                            invalidate()
                            onLocationPicked(p.latitude, p.longitude)
                            return true
                        }

                        override fun longPressHelper(p: GeoPoint): Boolean {
                            marker.position = p
                            invalidate()
                            onLocationPicked(p.latitude, p.longitude)
                            return true
                        }
                    })
                    overlays.add(0, overlayEvents)
                }
            }
        },
        update = { mapView ->
            val point = GeoPoint(latitude, longitude)
            val marker = mapView.overlays.filterIsInstance<Marker>().firstOrNull()
            marker?.position = point
            marker?.title = markerTitle
            mapView.invalidate()
        },
        modifier = modifier
    )
}
