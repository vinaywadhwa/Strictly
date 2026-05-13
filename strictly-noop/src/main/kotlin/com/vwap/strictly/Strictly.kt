package com.vwap.strictly

import android.app.Application
import android.content.Context
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
 * All methods return immediately. [violations] always emits an empty map so
 * any code that reads it in release builds (eg a debug menu that happens to
 * be compiled in but hidden) behaves as if nothing has been recorded.
 */
object Strictly {

    private val empty = MutableStateFlow<Map<String, Violation>>(emptyMap())

    val violations: StateFlow<Map<String, Violation>> = empty.asStateFlow()

    fun install(application: Application, config: StrictlyConfig = StrictlyConfig()) = Unit
    fun openDetailScreen(context: Context) = Unit
    fun markBaseline() = Unit
    fun clear() = Unit

    val isLiveListeningSupported: Boolean = false
}
