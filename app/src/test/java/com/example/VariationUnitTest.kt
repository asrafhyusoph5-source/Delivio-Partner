package com.example

import com.example.data.model.BusinessType
import com.example.data.model.MenuItem
import com.example.data.model.ProductVariant
import com.example.data.model.ProductVariation
import com.example.data.model.VariationOption
import com.example.util.MenuSyncEngine
import com.example.util.VariationUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VariationUnitTest {

    @Test
    fun buildCombinationDrafts_generatesCartesianInVariationOrder_andPreservesExistingPricesAndStock() {
        val black = VariationOption(id = "o_blk", name = "Black")
        val white = VariationOption(id = "o_wht", name = "White")
        val s42 = VariationOption(id = "o_42", name = "42")
        val s43 = VariationOption(id = "o_43", name = "43")
        val s44 = VariationOption(id = "o_44", name = "44")

        val v1 = ProductVariation(id = "v1", name = "Color", options = listOf(black, white))
        val v2 = ProductVariation(id = "v2", name = "Size", options = listOf(s42, s43))

        val initialDrafts = VariationUtils.buildCombinationDrafts(listOf(v1, v2), emptyList())
        assertEquals(listOf("Black / 42", "Black / 43", "White / 42", "White / 43"), initialDrafts.map { it.label })
        assertTrue(initialDrafts.all { it.existingVariant == null })

        val savedVariants = listOf(
            ProductVariant(key = "o_blk|o_42", optionIds = listOf("o_blk", "o_42"), label = "Black / 42", price = 599.0, stock = 8, salePrice = 499.0),
            ProductVariant(key = "o_blk|o_43", optionIds = listOf("o_blk", "o_43"), label = "Black / 43", price = 599.0, stock = 4),
            ProductVariant(key = "o_wht|o_42", optionIds = listOf("o_wht", "o_42"), label = "White / 42", price = 650.0, stock = 2),
            ProductVariant(key = "o_wht|o_43", optionIds = listOf("o_wht", "o_43"), label = "White / 43", price = 650.0, stock = 0)
        )

        // Add option "44" to Size: existing 4 combos keep their price/stock/salePrice, new 2 combos start null
        val updatedV2 = v2.copy(options = listOf(s42, s43, s44))
        val updatedDrafts = VariationUtils.buildCombinationDrafts(listOf(v1, updatedV2), savedVariants)
        assertEquals(6, updatedDrafts.size)

        val blk42 = updatedDrafts.first { it.label == "Black / 42" }
        assertEquals(599.0, blk42.existingVariant!!.price, 0.001)
        assertEquals(8, blk42.existingVariant!!.stock)
        assertEquals(499.0, blk42.existingVariant!!.salePrice!!, 0.001)

        val blk44 = updatedDrafts.first { it.label == "Black / 44" }
        assertNull(blk44.existingVariant)
    }

    @Test
    fun validateVariationsStep_enforcesLimitsUniquenessAndVariation1ImagesOnly() {
        val dupGroup = listOf(
            ProductVariation(id = "g1", name = "Color", options = listOf(VariationOption(id = "a", name = "Red"))),
            ProductVariation(id = "g2", name = "color", options = listOf(VariationOption(id = "b", name = "Blue")))
        )
        val dupGroupRes = VariationUtils.validateVariationsStep(dupGroup)
        assertFalse(dupGroupRes.isValid)
        assertTrue(dupGroupRes.variationNameErrors.containsKey("g2"))

        val longName = ProductVariation(id = "g1", name = "123456789012345678901", options = listOf(VariationOption(id = "a", name = "S")))
        assertFalse(VariationUtils.validateVariationsStep(listOf(longName)).isValid)

        val dupOpt = ProductVariation(
            id = "g1",
            name = "Color",
            options = listOf(VariationOption(id = "a", name = "Red"), VariationOption(id = "b", name = "RED"))
        )
        assertFalse(VariationUtils.validateVariationsStep(listOf(dupOpt)).isValid)

        val tooManyOpts = ProductVariation(
            id = "g1",
            name = "Size",
            options = (1..13).map { VariationOption(id = "s$it", name = "S$it") }
        )
        assertFalse(VariationUtils.validateVariationsStep(listOf(tooManyOpts)).isValid)

        // 12 x 12 x 2 = 288 > 200 combinations limit
        val overCombo = listOf(
            ProductVariation(id = "g1", name = "V1", options = (1..12).map { VariationOption(id = "a$it", name = "A$it") }),
            ProductVariation(id = "g2", name = "V2", options = (1..12).map { VariationOption(id = "b$it", name = "B$it") }),
            ProductVariation(id = "g3", name = "V3", options = (1..2).map { VariationOption(id = "c$it", name = "C$it") })
        )
        val overRes = VariationUtils.validateVariationsStep(overCombo)
        assertFalse(overRes.isValid)
        assertEquals(288, overRes.combinationCount)

        // Variation 1 image switch requires valid https URL on all Variation 1 options
        val imgMissing = ProductVariation(
            id = "g1",
            name = "Color",
            imageEnabled = true,
            options = listOf(
                VariationOption(id = "o1", name = "Black", imageUrl = "https://cdn.example.com/blk.jpg"),
                VariationOption(id = "o2", name = "White", imageUrl = "http://insecure.example.com/wht.jpg")
            )
        )
        val imgRes = VariationUtils.validateVariationsStep(listOf(imgMissing))
        assertFalse(imgRes.isValid)
        assertFalse(imgRes.imageErrors.containsKey("o1"))
        assertTrue(imgRes.imageErrors.containsKey("o2"))

        // normalizeVariationsForSave forces imageEnabled = false and clears imageUrl on Variation 2+
        val dirty = listOf(
            imgMissing.copy(options = listOf(VariationOption(id = "o1", name = " Black ", imageUrl = " https://cdn.example.com/blk.jpg "))),
            ProductVariation(
                id = "g2",
                name = " Size ",
                imageEnabled = true,
                options = listOf(VariationOption(id = "o3", name = " M ", imageUrl = "https://cdn.example.com/m.jpg"))
            )
        )
        val cleaned = VariationUtils.normalizeVariationsForSave(dirty)
        assertTrue(cleaned[0].imageEnabled)
        assertEquals("Black", cleaned[0].options[0].name)
        assertEquals("https://cdn.example.com/blk.jpg", cleaned[0].options[0].imageUrl)
        assertFalse(cleaned[1].imageEnabled)
        assertEquals("", cleaned[1].options[0].imageUrl)
    }

    @Test
    fun derivedFields_purchasable_andVariantSales_behavePerContract() {
        val variants = listOf(
            ProductVariant(key = "a|1", optionIds = listOf("a", "1"), label = "Black / 42", price = 100.0, stock = 3, salePrice = 75.0),
            ProductVariant(key = "b|1", optionIds = listOf("b", "1"), label = "White / 42", price = 200.0, stock = 0, salePrice = 180.0)
        )
        assertTrue(variants[0].isPurchasable)
        assertFalse(variants[1].isPurchasable)

        val derived = VariationUtils.computeDerivedFields(variants)
        assertEquals(100.0, derived.priceMin, 0.001)
        assertEquals(200.0, derived.priceMax, 0.001)
        assertEquals(3, derived.stockCount)
        assertTrue(derived.inStock)

        val zeroDerived = VariationUtils.computeDerivedFields(variants.map { it.copy(stock = 0) })
        assertEquals(0, zeroDerived.stockCount)
        assertFalse(zeroDerived.inStock)

        val now = 1_700_000_000_000L
        val item = MenuItem(
            id = "shoe1",
            name = "Running Shoes",
            businessType = BusinessType.CONVENIENCE,
            price = derived.priceMin,
            priceMax = derived.priceMax,
            stockCount = derived.stockCount,
            inStock = derived.inStock,
            lowStockAt = 5,
            variationsEnabled = true,
            variants = variants,
            saleActive = true,
            saleStartsAt = now - 60_000L,
            saleEndsAt = now + 60_000L
        )
        assertTrue(item.hasPriceRange)
        assertTrue(item.hasAnyLowStockVariant)
        assertEquals(2, item.lowStockVariants().size)
        assertTrue(item.isSaleInEffect(now))
        assertTrue(item.isVariantSaleInEffect(variants[0], now))
        assertEquals(75.0, item.lowestVariantSalePrice!!, 0.001)
        assertEquals(25, item.calculatedDiscountPercent)
        assertEquals("-25%", item.formattedDiscountPercent)
        // Expired sale window
        assertFalse(item.isSaleInEffect(now + 120_000L))
    }

    @Test
    fun adjustVariantsStock_deductsAndRestoresCleanly() {
        val variants = listOf(
            ProductVariant(key = "k1", optionIds = listOf("o1"), label = "Small", price = 50.0, stock = 5),
            ProductVariant(key = "k2", optionIds = listOf("o2"), label = "Large", price = 70.0, stock = 2)
        )
        val (afterAccept, acceptDerived) = VariationUtils.adjustVariantsStock(
            variants = variants,
            qtyByVariantKey = mapOf("k1" to 2, "k2" to 2),
            isDeduct = true
        )
        assertEquals(3, afterAccept[0].stock)
        assertEquals(0, afterAccept[1].stock)
        assertEquals(3, acceptDerived.stockCount)
        assertTrue(acceptDerived.inStock)

        val (afterZero, zeroDerived) = VariationUtils.adjustVariantsStock(
            variants = afterAccept,
            qtyByVariantKey = mapOf("k1" to 5),
            isDeduct = true
        )
        assertEquals(0, afterZero[0].stock)
        assertEquals(0, zeroDerived.stockCount)
        assertFalse(zeroDerived.inStock)

        val (afterRestore, restoreDerived) = VariationUtils.adjustVariantsStock(
            variants = afterAccept,
            qtyByVariantKey = mapOf("k1" to 2, "k2" to 2),
            isDeduct = false
        )
        assertEquals(5, afterRestore[0].stock)
        assertEquals(2, afterRestore[1].stock)
        assertEquals(7, restoreDerived.stockCount)
        assertTrue(restoreDerived.inStock)
    }

    @Test
    fun menuSyncEngine_buildCopiedItem_preservesVariationPricesAndZeroesStocks() {
        val srcOpt1 = VariationOption(id = "src_o1", name = "Black", imageUrl = "https://example.com/blk.jpg")
        val srcOpt2 = VariationOption(id = "src_o2", name = "White", imageUrl = "https://example.com/wht.jpg")
        val srcVar = ProductVariation(id = "src_g1", name = "Color", imageEnabled = true, options = listOf(srcOpt1, srcOpt2))
        val srcVariants = listOf(
            ProductVariant(key = "src_o1", optionIds = listOf("src_o1"), label = "Black", price = 199.0, stock = 12, salePrice = 149.0),
            ProductVariant(key = "src_o2", optionIds = listOf("src_o2"), label = "White", price = 249.0, stock = 5)
        )
        val srcItem = MenuItem(
            id = "src_item",
            merchantId = "m1",
            name = "Canvas Tote",
            businessType = BusinessType.CONVENIENCE,
            price = 199.0,
            priceMax = 249.0,
            stockCount = 17,
            inStock = true,
            variationsEnabled = true,
            variations = listOf(srcVar),
            variants = srcVariants,
            saleActive = true,
            salePrice = null
        )

        val copied = MenuSyncEngine.buildCopiedItem(
            sourceItem = srcItem,
            sourceMerchantId = "m1",
            targetMerchantId = "m2",
            targetSubcategoryId = null,
            targetSubcategoryName = null,
            targetMenuIds = listOf("main")
        )

        assertTrue(copied.variationsEnabled)
        assertEquals(1, copied.variations.size)
        assertEquals(2, copied.variants.size)
        assertNotEquals("src_o1", copied.variations[0].options[0].id)
        assertEquals(copied.variations[0].options[0].id, copied.variants[0].key)
        assertEquals(199.0, copied.price, 0.001)
        assertEquals(249.0, copied.priceMax!!, 0.001)
        assertEquals(0, copied.stockCount)
        assertFalse(copied.inStock)
        assertTrue(copied.variants.all { it.stock == 0 && it.salePrice == null })
        assertFalse(copied.saleActive)
    }
}
