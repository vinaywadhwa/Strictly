package com.vwap.strictly.export

import com.vwap.strictly.core.Session
import com.vwap.strictly.core.Violation
import com.vwap.strictly.store.SessionJson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Single source of truth for "turn a [Session] into a string you can paste
 * somewhere." The shape of the output is what the user paste's into an AI
 * agent (Claude, Cursor, etc) and what the HTTP server returns over the wire.
 *
 * JSON is the machine-readable canonical form (schema-versioned). Markdown is
 * the human-friendly form, written so a reviewer can scan the worst offenders
 * in a chat or PR comment without parsing JSON.
 */
internal object SessionExporter {

    fun toJson(session: Session): String = SessionJson.encodeSession(session)

    fun toMarkdown(session: Session): String = buildString {
        appendLine("# Strictly session")
        appendLine()
        appendLine(metaTable(session))
        appendLine()
        appendLine(summaryParagraph(session))
        appendLine()

        val sorted = session.violations.values.sortedByDescending { it.occurrenceCount }
        if (sorted.isEmpty()) {
            appendLine("_No violations were recorded in this session._")
            return@buildString
        }

        appendLine("## Top offenders")
        appendLine()
        appendLine("| Count | Severity | Type | Origin |")
        appendLine("| ---:| --- | --- | --- |")
        sorted.take(10).forEach { v ->
            val sev = severityOf(v.occurrenceCount)
            val origin = v.firstActionableFrame?.formatShort() ?: "unknown"
            val originSuffix = if (v.isThirdPartyOrigin) " (3p)" else ""
            appendLine("| ${v.occurrenceCount} | $sev | ${v.type.displayName} | `$origin$originSuffix` |")
        }
        appendLine()

        appendLine("## Detail")
        appendLine()
        sorted.forEachIndexed { idx, v ->
            appendViolationBlock(idx + 1, v)
        }
    }

    private fun StringBuilder.appendViolationBlock(idx: Int, v: Violation) {
        appendLine("### $idx. ${v.type.displayName} (×${v.occurrenceCount})")
        appendLine()
        val frame = v.firstActionableFrame
        if (frame != null) {
            val attr = if (v.isThirdPartyOrigin) "third-party" else "app code"
            appendLine("- Origin (${attr}): `${frame.formatShort()}`")
        }
        if (v.firstAppFrame != null && v.firstAppFrame != frame) {
            appendLine("- First app frame: `${v.firstAppFrame!!.formatShort()}`")
        }
        appendLine("- Thread: `${v.threadName}`")
        appendLine("- First seen: ${formatTime(v.firstOccurrenceAtMillis)}")
        appendLine("- Last seen: ${formatTime(v.lastOccurrenceAtMillis)}")
        appendLine("- Fingerprint: `${v.fingerprint}`")
        if (v.message.isNotEmpty()) {
            appendLine()
            appendLine("```")
            appendLine(v.message.trim())
            appendLine("```")
        }
        if (v.stackTrace.isNotEmpty()) {
            appendLine()
            appendLine("<details><summary>Stack trace (${v.stackTrace.size} frames)</summary>")
            appendLine()
            appendLine("```")
            v.stackTrace.forEach { appendLine("  at ${it.formatFull()}") }
            appendLine("```")
            appendLine()
            appendLine("</details>")
        }
        appendLine()
    }

    private fun metaTable(s: Session): String {
        val started = formatTime(s.startedAtMillis)
        val last = formatTime(s.lastEventAtMillis)
        val version = if (s.appVersionName.isNotEmpty()) "${s.appVersionName} (${s.appVersionCode})" else "n/a"
        val device = if (s.deviceModel.isNotEmpty()) "${s.deviceModel} · API ${s.osLevel}" else "n/a"
        return """
            | Field | Value |
            | --- | --- |
            | Session id | `${s.id}` |
            | Started | $started |
            | Last event | $last |
            | App version | $version |
            | Device | $device |
        """.trimIndent()
    }

    private fun summaryParagraph(s: Session): String {
        val unique = s.uniqueCount
        val events = s.totalEvents
        val topType = s.topType?.displayName ?: "none"
        return "**$unique unique violations** spanning **$events total events**. Most frequent type: **$topType**."
    }

    private val TIME_FORMAT by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
    }

    private fun formatTime(millis: Long): String = TIME_FORMAT.format(Date(millis))

    private fun severityOf(count: Int): String = when {
        count >= 50 -> "high"
        count >= 5 -> "medium"
        else -> "low"
    }
}
