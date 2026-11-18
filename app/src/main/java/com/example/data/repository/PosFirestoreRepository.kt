package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.PlatformPosSettings
import com.example.data.model.PosLog
import com.example.data.model.PosOrder
import com.example.data.model.PosShift
import com.example.util.CrashReportStore
import com.example.util.PosAnalyticsEngine
import com.example.util.PosDocumentParser
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class PosPeriodBundle(
    val orders: List<PosOrder> = emptyList(),
    val shifts: List<PosShift> = emptyList(),
    val logs: List<PosLog> = emptyList()
)

data class CachedPosDay(
    val dateKey: String,
    val orders: List<PosOrder>,
    val shifts: List<PosShift>,
    val logs: List<PosLog>
)

/**
 * Read-only Firestore repository for Delivio POS collections (`posOrders`, `posShifts`, `posLogs`).
 *
 * - Queries `posOrders` by `paidAt` range ONLY (no other Firestore filter, so no composite index is needed).
 * - Filters `status` (`paid` / `refunded`) on the device.
 * - Pages all collection reads by 500 documents via `startAfter`.
 * - Caches past completed calendar days (using `settings/platform.timezone`, default `Asia/Manila`) in memory.
 * - Wraps every document parse in its own try/catch so one corrupted record is skipped and logged non-fatally.
 */
