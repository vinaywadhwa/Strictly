package com.vwap.strictly.http

import com.google.common.truth.Truth.assertThat
import com.vwap.strictly.prefs.StrictlyPrefs
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins the contract on [deriveAnchorPort] and the persisted-port round-trip
 * through [StrictlyPrefs]. These existed implicitly inside the controller's
 * bind machinery before, where a missing constructor arg silently broke the
 * compile of the only test that touched the controller at all. Pure-function
 * coverage here means future signature drift gets caught on the next test
 * run, not at release publish time.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class PortDerivationTest {

    @Test
    fun `derived port is always in the 8700 to 8799 band`() {
        // Cover a deliberate spread: real-looking package names, the empty
        // string, ASCII edge cases, and a name long enough to push the hash
        // through several rounds. The band is a hard contract: adb forward
        // recipes in docs assume callers land here.
        val inputs = listOf(
            "com.acme.app",
            "io.github.vinaywadhwa.sample",
            "a",
            "",
            "x".repeat(256),
            "com.branch_international.branch.branch_demo_android",
            "com.example.feedmaker",
        )
        for (pkg in inputs) {
            val port = deriveAnchorPort(pkg)
            assertThat(port).isAtLeast(8700)
            assertThat(port).isAtMost(8799)
        }
    }

    @Test
    fun `derived port is deterministic for the same input`() {
        // Two calls in the same process trivially agree. The real contract is
        // that JVM String.hashCode is spec-stable across processes, which is
        // why we use it instead of Arrays.hashCode or similar. Pinning the
        // pair-wise equality at least catches accidental non-determinism (eg:
        // someone adding a salt or a timestamp).
        val pkg = "com.acme.app"
        assertThat(deriveAnchorPort(pkg)).isEqualTo(deriveAnchorPort(pkg))
    }

    @Test
    fun `lastBoundPort round-trips through prefs`() {
        // The bind-loop fallback persists whichever candidate succeeded so
        // subsequent launches don't drift unless they have to. If this getter
        // and setter ever disagree, two Strictly-enabled apps that initially
        // resolved to different fallback slots could collide on the next
        // reinstall. Cheap to pin, expensive to debug later.
        val prefs = StrictlyPrefs(RuntimeEnvironment.getApplication())
        assertThat(prefs.lastBoundPort()).isNull()

        prefs.setLastBoundPort(8742)
        assertThat(prefs.lastBoundPort()).isEqualTo(8742)

        prefs.setLastBoundPort(9999)
        assertThat(prefs.lastBoundPort()).isEqualTo(9999)
    }
}
