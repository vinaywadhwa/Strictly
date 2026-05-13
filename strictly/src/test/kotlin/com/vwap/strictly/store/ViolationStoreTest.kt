package com.vwap.strictly.store

import com.google.common.truth.Truth.assertThat
import com.vwap.strictly.core.StackFrame
import com.vwap.strictly.core.Violation
import com.vwap.strictly.core.ViolationType
import org.junit.Test

class ViolationStoreTest {

    @Test
    fun `recording a new fingerprint adds a row`() {
        val store = ViolationStore(maxSize = 10)
        store.record(violation("fp1"))
        assertThat(store.uniqueCount()).isEqualTo(1)
    }

    @Test
    fun `recording the same fingerprint increments occurrence count`() {
        val store = ViolationStore(maxSize = 10)
        store.record(violation("fp1"))
        store.record(violation("fp1"))
        store.record(violation("fp1"))

        assertThat(store.uniqueCount()).isEqualTo(1)
        assertThat(store.violations.value["fp1"]?.occurrenceCount).isEqualTo(3)
    }

    @Test
    fun `LRU evicts the oldest entry when capacity is exceeded`() {
        val store = ViolationStore(maxSize = 2)
        store.record(violation("fp1", lastAt = 100))
        store.record(violation("fp2", lastAt = 200))
        store.record(violation("fp3", lastAt = 300))

        val keys = store.violations.value.keys
        assertThat(keys).containsExactly("fp2", "fp3")
    }

    @Test
    fun `clear empties the store and resets event counter`() {
        val store = ViolationStore(maxSize = 10)
        store.record(violation("fp1"))
        store.record(violation("fp1"))
        assertThat(store.totalEventCount()).isEqualTo(2)

        store.clear()
        assertThat(store.uniqueCount()).isEqualTo(0)
        assertThat(store.totalEventCount()).isEqualTo(0)
    }

    @Test
    fun `baseline mode flags only post-mark violations as new`() {
        val store = ViolationStore(maxSize = 10)
        val old = violation("fp_old", firstAt = 100, lastAt = 100)
        store.record(old)

        store.markBaselineNow()
        val markTime = System.currentTimeMillis()

        // Old violation is not new.
        assertThat(store.isNewSinceBaseline(old)).isFalse()

        val recent = violation("fp_new", firstAt = markTime + 1000, lastAt = markTime + 1000)
        assertThat(store.isNewSinceBaseline(recent)).isTrue()
    }

    @Test
    fun `clearBaseline removes the mark`() {
        val store = ViolationStore(maxSize = 10)
        store.markBaselineNow()
        assertThat(store.hasBaseline()).isTrue()
        store.clearBaseline()
        assertThat(store.hasBaseline()).isFalse()
    }

    // ---- helpers ----

    private fun violation(
        fp: String,
        firstAt: Long = 0,
        lastAt: Long = 0,
    ) = Violation(
        fingerprint = fp,
        type = ViolationType.DiskRead,
        message = "",
        stackTrace = listOf(StackFrame("com.acme.X", "f", "X.kt", 1)),
        firstAppFrame = null,
        firstOccurrenceAtMillis = firstAt,
        lastOccurrenceAtMillis = lastAt,
        occurrenceCount = 1,
        threadName = "main",
        processName = "main",
    )
}
