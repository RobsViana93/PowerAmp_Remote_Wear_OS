package com.powerampremote.mobile.poweramp

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import com.powerampremote.mobile.communication.ListeningProtocol
import com.powerampremote.mobile.communication.PlayerProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.util.Locale

/** Soma o tempo tocando por dia; cada evento aceito fecha o trecho anterior e abre outro se estiver tocando. */
object ListeningStats {
    private const val PREFS = "listening_stats"
    private val mutableState = MutableStateFlow(ListeningProtocol.Snapshot())
    val state: StateFlow<ListeningProtocol.Snapshot> = mutableState.asStateFlow()
    private var loaded = false

    fun onEvent(context: Context, state: PlayerProtocol.State, at: Long) {
        synchronized(this) {
            val current = load(context)
            var days = current.days
            val since = current.playingSince
            if (since > 0 && at >= since) {
                days = ListeningProtocol.addSegment(days, since, since + minOf(at - since, ListeningProtocol.MAX_SEGMENT_MS))
            }
            val next = ListeningProtocol.Snapshot(days, if (state == PlayerProtocol.State.PLAYING) at else 0L)
            prefs(context).edit()
                .putString("history", ListeningProtocol.encodeDays(next.days))
                .putLong("since", next.playingSince)
                .putInt("since_boot", bootCount(context))
                .apply()
            mutableState.value = next
        }
    }

    fun snapshot(context: Context): ListeningProtocol.Snapshot = synchronized(this) { load(context) }

    fun exportCsv(context: Context): String {
        val days = snapshot(context).days
        return buildString {
            append("data,minutos\n")
            for ((day, ms) in days.entries.sortedBy { it.key }) {
                append(LocalDate.ofEpochDay(day)).append(',')
                    .append(String.format(Locale.US, "%.1f", ms / 60_000.0)).append('\n')
            }
        }
    }

    /** Chamado sob o lock. Carrega uma vez; trecho aberto de outro boot é descartado. */
    private fun load(context: Context): ListeningProtocol.Snapshot {
        if (!loaded) {
            val prefs = prefs(context)
            val days = readHistory(prefs)
            val since = if (bootCount(context) == prefs.getInt("since_boot", -2)) prefs.getLong("since", 0L) else 0L
            mutableState.value = ListeningProtocol.Snapshot(days, since)
            loaded = true
        }
        return mutableState.value
    }

    /** Migra do formato antigo (total/day/today) quando não há "history". */
    private fun readHistory(prefs: SharedPreferences): Map<Long, Long> {
        prefs.getString("history", null)?.let { return ListeningProtocol.decodeDays(it) ?: emptyMap() }
        val day = prefs.getLong("day", -1L)
        val today = prefs.getLong("today", 0L)
        val days = if (day >= 0 && today > 0) mapOf(day to today) else emptyMap()
        prefs.edit()
            .putString("history", ListeningProtocol.encodeDays(days))
            .remove("total").remove("day").remove("today")
            .apply()
        return days
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun bootCount(context: Context): Int =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
}
