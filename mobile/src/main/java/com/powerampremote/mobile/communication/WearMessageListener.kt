package com.powerampremote.mobile.communication

import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.util.concurrent.TimeUnit
import com.powerampremote.mobile.poweramp.PowerampController
import com.powerampremote.mobile.poweramp.PowerampRepository
import com.powerampremote.mobile.poweramp.PowerampPositionSync
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** Google Play services ativa este serviço somente para eventos filtrados. */
class WearMessageListener : WearableListenerService() {
    // O callback é serializado pelo serviço. Evita repetir um mesmo pedido nesta instância.
    private val completed = LinkedHashMap<String, PlayerProtocol.Result>()
    private val completedSeeks = LinkedHashMap<String, Pair<VisualProtocol.Seek, VisualProtocol.Result>>()
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == VisualProtocol.SEEK_PATH) {
            val request = VisualProtocol.decode(event.data) ?: return
            val key = "${event.sourceNodeId}:${request.id}"
            val previous = completedSeeks[key]
            if (previous != null && previous.first != request) return
            val result = previous?.second ?: com.powerampremote.mobile.poweramp.PowerampSeek.execute(this, request)
            completedSeeks[key] = request to result
            if (completedSeeks.size > 32) completedSeeks.remove(completedSeeks.keys.first())
            try {
                runBlocking { withTimeout(3_000) { PowerampRepository.publish(this@WearMessageListener) } }
                Tasks.await(Wearable.getMessageClient(this).sendMessage(event.sourceNodeId,
                    VisualProtocol.RESULT_PATH, VisualProtocol.encodeResult(result)), 2, TimeUnit.SECONDS)
            } catch (_: Exception) { /* O relógio limita a espera. */ }
            return
        }
        if (event.path == NowPlayingProtocol.REFRESH_PATH) {
            refreshPosition(event)
            return
        }
        if (event.path == PlayerProtocol.COMMAND_PATH) {
            receivePlayerCommand(event)
            return
        }
        if (event.path != TestProtocol.REQUEST_PATH) return
        val requestId = TestProtocol.decode(event.data) ?: return
        CompanionStatus.update(ReceiptStatus.RECEIVED)
        try {
            // O callback padrão roda em thread de fundo, não na thread da tela.
            // Espera limitada mantém o callback vivo até solicitar a resposta.
            Tasks.await(
                Wearable.getMessageClient(this).sendMessage(
                    event.sourceNodeId,
                    TestProtocol.ACK_PATH,
                    TestProtocol.encode(requestId)
                ),
                5, TimeUnit.SECONDS
            )
            CompanionStatus.update(ReceiptStatus.ACK_SENT)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            CompanionStatus.update(ReceiptStatus.ACK_FAILED)
        } catch (_: Exception) {
            CompanionStatus.update(ReceiptStatus.ACK_FAILED)
        }
    }

    private fun refreshPosition(event: MessageEvent) {
        val id = NowPlayingProtocol.decodeRefresh(event.data) ?: return
        try {
            val stage = PowerampPositionSync(this).request()
            runBlocking { withTimeout(3_000) {
                PowerampRepository.publish(this@WearMessageListener)
                com.powerampremote.mobile.poweramp.AlbumArtPublisher.publish(this@WearMessageListener, force = true)
            } }
            Tasks.await(Wearable.getMessageClient(this).sendMessage(event.sourceNodeId,
                NowPlayingProtocol.REFRESH_RESULT_PATH,
                NowPlayingProtocol.encodeSyncResult(NowPlayingProtocol.SyncResult(id, stage))), 2, TimeUnit.SECONDS)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (_: Exception) {
            // O relógio limita sua espera; uma falha não inventa posição ou confirmação.
        }
    }

    private fun receivePlayerCommand(event: MessageEvent) {
        val request = PlayerProtocol.decodeRequest(event.data) ?: return
        val key = "${event.sourceNodeId}:${request.id}"
        completed[key]?.let {
            if (it.request == request) replyPlayer(event.sourceNodeId, it)
            return
        }
        fun reply(stage: PlayerProtocol.Stage) {
            replyPlayer(event.sourceNodeId, PlayerProtocol.Result(request, stage, PowerampRepository.read(this)))
        }
        reply(PlayerProtocol.Stage.RECEIVED)
        try {
            val result = PowerampController(this).execute(request) { reply(PlayerProtocol.Stage.REQUESTED) }
            completed[key] = result
            if (completed.size > 32) completed.remove(completed.keys.first())
            replyPlayer(event.sourceNodeId, result)
        } catch (_: RuntimeException) {
            reply(PlayerProtocol.Stage.ERROR)
        }
    }

    private fun replyPlayer(nodeId: String, result: PlayerProtocol.Result) {
        try {
            Tasks.await(Wearable.getMessageClient(this).sendMessage(
                nodeId, PlayerProtocol.RESULT_PATH, PlayerProtocol.encodeResult(result)
            ), 2, TimeUnit.SECONDS)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (_: Exception) {
            // Falha de transporte não transforma um pedido ao player em sucesso.
        }
    }
}
