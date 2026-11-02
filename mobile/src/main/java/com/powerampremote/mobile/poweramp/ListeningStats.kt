package com.powerampremote.mobile.poweramp

import android.content.Context
import android.provider.Settings
import com.powerampremote.mobile.communication.ListeningProtocol
import com.powerampremote.mobile.communication.PlayerProtocol

/** Soma o tempo tocando; cada evento aceito fecha o trecho anterior e abre outro se estiver tocando. */
object ListeningStats {
    private const val PREFS = "listening_stats"

    fun onEvent(context: Context, state: PlayerProtocol.State, at: Long) {
        synchronized(this) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val boot = bootCount(context)
            var total = prefs.getLong("total", 0L)
            var day = prefs.getLong("day", -1L)
            var today = prefs.getLong("today", 0L)
            val since = prefs.getLong("since", 0L)
            // Trecho de outro boot, ou com relógio recuado, não é confiável.
            if (since > 0 && at >= since && boot == prefs.getInt("since_boot", -2)) {
                val duration = minOf(at - since, ListeningProtocol.MAX_SEGMENT_MS)
                total += duration
                val atDay = ListeningProtocol.epochDay(at)
                if (day != atDay) { day = atDay; today = 0L }
                val end = since + duration
                today += (end - maxOf(since, ListeningProtocol.startOfDayMs(atDay))).coerceIn(0L, duration)
            }
            prefs.edit()
                .putLong("total", total)
                .putLong("day", day)
                .putLong("today", today)
                .putLong("since", if (state == PlayerProtocol.State.PLAYING) at else 0L)
                .putInt("since_boot", boot)
                .apply()
        }
    }

    fun snapshot(context: Context): ListeningProtocol.Snapshot {
        synchronized(this) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val day = ListeningProtocol.epochDay(System.currentTimeMillis())
            val openValid = bootCount(context) == prefs.getInt("since_boot", -2)
            return ListeningProtocol.Snapshot(
                totalMs = prefs.getLong("total", 0L),
                day = day,
                todayMs = if (prefs.getLong("day", -1L) == day) prefs.getLong("today", 0L) else 0L,
                playingSince = if (openValid) prefs.getLong("since", 0L) else 0L)
        }
    }

    private fun bootCount(context: Context): Int =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
}
