package com.powerampremote.mobile.poweramp

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.powerampremote.mobile.communication.EqualizerProtocol as Protocol
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Receiver temporário, sem serviço permanente e sem inferir estado a partir do comando. */
class EqualizerController(private val context: Context) {
    companion object {
        private const val CHANGED = "com.maxmpz.audioplayer.EQU_CHANGED"
        private val lock = Any()
    }
    private val preferences = context.getSharedPreferences("equalizer_events_v6", Context.MODE_PRIVATE)
    private fun read(): Protocol.Snapshot = synchronized(lock) {
        val state = Protocol.State.entries.firstOrNull { it.name == preferences.getString("state", null) }
            ?: Protocol.State.UNKNOWN
        Protocol.Snapshot(state, preferences.getLong("at", 0)).takeIf(Protocol::valid) ?: Protocol.Snapshot()
    }

    fun execute(request: Protocol.Request): Protocol.Result {
        val latch = CountDownLatch(1)
        var registered = false
        // Leitura é de cache; o evento EQU_CHANGED não é sticky no Poweramp v3.
        val issuedAt = AtomicLong(Long.MAX_VALUE)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != CHANGED) return
                try {
                    @Suppress("DEPRECATION")
                    val enabled = intent.extras?.get("equ") as? Boolean
                    val at = intent.getLongExtra("ts", 0)
                    val now = System.currentTimeMillis()
                    val snapshot = synchronized(lock) {
                        val previous = read()
                        val next = Protocol.accept(previous, enabled, at, now)
                        if (next != previous) preferences.edit().putString("state", next.state.name)
                            .putLong("at", next.reportedAt).apply()
                        next
                    }
                    if (enabled != null && at > 0 && at <= now && snapshot.reportedAt == at &&
                        Protocol.observed(request, snapshot, issuedAt.get())) latch.countDown()
                } catch (_: RuntimeException) { /* Evento inválido não confirma um comando. */ }
            }
        }
        try {
            @Suppress("DEPRECATION")
            try { context.packageManager.getPackageInfo(PowerampContract.PACKAGE, 0) }
            catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                return Protocol.Result(request, Protocol.Stage.UNAVAILABLE, Protocol.Snapshot())
            }
            if (request.action == Protocol.Action.READ) return Protocol.Result(request, Protocol.Stage.READ, read())
            ContextCompat.registerReceiver(context, receiver, IntentFilter(CHANGED), ContextCompat.RECEIVER_EXPORTED)
            registered = true
            issuedAt.set(System.currentTimeMillis())
            context.sendBroadcast(Intent(PowerampContract.COMMAND)
                .setComponent(ComponentName(PowerampContract.PACKAGE, PowerampContract.RECEIVER))
                .putExtra("cmd", 53).putExtra("equ", request.action == Protocol.Action.ENABLE))
            // Não envia tone nem valores de bandas/preset.
            val received = latch.await(2, TimeUnit.SECONDS)
            val snapshot = read()
            val stage = if (received && Protocol.observed(request, snapshot, issuedAt.get()))
                Protocol.Stage.OBSERVED else Protocol.Stage.REQUESTED
            return Protocol.Result(request, stage, snapshot)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return Protocol.Result(request, Protocol.Stage.ERROR, read())
        } catch (_: RuntimeException) {
            return Protocol.Result(request, Protocol.Stage.ERROR, read())
        } finally { if (registered) context.unregisterReceiver(receiver) }
    }
}
