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
    val longitude: Double,
    val area: String = "",
    val formattedAddress: String = ""
)

data class ReverseGeocodeResult(
    val formattedAddress: String = "",
    val area: String = "",
    val city: String = ""
)

object OsmRouteHelper {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    private fun JSONObject?.optTrimmedString(key: String): String? {
        if (this == null || isNull(key)) return null
        val value = optString(key, "").trim()
        return value.takeIf { it.isNotEmpty() }
    }

    /**
     * Builds an address string from geocoder parts in order:
     * 1. road and house number
     * 2. barangay or suburb or neighbourhood
     * 3. city or town or municipality
     * 4. province
     * Removes empty parts and repeated parts (ignoring capitals), keeps at most 4,
     * and falls back to a trimmed display name only when nothing else exists.
     */
    fun buildGeocoderAddress(
        road: String? = null,
        houseNumber: String? = null,
        barangay: String? = null,
        suburb: String? = null,
        neighbourhood: String? = null,
        city: String? = null,
        town: String? = null,
        municipality: String? = null,
        province: String? = null,
        displayName: String? = null
    ): String {
        val cleanRoad = road?.trim()?.takeIf { it.isNotEmpty() }
        val cleanHouseNumber = houseNumber?.trim()?.takeIf { it.isNotEmpty() }
        val streetPart = when {
            cleanHouseNumber != null && cleanRoad != null -> {
                if (cleanHouseNumber.equals(cleanRoad, ignoreCase = true)) cleanRoad
                else "$cleanHouseNumber $cleanRoad"
            }
            cleanRoad != null -> cleanRoad
            cleanHouseNumber != null -> cleanHouseNumber
            else -> null
        }

        val localityPart = barangay?.trim()?.takeIf { it.isNotEmpty() }
            ?: suburb?.trim()?.takeIf { it.isNotEmpty() }
            ?: neighbourhood?.trim()?.takeIf { it.isNotEmpty() }

        val cityPart = city?.trim()?.takeIf { it.isNotEmpty() }
            ?: town?.trim()?.takeIf { it.isNotEmpty() }
            ?: municipality?.trim()?.takeIf { it.isNotEmpty() }

        val provincePart = province?.trim()?.takeIf { it.isNotEmpty() }

        val seen = HashSet<String>()
        val parts = listOfNotNull(streetPart, localityPart, cityPart, provincePart)
            .filter { seen.add(it.lowercase(java.util.Locale.ROOT)) }
            .take(4)

        return if (parts.isNotEmpty()) {
            parts.joinToString(", ")
        } else {
            displayName?.trim().orEmpty()
        }
    }

    fun formatGeocoderAddress(addressObj: JSONObject?, displayName: String?): String {
        return buildGeocoderAddress(
            road = addressObj.optTrimmedString("road"),
            houseNumber = addressObj.optTrimmedString("house_number"),
            barangay = addressObj.optTrimmedString("barangay"),
            suburb = addressObj.optTrimmedString("suburb"),
            neighbourhood = addressObj.optTrimmedString("neighbourhood"),
            city = addressObj.optTrimmedString("city"),
            town = addressObj.optTrimmedString("town"),
            municipality = addressObj.optTrimmedString("municipality"),
            province = addressObj.optTrimmedString("province"),
            displayName = displayName
        )
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

    suspend fun searchNominatim(query: String, countryCode: String = "ph"): List<NominatimResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=6&countrycodes=$countryCode&addressdetails=1"
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
                val displayName = if (item.isNull("display_name")) "" else item.optString("display_name", "")
                val lat = item.optDouble("lat", 0.0)
                val lon = item.optDouble("lon", 0.0)
                val addrObj = item.optJSONObject("address")
                val areaName = addrObj.optTrimmedString("barangay")
                    ?: addrObj.optTrimmedString("suburb")
                    ?: addrObj.optTrimmedString("neighbourhood")
                    ?: addrObj.optTrimmedString("quarter")
                    ?: addrObj.optTrimmedString("residential")
                    ?: addrObj.optTrimmedString("city_district")
                    ?: addrObj.optTrimmedString("city")
                    ?: addrObj.optTrimmedString("town")
                    ?: addrObj.optTrimmedString("municipality")
                    ?: ""
                val formatted = formatGeocoderAddress(addrObj, displayName)

                if (lat != 0.0 && lon != 0.0) {
                    results.add(NominatimResult(displayName.trim(), lat, lon, areaName, formatted))
                }
            }
            results
        } catch (e: Exception) {
            Log.w("OsmRouteHelper", "Nominatim search failed: ${e.localizedMessage}")
            emptyList()
        }
    }

    suspend fun reverseGeocode(lat: Double, lng: Double): ReverseGeocodeResult = withContext(Dispatchers.IO) {
        if (lat == 0.0 && lng == 0.0) return@withContext ReverseGeocodeResult()
        try {
            val url = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lng&format=json&addressdetails=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "DelivioPartner/1.0 (Android)")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext ReverseGeocodeResult(
                    formattedAddress = "Location (%.5f, %.5f)".format(java.util.Locale.US, lat, lng),
                    area = "Custom Pin"
                )
            }

            val body = response.body?.string() ?: return@withContext ReverseGeocodeResult(
                formattedAddress = "Location (%.5f, %.5f)".format(java.util.Locale.US, lat, lng),
                area = "Custom Pin"
            )
            val json = JSONObject(body)
            val displayName = if (json.isNull("display_name")) "" else json.optString("display_name", "")
            val addrObj = json.optJSONObject("address")

            val barangay = addrObj.optTrimmedString("barangay")
            val suburb = addrObj.optTrimmedString("suburb")
            val neighbourhood = addrObj.optTrimmedString("neighbourhood")
            val quarter = addrObj.optTrimmedString("quarter")
            val residential = addrObj.optTrimmedString("residential")
            val cityDistrict = addrObj.optTrimmedString("city_district")
            val city = addrObj.optTrimmedString("city")
                ?: addrObj.optTrimmedString("town")
                ?: addrObj.optTrimmedString("municipality")
                ?: ""

            val area = barangay ?: suburb ?: neighbourhood ?: quarter ?: residential ?: cityDistrict
                ?: city.ifBlank { "Selected Area" }

            val formatted = formatGeocoderAddress(addrObj, displayName)
                .ifBlank { "Location (%.5f, %.5f)".format(java.util.Locale.US, lat, lng) }

            ReverseGeocodeResult(
                formattedAddress = formatted,
                area = area,
                city = city
            )
        } catch (e: Exception) {
            Log.w("OsmRouteHelper", "Reverse geocode failed: ${e.localizedMessage}")
            ReverseGeocodeResult(
                formattedAddress = "Location (%.5f, %.5f)".format(java.util.Locale.US, lat, lng),
                area = "Pinned Location"
            )
        }
    }
}
