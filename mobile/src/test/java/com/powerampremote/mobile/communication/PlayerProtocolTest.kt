package com.powerampremote.mobile.communication

import org.junit.Assert.*
import org.junit.Test
import com.powerampremote.mobile.communication.PlayerProtocol.Command
import com.powerampremote.mobile.communication.PlayerProtocol.Snapshot
import com.powerampremote.mobile.communication.PlayerProtocol.State

class PlayerProtocolTest {
    private val id = "550e8400-e29b-41d4-a716-446655440000"

    @Test fun requestRoundTripPreservesCommandAndId() {
        for (command in Command.entries) {
            val request = PlayerProtocol.Request(id, command)
            assertEquals(request, PlayerProtocol.decodeRequest(PlayerProtocol.encodeRequest(request)))
        }
    }

    @Test fun rejectsArbitraryCommandsAndOtherVersions() {
        assertNull(PlayerProtocol.decodeRequest("v2|$id|DELETE".toByteArray()))
        assertNull(PlayerProtocol.decodeRequest("v1|$id|PLAY".toByteArray()))
        assertNull(PlayerProtocol.decodeRequest("v2|bad-id|PLAY".toByteArray()))
    }

    @Test fun resultPreservesCorrelationAndObservedState() {
        val result = PlayerProtocol.Result(PlayerProtocol.Request(id, Command.PAUSE),
            PlayerProtocol.Stage.OBSERVED, Snapshot(State.PAUSED, 100, 5, 90))
        assertEquals(result, PlayerProtocol.decodeResult(PlayerProtocol.encodeResult(result)))
    }

    @Test fun rejectsMalformedResultsAndUnboundedPayloads() {
        assertNull(PlayerProtocol.decodeResult("v2|$id|PLAY|SUCCESS|PLAYING|100|4|100".toByteArray()))
        assertNull(PlayerProtocol.decodeSnapshot("v2|PLAYING|-10|4|0".toByteArray()))
        assertNull(PlayerProtocol.decodeRequest(ByteArray(257) { 65 }))
        assertNull(PlayerProtocol.decodeRequest(ByteArray(40) { 0xff.toByte() }))
    }

    @Test fun oldPlayingStateDoesNotConfirmNewPlay() {
        val before = Snapshot(State.PLAYING, 100)
        assertFalse(PlayerProtocol.hasEvidence(Command.PLAY, before, before, 101))
        // Mesmo um timestamp anterior adiantado não pode reutilizar o mesmo evento.
        assertFalse(PlayerProtocol.hasEvidence(Command.PLAY, before, before, 99))
    }

    @Test fun freshWrongStateDoesNotConfirmPlay() {
        assertFalse(PlayerProtocol.hasEvidence(Command.PLAY, Snapshot(), Snapshot(State.PAUSED, 200), 150))
        assertTrue(PlayerProtocol.hasEvidence(Command.PLAY, Snapshot(), Snapshot(State.PLAYING, 200), 150))
    }

    @Test fun nextNeedsNewTrackEventAndKnownDistinctIds() {
        val before = Snapshot(State.PLAYING, 100, 7, 100)
        assertFalse(PlayerProtocol.hasEvidence(Command.NEXT, before, before.copy(trackReportedAt = 200), 150))
        assertFalse(PlayerProtocol.hasEvidence(Command.NEXT, before, before.copy(trackId = 8), 150))
        assertFalse(PlayerProtocol.hasEvidence(Command.NEXT, Snapshot(), before.copy(trackReportedAt = 200), 150))
        assertTrue(PlayerProtocol.hasEvidence(Command.NEXT, before, before.copy(trackId = 8, trackReportedAt = 200), 150))
    }

    @Test fun pauseAcceptsFreshPausedOrStoppedButNotPlaying() {
        assertTrue(PlayerProtocol.hasEvidence(Command.PAUSE, Snapshot(), Snapshot(State.STOPPED, 200), 150))
        assertTrue(PlayerProtocol.hasEvidence(Command.PAUSE, Snapshot(), Snapshot(State.PAUSED, 200), 150))
        assertFalse(PlayerProtocol.hasEvidence(Command.PAUSE, Snapshot(), Snapshot(State.PLAYING, 200), 150))
    }

    @Test fun cachedUnknownSnapshotRemainsUnknown() {
        val empty = Snapshot()
        assertEquals(empty, PlayerProtocol.decodeSnapshot(PlayerProtocol.encodeSnapshot(empty)))
        assertFalse(PlayerProtocol.hasEvidence(Command.PLAY, empty, empty, 100))
    }

    @Test fun apiCodesMatchOfficialPowerampCommands() {
        assertEquals(3, Command.PLAY.apiCode)
        assertEquals(2, Command.PAUSE.apiCode)
        assertEquals(4, Command.NEXT.apiCode)
        assertEquals(5, Command.PREVIOUS.apiCode)
    }
}
