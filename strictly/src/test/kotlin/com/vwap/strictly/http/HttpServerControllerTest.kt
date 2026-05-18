package com.vwap.strictly.http

import com.google.common.truth.Truth.assertThat
import com.vwap.strictly.core.StrictlyConfig
import com.vwap.strictly.prefs.StrictlyPrefs
import com.vwap.strictly.store.SessionStore
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.ServerSocket

/**
 * Mirrors [HttpServerController.FALLBACK_WINDOW]. Kept in sync by hand: the
 * production constant is private to its companion (correct for the runtime
 * type), so test infrastructure re-declares the value rather than relaxing
 * visibility for a single test consumer.
 */
private const val CONTROLLER_FALLBACK_WINDOW = 4

/**
 * Covers the four bugs that hid in this controller before the morning of
 * 2026-05-15:
 *
 * 1. Toggle bound to running, not desired (intent vs reality).
 * 2. _lastError persisted across off-then-on cycles.
 * 3. Successful retry left _lastError stale after a prior failure.
 * 4. Re-install orphaned the old controller's bound socket so the new
 *    controller's bind attempt spuriously reported "Port busy".
 *
 * Each test pins one of those state transitions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class HttpServerControllerTest {

    private lateinit var prefs: StrictlyPrefs
    private lateinit var store: SessionStore
    private val controllers = mutableListOf<HttpServerController>()

    @Before
    fun setUp() {
        val application: android.app.Application = RuntimeEnvironment.getApplication()
        prefs = StrictlyPrefs(application)
        store = SessionStore(application, maxStoredSessions = 5)
    }

    @After
    fun tearDown() {
        controllers.forEach { runCatching { it.shutdown() } }
        controllers.clear()
        prefs.setHttpEnabled(false)
    }

    private fun makeController(port: Int = freePort()): HttpServerController =
        HttpServerController(
            config = StrictlyConfig(httpDebugPort = port),
            store = store,
            prefs = prefs,
            packageName = "com.test.app",
            versionName = "test/0.0.0",
        ).also { controllers += it }

    private fun freePort(): Int =
        ServerSocket(0, 50, InetAddress.getLoopbackAddress()).use { it.localPort }

    /**
     * Hold a loopback-bound listener on [port] so NanoHTTPD's bind attempt
     * (which targets 127.0.0.1:port) actively conflicts on the same
     * interface, not just the wildcard. macOS will otherwise let the
     * SO_REUSEADDR socket bind 127.0.0.1 while a 0.0.0.0 blocker is up.
     */
    private fun blockLoopbackPort(port: Int): ServerSocket =
        ServerSocket(port, 50, InetAddress.getLoopbackAddress())

    /**
     * Block the controller's full fallback window so the bind-loop has no
     * slot to fall through to. Required because [HttpServerController] now
     * walks `anchor`, `anchor+1`, ..., `anchor+FALLBACK_WINDOW-1` before
     * surfacing an error: blocking only the anchor lets the controller
     * silently bind the next slot, which is the opposite of what these
     * bind-failure tests intend to assert.
     */
    private fun blockLoopbackWindow(anchor: Int): List<ServerSocket> =
        (0 until CONTROLLER_FALLBACK_WINDOW).map { offset ->
            ServerSocket(anchor + offset, 50, InetAddress.getLoopbackAddress())
        }

    /**
     * Find an anchor port where the entire fallback window is bindable, so
     * a subsequent [blockLoopbackWindow] call cannot race with an external
     * process that grabbed one of the adjacent slots between probe and
     * block. Retries because freePort() returns a single port and the next
     * three are not guaranteed to be free.
     */
    private fun freeWindowAnchor(): Int {
        repeat(50) {
            val candidate = freePort()
            val holders = mutableListOf<ServerSocket>()
            try {
                for (offset in 0 until CONTROLLER_FALLBACK_WINDOW) {
                    holders += ServerSocket(candidate + offset, 50, InetAddress.getLoopbackAddress())
                }
                return candidate
            } catch (_: Exception) {
                // Some offset was taken. Try a fresh anchor.
            } finally {
                holders.forEach { runCatching { it.close() } }
            }
        }
        error("Could not find $CONTROLLER_FALLBACK_WINDOW consecutive free ports after 50 attempts")
    }

    @Test
    fun `desired tracks prefs, not running`() {
        val controller = makeController()
        assertThat(controller.desired.value).isFalse()
        assertThat(controller.running.value).isFalse()

        controller.setEnabled(true)
        assertThat(controller.desired.value).isTrue()
        assertThat(controller.running.value).isTrue()

        controller.shutdown()
        assertThat(controller.running.value).isFalse()
        assertThat(controller.desired.value).isTrue()
    }

    @Test
    fun `failed bind leaves desired on while running is off and lastError is set`() {
        val anchor = freeWindowAnchor()
        val blockers = blockLoopbackWindow(anchor)
        try {
            val controller = makeController(anchor)
            controller.setEnabled(true)

            assertThat(controller.desired.value).isTrue()
            assertThat(controller.running.value).isFalse()
            assertThat(controller.lastError.value).contains("$anchor")
            assertThat(controller.lastError.value).contains("busy")
        } finally {
            blockers.forEach { runCatching { it.close() } }
        }
    }

    @Test
    fun `retry clears lastError on successful rebind`() {
        val anchor = freeWindowAnchor()
        val blockers = blockLoopbackWindow(anchor)
        val controller = makeController(anchor)
        controller.setEnabled(true)
        assertThat(controller.lastError.value).isNotNull()

        blockers.forEach { runCatching { it.close() } }
        controller.retry()

        assertThat(controller.lastError.value).isNull()
        assertThat(controller.running.value).isTrue()
    }

    @Test
    fun `setEnabled false clears lastError`() {
        val anchor = freeWindowAnchor()
        val blockers = blockLoopbackWindow(anchor)
        try {
            val controller = makeController(anchor)
            controller.setEnabled(true)
            assertThat(controller.lastError.value).isNotNull()

            controller.setEnabled(false)
            assertThat(controller.lastError.value).isNull()
            assertThat(controller.running.value).isFalse()
            assertThat(controller.desired.value).isFalse()
        } finally {
            blockers.forEach { runCatching { it.close() } }
        }
    }

    @Test
    fun `shutdown releases the bound socket so a fresh controller can rebind`() {
        val port = freePort()
        val first = makeController(port)
        first.setEnabled(true)
        assertThat(first.running.value).isTrue()

        first.shutdown()

        val second = makeController(port)
        second.setEnabled(true)
        assertThat(second.running.value).isTrue()
        assertThat(second.lastError.value).isNull()
    }

    @Test
    fun `setEnabled is idempotent and does not re-trigger bind when already running`() {
        val controller = makeController()
        controller.setEnabled(true)
        assertThat(controller.running.value).isTrue()

        // Calling setEnabled(true) again should be a no-op state-wise: still
        // running, no error, no surprise re-bind that could fail.
        controller.setEnabled(true)
        assertThat(controller.running.value).isTrue()
        assertThat(controller.lastError.value).isNull()
    }
}
