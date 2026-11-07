package com.example.alakey.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * M69: queue positions are ordinals computed from the queue's own data —
 * never from the clock. Same-millisecond ties, clock-step reordering and the
 * ~1.7e12 timestamp base all become impossible by construction.
 */
class QueueOrdersTest {

    @Test
    fun appendLandsAfterEveryExistingEntry() {
        assertEquals(10L, QueueOrders.append(listOf(5L, 3L, 9L)))
    }

    @Test
    fun appendOnEmptyQueueSeedsAtOne() {
        assertEquals(1L, QueueOrders.append(emptyList()))
    }

    @Test
    fun playNextLandsBeforeEveryExistingEntry() {
        assertEquals(2L, QueueOrders.playNext(listOf(5L, 3L, 9L)))
    }

    @Test
    fun playNextOnEmptyQueueSeedsAtMinusOne() {
        assertEquals(-1L, QueueOrders.playNext(emptyList()))
    }

    @Test
    fun timestampEraOrdersKeepWorking() {
        // Existing installs carry wall-clock-era orders (~1.7e12): append must
        // still land after them, play-next still ahead of them.
        val legacy = listOf(1_700_000_000_000L, 1_700_000_000_500L)
        assertEquals(1_700_000_000_501L, QueueOrders.append(legacy))
        assertEquals(1_699_999_999_999L, QueueOrders.playNext(legacy))
    }
}
