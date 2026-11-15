package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var activeField by remember(mode) { mutableIntStateOf(0) } // CREATE: 0=new, 1=confirm; CHANGE: 0=old, 1=new, 2=confirm
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
        PinModalMode.CREATE -> "Choose a 4 to 6 digit PIN to protect outlets and menu sync."
        PinModalMode.ENTER -> "Enter your 4 to 6 digit Outlets PIN to continue."
        PinModalMode.FORGOT_PASSWORD -> "Confirm your Delivio Partner account password to set a new Outlets PIN."
        PinModalMode.CHANGE -> "Enter your current PIN, then choose a new 4 to 6 digit PIN."
    }

    val onDigitPress: (String) -> Unit = { digit ->
        errorMsg = null
        when (mode) {
            PinModalMode.ENTER -> {
                if (lockoutSeconds <= 0L && pin1.length < 6) {
                    pin1 += digit
                }
            }
            PinModalMode.CREATE -> {
                if (activeField == 0 && pin1.length < 6) pin1 += digit
                else if (activeField == 1 && pin2.length < 6) pin2 += digit
            }
            PinModalMode.CHANGE -> {
                when (activeField) {
                    0 -> if (oldPin.length < 6) oldPin += digit
                    1 -> if (pin1.length < 6) pin1 += digit
                    2 -> if (pin2.length < 6) pin2 += digit
                }
            }
            PinModalMode.FORGOT_PASSWORD -> Unit
        }
    }

    val onBackspacePress: () -> Unit = {
        errorMsg = null
        when (mode) {
            PinModalMode.ENTER -> {
                if (lockoutSeconds <= 0L && pin1.isNotEmpty()) pin1 = pin1.dropLast(1)
            }
            PinModalMode.CREATE -> {
                if (activeField == 0 && pin1.isNotEmpty()) pin1 = pin1.dropLast(1)
                else if (activeField == 1 && pin2.isNotEmpty()) pin2 = pin2.dropLast(1)
            }
            PinModalMode.CHANGE -> {
                when (activeField) {
                    0 -> if (oldPin.isNotEmpty()) oldPin = oldPin.dropLast(1)
                    1 -> if (pin1.isNotEmpty()) pin1 = pin1.dropLast(1)
                    2 -> if (pin2.isNotEmpty()) pin2 = pin2.dropLast(1)
                }
            }
            PinModalMode.FORGOT_PASSWORD -> Unit
        }
    }

    val onClearPress: () -> Unit = {
        errorMsg = null
        when (mode) {
            PinModalMode.ENTER -> if (lockoutSeconds <= 0L) pin1 = ""
            PinModalMode.CREATE -> {
                if (activeField == 0) pin1 = "" else pin2 = ""
            }
            PinModalMode.CHANGE -> {
                when (activeField) {
                    0 -> oldPin = ""
                    1 -> pin1 = ""
                    2 -> pin2 = ""
                }
            }
            PinModalMode.FORGOT_PASSWORD -> Unit
        }
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )

                when (mode) {
                    PinModalMode.ENTER -> {
                        PinDotsRow(
                            digitCount = pin1.length,
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .testTag("pin_dots_row")
                        )

                        if (lockoutSeconds > 0L) {
                            val mins = lockoutSeconds / 60
                            val secs = lockoutSeconds % 60
                            Text(
                                text = "Too many wrong attempts. Try again in %d:%02d.".format(mins, secs),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pin_lockout_message")
                            )
                        }

                        if (!errorMsg.isNullOrBlank()) {
                            Text(
                                text = errorMsg!!,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pin_dialog_error")
                            )
                        }

                        PinNumericKeypad(
                            enabled = !isBusy && lockoutSeconds <= 0L,
                            onDigit = onDigitPress,
                            onBackspace = onBackspacePress,
                            onClear = onClearPress
                        )

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

                    PinModalMode.CREATE -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PinStepSelectorBox(
                                label = "New PIN",
                                digitCount = pin1.length,
                                isSelected = activeField == 0,
                                onClick = { activeField = 0 },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pin_field_new")
                            )
                            PinStepSelectorBox(
                                label = "Confirm PIN",
                                digitCount = pin2.length,
                                isSelected = activeField == 1,
                                onClick = { activeField = 1 },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pin_field_confirm")
                            )
                        }

                        if (!errorMsg.isNullOrBlank()) {
                            Text(
                                text = errorMsg!!,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pin_dialog_error")
                            )
                        }

                        PinNumericKeypad(
                            enabled = !isBusy,
                            onDigit = onDigitPress,
                            onBackspace = onBackspacePress,
                            onClear = onClearPress
                        )
                    }

                    PinModalMode.CHANGE -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PinStepSelectorBox(
                                label = "Current",
                                digitCount = oldPin.length,
                                isSelected = activeField == 0,
                                onClick = { activeField = 0 },
                                dotSize = 9.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pin_field_old")
                            )
                            PinStepSelectorBox(
                                label = "New PIN",
                                digitCount = pin1.length,
                                isSelected = activeField == 1,
                                onClick = { activeField = 1 },
                                dotSize = 9.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pin_field_new")
                            )
                            PinStepSelectorBox(
                                label = "Confirm",
                                digitCount = pin2.length,
                                isSelected = activeField == 2,
                                onClick = { activeField = 2 },
                                dotSize = 9.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pin_field_confirm")
                            )
                        }

                        if (!errorMsg.isNullOrBlank()) {
                            Text(
                                text = errorMsg!!,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pin_dialog_error")
                            )
                        }

                        PinNumericKeypad(
                            enabled = !isBusy,
                            onDigit = onDigitPress,
                            onBackspace = onBackspacePress,
                            onClear = onClearPress
                        )
                    }

                    PinModalMode.FORGOT_PASSWORD -> {
                        val visTrans = if (showSecret) VisualTransformation.None else PasswordVisualTransformation()
                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                errorMsg = null
                            },
                            label = { Text("Account Password") },
                            singleLine = true,
                            visualTransformation = visTrans,
                            trailingIcon = {
                                IconButton(onClick = { showSecret = !showSecret }) {
                                    Icon(
                                        imageVector = if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showSecret) "Hide password" else "Show password"
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pin_forgot_password_input")
                        )

                        if (!errorMsg.isNullOrBlank()) {
                            Text(
                                text = errorMsg!!,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pin_dialog_error")
                            )
                        }
                    }
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
                                activeField = 0
                                errorMsg = "PIN must be 4 to 6 digits."
                                return@Button
                            }
                            if (pin2.isEmpty()) {
                                activeField = 1
                                errorMsg = "Confirm your new PIN."
                                return@Button
                            }
                            if (pin1 != pin2) {
                                activeField = 1
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
                                    val triesWord = if (res.remainingAttempts == 1) "try" else "tries"
                                    errorMsg = "Wrong PIN. ${res.remainingAttempts} $triesWord left"
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
                                activeField = 0
                                errorMsg = "Enter your current PIN."
                                return@Button
                            }
                            if (!OutletsPinHelper.isValidPinFormat(pin1)) {
                                activeField = 1
                                errorMsg = "New PIN must be 4 to 6 digits."
                                return@Button
                            }
                            if (pin2.isEmpty()) {
                                activeField = 2
                                errorMsg = "Confirm your new PIN."
                                return@Button
                            }
                            if (pin1 != pin2) {
                                activeField = 2
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

@Composable
private fun PinDotsRow(
    digitCount: Int,
    dotSize: Dp = 14.dp,
    spacing: Dp = 10.dp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until 6) {
            val isFilled = i < digitCount
            val isRequiredSlot = i < 4
            val fillColor = when {
                isFilled -> Color(0xFF1A1A1A)
                else -> Color.Transparent
            }
            val borderColor = when {
                isFilled -> Color(0xFF1A1A1A)
                isRequiredSlot -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
            }
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(fillColor)
                    .border(width = 1.5.dp, color = borderColor, shape = CircleShape)
                    .testTag("pin_dot_$i")
            )
        }
    }
}

