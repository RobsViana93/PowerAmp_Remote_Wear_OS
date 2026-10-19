package com.powerampremote.mobile.poweramp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.powerampremote.mobile.communication.LibraryProtocol as Protocol
import java.util.UUID

/** Acesso somente de leitura ao provedor público. Todos os métodos de consulta rodam em fundo. */
class PowerampLibrary(private val context: Context) {
    companion object {
        private const val ROOT = "content://com.maxmpz.audioplayer.data"
        private const val TTL_MS = 10 * 60_000L
        private data class Bookmark(val owner: String, val action: Protocol.Action, val albumId: Long,
                                    val title: String, val number: Long, val id: Long, val at: Long)
        private val bookmarks = LinkedHashMap<String, Bookmark>()
        fun permissionIntent(context: Context): Intent = Intent("com.maxmpz.audioplayer.ACTION_ASK_FOR_DATA_PERMISSION")
            .setComponent(ComponentName(PowerampContract.PACKAGE, "com.maxmpz.audioplayer.PowerampAPIActivity"))
            // EXTRA_PACKAGE é "pak", conforme o exemplo oficial; não é o nome literal "package".
            .putExtra("pak", context.packageName)
    }
    private fun uri(path: String, limit: Int): Uri = Uri.parse("$ROOT/$path").buildUpon()
        .appendQueryParameter("lim", limit.toString()).build()

    private fun <T> query(path: String, projection: Array<String>, selection: String? = null,
                          args: Array<String>? = null, sort: String? = null, limit: Int = Protocol.PAGE_SIZE + 1,
                          block: (Cursor) -> T): T {
        val signal = CancellationSignal()
        val handler = Handler(Looper.getMainLooper())
        val cancel = Runnable { signal.cancel() }
        handler.postDelayed(cancel, 5_000)
        try {
            return context.contentResolver.query(uri(path, limit), projection, selection, args, sort, signal)
                ?.use(block) ?: throw IllegalStateException("Consulta sem cursor")
        } finally { handler.removeCallbacks(cancel) }
    }
    fun probe(): Protocol.Stage = execute(Protocol.Request(UUID.randomUUID().toString(), Protocol.Action.ALBUMS), "setup").stage

