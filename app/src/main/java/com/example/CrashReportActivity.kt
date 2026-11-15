package com.example

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.util.CrashReportStore

/**
 * Isolated crash report screen running in android:process=":crash".
 * Uses plain Android Views with an explicit light theme and zero dependency
 * on Application state, Compose, Firebase, or Theme classes so it still works
 * if the main process crashes during startup.
 */
class CrashReportActivity : Activity() {

    companion object {
        const val EXTRA_EXCEPTION_SUMMARY = "extra_exception_summary"
        const val EXTRA_STACK_TRACE = "extra_stack_trace"
        const val EXTRA_FULL_REPORT = "extra_full_report"
        const val EXTRA_METADATA = "extra_metadata"

        const val TAG_TITLE = "crash_report_title"
        const val TAG_EXCEPTION_SUMMARY = "crash_report_exception_summary"
        const val TAG_STACK_TRACE_BOX = "crash_report_stack_trace"
        const val TAG_COPY_BTN = "crash_report_copy_btn"
        const val TAG_SHARE_BTN = "crash_report_share_btn"
        const val TAG_RESTART_BTN = "crash_report_restart_btn"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val latestSaved = CrashReportStore.loadReports(this).firstOrNull()
        val exceptionSummary = intent?.getStringExtra(EXTRA_EXCEPTION_SUMMARY)
            ?.takeIf { it.isNotBlank() }
            ?: latestSaved?.summaryTitle
            ?: "Unknown error"
        val fullTrace = intent?.getStringExtra(EXTRA_STACK_TRACE)
            ?.takeIf { it.isNotBlank() }
            ?: latestSaved?.stackTrace
            ?: "No stack trace captured."
        val previewTrace = CrashReportStore.firstLines(fullTrace, CrashReportStore.MAX_PREVIEW_LINES)
        val fullReport = intent?.getStringExtra(EXTRA_FULL_REPORT)
            ?.takeIf { it.isNotBlank() }
            ?: latestSaved?.let { CrashReportStore.formatFullReport(it) }
            ?: "$exceptionSummary\n\n$fullTrace"
        val metadataText = intent?.getStringExtra(EXTRA_METADATA)
            ?.takeIf { it.isNotBlank() }
            ?: latestSaved?.let {
                "Time: ${CrashReportStore.formatTimestamp(it.timestampMs)} • Version: ${it.appVersion} • Thread: ${it.threadName}"
            }
            ?: ""

        fun dp(value: Int): Int = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(20), dp(36), dp(20), dp(24))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val titleView = TextView(this).apply {
            tag = TAG_TITLE
            text = "The app closed unexpectedly"
            setTextColor(Color.parseColor("#111827"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
        }
        root.addView(titleView)

        if (metadataText.isNotBlank()) {
            val metaView = TextView(this).apply {
                text = metadataText
                setTextColor(Color.parseColor("#4B5563"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(6), 0, 0)
            }
            root.addView(metaView)
        }

        val summaryBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FEF2F2"))
                setStroke(dp(1), Color.parseColor("#FECACA"))
                cornerRadius = dp(10).toFloat()
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(14)
                bottomMargin = dp(12)
            }
        }

        val exceptionView = TextView(this).apply {
            tag = TAG_EXCEPTION_SUMMARY
            text = exceptionSummary
            setTextColor(Color.parseColor("#B91C1C"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
        }
        summaryBox.addView(exceptionView)
        root.addView(summaryBox)

        val traceLabel = TextView(this).apply {
            text = "Stack trace (first 40 lines)"
            setTextColor(Color.parseColor("#374151"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
            setPadding(0, 0, 0, dp(6))
        }
        root.addView(traceLabel)

        val traceContainer = ScrollView(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F9FAFB"))
                setStroke(dp(1), Color.parseColor("#D1D5DB"))
                cornerRadius = dp(10).toFloat()
            }
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val hScroll = HorizontalScrollView(this).apply {
            isFillViewport = true
        }
        val traceView = TextView(this).apply {
            tag = TAG_STACK_TRACE_BOX
            text = previewTrace
            setTextColor(Color.parseColor("#1F2937"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        hScroll.addView(traceView)
        traceContainer.addView(hScroll)
        root.addView(traceContainer)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(14)
            }
        }

        fun createActionButton(
            buttonTag: String,
            label: String,
            bgHex: String,
            fgHex: String,
            onClick: () -> Unit
        ): Button {
            return Button(this).apply {
                tag = buttonTag
                text = label
                isAllCaps = false
                setTextColor(Color.parseColor(fgHex))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(bgHex))
                    cornerRadius = dp(10).toFloat()
                }
                minimumHeight = dp(48)
                setPadding(dp(10), dp(8), dp(10), dp(8))
                setOnClickListener { onClick() }
            }
        }

        val copyBtn = createActionButton(
            buttonTag = TAG_COPY_BTN,
            label = "Copy report",
            bgHex = "#E5E7EB",
            fgHex = "#111827"
        ) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("Crash Report", fullReport))
            Toast.makeText(this, "Full crash report copied", Toast.LENGTH_SHORT).show()
        }

        val shareBtn = createActionButton(
            buttonTag = TAG_SHARE_BTN,
            label = "Share report",
            bgHex = "#E5E7EB",
            fgHex = "#111827"
        ) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Delivio Partner Crash Report")
                putExtra(Intent.EXTRA_TEXT, fullReport)
            }
            startActivity(Intent.createChooser(shareIntent, "Share crash report"))
        }

        val restartBtn = createActionButton(
            buttonTag = TAG_RESTART_BTN,
            label = "Restart app",
            bgHex = "#FFC107",
            fgHex = "#1A1A1A"
        ) {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            if (launchIntent != null) {
                startActivity(launchIntent)
            }
            finish()
        }

        val btnParams1 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(6)
        }
        val btnParams2 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(3)
            marginEnd = dp(3)
        }
        val btnParams3 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(6)
        }

        buttonRow.addView(copyBtn, btnParams1)
        buttonRow.addView(shareBtn, btnParams2)
        buttonRow.addView(restartBtn, btnParams3)
        root.addView(buttonRow)

        setContentView(root)
    }
}
