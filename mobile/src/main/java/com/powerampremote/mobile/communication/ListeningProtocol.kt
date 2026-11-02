package com.powerampremote.mobile.communication

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

/** Tempo de escuta calculado no celular e exibido no relógio. */
object ListeningProtocol {
    const val DATA_KEY = "listening_v2"
    const val MAX_SEGMENT_MS = 6L * 60 * 60 * 1000
    private const val MAX_BYTES = 32 * 1024

    /** days: dia local (epochDay) -> ms ouvidos; playingSince: início do trecho aberto ou 0. */
    data class Snapshot(val days: Map<Long, Long> = emptyMap(), val playingSince: Long = 0)

    fun encode(snapshot: Snapshot): ByteArray =
        "v2|${snapshot.playingSince}|${encodeDays(snapshot.days)}".toByteArray(Charsets.US_ASCII)

    fun decode(data: ByteArray): Snapshot? {
        if (data.isEmpty() || data.size > MAX_BYTES || data.any { it.toInt() !in 32..126 }) return null
        val parts = data.toString(Charsets.US_ASCII).split('|')
        if (parts.size != 3 || parts[0] != "v2") return null
        val since = parts[1].toLongOrNull()?.takeIf { it >= 0 } ?: return null
        val days = decodeDays(parts[2]) ?: return null
        return Snapshot(days, since)
    }

    /** Formato "d:ms;d:ms" ordenado por dia. */
    fun encodeDays(days: Map<Long, Long>): String =
        days.entries.sortedBy { it.key }.joinToString(";") { "${it.key}:${it.value}" }

    /** Null se algum item for inválido; texto vazio é mapa vazio. */
    fun decodeDays(text: String): Map<Long, Long>? {
        if (text.isEmpty()) return emptyMap()
        val days = LinkedHashMap<Long, Long>()
        for (item in text.split(';')) {
            val parts = item.split(':')
            if (parts.size != 2) return null
            val day = parts[0].toLongOrNull()?.takeIf { it >= 0 } ?: return null
            val ms = parts[1].toLongOrNull()?.takeIf { it >= 0 } ?: return null
            days[day] = ms
        }
        return days
    }

    /** Mantém só os dias recentes para o relógio. */
    fun recent(snapshot: Snapshot, today: Long, keep: Int = 400): Snapshot {
        val oldest = today - keep + 1
        return snapshot.copy(days = snapshot.days.filterKeys { it >= oldest })
    }

    /** Soma [start, end) dividindo nas meias-noites locais. */
    fun addSegment(days: Map<Long, Long>, start: Long, end: Long, zone: ZoneId = ZoneId.systemDefault()): Map<Long, Long> {
        if (end <= start) return days
        val result = days.toMutableMap()
        var cursor = start
        while (cursor < end) {
            val day = epochDay(cursor, zone)
            val nextMidnight = startOfDayMs(day + 1, zone)
            val part = minOf(end, nextMidnight) - cursor
            if (part <= 0) break
            result[day] = (result[day] ?: 0L) + part
            cursor += part
        }
        return result
    }

    /** Histórico com o trecho que ainda está tocando (limitado a MAX_SEGMENT_MS). */
    fun withOpen(snapshot: Snapshot, now: Long, zone: ZoneId = ZoneId.systemDefault()): Map<Long, Long> {
        if (snapshot.playingSince <= 0 || now <= snapshot.playingSince) return snapshot.days
        val end = snapshot.playingSince + minOf(now - snapshot.playingSince, MAX_SEGMENT_MS)
        return addSegment(snapshot.days, snapshot.playingSince, end, zone)
    }

    fun today(days: Map<Long, Long>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        days[epochDay(now, zone)] ?: 0L

    /** Semana de domingo a sábado que contém hoje. */
    fun week(days: Map<Long, Long>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val start = weekStart(localDate(now, zone))
        return sumBetween(days, start, start.plusWeeks(1))
    }

    /** Mês corrente; o histórico guardado não é apagado. */
    fun month(days: Map<Long, Long>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val start = localDate(now, zone).withDayOfMonth(1)
        return sumBetween(days, start, start.plusMonths(1))
    }

    /** Últimos [count] dias, do mais recente; começa no primeiro registro se ele for mais recente. */
    fun dailyRows(days: Map<Long, Long>, count: Int, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Pair<LocalDate, Long>> {
        val today = localDate(now, zone)
        val oldest = maxOf(firstDay(days, today), today.minusDays(count - 1L))
        return generateSequence(today) { it.minusDays(1) }
            .takeWhile { !it.isBefore(oldest) }
            .map { it to sumBetween(days, it, it.plusDays(1)) }
            .toList()
    }

    /** Semanas (chave = domingo) das mais recentes para as mais antigas. */
    fun weeklyRows(days: Map<Long, Long>, count: Int, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Pair<LocalDate, Long>> {
        val today = localDate(now, zone)
        val thisWeek = weekStart(today)
        val oldest = maxOf(weekStart(firstDay(days, today)), thisWeek.minusWeeks(count - 1L))
        return generateSequence(thisWeek) { it.minusWeeks(1) }
            .takeWhile { !it.isBefore(oldest) }
            .map { it to sumBetween(days, it, it.plusWeeks(1)) }
            .toList()
    }

    /** Meses (chave = dia 1) do mais recente para o mais antigo. */
    fun monthlyRows(days: Map<Long, Long>, count: Int, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Pair<LocalDate, Long>> {
        val today = localDate(now, zone)
        val thisMonth = today.withDayOfMonth(1)
        val oldest = maxOf(firstDay(days, today).withDayOfMonth(1), thisMonth.minusMonths(count - 1L))
        return generateSequence(thisMonth) { it.minusMonths(1) }
            .takeWhile { !it.isBefore(oldest) }
            .map { it to sumBetween(days, it, it.plusMonths(1)) }
            .toList()
    }

    fun epochDay(ms: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        localDate(ms, zone).toEpochDay()

    fun startOfDayMs(epochDay: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli()

    fun format(ms: Long): String {
        val minutes = ms.coerceAtLeast(0) / 60_000
        val hours = minutes / 60
        return if (hours == 0L) "${minutes}min" else "${hours}h ${(minutes % 60).toString().padStart(2, '0')}min"
    }

    private fun localDate(ms: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    private fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

    /** Primeiro dia registrado, limitado a hoje (ou hoje, se não há registro). */
    private fun firstDay(days: Map<Long, Long>, today: LocalDate): LocalDate =
        days.keys.minOrNull()?.let { LocalDate.ofEpochDay(it).coerceAtMost(today) } ?: today

    private fun sumBetween(days: Map<Long, Long>, from: LocalDate, until: LocalDate): Long {
        val first = from.toEpochDay()
        val last = until.toEpochDay()
        return days.entries.filter { it.key in first until last }.sumOf { it.value }
    }
}
