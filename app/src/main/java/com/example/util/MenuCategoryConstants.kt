package com.example.util

import com.example.data.model.BusinessType
import com.example.data.model.MainCategoriesStore
import com.example.data.model.MainCategoryOption
import com.example.data.model.MenuCategory
import org.json.JSONArray
import org.json.JSONObject

data class ResolvedItemCategory(
    val matchedOption: MainCategoryOption?,
    val displayLabel: String,
    val isLegacyUnmatched: Boolean,
    val legacyOldValue: String,
    val storedCategoryName: String,
    val storedCategoryId: String
)

object MainCategoryConfig {

    private val DEFAULT_FOOD_OPTIONS = listOf(
        MainCategoryOption(id = "dessert", name = "Dessert", active = true, sortOrder = 1, businessType = BusinessType.FOOD),
        MainCategoryOption(id = "burgers", name = "Burgers", active = true, sortOrder = 2, businessType = BusinessType.FOOD),
        MainCategoryOption(id = "pizza", name = "Pizza", active = true, sortOrder = 3, businessType = BusinessType.FOOD),
        MainCategoryOption(id = "chicken", name = "Chicken", active = true, sortOrder = 4, businessType = BusinessType.FOOD),
        MainCategoryOption(id = "drinks", name = "Drinks", active = true, sortOrder = 5, businessType = BusinessType.FOOD),
        MainCategoryOption(id = "others", name = "Others", active = true, sortOrder = 999, businessType = BusinessType.FOOD)
    )

    private val DEFAULT_CONVENIENCE_OPTIONS = listOf(
        MainCategoryOption(id = "grocery_stores", name = "Grocery Stores", active = true, sortOrder = 1, businessType = BusinessType.CONVENIENCE),
        MainCategoryOption(id = "pharmacies", name = "Pharmacies", active = true, sortOrder = 2, businessType = BusinessType.CONVENIENCE),
        MainCategoryOption(id = "convenience_stores", name = "Convenience Stores", active = true, sortOrder = 3, businessType = BusinessType.CONVENIENCE),
        MainCategoryOption(id = "school_supplies", name = "School Supplies", active = true, sortOrder = 4, businessType = BusinessType.CONVENIENCE),
        MainCategoryOption(id = "flowers", name = "Flowers", active = true, sortOrder = 5, businessType = BusinessType.CONVENIENCE),
        MainCategoryOption(id = "pet_supplies", name = "Pet Supplies", active = true, sortOrder = 6, businessType = BusinessType.CONVENIENCE),
        MainCategoryOption(id = "beauty", name = "Beauty", active = true, sortOrder = 7, businessType = BusinessType.CONVENIENCE),
        MainCategoryOption(id = "fresh_and_frozen", name = "Fresh & Frozen", active = true, sortOrder = 8, businessType = BusinessType.CONVENIENCE),
        MainCategoryOption(id = "electronics", name = "Electronics", active = true, sortOrder = 9, businessType = BusinessType.CONVENIENCE),
        MainCategoryOption(id = "others", name = "Others", active = true, sortOrder = 999, businessType = BusinessType.CONVENIENCE)
    )

    fun builtInDefaultStore(): MainCategoriesStore = MainCategoriesStore(
        food = DEFAULT_FOOD_OPTIONS,
        convenience = DEFAULT_CONVENIENCE_OPTIONS
    )

    fun getBuiltInDefaults(type: BusinessType): List<MainCategoryOption> = when (type) {
        BusinessType.FOOD -> DEFAULT_FOOD_OPTIONS
        BusinessType.CONVENIENCE -> DEFAULT_CONVENIENCE_OPTIONS
    }

    fun normalizeCategoryKey(raw: String?): String {
        return raw?.replace("\\s+".toRegex(), "")?.lowercase().orEmpty()
    }

    fun slugifyId(name: String): String {
        return name.lowercase()
            .trim()
            .replace("&", "and")
            .replace("[^a-z0-9]+".toRegex(), "_")
            .trim('_')
            .ifBlank { "category" }
    }

    fun isOthersCategory(option: MainCategoryOption): Boolean {
        val normName = normalizeCategoryKey(option.name)
        val normId = normalizeCategoryKey(option.id)
        return normName == "others" || normId == "others"
    }

    /**
     * Returns only active categories in the admin's order, with "Others" always placed last.
     */
    fun activeOrdered(options: List<MainCategoryOption>): List<MainCategoryOption> {
        return options
            .filter { it.active && it.name.isNotBlank() }
            .withIndex()
            .sortedWith(
                compareBy<IndexedValue<MainCategoryOption>>(
                    { isOthersCategory(it.value) },
                    { it.value.sortOrder },
                    { it.index }
                )
            )
            .map { it.value }
    }

