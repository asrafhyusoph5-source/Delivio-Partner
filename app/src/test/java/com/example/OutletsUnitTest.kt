package com.example

import com.example.data.model.BusinessType
import com.example.data.model.MenuItem
import com.example.data.model.MerchantSettings
import com.example.data.model.Order
import com.example.data.model.ShopMenu
import com.example.data.model.Subcategory
import com.example.util.MenuSyncEngine
import com.example.util.MenuSyncResult
import com.example.util.OutletMenuSnapshot
import com.example.util.OutletsPinHelper
import com.example.util.OutletsPinSessionManager
import com.example.util.OutletsUtils
import com.example.util.PinVerifyResult
import com.example.util.SyncField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutletsUnitTest {

    @Test
    fun formatShopDisplayName_formatsWithAndWithoutBranch() {
        assertEquals("Jollibee - Palawan Rizal", OutletsUtils.formatShopDisplayName("Jollibee", "Palawan Rizal"))
        assertEquals("Jollibee", OutletsUtils.formatShopDisplayName("Jollibee", ""))
        assertEquals("Jollibee", OutletsUtils.formatShopDisplayName("Jollibee", "   "))
        assertEquals("Palawan Rizal", OutletsUtils.formatShopDisplayName("", "Palawan Rizal"))

        val withBranch = MerchantSettings(storeName = "Jollibee", branchName = "Palawan Rizal", branchCode = "PRZ")
        assertEquals("Jollibee - Palawan Rizal", withBranch.displayName)
        assertEquals("Jollibee - Palawan Rizal (PRZ)", withBranch.displayNameWithCode)

        val singleShop = MerchantSettings(storeName = "Solo Cafe")
        assertEquals("Solo Cafe", singleShop.displayName)
        assertEquals("Solo Cafe", singleShop.displayNameWithCode)
    }

    @Test
    fun menuMatchingKey_normalizesCaseAndWhitespace() {
        assertEquals("chickenjoy 1pc", OutletsUtils.menuMatchingKey("  ChickenJoy   1pc  "))
        assertEquals(
            OutletsUtils.menuMatchingKey("Super Meals"),
            OutletsUtils.menuMatchingKey("super   meals")
        )
        assertEquals("breakfast menu", OutletsUtils.menuMatchingKey("  BREAKFAST\t\n Menu "))
    }

    @Test
    fun branchValidation_checksNameAndCodeRules() {
        assertFalse(OutletsUtils.isValidBranchName("A"))
        assertTrue(OutletsUtils.isValidBranchName("AB"))
        assertTrue(OutletsUtils.isValidBranchName("Palawan Rizal"))
        assertFalse(OutletsUtils.isValidBranchName("A".repeat(41)))

        assertEquals("PRZ01", OutletsUtils.sanitizeBranchCodeInput("prz-01!"))
        assertEquals("ABCDEFGHIJ", OutletsUtils.sanitizeBranchCodeInput("abcdefghijklmno"))
        assertTrue(OutletsUtils.isValidBranchCode("PRZ"))
        assertTrue(OutletsUtils.isValidBranchCode("A1"))
        assertFalse(OutletsUtils.isValidBranchCode("A"))
        assertFalse(OutletsUtils.isValidBranchCode("pr"))
        assertFalse(OutletsUtils.isValidBranchCode("PR-1"))
    }

    @Test
    fun orderDefaults_includeOutletSnapshotFields() {
        val order = Order(
            merchantName = "Jollibee",
            branchName = "Palawan Rizal",
            branchCode = "PRZ",
            chainId = "chain_1"
        )
        assertEquals("Jollibee", order.merchantName)
        assertEquals("Palawan Rizal", order.branchName)
        assertEquals("PRZ", order.branchCode)
        assertEquals("chain_1", order.chainId)
    }

    @Test
    fun pinHelper_saltedSha256VerifiesCorrectly() {
        assertFalse(OutletsPinHelper.isValidPinFormat("123"))
        assertTrue(OutletsPinHelper.isValidPinFormat("1234"))
        assertTrue(OutletsPinHelper.isValidPinFormat("123456"))
        assertFalse(OutletsPinHelper.isValidPinFormat("1234567"))
        assertFalse(OutletsPinHelper.isValidPinFormat("12a4"))

        val salt1 = OutletsPinHelper.generateSalt()
        val salt2 = OutletsPinHelper.generateSalt()
        assertNotEquals(salt1, salt2)

        val hash = OutletsPinHelper.hashPin(salt1, "2580")
        assertEquals(64, hash.length)
        assertTrue(OutletsPinHelper.verifyPin("2580", salt1, hash))
        assertFalse(OutletsPinHelper.verifyPin("2581", salt1, hash))
        assertFalse(OutletsPinHelper.verifyPin("2580", salt2, hash))
    }

    @Test
    fun pinSessionManager_enforces10MinExpiry_2MinBackgroundLock_and5AttemptLockout() {
        var now = 1_000_000L
        val mgr = OutletsPinSessionManager(prefs = null, clock = { now })
        mgr.bindOwner("owner_1")

        val salt = "salt123"
        val hash = OutletsPinHelper.hashPin(salt, "4321")

        // 4 wrong attempts -> WrongPin with decrementing attempts
        assertEquals(PinVerifyResult.WrongPin(4), mgr.verifyPin("0000", salt, hash))
        assertEquals(PinVerifyResult.WrongPin(3), mgr.verifyPin("0000", salt, hash))
        assertEquals(PinVerifyResult.WrongPin(2), mgr.verifyPin("0000", salt, hash))
        assertEquals(PinVerifyResult.WrongPin(1), mgr.verifyPin("0000", salt, hash))

        // 5th wrong attempt -> 5 minute lockout
        val lockRes = mgr.verifyPin("0000", salt, hash)
        assertTrue(lockRes is PinVerifyResult.LockedOut)
        assertTrue(mgr.isLockedOut())
        assertEquals(300L, (lockRes as PinVerifyResult.LockedOut).remainingSeconds)

        // Even correct PIN fails while locked out
        assertTrue(mgr.verifyPin("4321", salt, hash) is PinVerifyResult.LockedOut)

        // Advance past 5-minute lockout -> succeeds & unlocks for 10 minutes
        now += 5L * 60L * 1000L + 1L
        assertFalse(mgr.isLockedOut())
        assertEquals(PinVerifyResult.Success, mgr.verifyPin("4321", salt, hash))
        assertTrue(mgr.isUnlocked())

        // Background for 90s (< 2 min) stays unlocked
        mgr.onAppBackground()
        now += 90_000L
        mgr.onAppForeground()
        assertTrue(mgr.isUnlocked())

        // Background for > 2 min locks again
        mgr.onAppBackground()
        now += 121_000L
        mgr.onAppForeground()
        assertFalse(mgr.isUnlocked())

        // Unlock again and verify 10-minute session expiry
        assertEquals(PinVerifyResult.Success, mgr.verifyPin("4321", salt, hash))
        assertTrue(mgr.isUnlocked())
        now += 10L * 60L * 1000L + 1L
        assertFalse(mgr.isUnlocked())
    }

    @Test
    fun menuSyncEngine_filtersEligibleTargets_andPlansPushPull() {
        val source = OutletMenuSnapshot(
            merchantId = "m1",
            displayName = "Jollibee - Main",
            branchCode = "MAIN",
            businessType = BusinessType.FOOD,
            status = "active",
            items = listOf(
                MenuItem(id = "i1", merchantId = "m1", name = "ChickenJoy 1pc", price = 99.0, inStock = false, stockCount = 7),
                MenuItem(id = "i2", merchantId = "m1", name = "Peach Mango Pie", price = 48.0, inStock = true)
            ),
            subcategories = listOf(Subcategory(id = "s1", name = "Chicken")),
            menus = listOf(ShopMenu(id = "main", name = "Main Menu"))
        )
        val activeSame = OutletMenuSnapshot(
            merchantId = "m2",
            displayName = "Jollibee - Rizal",
            branchCode = "RIZ",
            businessType = BusinessType.FOOD,
            status = "active",
            items = listOf(MenuItem(id = "x1", merchantId = "m2", name = "  chickenjoy   1pc ", price = 110.0)),
            subcategories = emptyList(),
            menus = emptyList()
        )
        val suspendedSame = activeSame.copy(merchantId = "m3", status = "suspended")
        val differentType = activeSame.copy(merchantId = "m4", businessType = BusinessType.CONVENIENCE)

        val eligible = MenuSyncEngine.eligibleTargets(source, listOf(source, activeSame, suspendedSame, differentType))
        assertEquals(listOf("m2"), eligible.map { it.merchantId })

        val copyPlan = MenuSyncEngine.planMissingItemsCopy(source, activeSame)
        assertEquals(1, copyPlan.alreadyExistsCount)
        assertEquals(listOf("Peach Mango Pie"), copyPlan.itemsToCopy.map { it.name })

        val copied = MenuSyncEngine.buildCopiedItem(
            sourceItem = source.items.first(),
            sourceMerchantId = "m1",
            targetMerchantId = "m2",
            targetSubcategoryId = null,
            targetSubcategoryName = "Chicken",
            targetMenuIds = listOf("main"),
            nowMs = 5555L
        )
        assertTrue(copied.inStock)
        assertEquals("m1", copied.copiedFromMerchantId)
        assertEquals("i1", copied.copiedFromItemId)
        assertEquals(5555L, copied.copiedAt)
        assertEquals(false, copied.trackStock)
    }

    @Test
    fun menuSyncEngine_syncPlanDetectsConflictsAndChunks400Ops() {
        val mine = OutletMenuSnapshot(
            merchantId = "m1",
            displayName = "Brand - A",
            branchCode = "A",
            businessType = BusinessType.FOOD,
            status = "active",
            items = listOf(
                MenuItem(id = "1", merchantId = "m1", name = "Burger", price = 100.0, description = "Classic"),
                MenuItem(id = "2", merchantId = "m1", name = "Fries", price = 50.0),
                MenuItem(id = "3", merchantId = "m1", name = "Only Mine", price = 70.0)
            ),
            subcategories = emptyList(),
            menus = emptyList()
        )
        val theirs = OutletMenuSnapshot(
            merchantId = "m2",
            displayName = "Brand - B",
            branchCode = "B",
            businessType = BusinessType.FOOD,
            status = "active",
            items = listOf(
                MenuItem(id = "10", merchantId = "m2", name = " burger ", price = 120.0, description = "Classic"),
                MenuItem(id = "20", merchantId = "m2", name = "Fries", price = 50.0),
                MenuItem(id = "30", merchantId = "m2", name = "Only Theirs", price = 80.0)
            ),
            subcategories = emptyList(),
            menus = emptyList()
        )

        val plan = MenuSyncEngine.planSync(
            mine = mine,
            theirs = theirs,
            fieldsToSync = setOf(SyncField.PRICE),
            addMissingBothWays = true
        )
        assertEquals(listOf("Only Theirs"), plan.addHereItems.map { it.name })
        assertEquals(listOf("Only Mine"), plan.addThereItems.map { it.name })
        assertEquals(1, plan.conflicts.size)
        assertEquals("burger", plan.conflicts.first().normalizedKey)
        assertEquals(SyncField.PRICE, plan.conflicts.first().diffs.first().field)
        assertEquals(1, plan.skippedInSyncCount)

        // With addMissingBothWays = false, no additions planned
        val noAddPlan = MenuSyncEngine.planSync(mine, theirs, setOf(SyncField.PRICE), addMissingBothWays = false)
        assertTrue(noAddPlan.addHereItems.isEmpty())
        assertTrue(noAddPlan.addThereItems.isEmpty())
        assertEquals(3, noAddPlan.skippedInSyncCount)

        // Chunking caps batches at 400
        val ops = (1..850).toList()
        val chunks = MenuSyncEngine.chunkForFirestoreBatches(ops)
        assertEquals(listOf(400, 400, 50), chunks.map { it.size })

        // Summary text
        val summary = MenuSyncResult("Sync Menu", added = 2, updated = 1, skipped = 1, failed = 0).toCopyableSummary()
        assertTrue(summary.contains("Added: 2"))
        assertTrue(summary.contains("Updated: 1"))
    }
}