    fun execute(request: Protocol.Request, owner: String): Protocol.Result {
        if (!Protocol.valid(request)) return Protocol.Result(request, Protocol.Stage.ERROR)
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(PowerampContract.PACKAGE, 0)
            return if (Protocol.browsing(request.action)) page(request, owner) else play(request)
        } catch (_: PackageManager.NameNotFoundException) {
            return Protocol.Result(request, Protocol.Stage.UNAVAILABLE)
        } catch (_: SecurityException) {
            return Protocol.Result(request, Protocol.Stage.DENIED)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return Protocol.Result(request, Protocol.Stage.ERROR)
        } catch (error: RuntimeException) {
            android.util.Log.w("PowerampLibrary", "Falha ao consultar a API pública", error)
            return Protocol.Result(request, Protocol.Stage.ERROR)
        }
    }
    private fun page(request: Protocol.Request, owner: String): Protocol.Result {
        val previous = if (request.cursor.isEmpty()) null else {
            synchronized(bookmarks) {
                bookmarks[request.cursor]?.takeIf { it.owner == owner && it.action == request.action &&
                    it.albumId == request.albumId && SystemClock.elapsedRealtime() - it.at in 0..TTL_MS }
            } ?: return Protocol.Result(request, Protocol.Stage.EXPIRED)
        }
        val albums = request.action == Protocol.Action.ALBUMS
        val idColumn = if (albums) "albums._id" else "folder_files._id"
        val orderColumn = if (albums) "COALESCE(album,'') COLLATE NOCASE" else "COALESCE(track_number,10000)"
        val path = if (albums) "albums" else "albums/${request.albumId}/files"
        if (!albums && !albumExists(request.albumId)) return Protocol.Result(request, Protocol.Stage.NOT_FOUND)
        val projection = if (albums) arrayOf(idColumn, "album", "albums.album_artist_id")
            else arrayOf(idColumn, "title_tag", "folder_files.name", "track_number", "artist_tag")
        val parameter = if (albums) "?" else "CAST(? AS INTEGER)"
        val selection = previous?.let { "($orderColumn > $parameter OR ($orderColumn = $parameter AND $idColumn > ?))" }
        val key = previous?.let { if (albums) it.title else it.number.toString() }
        val args = previous?.let { arrayOf(key!!, key, it.id.toString()) }
        data class Row(val item: Protocol.Item, val title: String, val number: Long, val artistId: Long)
        val rows = query(path, projection, selection, args, "$orderColumn, $idColumn") { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    check(size <= Protocol.PAGE_SIZE) { "Provedor ignorou o limite" }
                    val id = cursor.getLong(0)
                    check(id > 0)
                    val raw = cursor.getString(1).orEmpty()
                    check(raw.length <= 4096) { "Chave excessiva" }
                    if (albums) add(Row(Protocol.Item(id, Protocol.text(raw)), raw, 0, cursor.getLong(2)))
                    else add(Row(Protocol.Item(id, Protocol.text(raw.ifBlank { cursor.getString(2).orEmpty() }),
                        Protocol.text(cursor.getString(4))), "", cursor.getLong(3).let { if (cursor.isNull(3)) 10000 else it }, 0))
                }
            }
        }
        val visible = rows.take(Protocol.PAGE_SIZE)
        val artists = if (albums) artistNames(visible.map { it.artistId }.filter { it > 0 }.distinct()) else emptyMap()
        val next = if (rows.size <= Protocol.PAGE_SIZE) "" else {
            val last = visible.last()
            val token = UUID.randomUUID().toString()
            synchronized(bookmarks) {
                bookmarks[token] = Bookmark(owner, request.action, request.albumId, last.title, last.number,
                    last.item.id, SystemClock.elapsedRealtime())
                while (bookmarks.size > 128) bookmarks.remove(bookmarks.keys.first())
            }
            token
        }
        return Protocol.Result(request, Protocol.Stage.PAGE, visible.map { row ->
            if (albums) row.item.copy(subtitle = Protocol.text(artists[row.artistId])) else row.item
        }, next)
    }
    private fun artistNames(ids: List<Long>): Map<Long, String> {
        if (ids.isEmpty()) return emptyMap()
        return query("album_artists", arrayOf("album_artists._id", "album_artist"),
            "album_artists._id IN (${ids.joinToString(",") { "?" }})", ids.map(Long::toString).toTypedArray(),
            limit = Protocol.PAGE_SIZE) { cursor -> buildMap {
            while (cursor.moveToNext()) {
                check(size < Protocol.PAGE_SIZE)
                put(cursor.getLong(0), cursor.getString(1).orEmpty())
            }
        } }
    }
    private fun albumExists(id: Long): Boolean = query("albums", arrayOf("albums._id"),
        "albums._id=?", arrayOf(id.toString()), limit = 1) { it.moveToFirst() }
    private fun containsTrack(albumId: Long, trackId: Long = 0): Boolean =
        query("albums/$albumId/files", arrayOf("folder_files._id"),
            if (trackId > 0) "folder_files._id=?" else null,
            if (trackId > 0) arrayOf(trackId.toString()) else null, limit = 1) { it.moveToFirst() }

    private fun play(request: Protocol.Request): Protocol.Result {
        if (!albumExists(request.albumId) || !containsTrack(request.albumId, request.trackId))
            return Protocol.Result(request, Protocol.Stage.NOT_FOUND)
        val trackId = if (request.trackId > 0) request.trackId else query("albums/${request.albumId}/files",
            arrayOf("folder_files._id"), sort = "COALESCE(track_number,10000), folder_files._id", limit = 1) {
            if (it.moveToFirst()) it.getLong(0) else 0
        }
        if (trackId <= 0) return Protocol.Result(request, Protocol.Stage.NOT_FOUND)
        val mediaId = "/albums/${request.albumId}/files/$trackId"
        val issuedAt = System.currentTimeMillis()
        val issuedElapsed = SystemClock.elapsedRealtime()
        val observed = LibraryPlayback.request(context, mediaId) { controller ->
            val deadline = SystemClock.elapsedRealtime() + 5_000
            var matched = false
            while (!matched && SystemClock.elapsedRealtime() < deadline) {
                val snapshot = PowerampRepository.read(context)
                val realId = PowerampRepository.readArtReference(context).realId
                val session = controller.playbackState
                matched = Protocol.hasPlaybackEvidence(request, realId, snapshot.trackReportedAt, issuedAt,
                    session?.state == android.media.session.PlaybackState.STATE_PLAYING,
                    session?.lastPositionUpdateTime ?: -1, issuedElapsed) && containsTrack(request.albumId, realId)
                if (!matched) Thread.sleep(100)
            }
            matched
        }
        return Protocol.Result(request, when (observed) {
            true -> Protocol.Stage.OBSERVED
            false -> Protocol.Stage.REQUESTED
            null -> Protocol.Stage.ERROR
        })
    }
}
