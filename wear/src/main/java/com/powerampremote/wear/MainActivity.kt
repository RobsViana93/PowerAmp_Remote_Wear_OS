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

/** A Activity apresenta a tela; o ViewModel mantém o estado da comunicação. */
class MainActivity : ComponentActivity() {
    private lateinit var communication: CommunicationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        communication = ViewModelProvider(this)[CommunicationViewModel::class.java]
        setContent {
            val state by communication.state.collectAsStateWithLifecycle()
            MaterialTheme {
                CommunicationScreen(state, communication::sendTest, communication::refreshConnection)
            }
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
private fun CommunicationScreen(state: CommunicationState, onTest: () -> Unit, onRefresh: () -> Unit) {
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
        Button(onClick = onTest, enabled = !state.busy && state.connection == Connection.AVAILABLE) {
            Text(stringResource(R.string.send_test))
        }
        Text(stringResource(phaseText), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall, color = Color.White)
        Button(onClick = onRefresh, enabled = !state.busy && state.connection != Connection.CHECKING) {
            Text(stringResource(R.string.refresh_connection))
        }
    }
}
