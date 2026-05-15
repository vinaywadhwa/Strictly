#!/usr/bin/env node
// Strictly MCP server: a thin proxy from MCP stdio tools to the on-device
// Strictly HTTP debug server.
//
// Why: an AI agent (Claude Code or Cursor) running on the dev's laptop can
// call these tools to read live StrictMode violations off the connected
// Android device. The user pairs the device port via
// `adb forward tcp:8765 tcp:8765`. Everything below "just works" through
// 127.0.0.1.
//
// Error envelopes use a structured `{code, message, nextSteps[]}` JSON body so
// the calling agent can teach the user how to fix the setup itself: "Strictly
// HTTP server isn't running, here's how to turn it on", or "Wrong port,
// here's where to update it".

import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { z } from 'zod';

// ---- Configuration ---------------------------------------------------------

const STRICTLY_URL =
  process.env.STRICTLY_URL?.replace(/\/+$/, '') || 'http://127.0.0.1:8765';
const STRICTLY_SECRET = process.env.STRICTLY_SECRET || '';

const log = (...args) => console.error('[strictly-mcp]', ...args);

// ---- Error envelopes -------------------------------------------------------

const ERR = {
  HTTP_UNREACHABLE: 'STRICTLY_HTTP_UNREACHABLE',
  HTTP_AUTH_REQUIRED: 'STRICTLY_HTTP_AUTH_REQUIRED',
  HTTP_AUTH_REJECTED: 'STRICTLY_HTTP_AUTH_REJECTED',
  SESSION_NOT_FOUND: 'STRICTLY_SESSION_NOT_FOUND',
  NO_SESSIONS: 'STRICTLY_NO_SESSIONS',
  LIBRARY_NOT_INSTALLED: 'STRICTLY_LIBRARY_NOT_INSTALLED',
  VERSION_MISMATCH: 'STRICTLY_VERSION_MISMATCH',
  UNEXPECTED: 'STRICTLY_UNEXPECTED',
};

function errorResult(code, message, nextSteps) {
  const payload = { error: { code, message, nextSteps: nextSteps || [] } };
  return {
    isError: true,
    content: [{ type: 'text', text: JSON.stringify(payload, null, 2) }],
  };
}

function jsonResult(obj) {
  return {
    content: [{ type: 'text', text: JSON.stringify(obj, null, 2) }],
  };
}

function textResult(text) {
  return { content: [{ type: 'text', text }] };
}

// ---- HTTP client ----------------------------------------------------------

async function strictlyFetch(path, { acceptMarkdown = false } = {}) {
  const url = `${STRICTLY_URL}${path}`;
  const headers = {};
  if (STRICTLY_SECRET) headers['X-Strictly-Secret'] = STRICTLY_SECRET;
  if (acceptMarkdown) headers['Accept'] = 'text/markdown,application/json';

  let res;
  try {
    res = await fetch(url, { headers, signal: AbortSignal.timeout(5000) });
  } catch (e) {
    const reason = e?.cause?.code || e?.code || e?.name || 'fetch failed';
    const port = urlPort(STRICTLY_URL);
    throw new StrictlyError(
      ERR.HTTP_UNREACHABLE,
      `Could not reach Strictly at ${STRICTLY_URL} (${reason}).`,
      [
        `Run \`adb forward tcp:${port} tcp:${port}\` on the host (this is the most common cause). Works the same for USB devices or emulators.`,
        'Make sure the Android device with the Strictly-instrumented app is attached (`adb devices` should list it as `device`, not `unauthorized` or `offline`).',
        'In the app, open Strictly via the home-screen shortcut, tap the gear icon, then toggle "Debug HTTP server" on.',
        `If you changed Strictly's httpDebugPort, set STRICTLY_URL to match (currently ${STRICTLY_URL}).`,
      ],
    );
  }

  if (res.status === 401) {
    if (!STRICTLY_SECRET) {
      throw new StrictlyError(
        ERR.HTTP_AUTH_REQUIRED,
        'Strictly is configured with a secret but none was provided.',
        [
          'Set the STRICTLY_SECRET environment variable in your MCP client config to the value from your app\'s StrictlyConfig.httpDebugSecret',
        ],
      );
    }
    throw new StrictlyError(
      ERR.HTTP_AUTH_REJECTED,
      'Strictly rejected the X-Strictly-Secret header.',
      [
        'Verify STRICTLY_SECRET matches StrictlyConfig.httpDebugSecret in the app',
        'Restart the MCP client after changing the env var',
      ],
    );
  }

  if (res.status === 404) {
    let parsed = null;
    try { parsed = await res.json(); } catch {}
    if (parsed?.error?.code === ERR.SESSION_NOT_FOUND) {
      throw new StrictlyError(
        ERR.SESSION_NOT_FOUND,
        parsed.error.message || 'Session not found.',
        [
          'Call list_sessions to see currently available session ids',
          'The session may have been evicted (default cap is 20 archived sessions)',
        ],
      );
    }
    throw new StrictlyError(
      ERR.UNEXPECTED,
      `Strictly responded 404 at ${path}`,
      ['Likely a strictly-mcp / strictly version skew, run `claude mcp list` and check'],
    );
  }

  if (!res.ok) {
    let body = '';
    try { body = await res.text(); } catch {}
    throw new StrictlyError(
      ERR.UNEXPECTED,
      `Strictly responded ${res.status} at ${path}. Body: ${body.slice(0, 200)}`,
      [],
    );
  }

  return res;
}

