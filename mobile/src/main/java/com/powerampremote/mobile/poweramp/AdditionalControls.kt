package com.powerampremote.mobile.poweramp

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.powerampremote.mobile.communication.ControlsProtocol as Protocol
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Consulta temporária dos modos e ajustes explícitos; nenhum serviço de áudio próprio. */
class AdditionalControls(private val context: Context) {
    companion object { private const val MODE_CHANGED = "com.maxmpz.audioplayer.PLAYING_MODE_CHANGED" }
    private val lock = Any()
    private var modes = Protocol.Snapshot()

    fun execute(request: Protocol.Request): Protocol.Result {
        val latch = CountDownLatch(1)
        val issuedAt = System.currentTimeMillis()
        val target = when (request.action) {
            Protocol.Action.SHUFFLE_OFF -> "shuffle" to 0
            Protocol.Action.SHUFFLE_SONGS -> "shuffle" to 2
            Protocol.Action.REPEAT_OFF -> "repeat" to 0
            Protocol.Action.REPEAT_CATEGORY -> "repeat" to 1
            Protocol.Action.REPEAT_SONG -> "repeat" to 3
            else -> null
        }
        fun accept(intent: Intent) {
            if (intent.action != MODE_CHANGED) return
            val at = intent.getLongExtra("ts", 0)
            if (at <= 0 || at > System.currentTimeMillis() + 1000) return
            val shuffle = intent.getIntExtra("shuffle", -1)
            val repeat = intent.getIntExtra("repeat", -1)
            if (shuffle !in 0..4 || repeat !in 0..5) return
            synchronized(lock) { if (at >= modes.modesAt) modes = modes.copy(shuffle = shuffle, repeat = repeat, modesAt = at) }
            if (target != null && at >= issuedAt && intent.getIntExtra(target.first, -1) == target.second) latch.countDown()
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { try { accept(intent) } catch (_: RuntimeException) { } }
        }
        var registered = false
        var stage = Protocol.Stage.READ
        var position: Protocol.Position? = null
        try {
            val sticky = ContextCompat.registerReceiver(context, receiver, IntentFilter(MODE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
            registered = true
            sticky?.let(::accept)
            val audio = context.getSystemService(AudioManager::class.java)
            if (target != null) {
                @Suppress("DEPRECATION")
                try { context.packageManager.getPackageInfo(PowerampContract.PACKAGE, 0) }
                catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                    return Protocol.Result(request, Protocol.Stage.UNAVAILABLE, synchronized(lock) { modes })
                }
                // Só envia valores explícitos. Repetir o mesmo pedido não cicla os modos.
                context.sendBroadcast(Intent(PowerampContract.COMMAND)
                    .setComponent(ComponentName(PowerampContract.PACKAGE, PowerampContract.RECEIVER))
                    .putExtra("cmd", if (target.first == "shuffle") 9 else 8)
                    .putExtra(target.first, target.second))
                stage = if (latch.await(2, TimeUnit.SECONDS)) Protocol.Stage.CONFIRMED else Protocol.Stage.REQUESTED
            } else if (request.action in setOf(Protocol.Action.MEDIA_DOWN, Protocol.Action.MEDIA_UP)) {
                if (audio.isVolumeFixed) stage = Protocol.Stage.UNAVAILABLE else {
                    val minimum = if (Build.VERSION.SDK_INT >= 28) audio.getStreamMinVolume(AudioManager.STREAM_MUSIC) else 0
                    val maximum = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val before = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val delta = if (request.action == Protocol.Action.MEDIA_UP) 1 else -1
                    val desired = (before + delta).coerceIn(minimum, maximum)
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, desired, 0)
                    stage = if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) == desired)
                        Protocol.Stage.CONFIRMED else Protocol.Stage.REQUESTED
                }
            } else if (PowerampRepository.read(context).state == com.powerampremote.mobile.communication.PlayerProtocol.State.PLAYING) {
                if (PowerampPositionSync(context).request() == com.powerampremote.mobile.communication.NowPlayingProtocol.SyncStage.SYNCED) {
                    val player = PowerampRepository.read(context)
                    val metadata = PowerampRepository.readNowPlaying(context)
                    val report = Protocol.Position(player.trackId, player.trackReportedAt, metadata.positionMs,
                        metadata.durationMs, metadata.positionAt)
                    if (Protocol.validPosition(report) && metadata.positionAt >= issuedAt &&
                        com.powerampremote.mobile.communication.NowPlayingProtocol.matchesTrack(metadata,
                            player.trackId, player.trackReportedAt)) position = report
                }
            }
            val result = synchronized(lock) { modes }.copy(
                volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
                minimum = if (Build.VERSION.SDK_INT >= 28) audio.getStreamMinVolume(AudioManager.STREAM_MUSIC) else 0,
                maximum = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), fixed = audio.isVolumeFixed,
                volumeAt = System.currentTimeMillis())
            return Protocol.Result(request, stage, result, position)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            return Protocol.Result(request, Protocol.Stage.ERROR, synchronized(lock) { modes })
        } catch (_: RuntimeException) {
            return Protocol.Result(request, Protocol.Stage.ERROR, synchronized(lock) { modes })
        } finally { if (registered) context.unregisterReceiver(receiver) }
    }
}
