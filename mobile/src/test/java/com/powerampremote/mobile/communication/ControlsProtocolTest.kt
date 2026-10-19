package com.powerampremote.mobile.communication

import org.junit.Assert.*
import org.junit.Test

class ControlsProtocolTest {
    private val id = "12345678-1234-1234-1234-123456789abc"
    @Test fun allCommandsRoundTrip() {
        for (action in ControlsProtocol.Action.entries) {
            val request = ControlsProtocol.Request(id, action)
            assertEquals(request, ControlsProtocol.decode(ControlsProtocol.encode(request)))
        }
    }
    @Test fun allResultsAndUnknownValuesRoundTrip() {
        for (stage in ControlsProtocol.Stage.entries) {
            for (snapshot in listOf(ControlsProtocol.Snapshot(), ControlsProtocol.Snapshot(4, 5, 100, 7, 0, 15, false, 200))) {
                val result = ControlsProtocol.Result(ControlsProtocol.Request(id, ControlsProtocol.Action.QUERY), stage, snapshot)
                assertEquals(result, ControlsProtocol.decodeResult(ControlsProtocol.encodeResult(result)))
            }
        }
    }
    @Test fun rejectsUnknownCommandsAndWrongVersion() {
        for (text in listOf("v5|$id|SET_VOLUME|100", "v5|$id|STOP", "v4|$id|QUERY", "v5|bad|QUERY", ""))
            assertNull(ControlsProtocol.decode(text.toByteArray()))
    }
    @Test fun correlatedPositionRoundTripWithoutDependingOnClockDifference() {
        val report = ControlsProtocol.Position(7, 100, 12000, 60000, 200)
        val result = ControlsProtocol.Result(ControlsProtocol.Request(id, ControlsProtocol.Action.QUERY),
            ControlsProtocol.Stage.READ, ControlsProtocol.Snapshot(), report)
        assertEquals(result, ControlsProtocol.decodeResult(ControlsProtocol.encodeResult(result)))
        assertTrue(ControlsProtocol.validPosition(report.copy(positionAt = Long.MAX_VALUE)))
    }
    @Test fun rejectsInvalidPositionAndPositionInMutatingCommand() {
        val report = ControlsProtocol.Position(7, 100, 12000, 60000, 200)
        assertFalse(ControlsProtocol.validPosition(report.copy(trackId = -1)))
        assertFalse(ControlsProtocol.validPosition(report.copy(positionMs = -1)))
        assertFalse(ControlsProtocol.validPosition(report.copy(durationMs = 0)))
        val result = ControlsProtocol.Result(ControlsProtocol.Request(id, ControlsProtocol.Action.MEDIA_UP),
            ControlsProtocol.Stage.CONFIRMED, ControlsProtocol.Snapshot(), report)
        assertNull(ControlsProtocol.decodeResult(ControlsProtocol.encodeResult(result)))
    }
    @Test fun rejectsCorruptOrTrailingResult() {
        val result = ControlsProtocol.Result(ControlsProtocol.Request(id, ControlsProtocol.Action.QUERY),
            ControlsProtocol.Stage.READ, ControlsProtocol.Snapshot())
        val encoded = ControlsProtocol.encodeResult(result)
        assertNull(ControlsProtocol.decodeResult(encoded.copyOf(encoded.size - 1)))
        assertNull(ControlsProtocol.decodeResult(encoded + byteArrayOf(0)))
        assertNull(ControlsProtocol.decodeResult(encoded.copyOf().apply { this[0] = 0 }))
    }
    @Test fun rejectsInvalidModeAndVolumeRanges() {
        assertFalse(ControlsProtocol.valid(ControlsProtocol.Snapshot(shuffle = 5)))
        assertFalse(ControlsProtocol.valid(ControlsProtocol.Snapshot(repeat = 6)))
        assertFalse(ControlsProtocol.valid(ControlsProtocol.Snapshot(0, 0, 0)))
        assertFalse(ControlsProtocol.valid(ControlsProtocol.Snapshot(volume = 16, maximum = 15, volumeAt = 100)))
        assertFalse(ControlsProtocol.valid(ControlsProtocol.Snapshot(volume = 0, minimum = 2, maximum = 15, volumeAt = 100)))
        assertTrue(ControlsProtocol.valid(ControlsProtocol.Snapshot(volume = 0, maximum = 15, volumeAt = 100)))
    }
    @Test fun estimateStopsAfterThirtySecondsAndAtTrackEnd() {
        assertEquals(12000L, ControlsProtocol.estimatedPosition(10000, 60000, 2000))
        assertEquals(40000L, ControlsProtocol.estimatedPosition(10000, 60000, 120000))
        assertEquals(60000L, ControlsProtocol.estimatedPosition(59000, 60000, 2000))
    }
    @Test fun estimateDoesNotInventUnknownPositionOrGoBackwards() {
        assertEquals(-1L, ControlsProtocol.estimatedPosition(-1, 60000, 2000))
        assertEquals(10000L, ControlsProtocol.estimatedPosition(10000, -1, 2000))
        assertEquals(10000L, ControlsProtocol.estimatedPosition(10000, 60000, -2000))
        assertEquals(Long.MAX_VALUE, ControlsProtocol.estimatedPosition(Long.MAX_VALUE, Long.MAX_VALUE, 2000))
    }
}
