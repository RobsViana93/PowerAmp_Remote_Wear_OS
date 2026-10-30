package com.powerampremote.mobile

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.powerampremote.mobile.communication.LibraryProtocol
import com.powerampremote.mobile.poweramp.PowerampLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A autorização é iniciada no celular e comprovada por uma consulta, não pelo retorno da Activity. */
@Composable
internal fun LibraryAccessPanel() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var refresh by remember { mutableIntStateOf(0) }
    var stage by remember { mutableStateOf<LibraryProtocol.Stage?>(null) }
    var launchFailed by remember { mutableStateOf(false) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(refresh) {
        stage = null
        stage = withContext(Dispatchers.IO) { PowerampLibrary(context.applicationContext).probe() }
    }
    Text(stringResource(R.string.library_title), color = Color.White, style = MaterialTheme.typography.titleLarge)
    val status = when {
        launchFailed -> R.string.library_open_failed
        stage == null -> R.string.library_checking
        stage == LibraryProtocol.Stage.PAGE -> R.string.library_allowed
        stage == LibraryProtocol.Stage.DENIED -> R.string.library_denied
        stage == LibraryProtocol.Stage.UNAVAILABLE -> R.string.library_unavailable
        else -> R.string.library_failed
    }
    Text(stringResource(status), color = when {
        launchFailed || stage == LibraryProtocol.Stage.ERROR -> MaterialTheme.colorScheme.error
        stage == LibraryProtocol.Stage.DENIED || stage == LibraryProtocol.Stage.UNAVAILABLE -> Color(0xFFFFD28C)
        else -> AppAccent
    }, style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.library_setup), color = AppSecondary, style = MaterialTheme.typography.bodyMedium)
    Button(onClick = {
        try { context.startActivity(PowerampLibrary.permissionIntent(context)); launchFailed = false }
        catch (_: RuntimeException) { launchFailed = true }
    }) { Text(stringResource(R.string.library_authorize)) }
    OutlinedButton(onClick = { refresh++ }, enabled = stage != null) { Text(stringResource(R.string.library_check)) }
}
