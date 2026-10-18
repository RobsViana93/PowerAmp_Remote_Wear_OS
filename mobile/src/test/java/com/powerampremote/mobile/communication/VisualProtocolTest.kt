package com.powerampremote.mobile.communication

import org.junit.Assert.*
import org.junit.Test

class VisualProtocolTest {
    private val request = VisualProtocol.Seek("12345678-1234-1234-1234-123456789abc", 7, 1000, 25)

    @Test fun seekRoundTrip() { assertEquals(request, VisualProtocol.decode(VisualProtocol.encode(request))) }
    @Test fun resultsRoundTrip() {
        for (stage in VisualProtocol.Stage.entries) {
            val result = VisualProtocol.Result(request.id, stage)
            assertEquals(result, VisualProtocol.decodeResult(VisualProtocol.encodeResult(result)))
        }
    }
    @Test fun rejectsMalformedCommands() {
        for (text in listOf("", "v4|${request.id}|7|1000|-1", "v4|${request.id}|-2|1000|25",
            "v4|${request.id}|7|0|25", "v4|${request.id}|7|1000|2147483648",
            "v4|invalid|7|1000|25", "v4|${request.id}|7|1000|25|extra")) {
            assertNull(text, VisualProtocol.decode(text.toByteArray()))
        }
    }
    @Test fun rejectsOldTrackIncludingReturningToSameId() {
        assertFalse(VisualProtocol.validTarget(request, 8, 1000, 60000))
        assertFalse(VisualProtocol.validTarget(request, 7, 2000, 60000))
        assertTrue(VisualProtocol.validTarget(request, 7, 1000, 60000))
    }
    @Test fun neverSeeksBeyondEndOrUnknownDuration() {
        assertFalse(VisualProtocol.validTarget(request, 7, 1000, -1))
        assertFalse(VisualProtocol.validTarget(request, 7, 1000, 25000))
        assertTrue(VisualProtocol.validTarget(request.copy(seconds = 0), 7, 1000, 60000))
    }
    @Test fun clampsProgressAndKeepsUnknownEmpty() {
        assertEquals(0f, VisualProtocol.fraction(-1, 60000), 0f)
        assertEquals(0f, VisualProtocol.fraction(30, 0), 0f)
        assertEquals(.5f, VisualProtocol.fraction(30000, 60000), 0f)
        assertEquals(1f, VisualProtocol.fraction(Long.MAX_VALUE, 60000), 0f)
    }
    @Test fun rejectsUnknownResultAndWrongVersion() {
        assertNull(VisualProtocol.decodeResult("v4|${request.id}|SUCCESS".toByteArray()))
        assertNull(VisualProtocol.decode(VisualProtocol.encode(request).toString(Charsets.US_ASCII).replace("v4|", "v3|").toByteArray()))
    }
}
