package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MerchantApplication
import com.example.ui.theme.LocalDelivioColors

@Composable
fun ApplicationStatusScreen(
    application: MerchantApplication,
    onResubmit: (MerchantApplication) -> Unit,
    onLogout: () -> Unit
) {
    val customColors = LocalDelivioColors.current
    var isEditing by remember { mutableStateOf(false) }

    var businessName by remember { mutableStateOf(application.businessName) }
    var ownerName by remember { mutableStateOf(application.ownerName) }
    var phone by remember { mutableStateOf(application.phone) }
    var address by remember { mutableStateOf(application.address) }
    var logoUrl by remember { mutableStateOf(application.logoUrl) }
    var storePhotoUrl by remember { mutableStateOf(application.storePhotoUrl) }
    var permitUrl by remember { mutableStateOf(application.permitUrl) }
    var dtiSecUrl by remember { mutableStateOf(application.dtiSecUrl) }
    var barangayUrl by remember { mutableStateOf(application.barangayUrl) }
    var validIdUrl by remember { mutableStateOf(application.validIdUrl) }

    val status = application.status.ifBlank { "Pending" }

    val (badgeBg, badgeFg, iconEmoji, statusTitle, statusSubtitle) = when (status.lowercase()) {
        "pending" -> Tuple5(
            Color(0xFFFEF3C7),
            Color(0xFF92400E),
            "⏳",
            "Application Submitted",
            "Wait for admin approval. Our onboarding team is reviewing your documents."
        )
        "under review" -> Tuple5(
            Color(0xFFDBEAFE),
            Color(0xFF1E40AF),
            "🔍",
            "Under Review",
            "Your merchant application is currently being verified by Delivio administrators."
        )
        "needs info" -> Tuple5(
            Color(0xFFFFEDD5),
            Color(0xFFC2410C),
            "📝",
            "Additional Info Required",
            "The admin team has requested revisions or additional details for your store."
        )
        "rejected" -> Tuple5(
            Color(0xFFFEE2E2),
            Color(0xFF991B1B),
            "❌",
            "Application Rejected",
            "Your application could not be approved at this time."
        )
        else -> Tuple5(
            Color(0xFFF3F4F6),
            Color(0xFF374151),
            "📋",
            status,
            "Application status: $status"
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Icon circle
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Text(iconEmoji, fontSize = 36.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = statusTitle,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold)
            )

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = badgeBg,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
            ) {
                Text(
                    text = "STATUS: ${status.uppercase()}",
                    color = badgeFg,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            Text(
                text = statusSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // Rejection reason or admin notes if present
            if (!application.rejectionReason.isNullOrBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Reason for Rejection:",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = application.rejectionReason,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            if (!application.adminNotes.isNullOrBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEDD5)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Admin Review Notes:",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC2410C),
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = application.adminNotes,
                            color = Color(0xFF9A3412),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Application Details Summary Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Application Summary", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    DetailRow("Store Name", application.businessName)
                    DetailRow("Owner Name", application.ownerName)
                    DetailRow("Email", application.email)
                    DetailRow("Phone", application.phone)
                    DetailRow("Business Type", if (application.businessType == "food") "Food & Dining" else "Convenience")
                    DetailRow("Address", application.address)
                }
            }

            // If "Needs Info" or "Rejected", provide inline resubmit form
            if (status.equals("needs info", ignoreCase = true) || isEditing) {
                Spacer(modifier = Modifier.height(20.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Update & Resubmit Form", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = businessName,
                            onValueChange = { businessName = it },
                            label = { Text("Business Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Phone") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("Address") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = permitUrl,
                            onValueChange = { permitUrl = it },
                            label = { Text("Business Permit URL") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = dtiSecUrl,
                            onValueChange = { dtiSecUrl = it },
                            label = { Text("DTI / SEC URL") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                val updated = application.copy(
                                    businessName = businessName,
                                    phone = phone,
                                    address = address,
                                    permitUrl = permitUrl,
                                    dtiSecUrl = dtiSecUrl,
                                    logoUrl = logoUrl,
                                    storePhotoUrl = storePhotoUrl
                                )
                                onResubmit(updated)
                                isEditing = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = customColors.accent,
                                contentColor = customColors.onAccent
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Resubmit Application", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (status.equals("rejected", ignoreCase = true)) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { isEditing = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Edit & Resubmit Application")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Logout Button
            OutlinedButton(
                onClick = onLogout,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Out / Use Different Account")
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

private data class Tuple5<A, B, C, D, E>(
    val a: A,
    val b: B,
    val c: C,
    val d: D,
    val e: E
)
