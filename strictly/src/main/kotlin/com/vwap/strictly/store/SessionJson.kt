package com.vwap.strictly.store

import com.vwap.strictly.core.Session
import com.vwap.strictly.core.SessionSummary
import com.vwap.strictly.core.StackFrame
import com.vwap.strictly.core.Violation
import com.vwap.strictly.core.ViolationType
import org.json.JSONArray
import org.json.JSONObject

/**
 * Hand-rolled JSON serialization for [Session] and [SessionSummary].
 *
 * Why not kotlinx.serialization: keeps the .aar tiny and the build simpler
 * for a fixed schema we own end-to-end. Uses [org.json] which ships with
 * Android since API 1.
 *
 * Schema is versioned via the top-level `schema` field so consumers (the
 * HTTP server, the MCP, external diff tools) can detect mismatches.
 */
internal object SessionJson {

    const val SESSION_SCHEMA = "strictly/session.v1"
    const val INDEX_SCHEMA = "strictly/sessions-index.v1"

    fun encodeSession(session: Session): String = JSONObject().apply {
        put("schema", SESSION_SCHEMA)
        put(
            "session",
            JSONObject().apply {
                put("id", session.id)
                put("startedAtMillis", session.startedAtMillis)
                put("lastEventAtMillis", session.lastEventAtMillis)
                put("appVersionName", session.appVersionName)
                put("appVersionCode", session.appVersionCode)
                put("deviceModel", session.deviceModel)
                put("osLevel", session.osLevel)
            },
        )
        put("summary", summaryBlock(session))
        put("violations", violationsArray(session.violations.values))
    }.toString(2)

    fun encodeIndex(summaries: List<SessionSummary>): String = JSONObject().apply {
        put("schema", INDEX_SCHEMA)
        put(
            "sessions",
            JSONArray().apply {
                summaries.forEach { put(encodeSummary(it)) }
            },
        )
    }.toString(2)

    fun decodeSession(text: String): Session {
        val root = JSONObject(text)
        val sessionObj = root.getJSONObject("session")
        val violationsArr = root.getJSONArray("violations")
        val violations = LinkedHashMap<String, Violation>()
        for (i in 0 until violationsArr.length()) {
            val v = decodeViolation(violationsArr.getJSONObject(i))
            violations[v.fingerprint] = v
        }
        return Session(
            id = sessionObj.getString("id"),
            startedAtMillis = sessionObj.getLong("startedAtMillis"),
            lastEventAtMillis = sessionObj.getLong("lastEventAtMillis"),
            appVersionName = sessionObj.optString("appVersionName", ""),
            appVersionCode = sessionObj.optInt("appVersionCode", 0),
            deviceModel = sessionObj.optString("deviceModel", ""),
            osLevel = sessionObj.optInt("osLevel", 0),
            violations = violations,
        )
    }

    fun decodeIndex(text: String): List<SessionSummary> {
        val root = JSONObject(text)
        val arr = root.optJSONArray("sessions") ?: return emptyList()
        val out = ArrayList<SessionSummary>(arr.length())
        for (i in 0 until arr.length()) {
            out.add(decodeSummary(arr.getJSONObject(i)))
        }
        return out
    }

    private fun encodeSummary(s: SessionSummary): JSONObject = JSONObject().apply {
        put("id", s.id)
        put("startedAtMillis", s.startedAtMillis)
        put("lastEventAtMillis", s.lastEventAtMillis)
        put("uniqueCount", s.uniqueCount)
        put("totalEvents", s.totalEvents)
        put("topType", s.topType?.name)
        put("appVersionName", s.appVersionName)
    }

    private fun decodeSummary(o: JSONObject): SessionSummary = SessionSummary(
        id = o.getString("id"),
        startedAtMillis = o.getLong("startedAtMillis"),
        lastEventAtMillis = o.getLong("lastEventAtMillis"),
        uniqueCount = o.optInt("uniqueCount"),
        totalEvents = o.optInt("totalEvents"),
        topType = o.optString("topType", "").takeIf { it.isNotEmpty() }
            ?.let { runCatching { ViolationType.valueOf(it) }.getOrNull() },
        appVersionName = o.optString("appVersionName", ""),
    )

