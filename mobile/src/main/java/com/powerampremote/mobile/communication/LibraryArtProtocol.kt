package com.powerampremote.mobile.communication

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Canal opcional de capas; não altera as páginas binárias da biblioteca v7. */
object LibraryArtProtocol {
    const val REQUEST_PATH = "/powerampremote/v8/library/art"
    const val RESULT_PATH = "/powerampremote/v8/library/art-result"
    const val MAX_IMAGE_BYTES = 32 * 1024
    private const val MAGIC = 0x4C415238
    private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    data class Request(val id: String, val albumId: Long)
    enum class Status { AVAILABLE, MISSING, ERROR }
    data class Result(val request: Request, val status: Status, val jpeg: ByteArray = byteArrayOf())
    fun valid(request: Request) = uuid.matches(request.id) && request.albumId > 0
    fun valid(result: Result) = valid(result.request) && result.jpeg.size <= MAX_IMAGE_BYTES &&
        ((result.status == Status.AVAILABLE && result.jpeg.isNotEmpty()) ||
            (result.status != Status.AVAILABLE && result.jpeg.isEmpty()))
    fun encode(request: Request): ByteArray = ByteArrayOutputStream().also { output ->
        DataOutputStream(output).use { it.writeInt(MAGIC); it.writeUTF(request.id); it.writeLong(request.albumId) }
    }.toByteArray()
    fun decode(bytes: ByteArray): Request? = try {
        if (bytes.size !in 50..80) null else DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            if (input.readInt() != MAGIC) null else Request(input.readUTF(), input.readLong())
                .takeIf { valid(it) && input.available() == 0 }
        }
    } catch (_: Exception) { null }
    fun encodeResult(result: Result): ByteArray = ByteArrayOutputStream().also { output ->
        require(valid(result))
        DataOutputStream(output).use { data ->
            data.write(encode(result.request)); data.writeUTF(result.status.name)
            data.writeInt(result.jpeg.size); data.write(result.jpeg)
        }
    }.toByteArray()
    fun decodeResult(bytes: ByteArray): Result? = try {
        if (bytes.size !in 63..(MAX_IMAGE_BYTES + 100)) null else DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            if (input.readInt() != MAGIC) null else {
                val request = Request(input.readUTF(), input.readLong())
                val statusName = input.readUTF()
                val status = Status.entries.firstOrNull { it.name == statusName }
                val size = input.readInt()
                if (!valid(request) || status == null || size !in 0..MAX_IMAGE_BYTES || size != input.available()) null
                else Result(request, status, ByteArray(size).also { input.readFully(it) }).takeIf(::valid)
            }
        }
    } catch (_: Exception) { null }
}
