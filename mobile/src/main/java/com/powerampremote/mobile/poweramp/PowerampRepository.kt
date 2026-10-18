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
import com.powerampremote.mobile.communication.NowPlayingProtocol
import java.util.Base64

/** Últimos eventos informados pelo player. UNKNOWN não significa pausado. */
object PowerampRepository {
    private val monitor = Object()
    private var restored = false
    private val mutableSnapshot = MutableStateFlow(Snapshot())
    val snapshot = mutableSnapshot.asStateFlow()
    private val mutableNowPlaying = MutableStateFlow(NowPlayingProtocol.Snapshot())
    val nowPlaying = mutableNowPlaying.asStateFlow()

    fun read(context: Context): Snapshot = synchronized(monitor) {
        if (!restored) {
            val encoded = context.getSharedPreferences("poweramp_state", Context.MODE_PRIVATE)
                .getString("snapshot", null)
            mutableSnapshot.value = encoded?.let { PlayerProtocol.decodeSnapshot(it.toByteArray()) } ?: Snapshot()
            val metadata = context.getSharedPreferences("poweramp_state", Context.MODE_PRIVATE)
                .getString("now_playing_v3", null)
            mutableNowPlaying.value = try {
                metadata?.let { NowPlayingProtocol.decode(Base64.getDecoder().decode(it)) }
                    ?: NowPlayingProtocol.Snapshot()
            } catch (_: IllegalArgumentException) { NowPlayingProtocol.Snapshot() }
            restored = true
        }
        mutableSnapshot.value
    }

    fun readNowPlaying(context: Context): NowPlayingProtocol.Snapshot = synchronized(monitor) {
        read(context)
        mutableNowPlaying.value
    }

    /** Chamado apenas para os dois eventos explícitos documentados pelo Poweramp. */
    fun receive(context: Context, intent: Intent): Boolean = synchronized(monitor) {
        if (intent.action !in setOf(PowerampContract.STATUS, PowerampContract.TRACK)) return false
        val at = intent.getLongExtra("ts", 0)
        if (at <= 0 || at > System.currentTimeMillis() + 1_000) return false
        val before = read(context)
        val metadataBefore = mutableNowPlaying.value
        val metadataAfter = NowPlayingProtocol.merge(metadataBefore, PowerampMetadataParser.parse(intent, at))
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
                // Builds recentes também incluem a faixa no evento de estado.
                val metadataId = metadataAfter.trackId
                val normalizedId = if (metadataId > 0) metadataId else -1
                if (metadataAfter.metadataAt == at && normalizedId != before.trackId) {
                    before.copy(state = state, reportedAt = at, trackId = normalizedId, trackReportedAt = at)
                } else before.copy(state = state, reportedAt = at)
            }
            else -> {
                if (at < before.trackReportedAt) return false
                @Suppress("DEPRECATION")
                val track = intent.getBundleExtra("track")
                val id = if (intent.hasExtra("id")) intent.getLongExtra("id", -1)
                    else track?.getLong("id", -1) ?: -1
                before.copy(trackId = if (id > 0) id else -1, trackReportedAt = at)
            }
        }
        if (before == after && metadataBefore == metadataAfter) return false
        mutableSnapshot.value = after
        mutableNowPlaying.value = metadataAfter
        context.getSharedPreferences("poweramp_state", Context.MODE_PRIVATE).edit()
            .putString("snapshot", PlayerProtocol.encodeSnapshot(after).toString(Charsets.US_ASCII)).apply()
        saveNowPlaying(context)
        monitor.notifyAll()
        true
    }

    private fun saveNowPlaying(context: Context) {
        context.getSharedPreferences("poweramp_state", Context.MODE_PRIVATE).edit()
            .putString("now_playing_v3", Base64.getEncoder().encodeToString(
                NowPlayingProtocol.encode(mutableNowPlaying.value))).apply()
    }

    /** POS_SYNC não identifica a faixa: rejeita a resposta se a referência mudou na espera. */
    fun receivePosition(context: Context, seconds: Int, expected: NowPlayingProtocol.Snapshot): Boolean = synchronized(monitor) {
        val current = readNowPlaying(context)
        if (seconds < 0 || current.trackId != expected.trackId || current.metadataAt != expected.metadataAt) return false
        val at = System.currentTimeMillis()
        if (at < maxOf(current.metadataAt, current.positionAt)) return false
        mutableNowPlaying.value = current.copy(positionMs = seconds.toLong() * 1000, positionAt = at)
        saveNowPlaying(context)
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
        // Estado e metadados são capturados sob o mesmo monitor: uma versão coerente.
        synchronized(monitor) {
            request.dataMap.putByteArray("snapshot", PlayerProtocol.encodeSnapshot(read(context)))
            request.dataMap.putByteArray(NowPlayingProtocol.DATA_KEY,
                NowPlayingProtocol.encode(mutableNowPlaying.value))
        }
        Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent()).await()
    }
}
