package com.vwap.strictly.core

import java.util.UUID

/**
 * A StrictMode violation captured from the device. Decoupled from
 * `android.os.strictmode.Violation` so the SDK can compile on API 21+
 * and so the public surface stays stable across new Android releases.
 */
data class Violation(
    val id: String = UUID.randomUUID().toString(),
    val fingerprint: String,
    val type: ViolationType,
    val message: String,
    val stackTrace: List<StackFrame>,
    val firstAppFrame: StackFrame?,
    val firstOccurrenceAtMillis: Long,
    val lastOccurrenceAtMillis: Long,
    val occurrenceCount: Int,
    val threadName: String,
    val processName: String,
) {
    val title: String get() = type.displayName
    val subtitle: String get() = firstAppFrame?.formatShort() ?: "unknown origin"
}

/**
 * The kinds of violations we surface. Maps cleanly to the StrictMode policy
 * detectors, with `Unknown` as a defensive fallback for subtypes added in
 * future Android versions.
 */
enum class ViolationType(val displayName: String, val category: Category) {
    DiskRead("Disk read on main thread", Category.Thread),
    DiskWrite("Disk write on main thread", Category.Thread),
    Network("Network call on main thread", Category.Thread),
    CustomSlowCall("Slow call on main thread", Category.Thread),
    ResourceMismatch("Resource mismatch", Category.Thread),
    UnbufferedIo("Unbuffered I/O", Category.Thread),
    ExplicitGc("Explicit GC", Category.Thread),

    LeakedClosable("Leaked Closeable", Category.Vm),
    LeakedActivity("Leaked Activity", Category.Vm),
    LeakedSqlObject("Leaked SQL object", Category.Vm),
    LeakedRegistration("Leaked registration", Category.Vm),
    FileUriExposure("File:// URI exposed", Category.Vm),
    CleartextNetwork("Cleartext network", Category.Vm),
    UntaggedSocket("Untagged socket", Category.Vm),
    NonSdkApi("Non-SDK API access", Category.Vm),
    ContentUriWithoutPermission("Content URI without permission", Category.Vm),
    CredentialProtectedWhileLocked("Credential-protected data while locked", Category.Vm),
    ImplicitDirectBoot("Implicit direct boot violation", Category.Vm),
    IncorrectContextUse("Incorrect Context use", Category.Vm),
    UnsafeIntentLaunch("Unsafe Intent launch", Category.Vm),

    Unknown("Unknown violation", Category.Thread);

    enum class Category { Thread, Vm }
}

/**
 * One frame of a stack trace, simplified from java.lang.StackTraceElement
 * for serialization friendliness and a smaller memory footprint when we
 * hold hundreds of these.
 */
data class StackFrame(
    val className: String,
    val methodName: String,
    val fileName: String?,
    val lineNumber: Int,
) {
    /** Compact form for one-line summaries: "UserRepo.loadProfile:142". */
    fun formatShort(): String {
        val simpleClass = className.substringAfterLast('.')
        return if (lineNumber > 0) "$simpleClass.$methodName:$lineNumber"
        else "$simpleClass.$methodName"
    }

    /** Full form for stack-trace display: "com.foo.UserRepo.loadProfile(UserRepo.kt:142)". */
    fun formatFull(): String {
        val location = when {
            fileName != null && lineNumber > 0 -> "($fileName:$lineNumber)"
            fileName != null -> "($fileName)"
            else -> "(Unknown Source)"
        }
        return "$className.$methodName$location"
    }
}
