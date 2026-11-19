package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalDelivioColors
import com.example.util.FormatUtils
import java.util.Locale
import java.util.UUID

class EditableChoice(
    val id: String = UUID.randomUUID().toString(),
    initialName: String = "",
    initialPriceText: String = "0",
    initialAvailable: Boolean = true,
    initialPopular: Boolean = false
) {
    var name by mutableStateOf(initialName)
    var priceText by mutableStateOf(initialPriceText)
    var available by mutableStateOf(initialAvailable)
    var popular by mutableStateOf(initialPopular)
    var wasTouched by mutableStateOf(false)

    fun deepCopy(newId: String = id): EditableChoice = EditableChoice(
        id = newId,
        initialName = name,
        initialPriceText = priceText,
        initialAvailable = available,
        initialPopular = popular
    )
}

class EditableSet(
    val id: String = UUID.randomUUID().toString(),
    initialName: String = "",
    initialRequired: Boolean = true,
    initialMinSelect: Int = 1,
    initialMaxSelect: Int = 1,
    initialChoices: List<EditableChoice> = listOf(EditableChoice())
) {
    var name by mutableStateOf(initialName)
    var required by mutableStateOf(initialRequired)
    var minSelect by mutableIntStateOf(initialMinSelect)
    var maxSelect by mutableIntStateOf(initialMaxSelect)
    var wasTouched by mutableStateOf(false)
    var showAdvancedMin by mutableStateOf(false)
    val choices = mutableStateListOf<EditableChoice>().apply { addAll(initialChoices) }
}

fun parseBulkChoicesText(raw: String): List<EditableChoice> {
    val linePriceRegex = Regex("""^(.+?)\s*(?:[-–]\s*)?\+\s*[₱P]?\s*(\d+(?:\.\d{1,2})?)\s*$""", RegexOption.IGNORE_CASE)
    return raw.replace("\r\n", "\n")
        .split("\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val match = linePriceRegex.matchEntire(line)
            if (match != null) {
                val parsedName = match.groupValues[1].trim()
                val rawPrice = match.groupValues[2].trim()
                val numPrice = rawPrice.toDoubleOrNull() ?: 0.0
                val cleanPrice = if (numPrice % 1.0 == 0.0) numPrice.toInt().toString() else String.format(Locale.US, "%.2f", numPrice)
                if (parsedName.isNotEmpty()) {
                    EditableChoice(initialName = parsedName, initialPriceText = cleanPrice)
                } else null
            } else {
                EditableChoice(initialName = line, initialPriceText = "0")
            }
        }
}

fun formatChoicePriceBadge(priceText: String): String {
    val value = priceText.toDoubleOrNull() ?: 0.0
    return if (value <= 0.0) "Free" else "+${FormatUtils.formatCurrency(value)}"
}

