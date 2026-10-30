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
import androidx.compose.ui.res.stringArrayResource
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
import com.powerampremote.wear.communication.ControlsProtocol
import java.text.DateFormat
import java.util.Date

private val Background = Color(0xFF080B10)
private val Surface = Color(0xFF1D2938)
private val Accent = Color(0xFF72B7FF)
private val Secondary = Color(0xFFB3BDCD)

/** Seleções explícitas; o texto dos modos descreve o último evento, não o botão tocado. */
@Composable
internal fun AdditionalControlsScreen(state: CommunicationState, onAction: (ControlsProtocol.Action) -> Unit,
                                     onBack: () -> Unit) {
    val snapshot = state.controls
    val enabled = state.connection == Connection.AVAILABLE && state.controlsAvailable && !state.busy
    Column(Modifier.fillMaxSize().background(Background).verticalScroll(rememberScrollState())
        .padding(horizontal = 28.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onBack) { Text(stringResource(R.string.visual_back)) }
        Text(stringResource(R.string.controls_title), color = Color.White, textAlign = TextAlign.Center)
        if (!state.controlsAvailable) Text(stringResource(R.string.controls_update_companion), color = Color.White,
            textAlign = TextAlign.Center)
        val shuffle = stringArrayResource(R.array.controls_shuffle_modes).getOrNull(snapshot.shuffle)
            ?: stringResource(R.string.controls_unknown)
        val repeat = stringArrayResource(R.array.controls_repeat_modes).getOrNull(snapshot.repeat)
            ?: stringResource(R.string.controls_unknown)
        SectionTitle(R.string.controls_shuffle_title)
        StateCard(stringResource(R.string.controls_shuffle_state, shuffle))
        ActionButton(R.string.controls_shuffle_off, ControlsProtocol.Action.SHUFFLE_OFF, enabled, onAction,
            selected = snapshot.modesAt > 0 && snapshot.shuffle == 0)
        ActionButton(R.string.controls_shuffle_songs, ControlsProtocol.Action.SHUFFLE_SONGS, enabled, onAction,
            selected = snapshot.modesAt > 0 && snapshot.shuffle == 1)
        SectionTitle(R.string.controls_repeat_title)
        StateCard(stringResource(R.string.controls_repeat_state, repeat))
        ActionButton(R.string.controls_repeat_off, ControlsProtocol.Action.REPEAT_OFF, enabled, onAction,
            selected = snapshot.modesAt > 0 && snapshot.repeat == 0)
        ActionButton(R.string.controls_repeat_category, ControlsProtocol.Action.REPEAT_CATEGORY, enabled, onAction,
            selected = snapshot.modesAt > 0 && snapshot.repeat == 1)
        ActionButton(R.string.controls_repeat_song, ControlsProtocol.Action.REPEAT_SONG, enabled, onAction,
            selected = snapshot.modesAt > 0 && snapshot.repeat == 3)
        Text(if (snapshot.modesAt > 0) stringResource(R.string.controls_modes_time,
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(snapshot.modesAt)))
            else stringResource(R.string.controls_modes_unknown), color = Secondary,
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        SectionTitle(R.string.controls_media_title)
        StateCard(if (snapshot.volume >= 0) stringResource(R.string.controls_volume_value, snapshot.volume, snapshot.maximum)
            else stringResource(R.string.controls_unknown))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ActionButton(R.string.controls_volume_down, ControlsProtocol.Action.MEDIA_DOWN,
                enabled && !snapshot.fixed && snapshot.volume > snapshot.minimum, onAction)
            ActionButton(R.string.controls_volume_up, ControlsProtocol.Action.MEDIA_UP,
                enabled && !snapshot.fixed && snapshot.volume >= 0 && snapshot.volume < snapshot.maximum, onAction)
        }
        Text(stringResource(if (snapshot.fixed) R.string.controls_volume_fixed else R.string.controls_volume_scope),
            color = Secondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        val notice = when {
            state.busy -> R.string.visual_busy
            state.controlsFailed -> R.string.controls_failed
            state.controlsStage == ControlsProtocol.Stage.CONFIRMED -> R.string.controls_confirmed
            state.controlsStage == ControlsProtocol.Stage.REQUESTED -> R.string.controls_requested
            state.controlsStage == ControlsProtocol.Stage.UNAVAILABLE -> R.string.controls_unavailable
            state.controlsStage == ControlsProtocol.Stage.ERROR -> R.string.controls_failed
            else -> R.string.controls_hint
        }
        Text(stringResource(notice), color = Color.White, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        ActionButton(R.string.controls_read, ControlsProtocol.Action.QUERY, enabled, onAction)
        Button(onClick = onBack) { Text(stringResource(R.string.visual_back)) }
    }
}

@Composable
private fun ActionButton(label: Int, action: ControlsProtocol.Action, enabled: Boolean,
                         onAction: (ControlsProtocol.Action) -> Unit, selected: Boolean = false) {
    Button(onClick = { onAction(action) }, enabled = enabled,
        modifier = Modifier.semantics { if (selected) this.selected = true },
        colors = ButtonDefaults.buttonColors(containerColor = if (selected) Accent else Surface,
            contentColor = if (selected) Color(0xFF061A30) else Color.White)) {
        Text(stringResource(label), textAlign = TextAlign.Center)
    }
}

@Composable
private fun SectionTitle(label: Int) {
    Text(stringResource(label), modifier = Modifier.padding(top = 8.dp), color = Color.White,
        style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
}

@Composable
private fun StateCard(value: String) {
    Text(value, modifier = Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(14.dp)).padding(10.dp),
        color = Accent, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
}
