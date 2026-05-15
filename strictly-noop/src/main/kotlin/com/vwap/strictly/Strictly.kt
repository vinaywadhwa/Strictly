package com.vwap.strictly

import android.app.Application
import android.content.Context
import com.vwap.strictly.core.Session
import com.vwap.strictly.core.SessionSummary
import com.vwap.strictly.core.StrictlyConfig
import com.vwap.strictly.core.Violation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * No-op stand-in for the real [Strictly] API. Wired in via
 * `releaseImplementation("com.vwap.strictly:strictly-noop:<version>")` so
 * production builds carry no code from the live library.
 *
 * Every method returns immediately. Every flow emits an empty value so any
 * code that reads the surface in release builds (eg: a debug menu that
 * happens to be compiled in but hidden) behaves as if nothing has been
 * recorded.
 */
object Strictly {

    private val emptyViolations = MutableStateFlow<Map<String, Violation>>(emptyMap())
    private val emptySessions = MutableStateFlow<List<SessionSummary>>(emptyList())

    val violations: StateFlow<Map<String, Violation>> = emptyViolations.asStateFlow()
    val sessions: StateFlow<List<SessionSummary>> = emptySessions.asStateFlow()
    val currentSessionId: String = ""

    fun install(application: Application, config: StrictlyConfig = StrictlyConfig()) = Unit
    fun openDetailScreen(context: Context) = Unit
    fun loadSession(id: String): Session? = null
    fun clearCurrent() = Unit
    fun deleteSession(id: String) = Unit
    fun wipeAllSessions() = Unit

    val isLiveListeningSupported: Boolean = false

    object DebugHttp {
        private val falseFlow = MutableStateFlow(false)
        private val nullFlow = MutableStateFlow<String?>(null)

        val running: StateFlow<Boolean> = falseFlow.asStateFlow()
        val desired: StateFlow<Boolean> = falseFlow.asStateFlow()
        val lastError: StateFlow<String?> = nullFlow.asStateFlow()
        val port: Int = 0
        val hasSecret: Boolean = false
        fun setEnabled(enabled: Boolean) = Unit
        fun retry() = Unit
    }
}
