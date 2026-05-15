package com.vwap.strictly

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pins the public surface of the no-op artifact. Every test here asserts a
 * call returns without throwing and the StateFlows emit benign empty values.
 *
 * This is the safety net that catches drift between the live module and the
 * no-op module. If a new method ships on the live Strictly object and isn't
 * mirrored here, callers' release builds break at compile time. This test
 * file makes that contract explicit: every method on the public surface is
 * exercised here.
 *
 * Run via `:strictly-noop:test`. CI runs both modules' tests on every PR.
 */
class NoopParityTest {

    @Test
    fun `every public no-op call returns without throwing`() {
        // Direct property reads.
        assertThat(Strictly.currentSessionId).isEqualTo("")
        assertThat(Strictly.isLiveListeningSupported).isFalse()

        // Mutators are no-ops, just confirm they don't throw.
        Strictly.clearCurrent()
        Strictly.deleteSession("any")
        Strictly.wipeAllSessions()
        Strictly.loadSession("any")

        // DebugHttp surface.
        assertThat(Strictly.DebugHttp.running.value).isFalse()
        assertThat(Strictly.DebugHttp.desired.value).isFalse()
        assertThat(Strictly.DebugHttp.lastError.value).isNull()
        assertThat(Strictly.DebugHttp.port).isEqualTo(0)
        assertThat(Strictly.DebugHttp.hasSecret).isFalse()
        Strictly.DebugHttp.setEnabled(true)
        Strictly.DebugHttp.setEnabled(false)
        Strictly.DebugHttp.retry()
    }

    @Test
    fun `sessions flow emits empty list`() {
        assertThat(Strictly.sessions.value).isEmpty()
    }

    @Test
    fun `violations flow emits empty map`() {
        assertThat(Strictly.violations.value).isEmpty()
    }
}
