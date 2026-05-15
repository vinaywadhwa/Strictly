package com.vwap.strictly.store

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pins the persisted JSON schema identifiers. These strings live in clients'
 * stored sessions on disk and in HTTP responses consumed by the MCP and any
 * other external tool. Bumping them is a breaking change and must be a
 * deliberate, versioned act, not an incidental refactor.
 *
 * If you intentionally need a new schema version, bump the suffix here AND
 * write a migration path for the loader, then update this test.
 */
class SessionSchemaGoldenTest {

    @Test
    fun `session schema id is stable`() {
        assertThat(SessionJson.SESSION_SCHEMA).isEqualTo("strictly/session.v1")
    }

    @Test
    fun `sessions index schema id is stable`() {
        assertThat(SessionJson.INDEX_SCHEMA).isEqualTo("strictly/sessions-index.v1")
    }
}
