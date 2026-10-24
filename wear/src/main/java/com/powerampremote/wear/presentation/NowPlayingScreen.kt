package com.powerampremote.wear.presentation

import androidx.compose.foundation.Canvas
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.MaterialTheme
import com.powerampremote.wear.R
import com.powerampremote.wear.communication.*

private val Accent = Color(0xFF80CBC4)

/** A tela principal reúne os controles; os detalhes técnicos ficam em outra página. */
@Composable
internal fun NowPlayingScreen(state: CommunicationState, onCommand: (PlayerProtocol.Command) -> Unit,
                              onRefresh: () -> Unit, onSeek: (Int, Long, Long) -> Unit,
                              onAdditionalControl: (ControlsProtocol.Action) -> Unit,
                              onEqualizer: (EqualizerProtocol.Action) -> Unit,
                              onLibrary: (LibraryIntent) -> Unit,
                              onPresets: (PresetIntent) -> Unit,
                              onPlayerVisibility: (Boolean) -> Unit,
                              details: @Composable () -> Unit) {
    var showDetails by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    var showPresets by remember { mutableStateOf(false) }
    var seeking by remember(state.playerSnapshot.trackId, state.playerSnapshot.trackReportedAt) { mutableStateOf(false) }
    SideEffect { onPlayerVisibility(!showDetails && !showControls && !showEqualizer && !showLibrary && !showPresets && !seeking) }
    DisposableEffect(Unit) { onDispose { onPlayerVisibility(false) } }
    BackHandler(enabled = showDetails || showControls || showEqualizer || showLibrary || showPresets || seeking) {
        when { showPresets -> { showPresets = false; onPresets(PresetIntent.Close) }
            showDetails -> showDetails = false; showControls -> showControls = false
            showLibrary -> if (state.library.album != null) onLibrary(LibraryIntent.Albums)
                else { showLibrary = false; onLibrary(LibraryIntent.Close) }
            showEqualizer -> showEqualizer = false; else -> seeking = false }
    }
    if (showPresets) {
        PresetScreen(state, onPresets) { showPresets = false; onPresets(PresetIntent.Close) }
        return
    }
    if (showLibrary) {
        LibraryScreen(state, onLibrary,
            onClose = { if (state.library.album != null) onLibrary(LibraryIntent.Albums)
                else { showLibrary = false; onLibrary(LibraryIntent.Close) } },
            onPlayer = { showLibrary = false; onLibrary(LibraryIntent.Close) })
        return
    }
    if (showEqualizer) {
        EqualizerScreen(state, onEqualizer, onPresets = { showPresets = true; onPresets(PresetIntent.Open) }) {
            showEqualizer = false
        }
        return
    }
    if (showControls) {
        AdditionalControlsScreen(state, onAdditionalControl) { showControls = false }
        return
    }
    if (showDetails) {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Button(onClick = { showDetails = false }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp)) {
                Text(stringResource(R.string.visual_back))
            }
            Box(Modifier.weight(1f)) { details() }
        }
        return
    }
    val metadata = state.nowPlaying
    val player = state.playerSnapshot
    val matches = NowPlayingProtocol.matchesTrack(metadata, player.trackId, player.trackReportedAt)
    val position = if (matches) state.estimatedPositionMs ?: metadata.positionMs else -1
    val duration = if (matches) metadata.durationMs else -1
    val connected = state.connection == Connection.AVAILABLE
    val enabled = connected && state.playerAvailable && !state.busy
    Column(Modifier.fillMaxSize().background(Color.Black).verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(6.dp).background(if (connected) Accent else Color(0xFFFFB4AB), CircleShape))
            Text(stringResource(if (connected) R.string.visual_connected else R.string.visual_disconnected),
                style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, lineHeight = 12.sp,
                color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        val cover = state.artwork
        if (cover != null && matches) {
            Image(cover.asImageBitmap(), stringResource(R.string.visual_cover),
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.size(36.dp).background(Color(0xFF182623), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center) {
                Text("♪", color = Accent, style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { contentDescription = "Sem capa disponível" })
            }
        }
        Text(if (matches) metadata.title.ifBlank { stringResource(R.string.metadata_title_unknown) }
            else stringResource(R.string.metadata_title_unknown), color = Color.White,
            style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center)
        Text(if (matches) metadata.artist.ifBlank { stringResource(R.string.metadata_artist_unknown) }
            else stringResource(R.string.metadata_artist_unknown), color = Accent,
            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val progressDescription = stringResource(if (state.estimatedPositionMs != null) R.string.controls_estimated_description
            else R.string.metadata_position, NowPlayingProtocol.formatTime(position),
            NowPlayingProtocol.formatTime(duration))
        Canvas(Modifier.fillMaxWidth().height(6.dp).semantics { contentDescription = progressDescription }) {
            drawLine(Color(0xFF384542), start = androidx.compose.ui.geometry.Offset(0f, size.height / 2),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2), strokeWidth = size.height, cap = StrokeCap.Round)
            val fraction = VisualProtocol.fraction(position, duration)
            if (fraction > 0f) drawLine(Accent, start = androidx.compose.ui.geometry.Offset(0f, size.height / 2),
                end = androidx.compose.ui.geometry.Offset(size.width * fraction, size.height / 2),
                strokeWidth = size.height, cap = StrokeCap.Round)
        }
        Text(stringResource(if (state.estimatedPositionMs != null) R.string.controls_estimated_time else R.string.controls_reported_time,
            NowPlayingProtocol.formatTime(position), NowPlayingProtocol.formatTime(duration)),
            color = Color.White, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, lineHeight = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            TransportButton(PlayerProtocol.Command.PREVIOUS, enabled, onCommand)
            TransportButton(if (player.state == PlayerProtocol.State.PLAYING) PlayerProtocol.Command.PAUSE
                else PlayerProtocol.Command.PLAY, enabled, onCommand)
            TransportButton(PlayerProtocol.Command.NEXT, enabled, onCommand)
        }
        val playback = when (player.state) {
            PlayerProtocol.State.PLAYING -> R.string.player_playing
            PlayerProtocol.State.PAUSED -> R.string.player_paused
            PlayerProtocol.State.STOPPED -> R.string.player_stopped
            PlayerProtocol.State.UNKNOWN -> R.string.player_unknown
        }
        Text(stringResource(if (state.busy) R.string.visual_busy else playback), color = Color.White,
            style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        if (state.playerStage in setOf(PlayerProtocol.Stage.ERROR, PlayerProtocol.Stage.NO_UPDATE,
                PlayerProtocol.Stage.NOT_INSTALLED, PlayerProtocol.Stage.UNSUPPORTED) ||
            state.playerNotice in setOf(PlayerNotice.FAILED, PlayerNotice.TIMEOUT, PlayerNotice.INCOMPATIBLE, PlayerNotice.NO_PHONE)) {
            Text(stringResource(R.string.visual_command_problem), color = Color(0xFFFFB4AB),
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
        if (seeking) {
            SeekEditor(position, duration, enabled && state.visualAvailable,
                onCancel = { seeking = false }, onConfirm = { seconds ->
                    seeking = false
                    onSeek(seconds, player.trackId, player.trackReportedAt)
                })
        } else {
            Button(onClick = { seeking = true }, enabled = enabled && state.visualAvailable && matches &&
                player.trackId > 0 && duration >= 1000) { Text(stringResource(R.string.visual_seek)) }
        }
        if (state.seekFailed || state.seekStage != null) {
            val result = when {
                state.seekFailed -> R.string.visual_seek_failed
                state.seekStage == VisualProtocol.Stage.CONFIRMED -> R.string.visual_seek_confirmed
                state.seekStage == VisualProtocol.Stage.REQUESTED -> R.string.visual_seek_requested
                state.seekStage == VisualProtocol.Stage.STALE_TRACK -> R.string.visual_seek_stale
                else -> R.string.visual_seek_failed
            }
            Text(stringResource(result), color = Accent, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
        Button(onClick = onRefresh, enabled = !state.busy) { Text(stringResource(R.string.refresh_connection)) }
        val refreshProblem = when {
            state.syncNotice == SyncNotice.NO_PHONE -> R.string.visual_disconnected
            state.syncNotice == SyncNotice.UPDATE_COMPANION -> R.string.metadata_update_companion
            state.syncNotice == SyncNotice.FAILED || state.syncStage == NowPlayingProtocol.SyncStage.ERROR -> R.string.metadata_sync_failed
            state.syncStage == NowPlayingProtocol.SyncStage.NOT_INSTALLED -> R.string.player_missing
            else -> null
        }
        if (refreshProblem != null) Text(stringResource(refreshProblem), color = Color(0xFFFFB4AB),
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Text(stringResource(when {
            state.autoUpdatesFailed -> R.string.controls_auto_failed
            state.estimatedPositionMs != null -> R.string.controls_position_estimated
            state.autoUpdatesActive -> R.string.controls_auto_active
            else -> R.string.visual_reported_position
        }), color = Color.White,
            style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        Text(if (matches) metadata.album.ifBlank { stringResource(R.string.metadata_album_unknown) }
            else stringResource(R.string.metadata_album_unknown), color = Color.White,
            style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Button(onClick = { showControls = true; onAdditionalControl(ControlsProtocol.Action.QUERY) }) {
            Text(stringResource(R.string.controls_title))
        }
        Button(onClick = { showLibrary = true; onLibrary(LibraryIntent.Open) }, enabled = !state.busy) {
            Text(stringResource(R.string.library_title))
        }
        Button(onClick = { showEqualizer = true; onEqualizer(EqualizerProtocol.Action.READ) }) {
            Text(stringResource(R.string.equalizer_title))
        }
        Button(onClick = { showDetails = true }) { Text(stringResource(R.string.visual_details)) }
    }
}

@Composable
private fun TransportButton(command: PlayerProtocol.Command, enabled: Boolean,
                            onCommand: (PlayerProtocol.Command) -> Unit) {
    val label = stringResource(when (command) {
        PlayerProtocol.Command.PLAY -> R.string.player_play
        PlayerProtocol.Command.PAUSE -> R.string.player_pause
        PlayerProtocol.Command.NEXT -> R.string.player_next
        PlayerProtocol.Command.PREVIOUS -> R.string.player_previous
    })
    Button(onClick = { onCommand(command) }, enabled = enabled,
        modifier = Modifier.size(48.dp).semantics { contentDescription = label }) {
        Canvas(Modifier.size(20.dp)) {
            val w = size.width
            val h = size.height
            val ink = Color(0xFF172B28)
            if (command == PlayerProtocol.Command.PAUSE) {
                drawRect(ink, androidx.compose.ui.geometry.Offset(w * .2f, h * .1f),
                    androidx.compose.ui.geometry.Size(w * .2f, h * .8f))
                drawRect(ink, androidx.compose.ui.geometry.Offset(w * .6f, h * .1f),
                    androidx.compose.ui.geometry.Size(w * .2f, h * .8f))
            } else {
                val previous = command == PlayerProtocol.Command.PREVIOUS
                val path = Path().apply {
                    moveTo(if (previous) w * .8f else w * .2f, h * .1f)
                    lineTo(if (previous) w * .2f else w * .8f, h * .5f)
                    lineTo(if (previous) w * .8f else w * .2f, h * .9f)
                    close()
                }
                drawPath(path, ink)
                if (command != PlayerProtocol.Command.PLAY) drawLine(ink,
                    androidx.compose.ui.geometry.Offset(if (previous) w * .1f else w * .9f, h * .1f),
                    androidx.compose.ui.geometry.Offset(if (previous) w * .1f else w * .9f, h * .9f), strokeWidth = w * .1f)
            }
        }
    }
}

@Composable
private fun SeekEditor(position: Long, duration: Long, enabled: Boolean, onCancel: () -> Unit,
                       onConfirm: (Int) -> Unit) {
    val maximum = ((duration / 1000) - 1).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    var target by remember { mutableIntStateOf((position / 1000).coerceIn(0, maximum.toLong()).toInt()) }
    Text(stringResource(R.string.visual_seek_target, NowPlayingProtocol.formatTime(target.toLong() * 1000)),
        color = Accent, textAlign = TextAlign.Center)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(onClick = { target = (target - 10).coerceAtLeast(0) }, enabled = enabled && target > 0) { Text("−10s") }
        Button(onClick = { target = (target.toLong() + 10).coerceAtMost(maximum.toLong()).toInt() },
            enabled = enabled && target < maximum) { Text("+10s") }
    }
    Button(onClick = { onConfirm(target) }, enabled = enabled) { Text(stringResource(R.string.visual_seek_confirm)) }
    Button(onClick = onCancel) { Text(stringResource(R.string.visual_cancel)) }
}
