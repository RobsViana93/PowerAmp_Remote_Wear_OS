package com.powerampremote.wear.communication

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Tempo de escuta calculado no celular e exibido no relógio. */
object ListeningProtocol {
    const val DATA_KEY = "listening_v1"
    const val MAX_SEGMENT_MS = 6L * 60 * 60 * 1000
    data class Snapshot(val totalMs: Long = 0, val day: Long = -1, val todayMs: Long = 0, val playingSince: Long = 0)

    fun encode(snapshot: Snapshot): ByteArray =
        "v1|${snapshot.totalMs}|${snapshot.day}|${snapshot.todayMs}|${snapshot.playingSince}".toByteArray(Charsets.US_ASCII)

    fun decode(data: ByteArray): Snapshot? {
        if (data.isEmpty() || data.size > 128 || data.any { it.toInt() !in 32..126 }) return null
        val parts = data.toString(Charsets.US_ASCII).split('|')
        if (parts.size != 5 || parts[0] != "v1") return null
        val total = parts[1].toLongOrNull()?.takeIf { it >= 0 } ?: return null
        val day = parts[2].toLongOrNull()?.takeIf { it >= -1 } ?: return null
        val today = parts[3].toLongOrNull()?.takeIf { it >= 0 } ?: return null
        val since = parts[4].toLongOrNull()?.takeIf { it >= 0 } ?: return null
        return Snapshot(total, day, today, since)
    }

    /** Retorna (hoje, total) incluindo o trecho que ainda está tocando. */
    fun live(snapshot: Snapshot, now: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val open = if (snapshot.playingSince > 0) (now - snapshot.playingSince).coerceIn(0L, MAX_SEGMENT_MS) else 0L
        val todayDay = epochDay(now, zone)
        val openToday = if (open > 0) (now - maxOf(snapshot.playingSince, startOfDayMs(todayDay, zone))).coerceIn(0L, open) else 0L
        val today = (if (snapshot.day == todayDay) snapshot.todayMs else 0L) + openToday
        return Pair(today, snapshot.totalMs + open)
    }

    fun epochDay(ms: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay()

    fun startOfDayMs(epochDay: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli()

    fun format(ms: Long): String {
        val minutes = ms.coerceAtLeast(0) / 60_000
        val hours = minutes / 60
        return if (hours == 0L) "${minutes}min" else "${hours}h ${(minutes % 60).toString().padStart(2, '0')}min"
    }
}
