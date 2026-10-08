package com.powerampremote.mobile.communication

import org.junit.Assert.*
import org.junit.Test

/** Verifica o contrato e rejeita dados que não devem provocar resposta. */
class TestProtocolTest {
    private val id = "550e8400-e29b-41d4-a716-446655440000"

    @Test fun preservesCorrelationId() {
        assertEquals(id, TestProtocol.decode(TestProtocol.encode(id)))
    }
    @Test fun rejectsUnsupportedVersion() {
        assertNull(TestProtocol.decode("v2:$id".toByteArray()))
    }
    @Test fun rejectsTruncatedOrOversizedPayload() {
        assertNull(TestProtocol.decode("v1:$id-extra".toByteArray()))
        assertNull(TestProtocol.decode(byteArrayOf()))
    }
    @Test fun rejectsMalformedIdAndInvalidUtf8() {
        assertNull(TestProtocol.decode("v1:xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx".toByteArray()))
        assertNull(TestProtocol.decode(ByteArray(39) { 0xff.toByte() }))
    }
    @Test(expected = IllegalArgumentException::class)
    fun refusesArbitraryOutgoingCommand() { TestProtocol.encode("play") }
}
