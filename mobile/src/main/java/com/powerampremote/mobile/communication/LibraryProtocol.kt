package com.powerampremote.mobile.communication

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Páginas limitadas; o relógio nunca fornece SQL nem URI ao Companion. */
object LibraryProtocol {
    const val CAPABILITY = "poweramp_remote_companion_v7"
    const val PATH = "/powerampremote/v7/library"
    const val RESULT_PATH = "/powerampremote/v7/library-result"
    const val PAGE_SIZE = 12
    const val TEXT_LIMIT = 160
    const val MAX_BYTES = 20_000
    private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    enum class Action { ALBUMS, TRACKS, PLAY_ALBUM, PLAY_TRACK }
    enum class Stage { PAGE, REQUESTED, OBSERVED, DENIED, UNAVAILABLE, NOT_FOUND, EXPIRED, ERROR }
    data class Request(val id: String, val action: Action, val albumId: Long = 0,
                       val trackId: Long = 0, val cursor: String = "")
    data class Item(val id: Long, val title: String, val subtitle: String = "")
    data class Result(val request: Request, val stage: Stage, val items: List<Item> = emptyList(),
                      val next: String = "")
    fun browsing(action: Action) = action == Action.ALBUMS || action == Action.TRACKS
    fun valid(request: Request): Boolean = uuid.matches(request.id) &&
        (request.cursor.isEmpty() || uuid.matches(request.cursor)) && when (request.action) {
            Action.ALBUMS -> request.albumId == 0L && request.trackId == 0L
            Action.TRACKS -> request.albumId > 0 && request.trackId == 0L
            Action.PLAY_ALBUM -> request.albumId > 0 && request.trackId == 0L && request.cursor.isEmpty()
            Action.PLAY_TRACK -> request.albumId > 0 && request.trackId > 0 && request.cursor.isEmpty()
        }
    fun valid(result: Result): Boolean {
        if (!valid(result.request) || result.items.size > PAGE_SIZE ||
            (result.next.isNotEmpty() && !uuid.matches(result.next))) return false
        if (result.next.isNotEmpty() && result.next == result.request.cursor) return false
        if (result.items.any { it.id <= 0 || it.title.length > TEXT_LIMIT || it.subtitle.length > TEXT_LIMIT } ||
            result.items.map { it.id }.distinct().size != result.items.size) return false
        if (result.stage == Stage.PAGE) return browsing(result.request.action) &&
            (result.next.isEmpty() || result.items.size == PAGE_SIZE)
        if (result.items.isNotEmpty() || result.next.isNotEmpty()) return false
        if (result.stage == Stage.EXPIRED && !browsing(result.request.action)) return false
        return result.stage !in setOf(Stage.REQUESTED, Stage.OBSERVED) || !browsing(result.request.action)
    }
    fun text(value: String?) = value.orEmpty().take(TEXT_LIMIT)
    /** Os dois relógios usados aqui são do celular: evento de faixa e sessão de mídia. */
    fun hasPlaybackEvidence(request: Request, realId: Long, trackAt: Long, issuedAt: Long,
                            playing: Boolean, sessionAt: Long, issuedElapsed: Long): Boolean =
        !browsing(request.action) && realId > 0 && issuedAt > 0 && trackAt >= issuedAt &&
            (request.trackId == 0L || request.trackId == realId) && playing &&
            issuedElapsed >= 0 && sessionAt >= issuedElapsed
    private fun writeRequest(data: DataOutputStream, request: Request) {
        data.writeInt(0x4C494237); data.writeUTF(request.id); data.writeUTF(request.action.name)
        data.writeLong(request.albumId); data.writeLong(request.trackId); data.writeUTF(request.cursor)
    }
    private fun readRequest(data: DataInputStream): Request? {
        if (data.readInt() != 0x4C494237) return null
        val id = data.readUTF()
        val actionName = data.readUTF()
        val action = Action.entries.firstOrNull { it.name == actionName } ?: return null
        return Request(id, action, data.readLong(), data.readLong(), data.readUTF()).takeIf(::valid)
    }
    fun encode(request: Request): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { writeRequest(it, request) }
    }.toByteArray()
    fun decode(bytes: ByteArray): Request? {
        if (bytes.size !in 60..160) return null
        return try { DataInputStream(ByteArrayInputStream(bytes)).use { data ->
            readRequest(data)?.takeIf { data.available() == 0 }
        } } catch (_: Exception) { null }
    }
    fun encodeResult(result: Result): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { data ->
            writeRequest(data, result.request); data.writeUTF(result.stage.name); data.writeUTF(result.next)
            data.writeInt(result.items.size)
            for (item in result.items) { data.writeLong(item.id); data.writeUTF(item.title); data.writeUTF(item.subtitle) }
        }
    }.toByteArray()
    fun decodeResult(bytes: ByteArray): Result? {
        if (bytes.size !in 70..MAX_BYTES) return null
        return try { DataInputStream(ByteArrayInputStream(bytes)).use { data ->
            val request = readRequest(data) ?: return null
            val stageName = data.readUTF()
            val stage = Stage.entries.firstOrNull { it.name == stageName } ?: return null
            val next = data.readUTF()
            val count = data.readInt()
            if (count !in 0..PAGE_SIZE) return null
            val items = List(count) { Item(data.readLong(), data.readUTF(), data.readUTF()) }
            Result(request, stage, items, next).takeIf { data.available() == 0 && valid(it) }
        } } catch (_: Exception) { null }
    }
}
