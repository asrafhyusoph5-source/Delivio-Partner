package com.example

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.widget.TextView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.MerchantSettings
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.DelivioPartnerTheme
import com.example.util.CrashReportEntry
import com.example.util.CrashReportStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h850dp-xxhdpi")
class CrashReporterTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        CrashReportStore.clearReports(context)
    }

    @Test
    fun crashReportStore_keepsLast5Reports_andLogsNonFatalWithDocumentPath() {
        repeat(7) { idx ->
            CrashReportStore.saveReport(
                context,
                CrashReportEntry(
                    id = "c_$idx",
                    timestampMs = 1700000000000L + idx * 1000L,
                    appVersion = "1.0",
                    threadName = "main",
                    exceptionType = "IllegalStateException",
                    exceptionMessage = "Crash #$idx",
                    stackTrace = "line1\nline2",
                    isFatal = true
                )
            )
        }

        val savedAfter7 = CrashReportStore.loadReports(context)
        assertEquals(5, savedAfter7.size)
        assertEquals("Crash #6", savedAfter7.first().exceptionMessage)
        assertEquals("Crash #2", savedAfter7.last().exceptionMessage)

        val nonFatal = CrashReportStore.logNonFatal(
            context = context,
            documentPath = "orders/bad_doc_77",
            throwable = IllegalArgumentException("Bad order field")
        )
        val afterNonFatal = CrashReportStore.loadReports(context)
        assertEquals(5, afterNonFatal.size)
        assertFalse(afterNonFatal.first().isFatal)
        assertEquals("orders/bad_doc_77", afterNonFatal.first().documentPath)
        assertEquals(nonFatal.id, afterNonFatal.first().id)
    }

    @Test
    fun crashReportStore_firstLinesCapsAt40_whileFullReportContainsAllLines() {
        val longTrace = (1..65).joinToString("\n") { "\tat com.example.Class.method(File.kt:$it)" }
        val entry = CrashReportEntry(
            timestampMs = 1700000000000L,
            appVersion = "1.0 (1)",
            threadName = "worker-1",
            exceptionType = "NullPointerException",
            exceptionMessage = "Null item",
            stackTrace = longTrace,
            isFatal = true
        )

        val preview = CrashReportStore.firstLines(entry.stackTrace, 40)
        assertEquals(40, preview.lines().size)
        assertTrue(preview.contains("File.kt:40"))
        assertFalse(preview.contains("File.kt:41"))

        val full = CrashReportStore.formatFullReport(entry)
        assertTrue(full.contains("File.kt:65"))
        assertTrue(full.contains("Thread: worker-1"))
        assertTrue(full.contains("NullPointerException"))
    }

    @Test
    fun crashReportActivity_displaysPlainReport_copiesFullReport_andSharesReport() {
        val longTrace = (1..55).joinToString("\n") { "\tat com.example.Startup.init(Startup.kt:$it)" }
        val fullReportText = "FULL CRASH REPORT HEADER\n$longTrace"
        val intent = Intent(context, CrashReportActivity::class.java).apply {
            putExtra(CrashReportActivity.EXTRA_EXCEPTION_SUMMARY, "RuntimeException: Startup failure")
            putExtra(CrashReportActivity.EXTRA_STACK_TRACE, longTrace)
            putExtra(CrashReportActivity.EXTRA_FULL_REPORT, fullReportText)
            putExtra(CrashReportActivity.EXTRA_METADATA, "Time: 2026-01-01 • Version: 1.0 • Thread: main")
        }

        val activity = Robolectric.buildActivity(CrashReportActivity::class.java, intent).setup().get()
        val root = activity.window.decorView

        val titleView = root.findViewWithTag<TextView>(CrashReportActivity.TAG_TITLE)
        val summaryView = root.findViewWithTag<TextView>(CrashReportActivity.TAG_EXCEPTION_SUMMARY)
        val traceView = root.findViewWithTag<TextView>(CrashReportActivity.TAG_STACK_TRACE_BOX)
        val copyBtn = root.findViewWithTag<TextView>(CrashReportActivity.TAG_COPY_BTN)
        val shareBtn = root.findViewWithTag<TextView>(CrashReportActivity.TAG_SHARE_BTN)
        val restartBtn = root.findViewWithTag<TextView>(CrashReportActivity.TAG_RESTART_BTN)

        assertNotNull(titleView)
        assertEquals("The app closed unexpectedly", titleView.text.toString())
        assertEquals("RuntimeException: Startup failure", summaryView.text.toString())
        assertTrue(summaryView.typeface.isBold || summaryView.typeface.style == Typeface.BOLD)
        assertEquals(Typeface.MONOSPACE, traceView.typeface)
        assertEquals(40, traceView.text.toString().lines().size)

        assertEquals("Copy report", copyBtn.text.toString())
        assertEquals("Share report", shareBtn.text.toString())
        assertEquals("Restart app", restartBtn.text.toString())

        copyBtn.performClick()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val copied = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        assertTrue(copied.contains("Startup.kt:55"))

        shareBtn.performClick()
        val startedShare = shadowOf(activity).nextStartedActivity
        assertNotNull(startedShare)
        assertEquals(Intent.ACTION_CHOOSER, startedShare.action)
    }

    @Test
    fun settingsScreen_hidesCrashReportsWhenEmpty_andShowsThemUnderHelpWhenPresent() {
        composeRule.setContent {
            DelivioPartnerTheme {
                SettingsScreen(
                    settings = MerchantSettings(storeName = "Test Bakery"),
                    crashReports = emptyList(),
                    onBack = {}
                )
            }
        }

        assertEquals(0, composeRule.onAllNodesWithTag("settings_crash_reports_row").fetchSemanticsNodes().size)
    }

    @Test
    fun settingsScreen_showsHelpCrashReportsRowAndOpensDialogWithDocPath() {
        val reports = listOf(
            CrashReportEntry(
                id = "nf_1",
                timestampMs = 1700000000000L,
                appVersion = "1.0",
                threadName = "main",
                exceptionType = "RuntimeException",
                exceptionMessage = "Corrupt field",
                stackTrace = "at com.example.Parser.parse(Parser.kt:12)",
                isFatal = false,
                documentPath = "orders/bad_order_42"
            )
        )

        composeRule.setContent {
            DelivioPartnerTheme {
                SettingsScreen(
                    settings = MerchantSettings(storeName = "Test Bakery"),
                    crashReports = reports,
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("settings_crash_reports_row").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("crash_reports_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Document: orders/bad_order_42").assertIsDisplayed()
        composeRule.onNodeWithText("RuntimeException: Corrupt field").assertIsDisplayed()
    }
}
