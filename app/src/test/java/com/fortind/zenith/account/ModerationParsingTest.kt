package com.fortind.zenith.account

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ModerationParsingTest {

    @Test
    fun aTimedMuteKeepsItsEndAndARemoteUserKeepsItsHost() {
        val entry = parseModerationEntry(
            ModerationKind.Mute,
            JSONObject(
                """{"id":"m1","createdAt":"2026-01-01T00:00:00.000Z","expiresAt":"2026-01-02T00:00:00.000Z","muteeId":"u1",
                    "mutee":{"id":"u1","username":"bob","host":"other.example","name":"Bob","avatarUrl":"https://x/a.png"}}""",
            ),
        )!!

        assertEquals("m1", entry.id)
        assertEquals("u1", entry.user.id)
        assertEquals("@bob@other.example", entry.user.handle)
        assertEquals(1_767_312_000_000L, entry.expiresAtMillis)
    }

    @Test
    fun anIndefiniteMuteAndALocalUserHaveNoEndOrHost() {
        val entry = parseModerationEntry(
            ModerationKind.Mute,
            JSONObject("""{"id":"m2","expiresAt":null,"mutee":{"id":"u2","username":"amy","host":null,"name":null}}"""),
        )!!

        assertNull(entry.expiresAtMillis)
        assertNull(entry.user.host)
        assertEquals("amy", entry.user.displayName)
        assertEquals("@amy@social.fort1nd.com", entry.user.handle)
    }

    @Test
    fun blocksReadTheBlockeeAndMalformedRowsAreSkipped() {
        val block = parseModerationEntry(
            ModerationKind.Block,
            JSONObject("""{"id":"b1","blockee":{"id":"u3","username":"cat"}}"""),
        )
        assertNotNull(block)
        assertEquals("u3", block!!.user.id)

        assertNull(parseModerationEntry(ModerationKind.Block, JSONObject("""{"id":"b2","mutee":{"id":"u3","username":"cat"}}""")))
        assertNull(parseModerationEntry(ModerationKind.Mute, JSONObject("""{"mutee":{"id":"u3","username":"cat"}}""")))
    }
}
