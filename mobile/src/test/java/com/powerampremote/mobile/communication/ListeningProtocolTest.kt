package com.powerampremote.mobile.communication

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class ListeningProtocolTest {
    private val utc = ZoneOffset.UTC
    private val hour = 3_600_000L
    private fun at(text: String) = LocalDateTime.parse(text).toInstant(utc).toEpochMilli()
    private val day = LocalDate.of(2026, 11, 1).toEpochDay()

    @Test fun roundTrips() {
        for (snapshot in listOf(ListeningProtocol.Snapshot(), ListeningProtocol.Snapshot(hour, day, 60_000, 1_700_000_000_000))) {
            assertEquals(snapshot, ListeningProtocol.decode(ListeningProtocol.encode(snapshot)))
        }
    }

    @Test fun rejectsInvalidValues() {
        for (text in listOf("", "v2|0|1|0|0", "v1|0|1|0", "v1|0|1|0|0|x", "v1|-1|1|0|0", "v1|0|-2|0|0", "v1|a|1|0|0", "v1|0|1|-5|0")) {
            assertNull(text, ListeningProtocol.decode(text.toByteArray(Charsets.US_ASCII)))
        }
    }

    @Test fun liveSplitsOpenSegmentAtMidnight() {
        val now = at("2026-11-01T00:30:00")
        val snapshot = ListeningProtocol.Snapshot(totalMs = 0, day = day, todayMs = 0, playingSince = at("2026-10-31T23:30:00"))
        assertEquals(Pair(30 * 60_000L, 60 * 60_000L), ListeningProtocol.live(snapshot, now, utc))
    }

    @Test fun liveResetsTodayFromPreviousDay() {
        val now = at("2026-11-01T10:00:00")
        val snapshot = ListeningProtocol.Snapshot(totalMs = hour, day = day - 1, todayMs = 20 * 60_000L)
        assertEquals(Pair(0L, hour), ListeningProtocol.live(snapshot, now, utc))
    }

    @Test fun liveCapsOpenSegment() {
        val now = at("2026-11-01T12:00:00")
        val snapshot = ListeningProtocol.Snapshot(day = day, playingSince = at("2026-11-01T02:00:00"))
        assertEquals(Pair(6 * hour, 6 * hour), ListeningProtocol.live(snapshot, now, utc))
    }

    @Test fun formats() {
        assertEquals("0min", ListeningProtocol.format(0))
        assertEquals("45min", ListeningProtocol.format(45 * 60_000L))
        assertEquals("1h 05min", ListeningProtocol.format(65 * 60_000L))
        assertEquals("2h 05min", ListeningProtocol.format(125 * 60_000L))
    }
}