    fun matchesOption(
        candidateNameOrId: String?,
        option: MainCategoryOption
    ): Boolean {
        val norm = normalizeCategoryKey(candidateNameOrId)
        if (norm.isBlank()) return false
        if (normalizeCategoryKey(option.id) == norm) return true
        if (normalizeCategoryKey(option.name) == norm) return true
        return option.aliases.any { normalizeCategoryKey(it) == norm }
    }

    fun matchesOption(
        itemCategory: String?,
        itemCategoryId: String?,
        option: MainCategoryOption
    ): Boolean {
        return matchesOption(itemCategory, option) || matchesOption(itemCategoryId, option)
    }

    fun resolveItemCategory(
        itemCategory: String?,
        itemCategoryId: String?,
        activeCategories: List<MainCategoryOption>,
        legacyShopCategories: List<MenuCategory> = emptyList()
    ): ResolvedItemCategory {
        val rawName = itemCategory?.trim().orEmpty()
        val rawId = itemCategoryId?.trim().orEmpty()

        val matchedByName = if (rawName.isNotBlank()) {
            activeCategories.firstOrNull { matchesOption(rawName, it) }
        } else null

        val matched = matchedByName ?: if (rawId.isNotBlank()) {
            activeCategories.firstOrNull { matchesOption(rawId, it) }
        } else null

        if (matched != null) {
            return ResolvedItemCategory(
                matchedOption = matched,
                displayLabel = matched.name,
                isLegacyUnmatched = false,
                legacyOldValue = "",
                storedCategoryName = matched.name,
                storedCategoryId = matched.id
            )
        }

        val legacyDocName = if (rawId.isNotBlank()) {
            legacyShopCategories.find { it.id == rawId }?.name?.trim().orEmpty()
        } else ""
        val oldValue = rawName.ifBlank { legacyDocName }.ifBlank { rawId }

        if (oldValue.isNotBlank()) {
            return ResolvedItemCategory(
                matchedOption = null,
                displayLabel = "Others (was: $oldValue)",
                isLegacyUnmatched = true,
                legacyOldValue = oldValue,
                storedCategoryName = rawName,
                storedCategoryId = rawId
            )
        }

        return ResolvedItemCategory(
            matchedOption = null,
            displayLabel = "",
            isLegacyUnmatched = false,
            legacyOldValue = "",
            storedCategoryName = "",
            storedCategoryId = ""
        )
    }

    fun parseFromFirestoreMap(data: Map<String, Any?>?): MainCategoriesStore? {
        if (data.isNullOrEmpty()) return null

        val foodRaw = data["food"] ?: data["foodCategories"]
        val convRaw = data["convenience"] ?: data["convenienceCategories"]
        val flatRaw = data["categories"] ?: data["items"] ?: data["list"] ?: data["mainCategories"]

        val parsedFoodFromKey = if (foodRaw != null) parseEntries(foodRaw, BusinessType.FOOD) else null
        val parsedConvFromKey = if (convRaw != null) parseEntries(convRaw, BusinessType.CONVENIENCE) else null

        var parsedFoodFromFlat: MutableList<MainCategoryOption>? = null
        var parsedConvFromFlat: MutableList<MainCategoryOption>? = null

        if (flatRaw is List<*>) {
            flatRaw.forEachIndexed { idx, entry ->
                val map = entry as? Map<*, *> ?: return@forEachIndexed
                val typeStr = (map["businessType"] ?: map["type"] ?: map["serviceType"])?.toString()
                val bType = if (typeStr?.contains("conv", ignoreCase = true) == true) {
                    BusinessType.CONVENIENCE
                } else {
                    BusinessType.FOOD
                }
                val opt = parseSingleEntry(entry, idx, bType) ?: return@forEachIndexed
                if (bType == BusinessType.FOOD) {
                    if (parsedFoodFromFlat == null) parsedFoodFromFlat = mutableListOf()
                    parsedFoodFromFlat!!.add(opt)
                } else {
                    if (parsedConvFromFlat == null) parsedConvFromFlat = mutableListOf()
                    parsedConvFromFlat!!.add(opt)
                }
            }
        }

        val hasAnySource = parsedFoodFromKey != null || parsedConvFromKey != null ||
            parsedFoodFromFlat != null || parsedConvFromFlat != null
        if (!hasAnySource) return null

        val finalFood = parsedFoodFromKey ?: parsedFoodFromFlat ?: DEFAULT_FOOD_OPTIONS
        val finalConv = parsedConvFromKey ?: parsedConvFromFlat ?: DEFAULT_CONVENIENCE_OPTIONS

        return MainCategoriesStore(
            food = finalFood,
            convenience = finalConv
        )
    }

