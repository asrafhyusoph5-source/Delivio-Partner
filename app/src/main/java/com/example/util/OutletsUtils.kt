package com.example.util

import android.content.SharedPreferences
import com.example.data.model.BusinessType
import com.example.data.model.MenuItem
import com.example.data.model.ProductAvailability
import com.example.data.model.ProductSet
import com.example.data.model.ShopMenu
import com.example.data.model.Subcategory
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/**
 * Pure Kotlin helpers for Multi-Outlet normalization, branch validation,
 * Outlets PIN hashing/session management, and Menu Push/Pull/Sync planning.
 */
object OutletsUtils {

    /**
     * Menu matching key: lowercased, trimmed, with repeated spaces collapsed.
     * Used for products, category classes (subcategories), and menu groups.
     */
    fun menuMatchingKey(raw: String): String {
        return raw.trim().lowercase().replace(Regex("\\s+"), " ")
    }

    /**
     * Display name everywhere customers, drivers, or merchants see a shop:
     * "<name> - <branchName>"; when branchName is empty show just the name.
     */
    fun formatShopDisplayName(name: String, branchName: String?): String {
        val cleanName = name.trim()
        val cleanBranch = branchName?.trim().orEmpty()
        return when {
            cleanName.isBlank() && cleanBranch.isNotBlank() -> cleanBranch
            cleanBranch.isNotBlank() -> "$cleanName - $cleanBranch"
            else -> cleanName
        }
    }

    fun isValidBranchName(raw: String): Boolean {
        val len = raw.trim().length
        return len in 2..40
    }

    fun sanitizeBranchCodeInput(raw: String): String {
        return raw.uppercase().replace(Regex("[^A-Z0-9]"), "").take(10)
    }

    fun isValidBranchCode(code: String): Boolean {
        val clean = code.trim()
        return clean.length in 2..10 && clean.all { it in 'A'..'Z' || it in '0'..'9' }
    }
}

object OutletsPinHelper {

    fun isValidPinFormat(pin: String): Boolean {
        return pin.length in 4..6 && pin.all { it.isDigit() }
    }

    fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hashPin(salt: String, pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = (salt + pin).toByteArray(Charsets.UTF_8)
        val hashBytes = digest.digest(input)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(pin: String, salt: String, expectedHash: String): Boolean {
        if (salt.isBlank() || expectedHash.isBlank() || !isValidPinFormat(pin)) return false
        val actualHash = hashPin(salt, pin)
        return MessageDigest.isEqual(
            actualHash.toByteArray(Charsets.UTF_8),
            expectedHash.toByteArray(Charsets.UTF_8)
        )
    }
}

sealed class PinVerifyResult {
    data object Success : PinVerifyResult()
    data class WrongPin(val remainingAttempts: Int) : PinVerifyResult()
    data class LockedOut(val remainingSeconds: Long) : PinVerifyResult()
}

/**
 * Manages Outlets PIN unlock state, 10-minute session expiry,
 * 2-minute background lock timeout, and 5-attempt / 5-minute lockout.
 */
class OutletsPinSessionManager(
    private val prefs: SharedPreferences? = null,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        const val UNLOCK_DURATION_MS = 10L * 60L * 1000L // 10 minutes
        const val BACKGROUND_LOCK_AFTER_MS = 2L * 60L * 1000L // 2 minutes
        const val LOCKOUT_DURATION_MS = 5L * 60L * 1000L // 5 minutes
        const val MAX_WRONG_ATTEMPTS = 5
    }

    private var currentOwnerUid: String = ""
    private var unlockedUntilMs: Long = 0L
    private var backgroundStartedAtMs: Long = 0L
    private var wrongAttempts: Int = 0
    private var lockedOutUntilMs: Long = 0L

    fun bindOwner(ownerUid: String) {
        if (currentOwnerUid == ownerUid) return
        currentOwnerUid = ownerUid
        unlockedUntilMs = 0L
        backgroundStartedAtMs = 0L
        if (prefs != null && ownerUid.isNotBlank()) {
            wrongAttempts = prefs.getInt("outlets_pin_wrong_$ownerUid", 0)
            lockedOutUntilMs = prefs.getLong("outlets_pin_lock_$ownerUid", 0L)
        } else {
            wrongAttempts = 0
            lockedOutUntilMs = 0L
        }
    }

    fun resetSession() {
        unlockedUntilMs = 0L
        backgroundStartedAtMs = 0L
        wrongAttempts = 0
        lockedOutUntilMs = 0L
    }

    fun lockNow() {
        unlockedUntilMs = 0L
        backgroundStartedAtMs = 0L
    }

