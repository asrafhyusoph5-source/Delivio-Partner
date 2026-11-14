package com.example.util

import com.example.data.model.ProductVariant
import com.example.data.model.ProductVariation
import com.example.data.model.VariationOption
import kotlin.math.round

data class DerivedVariantSummary(
    val priceMin: Double,
    val priceMax: Double,
    val stockCount: Int,
    val inStock: Boolean
)

data class VariantCombinationDraft(
    val key: String,
    val optionIds: List<String>,
    val label: String,
    val existingVariant: ProductVariant?
)

data class VariationValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val variationNameErrors: Map<String, String> = emptyMap(),
    val optionErrors: Map<String, String> = emptyMap(),
    val imageErrors: Map<String, String> = emptyMap(),
    val combinationCount: Int = 0
)

object VariationUtils {

    const val MAX_VARIATIONS = 4
    const val MAX_OPTIONS_PER_VARIATION = 12
    const val MAX_NAME_LENGTH = 20
    const val MAX_COMBINATIONS = 200

    val SUGGESTED_VARIATION_NAMES = listOf("Color", "Size", "Flavor", "Type", "Pack")

    fun normalizeVariationsForSave(variations: List<ProductVariation>): List<ProductVariation> {
        return variations.take(MAX_VARIATIONS).mapIndexed { idx, group ->
            val allowImage = idx == 0 && group.imageEnabled
            group.copy(
                name = group.name.trim().take(MAX_NAME_LENGTH),
                imageEnabled = allowImage,
                options = group.options.take(MAX_OPTIONS_PER_VARIATION).map { opt ->
                    opt.copy(
                        name = opt.name.trim().take(MAX_NAME_LENGTH),
                        imageUrl = if (allowImage) opt.imageUrl.trim() else ""
                    )
                }
            )
        }
    }

    fun combinationCount(variations: List<ProductVariation>): Int {
        if (variations.isEmpty()) return 0
        var total = 1L
        for (group in variations) {
            if (group.options.isEmpty()) return 0
            total *= group.options.size.toLong()
            if (total > Int.MAX_VALUE) return Int.MAX_VALUE
        }
        return total.toInt()
    }

    fun makeVariantKey(optionIds: List<String>): String = optionIds.joinToString("|")

    fun makeVariantLabel(optionNames: List<String>): String = optionNames.joinToString(" / ")

    fun isValidHttpsImageUrl(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.startsWith("https://", ignoreCase = true) &&
            trimmed.length > 8 &&
            !trimmed.any { it.isWhitespace() }
    }

    fun validateVariationsStep(variations: List<ProductVariation>): VariationValidationResult {
        val errors = mutableListOf<String>()
        val variationNameErrors = mutableMapOf<String, String>()
        val optionErrors = mutableMapOf<String, String>()
        val imageErrors = mutableMapOf<String, String>()

        if (variations.isEmpty()) {
            errors.add("Add at least 1 variation")
            return VariationValidationResult(
                isValid = false,
                errors = errors,
                combinationCount = 0
            )
        }

        if (variations.size > MAX_VARIATIONS) {
            errors.add("Maximum of $MAX_VARIATIONS variations allowed")
        }

        val seenGroupNames = mutableSetOf<String>()
        variations.forEachIndexed { gIdx, group ->
            val trimmedName = group.name.trim()
            val gNum = gIdx + 1
            when {
                trimmedName.isEmpty() -> {
                    val msg = "Variation $gNum name is required"
                    variationNameErrors[group.id] = msg
                    errors.add(msg)
                }
                trimmedName.length > MAX_NAME_LENGTH -> {
                    val msg = "Variation name must be $MAX_NAME_LENGTH characters or less"
                    variationNameErrors[group.id] = msg
                    errors.add(msg)
                }
                !seenGroupNames.add(trimmedName.lowercase()) -> {
                    val msg = "Variation name \"$trimmedName\" must be unique"
                    variationNameErrors[group.id] = msg
                    errors.add(msg)
                }
            }

            if (group.options.isEmpty()) {
                val msg = "Add at least 1 option for ${trimmedName.ifBlank { "Variation $gNum" }}"
                optionErrors[group.id] = msg
                errors.add(msg)
            } else if (group.options.size > MAX_OPTIONS_PER_VARIATION) {
                val msg = "Maximum $MAX_OPTIONS_PER_VARIATION options per variation"
                optionErrors[group.id] = msg
                errors.add(msg)
            }

            val seenOptions = mutableSetOf<String>()
            group.options.forEach { opt ->
                val optName = opt.name.trim()
                when {
                    optName.isEmpty() -> {
                        val msg = "Option name cannot be empty"
                        optionErrors[group.id] = msg
                        errors.add(msg)
                    }
                    optName.length > MAX_NAME_LENGTH -> {
                        val msg = "Option \"$optName\" exceeds $MAX_NAME_LENGTH characters"
                        optionErrors[group.id] = msg
                        errors.add(msg)
                    }
                    !seenOptions.add(optName.lowercase()) -> {
                        val msg = "Duplicate option \"$optName\" in ${trimmedName.ifBlank { "Variation $gNum" }}"
                        optionErrors[group.id] = msg
                        errors.add(msg)
                    }
                }

                if (gIdx == 0 && group.imageEnabled) {
                    if (opt.imageUrl.isBlank()) {
                        val msg = "Image URL required for \"$optName\""
                        imageErrors[opt.id] = msg
                        errors.add(msg)
                    } else if (!isValidHttpsImageUrl(opt.imageUrl)) {
                        val msg = "Must be a valid https:// URL"
                        imageErrors[opt.id] = msg
                        errors.add(msg)
                    }
                }
            }
        }

        val comboCount = combinationCount(variations)
        if (comboCount > MAX_COMBINATIONS) {
            errors.add("Too many combinations ($comboCount). Maximum is $MAX_COMBINATIONS.")
        }

        return VariationValidationResult(
            isValid = errors.isEmpty() && comboCount in 1..MAX_COMBINATIONS,
            errors = errors.distinct(),
            variationNameErrors = variationNameErrors,
            optionErrors = optionErrors,
            imageErrors = imageErrors,
            combinationCount = comboCount
        )
    }

