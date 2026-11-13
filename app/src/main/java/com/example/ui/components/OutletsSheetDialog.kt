package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.MerchantSettings
import com.example.ui.theme.DelivioYellow
import com.example.ui.theme.StatusClosedRed
import com.example.ui.theme.StatusOpenGreen
import com.example.ui.theme.StatusWarningAmber
import com.example.util.BusinessHoursHelper
import com.example.util.OutletsUtils
import com.example.viewmodel.OutletsSheetMode

@Composable
fun OutletsSelectorSheet(
    mode: OutletsSheetMode,
    outlets: List<MerchantSettings>,
    activeMerchantId: String,
    selectedFilterIds: Set<String>,
    newOrderCountByOutlet: Map<String, Int>,
    currentTime: Long,
    onDismiss: () -> Unit,
    onApplySingle: (String) -> Unit,
    onApplyMulti: (Set<String>) -> Unit
) {
    val isSingleMode = mode == OutletsSheetMode.SINGLE_SWITCH
    var searchQuery by remember { mutableStateOf("") }
    var draftSingleId by remember(activeMerchantId, mode) { mutableStateOf(activeMerchantId) }
    var draftMultiIds by remember(selectedFilterIds, activeMerchantId, mode) {
        mutableStateOf(
            if (selectedFilterIds.isNotEmpty()) selectedFilterIds
            else if (activeMerchantId.isNotBlank()) setOf(activeMerchantId)
            else emptySet()
        )
    }

    val activeOutlet = remember(outlets, activeMerchantId) {
        outlets.find { it.merchantId == activeMerchantId }
    }

    val filteredOutlets = remember(outlets, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isBlank()) outlets
        else outlets.filter { o ->
            o.displayName.lowercase().contains(q) ||
                o.branchName.lowercase().contains(q) ||
                o.branchCode.lowercase().contains(q)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.86f)
                .testTag("outlets_selector_sheet"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header: Close X, "Outlets", Reset
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("outlets_sheet_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Outlets"
                        )
                    }

                    Text(
                        text = "Outlets",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )

                    TextButton(
                        onClick = {
                            searchQuery = ""
                            if (isSingleMode) {
                                draftSingleId = activeMerchantId
                            } else {
                                draftMultiIds = if (activeMerchantId.isNotBlank()) setOf(activeMerchantId) else emptySet()
                            }
                        },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("outlets_sheet_reset_btn")
                    ) {
                        Text(
                            text = "Reset",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Subtitle
                val subtitleText = if (isSingleMode) {
                    val managingLabel = activeOutlet?.branchName?.ifBlank { activeOutlet.displayName }
                        ?: activeOutlet?.displayName
                        ?: "Current Outlet"
                    "Currently managing: $managingLabel"
                } else {
                    "Currently selected ${draftMultiIds.size} out of ${outlets.size}"
                }
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .testTag("outlets_sheet_subtitle")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Search input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search branch name or code") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search outlets") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("outlets_search_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Outlet list
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredOutlets, key = { it.merchantId }) { outlet ->
                        val isSelected = if (isSingleMode) {
                            draftSingleId == outlet.merchantId
                        } else {
                            outlet.merchantId in draftMultiIds
                        }
                        val isSuspended = !outlet.status.equals("active", ignoreCase = true)
                        val effectiveOpen = if (isSuspended) false else BusinessHoursHelper.isEffectiveOpen(outlet, currentTime)
                        val newCount = newOrderCountByOutlet[outlet.merchantId] ?: 0

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) DelivioYellow.copy(alpha = 0.14f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) DelivioYellow else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isSingleMode) {
                                        draftSingleId = outlet.merchantId
                                    } else {
                                        draftMultiIds = if (outlet.merchantId in draftMultiIds) {
                                            draftMultiIds - outlet.merchantId
                                        } else {
                                            draftMultiIds + outlet.merchantId
                                        }
                                    }
                                }
                                .testTag("outlet_row_${outlet.merchantId}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSingleMode) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { draftSingleId = outlet.merchantId },
                                        modifier = Modifier.testTag("outlet_radio_${outlet.merchantId}")
                                    )
                                } else {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            draftMultiIds = if (checked) draftMultiIds + outlet.merchantId else draftMultiIds - outlet.merchantId
                                        },
                                        modifier = Modifier.testTag("outlet_checkbox_${outlet.merchantId}")
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = outlet.displayName.ifBlank { "Outlet" },
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (outlet.branchCode.isNotBlank()) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.testTag("outlet_code_chip_${outlet.merchantId}")
                                            ) {
                                                Text(
                                                    text = outlet.branchCode,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val statusLabel = when {
                                            isSuspended -> "Suspended"
                                            effectiveOpen -> "Open"
                                            else -> "Closed"
                                        }
                                        val statusColor = when {
                                            isSuspended -> StatusWarningAmber
                                            effectiveOpen -> StatusOpenGreen
                                            else -> StatusClosedRed
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(statusColor.copy(alpha = 0.14f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(statusColor)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = statusLabel,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = statusColor
                                            )
                                        }

                                        if (newCount > 0) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.error,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.testTag("outlet_new_orders_badge_${outlet.merchantId}")
                                            ) {
                                                Text(
                                                    text = "$newCount new",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Apply CTA
                val applyEnabled = if (isSingleMode) draftSingleId.isNotBlank() else draftMultiIds.isNotEmpty()
                Button(
                    onClick = {
                        if (isSingleMode) {
                            onApplySingle(draftSingleId)
                        } else {
                            onApplyMulti(draftMultiIds)
                        }
                    },
                    enabled = applyEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DelivioYellow,
                        contentColor = Color(0xFF1A1A1A)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("outlets_sheet_apply_btn")
                ) {
                    Text(
                        text = if (isSingleMode) "Apply" else "Apply (${draftMultiIds.size})",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
fun BranchSetupDialog(
    outlet: MerchantSettings,
    existingOutlets: List<MerchantSettings>,
    onSave: (branchName: String, branchCode: String, onDone: (Boolean, String?) -> Unit) -> Unit
) {
    var branchName by remember(outlet.merchantId) { mutableStateOf(outlet.branchName) }
    var branchCode by remember(outlet.merchantId) { mutableStateOf(OutletsUtils.sanitizeBranchCodeInput(outlet.branchCode)) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 520.dp)
                .testTag("branch_setup_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = DelivioYellow.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Storefront,
                                contentDescription = null,
                                tint = Color(0xFF1A1A1A)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Set Up Branch Details",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                        )
                        Text(
                            text = outlet.storeName.ifBlank { outlet.chainName },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "Enter this outlet's branch name and unique short code. Once saved, only an admin can change them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = branchName,
                    onValueChange = {
                        if (it.length <= 40) branchName = it
                        errorMessage = null
                    },
                    label = { Text("Branch name *") },
                    placeholder = { Text("e.g. Palawan Rizal") },
                    singleLine = true,
                    supportingText = { Text("${branchName.trim().length}/40 characters") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("branch_setup_name_input")
                )

                OutlinedTextField(
                    value = branchCode,
                    onValueChange = {
                        branchCode = OutletsUtils.sanitizeBranchCodeInput(it)
                        errorMessage = null
                    },
                    label = { Text("Branch code *") },
                    placeholder = { Text("e.g. PRZ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    supportingText = { Text("2 to 10 characters, A-Z and 0-9, unique in this chain") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("branch_setup_code_input")
                )

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("branch_setup_error")
                    )
                }

                Button(
                    onClick = {
                        val cleanName = branchName.trim()
                        val cleanCode = OutletsUtils.sanitizeBranchCodeInput(branchCode)
                        if (!OutletsUtils.isValidBranchName(cleanName)) {
                            errorMessage = "Branch name must be 2 to 40 characters."
                            return@Button
                        }
                        if (!OutletsUtils.isValidBranchCode(cleanCode)) {
                            errorMessage = "Branch code must be 2 to 10 characters (A-Z, 0-9)."
                            return@Button
                        }
                        val dup = existingOutlets.any {
                            it.merchantId != outlet.merchantId &&
                                it.chainId == outlet.chainId &&
                                it.branchCode.equals(cleanCode, ignoreCase = true)
                        }
                        if (dup) {
                            errorMessage = "Branch code \"$cleanCode\" is already used in this chain."
                            return@Button
                        }
                        isSaving = true
                        onSave(cleanName, cleanCode) { success, err ->
                            isSaving = false
                            if (!success) {
                                errorMessage = err ?: "Could not save branch details"
                            }
                        }
                    },
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DelivioYellow,
                        contentColor = Color(0xFF1A1A1A)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("branch_setup_save_btn")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF1A1A1A)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Save & Continue", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}
