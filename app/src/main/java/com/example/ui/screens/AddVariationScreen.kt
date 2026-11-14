package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ProductVariation
import com.example.data.model.VariationOption
import com.example.ui.theme.LocalDelivioColors
import com.example.ui.theme.StatusClosedRed
import com.example.ui.theme.StatusOpenGreen
import com.example.util.VariationUtils
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddVariationScreen(
    initialVariations: List<ProductVariation>,
    onBack: () -> Unit,
    onNext: (List<ProductVariation>) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val customColors = LocalDelivioColors.current

    var variations by remember(initialVariations) {
        mutableStateOf(
            if (initialVariations.isNotEmpty()) {
                initialVariations.take(VariationUtils.MAX_VARIATIONS)
            } else {
                listOf(
                    ProductVariation(
                        id = UUID.randomUUID().toString(),
                        name = "",
                        imageEnabled = false,
                        options = emptyList()
                    )
                )
            }
        )
    }

    var hasInteracted by remember { mutableStateOf(false) }
    val touchedGroups = remember { mutableStateMapOf<String, Boolean>() }
    val activeInlineAddGroupIds = remember { mutableStateMapOf<String, Boolean>() }
    val inlineOptionDrafts = remember { mutableStateMapOf<String, String>() }
    val inlineOptionErrors = remember { mutableStateMapOf<String, String?>() }
    val optionImageLoadFailed = remember { mutableStateMapOf<String, Boolean>() }

    val validation = remember(variations) {
        VariationUtils.validateVariationsStep(variations)
    }

    fun tryCommitInlineOption(groupId: String) {
        hasInteracted = true
        touchedGroups[groupId] = true
        val group = variations.find { it.id == groupId } ?: return
        val rawText = inlineOptionDrafts[groupId].orEmpty().trim()
        if (rawText.isEmpty()) {
            inlineOptionErrors[groupId] = "Option name cannot be empty"
            return
        }
        if (rawText.length > VariationUtils.MAX_NAME_LENGTH) {
            inlineOptionErrors[groupId] = "Max ${VariationUtils.MAX_NAME_LENGTH} characters"
            return
        }
        if (group.options.size >= VariationUtils.MAX_OPTIONS_PER_VARIATION) {
            inlineOptionErrors[groupId] = "Max ${VariationUtils.MAX_OPTIONS_PER_VARIATION} options"
            return
        }
        if (group.options.any { it.name.trim().equals(rawText, ignoreCase = true) }) {
            inlineOptionErrors[groupId] = "Option \"$rawText\" already exists"
            return
        }
        val newOption = VariationOption(
            id = UUID.randomUUID().toString(),
            name = rawText,
            imageUrl = ""
        )
        variations = variations.map { g ->
            if (g.id == groupId) g.copy(options = g.options + newOption) else g
        }
        inlineOptionDrafts[groupId] = ""
        inlineOptionErrors[groupId] = null
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("add_variation_screen"),
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("add_variation_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Add Variation",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
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
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hasInteracted && !validation.isValid && validation.errors.isNotEmpty()) {
                        Text(
                            text = validation.errors.first(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("add_variation_error_banner")
                        )
                    }
                    Button(
                        onClick = {
                            hasInteracted = true
                            variations.forEach { touchedGroups[it.id] = true }
                            if (validation.isValid) {
                                onNext(VariationUtils.normalizeVariationsForSave(variations))
                            }
                        },
                        enabled = validation.isValid,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = customColors.accent,
                            contentColor = customColors.onAccent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("next_set_stock_price_btn")
                    ) {
                        Text("Next: Set Stock and Price", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            variations.forEachIndexed { vIdx, group ->
                val vNum = vIdx + 1
                val groupTouched = hasInteracted || (touchedGroups[group.id] == true)
                val nameError = if (groupTouched) validation.variationNameErrors[group.id] else null
                val groupOptError = if (groupTouched) validation.optionErrors[group.id] else null

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("variation_card_$vIdx")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Variation $vNum",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = customColors.accent
                            )
                            if (vIdx > 0) {
                                IconButton(
                                    onClick = {
                                        hasInteracted = true
                                        variations = variations.filterNot { it.id == group.id }
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("delete_variation_group_$vIdx")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Variation $vNum",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Name input (max 20 characters)
                        OutlinedTextField(
                            value = group.name,
                            onValueChange = { next ->
                                hasInteracted = true
                                touchedGroups[group.id] = true
                                if (next.length <= VariationUtils.MAX_NAME_LENGTH) {
                                    variations = variations.map { g ->
                                        if (g.id == group.id) g.copy(name = next) else g
                                    }
                                }
                            },
                            label = { Text("Name") },
                            placeholder = { Text("e.g. Color, Size, Flavor") },
                            isError = nameError != null,
                            supportingText = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = nameError ?: "Required, unique per item",
                                        color = if (nameError != null) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${group.name.length}/${VariationUtils.MAX_NAME_LENGTH}",
                                        color = if (group.name.length >= VariationUtils.MAX_NAME_LENGTH) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("variation_name_input_$vIdx")
                        )

                        // Suggestion chips: Color, Size, Flavor, Type, Pack
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            VariationUtils.SUGGESTED_VARIATION_NAMES.forEach { suggestion ->
                                val alreadyUsedByOther = variations.any {
                                    it.id != group.id && it.name.trim().equals(suggestion, ignoreCase = true)
                                }
                                val isCurrent = group.name.trim().equals(suggestion, ignoreCase = true)
                                FilterChip(
                                    selected = isCurrent,
                                    enabled = !alreadyUsedByOther,
                                    onClick = {
                                        hasInteracted = true
                                        touchedGroups[group.id] = true
                                        variations = variations.map { g ->
                                            if (g.id == group.id) g.copy(name = suggestion) else g
                                        }
                                    },
                                    label = { Text(suggestion, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = customColors.accent,
                                        selectedLabelColor = customColors.onAccent
                                    ),
                                    modifier = Modifier.testTag("variation_suggestion_${vIdx}_${suggestion.lowercase()}")
                                )
                            }
                        }

                        // Options section
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Options (${group.options.size}/${VariationUtils.MAX_OPTIONS_PER_VARIATION})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (group.options.size > 1) {
                                Text(
                                    text = "Long press & drag to reorder",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val optionBounds = remember(group.id) { mutableStateMapOf<String, Rect>() }
                        var draggingOptionId by remember(group.id) { mutableStateOf<String?>(null) }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            group.options.forEachIndexed { oIdx, opt ->
                                val isDragging = draggingOptionId == opt.id
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isDragging) customColors.accent.copy(alpha = 0.25f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isDragging) customColors.accent
                                        else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .onGloballyPositioned { coords ->
                                            optionBounds[opt.id] = coords.boundsInParent()
                                        }
                                        .pointerInput(group.id, opt.id, group.options.size) {
                                            var cumulativeX = 0f
                                            var cumulativeY = 0f
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    draggingOptionId = opt.id
                                                    cumulativeX = 0f
                                                    cumulativeY = 0f
                                                },
                                                onDragEnd = { draggingOptionId = null },
                                                onDragCancel = { draggingOptionId = null },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    cumulativeX += dragAmount.x
                                                    cumulativeY += dragAmount.y
                                                    val currentRect = optionBounds[opt.id] ?: return@detectDragGesturesAfterLongPress
                                                    val targetX = currentRect.center.x + cumulativeX
                                                    val targetY = currentRect.center.y + cumulativeY
                                                    val targetEntry = optionBounds.entries.firstOrNull { (otherId, rect) ->
                                                        otherId != opt.id && rect.contains(androidx.compose.ui.geometry.Offset(targetX, targetY))
                                                    }
                                                    if (targetEntry != null) {
                                                        val fromIdx = group.options.indexOfFirst { it.id == opt.id }
                                                        val toIdx = group.options.indexOfFirst { it.id == targetEntry.key }
                                                        if (fromIdx != -1 && toIdx != -1 && fromIdx != toIdx) {
                                                            val mutable = group.options.toMutableList()
                                                            val moved = mutable.removeAt(fromIdx)
                                                            mutable.add(toIdx, moved)
                                                            variations = variations.map { g ->
                                                                if (g.id == group.id) g.copy(options = mutable) else g
                                                            }
                                                            cumulativeX = 0f
                                                            cumulativeY = 0f
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                        .testTag("option_chip_${vIdx}_$oIdx")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                                    ) {
                                        Text(
                                            text = opt.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = {
                                                hasInteracted = true
                                                touchedGroups[group.id] = true
                                                variations = variations.map { g ->
                                                    if (g.id == group.id) {
                                                        g.copy(options = g.options.filterNot { it.id == opt.id })
                                                    } else g
                                                }
                                            },
                                            modifier = Modifier
                                                .size(24.dp)
                                                .testTag("remove_option_${vIdx}_$oIdx")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove ${opt.name}",
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Dashed "+ Add" chip
                            if (group.options.size < VariationUtils.MAX_OPTIONS_PER_VARIATION) {
                                val outlineColor = customColors.accent
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable {
                                            hasInteracted = true
                                            activeInlineAddGroupIds[group.id] = true
                                            inlineOptionErrors[group.id] = null
                                        }
                                        .testTag("add_option_chip_$vIdx")
                                ) {
                                    androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                                        drawRoundRect(
                                            color = outlineColor,
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                                            style = Stroke(
                                                width = 1.5.dp.toPx(),
                                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                                            )
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add option",
                                            tint = customColors.accent,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "+ Add",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = customColors.accent
                                        )
                                    }
                                }
                            }
                        }

                        // Inline option input when dashed "+ Add" chip is tapped
                        if (activeInlineAddGroupIds[group.id] == true) {
                            val draft = inlineOptionDrafts[group.id].orEmpty()
                            val inlineErr = inlineOptionErrors[group.id]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = draft,
                                    onValueChange = { next ->
                                        if (next.length <= VariationUtils.MAX_NAME_LENGTH) {
                                            inlineOptionDrafts[group.id] = next
                                        }
                                        inlineOptionErrors[group.id] = null
                                    },
                                    label = { Text("Option name") },
                                    placeholder = { Text("e.g. Black, 42") },
                                    isError = inlineErr != null,
                                    supportingText = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = inlineErr ?: "Press Enter to add",
                                                color = if (inlineErr != null) MaterialTheme.colorScheme.error
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text("${draft.length}/${VariationUtils.MAX_NAME_LENGTH}")
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(
                                        onDone = { tryCommitInlineOption(group.id) }
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("inline_option_input_$vIdx")
                                )

                                IconButton(
                                    onClick = { tryCommitInlineOption(group.id) },
                                    modifier = Modifier
                                        .padding(top = 6.dp)
                                        .size(44.dp)
                                        .background(customColors.accent, CircleShape)
                                        .testTag("inline_option_confirm_btn_$vIdx")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Add option",
                                        tint = customColors.onAccent
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        activeInlineAddGroupIds[group.id] = false
                                        inlineOptionDrafts[group.id] = ""
                                        inlineOptionErrors[group.id] = null
                                    },
                                    modifier = Modifier
                                        .padding(top = 6.dp)
                                        .size(44.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            }
                        }

                        if (groupOptError != null) {
                            Text(
                                text = groupOptError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        // Image toggle ONLY for Variation 1 (vIdx == 0)
                        if (vIdx == 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            val labelVarName = group.name.trim().ifBlank { "Variation 1" }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Add image to \"$labelVarName\" variations",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "All images are required to be uploaded if toggled on",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = group.imageEnabled,
                                    onCheckedChange = { enabled ->
                                        hasInteracted = true
                                        touchedGroups[group.id] = true
                                        variations = variations.mapIndexed { idx, g ->
                                            if (idx == 0) g.copy(imageEnabled = enabled) else g
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = customColors.onAccent,
                                        checkedTrackColor = customColors.accent
                                    ),
                                    modifier = Modifier.testTag("variation_image_switch")
                                )
                            }

                            if (group.imageEnabled) {
                                if (group.options.isEmpty()) {
                                    Text(
                                        text = "Add options above to set images for each option.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        group.options.forEachIndexed { oIdx, opt ->
                                            val imgErr = if (groupTouched) validation.imageErrors[opt.id] else null
                                            val imgFailed = optionImageLoadFailed[opt.id] == true
                                            val hasUrl = opt.imageUrl.isNotBlank()
                                            val isHttpsValid = VariationUtils.isValidHttpsImageUrl(opt.imageUrl)

                                            Card(
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                                ),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (imgErr != null) MaterialTheme.colorScheme.error
                                                    else MaterialTheme.colorScheme.outlineVariant
                                                ),
                                                modifier = Modifier
                                                    .width(172.dp)
                                                    .testTag("variation_image_card_$oIdx")
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(10.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(100.dp)
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(MaterialTheme.colorScheme.surface)
                                                            .border(
                                                                1.dp,
                                                                MaterialTheme.colorScheme.outlineVariant,
                                                                RoundedCornerShape(10.dp)
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (hasUrl) {
                                                            AsyncImage(
                                                                model = opt.imageUrl.trim(),
                                                                contentDescription = opt.name,
                                                                contentScale = ContentScale.Crop,
                                                                onError = { optionImageLoadFailed[opt.id] = true },
                                                                onSuccess = { optionImageLoadFailed[opt.id] = false },
                                                                modifier = Modifier.fillMaxSize()
                                                            )
                                                        } else {
                                                            Icon(
                                                                imageVector = Icons.Default.Image,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                                modifier = Modifier.size(32.dp)
                                                            )
                                                        }
                                                    }

                                                    Text(
                                                        text = opt.name,
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )

                                                    OutlinedTextField(
                                                        value = opt.imageUrl,
                                                        onValueChange = { nextUrl ->
                                                            hasInteracted = true
                                                            touchedGroups[group.id] = true
                                                            optionImageLoadFailed[opt.id] = false
                                                            variations = variations.mapIndexed { idx, g ->
                                                                if (idx == 0) {
                                                                    g.copy(
                                                                        options = g.options.map { o ->
                                                                            if (o.id == opt.id) o.copy(imageUrl = nextUrl) else o
                                                                        }
                                                                    )
                                                                } else g
                                                            }
                                                        },
                                                        label = { Text("Image URL (https)", fontSize = 11.sp) },
                                                        placeholder = { Text("https://...", fontSize = 11.sp) },
                                                        isError = imgErr != null,
                                                        singleLine = true,
                                                        shape = RoundedCornerShape(10.dp),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .testTag("variation_option_url_$oIdx")
                                                    )

                                                    if (hasUrl) {
                                                        val showBad = imgFailed || !isHttpsValid
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = if (showBad) StatusClosedRed.copy(alpha = 0.15f)
                                                            else StatusOpenGreen.copy(alpha = 0.15f)
                                                        ) {
                                                            Text(
                                                                text = if (showBad) "Could not load" else "OK",
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (showBad) StatusClosedRed else StatusOpenGreen,
                                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                            )
                                                        }
                                                    } else if (imgErr != null) {
                                                        Text(
                                                            text = imgErr,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // "+ Add Variation" row below cards (up to 4)
            if (variations.size < VariationUtils.MAX_VARIATIONS) {
                OutlinedCard(
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, customColors.accent.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            hasInteracted = true
                            variations = variations + ProductVariation(
                                id = UUID.randomUUID().toString(),
                                name = "",
                                imageEnabled = false,
                                options = emptyList()
                            )
                        }
                        .testTag("add_variation_group_btn")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircleOutline,
                            contentDescription = "Add Variation",
                            tint = customColors.accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "+ Add Variation (${variations.size}/${VariationUtils.MAX_VARIATIONS})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = customColors.accent
                        )
                    }
                }
            }

            // Live combinations line
            val isComboOverLimit = validation.combinationCount > VariationUtils.MAX_COMBINATIONS
            val isComboZeroAfterEdit = hasInteracted && validation.combinationCount == 0
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isComboOverLimit || isComboZeroAfterEdit) StatusClosedRed.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${validation.combinationCount} combinations (maximum ${VariationUtils.MAX_COMBINATIONS})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isComboOverLimit || isComboZeroAfterEdit) StatusClosedRed
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("variation_combinations_count")
                    )
                }
            }

            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}
