package com.powerampremote.mobile.communication

import org.junit.Assert.*
import org.junit.Test

class LibraryProtocolTest {
    private val id = "12345678-1234-1234-1234-123456789abc"
    private val token = "87654321-1234-1234-1234-123456789abc"
    private fun request(action: LibraryProtocol.Action = LibraryProtocol.Action.ALBUMS) =
        LibraryProtocol.Request(id, action, if (action == LibraryProtocol.Action.ALBUMS) 0 else 1001,
            if (action == LibraryProtocol.Action.PLAY_TRACK) 2001 else 0)

    @Test fun requestsRoundTripWithoutQueriesOrUris() {
        for (action in LibraryProtocol.Action.entries) {
            val request = request(action)
            assertEquals(request, LibraryProtocol.decode(LibraryProtocol.encode(request)))
        }
        val page = request().copy(cursor = token)
        assertEquals(page, LibraryProtocol.decode(LibraryProtocol.encode(page)))
    }
    @Test fun rejectsInvalidIdsCursorsAndActionCombinations() {
        val invalid = listOf(request().copy(id = "bad"), request().copy(albumId = 1),
            request().copy(cursor = "content://external"), request().copy(cursor = "SELECT *"),
            request(LibraryProtocol.Action.TRACKS).copy(albumId = -1),
            request(LibraryProtocol.Action.TRACKS).copy(trackId = 1),
            request(LibraryProtocol.Action.PLAY_ALBUM).copy(cursor = token),
            request(LibraryProtocol.Action.PLAY_TRACK).copy(trackId = 0))
        for (value in invalid) assertNull(LibraryProtocol.decode(LibraryProtocol.encode(value)))
    }
    @Test fun boundedUnicodePageAndEmptyPageRoundTrip() {
        val entries = List(LibraryProtocol.PAGE_SIZE) { LibraryProtocol.Item(it.toLong() + 1,
            "Álbum 音".repeat(30).take(LibraryProtocol.TEXT_LIMIT), "Artista".repeat(30).take(LibraryProtocol.TEXT_LIMIT)) }
        val page = LibraryProtocol.Result(request(), LibraryProtocol.Stage.PAGE, entries, token)
        val bytes = LibraryProtocol.encodeResult(page)
        assertTrue(bytes.size <= LibraryProtocol.MAX_BYTES)
        assertEquals(page, LibraryProtocol.decodeResult(bytes))
        val empty = page.copy(items = emptyList(), next = "")
        assertEquals(empty, LibraryProtocol.decodeResult(LibraryProtocol.encodeResult(empty)))
    }
    @Test fun homonymousAlbumsRemainDistinctById() {
        val page = LibraryProtocol.Result(request(), LibraryProtocol.Stage.PAGE,
            listOf(LibraryProtocol.Item(1, "Mesmo nome", "Artista A"), LibraryProtocol.Item(2, "Mesmo nome", "Artista B")))
        assertEquals(page, LibraryProtocol.decodeResult(LibraryProtocol.encodeResult(page)))
        assertNull(LibraryProtocol.decodeResult(LibraryProtocol.encodeResult(page.copy(items = page.items + page.items.first()))))
    }
    @Test fun rejectsLargePagesTextsAndLoopingTokens() {
        val page = LibraryProtocol.Result(request(), LibraryProtocol.Stage.PAGE,
            List(LibraryProtocol.PAGE_SIZE + 1) { LibraryProtocol.Item(it.toLong() + 1, "a") })
        assertNull(LibraryProtocol.decodeResult(LibraryProtocol.encodeResult(page)))
        assertNull(LibraryProtocol.decodeResult(LibraryProtocol.encodeResult(page.copy(
            items = listOf(LibraryProtocol.Item(1, "x".repeat(LibraryProtocol.TEXT_LIMIT + 1)))))))
        val full = page.copy(request = request().copy(cursor = token), items = page.items.take(LibraryProtocol.PAGE_SIZE), next = token)
        assertNull(LibraryProtocol.decodeResult(LibraryProtocol.encodeResult(full)))
        assertNull(LibraryProtocol.decodeResult(ByteArray(LibraryProtocol.MAX_BYTES + 1)))
    }
    @Test fun rejectsContradictoryResultsAndUnprovenPlayback() {
        val page = LibraryProtocol.Result(request(), LibraryProtocol.Stage.PAGE, listOf(LibraryProtocol.Item(1, "a")))
        assertNull(LibraryProtocol.decodeResult(LibraryProtocol.encodeResult(page.copy(next = token))))
        for (stage in listOf(LibraryProtocol.Stage.OBSERVED, LibraryProtocol.Stage.REQUESTED))
            assertNull(LibraryProtocol.decodeResult(LibraryProtocol.encodeResult(page.copy(stage = stage, items = emptyList()))))
        assertNull(LibraryProtocol.decodeResult(LibraryProtocol.encodeResult(page.copy(request = request(LibraryProtocol.Action.PLAY_ALBUM)))))
        assertNull(LibraryProtocol.decodeResult(LibraryProtocol.encodeResult(page.copy(stage = LibraryProtocol.Stage.DENIED))))
    }
    @Test fun errorsAndPlaybackResultsRoundTripWithOriginalRequest() {
        for (stage in LibraryProtocol.Stage.entries.filter { it != LibraryProtocol.Stage.PAGE && it != LibraryProtocol.Stage.EXPIRED }) {
            val result = LibraryProtocol.Result(request(LibraryProtocol.Action.PLAY_TRACK), stage)
            assertEquals(result, LibraryProtocol.decodeResult(LibraryProtocol.encodeResult(result)))
        }
        val expired = LibraryProtocol.Result(request().copy(cursor = token), LibraryProtocol.Stage.EXPIRED)
        assertEquals(expired, LibraryProtocol.decodeResult(LibraryProtocol.encodeResult(expired)))
    }
    @Test fun rejectsTruncationTrailingAndOtherProtocolMagic() {
        val bytes = LibraryProtocol.encodeResult(LibraryProtocol.Result(request(), LibraryProtocol.Stage.PAGE))
        assertNull(LibraryProtocol.decodeResult(bytes.copyOf(bytes.size - 1)))
        assertNull(LibraryProtocol.decodeResult(bytes + byteArrayOf(0)))
        assertNull(LibraryProtocol.decodeResult(bytes.copyOf().apply { this[0] = 0 }))
        val command = LibraryProtocol.encode(request())
        assertNull(LibraryProtocol.decode(command + byteArrayOf(0)))
        assertNull(LibraryProtocol.decode(command.copyOf(10)))
    }
    @Test fun trackChangeCanConfirmWithFreshSessionWithoutNewStatusBroadcast() {
        val request = request(LibraryProtocol.Action.PLAY_TRACK)
        assertTrue(LibraryProtocol.hasPlaybackEvidence(request, 2001, 1200, 1100, true, 510, 500))
        assertFalse(LibraryProtocol.hasPlaybackEvidence(request, 2001, 1099, 1100, true, 510, 500))
        assertFalse(LibraryProtocol.hasPlaybackEvidence(request, 2001, 1200, 1100, true, 499, 500))
        assertFalse(LibraryProtocol.hasPlaybackEvidence(request, 2001, 1200, 1100, false, 510, 500))
        assertFalse(LibraryProtocol.hasPlaybackEvidence(request, 2002, 1200, 1100, true, 510, 500))
        assertFalse(LibraryProtocol.hasPlaybackEvidence(request(), 2001, 1200, 1100, true, 510, 500))
    }
}
