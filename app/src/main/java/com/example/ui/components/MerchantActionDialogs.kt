package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
        title = { Text("Close Store with Active Orders?") },
        text = {
            Text("You currently have $activeOrdersCount active orders in progress (preparing/ready). Are you sure you want to set the store to CLOSED now? You will still be able to complete active deliveries.")
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
                    label = { Text("Withdrawal Amount ($)") },
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
fun BusinessHoursDialog(
    schedule: List<DaySchedule>,
    autoScheduleEnabled: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Weekly Business Hours") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Operating schedule for automated open/close transitions:", style = MaterialTheme.typography.bodySmall)
                schedule.forEach { day ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(day.day, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("${day.openTime} - ${day.closeTime}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
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
