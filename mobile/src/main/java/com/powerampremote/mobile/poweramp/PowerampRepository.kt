package com.powerampremote.mobile.poweramp

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.powerampremote.mobile.communication.PlayerProtocol
import com.powerampremote.mobile.communication.PlayerProtocol.Snapshot
import com.powerampremote.mobile.communication.PlayerProtocol.State
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/** Últimos eventos informados pelo player. UNKNOWN não significa pausado. */
object PowerampRepository {
    private val monitor = Object()
    private var restored = false
    private val mutableSnapshot = MutableStateFlow(Snapshot())
    val snapshot = mutableSnapshot.asStateFlow()

    fun read(context: Context): Snapshot = synchronized(monitor) {
        if (!restored) {
            val encoded = context.getSharedPreferences("poweramp_state", Context.MODE_PRIVATE)
                .getString("snapshot", null)
            mutableSnapshot.value = encoded?.let { PlayerProtocol.decodeSnapshot(it.toByteArray()) } ?: Snapshot()
            restored = true
        }
        mutableSnapshot.value
    }

    /** Chamado apenas para os dois eventos explícitos documentados pelo Poweramp. */
    fun receive(context: Context, intent: Intent): Boolean = synchronized(monitor) {
        if (intent.action !in setOf(PowerampContract.STATUS, PowerampContract.TRACK)) return false
        val at = intent.getLongExtra("ts", 0)
        if (at <= 0 || at > System.currentTimeMillis() + 1_000) return false
        val before = read(context)
        val after = when (intent.action) {
            PowerampContract.STATUS -> {
                if (at < before.reportedAt) return false
                val state = when (intent.getIntExtra("state", -1)) {
                    0 -> State.STOPPED
                    1 -> State.PLAYING
                    2 -> State.PAUSED
                    -1 -> State.UNKNOWN
                    else -> return false
                }
                before.copy(state = state, reportedAt = at)
            }
            else -> {
                if (at < before.trackReportedAt) return false
                @Suppress("DEPRECATION")
                val track = intent.getBundleExtra("track")
                val id = if (intent.hasExtra("id")) intent.getLongExtra("id", -1)
                    else track?.getLong("id", -1) ?: -1
                if (id < 0) return false
                before.copy(trackId = id, trackReportedAt = at)
            }
        }
        mutableSnapshot.value = after
        context.getSharedPreferences("poweramp_state", Context.MODE_PRIVATE).edit()
            .putString("snapshot", PlayerProtocol.encodeSnapshot(after).toString(Charsets.US_ASCII)).apply()
        monitor.notifyAll()
        true
    }

    /** Espera limitada numa thread de fundo. Não bloqueia a Activity. */
    fun awaitEvidence(context: Context, command: PlayerProtocol.Command, before: Snapshot, issuedAt: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        synchronized(monitor) {
            while (!PlayerProtocol.hasEvidence(command, before, read(context), issuedAt)) {
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0) return false
                monitor.wait(remaining)
            }
            return true
        }
    }

    suspend fun publish(context: Context) {
        val request = PutDataMapRequest.create(PlayerProtocol.STATE_PATH)
        request.dataMap.putByteArray("snapshot", PlayerProtocol.encodeSnapshot(read(context)))
        Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent()).await()
    }
}
