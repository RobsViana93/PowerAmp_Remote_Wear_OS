package com.powerampremote.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.powerampremote.wear.communication.CommunicationState
import com.powerampremote.wear.communication.CommunicationViewModel
import com.powerampremote.wear.communication.Connection
import com.powerampremote.wear.communication.TestPhase
import com.powerampremote.wear.communication.PlayerProtocol
import com.powerampremote.wear.communication.PlayerNotice
import java.text.DateFormat
import java.util.Date
import androidx.compose.ui.text.style.TextOverflow
import com.powerampremote.wear.communication.NowPlayingProtocol
import com.powerampremote.wear.communication.SyncNotice
import com.powerampremote.wear.presentation.NowPlayingScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

/** A Activity apresenta a tela; o ViewModel mantém o estado da comunicação. */
class MainActivity : ComponentActivity() {
    private lateinit var communication: CommunicationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        communication = ViewModelProvider(this)[CommunicationViewModel::class.java]
        setContent {
            val state by communication.state.collectAsStateWithLifecycle()
            MaterialTheme {
                NowPlayingScreen(state, communication::sendPlayerCommand, communication::refreshNowPlaying,
                    communication::seekTo, communication::sendAdditionalControl, communication::sendEqualizerAction,
                    communication::libraryAction) {
                    CommunicationScreen(state, communication::sendTest, communication::refreshNowPlaying,
                        communication::sendPlayerCommand)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) { communication.runForegroundUpdates() }
        }
    }

    override fun onStart() {
        super.onStart()
        communication.refreshConnection()
    }

    override fun onStop() {
        // Rotação mantém o ViewModel e o teste; sair da tela cancela a espera.
        if (!isChangingConfigurations) communication.cancelTest()
        super.onStop()
    }
}

@Composable
private fun CommunicationScreen(state: CommunicationState, onTest: () -> Unit, onRefresh: () -> Unit,
                                onCommand: (PlayerProtocol.Command) -> Unit) {
    val connectionText = when (state.connection) {
        Connection.CHECKING -> R.string.connection_checking
        Connection.AVAILABLE -> R.string.connection_available
        Connection.UNAVAILABLE -> R.string.connection_unavailable
        Connection.ERROR -> R.string.connection_error
    }
    val phaseText = when (state.phase) {
        TestPhase.IDLE -> R.string.test_idle
        TestPhase.SENDING -> R.string.test_sending
        TestPhase.WAITING_ACK -> R.string.test_waiting_ack
        TestPhase.CONFIRMED -> R.string.test_confirmed
        TestPhase.NO_PHONE -> R.string.test_no_phone
        TestPhase.FAILED -> R.string.test_failed
        TestPhase.TIMEOUT -> R.string.test_timeout
        TestPhase.CANCELLED -> R.string.test_cancelled
    }
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black)
            .verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        Text(stringResource(R.string.screen_title), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium, color = Color.White)
        Text(stringResource(connectionText), textAlign = TextAlign.Center,
            color = if (state.connection == Connection.AVAILABLE) Color(0xFF80CBC4) else Color.White)
        NowPlayingInformation(state)
        PlayerControls(state, onCommand)
        Button(onClick = onRefresh, enabled = !state.busy && state.connection != Connection.CHECKING) {
            Text(stringResource(R.string.refresh_connection))
        }
        Button(onClick = onTest, enabled = !state.busy && state.connection == Connection.AVAILABLE) {
            Text(stringResource(R.string.send_test))
        }
        Text(stringResource(phaseText), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall, color = Color.White)
    }
}