fun buildSetLiveHint(required: Boolean, minSelect: Int, maxSelect: Int): String {
    return when {
        maxSelect <= 1 -> "Select one"
        !required || minSelect == 0 || minSelect == 1 -> "Select up to $maxSelect"
        minSelect == maxSelect -> "Select $minSelect"
        else -> "Select $minSelect to $maxSelect"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OptionSetEditorScreen(
    initialSet: EditableSet?,
    isNew: Boolean,
    existingSetNames: Set<String>,
    onSave: (EditableSet) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val customColors = LocalDelivioColors.current
    val nameSuggestions = remember {
        listOf("Choice of Flavor", "Choice of Drink", "Add-ons", "Size", "Sides")
    }

    var setName by remember(initialSet) { mutableStateOf(initialSet?.name ?: "") }
    var nameTouched by remember(initialSet) { mutableStateOf(false) }
    var required by remember(initialSet) { mutableStateOf(initialSet?.required ?: true) }
    var minSelect by remember(initialSet) {
        mutableIntStateOf(
            if (initialSet != null) {
                if (initialSet.required) initialSet.minSelect.coerceAtLeast(1) else initialSet.minSelect.coerceAtLeast(0)
            } else 1
        )
    }
    var maxSelect by remember(initialSet) {
        mutableIntStateOf(initialSet?.maxSelect?.coerceAtLeast(1) ?: 1)
    }
    var isAnyMode by remember(initialSet) {
        mutableStateOf(
            initialSet != null &&
                initialSet.choices.size > 3 &&
                initialSet.maxSelect >= initialSet.choices.size
        )
    }
    var showHigherMin by remember(initialSet) {
        mutableStateOf(
            initialSet != null &&
                initialSet.minSelect > (if (initialSet.required) 1 else 0)
        )
    }
    val choices = remember(initialSet) {
        mutableStateListOf<EditableChoice>().apply {
            if (initialSet != null && initialSet.choices.isNotEmpty()) {
                addAll(initialSet.choices.map { it.deepCopy() })
            } else {
                add(EditableChoice())
            }
        }
    }
    var hasAttemptedSave by remember(initialSet) { mutableStateOf(false) }
    var showBulkPasteDialog by remember { mutableStateOf(false) }

    val effectiveMax = if (isAnyMode) maxOf(1, choices.size) else maxOf(1, maxSelect)
    val effectiveMin = if (required) minSelect.coerceIn(1, effectiveMax) else minSelect.coerceIn(0, effectiveMax)

    val trimmedName = setName.trim()
    val isNameBlank = trimmedName.isBlank()
    val isNameTooLong = setName.length > 50
    val isNameDuplicate = trimmedName.isNotBlank() &&
        existingSetNames.any { it.equals(trimmedName, ignoreCase = true) }

    val namedChoicesCount = choices.count { it.name.trim().isNotBlank() }
    val hasBlankChoice = choices.any { it.name.trim().isBlank() }
    val hasInvalidPrice = choices.any { (it.priceText.toDoubleOrNull() ?: -1.0) < 0.0 }
    val duplicateChoiceNames = choices
        .map { it.name.trim() }
        .filter { it.isNotBlank() }
        .groupingBy { it.lowercase(Locale.US) }
        .eachCount()
        .filter { it.value > 1 }
        .keys

    val isMinExceedsMax = required && minSelect > effectiveMax
    val invalidReason: String? = when {
        isNameBlank -> "Enter a set name"
        isNameTooLong -> "Set name cannot exceed 50 characters"
        isNameDuplicate -> "Set name must be unique"
        namedChoicesCount == 0 -> "Add at least 1 named choice"
        hasBlankChoice -> "Name all choices or remove blank rows"
        hasInvalidPrice -> "Enter a valid non-negative price for each choice"
        isMinExceedsMax -> "Minimum choices cannot exceed maximum"
        else -> null
    }
    val isFormValid = invalidReason == null

    fun performSave() {
        hasAttemptedSave = true
        nameTouched = true
        choices.forEach { it.wasTouched = true }
        if (!isFormValid) return

        val finalChoices = choices.map { c ->
            val pNum = c.priceText.toDoubleOrNull() ?: 0.0
            val formattedPrice = if (pNum == 0.0) "0" else if (pNum % 1.0 == 0.0) pNum.toInt().toString() else String.format(Locale.US, "%.2f", pNum)
            EditableChoice(
                id = c.id,
                initialName = c.name.trim(),
                initialPriceText = formattedPrice,
                initialAvailable = c.available,
                initialPopular = c.popular
            )
        }
        val savedMax = if (isAnyMode) maxOf(1, finalChoices.size) else maxOf(1, maxSelect)
        val savedMin = if (required) minSelect.coerceIn(1, savedMax) else 0
        val savedSet = EditableSet(
            id = initialSet?.id ?: UUID.randomUUID().toString(),
            initialName = trimmedName,
            initialRequired = required,
            initialMinSelect = savedMin,
            initialMaxSelect = savedMax,
            initialChoices = finalChoices
        )
        savedSet.showAdvancedMin = showHigherMin
        onSave(savedSet)
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("option_set_editor_screen"),
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 2.dp
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
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(48.dp).testTag("set_editor_back_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isNew) "New Set" else "Edit Set",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("set_editor_title")
                        )
                    }

                    TextButton(
                        onClick = { performSave() },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("set_editor_top_save_btn")
                    ) {
                        Text(
                            text = "Save",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = customColors.accent
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    if (invalidReason != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .testTag("set_editor_missing_reason")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = invalidReason,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Button(
                        onClick = { performSave() },
                        enabled = isFormValid,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = customColors.accent,
                            contentColor = customColors.onAccent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_set_bottom_btn")
                    ) {
                        Text("Save set", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // CARD 1: SET NAME
            val showNameError = (nameTouched || hasAttemptedSave) && (isNameBlank || isNameTooLong || isNameDuplicate)
            SetEditorCard(title = "SET NAME") {
                OutlinedTextField(
                    value = setName,
                    onValueChange = {
                        if (it.length <= 50) {
                            setName = it
                        }
                        nameTouched = true
                    },
                    label = { Text("Set name *") },
                    placeholder = { Text("e.g. Choice of Drink") },
                    isError = showNameError,
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = when {
                                    showNameError && isNameBlank -> "Set name is required"
                                    showNameError && isNameDuplicate -> "Set name must be unique"
                                    showNameError && isNameTooLong -> "Max 50 characters"
                                    else -> "1 to 50 characters"
                                },
                                color = if (showNameError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${setName.length}/50",
                                color = if (setName.length >= 50) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("set_name_char_counter")
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("set_name_input")
                )

                Text(
                    text = "Quick suggestions",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    nameSuggestions.forEachIndexed { idx, suggestion ->
                        val isSelected = setName.trim().equals(suggestion, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                setName = suggestion
                                nameTouched = true
                            },
                            label = { Text(suggestion, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = customColors.accentTint,
                                selectedLabelColor = customColors.accent
                            ),
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .testTag("set_name_suggestion_$idx")
                        )
                    }
                }
            }

            // CARD 2: RULES
            SetEditorCard(title = "RULES") {
                // Required | Optional segmented control
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = required,
                        onClick = {
                            required = true
                            minSelect = maxOf(1, minSelect)
                            if (maxSelect < minSelect) maxSelect = minSelect
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("rules_required_btn")
                    ) {
                        Text("Required", fontWeight = FontWeight.Bold)
                    }
                    SegmentedButton(
                        selected = !required,
                        onClick = {
                            required = false
                            minSelect = 0
                            showHigherMin = false
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("rules_optional_btn")
                    ) {
                        Text("Optional", fontWeight = FontWeight.Bold)
                    }
                }

                Text(
                    text = "How many can customers choose?",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                // Quick chips: Pick 1, Up to 2, Up to 3, Any
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = !isAnyMode && maxSelect == 1,
                        onClick = {
                            isAnyMode = false
                            maxSelect = 1
                            if (minSelect > 1) minSelect = 1
                            if (required && minSelect < 1) minSelect = 1
                        },
                        label = { Text("Pick 1") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = customColors.accentTint,
                            selectedLabelColor = customColors.accent
                        ),
                        modifier = Modifier.testTag("rule_chip_pick_1")
                    )
                    FilterChip(
                        selected = !isAnyMode && maxSelect == 2,
                        onClick = {
                            isAnyMode = false
                            maxSelect = 2
                            minSelect = if (required) minSelect.coerceIn(1, 2) else 0
                        },
                        label = { Text("Up to 2") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = customColors.accentTint,
                            selectedLabelColor = customColors.accent
                        ),
                        modifier = Modifier.testTag("rule_chip_up_to_2")
                    )
                    FilterChip(
                        selected = !isAnyMode && maxSelect == 3,
                        onClick = {
                            isAnyMode = false
                            maxSelect = 3
                            minSelect = if (required) minSelect.coerceIn(1, 3) else 0
                        },
                        label = { Text("Up to 3") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = customColors.accentTint,
                            selectedLabelColor = customColors.accent
                        ),
                        modifier = Modifier.testTag("rule_chip_up_to_3")
                    )
                    FilterChip(
                        selected = isAnyMode,
                        onClick = {
                            isAnyMode = true
                            maxSelect = maxOf(1, choices.size)
                            if (minSelect > maxSelect) minSelect = if (required) 1 else 0
                        },
                        label = { Text("Any") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = customColors.accentTint,
                            selectedLabelColor = customColors.accent
                        ),
                        modifier = Modifier.testTag("rule_chip_any")
                    )
                }

                // Custom maximum stepper row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Maximum choices",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            val minAllowedMax = if (required) maxOf(1, minSelect) else 1
                            IconButton(
                                onClick = {
                                    isAnyMode = false
                                    if (effectiveMax > minAllowedMax) {
                                        maxSelect = effectiveMax - 1
                                        if (minSelect > maxSelect) minSelect = maxSelect
                                    }
                                },
                                enabled = effectiveMax > minAllowedMax,
                                modifier = Modifier.size(40.dp).testTag("rules_max_decrease")
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease maximum choices")
                            }

                            Text(
                                text = if (isAnyMode) "Any ($effectiveMax)" else "$effectiveMax",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 10.dp).testTag("rules_max_value")
                            )

                            IconButton(
                                onClick = {
                                    isAnyMode = false
                                    val upperCap = maxOf(choices.size, 20)
                                    if (effectiveMax < upperCap) {
                                        maxSelect = effectiveMax + 1
                                    }
                                },
                                enabled = effectiveMax < maxOf(choices.size, 20),
                                modifier = Modifier.size(40.dp).testTag("rules_max_increase")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase maximum choices")
                            }
                        }
                    }
                }

                // "Need a higher minimum?" text button + stepper
                TextButton(
                    onClick = { showHigherMin = !showHigherMin },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier.heightIn(min = 44.dp).testTag("toggle_higher_min_btn")
                ) {
                    Text(
                        text = if (showHigherMin) "Hide minimum choices" else "Need a higher minimum?",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                if (showHigherMin) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Minimum required", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(
                                text = "Customers must choose at least this many",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val minFloor = if (required) 1 else 0
                            OutlinedIconButton(
                                onClick = {
                                    if (minSelect > minFloor) {
                                        minSelect -= 1
                                    }
                                },
                                enabled = minSelect > minFloor,
                                shape = CircleShape,
                                modifier = Modifier.size(40.dp).testTag("rules_min_decrease")
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease minimum choices", modifier = Modifier.size(16.dp))
                            }

                            Text(
                                text = "$minSelect",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 10.dp).testTag("rules_min_value")
                            )

                            OutlinedIconButton(
                                onClick = {
                                    if (minSelect < effectiveMax) {
                                        minSelect += 1
                                        if (minSelect >= 1 && !required) {
                                            required = true
                                        }
                                    }
                                },
                                enabled = minSelect < effectiveMax,
                                shape = CircleShape,
                                modifier = Modifier.size(40.dp).testTag("rules_min_increase")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase minimum choices", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Pale-yellow hint box beneath
                val liveHint = buildSetLiveHint(required, effectiveMin, effectiveMax)
                Surface(
                    color = Color(0xFFFEF9C3),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFFDE047).copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth().testTag("rules_customer_hint_box")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF854D0E),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Customers see: $liveHint",
                            color = Color(0xFF854D0E),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // CARD 3: CHOICES
            SetEditorCard(title = "CHOICES (${choices.size})") {
                choices.forEachIndexed { cIdx, choice ->
                    key(choice.id) {
                        val isChoiceBlank = choice.name.trim().isBlank()
                        val isChoiceDup = duplicateChoiceNames.contains(choice.name.trim().lowercase(Locale.US))
                        val showChoiceNameErr = (choice.wasTouched || hasAttemptedSave) && isChoiceBlank
                        var dragAccumulated by remember { mutableFloatStateOf(0f) }

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(
                                1.dp,
                                when {
                                    showChoiceNameErr -> MaterialTheme.colorScheme.error
                                    isChoiceDup -> StatusWarningAmber
                                    else -> MaterialTheme.colorScheme.outlineVariant
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("choice_editor_card_$cIdx")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Drag/reorder handle + accessibility Up/Down buttons
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .pointerInput(choices.size, cIdx) {
                                                    detectVerticalDragGestures(
                                                        onDragEnd = { dragAccumulated = 0f },
                                                        onDragCancel = { dragAccumulated = 0f },
                                                        onVerticalDrag = { change, dragAmount ->
                                                            change.consume()
                                                            dragAccumulated += dragAmount
                                                            val thresholdPx = 90f
                                                            if (dragAccumulated > thresholdPx && cIdx < choices.size - 1) {
                                                                val moved = choices.removeAt(cIdx)
                                                                choices.add(cIdx + 1, moved)
                                                                dragAccumulated = 0f
                                                            } else if (dragAccumulated < -thresholdPx && cIdx > 0) {
                                                                val moved = choices.removeAt(cIdx)
                                                                choices.add(cIdx - 1, moved)
                                                                dragAccumulated = 0f
                                                            }
                                                        }
                                                    )
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DragHandle,
                                                contentDescription = "Reorder choice",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                if (cIdx > 0) {
                                                    val moved = choices.removeAt(cIdx)
                                                    choices.add(cIdx - 1, moved)
                                                }
                                            },
                                            enabled = cIdx > 0,
                                            modifier = Modifier.size(36.dp).testTag("choice_move_up_$cIdx")
                                        ) {
                                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move choice up", modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                if (cIdx < choices.size - 1) {
                                                    val moved = choices.removeAt(cIdx)
                                                    choices.add(cIdx + 1, moved)
                                                }
                                            },
                                            enabled = cIdx < choices.size - 1,
                                            modifier = Modifier.size(36.dp).testTag("choice_move_down_$cIdx")
                                        ) {
                                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move choice down", modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        val priceBadgeLabel = formatChoicePriceBadge(choice.priceText)
                                        val isFree = priceBadgeLabel == "Free"
                                        Surface(
                                            color = if (isFree) Color(0xFFDCFCE7) else customColors.accentTint,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.testTag("choice_price_chip_$cIdx")
                                        ) {
                                            Text(
                                                text = priceBadgeLabel,
                                                color = if (isFree) Color(0xFF166534) else customColors.accent,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                if (choices.size > 1) {
                                                    choices.removeAt(cIdx)
                                                }
                                            },
                                            enabled = choices.size > 1,
                                            modifier = Modifier.size(48.dp).testTag("choice_delete_btn_$cIdx")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete choice",
                                                tint = if (choices.size > 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    OutlinedTextField(
                                        value = choice.name,
                                        onValueChange = {
                                            choice.name = it
                                            choice.wasTouched = true
                                        },
                                        label = { Text("Choice name *") },
                                        placeholder = { Text("e.g. Original") },
                                        singleLine = true,
                                        isError = showChoiceNameErr,
                                        supportingText = if (showChoiceNameErr) {
                                            { Text("Choice name is required", color = MaterialTheme.colorScheme.error) }
                                        } else null,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1.4f).testTag("choice_name_input_$cIdx")
                                    )

                                    OutlinedTextField(
                                        value = choice.priceText,
                                        onValueChange = {
                                            choice.priceText = it
                                            choice.wasTouched = true
                                        },
                                        label = { Text("Price (₱)") },
                                        placeholder = { Text("0") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(0.9f).testTag("choice_price_input_$cIdx")
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Flame toggle "Popular"
                                    Surface(
                                        onClick = { choice.popular = !choice.popular },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (choice.popular) Color(0xFFFF5722).copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        border = BorderStroke(1.dp, if (choice.popular) Color(0xFFFF5722) else MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.heightIn(min = 48.dp).testTag("choice_popular_btn_$cIdx")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Whatshot,
                                                contentDescription = "Popular",
                                                tint = if (choice.popular) Color(0xFFFF5722) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Popular",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (choice.popular) FontWeight.Bold else FontWeight.Medium,
                                                color = if (choice.popular) Color(0xFFFF5722) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // "In stock" switch
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "In stock",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (choice.available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Switch(
                                            checked = choice.available,
                                            onCheckedChange = { choice.available = it },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = customColors.accent,
                                                checkedTrackColor = customColors.accentTint
                                            ),
                                            modifier = Modifier.testTag("choice_instock_switch_$cIdx")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (duplicateChoiceNames.isNotEmpty()) {
                    Surface(
                        color = Color(0xFFFEF3C7),
                        border = BorderStroke(1.dp, StatusWarningAmber.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("duplicate_choice_warning")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Duplicate choice names: ${duplicateChoiceNames.joinToString(", ")}",
                                color = Color(0xFFB45309),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { choices.add(EditableChoice()) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("add_choice_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+ Add choice", fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = { showBulkPasteDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("bulk_add_choices_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add several at once", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // CARD 4: LIVE PREVIEW
            val previewHint = buildSetLiveHint(required, effectiveMin, effectiveMax)
            SetEditorCard(title = "LIVE PREVIEW") {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().testTag("set_live_preview_card")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "How customers will see this",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = trimmedName.ifBlank { "Option Title" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = if (required) Color(0xFF1E1E1E) else Color(0xFF757575),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (required) "Required" else "Optional",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Text(
                            text = previewHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        val isSingleSelect = effectiveMax <= 1
                        choices.forEachIndexed { cIdx, choice ->
                            val cPrice = choice.priceText.toDoubleOrNull() ?: 0.0
                            val priceDisplay = if (cPrice <= 0.0) "Free" else "+ ${FormatUtils.formatCurrency(cPrice)}"

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSingleSelect) {
                                    RadioButton(
                                        selected = cIdx == 0 && choice.available,
                                        onClick = null,
                                        colors = RadioButtonDefaults.colors(selectedColor = customColors.accent),
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Checkbox(
                                        checked = cIdx == 0 && choice.available,
                                        onCheckedChange = null,
                                        colors = CheckboxDefaults.colors(checkedColor = customColors.accent),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = choice.name.trim().ifBlank { "Choice ${cIdx + 1}" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (choice.available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (choice.popular) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Whatshot,
                                            contentDescription = "Popular",
                                            tint = Color(0xFFFF5722),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "Popular",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFF5722)
                                        )
                                    }

                                    if (!choice.available) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "Sold out",
                                                color = MaterialTheme.colorScheme.error,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = priceDisplay,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (cPrice <= 0.0) Color(0xFF2E7D32) else customColors.accent
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showBulkPasteDialog) {
        var bulkInput by remember { mutableStateOf("") }
        val parsedPreview = remember(bulkInput) { parseBulkChoicesText(bulkInput) }

        ModalBottomSheet(
            onDismissRequest = { showBulkPasteDialog = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Add several at once",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Enter one choice per line. Add +price for paid options (e.g. Spicy +7).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = bulkInput,
                    onValueChange = { bulkInput = it },
                    placeholder = { Text("Original\nSpicy +7\nCheese +15") },
                    singleLine = false,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .testTag("bulk_paste_input")
                )
                if (parsedPreview.isNotEmpty()) {
                    Text(
                        text = "${parsedPreview.size} ${if (parsedPreview.size == 1) "choice" else "choices"} ready to add",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showBulkPasteDialog = false },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (parsedPreview.isNotEmpty()) {
                                if (choices.size == 1 && choices[0].name.isBlank()) {
                                    choices.clear()
                                }
                                choices.addAll(parsedPreview)
                            }
                            showBulkPasteDialog = false
                        },
                        enabled = parsedPreview.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = customColors.accent,
                            contentColor = customColors.onAccent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("bulk_paste_confirm_btn")
                    ) {
                        Text("Add ${parsedPreview.size}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SetEditorCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

private val StatusWarningAmber = Color(0xFFF59E0B)
