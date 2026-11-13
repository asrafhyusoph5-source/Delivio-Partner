package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.DelivioYellow
import com.example.ui.theme.StatusOpenGreen
import com.example.util.MenuSyncEngine
import com.example.util.MenuSyncResult
import com.example.util.OutletMenuSnapshot
import com.example.util.SyncField
import com.example.util.SyncPlanPreview
import com.example.util.SyncResolution
import com.example.viewmodel.MenuSyncDialogMode

@Composable
fun MenuSyncDialog(
    mode: MenuSyncDialogMode,
    activeMerchantId: String,
    snapshotsByMerchantId: Map<String, OutletMenuSnapshot>,
    isLoadingSnapshots: Boolean,
    isRunning: Boolean,
    progress: Pair<Int, Int>,
    result: MenuSyncResult?,
    onExecutePush: (source: OutletMenuSnapshot, targets: List<OutletMenuSnapshot>) -> Unit,
    onExecutePull: (target: OutletMenuSnapshot, source: OutletMenuSnapshot) -> Unit,
    onExecuteSync: (
        mine: OutletMenuSnapshot,
        theirs: OutletMenuSnapshot,
        fieldsToSync: Set<SyncField>,
        plan: SyncPlanPreview,
        resolutions: Map<String, SyncResolution>
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentSnapshot = snapshotsByMerchantId[activeMerchantId]
    val otherSnapshots = remember(snapshotsByMerchantId, activeMerchantId) {
        snapshotsByMerchantId.values
            .filter { it.merchantId != activeMerchantId }
            .sortedBy { it.displayName.lowercase() }
    }

    val titleText = when (mode) {
        MenuSyncDialogMode.PUSH -> "Push Menu"
        MenuSyncDialogMode.PULL -> "Pull Menu"
        MenuSyncDialogMode.SYNC -> "Sync Menu"
    }

    Dialog(
        onDismissRequest = { if (!isRunning) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isRunning,
            dismissOnClickOutside = !isRunning
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .testTag("menu_sync_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                    )
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isRunning,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("menu_sync_close_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                when {
                    isLoadingSnapshots -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = DelivioYellow)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Loading outlet menus...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    isRunning -> {
                        val (done, total) = progress
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth(0.8f)
                            ) {
                                Text(
                                    text = "Updating outlet menus...",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                if (total > 0) {
                                    LinearProgressIndicator(
                                        progress = { (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                                        color = DelivioYellow,
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Batch $done of $total",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    LinearProgressIndicator(color = DelivioYellow, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }

                    result != null -> {
                        SyncResultView(
                            result = result,
                            context = context,
                            onDone = onDismiss
                        )
                    }

                    currentSnapshot == null -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Unable to load current outlet menu.", color = MaterialTheme.colorScheme.error)
                        }
                    }

                    mode == MenuSyncDialogMode.PUSH -> {
                        PushMenuFlowContent(
                            source = currentSnapshot,
                            others = otherSnapshots,
                            onExecutePush = onExecutePush
                        )
                    }

                    mode == MenuSyncDialogMode.PULL -> {
                        PullMenuFlowContent(
                            target = currentSnapshot,
                            others = otherSnapshots,
                            onExecutePull = onExecutePull
                        )
                    }

                    mode == MenuSyncDialogMode.SYNC -> {
                        SyncMenuFlowContent(
                            mine = currentSnapshot,
                            others = otherSnapshots,
                            onExecuteSync = onExecuteSync
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PushMenuFlowContent(
    source: OutletMenuSnapshot,
    others: List<OutletMenuSnapshot>,
    onExecutePush: (OutletMenuSnapshot, List<OutletMenuSnapshot>) -> Unit
) {
    var selectedTargetIds by remember(others) {
        mutableStateOf(others.filter { it.isEligibleTargetFor(source) }.map { it.merchantId }.toSet())
    }
    var showConfirm by remember { mutableStateOf(false) }

    val selectedTargets = others.filter { it.merchantId in selectedTargetIds && it.isEligibleTargetFor(source) }
    val previews = selectedTargets.map { MenuSyncEngine.planMissingItemsCopy(source, it) }
    val totalToCopy = previews.sumOf { it.itemsToCopy.size }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Push products from ${source.displayName} to other outlets. Existing items with matching names in target outlets are kept unchanged.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(others, key = { it.merchantId }) { cand ->
                val eligible = cand.isEligibleTargetFor(source)
                val disabledReason = when {
                    cand.isSuspended -> "Suspended"
                    cand.businessType != source.businessType -> "Different shop type"
                    else -> null
                }
                val preview = if (eligible) MenuSyncEngine.planMissingItemsCopy(source, cand) else null
                val checked = cand.merchantId in selectedTargetIds && eligible

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (checked) DelivioYellow.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    border = BorderStroke(1.dp, if (checked) DelivioYellow else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (eligible) Modifier.clickable {
                                selectedTargetIds = if (checked) selectedTargetIds - cand.merchantId else selectedTargetIds + cand.merchantId
                            } else Modifier
                        )
                        .testTag("push_target_${cand.merchantId}")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            enabled = eligible,
                            onCheckedChange = { isChecked ->
                                selectedTargetIds = if (isChecked) selectedTargetIds + cand.merchantId else selectedTargetIds - cand.merchantId
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cand.displayName,
                                fontWeight = FontWeight.Bold,
                                color = if (eligible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = disabledReason
                                    ?: "${preview?.itemsToCopy?.size ?: 0} new items to copy · ${preview?.alreadyExistsCount ?: 0} already exist",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (disabledReason != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = { showConfirm = true },
            enabled = totalToCopy > 0 && selectedTargets.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = Color(0xFF1A1A1A)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("push_menu_confirm_trigger_btn")
        ) {
            Text(
                text = if (totalToCopy > 0) "Push $totalToCopy Missing Item(s)" else "Nothing Missing to Push",
                fontWeight = FontWeight.ExtraBold
            )
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Confirm Push Menu", fontWeight = FontWeight.Bold) },
            text = {
                Text("Copy $totalToCopy missing product(s) from ${source.displayName} into ${selectedTargets.size} outlet(s)? Copied items start in stock with no sales or stock counts.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirm = false
                        onExecutePush(source, selectedTargets)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = Color(0xFF1A1A1A)),
                    modifier = Modifier.testTag("push_menu_execute_btn")
                ) {
                    Text("Push Now", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PullMenuFlowContent(
    target: OutletMenuSnapshot,
    others: List<OutletMenuSnapshot>,
    onExecutePull: (OutletMenuSnapshot, OutletMenuSnapshot) -> Unit
) {
    val firstEligible = remember(others, target) { others.firstOrNull { target.isEligibleTargetFor(it) }?.merchantId }
    var selectedSourceId by remember(others) { mutableStateOf(firstEligible ?: "") }
    var showConfirm by remember { mutableStateOf(false) }

    val selectedSource = others.find { it.merchantId == selectedSourceId && target.isEligibleTargetFor(it) }
    val preview = selectedSource?.let { MenuSyncEngine.planMissingItemsCopy(it, target) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Choose an outlet to pull missing products into ${target.displayName}. Existing products here stay untouched.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(others, key = { it.merchantId }) { cand ->
                val eligible = target.isEligibleTargetFor(cand)
                val disabledReason = when {
                    cand.isSuspended -> "Suspended"
                    cand.businessType != target.businessType -> "Different shop type"
                    else -> null
                }
                val itemPreview = if (eligible) MenuSyncEngine.planMissingItemsCopy(cand, target) else null
                val selected = cand.merchantId == selectedSourceId && eligible

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) DelivioYellow.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    border = BorderStroke(1.dp, if (selected) DelivioYellow else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (eligible) Modifier.clickable { selectedSourceId = cand.merchantId } else Modifier)
                        .testTag("pull_source_${cand.merchantId}")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            enabled = eligible,
                            onClick = { selectedSourceId = cand.merchantId }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cand.displayName,
                                fontWeight = FontWeight.Bold,
                                color = if (eligible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = disabledReason
                                    ?: "${itemPreview?.itemsToCopy?.size ?: 0} items available to pull · ${itemPreview?.alreadyExistsCount ?: 0} already in this outlet",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (disabledReason != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        val pullCount = preview?.itemsToCopy?.size ?: 0
        Button(
            onClick = { showConfirm = true },
            enabled = selectedSource != null && pullCount > 0,
            colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = Color(0xFF1A1A1A)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("pull_menu_confirm_trigger_btn")
        ) {
            Text(
                text = if (pullCount > 0) "Pull $pullCount Missing Item(s)" else "Nothing Missing to Pull",
                fontWeight = FontWeight.ExtraBold
            )
        }
    }

    if (showConfirm && selectedSource != null && preview != null) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Confirm Pull Menu", fontWeight = FontWeight.Bold) },
            text = {
                Text("Pull ${preview.itemsToCopy.size} missing product(s) from ${selectedSource.displayName} into ${target.displayName}?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirm = false
                        onExecutePull(target, selectedSource)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = Color(0xFF1A1A1A)),
                    modifier = Modifier.testTag("pull_menu_execute_btn")
                ) {
                    Text("Pull Now", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SyncMenuFlowContent(
    mine: OutletMenuSnapshot,
    others: List<OutletMenuSnapshot>,
    onExecuteSync: (
        OutletMenuSnapshot,
        OutletMenuSnapshot,
        Set<SyncField>,
        SyncPlanPreview,
        Map<String, SyncResolution>
    ) -> Unit
) {
    val eligiblePeers = remember(others, mine) { others.filter { it.isEligibleTargetFor(mine) } }
    var selectedPeerId by remember(eligiblePeers) { mutableStateOf(eligiblePeers.firstOrNull()?.merchantId ?: "") }
    var selectedFields by remember { mutableStateOf(SyncField.entries.toSet()) }
    var addMissingBothWays by remember { mutableStateOf(true) }
    var step by remember { mutableStateOf(1) } // 1 = Configure fields, 2 = Conflict resolver
    var resolutions by remember { mutableStateOf<Map<String, SyncResolution>>(emptyMap()) }
    var showConfirm by remember { mutableStateOf(false) }

    val peer = eligiblePeers.find { it.merchantId == selectedPeerId }
    val plan = remember(mine, peer, selectedFields, addMissingBothWays) {
        if (peer == null) null
        else MenuSyncEngine.planSync(mine, peer, selectedFields, addMissingBothWays)
    }

    LaunchedEffect(plan) {
        val currentKeys = plan?.conflicts?.map { it.normalizedKey }?.toSet() ?: emptySet()
        resolutions = resolutions.filterKeys { it in currentKeys }
    }

    if (eligiblePeers.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No active outlets with the same shop type are available to sync.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    if (step == 1) {
        val hasWork = plan != null && (plan.addHereItems.isNotEmpty() || plan.addThereItems.isNotEmpty() || plan.conflicts.isNotEmpty())
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("Compare ${mine.displayName} with:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        eligiblePeers.forEach { cand ->
                            val isSel = cand.merchantId == selectedPeerId
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (isSel) DelivioYellow else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSel) DelivioYellow.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedPeerId = cand.merchantId }
                                    .testTag("sync_peer_${cand.merchantId}")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = isSel, onClick = { selectedPeerId = cand.merchantId })
                                    Text(cand.displayName, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                item {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Fields to sync", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    SyncField.entries.forEach { field ->
                        val checked = field in selectedFields
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedFields = if (checked) selectedFields - field else selectedFields + field
                                }
                                .padding(vertical = 2.dp)
                                .testTag("sync_field_${field.name}")
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { on ->
                                    selectedFields = if (on) selectedFields + field else selectedFields - field
                                }
                            )
                            Text(field.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Also add products that exist in only one outlet (both ways)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            Switch(
                                checked = addMissingBothWays,
                                onCheckedChange = { addMissingBothWays = it },
                                modifier = Modifier.testTag("sync_both_ways_switch")
                            )
                        }
                    }
                }

                if (plan != null) {
                    item {
                        Surface(
                            color = DelivioYellow.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("sync_plan_preview_banner")
                        ) {
                            Text(
                                text = "${plan.addHereItems.size} to add here · ${plan.addThereItems.size} to add there · ${plan.conflicts.size} difference(s) · ${plan.skippedInSyncCount} identical",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    if (plan != null && plan.conflicts.isNotEmpty()) {
                        step = 2
                    } else {
                        showConfirm = true
                    }
                },
                enabled = peer != null && selectedFields.isNotEmpty() && hasWork,
                colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = Color(0xFF1A1A1A)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("sync_step1_next_btn")
            ) {
                Text(
                    text = if ((plan?.conflicts?.size ?: 0) > 0) "Resolve ${plan!!.conflicts.size} Difference(s)" else "Review & Sync",
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    } else if (peer != null && plan != null) {
        // STEP 2: 2-Column Conflict Resolver
        val allResolved = plan.conflicts.all { resolutions.containsKey(it.normalizedKey) }
        val mineShort = mine.branchCode.ifBlank { mine.displayName }
        val theirShort = peer.branchCode.ifBlank { peer.displayName }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { step = 1 },
                    modifier = Modifier.size(48.dp).testTag("sync_step2_back_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Resolve Differences (${resolutions.size}/${plan.conflicts.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        resolutions = plan.conflicts.associate { it.normalizedKey to SyncResolution.USE_MINE }
                    },
                    modifier = Modifier.weight(1f).testTag("sync_use_mine_all_btn")
                ) {
                    Text("Use mine for all", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = {
                        resolutions = plan.conflicts.associate { it.normalizedKey to SyncResolution.USE_THEIRS }
                    },
                    modifier = Modifier.weight(1f).testTag("sync_use_theirs_all_btn")
                ) {
                    Text("Use theirs for all", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(plan.conflicts, key = { it.normalizedKey }) { conflict ->
                    val chosen = resolutions[conflict.normalizedKey]
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
                        modifier = Modifier.fillMaxWidth().testTag("sync_conflict_card_${conflict.normalizedKey}")
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = conflict.myItem.name,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold)
                            )

                            // 2-column comparison
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = if (chosen == SyncResolution.USE_MINE) DelivioYellow.copy(alpha = 0.16f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "Mine ($mineShort)",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                        conflict.diffs.forEach { diff ->
                                            Text(
                                                text = "${diff.label}: ${diff.myValue}",
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(DelivioYellow.copy(alpha = 0.25f))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    color = if (chosen == SyncResolution.USE_THEIRS) DelivioYellow.copy(alpha = 0.16f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "Theirs ($theirShort)",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                        conflict.diffs.forEach { diff ->
                                            Text(
                                                text = "${diff.label}: ${diff.theirValue}",
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(DelivioYellow.copy(alpha = 0.25f))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Per-item RadioButtons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { resolutions = resolutions + (conflict.normalizedKey to SyncResolution.USE_MINE) }
                                        .testTag("sync_conflict_mine_${conflict.normalizedKey}")
                                ) {
                                    RadioButton(
                                        selected = chosen == SyncResolution.USE_MINE,
                                        onClick = { resolutions = resolutions + (conflict.normalizedKey to SyncResolution.USE_MINE) }
                                    )
                                    Text("Use mine", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { resolutions = resolutions + (conflict.normalizedKey to SyncResolution.USE_THEIRS) }
                                        .testTag("sync_conflict_theirs_${conflict.normalizedKey}")
                                ) {
                                    RadioButton(
                                        selected = chosen == SyncResolution.USE_THEIRS,
                                        onClick = { resolutions = resolutions + (conflict.normalizedKey to SyncResolution.USE_THEIRS) }
                                    )
                                    Text("Use theirs", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = { showConfirm = true },
                enabled = allResolved,
                colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = Color(0xFF1A1A1A)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("sync_apply_btn")
            ) {
                Icon(Icons.Default.SyncAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Apply Sync", fontWeight = FontWeight.ExtraBold)
            }
        }
    }

    if (showConfirm && peer != null && plan != null) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Confirm Sync Menu", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Sync between ${mine.displayName} and ${peer.displayName}?\n" +
                        "• Add to ${mine.displayName}: ${plan.addHereItems.size}\n" +
                        "• Add to ${peer.displayName}: ${plan.addThereItems.size}\n" +
                        "• Update differences: ${plan.conflicts.size}\n" +
                        "No items will be deleted."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirm = false
                        onExecuteSync(mine, peer, selectedFields, plan, resolutions)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = Color(0xFF1A1A1A)),
                    modifier = Modifier.testTag("sync_menu_execute_btn")
                ) {
                    Text("Confirm Sync", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SyncResultView(
    result: MenuSyncResult,
    context: Context,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().testTag("sync_result_view"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusOpenGreen, modifier = Modifier.size(28.dp))
            Text(
                text = "${result.operationName} Complete",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
            )
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Added: ${result.added}", fontWeight = FontWeight.Bold)
                Text("Updated: ${result.updated}", fontWeight = FontWeight.Bold)
                Text("Skipped: ${result.skipped}", fontWeight = FontWeight.Medium)
                Text(
                    text = "Failed: ${result.failed}",
                    fontWeight = FontWeight.Medium,
                    color = if (result.failed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (result.details.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(result.details) { line ->
                    Text("• $line", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Menu Sync Summary", result.toCopyableSummary()))
                    Toast.makeText(context, "Summary copied", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(48.dp).testTag("sync_result_copy_btn")
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onDone,
                colors = ButtonDefaults.buttonColors(containerColor = DelivioYellow, contentColor = Color(0xFF1A1A1A)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(48.dp).testTag("sync_result_done_btn")
            ) {
                Text("Done", fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
