package com.powerampremote.mobile.communication

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PresetProtocolTest {
    private val list = PresetProtocol.Request(UUID.randomUUID().toString(), PresetProtocol.Action.LIST)
    private val apply = PresetProtocol.Request(UUID.randomUUID().toString(), PresetProtocol.Action.APPLY, presetId = 7)
    private fun item(id: Long) = PresetProtocol.Item(id, "Graves — ação 🎵", false)

    @Test fun requestsRoundTripWithoutConfusingCursorAndSelection() {
        for (request in listOf(list, list.copy(afterId = 15), apply))
            assertEquals(request, PresetProtocol.decode(PresetProtocol.encode(request)))
        for (request in listOf(list.copy(afterId = -1), list.copy(presetId = 1),
            apply.copy(presetId = 0), apply.copy(afterId = 3), list.copy(id = "inválido")))
            assertNull(PresetProtocol.decode(PresetProtocol.encode(request)))
    }
    @Test fun unicodePageAndEmptyPageRoundTrip() {
        for (items in listOf(emptyList(), listOf(item(2)))) {
            val result = PresetProtocol.Result(list, PresetProtocol.Stage.PAGE, items)
            assertEquals(result, PresetProtocol.decodeResult(PresetProtocol.encodeResult(result)))
        }
    }
    @Test fun continuationRequiresAFullStrictlyIncreasingPage() {
        val full = PresetProtocol.Result(list, PresetProtocol.Stage.PAGE,
            (1L..PresetProtocol.PAGE_SIZE.toLong()).map(::item), true)
        assertTrue(PresetProtocol.valid(full))
        assertFalse(PresetProtocol.valid(full.copy(items = full.items.dropLast(1))))
        assertFalse(PresetProtocol.valid(full.copy(items = full.items.reversed())))
        assertFalse(PresetProtocol.valid(full.copy(items = listOf(item(1), item(1)), hasMore = false)))
        assertFalse(PresetProtocol.valid(full.copy(request = list.copy(afterId = 1))))
    }
    @Test fun equalNamesHaveIndependentIds() {
        val result = PresetProtocol.Result(list, PresetProtocol.Stage.PAGE, listOf(item(2), item(3)))
        assertEquals(result, PresetProtocol.decodeResult(PresetProtocol.encodeResult(result)))
    }
    @Test fun payloadLimitsRejectOversizedNamesAndItemCounts() {
        val page = PresetProtocol.Result(list, PresetProtocol.Stage.PAGE,
            listOf(item(1).copy(name = "x".repeat(PresetProtocol.TEXT_LIMIT + 1))))
        assertNull(PresetProtocol.decodeResult(PresetProtocol.encodeResult(page)))
        assertFalse(PresetProtocol.valid(page.copy(items = listOf(item(1).copy(name = " ")))))
        assertNull(PresetProtocol.decodeResult(PresetProtocol.encodeResult(page.copy(
            items = (1L..11L).map(::item)))))
        assertNull(PresetProtocol.decodeResult(ByteArray(PresetProtocol.MAX_BYTES + 1)))
    }
    @Test fun stagesCannotInventPagesOrConfirmAList() {
        assertFalse(PresetProtocol.valid(PresetProtocol.Result(apply, PresetProtocol.Stage.PAGE)))
        assertFalse(PresetProtocol.valid(PresetProtocol.Result(list, PresetProtocol.Stage.OBSERVED)))
        assertFalse(PresetProtocol.valid(PresetProtocol.Result(apply, PresetProtocol.Stage.REQUESTED, listOf(item(7)))))
        assertTrue(PresetProtocol.valid(PresetProtocol.Result(apply, PresetProtocol.Stage.REQUESTED)))
        assertTrue(PresetProtocol.valid(PresetProtocol.Result(list, PresetProtocol.Stage.DENIED)))
    }
    @Test fun truncatedAndTrailingPayloadsAreRejected() {
        val request = PresetProtocol.encode(apply)
        val result = PresetProtocol.encodeResult(PresetProtocol.Result(list, PresetProtocol.Stage.PAGE, listOf(item(1))))
        assertNull(PresetProtocol.decode(request + byteArrayOf(0)))
        assertNull(PresetProtocol.decode(request.copyOf(request.size - 1)))
        assertNull(PresetProtocol.decodeResult(result + byteArrayOf(0)))
        assertNull(PresetProtocol.decodeResult(result.copyOf(result.size - 1)))
        assertNull(PresetProtocol.decode(request.copyOf().also { it[0] = 0 }))
    }
    @Test fun observationNeedsMatchingIdAndFreshEvent() {
        assertTrue(PresetProtocol.observed(apply, 7, 100, 100, 101))
        assertFalse(PresetProtocol.observed(apply, 8, 100, 100, 101))
        assertFalse(PresetProtocol.observed(apply, null, 100, 100, 101))
        assertFalse(PresetProtocol.observed(apply, 7, 99, 100, 101))
        assertFalse(PresetProtocol.observed(apply, 7, 102, 100, 101))
        assertFalse(PresetProtocol.observed(list, 7, 100, 100, 101))
    }
}