    fun onAppBackground() {
        if (unlockedUntilMs > 0L && backgroundStartedAtMs == 0L) {
            backgroundStartedAtMs = clock()
        }
    }

    fun onAppForeground() {
        val now = clock()
        if (backgroundStartedAtMs > 0L) {
            if (now - backgroundStartedAtMs > BACKGROUND_LOCK_AFTER_MS) {
                unlockedUntilMs = 0L
            }
            backgroundStartedAtMs = 0L
        }
        if (unlockedUntilMs > 0L && now >= unlockedUntilMs) {
            unlockedUntilMs = 0L
        }
    }

    fun isUnlocked(): Boolean {
        onAppForeground()
        val now = clock()
        return unlockedUntilMs > now
    }

    fun isLockedOut(): Boolean {
        val now = clock()
        if (lockedOutUntilMs > now) return true
        if (lockedOutUntilMs in 1L..now) {
            lockedOutUntilMs = 0L
            wrongAttempts = 0
            persistLockState()
        }
        return false
    }

    fun remainingLockoutSeconds(): Long {
        val remMs = (lockedOutUntilMs - clock()).coerceAtLeast(0L)
        return (remMs + 999L) / 1000L
    }

    fun unlockDirectlyAfterCreateOrReset() {
        val now = clock()
        wrongAttempts = 0
        lockedOutUntilMs = 0L
        unlockedUntilMs = now + UNLOCK_DURATION_MS
        backgroundStartedAtMs = 0L
        persistLockState()
    }

    fun verifyPin(pin: String, salt: String, expectedHash: String): PinVerifyResult {
        val now = clock()
        if (lockedOutUntilMs > now) {
            return PinVerifyResult.LockedOut(remainingLockoutSeconds())
        } else if (lockedOutUntilMs > 0L) {
            lockedOutUntilMs = 0L
            wrongAttempts = 0
            persistLockState()
        }

        return if (OutletsPinHelper.verifyPin(pin, salt, expectedHash)) {
            wrongAttempts = 0
            lockedOutUntilMs = 0L
            unlockedUntilMs = now + UNLOCK_DURATION_MS
            backgroundStartedAtMs = 0L
            persistLockState()
            PinVerifyResult.Success
        } else {
            wrongAttempts++
            if (wrongAttempts >= MAX_WRONG_ATTEMPTS) {
                lockedOutUntilMs = now + LOCKOUT_DURATION_MS
                wrongAttempts = 0
                unlockedUntilMs = 0L
                persistLockState()
                PinVerifyResult.LockedOut(remainingLockoutSeconds())
            } else {
                persistLockState()
                PinVerifyResult.WrongPin(MAX_WRONG_ATTEMPTS - wrongAttempts)
            }
        }
    }

    private fun persistLockState() {
        if (prefs != null && currentOwnerUid.isNotBlank()) {
            prefs.edit()
                .putInt("outlets_pin_wrong_$currentOwnerUid", wrongAttempts)
                .putLong("outlets_pin_lock_$currentOwnerUid", lockedOutUntilMs)
                .apply()
        }
    }
}

// =========================================================================
// MENU PUSH / PULL / SYNC ENGINE
// =========================================================================

enum class SyncField(val label: String) {
    NAME_DESC("Name and description"),
    PRICE("Price"),
    PHOTO("Photo"),
    ITEM_SIZE("Item size"),
    CATEGORY_MENUS("Category and menus"),
    OPTIONS("Options"),
    AVAILABILITY("Availability"),
    PREP_TAGS("Prep time and tags")
}

data class OutletMenuSnapshot(
    val merchantId: String,
    val displayName: String,
    val branchCode: String,
    val businessType: BusinessType,
    val status: String,
    val items: List<MenuItem>,
    val subcategories: List<Subcategory>,
    val menus: List<ShopMenu>
) {
    val isSuspended: Boolean
        get() = !status.equals("active", ignoreCase = true)

    fun isEligibleTargetFor(source: OutletMenuSnapshot): Boolean {
        return merchantId != source.merchantId && !isSuspended && businessType == source.businessType
    }
}

data class SyncFieldDiff(
    val field: SyncField,
    val label: String,
    val myValue: String,
    val theirValue: String
)

data class SyncConflict(
    val normalizedKey: String,
    val myItem: MenuItem,
    val theirItem: MenuItem,
    val diffs: List<SyncFieldDiff>
)

enum class SyncResolution {
    USE_MINE,
    USE_THEIRS
}

