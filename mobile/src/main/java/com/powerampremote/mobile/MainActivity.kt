package com.powerampremote.mobile

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powerampremote.mobile.communication.CompanionStatus
import com.powerampremote.mobile.communication.ReceiptStatus
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.powerampremote.mobile.poweramp.PowerampContract
import com.powerampremote.mobile.poweramp.PowerampRepository
import com.powerampremote.mobile.communication.PlayerProtocol
import java.text.DateFormat
import java.util.Date
import com.powerampremote.mobile.communication.NowPlayingProtocol

internal val AppBackground = Color(0xFF080B10)
internal val AppSurface = Color(0xFF141B25)
internal val AppAccent = Color(0xFF72B7FF)
internal val AppSecondary = Color(0xFFB3BDCD)

/** Ponto de entrada do aplicativo no celular. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PowerampRepository.read(applicationContext)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = AppAccent, onPrimary = Color(0xFF061A30),
                background = AppBackground, surface = AppSurface, onSurface = Color(0xFFF5F7FB))) {
                IdentificationScreen()
            }
        }
    }
}

/** Exibe o último evento de teste recebido neste processo do Companion. */
@Composable
private fun IdentificationScreen() {
    val context = LocalContext.current
    val player by PowerampRepository.snapshot.collectAsStateWithLifecycle()
    val metadata by PowerampRepository.nowPlaying.collectAsStateWithLifecycle()
    var openFailed by remember { mutableStateOf(false) }
    val receipt by CompanionStatus.status.collectAsStateWithLifecycle()
    val statusText = when (receipt) {
        ReceiptStatus.WAITING -> R.string.receipt_waiting
        ReceiptStatus.RECEIVED -> R.string.receipt_received
        ReceiptStatus.ACK_SENT -> R.string.receipt_ack_sent
        ReceiptStatus.ACK_FAILED -> R.string.receipt_ack_failed
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.screen_description), color = AppAccent,
            style = MaterialTheme.typography.labelLarge)
        Text(stringResource(R.string.screen_title), style = MaterialTheme.typography.headlineMedium,
            color = Color.White)
        Text(stringResource(R.string.home_intro), color = AppSecondary, style = MaterialTheme.typography.bodyMedium)
        val stateText = when (player.state) {
            PlayerProtocol.State.UNKNOWN -> R.string.player_unknown
            PlayerProtocol.State.PLAYING -> R.string.player_playing
            PlayerProtocol.State.PAUSED -> R.string.player_paused
            PlayerProtocol.State.STOPPED -> R.string.player_stopped
        }
        CompanionSection {
            Text(stringResource(R.string.home_player_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(stateText), color = AppAccent, style = MaterialTheme.typography.labelLarge)
            if (NowPlayingProtocol.matchesTrack(metadata, player.trackId, player.trackReportedAt)) {
                Text(metadata.title.ifBlank { stringResource(R.string.metadata_title_unknown) },
                    style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(metadata.artist.ifBlank { stringResource(R.string.metadata_artist_unknown) }, color = AppSecondary)
                Text(stringResource(R.string.metadata_album,
                    metadata.album.ifBlank { stringResource(R.string.metadata_album_unknown) }), color = AppSecondary)
                Text(stringResource(R.string.metadata_position, NowPlayingProtocol.formatTime(metadata.positionMs),
                    NowPlayingProtocol.formatTime(metadata.durationMs)), color = AppAccent)
            } else Text(stringResource(R.string.metadata_waiting), color = AppSecondary)
            if (player.reportedAt > 0) {
                Text(stringResource(R.string.player_report_time,
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(player.reportedAt))),
                    color = AppSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = {
                try {
                    val launch = context.packageManager.getLaunchIntentForPackage(PowerampContract.PACKAGE)
                    if (launch == null) openFailed = true else { context.startActivity(launch); openFailed = false }
                } catch (_: RuntimeException) { openFailed = true }
            }) { Text(stringResource(R.string.player_open)) }
            if (openFailed) Text(stringResource(R.string.player_open_failed), color = MaterialTheme.colorScheme.error)
        }
        CompanionSection { LibraryAccessPanel() }
        CompanionSection {
            Text(stringResource(R.string.home_diagnostics_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.home_watch_test), color = AppSecondary, style = MaterialTheme.typography.labelLarge)
            Text(stringResource(statusText), color = AppAccent)
            Text(stringResource(R.string.player_setup), color = AppSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun CompanionSection(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = AppSurface) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content)
    }
}
