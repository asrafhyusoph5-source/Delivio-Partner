package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
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
import com.example.data.model.MenuItem
import com.example.data.model.MenuSchedule
import com.example.data.model.ShopMenu
import com.example.ui.theme.LocalDelivioColors
import com.example.ui.theme.StatusWarningAmber
import com.example.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuEditScreen(
    menu: ShopMenu,
    isNew: Boolean,
    allMenus: List<ShopMenu>,
    menuItems: List<MenuItem>,
    onSave: (ShopMenu, Set<String>, (Boolean, String?) -> Unit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val customColors = LocalDelivioColors.current

    var name by remember { mutableStateOf(menu.name) }
    var active by remember { mutableStateOf(menu.active) }

    // Schedule state
    var hasSpecificSchedule by remember { mutableStateOf(menu.schedule != null && menu.schedule.days.isNotEmpty()) }
    val initialDays = remember(menu) { menu.schedule?.days?.toSet() ?: setOf("mon", "tue", "wed", "thu", "fri", "sat", "sun") }
    var selectedDays by remember { mutableStateOf(initialDays) }

    var startTime24 by remember { mutableStateOf(menu.schedule?.start ?: "06:00") }
    var endTime24 by remember { mutableStateOf(menu.schedule?.end ?: "10:00") }

    // Products in this menu
    val initiallyAssignedIds = remember(menu, menuItems) {
        if (isNew) emptySet()
        else menuItems.filter { it.getEffectiveMenuIds().contains(menu.id) }.map { it.id }.toSet()
    }
    val assignedProductIds = remember { mutableStateListOf<String>().apply { addAll(initiallyAssignedIds) } }
    var productSearchQuery by remember { mutableStateOf("") }

    // Form feedback and validation
    var hasSubmittedOnce by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Time picker dialog states
    var showTimePickerForStart by remember { mutableStateOf(false) }
    var showTimePickerForEnd by remember { mutableStateOf(false) }

    val trimmedName = name.trim()
    val isNameValid = trimmedName.length in 2..40
    val isNameDuplicate = allMenus.any { it.id != menu.id && it.name.trim().equals(trimmedName, ignoreCase = true) }
    val isDaysValid = !hasSpecificSchedule || selectedDays.isNotEmpty()

    val isFormValid = isNameValid && !isNameDuplicate && isDaysValid

    // Overlap detection
    val overlapWarning = remember(hasSpecificSchedule, selectedDays, startTime24, endTime24, allMenus, menu.id) {
        if (!hasSpecificSchedule || selectedDays.isEmpty()) null
        else {
            val thisOvernight = isTimeOvernight(startTime24, endTime24)
            val thisStartM = parseToMinutes(startTime24)
            val thisEndM = parseToMinutes(endTime24)

            val overlappingMenu = allMenus.firstOrNull { other ->
                if (other.id == menu.id || !other.active) return@firstOrNull false
                val otherSched = other.schedule ?: return@firstOrNull false
                if (otherSched.days.isEmpty()) return@firstOrNull false
                val commonDays = selectedDays.intersect(otherSched.days.toSet())
                if (commonDays.isEmpty()) return@firstOrNull false

                val otherOvernight = isTimeOvernight(otherSched.start, otherSched.end)
                val otherStartM = parseToMinutes(otherSched.start)
                val otherEndM = parseToMinutes(otherSched.end)

                // Interval intersection check
                if (!thisOvernight && !otherOvernight) {
                    thisStartM < otherEndM && thisEndM > otherStartM
                } else {
                    // Overlaps if overnight
                    true
                }
            }

            overlappingMenu?.let { other ->
                val commonDays = selectedDays.intersect(other.schedule?.days?.toSet() ?: emptySet())
                val dayStr = commonDays.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }
                "Overlaps with ${other.name} on $dayStr. Customers will see products from both menus during the overlap."
            }
        }
    }

    val isOvernight = remember(startTime24, endTime24) {
        isTimeOvernight(startTime24, endTime24)
    }

    val filteredProducts = remember(menuItems, productSearchQuery) {
        if (productSearchQuery.isBlank()) menuItems
        else menuItems.filter {
            it.name.contains(productSearchQuery, ignoreCase = true) ||
                    it.categoryName.contains(productSearchQuery, ignoreCase = true) ||
                    (it.subcategoryName?.contains(productSearchQuery, ignoreCase = true) == true)
        }
    }

    val isAllProductsSelected = filteredProducts.isNotEmpty() && filteredProducts.all { assignedProductIds.contains(it.id) }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("menu_edit_screen"),
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("menu_edit_back_btn")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isNew) "Add New Menu" else "Edit Menu",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            hasSubmittedOnce = true
                            if (!isFormValid || isSaving) return@Button
                            isSaving = true
                            val finalSchedule = if (hasSpecificSchedule) {
                                MenuSchedule(
                                    days = selectedDays.toList(),
                                    start = startTime24,
                                    end = endTime24
                                )
                            } else null

                            val updatedMenu = menu.copy(
                                name = trimmedName,
                                active = active,
                                schedule = finalSchedule
                            )

                            onSave(updatedMenu, assignedProductIds.toSet()) { success, err ->
                                isSaving = false
                                if (!success) {
                                    errorMessage = err ?: "Failed to save menu"
                                }
                            }
                        },
                        enabled = isFormValid && !isSaving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = customColors.accent,
                            contentColor = customColors.onAccent
                        ),
                        modifier = Modifier.testTag("save_menu_top_btn")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = customColors.onAccent, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Error banner if submit failed
            if (hasSubmittedOnce && !isFormValid) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    trimmedName.length < 2 -> "Menu name is too short (min 2 characters)"
                                    trimmedName.length > 40 -> "Menu name is too long (max 40 characters)"
                                    isNameDuplicate -> "Menu name \"$trimmedName\" already exists"
                                    !isDaysValid -> "Please select at least one day for availability"
                                    else -> "Please check the form requirements"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Menu Name Card
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Menu details",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Menu name *") },
                            placeholder = { Text("e.g. Breakfast Menu, Late Night Specials") },
                            singleLine = true,
                            isError = hasSubmittedOnce && (!isNameValid || isNameDuplicate),
                            supportingText = if (hasSubmittedOnce) {
                                when {
                                    trimmedName.length < 2 -> { { Text("Min 2 characters required", color = MaterialTheme.colorScheme.error) } }
                                    trimmedName.length > 40 -> { { Text("Max 40 characters allowed", color = MaterialTheme.colorScheme.error) } }
                                    isNameDuplicate -> { { Text("Name must be unique", color = MaterialTheme.colorScheme.error) } }
                                    else -> null
                                }
                            } else null,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("menu_name_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Active Menu", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "When disabled, products in this menu will be unavailable unless in another active menu.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = active,
                                onCheckedChange = { active = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = customColors.accent,
                                    checkedTrackColor = customColors.accentTint
                                )
                            )
                        }
                    }
                }
            }

            // Section: Menu availability
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Menu availability",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Set specific days and times (optional)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = hasSpecificSchedule,
                                onCheckedChange = { hasSpecificSchedule = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = customColors.accent,
                                    checkedTrackColor = customColors.accentTint
                                ),
                                modifier = Modifier.testTag("menu_schedule_toggle")
                            )
                        }

                        if (!hasSpecificSchedule) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.AccessTime, contentDescription = null, tint = customColors.accent, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Always available whenever your store is open.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            // Blue info box (as in reference photo)
                            Surface(
                                color = Color(0xFFEFF6FF), // Soft Tailwind blue-50
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Select when this menu will be available to customers.",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF1E40AF)
                                    )
                                }
                            }

                            // Quick Day Presets
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = selectedDays.size == 7,
                                    onClick = { selectedDays = setOf("mon", "tue", "wed", "thu", "fri", "sat", "sun") },
                                    label = { Text("Every day", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = selectedDays == setOf("mon", "tue", "wed", "thu", "fri"),
                                    onClick = { selectedDays = setOf("mon", "tue", "wed", "thu", "fri") },
                                    label = { Text("Weekdays", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = selectedDays == setOf("sat", "sun"),
                                    onClick = { selectedDays = setOf("sat", "sun") },
                                    label = { Text("Weekends", fontSize = 11.sp) }
                                )
                            }

                            // Day chips Mon to Sun
                            val weekDays = listOf(
                                "mon" to "Mon",
                                "tue" to "Tue",
                                "wed" to "Wed",
                                "thu" to "Thu",
                                "fri" to "Fri",
                                "sat" to "Sat",
                                "sun" to "Sun"
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                weekDays.forEach { (key, label) ->
                                    val isSelected = selectedDays.contains(key)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedDays = if (isSelected) selectedDays - key else selectedDays + key
                                        },
                                        label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = customColors.accent,
                                            selectedLabelColor = customColors.onAccent
                                        )
                                    )
                                }
                            }

                            // Start time & End time selectors (12-hour 15-min steps)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedCard(
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).clickable { showTimePickerForStart = true }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Start time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = MenuSchedule.formatTime12h(startTime24),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = customColors.accent
                                        )
                                    }
                                }

                                OutlinedCard(
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).clickable { showTimePickerForEnd = true }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("End time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = MenuSchedule.formatTime12h(endTime24),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = customColors.accent
                                        )
                                    }
                                }
                            }

                            if (isOvernight) {
                                Surface(
                                    color = customColors.accentTint.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "🌙 Overnight schedule: Ends after midnight on the following morning.",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = customColors.accent,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }

                            // Amber Overlap Warning (non-blocking)
                            overlapWarning?.let { warningText ->
                                Surface(
                                    color = Color(0xFFFEF3C7), // Tailwind amber-100
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = Color(0xFFD97706),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = warningText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF92400E),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section: Products in this menu
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Products in this menu",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${assignedProductIds.size} of ${menuItems.size} products selected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            TextButton(
                                onClick = {
                                    if (isAllProductsSelected) {
                                        val filteredSet = filteredProducts.map { it.id }.toSet()
                                        assignedProductIds.removeAll { it in filteredSet }
                                    } else {
                                        filteredProducts.forEach {
                                            if (!assignedProductIds.contains(it.id)) assignedProductIds.add(it.id)
                                        }
                                    }
                                }
                            ) {
                                Text(if (isAllProductsSelected) "Deselect all" else "Select all")
                            }
                        }

                        OutlinedTextField(
                            value = productSearchQuery,
                            onValueChange = { productSearchQuery = it },
                            placeholder = { Text("Search products to add...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Product selection list
            items(filteredProducts, key = { it.id }) { item ->
                val isChecked = assignedProductIds.contains(item.id)
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isChecked) customColors.accentTint.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, if (isChecked) customColors.accent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isChecked) assignedProductIds.remove(item.id) else assignedProductIds.add(item.id)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { check ->
                                if (check) assignedProductIds.add(item.id) else assignedProductIds.remove(item.id)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${FormatUtils.formatCurrency(item.price)} • ${item.subcategoryName ?: "Other"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Time Picker BottomSheet / Dialog (15-min increments, 12h)
    if (showTimePickerForStart) {
        TimePickerStepDialog(
            initialTime24 = startTime24,
            title = "Select Start Time",
            onDismiss = { showTimePickerForStart = false },
            onTimeSelected = {
                startTime24 = it
                showTimePickerForStart = false
            }
        )
    }

    if (showTimePickerForEnd) {
        TimePickerStepDialog(
            initialTime24 = endTime24,
            title = "Select End Time",
            onDismiss = { showTimePickerForEnd = false },
            onTimeSelected = {
                endTime24 = it
                showTimePickerForEnd = false
            }
        )
    }

    // Error alert
    errorMessage?.let { err ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Failed to Save Menu", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = { Text(err) },
            confirmButton = {
                Button(onClick = { errorMessage = null }) { Text("OK") }
            }
        )
    }
}

private fun parseToMinutes(time24: String): Int {
    val parts = time24.split(":")
    if (parts.size < 2) return 0
    val h = parts[0].toIntOrNull() ?: 0
    val m = parts[1].toIntOrNull() ?: 0
    return h * 60 + m
}

private fun isTimeOvernight(start: String, end: String): Boolean {
    val (sH, sM) = start.split(":").mapNotNull { it.toIntOrNull() }.let { if (it.size == 2) it[0] to it[1] else 0 to 0 }
    val (eH, eM) = end.split(":").mapNotNull { it.toIntOrNull() }.let { if (it.size == 2) it[0] to it[1] else 23 to 59 }
    return (eH * 60 + eM) < (sH * 60 + sM)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerStepDialog(
    initialTime24: String,
    title: String,
    onDismiss: () -> Unit,
    onTimeSelected: (String) -> Unit
) {
    val timeOptions = remember {
        val list = mutableListOf<String>()
        for (h in 0..23) {
            for (m in listOf(0, 15, 30, 45)) {
                list.add("%02d:%02d".format(h, m))
            }
        }
        list.add("23:59")
        list
    }

    var selectedTime by remember { mutableStateOf(initialTime24) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(timeOptions) { timeStr ->
                        val isSelected = selectedTime == timeStr
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTime = timeStr }
                                .padding(vertical = 4.dp, horizontal = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = MenuSchedule.formatTime12h(timeStr),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.AccessTime,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onTimeSelected(selectedTime) }) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