function urlPort(url) {
  try { return new URL(url).port || '80'; } catch { return '8765'; }
}

class StrictlyError extends Error {
  constructor(code, message, nextSteps) {
    super(message);
    this.code = code;
    this.nextSteps = nextSteps || [];
  }
}

async function strictlyJson(path) {
  const res = await strictlyFetch(path);
  let body;
  try {
    body = await res.json();
  } catch (e) {
    throw new StrictlyError(
      ERR.LIBRARY_NOT_INSTALLED,
      `Server at ${STRICTLY_URL} responded but didn't return JSON. Almost certainly not a Strictly server.`,
      [
        'Verify STRICTLY_URL points at the on-device Strictly server, not a generic HTTP service',
        'Default URL: http://127.0.0.1:8765',
      ],
    );
  }
  return body;
}

async function strictlyText(path) {
  const res = await strictlyFetch(path, { acceptMarkdown: true });
  return await res.text();
}

async function ensureHealthy() {
  let body;
  try {
    body = await strictlyJson('/v1/health');
  } catch (e) {
    if (e instanceof StrictlyError) throw e;
    throw new StrictlyError(ERR.UNEXPECTED, e.message, []);
  }
  if (body?.library !== 'strictly') {
    throw new StrictlyError(
      ERR.LIBRARY_NOT_INSTALLED,
      `Server at ${STRICTLY_URL} responded but didn't identify as Strictly (library=${body?.library}).`,
      ['Make sure the device is running an app that depends on com.vwap.strictly:strictly:0.1.0+'],
    );
  }
  return body;
}

// ---- Tool handlers --------------------------------------------------------

async function runTool(handler) {
  try {
    return await handler();
  } catch (e) {
    if (e instanceof StrictlyError) {
      return errorResult(e.code, e.message, e.nextSteps);
    }
    log('Unexpected error:', e);
    return errorResult(ERR.UNEXPECTED, e.message || String(e), []);
  }
}

const server = new McpServer({ name: 'strictly-mcp', version: '0.1.0' });

server.registerTool(
  'strictly_health',
  {
    title: 'Strictly health',
    description:
      'Verify the strictly-mcp can reach the on-device Strictly debug server. Returns library version, schema, and the live session id. Call this first when troubleshooting setup.',
    inputSchema: z.object({}),
    annotations: { readOnlyHint: true, idempotentHint: true },
  },
  async () => runTool(async () => {
    const body = await ensureHealthy();
    return jsonResult({
      ok: true,
      url: STRICTLY_URL,
      library: body.library,
      version: body.version,
      schema: body.schema,
      currentSessionId: body.currentSessionId,
      currentSessionStartedAtMillis: body.currentSessionStartedAtMillis,
    });
  }),
);

server.registerTool(
  'strictly_list_sessions',
  {
    title: 'List sessions',
    description:
      'List all recorded Strictly sessions on the device, latest-first. Each entry includes the session id, counts, top violation type, and whether it is the live session.',
    inputSchema: z.object({}),
    annotations: { readOnlyHint: true, idempotentHint: true },
  },
  async () => runTool(async () => {
    await ensureHealthy();
    const body = await strictlyJson('/v1/sessions');
    const sessions = body.sessions || [];
    if (sessions.length === 0) {
      return errorResult(
        ERR.NO_SESSIONS,
        'Strictly is running but no sessions have been recorded yet.',
        [
          'Use the app for a bit so something trips StrictMode',
          'Then call strictly_list_sessions again',
        ],
      );
    }
    return jsonResult({ sessions });
  }),
);

