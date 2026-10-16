package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.MerchantApplication
import com.example.ui.components.OsmMapView
import com.example.ui.theme.LocalDelivioColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterMerchantScreen(
    onBackToLogin: () -> Unit,
    onSubmit: (application: MerchantApplication, pass: String) -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    val customColors = LocalDelivioColors.current

    var businessName by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var selectedBusinessType by remember { mutableStateOf("food") } // "food" | "convenience"
    var showTypeConfirmationDialog by remember { mutableStateOf(false) }

    var address by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("Metro Manila") }
    var lat by remember { mutableDoubleStateOf(14.5492) }
    var lng by remember { mutableDoubleStateOf(121.0503) }

    var logoUrl by remember { mutableStateOf("") }
    var storePhotoUrl by remember { mutableStateOf("") }

    var permitUrl by remember { mutableStateOf("") }
    var dtiSecUrl by remember { mutableStateOf("") }
    var barangayUrl by remember { mutableStateOf("") }
    var validIdUrl by remember { mutableStateOf("") }

    var termsAccepted by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    fun validateAndProceed() {
        localError = null
        if (businessName.isBlank() || ownerName.isBlank() || phone.isBlank() || email.isBlank() || password.isBlank()) {
            localError = "Please fill in all basic contact & credentials fields."
            return
        }
        if (password.length < 6) {
            localError = "Password must be at least 6 characters."
            return
        }
        if (password != confirmPassword) {
            localError = "Passwords do not match."
            return
        }
        if (address.isBlank()) {
            localError = "Please enter your store address."
            return
        }
        if (!termsAccepted) {
            localError = "Please agree to the Delivio Partner Merchant Terms & Conditions."
            return
        }
        // Show confirmation for one-time business type choice
        showTypeConfirmationDialog = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Register as a Merchant",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackToLogin) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(
                text = "Partner Application",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold)
            )
            Text(
                text = "Join the Delivio food & convenience delivery network. Applications are reviewed and approved by the admin team.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            // Errors
            val displayError = localError ?: errorMessage
            if (displayError != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = displayError,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            // Section 1: Business Identity
            Text(
                "1. Business & Contact",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = customColors.accent
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = businessName,
                onValueChange = { businessName = it },
                label = { Text("Business / Store Name *") },
                placeholder = { Text("e.g. Delivio Burger Co.") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reg_business_name")
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = ownerName,
                onValueChange = { ownerName = it },
                label = { Text("Owner Full Name *") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reg_owner_name")
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone Number *") },
                placeholder = { Text("+63 9XX XXX XXXX") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reg_phone")
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address (Login Account) *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reg_email")
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Account Password *") },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reg_password")
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirm Password *") },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reg_confirm_password")
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section 2: Business Type (One-time choice)
            Text(
                "2. Business Type (One-Time Choice)",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = customColors.accent
            )
            Text(
                "Select whether your store operates as a Food/Dining restaurant or a Convenience/Grocery mart. Note: This cannot be changed later without admin approval.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedBusinessType = "food" }
                        .border(
                            width = if (selectedBusinessType == "food") 2.dp else 1.dp,
                            color = if (selectedBusinessType == "food") customColors.accent else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(14.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedBusinessType == "food") customColors.accentTint else MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🍔", fontSize = 28.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Food & Dining", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Restaurant / Cafe", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedBusinessType = "convenience" }
                        .border(
                            width = if (selectedBusinessType == "convenience") 2.dp else 1.dp,
                            color = if (selectedBusinessType == "convenience") customColors.accent else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(14.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedBusinessType == "convenience") customColors.accentTint else MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🏪", fontSize = 28.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Convenience", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Mart / Grocery", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 3: OpenStreetMap Location Pin
            Text(
                "3. Store Location (OpenStreetMap)",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = customColors.accent
            )
            Text(
                "Tap or drag on the OpenStreetMap view to set your exact store GPS pin coordinates for rider pickups.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp)
            ) {
                OsmMapView(
                    latitude = lat,
                    longitude = lng,
                    isPicker = true,
                    markerTitle = businessName.ifBlank { "Store Location" },
                    onLocationPicked = { pickedLat, pickedLng ->
                        lat = pickedLat
                        lng = pickedLng
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Text(
                text = "Pinned GPS: %.5f, %.5f (Tap map to move pin)".format(lat, lng),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp)
            )

            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Complete Address (Street, Building, Unit) *") },
                placeholder = { Text("e.g. Unit 102, Plaza Center, 88 Rizal Ave") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reg_address")
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = city,
                onValueChange = { city = it },
                label = { Text("City / Municipality *") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section 4: Store Branding URLs
            Text(
                "4. Store Visuals (URLs)",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = customColors.accent
            )
            Text(
                "Provide publicly accessible image URLs for your logo and storefront photo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            OutlinedTextField(
                value = logoUrl,
                onValueChange = { logoUrl = it },
                label = { Text("Logo Image URL") },
                placeholder = { Text("https://example.com/logo.png") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            if (logoUrl.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = "Logo Preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = storePhotoUrl,
                onValueChange = { storePhotoUrl = it },
                label = { Text("Storefront Photo URL") },
                placeholder = { Text("https://example.com/store.jpg") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            if (storePhotoUrl.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = storePhotoUrl,
                        contentDescription = "Storefront Preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 5: Verification Documents URLs
            Text(
                "5. Verification Documents (URLs)",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = customColors.accent
            )
            Text(
                "Provide direct image URLs for verification documents. Admin uses these for merchant approval.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            OutlinedTextField(
                value = permitUrl,
                onValueChange = { permitUrl = it },
                label = { Text("Mayor's / Business Permit URL") },
                placeholder = { Text("https://...") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = dtiSecUrl,
                onValueChange = { dtiSecUrl = it },
                label = { Text("DTI or SEC Registration URL") },
                placeholder = { Text("https://...") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = barangayUrl,
                onValueChange = { barangayUrl = it },
                label = { Text("Barangay Clearance URL") },
                placeholder = { Text("https://...") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = validIdUrl,
                onValueChange = { validIdUrl = it },
                label = { Text("Owner Valid Gov ID URL") },
                placeholder = { Text("https://...") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Terms checkbox
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { termsAccepted = !termsAccepted }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = termsAccepted,
                    onCheckedChange = { termsAccepted = it },
                    colors = CheckboxDefaults.colors(checkedColor = customColors.accent)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "I accept the Delivio Partner Merchant Agreement and confirm that all submitted business documents are accurate and authentic.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Submit Button
            Button(
                onClick = { validateAndProceed() },
                enabled = !isLoading,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = customColors.accent,
                    contentColor = customColors.onAccent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("submit_registration_btn")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = customColors.onAccent,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "Submit Merchant Application",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    // One-Time Business Type Confirmation Dialog
    if (showTypeConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showTypeConfirmationDialog = false },
            title = {
                Text("Confirm Business Type", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "You have selected: ${if (selectedBusinessType == "food") "Food & Dining" else "Convenience & Mart"}.\n\nThis cannot be changed later without admin approval. Do you want to submit your application now?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTypeConfirmationDialog = false
                        val application = MerchantApplication(
                            businessName = businessName.trim(),
                            ownerName = ownerName.trim(),
                            phone = phone.trim(),
                            email = email.trim(),
                            businessType = selectedBusinessType,
                            address = address.trim(),
                            lat = lat,
                            lng = lng,
                            cityId = city.trim(),
                            logoUrl = logoUrl.trim(),
                            storePhotoUrl = storePhotoUrl.trim(),
                            permitUrl = permitUrl.trim(),
                            dtiSecUrl = dtiSecUrl.trim(),
                            barangayUrl = barangayUrl.trim(),
                            validIdUrl = validIdUrl.trim(),
                            status = "Pending"
                        )
                        onSubmit(application, password.trim())
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = customColors.accent,
                        contentColor = customColors.onAccent
                    )
                ) {
                    Text("Confirm & Submit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTypeConfirmationDialog = false }) {
                    Text("Review Form")
                }
            }
        )
    }
}
