package com.vwap.strictly.store

import android.content.Context
import android.os.StrictMode
import android.util.Log
import com.vwap.strictly.core.Session
import com.vwap.strictly.core.SessionSummary
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Disk-backed writer for [Session] state. Lives alongside [SessionStore].
 *
 * Two-file layout under `<context.filesDir>/strictly/`:
 * - `sessions.json`: the index. Array of [SessionSummary] for fast list rendering.
 * - `sessions/<session-uuid>.json`: one file per session.
 *
 * Writes are debounced (~[DEBOUNCE_MILLIS]) to avoid disk churn during scroll
 * storms. The writer runs on a dedicated single-thread scheduled executor and
 * temporarily allows StrictMode disk writes around its own IO so it doesn't
 * trip the very policy it lives behind.
 */
internal class SessionPersister(context: Context) {

    private val root: File = File(context.filesDir, "strictly").apply { mkdirs() }
    private val sessionsDir: File = File(root, "sessions").apply { mkdirs() }
    private val indexFile: File = File(root, "sessions.json")

    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "Strictly-Persister").apply { isDaemon = true }
    }

    @Volatile private var pendingWrite: ScheduledFuture<*>? = null

    fun scheduleWrite(session: Session, afterWrite: () -> Unit) {
        pendingWrite?.cancel(false)
        pendingWrite = executor.schedule(
            {
                runCatching {
                    writeSessionNow(session)
                    afterWrite()
                }.onFailure { t ->
                    Log.w(TAG, "Failed to persist session ${session.id}", t)
                }
            },
            DEBOUNCE_MILLIS,
            TimeUnit.MILLISECONDS,
        )
    }

    fun readIndex(): List<SessionSummary> = withRelaxedStrictMode {
        runCatching {
            if (!indexFile.exists()) return@runCatching emptyList<SessionSummary>()
            val text = indexFile.readText()
            SessionJson.decodeIndex(text)
        }.getOrElse { t ->
            Log.w(TAG, "Failed to load session index", t)
            emptyList()
        }
    }

    fun writeIndex(summaries: List<SessionSummary>) {
        executor.submit {
            runCatching { writeIndexNow(summaries) }
                .onFailure { Log.w(TAG, "Failed to write session index", it) }
        }
    }

    fun readSession(id: String): Session? = withRelaxedStrictMode {
        runCatching {
            val file = File(sessionsDir, "$id.json")
            if (!file.exists()) return@runCatching null
            SessionJson.decodeSession(file.readText())
        }.getOrElse { t ->
            Log.w(TAG, "Failed to load session $id", t)
            null
        }
    }

    fun deleteSessionFile(id: String) {
        executor.submit {
            runCatching {
                File(sessionsDir, "$id.json").delete()
            }
        }
    }

    fun wipeAll() {
        executor.submit {
            runCatching {
                sessionsDir.listFiles()?.forEach { it.delete() }
                indexFile.delete()
            }
        }
    }

    private fun writeSessionNow(session: Session) = withRelaxedStrictMode {
        val target = File(sessionsDir, "${session.id}.json")
        val tmp = File(sessionsDir, "${session.id}.json.tmp")
        tmp.writeText(SessionJson.encodeSession(session))
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    private fun writeIndexNow(summaries: List<SessionSummary>) = withRelaxedStrictMode {
        val tmp = File(root, "sessions.json.tmp")
        tmp.writeText(SessionJson.encodeIndex(summaries))
        if (!tmp.renameTo(indexFile)) {
            tmp.copyTo(indexFile, overwrite = true)
            tmp.delete()
        }
    }

    /**
     * Strictly itself MUST NOT trip StrictMode while persisting violations.
     * Otherwise every disk write spawns a new violation that triggers a new
     * write that spawns a new violation. Death spiral.
     */
    private inline fun <T> withRelaxedStrictMode(block: () -> T): T {
        val prevThread = StrictMode.allowThreadDiskWrites()
        return try {
            block()
        } finally {
            StrictMode.setThreadPolicy(prevThread)
        }
    }

    private companion object {
        const val TAG = "Strictly"
        const val DEBOUNCE_MILLIS = 500L
    }
}
