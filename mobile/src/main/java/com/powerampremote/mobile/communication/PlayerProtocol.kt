package com.powerampremote.mobile.communication

/** Contrato fechado v2: somente os quatro comandos desta etapa. */
object PlayerProtocol {
    const val COMMAND_PATH = "/powerampremote/v2/player/command"
    const val RESULT_PATH = "/powerampremote/v2/player/result"
    const val STATE_PATH = "/powerampremote/v2/player/state"
    const val CAPABILITY = "poweramp_remote_companion_v2"
    private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

    enum class Command(val apiCode: Int) { PLAY(3), PAUSE(2), NEXT(4), PREVIOUS(5) }
    enum class State { UNKNOWN, STOPPED, PLAYING, PAUSED }
    enum class Stage { RECEIVED, REQUESTED, OBSERVED, NO_UPDATE, NOT_INSTALLED, UNSUPPORTED, ERROR }
    data class Request(val id: String, val command: Command)
    data class Snapshot(
        val state: State = State.UNKNOWN,
        val reportedAt: Long = 0,
        val trackId: Long = -1,
        val trackReportedAt: Long = 0
    )
    data class Result(val request: Request, val stage: Stage, val snapshot: Snapshot)

    private fun fields(data: ByteArray): List<String>? {
        if (data.isEmpty() || data.size > 256 || data.any { it.toInt() !in 32..126 }) return null
        return data.toString(Charsets.US_ASCII).split('|').takeIf { it.firstOrNull() == "v2" }
    }

    fun encodeRequest(request: Request): ByteArray {
        require(uuid.matches(request.id))
        return "v2|${request.id}|${request.command.name}".toByteArray(Charsets.US_ASCII)
    }

    fun decodeRequest(data: ByteArray): Request? {
        val parts = fields(data) ?: return null
        if (parts.size != 3 || !uuid.matches(parts[1])) return null
        return Request(parts[1], Command.entries.firstOrNull { it.name == parts[2] } ?: return null)
    }

    fun encodeSnapshot(snapshot: Snapshot): ByteArray =
        "v2|${snapshot.state}|${snapshot.reportedAt}|${snapshot.trackId}|${snapshot.trackReportedAt}"
            .toByteArray(Charsets.US_ASCII)

    fun decodeSnapshot(data: ByteArray): Snapshot? {
        val parts = fields(data) ?: return null
        if (parts.size != 5) return null
        val state = State.entries.firstOrNull { it.name == parts[1] } ?: return null
        val at = parts[2].toLongOrNull()?.takeIf { it >= 0 } ?: return null
        val id = parts[3].toLongOrNull()?.takeIf { it >= -1 } ?: return null
        val trackAt = parts[4].toLongOrNull()?.takeIf { it >= 0 } ?: return null
        return Snapshot(state, at, id, trackAt)
    }

    fun encodeResult(result: Result): ByteArray {
        val request = encodeRequest(result.request).toString(Charsets.US_ASCII)
        val snapshot = encodeSnapshot(result.snapshot).toString(Charsets.US_ASCII).removePrefix("v2|")
        return "$request|${result.stage}|$snapshot".toByteArray(Charsets.US_ASCII)
    }

    fun decodeResult(data: ByteArray): Result? {
        val parts = fields(data) ?: return null
        if (parts.size != 8) return null
        val request = decodeRequest(parts.take(3).joinToString("|").toByteArray()) ?: return null
        val stage = Stage.entries.firstOrNull { it.name == parts[3] } ?: return null
        val snapshot = decodeSnapshot(("v2|" + parts.drop(4).joinToString("|")).toByteArray()) ?: return null
        return Result(request, stage, snapshot)
    }

    /** Um estado antigo, por si só, nunca confirma uma solicitação nova. */
    fun hasEvidence(command: Command, before: Snapshot, after: Snapshot, issuedAt: Long): Boolean =
        when (command) {
            Command.PLAY -> after.reportedAt > before.reportedAt && after.reportedAt >= issuedAt && after.state == State.PLAYING
            Command.PAUSE -> after.reportedAt > before.reportedAt && after.reportedAt >= issuedAt &&
                after.state in setOf(State.PAUSED, State.STOPPED)
            Command.NEXT, Command.PREVIOUS -> before.trackId >= 0 && after.trackId >= 0 &&
                after.trackId != before.trackId && after.trackReportedAt > before.trackReportedAt &&
                after.trackReportedAt >= issuedAt
        }
}
