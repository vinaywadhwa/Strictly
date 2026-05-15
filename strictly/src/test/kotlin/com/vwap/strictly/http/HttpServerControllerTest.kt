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
        val port = freePort()
        val blocker = blockLoopbackPort(port)
        try {
            val controller = makeController(port)
            controller.setEnabled(true)

            assertThat(controller.desired.value).isTrue()
            assertThat(controller.running.value).isFalse()
            assertThat(controller.lastError.value).contains("$port")
            assertThat(controller.lastError.value).contains("busy")
        } finally {
            blocker.close()
        }
    }

    @Test
    fun `retry clears lastError on successful rebind`() {
        val port = freePort()
        val blocker = blockLoopbackPort(port)
        val controller = makeController(port)
        controller.setEnabled(true)
        assertThat(controller.lastError.value).isNotNull()

        blocker.close()
        controller.retry()

        assertThat(controller.lastError.value).isNull()
        assertThat(controller.running.value).isTrue()
    }

    @Test
    fun `setEnabled false clears lastError`() {
        val port = freePort()
        val blocker = blockLoopbackPort(port)
        try {
            val controller = makeController(port)
            controller.setEnabled(true)
            assertThat(controller.lastError.value).isNotNull()

            controller.setEnabled(false)
            assertThat(controller.lastError.value).isNull()
            assertThat(controller.running.value).isFalse()
            assertThat(controller.desired.value).isFalse()
        } finally {
            blocker.close()
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
