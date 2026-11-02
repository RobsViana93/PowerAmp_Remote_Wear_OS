package com.powerampremote.mobile

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powerampremote.mobile.communication.ListeningProtocol
import com.powerampremote.mobile.poweramp.ListeningStats
import kotlinx.coroutines.delay
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class ListeningRange(val label: Int) {
    DAY(R.string.listening_range_day), WEEK(R.string.listening_range_week), MONTH(R.string.listening_range_month)
}

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
private val DAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy (EEE)", PT_BR)
private val SHORT_DAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM", PT_BR)
private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy", PT_BR)

/** Tempo ouvindo no celular: resumo e histórico por dia, semana ou mês, com exportação CSV. */
@Composable
internal fun ListeningPanel() {
    val context = LocalContext.current
    val stored by ListeningStats.state.collectAsStateWithLifecycle()
    val playing = stored.playingSince > 0
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var range by remember { mutableStateOf(ListeningRange.DAY) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            val bytes = ListeningStats.exportCsv(context).toByteArray(Charsets.UTF_8)
            val ok = try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
            } catch (_: IOException) { false }
            val text = if (ok) R.string.listening_export_ok else R.string.listening_export_failed
            Toast.makeText(context, context.getString(text), Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(Unit) { ListeningStats.snapshot(context) }
    LaunchedEffect(playing, stored) {
        now = System.currentTimeMillis()
        while (playing) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val days = remember(stored, now) { ListeningProtocol.withOpen(stored, now) }
    val rows = remember(range, days, now) {
        when (range) {
            ListeningRange.DAY -> ListeningProtocol.dailyRows(days, 31, now).map { (day, ms) ->
                day.format(DAY_FORMAT) to ms
            }
            ListeningRange.WEEK -> ListeningProtocol.weeklyRows(days, 12, now).map { (start, ms) ->
                "${start.format(SHORT_DAY_FORMAT)} – ${start.plusDays(6).format(SHORT_DAY_FORMAT)}" to ms
            }
            ListeningRange.MONTH -> ListeningProtocol.monthlyRows(days, 12, now).map { (start, ms) ->
                start.format(MONTH_FORMAT) to ms
            }
        }
    }

    Text(stringResource(R.string.listening_title), style = MaterialTheme.typography.titleLarge)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ListeningMetric(stringResource(R.string.listening_today), ListeningProtocol.format(ListeningProtocol.today(days, now)))
        ListeningMetric(stringResource(R.string.listening_week), ListeningProtocol.format(ListeningProtocol.week(days, now)))
        ListeningMetric(stringResource(R.string.listening_month), ListeningProtocol.format(ListeningProtocol.month(days, now)))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ListeningRange.entries.forEach { option ->
            FilterChip(selected = range == option, onClick = { range = option },
                label = { Text(stringResource(option.label)) })
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { (label, ms) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = AppSecondary, style = MaterialTheme.typography.bodyMedium)
                Text(ListeningProtocol.format(ms), color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    Button(onClick = { exportLauncher.launch("tempo_ouvindo.csv") }) {
        Text(stringResource(R.string.listening_export))
    }
}

@Composable
private fun ListeningMetric(label: String, value: String) {
    Column {
        Text(label, color = AppSecondary, style = MaterialTheme.typography.labelMedium)
        Text(value, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}