    private fun parseEntries(raw: Any?, defaultType: BusinessType): List<MainCategoryOption> {
        return when (raw) {
            is List<*> -> raw.mapIndexedNotNull { idx, entry ->
                parseSingleEntry(entry, idx, defaultType)
            }
            is Map<*, *> -> raw.entries.mapIndexedNotNull { idx, (k, v) ->
                when (v) {
                    is Map<*, *> -> parseSingleEntry(
                        v.toMutableMap().apply {
                            if (get("id") == null && k is String) put("id", k)
                        },
                        idx,
                        defaultType
                    )
                    is String -> parseSingleEntry(
                        mapOf("id" to (k?.toString() ?: ""), "name" to v),
                        idx,
                        defaultType
                    )
                    else -> null
                }
            }
            else -> emptyList()
        }
    }

    private fun parseSingleEntry(raw: Any?, index: Int, defaultType: BusinessType): MainCategoryOption? {
        return when (raw) {
            is String -> {
                val clean = raw.trim()
                if (clean.isBlank()) null
                else MainCategoryOption(
                    id = slugifyId(clean),
                    name = clean,
                    aliases = emptyList(),
                    active = true,
                    sortOrder = index,
                    businessType = defaultType
                )
            }
            is Map<*, *> -> {
                val name = ((raw["name"] ?: raw["label"] ?: raw["title"]) as? String)?.trim().orEmpty()
                if (name.isBlank()) return null
                val rawId = (raw["id"] as? String)?.trim().orEmpty()
                val id = rawId.ifBlank { slugifyId(name) }
                val active = (raw["active"] as? Boolean)
                    ?: (raw["isActive"] as? Boolean)
                    ?: (raw["visible"] as? Boolean)
                    ?: (raw["enabled"] as? Boolean)
                    ?: true
                val sortOrder = FormatUtils.parseInt(raw["sortOrder"] ?: raw["order"] ?: raw["displayOrder"]) ?: index
                val aliases = (raw["aliases"] as? List<*>)
                    ?.mapNotNull { (it as? String)?.trim()?.takeIf { s -> s.isNotEmpty() } }
                    ?: emptyList()
                val typeStr = (raw["businessType"] ?: raw["type"])?.toString()
                val bType = if (typeStr != null) BusinessType.fromString(typeStr) else defaultType
                MainCategoryOption(
                    id = id,
                    name = name,
                    aliases = aliases,
                    active = active,
                    sortOrder = sortOrder,
                    businessType = bType
                )
            }
            else -> null
        }
    }

    fun toJsonString(store: MainCategoriesStore): String {
        val root = JSONObject()
        root.put("food", optionsToJsonArray(store.food))
        root.put("convenience", optionsToJsonArray(store.convenience))
        return root.toString()
    }

    fun fromJsonString(json: String?): MainCategoriesStore? {
        if (json.isNullOrBlank()) return null
        return try {
            val root = JSONObject(json)
            val food = jsonArrayToOptions(root.optJSONArray("food"), BusinessType.FOOD)
            val conv = jsonArrayToOptions(root.optJSONArray("convenience"), BusinessType.CONVENIENCE)
            if (food.isEmpty() && conv.isEmpty()) null
            else MainCategoriesStore(
                food = food.ifEmpty { DEFAULT_FOOD_OPTIONS },
                convenience = conv.ifEmpty { DEFAULT_CONVENIENCE_OPTIONS }
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun optionsToJsonArray(options: List<MainCategoryOption>): JSONArray {
        val arr = JSONArray()
        options.forEach { opt ->
            val obj = JSONObject()
            obj.put("id", opt.id)
            obj.put("name", opt.name)
            obj.put("active", opt.active)
            obj.put("sortOrder", opt.sortOrder)
            obj.put("businessType", if (opt.businessType == BusinessType.CONVENIENCE) "convenience" else "food")
            val aliasesArr = JSONArray()
            opt.aliases.forEach { aliasesArr.put(it) }
            obj.put("aliases", aliasesArr)
            arr.put(obj)
        }
        return arr
    }

    private fun jsonArrayToOptions(arr: JSONArray?, defaultType: BusinessType): List<MainCategoryOption> {
        if (arr == null) return emptyList()
        val list = mutableListOf<MainCategoryOption>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val name = obj.optString("name").trim()
            if (name.isBlank()) continue
            val id = obj.optString("id").trim().ifBlank { slugifyId(name) }
            val active = obj.optBoolean("active", true)
            val sortOrder = obj.optInt("sortOrder", i)
            val typeStr = obj.optString("businessType", "")
            val bType = if (typeStr.isNotBlank()) BusinessType.fromString(typeStr) else defaultType
            val aliasesArr = obj.optJSONArray("aliases")
            val aliases = mutableListOf<String>()
            if (aliasesArr != null) {
                for (j in 0 until aliasesArr.length()) {
                    val a = aliasesArr.optString(j).trim()
                    if (a.isNotBlank()) aliases.add(a)
                }
            }
            list.add(
                MainCategoryOption(
                    id = id,
                    name = name,
                    aliases = aliases,
                    active = active,
                    sortOrder = sortOrder,
                    businessType = bType
                )
            )
        }
        return list
    }
}