server.registerTool(
  'strictly_get_session',
  {
    title: 'Get full session (JSON)',
    description:
      'Fetch one Strictly session by id. Returns the full canonical JSON (schema-versioned) including every violation\'s stack trace.',
    inputSchema: z.object({
      id: z
        .string()
        .describe('Session id from strictly_list_sessions, or the literal string "live" for the current session.'),
    }),
    annotations: { readOnlyHint: true, idempotentHint: true },
  },
  async ({ id }) => runTool(async () => {
    const health = await ensureHealthy();
    const realId = id === 'live' ? health.currentSessionId : id;
    const body = await strictlyJson(`/v1/sessions/${encodeURIComponent(realId)}`);
    return jsonResult(body);
  }),
);

server.registerTool(
  'strictly_get_session_markdown',
  {
    title: 'Get session as Markdown',
    description:
      'Fetch one Strictly session by id rendered as Markdown. Best for direct inclusion in a chat or PR review.',
    inputSchema: z.object({
      id: z
        .string()
        .describe('Session id from strictly_list_sessions, or the literal string "live" for the current session.'),
    }),
    annotations: { readOnlyHint: true, idempotentHint: true },
  },
  async ({ id }) => runTool(async () => {
    const health = await ensureHealthy();
    const realId = id === 'live' ? health.currentSessionId : id;
    const md = await strictlyText(`/v1/sessions/${encodeURIComponent(realId)}.md`);
    return textResult(md);
  }),
);

server.registerTool(
  'strictly_top_violations',
  {
    title: 'Top violations across sessions',
    description:
      'Convenience: aggregate violation counts across the N most recent sessions and return the worst offenders. Useful first call when an agent is asked "what should I fix today?".',
    inputSchema: z.object({
      sessionLimit: z
        .number()
        .int()
        .min(1)
        .max(20)
        .default(3)
        .describe('How many most-recent sessions to combine. Default 3.'),
      offenderLimit: z
        .number()
        .int()
        .min(1)
        .max(50)
        .default(10)
        .describe('How many top offenders to return. Default 10.'),
    }),
    annotations: { readOnlyHint: true, idempotentHint: true },
  },
  async ({ sessionLimit, offenderLimit }) => runTool(async () => {
    await ensureHealthy();
    const index = await strictlyJson('/v1/sessions');
    const sessions = (index.sessions || []).slice(0, sessionLimit);
    if (sessions.length === 0) {
      return errorResult(
        ERR.NO_SESSIONS,
        'No sessions recorded yet on the device.',
        ['Use the app so StrictMode trips something'],
      );
    }

    const byKey = new Map();
    for (const s of sessions) {
      const full = await strictlyJson(`/v1/sessions/${encodeURIComponent(s.id)}`);
      for (const v of full.violations || []) {
        const key = `${v.type}|${v.firstActionableFrame || '?'}`;
        const existing = byKey.get(key);
        if (existing) {
          existing.count += v.occurrenceCount || 0;
          existing.sessions.add(s.id);
        } else {
          byKey.set(key, {
            type: v.type,
            typeDisplay: v.typeDisplay,
            origin: v.firstActionableFrame,
            isThirdPartyOrigin: v.isThirdPartyOrigin,
            count: v.occurrenceCount || 0,
            sessions: new Set([s.id]),
          });
        }
      }
    }

    const offenders = [...byKey.values()]
      .sort((a, b) => b.count - a.count)
      .slice(0, offenderLimit)
      .map((o) => ({
        ...o,
        sessions: [...o.sessions],
        sessionCount: o.sessions.size,
      }));

    return jsonResult({
      summary: {
        sessionsConsidered: sessions.length,
        uniqueOffenders: byKey.size,
      },
      offenders,
    });
  }),
);

// ---- Bootstrap -------------------------------------------------------------

async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  log(`Connected. Proxying to ${STRICTLY_URL}${STRICTLY_SECRET ? ' (auth)' : ''}.`);
}

main().catch((e) => {
  log('Fatal:', e);
  process.exit(1);
});
