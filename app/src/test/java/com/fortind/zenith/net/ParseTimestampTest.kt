package com.fortind.zenith.net

import java.time.Instant
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ParseTimestampTest {

    @Test
    fun matchesInstantForTheFormatTheServerSends() {
        listOf(
            "2026-01-02T00:00:00.000Z",
            "2026-11-02T10:06:59.123Z",
            "1970-01-01T00:00:00.000Z",
            "2000-02-29T23:59:59.999Z",
            "2026-06-15T12:30:45Z",
        ).forEach { assertEquals(it, Instant.parse(it).toEpochMilli(), parseTimestamp(it)) }
    }

    @Test
    fun fractionsAreTruncatedToMilliseconds() {
        assertEquals(Instant.parse("2026-01-02T03:04:05.123Z").toEpochMilli(), parseTimestamp("2026-01-02T03:04:05.123456789Z"))
        assertEquals(Instant.parse("2026-01-02T03:04:05.500Z").toEpochMilli(), parseTimestamp("2026-01-02T03:04:05.5Z"))
    }

    @Test
    fun offsetsAreApplied() {
        listOf("2026-01-02T03:04:05.000+09:00", "2026-01-02T03:04:05.000-0530").forEach {
            assertEquals(it, OffsetDateTime.parse(it.replace("-0530", "-05:30")).toInstant().toEpochMilli(), parseTimestamp(it))
        }
    }

    @Test
    fun anythingElseIsZero() {
        listOf(null, "", "yesterday", "2026-13-02T00:00:00Z", "2026-01-02", "2026-01-02T25:00:00Z").forEach {
            assertEquals(it, 0L, parseTimestamp(it))
        }
    }
}
