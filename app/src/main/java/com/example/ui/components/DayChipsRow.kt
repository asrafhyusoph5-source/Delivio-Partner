package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DelivioYellow

data class DayOption(
    val key: String,
    val label: String
)

val WEEK_DAYS_ORDERED = listOf(
    DayOption("mon", "Mon"),
    DayOption("tue", "Tue"),
    DayOption("wed", "Wed"),
    DayOption("thu", "Thu"),
    DayOption("fri", "Fri"),
    DayOption("sat", "Sat"),
    DayOption("sun", "Sun")
)

val ALL_DAY_KEYS: Set<String> = setOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
val WEEKDAY_KEYS: Set<String> = setOf("mon", "tue", "wed", "thu", "fri")
val WEEKEND_KEYS: Set<String> = setOf("sat", "sun")

fun orderedDaysList(selectedDays: Set<String>): List<String> {
    val lower = selectedDays.map { it.trim().lowercase() }.toSet()
    return WEEK_DAYS_ORDERED.map { it.key }.filter { it in lower }
}

/**
 * Reusable quick-preset + 7 weekday chips row (Mon - Sun).
 *
 * Rules:
 * - Always displays all 7 days, never scrolls sideways, never clips Sat/Sun.
 * - Single Row with 7 equal-weight chips (Modifier.weight(1f), spacedBy(4.dp), no minimum width,
 *   44 dp height, 13 sp text, maxLines = 1, softWrap = false, horizontal padding 2 dp).
 * - If fontScale > 1.3 or available width < 340 dp, switches to a 2-line FlowRow (4 chips + 3 chips)
 *   with equal widths.
 * - Selected chip: yellow with dark text. Unselected chip: white with grey outline.
 * - Minimum tap target height 44 dp (never forces minimum width).
 * - Quick chips ("Every day", "Weekdays", "Weekends") set the exact set and show as selected
 *   only when selectedDays equals that exact set.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayChipsRow(
    selectedDays: Set<String>,
    onChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    showQuickChips: Boolean = true
) {
    val cleanSelected = selectedDays.map { it.trim().lowercase() }.toSet()
    val fontScale = LocalDensity.current.fontScale

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showQuickChips) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    QuickPresetChip(
                        label = "Every day",
                        isSelected = cleanSelected == ALL_DAY_KEYS,
                        onClick = { onChange(ALL_DAY_KEYS) },
                        testTagName = "quick_chip_every_day"
                    )
                    QuickPresetChip(
                        label = "Weekdays",
                        isSelected = cleanSelected == WEEKDAY_KEYS,
                        onClick = { onChange(WEEKDAY_KEYS) },
                        testTagName = "quick_chip_weekdays"
                    )
                    QuickPresetChip(
                        label = "Weekends",
                        isSelected = cleanSelected == WEEKEND_KEYS,
                        onClick = { onChange(WEEKEND_KEYS) },
                        testTagName = "quick_chip_weekends"
                    )
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val shouldWrap = fontScale > 1.3f || maxWidth < 340.dp

            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                if (shouldWrap) {
                    val gap = 4.dp
                    val chipWidth = ((maxWidth - gap * 3 - 1.dp) / 4).coerceAtLeast(1.dp)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        maxItemsInEachRow = 4,
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WEEK_DAYS_ORDERED.forEach { day ->
                            DayChipItem(
                                day = day,
                                isSelected = cleanSelected.contains(day.key),
                                onToggle = {
                                    val next = if (cleanSelected.contains(day.key)) {
                                        cleanSelected - day.key
                                    } else {
                                        cleanSelected + day.key
                                    }
                                    onChange(next)
                                },
                                modifier = Modifier.width(chipWidth)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WEEK_DAYS_ORDERED.forEach { day ->
                            DayChipItem(
                                day = day,
                                isSelected = cleanSelected.contains(day.key),
                                onToggle = {
                                    val next = if (cleanSelected.contains(day.key)) {
                                        cleanSelected - day.key
                                    } else {
                                        cleanSelected + day.key
                                    }
                                    onChange(next)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPresetChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTagName: String
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        modifier = Modifier
            .height(44.dp)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { selected = isSelected }
            .testTag(testTagName),
        shape = shape,
        color = if (isSelected) DelivioYellow else Color.White,
        border = BorderStroke(
            1.dp,
            if (isSelected) Color(0xFFD4A000) else Color(0xFFBDBDBD)
        )
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = Color(0xFF1A1A1A),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun DayChipItem(
    day: DayOption,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .semantics { selected = isSelected }
            .testTag("day_chip_${day.key}"),
        shape = shape,
        color = if (isSelected) DelivioYellow else Color.White,
        border = BorderStroke(
            1.dp,
            if (isSelected) Color(0xFFD4A000) else Color(0xFFBDBDBD)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = day.label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = Color(0xFF1A1A1A),
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center
            )
        }
    }
}