data class SyncPlanPreview(
    val addHereItems: List<MenuItem>,
    val addThereItems: List<MenuItem>,
    val conflicts: List<SyncConflict>,
    val skippedInSyncCount: Int
)

data class MenuCopyPreview(
    val targetOutletId: String,
    val targetOutletName: String,
    val itemsToCopy: List<MenuItem>,
    val alreadyExistsCount: Int
)

data class MenuSyncResult(
    val operationName: String,
    val added: Int,
    val updated: Int,
    val skipped: Int,
    val failed: Int,
    val details: List<String> = emptyList()
) {
    fun toCopyableSummary(): String {
        val sb = StringBuilder()
        sb.appendLine("$operationName Summary")
        sb.appendLine("Added: $added")
        sb.appendLine("Updated: $updated")
        sb.appendLine("Skipped: $skipped")
        sb.appendLine("Failed: $failed")
        if (details.isNotEmpty()) {
            sb.appendLine()
            details.forEach { sb.appendLine("- $it") }
        }
        return sb.toString().trim()
    }
}

object MenuSyncEngine {

    const val MAX_BATCH_OPERATIONS = 400

    /**
     * Filters outlets that can receive items pushed from [source] (active, same businessType, not source).
     */
    fun eligibleTargets(source: OutletMenuSnapshot, candidates: List<OutletMenuSnapshot>): List<OutletMenuSnapshot> {
        return candidates.filter { it.isEligibleTargetFor(source) }
    }

    /**
     * Plans a Push or Pull copy: finds items present in [source] whose normalized name does not exist in [target].
     * Existing matching names in [target] are left untouched.
     */
    fun planMissingItemsCopy(
        source: OutletMenuSnapshot,
        target: OutletMenuSnapshot
    ): MenuCopyPreview {
        val targetKeys = target.items.map { OutletsUtils.menuMatchingKey(it.name) }.toSet()
        val missing = mutableListOf<MenuItem>()
        val seenKeys = mutableSetOf<String>()
        var existing = 0

        for (item in source.items) {
            val key = OutletsUtils.menuMatchingKey(item.name)
            if (key.isBlank()) continue
            if (key in targetKeys || !seenKeys.add(key)) {
                existing++
            } else {
                missing.add(item)
            }
        }
        return MenuCopyPreview(
            targetOutletId = target.merchantId,
            targetOutletName = target.displayName,
            itemsToCopy = missing,
            alreadyExistsCount = existing
        )
    }

    /**
     * Compares [mine] and [theirs] using normalized product name and the selected [fieldsToSync].
     */
    fun planSync(
        mine: OutletMenuSnapshot,
        theirs: OutletMenuSnapshot,
        fieldsToSync: Set<SyncField>,
        addMissingBothWays: Boolean
    ): SyncPlanPreview {
        val myByKey = LinkedHashMap<String, MenuItem>()
        for (item in mine.items) {
            val key = OutletsUtils.menuMatchingKey(item.name)
            if (key.isNotBlank() && !myByKey.containsKey(key)) myByKey[key] = item
        }
        val theirByKey = LinkedHashMap<String, MenuItem>()
        for (item in theirs.items) {
            val key = OutletsUtils.menuMatchingKey(item.name)
            if (key.isNotBlank() && !theirByKey.containsKey(key)) theirByKey[key] = item
        }

        val addHere = mutableListOf<MenuItem>()
        val addThere = mutableListOf<MenuItem>()
        val conflicts = mutableListOf<SyncConflict>()
        var skipped = 0

        val allKeys = LinkedHashSet<String>().apply {
            addAll(myByKey.keys)
            addAll(theirByKey.keys)
        }

        for (key in allKeys) {
            val myItem = myByKey[key]
            val theirItem = theirByKey[key]
            when {
                myItem != null && theirItem == null -> {
                    if (addMissingBothWays) addThere.add(myItem) else skipped++
                }
                myItem == null && theirItem != null -> {
                    if (addMissingBothWays) addHere.add(theirItem) else skipped++
                }
                myItem != null && theirItem != null -> {
                    val diffs = computeFieldDiffs(mine, theirs, myItem, theirItem, fieldsToSync)
                    if (diffs.isEmpty()) {
                        skipped++
                    } else {
                        conflicts.add(SyncConflict(key, myItem, theirItem, diffs))
                    }
                }
            }
        }

        return SyncPlanPreview(
            addHereItems = addHere,
            addThereItems = addThere,
            conflicts = conflicts,
            skippedInSyncCount = skipped
        )
    }

