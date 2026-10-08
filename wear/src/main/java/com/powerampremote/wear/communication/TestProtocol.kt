package com.powerampremote.wear.communication

/** Contrato mínimo v1. Não aceita comandos livres nem mensagens antigas. */
object TestProtocol {
    const val CAPABILITY = "poweramp_remote_companion_v1"
    const val REQUEST_PATH = "/powerampremote/v1/test"
    const val ACK_PATH = "/powerampremote/v1/test/ack"
    private val payloadPattern = Regex("v1:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

    fun encode(requestId: String): ByteArray {
        val payload = "v1:$requestId"
        require(payloadPattern.matches(payload)) { "Identificador de teste inválido" }
        return payload.toByteArray(Charsets.UTF_8)
    }

    /** Retorna null para dados excessivos, malformados ou de outra versão. */
    fun decode(data: ByteArray): String? {
        if (data.size != 39) return null
        val payload = data.toString(Charsets.UTF_8)
        return if (payloadPattern.matches(payload)) payload.removePrefix("v1:") else null
    }
}
