package com.powerampremote.mobile.communication

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Contrato fechado: páginas por ID, sem SQL, nomes de bandas ou URIs fornecidos pelo relógio. */
object PresetProtocol {
    const val CAPABILITY = "poweramp_remote_companion_v9"
    const val PATH = "/powerampremote/v9/presets"
    const val RESULT_PATH = "/powerampremote/v9/presets-result"
    const val PAGE_SIZE = 10
    const val TEXT_LIMIT = 120
    const val MAX_BYTES = 5_000
    private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    enum class Action { LIST, APPLY }
    enum class Stage { PAGE, REQUESTED, OBSERVED, DENIED, UNAVAILABLE, NOT_FOUND, ERROR }
    data class Request(val id: String, val action: Action, val afterId: Long = 0, val presetId: Long = 0)
    data class Item(val id: Long, val name: String, val parametric: Boolean)
    data class Result(val request: Request, val stage: Stage, val items: List<Item> = emptyList(),
                      val hasMore: Boolean = false)
    fun valid(request: Request): Boolean = uuid.matches(request.id) && when (request.action) {
        Action.LIST -> request.afterId >= 0 && request.presetId == 0L
        Action.APPLY -> request.afterId == 0L && request.presetId > 0
    }
    fun valid(result: Result): Boolean {
        if (!valid(result.request) || result.items.size > PAGE_SIZE) return false
        if (result.stage != Stage.PAGE) return result.items.isEmpty() && !result.hasMore &&
            (result.stage !in setOf(Stage.OBSERVED, Stage.REQUESTED) || result.request.action == Action.APPLY)
        return result.request.action == Action.LIST && (!result.hasMore || result.items.size == PAGE_SIZE) &&
            result.items.all { it.id > result.request.afterId && it.name.isNotBlank() && it.name.length <= TEXT_LIMIT } &&
            result.items.zipWithNext().all { (a, b) -> a.id < b.id }
    }
    fun observed(request: Request, eventId: Long?, eventAt: Long, issuedAt: Long, now: Long): Boolean =
        valid(request) && request.action == Action.APPLY && eventId == request.presetId &&
            issuedAt > 0 && eventAt >= issuedAt && eventAt <= now
    private fun write(data: DataOutputStream, request: Request) {
        data.writeInt(0x50525339); data.writeUTF(request.id); data.writeUTF(request.action.name)
        data.writeLong(request.afterId); data.writeLong(request.presetId)
    }
    private fun readRequest(data: DataInputStream): Request? {
        if (data.readInt() != 0x50525339) return null
        val id = data.readUTF()
        val actionName = data.readUTF()
        val action = Action.entries.firstOrNull { it.name == actionName } ?: return null
        return Request(id, action, data.readLong(), data.readLong()).takeIf(::valid)
    }
    fun encode(request: Request): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { write(it, request) }
    }.toByteArray()
    fun decode(bytes: ByteArray): Request? {
        if (bytes.size !in 60..80) return null
        return try { DataInputStream(ByteArrayInputStream(bytes)).use { data ->
            readRequest(data)?.takeIf { data.available() == 0 }
        } } catch (_: Exception) { null }
    }
    fun encodeResult(result: Result): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { data ->
            write(data, result.request); data.writeUTF(result.stage.name); data.writeBoolean(result.hasMore)
            data.writeInt(result.items.size)
            for (item in result.items) { data.writeLong(item.id); data.writeUTF(item.name); data.writeBoolean(item.parametric) }
        }
    }.toByteArray()
    fun decodeResult(bytes: ByteArray): Result? {
        if (bytes.size !in 70..MAX_BYTES) return null
        return try { DataInputStream(ByteArrayInputStream(bytes)).use { data ->
            val request = readRequest(data) ?: return null
            val stageName = data.readUTF()
            val stage = Stage.entries.firstOrNull { it.name == stageName } ?: return null
            val more = data.readBoolean()
            val count = data.readInt()
            if (count !in 0..PAGE_SIZE) return null
            val items = List(count) { Item(data.readLong(), data.readUTF(), data.readBoolean()) }
            Result(request, stage, items, more).takeIf { valid(it) && data.available() == 0 }
        } } catch (_: Exception) { null }
    }
}
