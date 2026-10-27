package com.powerampremote.mobile.communication

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class LibraryArtProtocolTest {
    private val request = LibraryArtProtocol.Request(UUID.randomUUID().toString(), 42)

    @Test fun requestRoundTripAndIdValidation() {
        assertTrue(LibraryArtProtocol.valid(request))
        assertEquals(request, LibraryArtProtocol.decode(LibraryArtProtocol.encode(request)))
        assertFalse(LibraryArtProtocol.valid(request.copy(id = "00000000-0000-0000-0000-0000-000000000000")))
        assertNull(LibraryArtProtocol.decode(LibraryArtProtocol.encode(request.copy(id = "bad"))))
        assertNull(LibraryArtProtocol.decode(LibraryArtProtocol.encode(request.copy(albumId = 0))))
    }

    @Test fun resultRoundTripAndSizeLimit() {
        val available = LibraryArtProtocol.Result(request, LibraryArtProtocol.Status.AVAILABLE,
            ByteArray(LibraryArtProtocol.MAX_IMAGE_BYTES) { 1 })
        val decoded = LibraryArtProtocol.decodeResult(LibraryArtProtocol.encodeResult(available))
        assertEquals(request, decoded?.request)
        assertEquals(LibraryArtProtocol.Status.AVAILABLE, decoded?.status)
        assertArrayEquals(available.jpeg, decoded?.jpeg)
        assertNull(LibraryArtProtocol.decodeResult(LibraryArtProtocol.encodeResult(available) + byteArrayOf(1)))
        assertFalse(LibraryArtProtocol.valid(available.copy(jpeg = ByteArray(LibraryArtProtocol.MAX_IMAGE_BYTES + 1))))
    }

    @Test fun responseKeepsRequestIdentityForCorrelation() {
        val reply = LibraryArtProtocol.Result(request, LibraryArtProtocol.Status.MISSING)
        val decoded = LibraryArtProtocol.decodeResult(LibraryArtProtocol.encodeResult(reply))
        assertEquals(request, decoded?.request)
        assertNotEquals(request.copy(id = UUID.randomUUID().toString()), decoded?.request)
        assertNotEquals(request.copy(albumId = 43), decoded?.request)
    }
}
