package com.powerampremote.mobile.communication

/** Contrato fechado para seek e associação da capa à faixa. Posições em segundos. */
object VisualProtocol {
    const val CAPABILITY = "poweramp_remote_companion_v4"
    const val SEEK_PATH = "/powerampremote/v4/player/seek"
    const val RESULT_PATH = "/powerampremote/v4/player/seek-result"
    const val ART_PATH = "/powerampremote/v4/player/art"
    const val MAX_IMAGE_BYTES = 262144
    private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    data class Seek(val id: String, val trackId: Long, val trackAt: Long, val seconds: Int)
    enum class Stage { CONFIRMED, REQUESTED, STALE_TRACK, INVALID_POSITION, ERROR }
    data class Result(val id: String, val stage: Stage)
    fun encode(request: Seek): ByteArray = "v4|${request.id}|${request.trackId}|${request.trackAt}|${request.seconds}".toByteArray(Charsets.US_ASCII)
    fun decode(bytes: ByteArray): Seek? {
        if (bytes.size !in 45..120) return null
        val parts = bytes.toString(Charsets.US_ASCII).split('|')
        if (parts.size != 5 || parts[0] != "v4" || !uuid.matches(parts[1])) return null
        val track = parts[2].toLongOrNull()?.takeIf { it > 0 } ?: return null
        val at = parts[3].toLongOrNull()?.takeIf { it > 0 } ?: return null
        val seconds = parts[4].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        return Seek(parts[1], track, at, seconds)
    }
    fun encodeResult(result: Result): ByteArray = "v4|${result.id}|${result.stage}".toByteArray(Charsets.US_ASCII)
    fun decodeResult(bytes: ByteArray): Result? {
        if (bytes.size !in 40..80) return null
        val parts = bytes.toString(Charsets.US_ASCII).split('|')
        if (parts.size != 3 || parts[0] != "v4" || !uuid.matches(parts[1])) return null
        return Stage.entries.firstOrNull { it.name == parts[2] }?.let { Result(parts[1], it) }
    }
    fun validTarget(request: Seek, trackId: Long, trackAt: Long, durationMs: Long): Boolean =
        request.trackId == trackId && request.trackAt == trackAt && trackId > 0 && trackAt > 0 &&
            durationMs >= 1000 && request.seconds >= 0 && request.seconds.toLong() < durationMs / 1000
    fun fraction(positionMs: Long, durationMs: Long): Float =
        if (positionMs < 0 || durationMs <= 0) 0f else (positionMs.toDouble() / durationMs).coerceIn(0.0, 1.0).toFloat()
}
