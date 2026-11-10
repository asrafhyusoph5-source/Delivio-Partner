package com.example.ui.screens

import androidx.compose.runtime.Composable
import com.example.data.model.AddressLocation

/**
 * Store location picker screen delegating to the unified MapPickerScreen.
 * Preserves 30-day location change rules and urgent relocation requests.
 */
@Composable
fun MapLocationScreen(
    currentLocation: AddressLocation,
    locationChangedAt: Long? = null,
    onSaveLocation: (AddressLocation, onResult: (Boolean, String?) -> Unit) -> Unit,
    onRequestUrgentChange: (address: String, lat: Double, lng: Double, reason: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onBack: () -> Unit
) {
    MapPickerScreen(
        title = "Store Location (Pickup Point)",
        initialLat = currentLocation.latitude,
        initialLng = currentLocation.longitude,
        initialAddress = currentLocation.street,
        locationChangedAt = locationChangedAt,
        onConfirmLocation = { lat, lng, address, _ ->
            val updated = currentLocation.copy(
                latitude = lat,
                longitude = lng,
                street = address
            )
            onSaveLocation(updated) { success, _ ->
                if (success) {
                    onBack()
                }
            }
        },
        onRequestUrgentChange = onRequestUrgentChange,
        onBack = onBack
    )
}
