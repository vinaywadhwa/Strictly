package com.vwap.strictly.core

/**
 * Minimal copies of the public Strictly types, just enough that callers'
 * type references compile in release builds. None of the data classes here
 * are ever instantiated. The no-op flows only ever emit empty values.
 *
 * The class layout intentionally matches the live module exactly so any
 * code path that defensively references these types (eg: a debug-menu list
 * that's compiled into both flavours) does the right thing.
 */

data class Violation(
    val id: String = "",
    val fingerprint: String = "",
    val type: ViolationType = ViolationType.Unknown,
    val message: String = "",
    val stackTrace: List<StackFrame> = emptyList(),
    val firstAppFrame: StackFrame? = null,
    val firstActionableFrame: StackFrame? = null,
    val firstOccurrenceAtMillis: Long = 0,
    val lastOccurrenceAtMillis: Long = 0,
    val occurrenceCount: Int = 0,
    val threadName: String = "",
    val processName: String = "",
)

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

data class StackFrame(
    val className: String = "",
    val methodName: String = "",
    val fileName: String? = null,
    val lineNumber: Int = 0,
)

data class Session(
    val id: String = "",
    val startedAtMillis: Long = 0,
    val lastEventAtMillis: Long = 0,
    val appVersionName: String = "",
    val appVersionCode: Int = 0,
    val deviceModel: String = "",
    val osLevel: Int = 0,
    val violations: Map<String, Violation> = emptyMap(),
) {
    val uniqueCount: Int get() = 0
    val totalEvents: Int get() = 0
    val topType: ViolationType? get() = null
}

data class SessionSummary(
    val id: String = "",
    val startedAtMillis: Long = 0,
    val lastEventAtMillis: Long = 0,
    val uniqueCount: Int = 0,
    val totalEvents: Int = 0,
    val topType: ViolationType? = null,
    val appVersionName: String = "",
)

data class StrictlyConfig(
    val enabled: Boolean = false,
    val appPackages: List<String> = emptyList(),
    val ignoredPackages: List<String> = emptyList(),
    val platformPackages: List<String> = emptyList(),
    val detectedTypes: Set<ViolationType> = emptySet(),
    val maxStoredViolations: Int = 0,
    val liveNotificationUpdates: Boolean = false,
    val notificationUpdateDebounceMillis: Long = 0,
    val registerAppShortcut: Boolean = false,
    val maxStoredSessions: Int = 0,
    val httpDebugPort: Int = 0,
    val httpDebugAutoStart: Boolean = false,
    val httpDebugSecret: String? = null,
)
