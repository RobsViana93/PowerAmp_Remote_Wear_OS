package com.powerampremote.mobile.poweramp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.powerampremote.mobile.communication.VisualProtocol
import kotlin.math.abs

/** SEEK=15, pos em segundos; consulta posterior não confunde envio com execução. */
object PowerampSeek {
    fun execute(context: Context, request: VisualProtocol.Seek): VisualProtocol.Result {
        fun result(stage: VisualProtocol.Stage) = VisualProtocol.Result(request.id, stage)
        val player = PowerampRepository.read(context)
        val metadata = PowerampRepository.readNowPlaying(context)
        if (player.trackId != request.trackId || player.trackReportedAt != request.trackAt ||
            metadata.trackId != request.trackId) return result(VisualProtocol.Stage.STALE_TRACK)
        if (!VisualProtocol.validTarget(request, player.trackId, player.trackReportedAt, metadata.durationMs))
            return result(VisualProtocol.Stage.INVALID_POSITION)
        return try {
            context.sendBroadcast(Intent(PowerampContract.COMMAND)
                .setComponent(ComponentName(PowerampContract.PACKAGE, PowerampContract.RECEIVER))
                .putExtra("cmd", 15).putExtra("pos", request.seconds))
            val sync = PowerampPositionSync(context).request()
            val after = PowerampRepository.readNowPlaying(context)
            val current = PowerampRepository.read(context)
            val confirmed = sync == com.powerampremote.mobile.communication.NowPlayingProtocol.SyncStage.SYNCED &&
                current.trackId == request.trackId && current.trackReportedAt == request.trackAt &&
                abs(after.positionMs - request.seconds.toLong() * 1000) <= 2000
            result(if (confirmed) VisualProtocol.Stage.CONFIRMED else VisualProtocol.Stage.REQUESTED)
        } catch (_: RuntimeException) { result(VisualProtocol.Stage.ERROR) }
    }
}
