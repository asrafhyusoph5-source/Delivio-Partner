package com.example.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.*

data class RouteResult(
    val distanceKm: Double,
    val waypoints: List<GeoPoint>,
    val isRoadRoute: Boolean
)

data class NominatimResult(
    val displayName: String,
    val latitude: Double,
    val longitude: Double
)

object OsmRouteHelper {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    fun calculateStraightLineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val dist = earthRadius * c
        return (round(dist * 10) / 10.0).coerceAtLeast(0.5)
    }

    suspend fun getRoute(
        pickupLat: Double,
        pickupLng: Double,
        dropoffLat: Double,
        dropoffLng: Double
    ): RouteResult = withContext(Dispatchers.IO) {
        val fallbackDistance = calculateStraightLineKm(pickupLat, pickupLng, dropoffLat, dropoffLng)
        val fallbackPoints = listOf(
            GeoPoint(pickupLat, pickupLng),
            GeoPoint(dropoffLat, dropoffLng)
        )

        try {
            val url = "https://router.project-osrm.org/route/v1/driving/$pickupLng,$pickupLat;$dropoffLng,$dropoffLat?overview=full&geometries=geojson"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "DelivioPartner/1.0 (Android)")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext RouteResult(fallbackDistance, fallbackPoints, isRoadRoute = false)
            }

            val body = response.body?.string() ?: return@withContext RouteResult(fallbackDistance, fallbackPoints, isRoadRoute = false)
            val json = JSONObject(body)
            val code = json.optString("code")
            if (code != "Ok") {
                return@withContext RouteResult(fallbackDistance, fallbackPoints, isRoadRoute = false)
            }

            val routes = json.getJSONArray("routes")
            if (routes.length() == 0) {
                return@withContext RouteResult(fallbackDistance, fallbackPoints, isRoadRoute = false)
            }

            val firstRoute = routes.getJSONObject(0)
            val distanceMeters = firstRoute.optDouble("distance", 0.0)
            val roadDistanceKm = if (distanceMeters > 0) {
                round((distanceMeters / 1000.0) * 10) / 10.0
            } else {
                fallbackDistance
            }

            val geometry = firstRoute.getJSONObject("geometry")
            val coordinates = geometry.getJSONArray("coordinates")
            val points = mutableListOf<GeoPoint>()
            for (i in 0 until coordinates.length()) {
                val coord = coordinates.getJSONArray(i)
                val lng = coord.getDouble(0)
                val lat = coord.getDouble(1)
                points.add(GeoPoint(lat, lng))
            }

            if (points.isEmpty()) {
                RouteResult(roadDistanceKm, fallbackPoints, isRoadRoute = true)
            } else {
                RouteResult(roadDistanceKm, points, isRoadRoute = true)
            }
        } catch (e: Exception) {
            Log.w("OsmRouteHelper", "OSRM request failed, using straight-line fallback: ${e.localizedMessage}")
            RouteResult(fallbackDistance, fallbackPoints, isRoadRoute = false)
        }
    }

    suspend fun searchNominatim(query: String): List<NominatimResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=5"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "DelivioPartner/1.0 (Android)")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body?.string() ?: return@withContext emptyList()
            val array = JSONArray(body)
            val results = mutableListOf<NominatimResult>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val displayName = item.optString("display_name", "")
                val lat = item.optDouble("lat", 0.0)
                val lon = item.optDouble("lon", 0.0)
                if (lat != 0.0 && lon != 0.0) {
                    results.add(NominatimResult(displayName, lat, lon))
                }
            }
            results
        } catch (e: Exception) {
            Log.w("OsmRouteHelper", "Nominatim search failed: ${e.localizedMessage}")
            emptyList()
        }
    }
}
