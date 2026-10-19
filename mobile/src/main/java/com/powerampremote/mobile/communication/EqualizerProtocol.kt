package com.powerampremote.mobile.communication

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Controle explícito de equ; leitura significa último evento, nunca consulta ativa ao player. */
object EqualizerProtocol {
    const val CAPABILITY = "poweramp_remote_companion_v6"
    const val PATH = "/powerampremote/v6/equalizer"
    const val RESULT_PATH = "/powerampremote/v6/equalizer-result"
    private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    enum class Action { READ, ENABLE, DISABLE }
    enum class Stage { READ, OBSERVED, REQUESTED, UNAVAILABLE, ERROR }
    enum class State { UNKNOWN, OFF, ON }
    data class Request(val id: String, val action: Action)
    data class Snapshot(val state: State = State.UNKNOWN, val reportedAt: Long = 0)
    data class Result(val request: Request, val stage: Stage, val snapshot: Snapshot)

    fun target(action: Action): State? = when (action) {
        Action.ENABLE -> State.ON
        Action.DISABLE -> State.OFF
        Action.READ -> null
    }
    fun valid(snapshot: Snapshot): Boolean = if (snapshot.state == State.UNKNOWN)
        snapshot.reportedAt == 0L else snapshot.reportedAt > 0

    /** Timestamp do celular; nunca comparado ao relógio de parede do Wear OS. */
    fun accept(current: Snapshot, enabled: Boolean?, at: Long, now: Long): Snapshot {
        if (enabled == null || at <= 0 || at > now || at < current.reportedAt) return current
        return Snapshot(if (enabled) State.ON else State.OFF, at)
    }
    fun observed(request: Request, snapshot: Snapshot, issuedAt: Long): Boolean =
        target(request.action)?.let { it == snapshot.state && snapshot.reportedAt >= issuedAt } == true

    fun encode(request: Request): ByteArray = "v6|${request.id}|${request.action}".toByteArray(Charsets.US_ASCII)
    fun decode(bytes: ByteArray): Request? {
        if (bytes.size !in 40..50) return null
        val parts = bytes.toString(Charsets.US_ASCII).split('|')
        if (parts.size != 3 || parts[0] != "v6" || !uuid.matches(parts[1])) return null
        return Action.entries.firstOrNull { it.name == parts[2] }?.let { Request(parts[1], it) }
    }
    fun encodeResult(result: Result): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use {
            it.writeInt(0x45515536)
            it.writeUTF(result.request.id); it.writeUTF(result.request.action.name)
            it.writeUTF(result.stage.name); it.writeUTF(result.snapshot.state.name)
            it.writeLong(result.snapshot.reportedAt)
        }
        return bytes.toByteArray()
    }
    fun decodeResult(bytes: ByteArray): Result? {
        if (bytes.size !in 60..100) return null
        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use {
                if (it.readInt() != 0x45515536) return null
                val id = it.readUTF().takeIf(uuid::matches) ?: return null
                val actionName = it.readUTF()
                val action = Action.entries.firstOrNull { action -> action.name == actionName } ?: return null
                val stageName = it.readUTF()
                val stage = Stage.entries.firstOrNull { stage -> stage.name == stageName } ?: return null
                val stateName = it.readUTF()
                val state = State.entries.firstOrNull { state -> state.name == stateName } ?: return null
                val snapshot = Snapshot(state, it.readLong())
                val request = Request(id, action)
                if (it.available() != 0 || !valid(snapshot)) return null
                if (stage == Stage.OBSERVED && (target(action) == null || target(action) != state)) return null
                if (stage == Stage.REQUESTED && action == Action.READ) return null
                if (stage == Stage.READ && action != Action.READ) return null
                Result(request, stage, snapshot)
            }
        } catch (_: Exception) { null }
    }
}