    fun resolveSubcategoryName(item: MenuItem, subcats: List<Subcategory>): String {
        if (!item.subcategoryId.isNullOrBlank()) {
            subcats.find { it.id == item.subcategoryId }?.name?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        }
        return item.subcategoryName?.trim().orEmpty()
    }

    fun resolveMenuNames(item: MenuItem, menus: List<ShopMenu>): List<String> {
        return item.getEffectiveMenuIds().map { mId ->
            if (mId == "main") "Main Menu"
            else menus.find { it.id == mId }?.name?.trim()?.ifBlank { mId } ?: mId
        }
    }

    private fun computeFieldDiffs(
        mine: OutletMenuSnapshot,
        theirs: OutletMenuSnapshot,
        myItem: MenuItem,
        theirItem: MenuItem,
        fields: Set<SyncField>
    ): List<SyncFieldDiff> {
        val diffs = mutableListOf<SyncFieldDiff>()

        if (SyncField.NAME_DESC in fields) {
            val myPair = "${myItem.name.trim()} | ${myItem.description.trim()}"
            val theirPair = "${theirItem.name.trim()} | ${theirItem.description.trim()}"
            if (myItem.name.trim() != theirItem.name.trim() || myItem.description.trim() != theirItem.description.trim()) {
                diffs.add(SyncFieldDiff(SyncField.NAME_DESC, "Name & description", myPair, theirPair))
            }
        }

        if (SyncField.PRICE in fields) {
            if (kotlin.math.abs(myItem.price - theirItem.price) > 0.009) {
                diffs.add(
                    SyncFieldDiff(
                        SyncField.PRICE,
                        "Price",
                        "₱${FormatUtils.formatCurrency(myItem.price)}",
                        "₱${FormatUtils.formatCurrency(theirItem.price)}"
                    )
                )
            }
        }

        if (SyncField.PHOTO in fields) {
            if (myItem.imageUrl.trim() != theirItem.imageUrl.trim()) {
                diffs.add(
                    SyncFieldDiff(
                        SyncField.PHOTO,
                        "Photo",
                        if (myItem.imageUrl.isBlank()) "None" else myItem.imageUrl.trim().take(36),
                        if (theirItem.imageUrl.isBlank()) "None" else theirItem.imageUrl.trim().take(36)
                    )
                )
            }
        }

        if (SyncField.ITEM_SIZE in fields) {
            if (myItem.itemSize.trim() != theirItem.itemSize.trim()) {
                diffs.add(
                    SyncFieldDiff(
                        SyncField.ITEM_SIZE,
                        "Item size",
                        myItem.itemSize.trim().ifBlank { "None" },
                        theirItem.itemSize.trim().ifBlank { "None" }
                    )
                )
            }
        }

        if (SyncField.CATEGORY_MENUS in fields) {
            val myMain = OutletsUtils.menuMatchingKey(myItem.categoryName)
            val theirMain = OutletsUtils.menuMatchingKey(theirItem.categoryName)
            val mySub = OutletsUtils.menuMatchingKey(resolveSubcategoryName(myItem, mine.subcategories))
            val theirSub = OutletsUtils.menuMatchingKey(resolveSubcategoryName(theirItem, theirs.subcategories))
            val myMenus = resolveMenuNames(myItem, mine.menus).map { OutletsUtils.menuMatchingKey(it) }.toSet()
            val theirMenus = resolveMenuNames(theirItem, theirs.menus).map { OutletsUtils.menuMatchingKey(it) }.toSet()
            if (myMain != theirMain || mySub != theirSub || myMenus != theirMenus) {
                val myStr = "${myItem.categoryName} / ${resolveSubcategoryName(myItem, mine.subcategories).ifBlank { "Other" }} / ${resolveMenuNames(myItem, mine.menus).joinToString()}"
                val theirStr = "${theirItem.categoryName} / ${resolveSubcategoryName(theirItem, theirs.subcategories).ifBlank { "Other" }} / ${resolveMenuNames(theirItem, theirs.menus).joinToString()}"
                diffs.add(SyncFieldDiff(SyncField.CATEGORY_MENUS, "Category & menus", myStr, theirStr))
            }
        }

        if (SyncField.OPTIONS in fields) {
            val mySig = normalizedSetsSignature(myItem.sets)
            val theirSig = normalizedSetsSignature(theirItem.sets)
            if (mySig != theirSig) {
                diffs.add(
                    SyncFieldDiff(
                        SyncField.OPTIONS,
                        "Options",
                        "${myItem.sets.size} set(s)",
                        "${theirItem.sets.size} set(s)"
                    )
                )
            }
        }

        if (SyncField.AVAILABILITY in fields) {
            val myAvail = normalizeAvailability(myItem.availability)
            val theirAvail = normalizeAvailability(theirItem.availability)
            if (myAvail != theirAvail) {
                diffs.add(
                    SyncFieldDiff(
                        SyncField.AVAILABILITY,
                        "Availability",
                        myItem.availability?.summary() ?: "Menu hours",
                        theirItem.availability?.summary() ?: "Menu hours"
                    )
                )
            }
        }

        if (SyncField.PREP_TAGS in fields) {
            val myTags = myItem.tags.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
            val theirTags = theirItem.tags.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
            if (myItem.prepTimeMinutes != theirItem.prepTimeMinutes || myTags != theirTags) {
                diffs.add(
                    SyncFieldDiff(
                        SyncField.PREP_TAGS,
                        "Prep & tags",
                        "${myItem.prepTimeMinutes}m · ${myItem.tags.joinToString().ifBlank { "No tags" }}",
                        "${theirItem.prepTimeMinutes}m · ${theirItem.tags.joinToString().ifBlank { "No tags" }}"
                    )
                )
            }
        }

        return diffs
    }

