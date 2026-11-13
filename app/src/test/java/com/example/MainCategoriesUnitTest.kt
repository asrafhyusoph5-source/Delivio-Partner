package com.example

import com.example.data.model.BusinessType
import com.example.data.model.MainCategoriesStore
import com.example.data.model.MainCategoryOption
import com.example.data.model.MerchantSettings
import com.example.util.MainCategoryConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainCategoriesUnitTest {

    @Test
    fun builtInDefaultsHaveOthersLastForFoodAndConvenience() {
        val food = MainCategoryConfig.activeOrdered(MainCategoryConfig.getBuiltInDefaults(BusinessType.FOOD))
        val conv = MainCategoryConfig.activeOrdered(MainCategoryConfig.getBuiltInDefaults(BusinessType.CONVENIENCE))

        assertTrue(food.isNotEmpty())
        assertTrue(conv.isNotEmpty())
        assertEquals("Others", food.last().name)
        assertEquals("Others", conv.last().name)
    }

    @Test
    fun parseFromFirestoreMap_missingReturnsNull_validRespectsOrderActiveAndOthersLast() {
        assertNull(MainCategoryConfig.parseFromFirestoreMap(null))
        assertNull(MainCategoryConfig.parseFromFirestoreMap(emptyMap()))

        val firestoreData = mapOf(
            "food" to listOf(
                mapOf("id" to "others", "name" to "Others", "active" to true, "sortOrder" to 1),
                mapOf("id" to "hidden_cat", "name" to "Hidden", "active" to false, "sortOrder" to 2),
                mapOf("id" to "ramen", "name" to "Ramen", "aliases" to listOf("Noodles", "Japanese Soup"), "active" to true, "sortOrder" to 4),
                mapOf("id" to "bbq", "name" to "BBQ & Grill", "active" to true, "sortOrder" to 3)
            ),
            "convenience" to listOf(
                mapOf("id" to "snacks", "name" to "Snacks", "active" to true, "sortOrder" to 1),
                mapOf("id" to "inactive_conv", "name" to "Alcohol", "active" to false, "sortOrder" to 2),
                mapOf("id" to "others", "name" to "Others", "active" to true, "sortOrder" to 0)
            )
        )

        val store = MainCategoryConfig.parseFromFirestoreMap(firestoreData)
        assertNotNull(store)

        val activeFood = MainCategoryConfig.activeOrdered(store!!.food)
        assertEquals(listOf("BBQ & Grill", "Ramen", "Others"), activeFood.map { it.name })

        val activeConv = MainCategoryConfig.activeOrdered(store.convenience)
        assertEquals(listOf("Snacks", "Others"), activeConv.map { it.name })
    }

    @Test
    fun resolveItemCategory_matchesByIdNameAndAliasIgnoringCaseAndSpaces() {
        val active = MainCategoryConfig.activeOrdered(
            listOf(
                MainCategoryOption(id = "cat_drinks", name = "Beverages", aliases = listOf("Cold Drinks", "Soda"), active = true, sortOrder = 1),
                MainCategoryOption(id = "cat_burgers", name = "Smash Burgers", aliases = listOf("Burgers"), active = true, sortOrder = 2),
                MainCategoryOption(id = "others", name = "Others", active = true, sortOrder = 3)
            )
        )

        // Match by alias ignoring capitals and spaces
        val byAlias = MainCategoryConfig.resolveItemCategory("  cold   DRINKS ", "", active)
        assertFalse(byAlias.isLegacyUnmatched)
        assertEquals("Beverages", byAlias.displayLabel)
        assertEquals("cat_drinks", byAlias.storedCategoryId)

        // Match by id when name changed without alias
        val byId = MainCategoryConfig.resolveItemCategory("Old Burger Label", "cat_burgers", active)
        assertFalse(byId.isLegacyUnmatched)
        assertEquals("Smash Burgers", byId.displayLabel)
        assertEquals("cat_burgers", byId.storedCategoryId)

        // Match by name ignoring spaces & capitals
        val byName = MainCategoryConfig.resolveItemCategory("smashburgers", "", active)
        assertFalse(byName.isLegacyUnmatched)
        assertEquals("Smash Burgers", byName.matchedOption?.name)
    }

    @Test
    fun resolveItemCategory_deletedCategoryShowsOthersWasOldValueAndPreservesStoredFields() {
        val active = MainCategoryConfig.activeOrdered(
            listOf(
                MainCategoryOption(id = "burgers", name = "Burgers", active = true, sortOrder = 1),
                MainCategoryOption(id = "others", name = "Others", active = true, sortOrder = 2)
            )
        )

        val resolved = MainCategoryConfig.resolveItemCategory("Tacos", "tacos_legacy", active)
        assertTrue(resolved.isLegacyUnmatched)
        assertNull(resolved.matchedOption)
        assertEquals("Others (was: Tacos)", resolved.displayLabel)
        assertEquals("Tacos", resolved.storedCategoryName)
        assertEquals("tacos_legacy", resolved.storedCategoryId)
    }

    @Test
    fun jsonCacheRoundTrip_preservesAllFields() {
        val original = MainCategoriesStore(
            food = listOf(
                MainCategoryOption(id = "bbq", name = "BBQ", aliases = listOf("Grill"), active = true, sortOrder = 1, businessType = BusinessType.FOOD),
                MainCategoryOption(id = "disabled", name = "Disabled", active = false, sortOrder = 2, businessType = BusinessType.FOOD)
            ),
            convenience = listOf(
                MainCategoryOption(id = "pantry", name = "Pantry", aliases = listOf("Dry Goods"), active = true, sortOrder = 1, businessType = BusinessType.CONVENIENCE)
            )
        )

        val json = MainCategoryConfig.toJsonString(original)
        val restored = MainCategoryConfig.fromJsonString(json)

        assertNotNull(restored)
        assertEquals(2, restored!!.food.size)
        assertEquals("BBQ", restored.food[0].name)
        assertEquals(listOf("Grill"), restored.food[0].aliases)
        assertFalse(restored.food[1].active)
        assertEquals(1, restored.convenience.size)
        assertEquals(BusinessType.CONVENIENCE, restored.convenience[0].businessType)
    }

    @Test
    fun merchantSettings_hasNoBannerUrlField() {
        val fieldNames = MerchantSettings::class.java.declaredFields.map { it.name }
        assertFalse("bannerUrl field must be removed", fieldNames.contains("bannerUrl"))
    }
}
