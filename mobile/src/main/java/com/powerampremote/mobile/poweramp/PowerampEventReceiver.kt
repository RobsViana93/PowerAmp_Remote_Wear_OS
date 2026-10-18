package com.powerampremote.mobile.poweramp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Eventos explícitos permitem observar o Poweramp com o Companion sem tela. */
class PowerampEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val accepted = try { PowerampRepository.receive(context, intent) } catch (_: RuntimeException) { false }
        if (!accepted) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(4_000) { PowerampRepository.publish(context.applicationContext) }
            } catch (_: Exception) {
                // O estado foi salvo; a próxima solicitação também devolve esse informe.
            } finally {
                pending.finish()
            }
        }
    }
}