    private fun normalizedSetsSignature(sets: List<ProductSet>): String {
        return sets.sortedBy { it.sortOrder }.joinToString("||") { s ->
            val ch = s.choices.joinToString(",") { c ->
                "${c.name.trim()}:${"%.2f".format(c.price)}:${c.available}:${c.popular}"
            }
            "${s.name.trim()}:${s.required}:${s.minSelect}:${s.maxSelect}[$ch]"
        }
    }

    private fun normalizeAvailability(av: ProductAvailability?): String {
        if (av == null) return "ALWAYS"
        val days = av.days.map { it.trim().lowercase() }.sorted().joinToString(",")
        return "$days|${av.start.trim()}|${av.end.trim()}|${av.dateFrom.orEmpty()}|${av.dateTo.orEmpty()}"
    }

    /**
     * Clones a full MenuItem from source to target (Push / Pull / both-ways add).
     * Starts inStock = true, copies no stock counts, generates new doc & set/choice IDs,
     * and attaches provenance fields.
     */
    fun buildCopiedItem(
        sourceItem: MenuItem,
        sourceMerchantId: String,
        targetMerchantId: String,
        targetSubcategoryId: String?,
        targetSubcategoryName: String?,
        targetMenuIds: List<String>,
        nowMs: Long = System.currentTimeMillis()
    ): MenuItem {
        val copiedSets = sourceItem.sets.mapIndexed { idx, s ->
            s.copy(
                id = UUID.randomUUID().toString(),
                subcategoryId = null,
                subcategoryName = null,
                sortOrder = idx,
                choices = s.choices.map { c -> c.copy(id = UUID.randomUUID().toString()) }
            )
        }
        return MenuItem(
            id = UUID.randomUUID().toString(),
            merchantId = targetMerchantId,
            categoryId = sourceItem.categoryId,
            categoryName = sourceItem.categoryName,
            subcategoryId = targetSubcategoryId,
            subcategoryName = targetSubcategoryName,
            menuIds = targetMenuIds.ifEmpty { listOf("main") },
            name = sourceItem.name.trim(),
            description = sourceItem.description.trim(),
            price = sourceItem.price,
            inStock = true,
            soldOutReason = null,
            soldOutUntilTimestamp = null,
            businessType = sourceItem.businessType,
            iconEmoji = sourceItem.iconEmoji,
            imageUrl = sourceItem.imageUrl.trim(),
            hasSets = copiedSets.isNotEmpty(),
            sets = copiedSets,
            prepTimeMinutes = sourceItem.prepTimeMinutes,
            tags = sourceItem.tags,
            sizes = emptyList(),
            modifiers = emptyList(),
            maxQuantity = sourceItem.maxQuantity,
            sku = "",
            barcode = "",
            unit = sourceItem.unit,
            trackStock = false,
            stockCount = 50,
            lowStockAt = 5,
            itemSize = sourceItem.itemSize.trim(),
            availability = sourceItem.availability,
            sortOrder = sourceItem.sortOrder,
            copiedFromMerchantId = sourceMerchantId,
            copiedFromItemId = sourceItem.id,
            copiedAt = nowMs
        )
    }

    /**
     * Splits a flat list of write lambdas/ops cleanly into chunks of at most MAX_BATCH_OPERATIONS (400).
     */
    fun <T> chunkForFirestoreBatches(ops: List<T>): List<List<T>> {
        if (ops.isEmpty()) return emptyList()
        return ops.chunked(MAX_BATCH_OPERATIONS)
    }
}
