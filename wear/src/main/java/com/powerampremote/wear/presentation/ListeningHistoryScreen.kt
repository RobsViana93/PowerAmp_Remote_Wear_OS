package com.powerampremote.wear.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.powerampremote.wear.R
import com.powerampremote.wear.communication.CommunicationState
import com.powerampremote.wear.communication.ListeningProtocol
import com.powerampremote.wear.communication.PlayerProtocol
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class HistoryRange(val label: Int, val count: Int) {
    DAY(R.string.listening_range_day, 14), WEEK(R.string.listening_range_week, 8), MONTH(R.string.listening_range_month, 6)
}

private val HistoryBackground = Color(0xFF080B10)
private val HistorySurface = Color(0xFF1D2938)
private val HistorySecondary = Color(0xFFB3BDCD)
private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
private val DAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM EEE", PT_BR)
private val SHORT_DAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM", PT_BR)
private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMM yyyy", PT_BR)

/** Histórico de escuta vindo do celular: dia, semana ou mês. */
@Composable
internal fun ListeningHistoryScreen(state: CommunicationState, onBack: () -> Unit) {
    var range by remember { mutableStateOf(HistoryRange.DAY) }
    Column(Modifier.fillMaxSize().background(HistoryBackground).verticalScroll(rememberScrollState())
        .padding(horizontal = 28.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CompactActionButton(stringResource(R.string.visual_back), "‹", onClick = onBack)
        Text(stringResource(R.string.listening_history), color = Color.White, textAlign = TextAlign.Center)
        val listening = state.listening
        if (listening == null) {
            Text(stringResource(R.string.listening_waiting), color = HistorySecondary,
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        } else {
            val now = System.currentTimeMillis()
            val playing = state.playerSnapshot.state == PlayerProtocol.State.PLAYING
            val days = ListeningProtocol.withOpen(if (playing) listening else listening.copy(playingSince = 0), now)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HistoryRange.entries.forEach { option ->
                    CompactActionButton(stringResource(option.label), null, modifier = Modifier.weight(1f),
                        selected = range == option, onClick = { range = option })
                }
            }
            val rows = when (range) {
                HistoryRange.DAY -> ListeningProtocol.dailyRows(days, HistoryRange.DAY.count, now).map { (day, ms) ->
                    day.format(DAY_FORMAT) to ms
                }
                HistoryRange.WEEK -> ListeningProtocol.weeklyRows(days, HistoryRange.WEEK.count, now).map { (start, ms) ->
                    "${start.format(SHORT_DAY_FORMAT)}–${start.plusDays(6).format(SHORT_DAY_FORMAT)}" to ms
                }
                HistoryRange.MONTH -> ListeningProtocol.monthlyRows(days, HistoryRange.MONTH.count, now).map { (start, ms) ->
                    start.format(MONTH_FORMAT) to ms
                }
            }
            rows.forEach { (label, ms) ->
                Row(Modifier.fillMaxWidth().background(HistorySurface, RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, color = HistorySecondary, style = MaterialTheme.typography.bodySmall)
                    Text(ListeningProtocol.format(ms), color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        CompactActionButton(stringResource(R.string.visual_back), "‹", onClick = onBack)
    }
}
