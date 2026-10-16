package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PresetColors
import com.example.ui.theme.getContrastTextColor
import com.example.ui.theme.parseHexColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerSheet(
    currentColorHex: String,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var hexInput by remember { mutableStateOf(currentColorHex) }
    var selectedColor by remember { mutableStateOf(parseHexColor(currentColorHex)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Choose Merchant Theme Color",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )

            Text(
                text = "Select a color to instantly update buttons, highlights, tabs, and brand accents app-wide.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            // Preset swatches (Yellow, Orange, Red, Green, Blue, Purple, Pink, Teal)
            Text(
                text = "Preset Brand Swatches",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PresetColors.take(4).forEach { preset ->
                    ColorSwatchItem(
                        name = preset.name,
                        color = preset.color,
                        isSelected = hexInput.equals(preset.hex, ignoreCase = true) || selectedColor == preset.color,
                        onClick = {
                            selectedColor = preset.color
                            hexInput = preset.hex
                            onColorSelected(preset.hex) // Instantly apply on tap!
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PresetColors.drop(4).take(4).forEach { preset ->
                    ColorSwatchItem(
                        name = preset.name,
                        color = preset.color,
                        isSelected = hexInput.equals(preset.hex, ignoreCase = true) || selectedColor == preset.color,
                        onClick = {
                            selectedColor = preset.color
                            hexInput = preset.hex
                            onColorSelected(preset.hex) // Instantly apply on tap!
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Custom HEX input
            Text(
                text = "Custom HEX Color",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(selectedColor)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                )

                Spacer(modifier = Modifier.width(12.dp))

                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { input ->
                        hexInput = input
                        val clean = input.trim().removePrefix("#")
                        if (clean.length == 6 || clean.length == 8) {
                            val parsed = parseHexColor("#$clean")
                            selectedColor = parsed
                            onColorSelected("#$clean")
                        }
                    },
                    label = { Text("Hex Code (e.g. #FFC107)") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("custom_hex_input")
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Apply Button
            val onTextColor = getContrastTextColor(selectedColor)
            Button(
                onClick = {
                    val clean = hexInput.trim().removePrefix("#")
                    val finalHex = if (clean.length == 6 || clean.length == 8) "#$clean" else currentColorHex
                    onColorSelected(finalHex)
                    onDismiss()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = selectedColor,
                    contentColor = onTextColor
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("apply_theme_color_btn")
            ) {
                Text(
                    text = "Done / Close Picker",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ColorSwatchItem(
    name: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(4.dp)
            .testTag("color_swatch_$name")
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = getContrastTextColor(color),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
