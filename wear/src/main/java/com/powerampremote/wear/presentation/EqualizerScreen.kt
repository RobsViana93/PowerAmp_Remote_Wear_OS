package com.powerampremote.wear.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.powerampremote.wear.R
import com.powerampremote.wear.communication.CommunicationState
import com.powerampremote.wear.communication.Connection
import com.powerampremote.wear.communication.EqualizerProtocol
import java.text.DateFormat
import java.util.Date

private val Background = Color(0xFF080B10)
private val Surface = Color(0xFF1D2938)
private val Accent = Color(0xFF72B7FF)
private val Secondary = Color(0xFFB3BDCD)

@Composable
internal fun EqualizerScreen(state: CommunicationState, onAction: (EqualizerProtocol.Action) -> Unit,
                             onPresets: () -> Unit,
                             onBack: () -> Unit) {
    val enabled = state.connection == Connection.AVAILABLE && state.equalizerAvailable && !state.busy
    Column(Modifier.fillMaxSize().background(Background).verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.equalizer_title), color = Color.White, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleSmall)
        val label = when (state.equalizer.state) {
            EqualizerProtocol.State.ON -> R.string.equalizer_on
            EqualizerProtocol.State.OFF -> R.string.equalizer_off
            EqualizerProtocol.State.UNKNOWN -> R.string.equalizer_unknown
        }
        Text(stringResource(label), modifier = Modifier.fillMaxWidth()
            .background(Surface, RoundedCornerShape(14.dp)).padding(10.dp),
            color = Accent, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall)
        val isOn = state.equalizer.state == EqualizerProtocol.State.ON
        val isOff = state.equalizer.state == EqualizerProtocol.State.OFF
        Button(onClick = { onAction(EqualizerProtocol.Action.ENABLE) }, enabled = enabled,
            modifier = Modifier.semantics { if (isOn) selected = true },
            colors = ButtonDefaults.buttonColors(containerColor = if (isOn) Accent else Surface,
                contentColor = if (isOn) Color(0xFF061A30) else Color.White)) {
            Text(stringResource(R.string.equalizer_enable))
        }
        Button(onClick = { onAction(EqualizerProtocol.Action.DISABLE) }, enabled = enabled,
            modifier = Modifier.semantics { if (isOff) selected = true },
            colors = ButtonDefaults.buttonColors(containerColor = if (isOff) Accent else Surface,
                contentColor = if (isOff) Color(0xFF061A30) else Color.White)) {
            Text(stringResource(R.string.equalizer_disable))
        }
        val notice = when {
            state.busy -> R.string.visual_busy
            state.connection != Connection.AVAILABLE -> R.string.visual_disconnected
            !state.equalizerAvailable -> R.string.controls_update_companion
            state.equalizerFailed -> R.string.equalizer_failed
            state.equalizerStage == EqualizerProtocol.Stage.OBSERVED -> R.string.equalizer_observed
            state.equalizerStage == EqualizerProtocol.Stage.REQUESTED -> R.string.equalizer_requested
            state.equalizerStage == EqualizerProtocol.Stage.UNAVAILABLE -> R.string.equalizer_unavailable
            state.equalizerStage == EqualizerProtocol.Stage.ERROR -> R.string.equalizer_failed
            else -> R.string.equalizer_read_hint
        }
        Text(stringResource(notice), color = Color.White, style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center)
        if (state.equalizer.reportedAt > 0) Text(stringResource(R.string.equalizer_report_time,
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(state.equalizer.reportedAt))),
            color = Secondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Text(stringResource(R.string.equalizer_scope), color = Secondary,
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Button(onClick = { onAction(EqualizerProtocol.Action.READ) }, enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = Surface, contentColor = Color.White)) {
            Text(stringResource(R.string.equalizer_read))
        }
        Button(onClick = onPresets, enabled = !state.busy,
            colors = ButtonDefaults.buttonColors(containerColor = Surface, contentColor = Color.White)) {
            Text(stringResource(R.string.presets_open))
        }
        Button(onClick = onBack) { Text(stringResource(R.string.visual_back)) }
    }
}