class PosFirestoreRepository(
    private val appContext: Context,
    private val firestoreProvider: () -> FirebaseFirestore?
) {
    private val _platformPosSettings = MutableStateFlow(PlatformPosSettings())
    val platformPosSettings: StateFlow<PlatformPosSettings> = _platformPosSettings.asStateFlow()

    // Memory cache for completed past calendar days: key = "$shopId:$tzId:$dateKey"
    private val pastDayCache = ConcurrentHashMap<String, CachedPosDay>()

    // Memory cache for whether a shop has ever had any posOrders document: key = shopId
    private val shopHasPosOrdersCache = ConcurrentHashMap<String, Boolean>()

    fun updatePlatformPosSettings(settings: PlatformPosSettings) {
        _platformPosSettings.value = settings
    }

    fun clearSessionCaches() {
        pastDayCache.clear()
        shopHasPosOrdersCache.clear()
    }

    suspend fun hasAnyPosOrdersForOutlets(outletIds: List<String>): Boolean = withContext(Dispatchers.IO) {
        val distinct = outletIds.filter { it.isNotBlank() }.distinct()
        if (distinct.isEmpty()) return@withContext false
        if (distinct.any { shopHasPosOrdersCache[it] == true }) return@withContext true

        val db = firestoreProvider() ?: return@withContext false
        for (shopId in distinct) {
            val cached = shopHasPosOrdersCache[shopId]
            if (cached == true) return@withContext true
            if (cached == false) continue

            val exists = try {
                val snap = db.collection("merchants")
                    .document(shopId)
                    .collection("posOrders")
                    .limit(1)
                    .get()
                    .awaitPosTask()
                !snap.isEmpty
            } catch (ex: Exception) {
                Log.w(TAG, "Failed checking posOrders presence for $shopId: ${ex.message}")
                false
            }
            shopHasPosOrdersCache[shopId] = exists
            if (exists) return@withContext true
        }
        false
    }

    suspend fun loadPosDataForPeriod(
        outletIds: List<String>,
        startMs: Long,
        endMs: Long,
        currentTime: Long,
        tz: TimeZone,
        includeShiftsAndLogs: Boolean = true
    ): PosPeriodBundle = withContext(Dispatchers.IO) {
        val distinct = outletIds.filter { it.isNotBlank() }.distinct()
        if (distinct.isEmpty() || endMs < startMs) return@withContext PosPeriodBundle()

        val db = firestoreProvider() ?: return@withContext PosPeriodBundle()
        val (todayStartMs, _) = PosAnalyticsEngine.computeTodayBounds(currentTime, tz)
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = tz }

        val allOrders = mutableListOf<PosOrder>()
        val allShifts = mutableListOf<PosShift>()
        val allLogs = mutableListOf<PosLog>()

        for (shopId in distinct) {
            // 1. Collect past calendar days strictly before todayStartMs
            val pastRangeEnd = minOf(endMs, todayStartMs - 1L)
            if (startMs <= pastRangeEnd) {
                val pastDays = enumerateCalendarDays(startMs, pastRangeEnd, tz, dayFmt)
                val uncachedRuns = mutableListOf<MutableList<DayBucket>>()
                var currentRun: MutableList<DayBucket>? = null

                for (day in pastDays) {
                    val cacheKey = "$shopId:${tz.id}:${day.dateKey}"
                    val cached = pastDayCache[cacheKey]
                    if (cached != null) {
                        allOrders.addAll(cached.orders.filter { (it.paidAt ?: 0L) in startMs..endMs })
                        if (includeShiftsAndLogs) {
                            allShifts.addAll(cached.shifts)
                            allLogs.addAll(cached.logs)
                        }
                        currentRun = null
                    } else {
                        if (currentRun == null) {
                            currentRun = mutableListOf()
                            uncachedRuns.add(currentRun)
                        }
                        currentRun.add(day)
                    }
                }

                // Query contiguous runs of uncached past days once and populate cache per day
                for (run in uncachedRuns) {
                    val runStart = run.first().startMs
                    val runEnd = run.last().endMs
                    val fetchedOrders = queryPosOrdersPaged(db, shopId, runStart, runEnd)
                    val fetchedShifts = if (includeShiftsAndLogs) queryPosShiftsPaged(db, shopId, runStart, runEnd) else emptyList()
                    val fetchedLogs = if (includeShiftsAndLogs) queryPosLogsPaged(db, shopId, runStart, runEnd) else emptyList()

                    if (fetchedOrders.isNotEmpty()) {
                        shopHasPosOrdersCache[shopId] = true
                    }

                    val ordersByDay = fetchedOrders.groupBy { ord ->
                        dayFmt.format(Date(ord.paidAt ?: ord.createdAt ?: runStart))
                    }
                    val shiftsByDay = fetchedShifts.groupBy { sh ->
                        dayFmt.format(Date(sh.openedAt ?: sh.closedAt ?: runStart))
                    }
                    val logsByDay = fetchedLogs.groupBy { log ->
                        dayFmt.format(Date(log.at ?: runStart))
                    }

                    for (day in run) {
                        val dayOrders = ordersByDay[day.dateKey].orEmpty()
                        val dayShifts = shiftsByDay[day.dateKey].orEmpty()
                        val dayLogs = logsByDay[day.dateKey].orEmpty()
                        if (includeShiftsAndLogs) {
                            pastDayCache["$shopId:${tz.id}:${day.dateKey}"] = CachedPosDay(
                                dateKey = day.dateKey,
                                orders = dayOrders,
                                shifts = dayShifts,
                                logs = dayLogs
                            )
                        }
                        allOrders.addAll(dayOrders.filter { (it.paidAt ?: 0L) in startMs..endMs })
                        if (includeShiftsAndLogs) {
                            allShifts.addAll(dayShifts)
                            allLogs.addAll(dayLogs)
                        }
                    }
                }
            }

            // 2. Fetch today/current portion fresh if range overlaps todayStartMs..endMs
            if (endMs >= todayStartMs) {
                val liveStart = maxOf(startMs, todayStartMs)
                val liveOrders = queryPosOrdersPaged(db, shopId, liveStart, endMs)
                if (liveOrders.isNotEmpty()) {
                    shopHasPosOrdersCache[shopId] = true
                }
                allOrders.addAll(liveOrders)
                if (includeShiftsAndLogs) {
                    allShifts.addAll(queryPosShiftsPaged(db, shopId, liveStart, endMs))
                    allLogs.addAll(queryPosLogsPaged(db, shopId, liveStart, endMs))
                }
            }
        }

        PosPeriodBundle(
            orders = allOrders.distinctBy { "${it.shopId}:${it.id}" },
            shifts = allShifts.distinctBy { "${it.shopId}:${it.id}" },
            logs = allLogs.distinctBy { "${it.shopId}:${it.id}" }
        )
    }

    private suspend fun queryPosOrdersPaged(
        db: FirebaseFirestore,
        shopId: String,
        startMs: Long,
        endMs: Long
    ): List<PosOrder> {
        val result = mutableListOf<PosOrder>()
        var lastDoc: DocumentSnapshot? = null
        val startTs = Timestamp(Date(startMs))
        val endTs = Timestamp(Date(endMs))

        try {
            do {
                var query = db.collection("merchants")
                    .document(shopId)
                    .collection("posOrders")
                    .whereGreaterThanOrEqualTo("paidAt", startTs)
                    .whereLessThanOrEqualTo("paidAt", endTs)
                    .orderBy("paidAt", Query.Direction.ASCENDING)
                    .limit(PAGE_SIZE)
                if (lastDoc != null) {
                    query = query.startAfter(lastDoc)
                }
                val snap = query.get().awaitPosTask()
                for (doc in snap.documents) {
                    val parsed = PosDocumentParser.parsePosOrderSnapshotSafe(
                        doc = doc,
                        shopId = shopId,
                        onParseError = { _, path, err ->
                            CrashReportStore.logNonFatal(appContext, path, err)
                        }
                    )
                    if (parsed != null) {
                        result.add(parsed)
                    }
                }
                lastDoc = snap.documents.lastOrNull()
            } while (snap.documents.size.toLong() >= PAGE_SIZE && lastDoc != null)
        } catch (ex: Exception) {
            Log.w(TAG, "posOrders query failed for $shopId: ${ex.message}")
        }
        return result
    }

    private suspend fun queryPosShiftsPaged(
        db: FirebaseFirestore,
        shopId: String,
        startMs: Long,
        endMs: Long
    ): List<PosShift> {
        val result = mutableListOf<PosShift>()
        var lastDoc: DocumentSnapshot? = null
        // Look back 12h to catch shifts opened right before midnight that closed in range
        val lookbackStartTs = Timestamp(Date((startMs - 12 * 3600_000L).coerceAtLeast(0L)))
        val endTs = Timestamp(Date(endMs))

        try {
            do {
                var query = db.collection("merchants")
                    .document(shopId)
                    .collection("posShifts")
                    .whereGreaterThanOrEqualTo("openedAt", lookbackStartTs)
                    .whereLessThanOrEqualTo("openedAt", endTs)
                    .orderBy("openedAt", Query.Direction.ASCENDING)
                    .limit(PAGE_SIZE)
                if (lastDoc != null) {
                    query = query.startAfter(lastDoc)
                }
                val snap = query.get().awaitPosTask()
                for (doc in snap.documents) {
                    val parsed = PosDocumentParser.parsePosShiftSnapshotSafe(
                        doc = doc,
                        shopId = shopId,
                        onParseError = { _, path, err ->
                            CrashReportStore.logNonFatal(appContext, path, err)
                        }
                    )
                    if (parsed != null) {
                        val opened = parsed.openedAt ?: 0L
                        val closed = parsed.closedAt ?: 0L
                        if ((opened in startMs..endMs) || (closed in startMs..endMs)) {
                            result.add(parsed)
                        }
                    }
                }
                lastDoc = snap.documents.lastOrNull()
            } while (snap.documents.size.toLong() >= PAGE_SIZE && lastDoc != null)
        } catch (ex: Exception) {
            Log.w(TAG, "posShifts query failed for $shopId: ${ex.message}")
        }
        return result
    }

    private suspend fun queryPosLogsPaged(
        db: FirebaseFirestore,
        shopId: String,
        startMs: Long,
        endMs: Long
    ): List<PosLog> {
        val result = mutableListOf<PosLog>()
        var lastDoc: DocumentSnapshot? = null
        val startTs = Timestamp(Date(startMs))
        val endTs = Timestamp(Date(endMs))

        try {
            do {
                var query = db.collection("merchants")
                    .document(shopId)
                    .collection("posLogs")
                    .whereGreaterThanOrEqualTo("at", startTs)
                    .whereLessThanOrEqualTo("at", endTs)
                    .orderBy("at", Query.Direction.ASCENDING)
                    .limit(PAGE_SIZE)
                if (lastDoc != null) {
                    query = query.startAfter(lastDoc)
                }
                val snap = query.get().awaitPosTask()
                for (doc in snap.documents) {
                    val parsed = PosDocumentParser.parsePosLogSnapshotSafe(
                        doc = doc,
                        shopId = shopId,
                        onParseError = { _, path, err ->
                            CrashReportStore.logNonFatal(appContext, path, err)
                        }
                    )
                    if (parsed != null) {
                        result.add(parsed)
                    }
                }
                lastDoc = snap.documents.lastOrNull()
            } while (snap.documents.size.toLong() >= PAGE_SIZE && lastDoc != null)
        } catch (ex: Exception) {
            Log.w(TAG, "posLogs query failed for $shopId: ${ex.message}")
        }
        return result
    }

    private data class DayBucket(
        val dateKey: String,
        val startMs: Long,
        val endMs: Long
    )

    private fun enumerateCalendarDays(
        startMs: Long,
        endMs: Long,
        tz: TimeZone,
        dayFmt: SimpleDateFormat
    ): List<DayBucket> {
        val buckets = mutableListOf<DayBucket>()
        val cal = Calendar.getInstance(tz).apply {
            timeInMillis = startMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        while (cal.timeInMillis <= endMs) {
            val dStart = cal.timeInMillis
            val key = dayFmt.format(Date(dStart))
            val endCal = (cal.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            buckets.add(DayBucket(dateKey = key, startMs = dStart, endMs = endCal.timeInMillis))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return buckets
    }

    private suspend fun <T> Task<T>.awaitPosTask(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }.addOnFailureListener { ex ->
            if (cont.isActive) cont.resumeWithException(ex)
        }
    }

    companion object {
        private const val TAG = "PosFirestoreRepo"
        const val PAGE_SIZE = 500L
    }
}
