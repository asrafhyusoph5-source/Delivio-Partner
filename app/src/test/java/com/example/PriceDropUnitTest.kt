package com.example

import com.example.data.model.BusinessType
import com.example.data.model.MenuItem
import com.example.util.MenuSyncEngine
import com.example.util.OutletMenuSnapshot
import com.example.util.PriceDropUtils
import com.example.util.SyncField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceDropUnitTest {

    @Test
    fun calculateAndFormatDiscountPercent_computesRoundedPercent() {
        assertEquals(62, PriceDropUtils.calculateDiscountPercent(100.0, 38.0))
        assertEquals("-62%", PriceDropUtils.formatDiscountPercent(100.0, 38.0))

        // Half-up / standard rounding check: (99 - 50) / 99 * 100 = 49.49 -> 49%
        assertEquals(49, PriceDropUtils.calculateDiscountPercent(99.0, 50.0))
        assertEquals("-49%", PriceDropUtils.formatDiscountPercent(99.0, 50.0))

        // Invalid bounds produce 0 / empty string
        assertEquals(0, PriceDropUtils.calculateDiscountPercent(100.0, 100.0))
        assertEquals("", PriceDropUtils.formatDiscountPercent(100.0, 100.0))
        assertEquals("", PriceDropUtils.formatDiscountPercent(100.0, 120.0))
        assertEquals("", PriceDropUtils.formatDiscountPercent(100.0, 0.0))
        assertEquals("", PriceDropUtils.formatDiscountPercent(0.0, 10.0))
    }

    @Test
    fun validateSalePrice_enforcesStrictBounds() {
        val empty = PriceDropUtils.validateSalePrice(100.0, "")
        assertFalse(empty.isValid)
        assertNotNull(empty.errorMessage)

        val nonNumeric = PriceDropUtils.validateSalePrice(100.0, "abc")
        assertFalse(nonNumeric.isValid)

        val zero = PriceDropUtils.validateSalePrice(100.0, "0")
        assertFalse(zero.isValid)

        val negative = PriceDropUtils.validateSalePrice(100.0, "-5")
        assertFalse(negative.isValid)

        val equal = PriceDropUtils.validateSalePrice(100.0, "100")
        assertFalse(equal.isValid)

        val above = PriceDropUtils.validateSalePrice(100.0, "105.50")
        assertFalse(above.isValid)

        val valid = PriceDropUtils.validateSalePrice(100.0, "38")
        assertTrue(valid.isValid)
        assertEquals(38.0, valid.parsedPrice!!, 0.001)
        assertNull(valid.errorMessage)
    }

    @Test
    fun isSaleInEffect_enforcesActiveFlagPriceBoundsAndTimestamps() {
        val now = 1_700_000_000_000L

        val activeOpenEnded = MenuItem(
            id = "p1",
            name = "Chicken",
            price = 100.0,
            salePrice = 38.0,
            saleStartsAt = null,
            saleEndsAt = null,
            saleActive = true
        )
        assertTrue(activeOpenEnded.isSaleInEffect(now))
        assertFalse(activeOpenEnded.isSaleScheduled(now))
        assertEquals("-62%", activeOpenEnded.formattedDiscountPercent)

        val inactive = activeOpenEnded.copy(saleActive = false)
        assertFalse(inactive.isSaleInEffect(now))

        val priceAtOrBelowSale = activeOpenEnded.copy(price = 38.0)
        assertFalse(priceAtOrBelowSale.isSaleInEffect(now))

        val futureStart = activeOpenEnded.copy(
            saleStartsAt = now + 60_000L,
            saleEndsAt = now + 3_600_000L
        )
        assertFalse(futureStart.isSaleInEffect(now))
        assertTrue(futureStart.isSaleScheduled(now))

        val expired = activeOpenEnded.copy(
            saleStartsAt = now - 3_600_000L,
            saleEndsAt = now - 1_000L
        )
        assertFalse(expired.isSaleInEffect(now))
        assertFalse(expired.isSaleScheduled(now))

        val insideWindow = activeOpenEnded.copy(
            saleStartsAt = now - 60_000L,
            saleEndsAt = now + 60_000L
        )
        assertTrue(insideWindow.isSaleInEffect(now))
        assertFalse(insideWindow.isSaleScheduled(now))
    }

    @Test
    fun pushPullAndSyncMenu_neverCopyOrOverwriteSaleFields() {
        val sourceOnSale = MenuItem(
            id = "src1",
            name = "Iced Latte",
            description = "Rich espresso",
            price = 120.0,
            salePrice = 85.0,
            saleStartsAt = 1_700_000_000_000L,
            saleEndsAt = 1_700_500_000_000L,
            saleActive = true,
            category = "Drinks",
            categoryId = "drinks",
            businessType = BusinessType.FOOD
        )

        // Push / Pull copy strips sale fields so target starts without a sale
        val copied = MenuSyncEngine.buildCopiedItem(
            sourceItem = sourceOnSale,
            sourceMerchantId = "m1",
            targetMerchantId = "m2",
            targetSubcategoryId = null,
            targetSubcategoryName = null,
            targetMenuIds = listOf("main"),
            nowMs = 1_700_100_000_000L
        )
        assertNull(copied.salePrice)
        assertNull(copied.saleStartsAt)
        assertNull(copied.saleEndsAt)
        assertFalse(copied.saleActive)
        assertEquals(120.0, copied.price, 0.001)

        // Sync diff on PRICE ignores sale fields when regular prices match
        val destWithOwnSale = sourceOnSale.copy(
            id = "dst2",
            salePrice = 99.0,
            saleActive = true
        )
        val mineSnap = OutletMenuSnapshot(
            merchantId = "m1",
            displayName = "Shop A",
            branchCode = "A",
            businessType = BusinessType.FOOD,
            status = "active",
            items = listOf(sourceOnSale),
            subcategories = emptyList(),
            menus = emptyList()
        )
        val theirSnapSameRegular = mineSnap.copy(
            merchantId = "m2",
            displayName = "Shop B",
            branchCode = "B",
            items = listOf(destWithOwnSale)
        )
        val planSameRegular = MenuSyncEngine.planSync(
            mine = mineSnap,
            theirs = theirSnapSameRegular,
            fieldsToSync = setOf(SyncField.PRICE),
            addMissingBothWays = false
        )
        assertTrue(planSameRegular.conflicts.isEmpty())
        assertEquals(1, planSameRegular.skippedInSyncCount)

        // When regular price differs, only PRICE shows as a diff — sale fields are never synced
        val sourceDifferentRegular = sourceOnSale.copy(price = 150.0)
        val planDiffRegular = MenuSyncEngine.planSync(
            mine = mineSnap.copy(items = listOf(sourceDifferentRegular)),
            theirs = theirSnapSameRegular,
            fieldsToSync = setOf(SyncField.PRICE),
            addMissingBothWays = false
        )
        assertEquals(1, planDiffRegular.conflicts.size)
        assertEquals(listOf(SyncField.PRICE), planDiffRegular.conflicts.single().diffs.map { it.field })
    }
}