@Composable
private fun PinStepSelectorBox(
    label: String,
    digitCount: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    dotSize: Dp = 10.dp,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) DelivioYellow.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) DelivioYellow else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        ),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            PinDotsRow(
                digitCount = digitCount,
                dotSize = dotSize,
                spacing = 5.dp
            )
        }
    }
}

@Composable
private fun PinNumericKeypad(
    enabled: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("CLEAR", "0", "BACK")
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    val tag = when (key) {
                        "CLEAR" -> "pin_key_clear"
                        "BACK" -> "pin_key_backspace"
                        else -> "pin_key_$key"
                    }
                    Surface(
                        onClick = {
                            if (!enabled) return@Surface
                            when (key) {
                                "CLEAR" -> onClear()
                                "BACK" -> onBackspace()
                                else -> onDigit(key)
                            }
                        },
                        enabled = enabled,
                        shape = RoundedCornerShape(12.dp),
                        color = if (key == "CLEAR" || key == "BACK") {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.55f else 0.25f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.85f else 0.35f)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .height(50.dp)
                            .testTag(tag)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            when (key) {
                                "CLEAR" -> Text(
                                    text = "Clear",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f)
                                )
                                "BACK" -> Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "Backspace",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f),
                                    modifier = Modifier.size(20.dp)
                                )
                                else -> Text(
                                    text = key,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
