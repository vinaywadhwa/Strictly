package com.vwap.strictly.http

import com.vwap.strictly.core.Session
import com.vwap.strictly.export.SessionExporter
import com.vwap.strictly.store.SessionJson
import com.vwap.strictly.store.SessionStore
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Opt-in loopback HTTP server backing the MCP and any external tool that
 * wants to read Strictly's recorded sessions.
 *
 * Routes (all under `/v1`):
 * - `GET  /v1/health`: version plus meta probe used to detect "is the right
 *   Strictly running?".
 * - `GET  /v1/sessions`: index of all sessions (latest-first).
 * - `GET  /v1/sessions/{id}`: full session JSON (the same canonical schema
 *   the file-based persister writes).
 * - `GET  /v1/sessions/{id}.md`: Markdown export of a session.
 *
 * Auth:
 * - Binds to 127.0.0.1 only so it's unreachable from off-device.
 * - Optionally requires `X-Strictly-Secret: <secret>` on every request when a
 *   secret is set in [com.vwap.strictly.core.StrictlyConfig.httpDebugSecret].
 *   That's belt-and-braces: loopback already isolates it.
 *
 * Errors:
 * - Every response is JSON. On error we return a structured envelope so the
 *   MCP can surface a typed reason to the calling agent:
 *   `{"error": {"code": "...", "message": "..."}}`
 */
internal class StrictlyHttpServer(
    port: Int,
    private val store: SessionStore,
    private val secret: String?,
    private val versionName: String,
) : NanoHTTPD("127.0.0.1", port) {

    init {
        // Set SO_REUSEADDR so the new process can rebind immediately after the
        // old one is killed. Without this, the OS keeps the port in TIME_WAIT
        // for ~60 seconds, which manifests as "Port 8765 is busy" right after
        // an APK reinstall or a manual kill-and-relaunch.
        setServerSocketFactory {
            java.net.ServerSocket().apply { reuseAddress = true }
        }
    }

    /** Start in foreground (blocking) is undesirable; use [tryStart] instead. */
    @Throws(IOException::class)
    fun startServer() {
        start(SOCKET_READ_TIMEOUT, false)
    }

    /** Returns true if the server bound successfully, false if the port was taken. */
    fun tryStart(): Boolean = try {
        startServer()
        true
    } catch (_: IOException) {
        false
    }

    override fun serve(session: IHTTPSession): Response {
        if (!checkAuth(session)) {
            return errorResponse(
                status = Response.Status.UNAUTHORIZED,
                code = "STRICTLY_HTTP_AUTH_REJECTED",
                message = "Missing or wrong X-Strictly-Secret header.",
            )
        }

        val uri = session.uri.trimEnd('/')
        return try {
            when {
                uri == "/v1/health" -> handleHealth()
                uri == "/v1/sessions" -> handleSessionsIndex()
                uri.startsWith("/v1/sessions/") && uri.endsWith(".md") -> {
                    val id = uri.removePrefix("/v1/sessions/").removeSuffix(".md")
                    handleSessionMarkdown(id)
                }
                uri.startsWith("/v1/sessions/") -> {
                    val id = uri.removePrefix("/v1/sessions/")
                    handleSession(id)
                }
                else -> errorResponse(
                    status = Response.Status.NOT_FOUND,
                    code = "STRICTLY_ROUTE_NOT_FOUND",
                    message = "No route at ${session.uri}.",
                )
            }
        } catch (t: Throwable) {
            errorResponse(
                status = Response.Status.INTERNAL_ERROR,
                code = "STRICTLY_INTERNAL_ERROR",
                message = t.message ?: t.javaClass.simpleName,
            )
        }
    }

    private fun checkAuth(session: IHTTPSession): Boolean {
        if (secret.isNullOrEmpty()) return true
        val header = session.headers["x-strictly-secret"]
        return header == secret
    }

    private fun handleHealth(): Response {
        val body = JSONObject().apply {
            put("status", "ok")
            put("library", "strictly")
            put("version", versionName)
            put("schema", SessionJson.SESSION_SCHEMA)
            put("currentSessionId", store.currentSessionId)
            put("currentSessionStartedAtMillis", store.currentStartedAt)
        }
        return jsonResponse(body)
    }

    private fun handleSessionsIndex(): Response {
        val summaries = store.visibleSessions()
        val body = JSONObject().apply {
            put("schema", SessionJson.INDEX_SCHEMA)
            put(
                "sessions",
                JSONArray().apply {
                    summaries.forEach {
                        put(
                            JSONObject().apply {
                                put("id", it.id)
                                put("startedAtMillis", it.startedAtMillis)
                                put("lastEventAtMillis", it.lastEventAtMillis)
                                put("uniqueCount", it.uniqueCount)
                                put("totalEvents", it.totalEvents)
                                put("topType", it.topType?.name)
                                put("appVersionName", it.appVersionName)
                                put("isLive", it.id == store.currentSessionId)
                            },
                        )
                    }
                },
            )
        }
        return jsonResponse(body)
    }

    private fun handleSession(id: String): Response {
        val session = store.loadSession(id) ?: return notFound(id)
        return rawResponse("application/json", SessionExporter.toJson(session))
    }

    private fun handleSessionMarkdown(id: String): Response {
        val session: Session = store.loadSession(id) ?: return notFound(id)
        return rawResponse("text/markdown; charset=utf-8", SessionExporter.toMarkdown(session))
    }

    private fun notFound(id: String): Response = errorResponse(
        status = Response.Status.NOT_FOUND,
        code = "STRICTLY_SESSION_NOT_FOUND",
        message = "No session with id '$id'. Maybe it was evicted or never persisted.",
    )

    private fun jsonResponse(body: JSONObject): Response =
        rawResponse("application/json", body.toString(2))

    private fun rawResponse(mime: String, text: String): Response {
        val r = newFixedLengthResponse(Response.Status.OK, mime, text)
        r.addHeader("Access-Control-Allow-Origin", "*")
        r.addHeader("Cache-Control", "no-store")
        return r
    }

    private fun errorResponse(
        status: Response.Status,
        code: String,
        message: String,
    ): Response {
        val payload = JSONObject().apply {
            put(
                "error",
                JSONObject().apply {
                    put("code", code)
                    put("message", message)
                },
            )
        }
        val r = newFixedLengthResponse(status, "application/json", payload.toString(2))
        r.addHeader("Access-Control-Allow-Origin", "*")
        r.addHeader("Cache-Control", "no-store")
        return r
    }
}
