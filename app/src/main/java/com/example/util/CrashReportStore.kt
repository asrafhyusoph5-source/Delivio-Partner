package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Process
import com.example.BuildConfig
import com.example.CrashReportActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.system.exitProcess

data class CrashReportEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestampMs: Long = System.currentTimeMillis(),
    val appVersion: String = BuildConfig.VERSION_NAME,
    val threadName: String = "",
    val exceptionType: String = "",
    val exceptionMessage: String = "",
    val stackTrace: String = "",
    val isFatal: Boolean = true,
    val documentPath: String = ""
) {
    val summaryTitle: String
        get() = buildString {
            append(exceptionType.ifBlank { "Error" })
            if (exceptionMessage.isNotBlank()) {
                append(": ")
                append(exceptionMessage)
            }
        }
}

object CrashReportStore {
    private const val FILE_NAME = "crash_reports.json"
    const val MAX_REPORTS = 5
    const val MAX_PREVIEW_LINES = 40

    private val lock = Any()
    private val _reports = MutableStateFlow<List<CrashReportEntry>>(emptyList())
    val reports: StateFlow<List<CrashReportEntry>> = _reports.asStateFlow()

    @Volatile
    private var handlerInstalled = false

    @Volatile
    private var handlingCrash = false

    fun refresh(context: Context): List<CrashReportEntry> {
        val loaded = loadReports(context)
        _reports.value = loaded
        return loaded
    }

