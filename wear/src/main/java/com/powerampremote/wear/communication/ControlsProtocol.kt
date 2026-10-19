package com.powerampremote.wear.communication

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Somente ações conhecidas; volume sempre se refere a STREAM_MUSIC do celular. */
object ControlsProtocol {
    const val CAPABILITY = "poweramp_remote_companion_v5"
    const val PATH = "/powerampremote/v5/player/controls"
    const val RESULT_PATH = "/powerampremote/v5/player/controls-result"
    const val AUTO_INTERVAL_MS = 15_000L
    const val ESTIMATE_LIMIT_MS = 30_000L
    private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    enum class Action { QUERY, SHUFFLE_OFF, SHUFFLE_SONGS, REPEAT_OFF, REPEAT_CATEGORY, REPEAT_SONG, MEDIA_DOWN, MEDIA_UP }
    enum class Stage { READ, CONFIRMED, REQUESTED, UNAVAILABLE, ERROR }
    data class Request(val id: String, val action: Action)
    data class Snapshot(val shuffle: Int = -1, val repeat: Int = -1, val modesAt: Long = 0,
                        val volume: Int = -1, val minimum: Int = 0, val maximum: Int = -1,
                        val fixed: Boolean = false, val volumeAt: Long = 0)
    data class Position(val trackId: Long, val trackAt: Long, val positionMs: Long, val durationMs: Long, val positionAt: Long)
    data class Result(val request: Request, val stage: Stage, val snapshot: Snapshot, val position: Position? = null)
    fun encode(request: Request): ByteArray = "v5|${request.id}|${request.action}".toByteArray(Charsets.US_ASCII)
    fun decode(bytes: ByteArray): Request? {
        if (bytes.size !in 40..80) return null
        val parts = bytes.toString(Charsets.US_ASCII).split('|')
        if (parts.size != 3 || parts[0] != "v5" || !uuid.matches(parts[1])) return null
        return Action.entries.firstOrNull { it.name == parts[2] }?.let { Request(parts[1], it) }
    }
    fun encodeResult(result: Result): ByteArray = ByteArrayOutputStream().use { output ->
        DataOutputStream(output).use { data ->
            data.writeInt(0x43545235)
            data.writeUTF(result.request.id)
            data.writeUTF(result.request.action.name)
            data.writeUTF(result.stage.name)
            with(result.snapshot) {
                data.writeInt(shuffle); data.writeInt(repeat); data.writeLong(modesAt)
                data.writeInt(volume); data.writeInt(minimum); data.writeInt(maximum)
                data.writeBoolean(fixed); data.writeLong(volumeAt)
            }
            data.writeBoolean(result.position != null)
            result.position?.let {
                data.writeLong(it.trackId); data.writeLong(it.trackAt); data.writeLong(it.positionMs)
                data.writeLong(it.durationMs); data.writeLong(it.positionAt)
            }
        }
        output.toByteArray()
    }
    fun decodeResult(bytes: ByteArray): Result? {
        if (bytes.size !in 80..240) return null
        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use { data ->
                if (data.readInt() != 0x43545235) return null
                val id = data.readUTF().takeIf(uuid::matches) ?: return null
                val actionName = data.readUTF()
                val stageName = data.readUTF()
                val action = Action.entries.firstOrNull { it.name == actionName } ?: return null
                val stage = Stage.entries.firstOrNull { it.name == stageName } ?: return null
                val snapshot = Snapshot(data.readInt(), data.readInt(), data.readLong(), data.readInt(),
                    data.readInt(), data.readInt(), data.readBoolean(), data.readLong())
                val position = if (data.readBoolean()) Position(data.readLong(), data.readLong(), data.readLong(),
                    data.readLong(), data.readLong()) else null
                if (data.available() != 0 || !valid(snapshot)) return null
                if (position != null && (action != Action.QUERY || !validPosition(position))) return null
                Result(Request(id, action), stage, snapshot, position)
            }
        } catch (_: Exception) { null }
    }
    fun validPosition(position: Position): Boolean = with(position) {
        trackId > 0 && trackAt > 0 && positionMs >= 0 && durationMs > 0 && positionAt > 0
    }
    fun valid(snapshot: Snapshot): Boolean = with(snapshot) {
        ((shuffle == -1 && repeat == -1 && modesAt == 0L) ||
            (shuffle in 0..4 && repeat in 0..5 && modesAt > 0)) &&
            ((volume == -1 && maximum == -1 && minimum == 0 && volumeAt == 0L && !fixed) ||
                (minimum in 0..1000 && maximum in minimum..1000 && volume in minimum..maximum && volumeAt > 0))
    }
    fun estimatedPosition(positionMs: Long, durationMs: Long, elapsedMs: Long): Long {
        if (positionMs < 0 || durationMs <= 0) return positionMs
        val elapsed = elapsedMs.coerceIn(0, ESTIMATE_LIMIT_MS)
        return positionMs.coerceIn(0, durationMs) + minOf(elapsed, durationMs - positionMs.coerceIn(0, durationMs))
    }
}
