package com.powerampremote.wear.communication

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Campo opcional v3 dentro do mesmo DataItem v2; não altera os comandos anteriores. */
object NowPlayingProtocol {
    const val DATA_KEY = "now_playing_v3"
    const val CAPABILITY = "poweramp_remote_companion_v3"
    const val REFRESH_PATH = "/powerampremote/v3/player/refresh"
    const val REFRESH_RESULT_PATH = "/powerampremote/v3/player/refreshed"
    private const val MAGIC = 0x4e503033
    private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

    data class Snapshot(
        val trackId: Long = -1,
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val durationMs: Long = -1,
        val positionMs: Long = -1,
        val metadataAt: Long = 0,
        val positionAt: Long = 0
    )
    data class Update(
        val at: Long,
        val trackEvent: Boolean,
        val trackId: Long? = null,
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val durationMs: Long? = null,
        val positionMs: Long? = null
    )
    enum class SyncStage { SYNCED, NO_POSITION, NOT_INSTALLED, ERROR }
    data class SyncResult(val id: String, val stage: SyncStage)

    fun cleanText(value: String): String {
        val text = value.take(1024).map { if (it.isISOControl()) ' ' else it }.joinToString("")
            .trim().take(256)
        return if (text.lastOrNull()?.isHighSurrogate() == true) text.dropLast(1) else text
    }

    /** Nova faixa limpa campos ausentes; evento parcial da mesma faixa os preserva. */
    fun merge(before: Snapshot, update: Update): Snapshot {
        if (update.at <= 0 || update.at < before.metadataAt) return before
        val changed = (update.trackId != null && update.trackId != before.trackId) ||
            (update.trackEvent && update.trackId == null)
        val base = if (changed) Snapshot(trackId = update.trackId ?: -1) else before
        val hasMetadata = update.trackEvent || update.trackId != null || update.title != null ||
            update.artist != null || update.album != null || update.durationMs != null
        val position = update.positionMs?.takeIf { it >= 0 && (changed || update.at >= before.positionAt) }
        return base.copy(
            title = update.title?.let(::cleanText) ?: base.title,
            artist = update.artist?.let(::cleanText) ?: base.artist,
            album = update.album?.let(::cleanText) ?: base.album,
            durationMs = update.durationMs?.takeIf { it >= 0 } ?: base.durationMs,
            positionMs = position ?: base.positionMs,
            metadataAt = if (hasMetadata) update.at else base.metadataAt,
            positionAt = if (position != null) update.at else base.positionAt
        )
    }

    /** durMs tem preferência; dur e pos oficiais são segundos. Ausência não vira zero. */
    fun durationMs(milliseconds: Long?, seconds: Long?): Long? =
        milliseconds?.takeIf { it >= 0 } ?: secondsToMs(seconds)

    fun secondsToMs(seconds: Long?): Long? =
        seconds?.takeIf { it in 0..(Long.MAX_VALUE / 1000) }?.times(1000)

    fun formatTime(milliseconds: Long): String {
        if (milliseconds < 0) return "--:--"
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        return "$minutes:${(seconds % 60).toString().padStart(2, '0')}"
    }

    fun matchesTrack(snapshot: Snapshot, trackId: Long, trackAt: Long): Boolean =
        snapshot.metadataAt > 0 && snapshot.metadataAt >= trackAt &&
            (trackId < 0 || snapshot.trackId == trackId)

    fun encode(snapshot: Snapshot): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(MAGIC)
            output.writeLong(snapshot.trackId)
            output.writeUTF(cleanText(snapshot.title))
            output.writeUTF(cleanText(snapshot.artist))
            output.writeUTF(cleanText(snapshot.album))
            output.writeLong(snapshot.durationMs)
            output.writeLong(snapshot.positionMs)
            output.writeLong(snapshot.metadataAt)
            output.writeLong(snapshot.positionAt)
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): Snapshot? {
        if (bytes.size !in 50..4096) return null
        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                if (input.readInt() != MAGIC) return null
                val snapshot = Snapshot(input.readLong(), input.readUTF(), input.readUTF(), input.readUTF(),
                    input.readLong(), input.readLong(), input.readLong(), input.readLong())
                if (input.available() != 0 || snapshot.trackId < -3 || snapshot.durationMs < -1 ||
                    snapshot.positionMs < -1 || snapshot.metadataAt < 0 || snapshot.positionAt < 0) return null
                if (listOf(snapshot.title, snapshot.artist, snapshot.album).any { it != cleanText(it) }) return null
                snapshot
            }
        } catch (_: Exception) { null }
    }

    fun encodeRefresh(id: String): ByteArray {
        require(uuid.matches(id))
        return "v3:$id".toByteArray(Charsets.US_ASCII)
    }
    fun decodeRefresh(bytes: ByteArray): String? {
        if (bytes.size != 39) return null
        val text = bytes.toString(Charsets.US_ASCII)
        if (!text.startsWith("v3:")) return null
        return text.substring(3).takeIf { uuid.matches(it) }
    }
    fun encodeSyncResult(result: SyncResult): ByteArray =
        (encodeRefresh(result.id).toString(Charsets.US_ASCII) + "|${result.stage}").toByteArray(Charsets.US_ASCII)

    fun decodeSyncResult(bytes: ByteArray): SyncResult? {
        if (bytes.size !in 41..64) return null
        val parts = bytes.toString(Charsets.US_ASCII).split('|')
        if (parts.size != 2) return null
        val id = decodeRefresh(parts[0].toByteArray(Charsets.US_ASCII)) ?: return null
        val stage = SyncStage.entries.firstOrNull { it.name == parts[1] } ?: return null
        return SyncResult(id, stage)
    }
}
