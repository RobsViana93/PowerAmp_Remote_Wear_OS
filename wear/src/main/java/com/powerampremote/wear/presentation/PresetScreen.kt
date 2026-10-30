package com.powerampremote.wear.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.powerampremote.wear.R
import com.powerampremote.wear.communication.*
import kotlinx.coroutines.launch

private val Background = Color(0xFF080B10)
private val Surface = Color(0xFF1D2938)
private val Accent = Color(0xFF72B7FF)
private val Secondary = Color(0xFFB3BDCD)

@Composable
internal fun PresetScreen(state: CommunicationState, onAction: (PresetIntent) -> Unit, onBack: () -> Unit) {
    val presets = state.presets
    val enabled = state.connection == Connection.AVAILABLE && state.presetsAvailable && !state.busy
    val list = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(presets.page?.request?.id, presets.selected, presets.stage, presets.failed) {
        list.scrollToItem(0)
        focus.requestFocus()
    }
    val message = when {
        state.busy -> R.string.visual_busy
        state.connection != Connection.AVAILABLE -> R.string.visual_disconnected
        !state.presetsAvailable -> R.string.presets_update
        presets.failed -> R.string.presets_failed
        presets.stage == PresetProtocol.Stage.DENIED -> R.string.presets_denied
        presets.stage == PresetProtocol.Stage.NOT_FOUND -> R.string.presets_missing
        presets.stage == PresetProtocol.Stage.UNAVAILABLE -> R.string.presets_unavailable
        presets.stage == PresetProtocol.Stage.ERROR -> R.string.presets_failed
        presets.stage == PresetProtocol.Stage.REQUESTED -> R.string.presets_requested
        presets.stage == PresetProtocol.Stage.OBSERVED -> R.string.presets_observed
        presets.page?.items?.isEmpty() == true -> R.string.presets_empty
        else -> R.string.presets_hint
    }
    LazyColumn(state = list, modifier = Modifier.fillMaxSize().background(Background)
        .onRotaryScrollEvent { event -> scope.launch { list.scrollBy(event.verticalScrollPixels) }; true }
        .focusRequester(focus).focusable(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text(stringResource(R.string.presets_title), color = Color.White, style = MaterialTheme.typography.titleSmall) }
        item { Text(stringResource(message), color = Accent, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall) }
        presets.selected?.let { selected ->
            item {
                Text(stringResource(R.string.presets_selected, selected.name), color = Color.White,
                    modifier = Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(14.dp)).padding(10.dp),
                    textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            item { Text(stringResource(R.string.presets_scope), color = Secondary,
                textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall) }
            item { Button(onClick = { onAction(PresetIntent.Apply) }, enabled = enabled,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF061A30))) {
                Text(stringResource(R.string.presets_apply))
            } }
        }
        presets.lastRequested?.let { last ->
            item { Text(stringResource(R.string.presets_last_request, last.name), color = Secondary,
                textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall) }
        }
        item { Text(stringResource(R.string.presets_active_unknown), color = Secondary,
            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall) }
        for (preset in presets.page?.items.orEmpty()) {
            item(key = preset.id) {
                val chosen = presets.selected?.id == preset.id
                Button(onClick = { onAction(PresetIntent.Select(preset)) }, enabled = enabled,
                    modifier = Modifier.fillMaxWidth().semantics { if (chosen) selected = true },
                    colors = ButtonDefaults.buttonColors(containerColor = if (chosen) Accent else Surface,
                        contentColor = if (chosen) Color(0xFF061A30) else Color.White)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(preset.name, maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        Text(stringResource(if (preset.parametric) R.string.presets_parametric else R.string.presets_graphic),
                            color = if (!enabled) Color(0xFF7D8794) else if (chosen) Color(0xFF173F68) else Secondary,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (presets.canGoPrevious) item { Button(onClick = { onAction(PresetIntent.Previous) }, enabled = enabled) {
            Text(stringResource(R.string.presets_previous))
        } }
        if (presets.canGoNext) item { Button(onClick = { onAction(PresetIntent.Next) }, enabled = enabled) {
            Text(stringResource(R.string.presets_next))
        } }
        item { Button(onClick = { onAction(PresetIntent.Refresh) }, enabled = enabled) {
            Text(stringResource(R.string.presets_refresh))
        } }
        item { Button(onClick = onBack) { Text(stringResource(R.string.visual_back)) } }
    }
}
