package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DelivioYellow
import com.example.util.OutletsPinHelper
import com.example.util.PinVerifyResult
import com.example.viewmodel.PinModalMode
import kotlinx.coroutines.delay

@Composable
fun OutletsPinDialog(
    mode: PinModalMode,
    isLockedOutInitially: Boolean,
    initialLockoutSeconds: Long,
    onCheckLockoutSeconds: () -> Long,
    onCreatePin: (pin: String, confirm: String, onDone: (Boolean, String?) -> Unit) -> Unit,
    onEnterPin: (pin: String) -> PinVerifyResult,
    onForgotPinClick: () -> Unit,
    onVerifyPasswordReset: (password: String, onDone: (Boolean, String?) -> Unit) -> Unit,
    onChangePin: (oldPin: String, newPin: String, confirm: String, onDone: (Boolean, String?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var pin1 by remember(mode) { mutableStateOf("") }
    var pin2 by remember(mode) { mutableStateOf("") }
    var oldPin by remember(mode) { mutableStateOf("") }
    var password by remember(mode) { mutableStateOf("") }
    var showSecret by remember(mode) { mutableStateOf(false) }
    var errorMsg by remember(mode) { mutableStateOf<String?>(null) }
    var isBusy by remember(mode) { mutableStateOf(false) }
    var lockoutSeconds by remember(mode, isLockedOutInitially, initialLockoutSeconds) {
        mutableStateOf(if (isLockedOutInitially) initialLockoutSeconds else 0L)
    }

    LaunchedEffect(lockoutSeconds) {
        if (lockoutSeconds > 0L) {
            delay(1000L)
            lockoutSeconds = onCheckLockoutSeconds()
        }
    }

    val title = when (mode) {
        PinModalMode.CREATE -> "Create Outlets PIN"
        PinModalMode.ENTER -> "Enter Outlets PIN"
        PinModalMode.FORGOT_PASSWORD -> "Verify Account Password"
        PinModalMode.CHANGE -> "Change Outlets PIN"
    }

    val subtitle = when (mode) {
        PinModalMode.CREATE -> "Choose a 4 to 6 digit PIN to protect outlet switching and menu sync."
        PinModalMode.ENTER -> "Enter your 4 to 6 digit Outlets PIN to continue."
        PinModalMode.FORGOT_PASSWORD -> "Confirm your Delivio Partner account password to set a new Outlets PIN."
        PinModalMode.CHANGE -> "Enter your current PIN, then choose a new 4 to 6 digit PIN."
    }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.testTag("pin_dialog_title")
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val visTrans = if (showSecret) VisualTransformation.None else PasswordVisualTransformation()
                val trailingToggle: @Composable () -> Unit = {
                    IconButton(onClick = { showSecret = !showSecret }) {
                        Icon(
                            imageVector = if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showSecret) "Hide" else "Show"
                        )
                    }
                }

                when (mode) {
                    PinModalMode.CREATE -> {
                        OutlinedTextField(
                            value = pin1,
                            onValueChange = {
                                val filtered = it.filter { ch -> ch.isDigit() }.take(6)
                                pin1 = filtered
                                errorMsg = null
                            },
                            label = { Text("New PIN (4-6 digits)") },
                            singleLine = true,
                            visualTransformation = visTrans,
                            trailingIcon = trailingToggle,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pin_create_input")
                        )
                        OutlinedTextField(
                            value = pin2,
                            onValueChange = {
                                val filtered = it.filter { ch -> ch.isDigit() }.take(6)
                                pin2 = filtered
                                errorMsg = null
                            },
                            label = { Text("Confirm PIN") },
                            singleLine = true,
                            visualTransformation = visTrans,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pin_create_confirm_input")
                        )
                    }

                    PinModalMode.ENTER -> {
                        OutlinedTextField(
                            value = pin1,
                            onValueChange = {
                                val filtered = it.filter { ch -> ch.isDigit() }.take(6)
                                pin1 = filtered
                                errorMsg = null
                            },
                            enabled = lockoutSeconds <= 0L,
                            label = { Text("Outlets PIN") },
                            singleLine = true,
                            visualTransformation = visTrans,
                            trailingIcon = trailingToggle,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pin_enter_input")
                        )

                        if (lockoutSeconds > 0L) {
                            val mins = lockoutSeconds / 60
                            val secs = lockoutSeconds % 60
                            Text(
                                text = "Too many wrong attempts. Try again in %d:%02d.".format(mins, secs),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("pin_lockout_message")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = onForgotPinClick,
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("pin_forgot_btn")
                            ) {
                                Text("Forgot PIN?", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    PinModalMode.FORGOT_PASSWORD -> {
                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                errorMsg = null
                            },
                            label = { Text("Account Password") },
                            singleLine = true,
                            visualTransformation = visTrans,
                            trailingIcon = trailingToggle,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pin_forgot_password_input")
                        )
                    }

                    PinModalMode.CHANGE -> {
                        OutlinedTextField(
                            value = oldPin,
                            onValueChange = {
                                oldPin = it.filter { ch -> ch.isDigit() }.take(6)
                                errorMsg = null
                            },
                            label = { Text("Current PIN") },
                            singleLine = true,
                            visualTransformation = visTrans,
                            trailingIcon = trailingToggle,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pin_change_old_input")
                        )
                        OutlinedTextField(
                            value = pin1,
                            onValueChange = {
                                pin1 = it.filter { ch -> ch.isDigit() }.take(6)
                                errorMsg = null
                            },
                            label = { Text("New PIN (4-6 digits)") },
                            singleLine = true,
                            visualTransformation = visTrans,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pin_change_new_input")
                        )
                        OutlinedTextField(
                            value = pin2,
                            onValueChange = {
                                pin2 = it.filter { ch -> ch.isDigit() }.take(6)
                                errorMsg = null
                            },
                            label = { Text("Confirm New PIN") },
                            singleLine = true,
                            visualTransformation = visTrans,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pin_change_confirm_input")
                        )
                    }
                }

                if (!errorMsg.isNullOrBlank()) {
                    Text(
                        text = errorMsg!!,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("pin_dialog_error")
                    )
                }
            }
        },
        confirmButton = {
            val actionTag = when (mode) {
                PinModalMode.CREATE -> "pin_create_submit_btn"
                PinModalMode.ENTER -> "pin_enter_submit_btn"
                PinModalMode.FORGOT_PASSWORD -> "pin_forgot_verify_btn"
                PinModalMode.CHANGE -> "pin_change_submit_btn"
            }
            val actionLabel = when (mode) {
                PinModalMode.CREATE -> "Save PIN"
                PinModalMode.ENTER -> "Unlock"
                PinModalMode.FORGOT_PASSWORD -> "Verify Password"
                PinModalMode.CHANGE -> "Update PIN"
            }
            Button(
                onClick = {
                    when (mode) {
                        PinModalMode.CREATE -> {
                            if (!OutletsPinHelper.isValidPinFormat(pin1)) {
                                errorMsg = "PIN must be 4 to 6 digits."
                                return@Button
                            }
                            if (pin1 != pin2) {
                                errorMsg = "PINs do not match."
                                return@Button
                            }
                            isBusy = true
                            onCreatePin(pin1, pin2) { ok, err ->
                                isBusy = false
                                if (!ok) errorMsg = err ?: "Could not save PIN"
                            }
                        }

                        PinModalMode.ENTER -> {
                            if (lockoutSeconds > 0L) return@Button
                            if (!OutletsPinHelper.isValidPinFormat(pin1)) {
                                errorMsg = "Enter a 4 to 6 digit PIN."
                                return@Button
                            }
                            when (val res = onEnterPin(pin1)) {
                                PinVerifyResult.Success -> Unit
                                is PinVerifyResult.WrongPin -> {
                                    pin1 = ""
                                    errorMsg = "Incorrect PIN. ${res.remainingAttempts} attempt(s) left."
                                }
                                is PinVerifyResult.LockedOut -> {
                                    pin1 = ""
                                    lockoutSeconds = res.remainingSeconds
                                    errorMsg = null
                                }
                            }
                        }

                        PinModalMode.FORGOT_PASSWORD -> {
                            if (password.isBlank()) {
                                errorMsg = "Enter your account password."
                                return@Button
                            }
                            isBusy = true
                            onVerifyPasswordReset(password) { ok, err ->
                                isBusy = false
                                if (!ok) errorMsg = err ?: "Incorrect password"
                            }
                        }

                        PinModalMode.CHANGE -> {
                            if (!OutletsPinHelper.isValidPinFormat(oldPin)) {
                                errorMsg = "Enter your current PIN."
                                return@Button
                            }
                            if (!OutletsPinHelper.isValidPinFormat(pin1)) {
                                errorMsg = "New PIN must be 4 to 6 digits."
                                return@Button
                            }
                            if (pin1 != pin2) {
                                errorMsg = "New PINs do not match."
                                return@Button
                            }
                            isBusy = true
                            onChangePin(oldPin, pin1, pin2) { ok, err ->
                                isBusy = false
                                if (!ok) errorMsg = err ?: "Could not update PIN"
                            }
                        }
                    }
                },
                enabled = !isBusy && (mode != PinModalMode.ENTER || lockoutSeconds <= 0L),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DelivioYellow,
                    contentColor = Color(0xFF1A1A1A)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag(actionTag)
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF1A1A1A)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(actionLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isBusy,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("pin_dialog_cancel_btn")
            ) {
                Text("Cancel")
            }
        },
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .widthIn(max = 460.dp)
            .testTag("outlets_pin_dialog")
    )
}