    private fun summaryBlock(session: Session): JSONObject = JSONObject().apply {
        put("uniqueCount", session.uniqueCount)
        put("totalEvents", session.totalEvents)
        put(
            "byType",
            JSONObject().apply {
                session.violations.values
                    .groupBy { it.type }
                    .mapValues { (_, vs) -> vs.sumOf { it.occurrenceCount } }
                    .forEach { (type, count) -> put(type.name, count) }
            },
        )
        put(
            "topAppFrameAttributions",
            JSONArray().apply {
                session.violations.values
                    .filter { it.firstAppFrame != null }
                    .groupBy { it.firstAppFrame!!.formatShort() }
                    .mapValues { (_, vs) -> vs.sumOf { it.occurrenceCount } }
                    .entries
                    .sortedByDescending { it.value }
                    .take(3)
                    .forEach { (frame, count) ->
                        put(
                            JSONObject().apply {
                                put("frame", frame)
                                put("count", count)
                            },
                        )
                    }
            },
        )
    }

    private fun violationsArray(violations: Collection<Violation>): JSONArray =
        JSONArray().apply {
            violations
                .sortedByDescending { it.occurrenceCount }
                .forEach { put(encodeViolation(it)) }
        }

    private fun encodeViolation(v: Violation): JSONObject = JSONObject().apply {
        put("fingerprint", v.fingerprint)
        put("type", v.type.name)
        put("typeDisplay", v.type.displayName)
        put("occurrenceCount", v.occurrenceCount)
        put("severity", severityOf(v.occurrenceCount))
        put("firstSeenMillis", v.firstOccurrenceAtMillis)
        put("lastSeenMillis", v.lastOccurrenceAtMillis)
        put("threadName", v.threadName)
        put("message", v.message)
        put("firstAppFrame", v.firstAppFrame?.formatShort())
        put("firstActionableFrame", v.firstActionableFrame?.formatShort())
        put("isThirdPartyOrigin", v.isThirdPartyOrigin)
        put(
            "stack",
            JSONArray().apply { v.stackTrace.forEach { put(it.formatFull()) } },
        )
        put(
            "stackFrames",
            JSONArray().apply { v.stackTrace.forEach { put(encodeFrame(it)) } },
        )
    }

    private fun decodeViolation(o: JSONObject): Violation {
        val stackFrames = o.optJSONArray("stackFrames")
        val frames: List<StackFrame> = if (stackFrames != null) {
            buildList {
                for (i in 0 until stackFrames.length()) {
                    add(decodeFrame(stackFrames.getJSONObject(i)))
                }
            }
        } else {
            emptyList()
        }
        val firstAppFrameLabel = o.optString("firstAppFrame", "").takeIf { it.isNotEmpty() }
        val firstActionableLabel = o.optString("firstActionableFrame", "").takeIf { it.isNotEmpty() }
        return Violation(
            fingerprint = o.getString("fingerprint"),
            type = runCatching { ViolationType.valueOf(o.getString("type")) }
                .getOrDefault(ViolationType.Unknown),
            message = o.optString("message", ""),
            stackTrace = frames,
            firstAppFrame = frames.firstOrNull { it.formatShort() == firstAppFrameLabel },
            firstActionableFrame = frames.firstOrNull { it.formatShort() == firstActionableLabel },
            firstOccurrenceAtMillis = o.getLong("firstSeenMillis"),
            lastOccurrenceAtMillis = o.getLong("lastSeenMillis"),
            occurrenceCount = o.getInt("occurrenceCount"),
            threadName = o.optString("threadName", "main"),
            processName = o.optString("processName", "main"),
        )
    }

    private fun encodeFrame(f: StackFrame): JSONObject = JSONObject().apply {
        put("className", f.className)
        put("methodName", f.methodName)
        put("fileName", f.fileName)
        put("lineNumber", f.lineNumber)
    }

    private fun decodeFrame(o: JSONObject): StackFrame = StackFrame(
        className = o.optString("className", "<unknown>"),
        methodName = o.optString("methodName", "<unknown>"),
        fileName = o.optString("fileName").takeIf { it.isNotEmpty() && it != "null" },
        lineNumber = o.optInt("lineNumber", 0),
    )

    private fun severityOf(count: Int): String = when {
        count >= 50 -> "high"
        count >= 5 -> "medium"
        else -> "low"
    }
}
