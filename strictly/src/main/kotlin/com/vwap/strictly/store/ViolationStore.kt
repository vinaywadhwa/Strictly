package com.vwap.strictly.store

import com.vwap.strictly.core.Violation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory, process-local store of unique violations.
 *
 * Design notes:
 * - Keyed by fingerprint, not by event. The 200th identical disk-read just
 *   bumps `occurrenceCount` and updates `lastOccurrenceAtMillis` on the
 *   existing record.
 * - Bounded by [maxSize] (LRU eviction by `lastOccurrenceAtMillis`) so a
 *   long-running session doesn't OOM the device.
 * - Exposes a [StateFlow] so the UI and notification can both subscribe and
 *   recompose without polling.
 * - Threading: writes happen on Strictly's single-thread executor; reads happen
 *   on the main thread (via collectAsState). [StateFlow] handles the boundary.
 *   The map mutation we do is single-writer, so a [LinkedHashMap] is fine.
 */
internal class ViolationStore(private val maxSize: Int) {

    private val _violations = MutableStateFlow<Map<String, Violation>>(emptyMap())
    val violations: StateFlow<Map<String, Violation>> = _violations.asStateFlow()

    private val totalSeen = AtomicLong(0)

    /**
     * The session-start mark used by baseline mode. When the user marks a
     * baseline, this is set to "now"; violations with `firstOccurrenceAtMillis`
     * before the mark are considered "accepted."
     */
    private val baselineMarkMillis = AtomicLong(0)

    fun record(incoming: Violation) {
        totalSeen.incrementAndGet()
        _violations.update { current ->
            val existing = current[incoming.fingerprint]
            val merged = if (existing != null) {
                existing.copy(
                    occurrenceCount = existing.occurrenceCount + 1,
                    lastOccurrenceAtMillis = incoming.lastOccurrenceAtMillis,
                )
            } else {
                incoming
            }

            val updated = current.toMutableMap().apply { put(incoming.fingerprint, merged) }

            // Evict oldest if we're over budget. LinkedHashMap-style behaviour
            // via a separate sort instead of insertion order, since we update
            // existing entries (which doesn't move them in insertion order).
            if (updated.size > maxSize) {
                val toEvict = updated.values
                    .sortedBy { it.lastOccurrenceAtMillis }
                    .take(updated.size - maxSize)
                toEvict.forEach { updated.remove(it.fingerprint) }
            }

            updated
        }
    }

    fun clear() {
        _violations.value = emptyMap()
        totalSeen.set(0)
    }

    /** Returns the total number of detection events ever seen (not unique). */
    fun totalEventCount(): Long = totalSeen.get()

    /** Returns the count of unique violations currently held. */
    fun uniqueCount(): Int = _violations.value.size

    fun markBaselineNow() {
        baselineMarkMillis.set(System.currentTimeMillis())
    }

    fun clearBaseline() {
        baselineMarkMillis.set(0)
    }

    fun isNewSinceBaseline(violation: Violation): Boolean {
        val mark = baselineMarkMillis.get()
        return mark > 0 && violation.firstOccurrenceAtMillis > mark
    }

    fun hasBaseline(): Boolean = baselineMarkMillis.get() > 0
}
