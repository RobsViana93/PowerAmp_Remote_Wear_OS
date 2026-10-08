package com.powerampremote.mobile.communication

import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.util.concurrent.TimeUnit

/** Google Play services ativa este serviço somente para eventos filtrados. */
class WearMessageListener : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
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
}
