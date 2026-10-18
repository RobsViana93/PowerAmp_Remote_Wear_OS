package com.powerampremote.mobile.poweramp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.powerampremote.mobile.communication.VisualProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Lê somente a capa atual pelo provedor oficial. Não acessa arquivos de música. */
object AlbumArtPublisher {
    private val mutex = Mutex()
    private var published: PowerampRepository.ArtReference? = null

    suspend fun publish(context: Context, force: Boolean = false) = mutex.withLock {
        val key = PowerampRepository.readArtReference(context)
        if (!force && published == key) return@withLock
        val bytes = if (key.realId > 0) withContext(Dispatchers.IO) { load(context, key.realId) } else null
        if (key != PowerampRepository.readArtReference(context)) return@withLock
        val request = PutDataMapRequest.create(VisualProtocol.ART_PATH)
        request.dataMap.apply {
            putLong("track_id", key.trackId)
            putLong("track_at", key.trackAt)
            putBoolean("available", bytes != null)
            bytes?.let { putAsset("cover", Asset.createFromBytes(it)) }
        }
        Wearable.getDataClient(context).putDataItem(request.asPutDataRequest()).await()
        published = key
    }

    private fun load(context: Context, realId: Long): ByteArray? = try {
        val uri = Uri.parse("content://com.maxmpz.audioplayer.aa/files/$realId?hd=0&dl=0")
        val source = context.contentResolver.openInputStream(uri)?.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var count: Int
            while (stream.read(buffer).also { count = it } != -1) {
                output.write(buffer, 0, count)
                if (output.size() > 8 * 1024 * 1024) return@use null
            }
            output.toByteArray()
        }
        if (source == null) null else resize(source)
    } catch (_: Exception) { null }

    private fun resize(bytes: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 512) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        return try {
            val scale = minOf(1f, 256f / maxOf(bitmap.width, bitmap.height))
            val scaled = Bitmap.createScaledBitmap(bitmap, maxOf(1, (bitmap.width * scale).toInt()),
                maxOf(1, (bitmap.height * scale).toInt()), true)
            try {
                ByteArrayOutputStream().use { output ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 80, output)
                    output.toByteArray().takeIf { it.size <= VisualProtocol.MAX_IMAGE_BYTES }
                }
            } finally { if (scaled !== bitmap) scaled.recycle() }
        } finally { bitmap.recycle() }
    }
}
