package com.vwap.strictly.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FingerprintTest {

    private val appPackages = listOf("com.acme.")

    @Test
    fun `same type and frames produce same fingerprint`() {
        val frames = listOf(
            StackFrame("com.acme.UserRepo", "loadProfile", "UserRepo.kt", 142),
            StackFrame("com.acme.Screen", "render", "Screen.kt", 50),
        )
        val a = Fingerprint.compute(ViolationType.DiskRead, frames, appPackages)
        val b = Fingerprint.compute(ViolationType.DiskRead, frames, appPackages)
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `line number changes do not change fingerprint`() {
        val frames1 = listOf(
            StackFrame("com.acme.UserRepo", "loadProfile", "UserRepo.kt", 142),
        )
        val frames2 = listOf(
            StackFrame("com.acme.UserRepo", "loadProfile", "UserRepo.kt", 999),
        )
        val a = Fingerprint.compute(ViolationType.DiskRead, frames1, appPackages)
        val b = Fingerprint.compute(ViolationType.DiskRead, frames2, appPackages)
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `different violation types produce different fingerprints`() {
        val frames = listOf(StackFrame("com.acme.UserRepo", "load", "U.kt", 1))
        val read = Fingerprint.compute(ViolationType.DiskRead, frames, appPackages)
        val write = Fingerprint.compute(ViolationType.DiskWrite, frames, appPackages)
        assertThat(read).isNotEqualTo(write)
    }

    @Test
    fun `framework-only stack falls back to top frames`() {
        // No app-frames at all; should still produce a stable fingerprint.
        val frames = listOf(
            StackFrame("java.io.FileInputStream", "read", "FIS.java", 100),
            StackFrame("java.io.FileInputStream", "open", "FIS.java", 50),
        )
        val a = Fingerprint.compute(ViolationType.DiskRead, frames, appPackages)
        val b = Fingerprint.compute(ViolationType.DiskRead, frames, appPackages)
        assertThat(a).isEqualTo(b)
        assertThat(a).isNotEmpty()
    }

    @Test
    fun `framework prefix on app frame is preferred over deeper system frames`() {
        val frames = listOf(
            StackFrame("java.io.FileInputStream", "read", "FIS.java", 100),
            StackFrame("com.acme.UserRepo", "load", "U.kt", 1),
            StackFrame("com.acme.AnotherRepo", "load", "A.kt", 1),
        )
        val appOnly = listOf(frames[1], frames[2])
        // Without the framework frame, fingerprint matches the app-only computation
        // because the framework frame is filtered out.
        val a = Fingerprint.compute(ViolationType.DiskRead, frames, appPackages)
        val b = Fingerprint.compute(ViolationType.DiskRead, appOnly, appPackages)
        assertThat(a).isEqualTo(b)
    }
}
