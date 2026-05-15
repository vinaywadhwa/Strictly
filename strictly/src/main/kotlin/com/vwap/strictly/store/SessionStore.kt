package com.vwap.strictly.store

import android.app.Application
import android.os.Build
import com.vwap.strictly.core.Session
import com.vwap.strictly.core.SessionSummary
import com.vwap.strictly.core.Violation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * Disk-backed store of Strictly sessions.
 *
 * Owns:
 * - The live session's violations (as a [StateFlow] for the UI / notification)
 * - The session index ([StateFlow] of [SessionSummary]) for the session list screen
 * - Read-through access to archived sessions on demand
 * - Retention (LRU eviction by [SessionSummary.lastEventAtMillis])
 *
 * Threading: writes happen on Strictly's single-thread executor. Reads happen
 * on the main thread (via `collectAsState`). Both [StateFlow]s handle the
 * boundary. The disk write-through is delegated to [SessionPersister] which
 * debounces.
 */
internal class SessionStore(
    application: Application,
    private val maxStoredSessions: Int,
) {
    private val persister = SessionPersister(application.applicationContext)

    private val sessionId: String = java.util.UUID.randomUUID().toString()
    private val startedAtMillis: Long = System.currentTimeMillis()
    private val deviceMeta = DeviceMeta.from(application)

    private val _currentViolations = MutableStateFlow<Map<String, Violation>>(emptyMap())
    val currentViolations: StateFlow<Map<String, Violation>> = _currentViolations.asStateFlow()

    private val _sessions = MutableStateFlow<List<SessionSummary>>(emptyList())
    val sessions: StateFlow<List<SessionSummary>> = _sessions.asStateFlow()

    private val totalSeen = AtomicLong(0)
    private val _currentLastEvent = MutableStateFlow(startedAtMillis)
    val currentLastEvent: StateFlow<Long> = _currentLastEvent.asStateFlow()

    private var currentPersistedYet = false

    init {
        // Load the index off-disk eagerly. List is small (max ~20 summaries).
        val loaded = persister.readIndex()
        _sessions.value = loaded
    }

    val currentSessionId: String get() = sessionId
    val currentStartedAt: Long get() = startedAtMillis

    fun record(incoming: Violation) {
        totalSeen.incrementAndGet()
        _currentLastEvent.value = incoming.lastOccurrenceAtMillis
        val merged = _currentViolations.updateAndReturn { current ->
            val existing = current[incoming.fingerprint]
            val newOrMerged = if (existing != null) {
                existing.copy(
                    occurrenceCount = existing.occurrenceCount + 1,
                    lastOccurrenceAtMillis = incoming.lastOccurrenceAtMillis,
                )
            } else {
                incoming
            }
            current + (incoming.fingerprint to newOrMerged)
        }
        scheduleWriteThrough(merged)
    }

    fun clearCurrent() {
        _currentViolations.value = emptyMap()
        totalSeen.set(0)
        _currentLastEvent.value = System.currentTimeMillis()
        // Wipe the persisted current-session file too: clearing the live store
        // should be reflected on disk so reopening the app doesn't resurrect
        // the violations.
        if (currentPersistedYet) {
            persister.deleteSessionFile(sessionId)
            refreshIndexAfterMutation(removeIds = listOf(sessionId))
        }
        currentPersistedYet = false
    }

    fun deleteSession(id: String) {
        if (id == sessionId) {
            clearCurrent()
            return
        }
        persister.deleteSessionFile(id)
        refreshIndexAfterMutation(removeIds = listOf(id))
    }

    fun wipeAllSessions() {
        persister.wipeAll()
        _sessions.value = emptyList()
        clearCurrent()
    }

    fun loadSession(id: String): Session? {
        if (id == sessionId) return snapshotCurrent()
        return persister.readSession(id)
    }

    fun snapshotCurrent(): Session = Session(
        id = sessionId,
        startedAtMillis = startedAtMillis,
        lastEventAtMillis = _currentLastEvent.value,
        appVersionName = deviceMeta.appVersionName,
        appVersionCode = deviceMeta.appVersionCode,
        deviceModel = deviceMeta.deviceModel,
        osLevel = deviceMeta.osLevel,
        violations = _currentViolations.value,
    )

    /** Returns the current session's summary plus the persisted archived ones. */
    fun visibleSessions(): List<SessionSummary> {
        val current = snapshotCurrent()
        val currentSummary = SessionSummary(
            id = current.id,
            startedAtMillis = current.startedAtMillis,
            lastEventAtMillis = current.lastEventAtMillis,
            uniqueCount = current.uniqueCount,
            totalEvents = current.totalEvents,
            topType = current.topType,
            appVersionName = current.appVersionName,
        )
        val archived = _sessions.value.filter { it.id != sessionId }
        return if (current.uniqueCount > 0) {
            (listOf(currentSummary) + archived).sortedByDescending { it.lastEventAtMillis }
        } else {
            archived.sortedByDescending { it.lastEventAtMillis }
        }
    }

    fun totalEventCount(): Long = totalSeen.get()
    fun uniqueCount(): Int = _currentViolations.value.size

    private fun scheduleWriteThrough(snapshot: Map<String, Violation>) {
        val session = Session(
            id = sessionId,
            startedAtMillis = startedAtMillis,
            lastEventAtMillis = _currentLastEvent.value,
            appVersionName = deviceMeta.appVersionName,
            appVersionCode = deviceMeta.appVersionCode,
            deviceModel = deviceMeta.deviceModel,
            osLevel = deviceMeta.osLevel,
            violations = snapshot,
        )
        persister.scheduleWrite(session) {
            currentPersistedYet = true
            refreshIndexAfterWrite(session)
            evictIfOverBudget()
        }
    }

    private fun refreshIndexAfterWrite(session: Session) {
        _sessions.update { existing ->
            val withoutSelf = existing.filter { it.id != session.id }
            val mine = SessionSummary(
                id = session.id,
                startedAtMillis = session.startedAtMillis,
                lastEventAtMillis = session.lastEventAtMillis,
                uniqueCount = session.uniqueCount,
                totalEvents = session.totalEvents,
                topType = session.topType,
                appVersionName = session.appVersionName,
            )
            (listOf(mine) + withoutSelf).sortedByDescending { it.lastEventAtMillis }
        }
        persister.writeIndex(_sessions.value)
    }

    private fun refreshIndexAfterMutation(removeIds: List<String>) {
        _sessions.update { it.filter { s -> s.id !in removeIds } }
        persister.writeIndex(_sessions.value)
    }

    private fun evictIfOverBudget() {
        val current = _sessions.value
        if (current.size <= maxStoredSessions) return
        // Never evict the live session, even if it's the oldest.
        val candidates = current
            .filter { it.id != sessionId }
            .sortedBy { it.lastEventAtMillis }
        val toEvict = candidates.take(current.size - maxStoredSessions)
        toEvict.forEach { persister.deleteSessionFile(it.id) }
        refreshIndexAfterMutation(removeIds = toEvict.map { it.id })
    }

    private inline fun <T> MutableStateFlow<T>.updateAndReturn(transform: (T) -> T): T {
        var captured: T? = null
        update { current ->
            val next = transform(current)
            captured = next
            next
        }
        @Suppress("UNCHECKED_CAST")
        return captured as T
    }

    private data class DeviceMeta(
        val appVersionName: String,
        val appVersionCode: Int,
        val deviceModel: String,
        val osLevel: Int,
    ) {
        companion object {
            fun from(application: Application): DeviceMeta {
                val pkg = runCatching {
                    @Suppress("DEPRECATION")
                    application.packageManager.getPackageInfo(application.packageName, 0)
                }.getOrNull()
                val versionName = pkg?.versionName ?: ""
                val versionCode = pkg?.let {
                    @Suppress("DEPRECATION")
                    it.versionCode
                } ?: 0
                return DeviceMeta(
                    appVersionName = versionName,
                    appVersionCode = versionCode,
                    deviceModel = Build.MODEL ?: "",
                    osLevel = Build.VERSION.SDK_INT,
                )
            }
        }
    }
}
