package com.example.ui.components

import androidx.compose.runtime.Composable
import com.example.data.model.MerchantSettings
import com.example.ui.screens.RequestRiderScreen

fun isValidPhilippinePhone(phone: String): Boolean {
    val clean = phone.replace(Regex("[^0-9+]"), "")
    return (clean.startsWith("+639") && clean.length == 13) ||
            (clean.startsWith("639") && clean.length == 12) ||
            (clean.startsWith("09") && clean.length == 11) ||
            (clean.startsWith("9") && clean.length == 10)
}

@Composable
fun ManualRiderBookingSheet(
    settings: MerchantSettings,
    isSubmitting: Boolean = false,
    submissionError: String? = null,
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
    RequestRiderScreen(
        settings = settings,
        isSubmitting = isSubmitting,
        submissionError = submissionError,
        onBookRider = { name, phone, addr, area, lat, lng, what, relOrder, _, cod, note, dist, delFee, svcFee, _, _ ->
            onBookRider(name, phone, addr, area, lat, lng, what, relOrder, cod, note, dist, delFee, svcFee)
        },
        onOpenLocationPicker = onOpenLocationPicker,
        onDismiss = onDismiss
    )
}
