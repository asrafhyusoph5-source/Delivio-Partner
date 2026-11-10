package com.example.ui.components

import androidx.compose.runtime.Composable
import com.example.ui.screens.MapPickerScreen

/**
 * Delegating wrapper to unified MapPickerScreen to avoid duplicate versions.
 */
@Composable
fun FullScreenLocationPicker(
    initialLat: Double,
    initialLng: Double,
    initialAddress: String = "",
    onConfirmLocation: (lat: Double, lng: Double, address: String, areaName: String) -> Unit,
    onDismiss: () -> Unit
) {
    MapPickerScreen(
        title = "Set Location",
        initialLat = initialLat,
        initialLng = initialLng,
        initialAddress = initialAddress,
        onConfirmLocation = onConfirmLocation,
        onBack = onDismiss
    )
}
