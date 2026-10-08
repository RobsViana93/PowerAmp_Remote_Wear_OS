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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme

/** Ponto de entrada do aplicativo no celular. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                IdentificationScreen()
            }
        }
    }
}

/** Exibe o último evento de teste recebido neste processo do Companion. */
@Composable
private fun IdentificationScreen() {
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
            .background(Color.Black)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = stringResource(R.string.screen_title),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.screen_description),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(statusText),
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF80CBC4),
            textAlign = TextAlign.Center
        )
    }
}
