package com.powerampremote.mobile.communication

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class ListeningProtocolTest {
    private val utc = ZoneOffset.UTC
    private val hour = 3_600_000L
    private val minute = 60_000L
    private fun at(text: String) = LocalDateTime.parse(text).toInstant(utc).toEpochMilli()
    private fun day(text: String) = LocalDate.parse(text).toEpochDay()

    @Test fun roundTrips() {
        val full = ListeningProtocol.Snapshot(mapOf(day("2026-10-31") to hour, day("2026-11-01") to 60 * minute),
            at("2026-11-01T10:00:00"))
        for (snapshot in listOf(ListeningProtocol.Snapshot(), full)) {
            assertEquals(snapshot, ListeningProtocol.decode(ListeningProtocol.encode(snapshot)))
        }
    }

    @Test fun rejectsInvalidValues() {
        val tooLarge = "v2|0|" + "1:1;".repeat(8200)
        for (text in listOf("", "v1|0|1:1", "v2|0", "v2|-1|", "v2|0|1:2:3", "v2|0|a:1", "v2|0|1:-5", "v2|0|1:1;", "v2|x|", tooLarge)) {
            assertNull(text.take(40), ListeningProtocol.decode(text.toByteArray(Charsets.US_ASCII)))
        }
    }

    @Test fun addSegmentSplitsAtMidnight() {
        val days = ListeningProtocol.addSegment(emptyMap(), at("2026-10-31T23:30:00"), at("2026-11-01T00:30:00"), utc)
        assertEquals(mapOf(day("2026-10-31") to 30 * minute, day("2026-11-01") to 30 * minute), days)
    }

    @Test fun withOpenCapsSegmentAtSixHours() {
        val snapshot = ListeningProtocol.Snapshot(playingSince = at("2026-11-01T02:00:00"))
        val days = ListeningProtocol.withOpen(snapshot, at("2026-11-01T12:00:00"), utc)
        assertEquals(mapOf(day("2026-11-01") to 6 * hour), days)
    }

    @Test fun withOpenSplitsAtMidnight() {
        val snapshot = ListeningProtocol.Snapshot(playingSince = at("2026-10-31T23:30:00"))
        val days = ListeningProtocol.withOpen(snapshot, at("2026-11-01T00:30:00"), utc)
        assertEquals(mapOf(day("2026-10-31") to 30 * minute, day("2026-11-01") to 30 * minute), days)
    }

    @Test fun weekStartsOnSunday() {
        val now = at("2026-11-02T09:00:00")
        val days = mapOf(day("2026-10-31") to hour, day("2026-11-01") to 2 * hour, day("2026-11-02") to 3 * hour)
        assertEquals(5 * hour, ListeningProtocol.week(days, now, utc))
    }

    @Test fun monthIgnoresPreviousMonth() {
        val now = at("2026-11-02T09:00:00")
        val days = mapOf(day("2026-10-31") to hour, day("2026-11-01") to 2 * hour)
        assertEquals(2 * hour, ListeningProtocol.month(days, now, utc))
    }

    @Test fun dailyRowsIncludeZeroDays() {
        val now = at("2026-11-02T09:00:00")
        val days = mapOf(day("2026-10-31") to hour)
        assertEquals(listOf(
            LocalDate.parse("2026-11-02") to 0L,
            LocalDate.parse("2026-11-01") to 0L,
            LocalDate.parse("2026-10-31") to hour), ListeningProtocol.dailyRows(days, 31, now, utc))
    }

    @Test fun weeklyAndMonthlyRowsIncludeZeroPeriods() {
        val now = at("2026-11-02T09:00:00")
        val days = mapOf(day("2026-10-31") to hour)
        assertEquals(listOf(LocalDate.parse("2026-11-01") to 0L, LocalDate.parse("2026-10-25") to hour),
            ListeningProtocol.weeklyRows(days, 12, now, utc))
        assertEquals(listOf(LocalDate.parse("2026-11-01") to 0L, LocalDate.parse("2026-10-01") to hour),
            ListeningProtocol.monthlyRows(days, 12, now, utc))
    }

    @Test fun recentKeepsLastDays() {
        val today = day("2026-11-02")
        val snapshot = ListeningProtocol.Snapshot(mapOf(today - 500 to 1L, today - 10 to 2L))
        assertEquals(mapOf(today - 10 to 2L), ListeningProtocol.recent(snapshot, today).days)
    }

    @Test fun formats() {
        assertEquals("0min", ListeningProtocol.format(0))
        assertEquals("45min", ListeningProtocol.format(45 * 60_000L))
        assertEquals("1h 05min", ListeningProtocol.format(65 * 60_000L))
        assertEquals("2h 05min", ListeningProtocol.format(125 * 60_000L))
    }
}
