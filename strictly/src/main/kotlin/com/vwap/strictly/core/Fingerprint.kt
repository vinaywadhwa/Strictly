package com.vwap.strictly.core

import java.security.MessageDigest

/**
 * Produces a stable fingerprint for a violation so that repeated occurrences
 * of the same underlying bug collapse into one record with a count, instead
 * of producing 200 separate notifications during a single scroll.
 *
 * Design choices:
 * - We hash (type + first N "interesting" frames), where "interesting" means
 *   frames whose class lives inside one of the consumer's app packages.
 *   Without this filter, a violation that originates in `java.io.FileInputStream`
 *   would always look the same and we'd never tell apart "preferences read"
 *   from "asset read."
 * - We exclude line numbers from the hash. A trivial edit two lines above the
 *   violation site would otherwise invalidate the fingerprint. Class + method
 *   is the right granularity for an identity that survives refactors.
 * - SHA-1 is fine here: not security-sensitive, just an identity. Stays short
 *   enough to read at a glance in exports.
 */
internal object Fingerprint {

    /** How many app-frames to fold into the fingerprint. More frames = stricter de-dupe. */
    private const val FRAME_DEPTH = 3

    fun compute(
        type: ViolationType,
        frames: List<StackFrame>,
        appPackages: List<String>,
    ): String {
        val appFrames = frames.asSequence()
            .filter { frame -> appPackages.any { pkg -> frame.className.startsWith(pkg) } }
            .take(FRAME_DEPTH)
            .toList()

        // If nothing in the trace belongs to the consumer's packages (eg the
        // violation fires deep inside a third-party SDK), fall back to the top
        // 3 raw frames so we still de-dupe sensibly.
        val finalFrames = appFrames.ifEmpty { frames.take(FRAME_DEPTH) }

        val seed = buildString {
            append(type.name)
            append('|')
            finalFrames.forEach { frame ->
                append(frame.className).append('#').append(frame.methodName).append(';')
            }
        }

        return sha1(seed).take(12)
    }

    private fun sha1(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return buildString(bytes.size * 2) {
            for (b in bytes) {
                val v = b.toInt() and 0xFF
                if (v < 16) append('0')
                append(v.toString(16))
            }
        }
    }
}
