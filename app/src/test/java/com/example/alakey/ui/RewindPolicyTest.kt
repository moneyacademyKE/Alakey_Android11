package com.example.alakey.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * M4 pin: the smart-resume rewind intent is emitted ONLY from a known
 * position. The pre-fix code coalesced a mid-rebuild controller to 0 and
 * restarted long-paused episodes on the fresh controller.
 */
class RewindPolicyTest {

    private val rewindMs = 3_000L

    @Test
    fun `mid-rebuild (unknown position) emits no rewind intent`() {
        assertNull(rewindSeekTarget(null, rewindMs))
    }

    @Test
    fun `a known position rewinds by the fixed amount`() {
        assertEquals(120_000L - rewindMs, rewindSeekTarget(120_000L, rewindMs))
    }

    @Test
    fun `a position inside the rewind window clamps to zero, never negative`() {
        assertEquals(0L, rewindSeekTarget(1_000L, rewindMs))
    }
}
