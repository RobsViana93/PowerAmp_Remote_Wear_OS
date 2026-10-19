package com.powerampremote.mobile.communication

import org.junit.Assert.*
import org.junit.Test

class EqualizerProtocolTest {
    private val id = "12345678-1234-1234-1234-123456789abc"
    private fun request(action: EqualizerProtocol.Action = EqualizerProtocol.Action.ENABLE) = EqualizerProtocol.Request(id, action)

    @Test fun closedRequestsRoundTrip() {
        for (action in EqualizerProtocol.Action.entries) {
            val request = request(action)
            assertEquals(request, EqualizerProtocol.decode(EqualizerProtocol.encode(request)))
        }
        for (text in listOf("v5|$id|ENABLE", "v6|bad|READ", "v6|$id|PRESET", "v6|$id|ENABLE|true", ""))
            assertNull(EqualizerProtocol.decode(text.toByteArray()))
    }
    @Test fun validResultsRoundTrip() {
        val results = listOf(
            EqualizerProtocol.Result(request(EqualizerProtocol.Action.READ), EqualizerProtocol.Stage.READ, EqualizerProtocol.Snapshot()),
            EqualizerProtocol.Result(request(), EqualizerProtocol.Stage.OBSERVED, EqualizerProtocol.Snapshot(EqualizerProtocol.State.ON, 100)),
            EqualizerProtocol.Result(request(EqualizerProtocol.Action.DISABLE), EqualizerProtocol.Stage.REQUESTED, EqualizerProtocol.Snapshot(EqualizerProtocol.State.ON, 100)),
            EqualizerProtocol.Result(request(), EqualizerProtocol.Stage.ERROR, EqualizerProtocol.Snapshot()),
            EqualizerProtocol.Result(request(), EqualizerProtocol.Stage.UNAVAILABLE, EqualizerProtocol.Snapshot()))
        for (result in results) assertEquals(result, EqualizerProtocol.decodeResult(EqualizerProtocol.encodeResult(result)))
    }
    @Test fun rejectsCorruptTruncatedTrailingAndOversizedResults() {
        val bytes = EqualizerProtocol.encodeResult(EqualizerProtocol.Result(request(), EqualizerProtocol.Stage.REQUESTED, EqualizerProtocol.Snapshot()))
        assertNull(EqualizerProtocol.decodeResult(bytes.copyOf(bytes.size - 1)))
        assertNull(EqualizerProtocol.decodeResult(bytes + byteArrayOf(0)))
        assertNull(EqualizerProtocol.decodeResult(bytes.copyOf().apply { this[0] = 0 }))
        assertNull(EqualizerProtocol.decodeResult(ByteArray(101)))
    }
    @Test fun rejectsImpossibleStatesAndStageCombinations() {
        for ((stage, action, snapshot) in listOf(
            Triple(EqualizerProtocol.Stage.READ, EqualizerProtocol.Action.ENABLE, EqualizerProtocol.Snapshot()),
            Triple(EqualizerProtocol.Stage.REQUESTED, EqualizerProtocol.Action.READ, EqualizerProtocol.Snapshot()),
            Triple(EqualizerProtocol.Stage.OBSERVED, EqualizerProtocol.Action.READ, EqualizerProtocol.Snapshot()),
            Triple(EqualizerProtocol.Stage.OBSERVED, EqualizerProtocol.Action.ENABLE, EqualizerProtocol.Snapshot(EqualizerProtocol.State.OFF, 100)),
            Triple(EqualizerProtocol.Stage.REQUESTED, EqualizerProtocol.Action.ENABLE, EqualizerProtocol.Snapshot(EqualizerProtocol.State.ON, 0)),
            Triple(EqualizerProtocol.Stage.REQUESTED, EqualizerProtocol.Action.ENABLE, EqualizerProtocol.Snapshot(EqualizerProtocol.State.UNKNOWN, 100)))) {
            assertNull(EqualizerProtocol.decodeResult(EqualizerProtocol.encodeResult(EqualizerProtocol.Result(request(action), stage, snapshot))))
        }
    }
    @Test fun missingOldFutureAndInvalidEventsDoNotChangeState() {
        val old = EqualizerProtocol.Snapshot(EqualizerProtocol.State.OFF, 100)
        assertEquals(old, EqualizerProtocol.accept(old, null, 200, 300))
        assertEquals(old, EqualizerProtocol.accept(old, true, 99, 300))
        assertEquals(old, EqualizerProtocol.accept(old, true, 301, 300))
        assertEquals(old, EqualizerProtocol.accept(old, true, 0, 300))
    }
    @Test fun freshExplicitEventUpdatesWithoutInferringFromRequest() {
        val updated = EqualizerProtocol.accept(EqualizerProtocol.Snapshot(), true, 200, 300)
        assertEquals(EqualizerProtocol.Snapshot(EqualizerProtocol.State.ON, 200), updated)
        assertTrue(EqualizerProtocol.observed(request(), updated, 150))
        assertFalse(EqualizerProtocol.observed(request(), updated, 201))
        assertFalse(EqualizerProtocol.observed(request(EqualizerProtocol.Action.DISABLE), updated, 150))
        assertFalse(EqualizerProtocol.observed(request(EqualizerProtocol.Action.READ), updated, 150))
    }
}
