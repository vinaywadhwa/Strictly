package com.vwap.strictly.core

import java.util.UUID

/**
 * One Strictly recording session. Created lazily on first violation after
 * `Strictly.install`. Persisted to disk so it survives process death, rebuilds,
 * and reboots.
 *
 * Sessions are the unit of navigation, export, and diff.
 */
data class Session(
    val id: String = UUID.randomUUID().toString(),
    val startedAtMillis: Long,
    val lastEventAtMillis: Long,
    val appVersionName: String,
    val appVersionCode: Int,
    val deviceModel: String,
    val osLevel: Int,
    val violations: Map<String, Violation>,
) {
    val uniqueCount: Int get() = violations.size
    val totalEvents: Int get() = violations.values.sumOf { it.occurrenceCount }
    val topType: ViolationType?
        get() = violations.values
            .groupBy { it.type }
            .maxByOrNull { it.value.sumOf { v -> v.occurrenceCount } }
            ?.key
}

/**
 * Lightweight summary of a session for the session list screen. Renders
 * without parsing the full session JSON.
 */
data class SessionSummary(
    val id: String,
    val startedAtMillis: Long,
    val lastEventAtMillis: Long,
    val uniqueCount: Int,
    val totalEvents: Int,
    val topType: ViolationType?,
    val appVersionName: String,
)