@Composable
private fun NowPlayingInformation(state: CommunicationState) {
    val metadata = state.nowPlaying
    val currentTrack = state.playerSnapshot
    if (NowPlayingProtocol.matchesTrack(metadata, currentTrack.trackId, currentTrack.trackReportedAt)) {
        Text(metadata.title.ifBlank { stringResource(R.string.metadata_title_unknown) },
            color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall,
            maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(metadata.artist.ifBlank { stringResource(R.string.metadata_artist_unknown) },
            color = Color.White, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(stringResource(R.string.metadata_album,
            metadata.album.ifBlank { stringResource(R.string.metadata_album_unknown) }),
            color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(stringResource(R.string.metadata_position, NowPlayingProtocol.formatTime(metadata.positionMs),
            NowPlayingProtocol.formatTime(metadata.durationMs)), color = Color(0xFF80CBC4),
            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
    } else {
        Text(stringResource(R.string.metadata_waiting), color = Color.White,
            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
    }
    val syncText = when (state.syncStage) {
        NowPlayingProtocol.SyncStage.SYNCED -> R.string.metadata_sync_received
        NowPlayingProtocol.SyncStage.NO_POSITION -> R.string.metadata_sync_unavailable
        NowPlayingProtocol.SyncStage.NOT_INSTALLED -> R.string.player_missing
        NowPlayingProtocol.SyncStage.ERROR -> R.string.metadata_sync_failed
        null -> when (state.syncNotice) {
            SyncNotice.IDLE -> R.string.metadata_sync_hint
            SyncNotice.LOADING -> R.string.metadata_sync_loading
            SyncNotice.UPDATE_COMPANION -> R.string.metadata_update_companion
            SyncNotice.FAILED -> R.string.metadata_sync_failed
        }
    }
    Text(stringResource(syncText), color = Color.White, textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun PlayerControls(state: CommunicationState, onCommand: (PlayerProtocol.Command) -> Unit) {
    val snapshot = state.playerSnapshot
    val playerState = when (snapshot.state) {
        PlayerProtocol.State.UNKNOWN -> R.string.player_unknown
        PlayerProtocol.State.PLAYING -> R.string.player_playing
        PlayerProtocol.State.PAUSED -> R.string.player_paused
        PlayerProtocol.State.STOPPED -> R.string.player_stopped
    }
    Text(stringResource(playerState), color = Color.White, textAlign = TextAlign.Center)
    if (snapshot.reportedAt > 0) {
        Text(stringResource(R.string.player_report_time,
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(snapshot.reportedAt))),
            color = Color.White, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
    val resultText = when (state.playerStage) {
        PlayerProtocol.Stage.RECEIVED -> R.string.player_received
        PlayerProtocol.Stage.REQUESTED -> R.string.player_requested
        PlayerProtocol.Stage.OBSERVED -> if (state.playerCommand in setOf(PlayerProtocol.Command.NEXT,
                PlayerProtocol.Command.PREVIOUS)) R.string.player_track_observed else R.string.player_state_observed
        PlayerProtocol.Stage.NO_UPDATE -> R.string.player_no_update
        PlayerProtocol.Stage.NOT_INSTALLED -> R.string.player_missing
        PlayerProtocol.Stage.UNSUPPORTED -> R.string.player_unsupported
        PlayerProtocol.Stage.ERROR -> R.string.player_failed
        null -> when (state.playerNotice) {
            PlayerNotice.IDLE -> R.string.player_idle
            PlayerNotice.SENDING -> R.string.player_sending
            PlayerNotice.WAITING -> R.string.player_waiting
            PlayerNotice.TIMEOUT -> R.string.player_timeout
            PlayerNotice.FAILED -> R.string.player_failed
            PlayerNotice.CANCELLED -> R.string.player_cancelled
            PlayerNotice.INCOMPATIBLE -> R.string.player_update_companion
        }
    }
    Text(stringResource(resultText), color = Color(0xFF80CBC4), textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodySmall)
    for (command in PlayerProtocol.Command.entries) {
        val label = when (command) {
            PlayerProtocol.Command.PLAY -> R.string.player_play
            PlayerProtocol.Command.PAUSE -> R.string.player_pause
            PlayerProtocol.Command.NEXT -> R.string.player_next
            PlayerProtocol.Command.PREVIOUS -> R.string.player_previous
        }
        Button(onClick = { onCommand(command) }, enabled = !state.busy &&
                state.connection == Connection.AVAILABLE && state.playerAvailable) {
            Text(stringResource(label))
        }
    }
    if (state.connection == Connection.AVAILABLE && !state.playerAvailable) {
        Text(stringResource(R.string.player_update_companion), color = Color.White, textAlign = TextAlign.Center)
    }
}