    /**
     * Generates all Cartesian combinations in variation order, matching previously configured variants
     * by their ordered `optionIds` (or `key`) so existing prices, stocks, and sale prices survive option edits.
     */
    fun buildCombinationDrafts(
        variations: List<ProductVariation>,
        existingVariants: List<ProductVariant>
    ): List<VariantCombinationDraft> {
        val cleanGroups = normalizeVariationsForSave(variations)
        if (cleanGroups.isEmpty() || cleanGroups.any { it.options.isEmpty() }) return emptyList()

        val existingByKey = HashMap<String, ProductVariant>(existingVariants.size)
        for (v in existingVariants) {
            val k = if (v.optionIds.isNotEmpty()) makeVariantKey(v.optionIds) else v.key
            if (k.isNotBlank()) existingByKey[k] = v
        }

        var partial: List<List<VariationOption>> = listOf(emptyList())
        for (group in cleanGroups) {
            val next = ArrayList<List<VariationOption>>(partial.size * group.options.size)
            for (prefix in partial) {
                for (opt in group.options) {
                    next.add(prefix + opt)
                }
            }
            partial = next
            if (partial.size > MAX_COMBINATIONS) break
        }

        return partial.take(MAX_COMBINATIONS).map { combo ->
            val optionIds = combo.map { it.id }
            val key = makeVariantKey(optionIds)
            val label = makeVariantLabel(combo.map { it.name })
            val matched = existingByKey[key]
            VariantCombinationDraft(
                key = key,
                optionIds = optionIds,
                label = label,
                existingVariant = matched
            )
        }
    }

    fun computeDerivedFields(variants: List<ProductVariant>): DerivedVariantSummary {
        if (variants.isEmpty()) {
            return DerivedVariantSummary(
                priceMin = 0.0,
                priceMax = 0.0,
                stockCount = 0,
                inStock = false
            )
        }
        val minPrice = roundCents(variants.minOf { it.price.coerceAtLeast(0.0) })
        val maxPrice = roundCents(variants.maxOf { it.price.coerceAtLeast(0.0) })
        val totalStock = variants.sumOf { it.stock.coerceAtLeast(0) }
        return DerivedVariantSummary(
            priceMin = minPrice,
            priceMax = maxPrice,
            stockCount = totalStock,
            inStock = totalStock > 0
        )
    }

    fun formatSummaryLine(variations: List<ProductVariation>, comboCount: Int): String {
        if (variations.isEmpty()) return ""
        val parts = variations.map { group ->
            val opts = group.options.joinToString(", ") { it.name.trim() }
            "${group.name.trim()}: $opts"
        }
        return (parts + "$comboCount combinations").joinToString(" | ")
    }

    fun roundCents(value: Double): Double = round(value * 100.0) / 100.0

    /**
     * Adjusts variant stock for order acceptance (isDeduct = true) or cancel/reject after accept (isDeduct = false).
     * Keys in [qtyByVariantKeyOrLabel] match `variant.key` or `variant.label` (case-insensitive fallback).
     */
    fun adjustVariantsStock(
        variants: List<ProductVariant>,
        qtyByVariantKey: Map<String, Int>,
        qtyByVariantLabel: Map<String, Int> = emptyMap(),
        isDeduct: Boolean
    ): Pair<List<ProductVariant>, DerivedVariantSummary> {
        val updated = variants.map { v ->
            val delta = qtyByVariantKey[v.key]
                ?: qtyByVariantLabel[v.label.trim().lowercase()]
                ?: 0
            if (delta == 0) {
                v
            } else {
                val nextStock = if (isDeduct) {
                    (v.stock - delta).coerceAtLeast(0)
                } else {
                    (v.stock + delta).coerceAtLeast(0)
                }
                v.copy(stock = nextStock)
            }
        }
        return updated to computeDerivedFields(updated)
    }
}
