package com.powerampremote.mobile.communication

import org.junit.Assert.*
import org.junit.Test
import com.powerampremote.mobile.communication.NowPlayingProtocol.Snapshot
import com.powerampremote.mobile.communication.NowPlayingProtocol.Update

class NowPlayingProtocolTest {
    private val id = "550e8400-e29b-41d4-a716-446655440000"
    private val song = Snapshot(1, "Canção | \"São João\" 🎵", "Artista á", "Álbum ç", 120_345, 3_000, 100, 100)

    @Test fun roundTripPreservesUnicodeAndTimes() {
        assertEquals(song, NowPlayingProtocol.decode(NowPlayingProtocol.encode(song)))
    }
    @Test fun rejectsInvalidVersionTruncationAndTrailingBytes() {
        val bytes = NowPlayingProtocol.encode(song)
        assertNull(NowPlayingProtocol.decode(bytes.copyOf(bytes.size - 1)))
        assertNull(NowPlayingProtocol.decode(bytes + byteArrayOf(0)))
        bytes[0] = 0
        assertNull(NowPlayingProtocol.decode(bytes))
        assertNull(NowPlayingProtocol.decode(ByteArray(4097)))
    }
    @Test fun textIsBoundedAndDoesNotSplitSurrogatePair() {
        val text = NowPlayingProtocol.cleanText("a".repeat(255) + "🎵" + "more")
        assertEquals(255, text.length)
        assertEquals("a b", NowPlayingProtocol.cleanText(" a\nb "))
    }
    @Test fun newTrackClearsMissingTagsAndOldPosition() {
        val next = NowPlayingProtocol.merge(song, Update(200, true, 2, title = "Nova"))
        assertEquals("Nova", next.title)
        assertEquals("", next.artist)
        assertEquals("", next.album)
        assertEquals(-1L, next.durationMs)
        assertEquals(-1L, next.positionMs)
        assertEquals(0L, next.positionAt)
    }
    @Test fun sameTrackPartialUpdatePreservesTags() {
        val after = NowPlayingProtocol.merge(song, Update(200, false, 1, positionMs = 12_000))
        assertEquals(song.title, after.title)
        assertEquals(song.artist, after.artist)
        assertEquals(song.album, after.album)
        assertEquals(12_000L, after.positionMs)
    }
    @Test fun oldEventCannotOverwriteNewerPositionOrSong() {
        assertEquals(song, NowPlayingProtocol.merge(song, Update(99, true, 2, title = "Velha")))
    }
    @Test fun delayedNewTrackClearsPositionSyncedForPreviousTrack() {
        val oldTrack = song.copy(positionAt = 300, positionMs = 90_000)
        val after = NowPlayingProtocol.merge(oldTrack, Update(200, true, 2, title = "Nova"))
        assertEquals(2L, after.trackId)
        assertEquals("Nova", after.title)
        assertEquals(-1L, after.positionMs)
        val partial = NowPlayingProtocol.merge(oldTrack, Update(200, false, 1, album = "Corrigido", positionMs = 5_000))
        assertEquals("Corrigido", partial.album)
        assertEquals(90_000L, partial.positionMs)
    }
    @Test fun zeroPositionIsKnownAndMissingPositionIsNotZero() {
        assertEquals("0:00", NowPlayingProtocol.formatTime(0))
        assertEquals("--:--", NowPlayingProtocol.formatTime(-1))
        val after = NowPlayingProtocol.merge(song, Update(200, false, positionMs = 0))
        assertEquals(0L, after.positionMs)
        assertEquals(200L, after.positionAt)
    }
    @Test fun millisecondsTakePriorityAndSecondsAreConverted() {
        assertEquals(123_456L, NowPlayingProtocol.durationMs(123_456, 123))
        assertEquals(123_000L, NowPlayingProtocol.durationMs(null, 123))
        assertNull(NowPlayingProtocol.durationMs(null, null))
    }
    @Test fun invalidSecondsDoNotOverflow() {
        assertNull(NowPlayingProtocol.secondsToMs(-1))
        assertNull(NowPlayingProtocol.secondsToMs(Long.MAX_VALUE))
    }
    @Test fun missingTagsRemainUnknownIncludingAfterPersistence() {
        val empty = Snapshot()
        assertEquals(empty, NowPlayingProtocol.decode(NowPlayingProtocol.encode(empty)))
        assertFalse(NowPlayingProtocol.matchesTrack(empty, -1, 0))
    }
    @Test fun metadataFromPreviousTrackIsNotDisplayed() {
        assertFalse(NowPlayingProtocol.matchesTrack(song, 2, 200))
        assertFalse(NowPlayingProtocol.matchesTrack(song, 1, 200))
        assertTrue(NowPlayingProtocol.matchesTrack(song, 1, 100))
    }
    @Test fun syncRequestsAndResultsPreserveCorrelation() {
        assertEquals(id, NowPlayingProtocol.decodeRefresh(NowPlayingProtocol.encodeRefresh(id)))
        for (stage in NowPlayingProtocol.SyncStage.entries) {
            val result = NowPlayingProtocol.SyncResult(id, stage)
            assertEquals(result, NowPlayingProtocol.decodeSyncResult(NowPlayingProtocol.encodeSyncResult(result)))
        }
    }
    @Test fun syncRejectsUnknownStagesAndMalformedIds() {
        assertNull(NowPlayingProtocol.decodeSyncResult("v3:$id|PLAY".toByteArray()))
        assertNull(NowPlayingProtocol.decodeRefresh("v2:$id".toByteArray()))
        assertNull(NowPlayingProtocol.decodeRefresh("v3:invalid".toByteArray()))
    }
    @Test fun longDurationAndRawFileIdAreSupported() {
        assertEquals("65:07", NowPlayingProtocol.formatTime(3_907_000))
        val raw = song.copy(trackId = -2)
        assertEquals(raw, NowPlayingProtocol.decode(NowPlayingProtocol.encode(raw)))
    }
}
