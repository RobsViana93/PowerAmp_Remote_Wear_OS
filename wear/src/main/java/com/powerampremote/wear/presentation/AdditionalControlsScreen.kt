package com.powerampremote.wear.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.powerampremote.wear.R
import com.powerampremote.wear.communication.CommunicationState
import com.powerampremote.wear.communication.Connection
import com.powerampremote.wear.communication.ControlsProtocol
import java.text.DateFormat
import java.util.Date

/** Seleções explícitas; o texto dos modos descreve o último evento, não o botão tocado. */
@Composable
internal fun AdditionalControlsScreen(state: CommunicationState, onAction: (ControlsProtocol.Action) -> Unit,
                                     onBack: () -> Unit) {
    val snapshot = state.controls
    val enabled = state.connection == Connection.AVAILABLE && state.controlsAvailable && !state.busy
    Column(Modifier.fillMaxSize().background(Color.Black).verticalScroll(rememberScrollState())
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
        Text(stringResource(R.string.controls_shuffle_state, shuffle), color = Color(0xFF80CBC4), textAlign = TextAlign.Center)
        ActionButton(R.string.controls_shuffle_off, ControlsProtocol.Action.SHUFFLE_OFF, enabled, onAction)
        ActionButton(R.string.controls_shuffle_songs, ControlsProtocol.Action.SHUFFLE_SONGS, enabled, onAction)
        Text(stringResource(R.string.controls_repeat_state, repeat), color = Color(0xFF80CBC4), textAlign = TextAlign.Center)
        ActionButton(R.string.controls_repeat_off, ControlsProtocol.Action.REPEAT_OFF, enabled, onAction)
        ActionButton(R.string.controls_repeat_category, ControlsProtocol.Action.REPEAT_CATEGORY, enabled, onAction)
        ActionButton(R.string.controls_repeat_song, ControlsProtocol.Action.REPEAT_SONG, enabled, onAction)
        Text(if (snapshot.modesAt > 0) stringResource(R.string.controls_modes_time,
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(snapshot.modesAt)))
            else stringResource(R.string.controls_modes_unknown), color = Color.White,
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Text(stringResource(R.string.controls_media_title), color = Color.White, textAlign = TextAlign.Center)
        Text(if (snapshot.volume >= 0) stringResource(R.string.controls_volume_value, snapshot.volume, snapshot.maximum)
            else stringResource(R.string.controls_unknown), color = Color(0xFF80CBC4), textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ActionButton(R.string.controls_volume_down, ControlsProtocol.Action.MEDIA_DOWN,
                enabled && !snapshot.fixed && snapshot.volume > snapshot.minimum, onAction)
            ActionButton(R.string.controls_volume_up, ControlsProtocol.Action.MEDIA_UP,
                enabled && !snapshot.fixed && snapshot.volume >= 0 && snapshot.volume < snapshot.maximum, onAction)
        }
        Text(stringResource(if (snapshot.fixed) R.string.controls_volume_fixed else R.string.controls_volume_scope),
            color = Color.White, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
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
                         onAction: (ControlsProtocol.Action) -> Unit) {
    Button(onClick = { onAction(action) }, enabled = enabled) { Text(stringResource(label)) }
}