    fun loadReports(context: Context): List<CrashReportEntry> {
        synchronized(lock) {
            return try {
                val file = File(context.applicationContext.filesDir, FILE_NAME)
                if (!file.exists()) return emptyList()
                val raw = file.readText(Charsets.UTF_8)
                if (raw.isBlank()) return emptyList()
                val arr = JSONArray(raw)
                val list = mutableListOf<CrashReportEntry>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    list.add(
                        CrashReportEntry(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            timestampMs = obj.optLong("timestampMs", 0L),
                            appVersion = obj.optString("appVersion", ""),
                            threadName = obj.optString("threadName", ""),
                            exceptionType = obj.optString("exceptionType", ""),
                            exceptionMessage = obj.optString("exceptionMessage", ""),
                            stackTrace = obj.optString("stackTrace", ""),
                            isFatal = obj.optBoolean("isFatal", true),
                            documentPath = obj.optString("documentPath", "")
                        )
                    )
                }
                list.take(MAX_REPORTS)
            } catch (_: Throwable) {
                emptyList()
            }
        }
    }

    fun saveReport(context: Context, entry: CrashReportEntry) {
        synchronized(lock) {
            try {
                val appCtx = context.applicationContext ?: context
                val existing = loadReports(appCtx)
                val updated = (listOf(entry) + existing).take(MAX_REPORTS)
                val arr = JSONArray()
                updated.forEach { item ->
                    val obj = JSONObject().apply {
                        put("id", item.id)
                        put("timestampMs", item.timestampMs)
                        put("appVersion", item.appVersion)
                        put("threadName", item.threadName)
                        put("exceptionType", item.exceptionType)
                        put("exceptionMessage", item.exceptionMessage)
                        put("stackTrace", item.stackTrace)
                        put("isFatal", item.isFatal)
                        put("documentPath", item.documentPath)
                    }
                    arr.put(obj)
                }
                val file = File(appCtx.filesDir, FILE_NAME)
                file.writeText(arr.toString(2), Charsets.UTF_8)
                _reports.value = updated
            } catch (_: Throwable) {
                // Must never throw while recording a crash
            }
        }
    }

    fun clearReports(context: Context) {
        synchronized(lock) {
            try {
                val appCtx = context.applicationContext ?: context
                val file = File(appCtx.filesDir, FILE_NAME)
                if (file.exists()) {
                    file.delete()
                }
                _reports.value = emptyList()
            } catch (_: Throwable) {
            }
        }
    }

    fun logNonFatal(
        context: Context,
        documentPath: String,
        throwable: Throwable,
        threadName: String = Thread.currentThread().name
    ): CrashReportEntry {
        val entry = createEntry(
            threadName = threadName,
            throwable = throwable,
            isFatal = false,
            documentPath = documentPath
        )
        saveReport(context, entry)
        return entry
    }

    fun createEntry(
        threadName: String,
        throwable: Throwable,
        isFatal: Boolean,
        documentPath: String = "",
        timestampMs: Long = System.currentTimeMillis(),
        appVersion: String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    ): CrashReportEntry {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val fullTrace = sw.toString()
        val type = throwable::class.java.simpleName.ifBlank { throwable::class.java.name }
        val message = throwable.message ?: throwable.localizedMessage ?: "(no message)"
        return CrashReportEntry(
            id = UUID.randomUUID().toString(),
            timestampMs = timestampMs,
            appVersion = appVersion,
            threadName = threadName,
            exceptionType = type,
            exceptionMessage = message,
            stackTrace = fullTrace,
            isFatal = isFatal,
            documentPath = documentPath
        )
    }

    fun firstLines(stackTrace: String, maxLines: Int = MAX_PREVIEW_LINES): String {
        val lines = stackTrace.lines()
        if (lines.size <= maxLines) return stackTrace
        return lines.take(maxLines).joinToString("\n")
    }

    fun formatTimestamp(timestampMs: Long): String {
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date(timestampMs))
        } catch (_: Throwable) {
            timestampMs.toString()
        }
    }

    fun formatFullReport(entry: CrashReportEntry): String {
        return buildString {
            appendLine(if (entry.isFatal) "=== Delivio Partner Crash Report ===" else "=== Delivio Partner Non-Fatal Report ===")
            appendLine("Time: ${formatTimestamp(entry.timestampMs)}")
            appendLine("App Version: ${entry.appVersion.ifBlank { "1.0" }}")
            appendLine("Thread: ${entry.threadName.ifBlank { "unknown" }}")
            if (entry.documentPath.isNotBlank()) {
                appendLine("Document Path: ${entry.documentPath}")
            }
            appendLine("Exception: ${entry.exceptionType}")
            appendLine("Message: ${entry.exceptionMessage}")
            appendLine()
            appendLine("Stack Trace:")
            append(entry.stackTrace)
        }.trimEnd()
    }

    fun installUncaughtExceptionHandler(context: Context) {
        val appCtx = context.applicationContext ?: context
        refresh(appCtx)
        if (handlerInstalled) return
        synchronized(lock) {
            if (handlerInstalled) return
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                if (handlingCrash) {
                    Process.killProcess(Process.myPid())
                    exitProcess(10)
                }
                handlingCrash = true
                try {
                    val entry = createEntry(
                        threadName = thread.name ?: "main",
                        throwable = throwable,
                        isFatal = true
                    )
                    saveReport(appCtx, entry)
                    val intent = Intent(appCtx, CrashReportActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                Intent.FLAG_ACTIVITY_NO_ANIMATION
                        )
                        putExtra(CrashReportActivity.EXTRA_EXCEPTION_SUMMARY, entry.summaryTitle)
                        putExtra(CrashReportActivity.EXTRA_STACK_TRACE, entry.stackTrace)
                        putExtra(CrashReportActivity.EXTRA_FULL_REPORT, formatFullReport(entry))
                        putExtra(
                            CrashReportActivity.EXTRA_METADATA,
                            "Time: ${formatTimestamp(entry.timestampMs)} • Version: ${entry.appVersion} • Thread: ${entry.threadName}"
                        )
                    }
                    appCtx.startActivity(intent)
                } catch (_: Throwable) {
                    // Best effort
                } finally {
                    Process.killProcess(Process.myPid())
                    exitProcess(10)
                }
            }
            handlerInstalled = true
        }
    }
}
