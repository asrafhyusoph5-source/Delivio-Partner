package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DaySchedule
import com.example.data.model.MenuItem
import com.example.data.model.Order
import com.example.ui.theme.LocalDelivioColors
import com.example.ui.theme.StatusClosedRed
import com.example.util.CurrencyConstants
import com.example.util.FormatUtils

@Composable
fun RejectOrderDialog(
    order: Order,
    onConfirm: (reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    val reasons = listOf(
        "Store too busy / high volume",
        "Out of essential ingredients",
        "Closing store soon",
        "Kitchen equipment issue",
        "Other operational issue"
    )
    var selectedReason by remember { mutableStateOf(reasons.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reject Order ${order.orderNumber}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Please specify why you are declining this order:", style = MaterialTheme.typography.bodySmall)
                reasons.forEach { reason ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReason = reason }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(reason, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedReason) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Confirm Rejection", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PrepTimePickerDialog(
    order: Order,
    defaultMinutes: Int = 15,
    onConfirm: (prepMinutes: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val customColors = LocalDelivioColors.current
    val times = listOf(10, 15, 20, 30, 45)
    var selectedTime by remember { mutableIntStateOf(defaultMinutes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Prep Time for ${order.orderNumber}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("How long will the kitchen take to prepare this order?", style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    times.forEach { mins ->
                        FilterChip(
                            selected = selectedTime == mins,
                            onClick = { selectedTime = mins },
                            label = { Text("${mins}m") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = customColors.accentTint,
                                selectedLabelColor = customColors.accent
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedTime) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = customColors.accent,
                    contentColor = customColors.onAccent
                )
            ) {
                Text("Start Preparing ($selectedTime min)", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SoldOutDurationDialog(
    item: MenuItem,
    onConfirm: (reason: String, durationHours: Int?) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        "Until I turn it on" to null,
        "For today only" to 24,
        "For 1 hour" to 1,
        "For 2 hours" to 2,
        "For 4 hours" to 4
    )
    var selectedOption by remember { mutableStateOf(options.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mark '${item.name}' Sold Out") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("How long should this item remain marked as Sold Out?", style = MaterialTheme.typography.bodySmall)
                options.forEach { opt ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedOption = opt }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = selectedOption == opt,
                            onClick = { selectedOption = opt }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(opt.first, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedOption.first, selectedOption.second) },
                colors = ButtonDefaults.buttonColors(containerColor = StatusClosedRed)
            ) {
                Text("Mark Sold Out", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun StorePauseSheet(
    onPause: (minutes: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val customColors = LocalDelivioColors.current
    val pauseDurations = listOf(15, 30, 60)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pause Incoming Orders") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Temporarily halt incoming orders if kitchen is slammed:", style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pauseDurations.forEach { mins ->
                        Button(
                            onClick = { onPause(mins) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = customColors.accent,
                                contentColor = customColors.onAccent
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("${mins} min", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun CloseConfirmationDialog(
    activeOrdersCount: Int,
    onConfirmClose: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Close Store", fontWeight = FontWeight.Bold) },
        text = {
            Text("New orders stop immediately. Your $activeOrdersCount active orders continue.")
        },
        confirmButton = {
            Button(
                onClick = onConfirmClose,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Close Store", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep Open") }
        }
    )
}

@Composable
fun EditOrderErrorDialog(
    errorMessage: String,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Failed to Update Order", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Delivio Order Error", errorMessage)
                    clipboard.setPrimaryClip(clip)
                    android.widget.Toast.makeText(context, "Error copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy Error")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}

@Composable
fun RequestPayoutDialog(
    availableBalance: Double = 845.20,
    bankName: String,
    accountNumber: String,
    onConfirm: (amount: Double) -> Unit,
    onDismiss: () -> Unit
) {
    val customColors = LocalDelivioColors.current
    var amountText by remember { mutableStateOf(availableBalance.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Request Merchant Payout") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Disbursement Destination: $bankName ($accountNumber)", style = MaterialTheme.typography.bodySmall)
                Text("Available Balance: ${FormatUtils.formatCurrency(availableBalance)}", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Withdrawal Amount (${CurrencyConstants.SYMBOL})") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    onConfirm(amount)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = customColors.accent,
                    contentColor = customColors.onAccent
                )
            ) {
                Text("Confirm Payout", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun BankDetailsDialog(
    initialBank: String,
    initialHolder: String,
    initialNum: String,
    onSave: (bank: String, holder: String, num: String) -> Unit,
    onDismiss: () -> Unit
) {
    val customColors = LocalDelivioColors.current
    var bank by remember { mutableStateOf(initialBank) }
    var holder by remember { mutableStateOf(initialHolder) }
    var num by remember { mutableStateOf(initialNum) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Payout Account Details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = bank, onValueChange = { bank = it }, label = { Text("Bank Name") }, singleLine = true, shape = RoundedCornerShape(10.dp))
                OutlinedTextField(value = holder, onValueChange = { holder = it }, label = { Text("Account Holder Name") }, singleLine = true, shape = RoundedCornerShape(10.dp))
                OutlinedTextField(value = num, onValueChange = { num = it }, label = { Text("Account Number") }, singleLine = true, shape = RoundedCornerShape(10.dp))
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(bank, holder, num) },
                colors = ButtonDefaults.buttonColors(containerColor = customColors.accent, contentColor = customColors.onAccent)
            ) {
                Text("Save Account")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SimpleTimePickerModal(
    title: String,
    initialTime: String,
    onTimeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val parts = initialTime.split(":")
    val initialH = parts.getOrNull(0)?.toIntOrNull() ?: 8
    val initialM = parts.getOrNull(1)?.toIntOrNull() ?: 0

    var selectedHour by remember { mutableIntStateOf(initialH.coerceIn(0, 23)) }
    var selectedMinute by remember { mutableIntStateOf(initialM.coerceIn(0, 59)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "%02d:%02d".format(selectedHour, selectedMinute),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hour Stepper
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Hour (0-23)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { selectedHour = (selectedHour - 1 + 24) % 24 }) {
                                Text("-", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "%02d".format(selectedHour),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(onClick = { selectedHour = (selectedHour + 1) % 24 }) {
                                Text("+", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Minute Stepper
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Minute", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { selectedMinute = (selectedMinute - 5 + 60) % 60 }) {
                                Text("-", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "%02d".format(selectedMinute),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(onClick = { selectedMinute = (selectedMinute + 5) % 60 }) {
                                Text("+", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onTimeSelected("%02d:%02d".format(selectedHour, selectedMinute))
                }
            ) {
                Text("Set Time")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun BusinessHoursDialog(
    initialHours: Map<String, com.example.data.model.DayHours>,
    initialAutoSchedule: Boolean,
    onSave: (Map<String, com.example.data.model.DayHours>, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val customColors = LocalDelivioColors.current
    val dayKeys = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
    val dayNames = mapOf(
        "mon" to "Monday",
        "tue" to "Tuesday",
        "wed" to "Wednesday",
        "thu" to "Thursday",
        "fri" to "Friday",
        "sat" to "Saturday",
        "sun" to "Sunday"
    )

    var hoursMap by remember(initialHours) {
        val base = if (initialHours.isNotEmpty()) initialHours else mapOf(
            "mon" to com.example.data.model.DayHours("08:00", "22:00", false),
            "tue" to com.example.data.model.DayHours("08:00", "22:00", false),
            "wed" to com.example.data.model.DayHours("08:00", "22:00", false),
            "thu" to com.example.data.model.DayHours("08:00", "22:00", false),
            "fri" to com.example.data.model.DayHours("08:00", "23:00", false),
            "sat" to com.example.data.model.DayHours("09:00", "23:00", false),
            "sun" to com.example.data.model.DayHours("09:00", "21:00", false)
        )
        mutableStateOf(base.toMutableMap())
    }

    var autoSchedule by remember(initialAutoSchedule) {
        mutableStateOf(initialAutoSchedule)
    }

    // Time picker picker target state: Pair(dayKey, "open" or "close")
    var timePickerTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    timePickerTarget?.let { (dayKey, mode) ->
        val currentDay = hoursMap[dayKey] ?: com.example.data.model.DayHours()
        val initTime = if (mode == "open") currentDay.open else currentDay.close
        val dayName = dayNames[dayKey] ?: dayKey

        SimpleTimePickerModal(
            title = "Set $dayName ${if (mode == "open") "Opening" else "Closing"} Time",
            initialTime = initTime,
            onTimeSelected = { newTime ->
                val updated = hoursMap.toMutableMap()
                val existing = updated[dayKey] ?: com.example.data.model.DayHours()
                updated[dayKey] = if (mode == "open") existing.copy(open = newTime) else existing.copy(close = newTime)
                hoursMap = updated
                timePickerTarget = null
            },
            onDismiss = { timePickerTarget = null }
        )
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Weekly Business Hours", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                        Text("Configure store operating hours & automated schedule", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Auto-schedule Switch Card
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (autoSchedule) customColors.accentTint.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Open and close automatically by schedule",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Store status automatically switches open/closed based on operating hours timetable.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Switch(
                                checked = autoSchedule,
                                onCheckedChange = { autoSchedule = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = customColors.accent,
                                    checkedTrackColor = customColors.accentTint
                                )
                            )
                        }
                    }

                    // Days List
                    dayKeys.forEach { key ->
                        val dayHours = hoursMap[key] ?: com.example.data.model.DayHours()
                        val dayName = dayNames[key] ?: key
                        val isOvernight = dayHours.isOvernight
                        val is24h = dayHours.isOpen24Hours

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (!dayHours.closed) MaterialTheme.colorScheme.surface
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (!dayHours.closed) MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = dayName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        if (dayHours.closed) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = StatusClosedRed.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "Closed",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = StatusClosedRed,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                )
                                            }
                                        } else if (is24h) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = customColors.accentTint
                                            ) {
                                                Text(
                                                    text = "24 Hours",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = customColors.accent,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Open / Closed switch
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (dayHours.closed) "Closed" else "Open",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (dayHours.closed) StatusClosedRed else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Switch(
                                            checked = !dayHours.closed,
                                            onCheckedChange = { isOpen ->
                                                val updated = hoursMap.toMutableMap()
                                                updated[key] = dayHours.copy(closed = !isOpen)
                                                hoursMap = updated
                                            }
                                        )
                                    }
                                }

                                if (!dayHours.closed) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            // Open Time Button
                                            OutlinedButton(
                                                onClick = { timePickerTarget = Pair(key, "open") },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Text(dayHours.open, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }

                                            Text("to", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                            // Close Time Button
                                            OutlinedButton(
                                                onClick = { timePickerTarget = Pair(key, "close") },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Text(dayHours.close, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            // 24 Hours Toggle
                                            FilterChip(
                                                selected = is24h,
                                                onClick = {
                                                    val updated = hoursMap.toMutableMap()
                                                    if (is24h) {
                                                        updated[key] = dayHours.copy(open = "08:00", close = "22:00")
                                                    } else {
                                                        updated[key] = dayHours.copy(open = "00:00", close = "23:59")
                                                    }
                                                    hoursMap = updated
                                                },
                                                label = { Text("24h", fontSize = 11.sp) },
                                                modifier = Modifier.height(30.dp)
                                            )

                                            // Copy to all days
                                            TextButton(
                                                onClick = {
                                                    val updated = hoursMap.toMutableMap()
                                                    dayKeys.forEach { k ->
                                                        updated[k] = dayHours.copy()
                                                    }
                                                    hoursMap = updated
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Text("Copy to all", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    // Overnight Note
                                    if (isOvernight) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "🌙 Overnight: Closes next morning at ${dayHours.close}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = customColors.accent
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Action Buttons
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            onSave(hoursMap, autoSchedule)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = customColors.accent,
                            contentColor = customColors.onAccent
                        ),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(48.dp)
                            .testTag("save_business_hours_btn")
                    ) {
                        Text("Save Schedule", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun BusinessHoursDialog(
    schedule: List<DaySchedule>,
    autoScheduleEnabled: Boolean,
    onDismiss: () -> Unit
) {
    val initialHours = remember(schedule) {
        val map = mutableMapOf<String, com.example.data.model.DayHours>()
        val dayKeyMap = mapOf(
            "monday" to "mon", "tuesday" to "tue", "wednesday" to "wed",
            "thursday" to "thu", "friday" to "fri", "saturday" to "sat", "sunday" to "sun"
        )
        schedule.forEach { ds ->
            val k = dayKeyMap[ds.day.trim().lowercase()] ?: "mon"
            map[k] = com.example.data.model.DayHours(open = ds.openTime, close = ds.closeTime, closed = !ds.isOpen)
        }
        map
    }
    BusinessHoursDialog(
        initialHours = initialHours,
        initialAutoSchedule = autoScheduleEnabled,
        onSave = { _, _ -> onDismiss() },
        onDismiss = onDismiss
    )
}

@Composable
fun AddStaffDialog(
    onAdd: (name: String, role: String, email: String) -> Unit,
    onDismiss: () -> Unit
) {
    val customColors = LocalDelivioColors.current
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("Kitchen Staff") }
    var email by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Staff Member") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Staff Full Name") }, shape = RoundedCornerShape(10.dp))
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email Address") }, shape = RoundedCornerShape(10.dp))
                OutlinedTextField(value = role, onValueChange = { role = it }, label = { Text("Role (Manager, Kitchen, Cashier)") }, shape = RoundedCornerShape(10.dp))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) onAdd(name, role, email)
                },
                colors = ButtonDefaults.buttonColors(containerColor = customColors.accent, contentColor = customColors.onAccent)
            ) {
                Text("Add Staff")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
