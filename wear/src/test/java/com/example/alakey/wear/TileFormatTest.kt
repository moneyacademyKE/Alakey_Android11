package com.example.alakey.wear

import com.example.alakey.tile.TileContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** M11: wear module had no test sourceset at all. These pin the tile's pure presentation logic. */
class TileFormatTest {

    private fun frame(timestampMs: Long, playing: Boolean = true) =
        TileContract.NowPlaying("Show", "Episode", playing, false, 0, 0, timestampMs)

    @Test
    fun `glyph reflects buffering then playing then paused`() {
        assertEquals("◌", TileFormat.glyph(frame(1).copy(isBuffering = true)))
        assertEquals("❚❚", TileFormat.glyph(frame(1)))
        assertEquals("▶", TileFormat.glyph(frame(1, playing = false)))
    }

    @Test
    fun `mmss formats minutes and zero-padded seconds`() {
        assertEquals("0:00", TileFormat.mmss(0))
        assertEquals("0:59", TileFormat.mmss(59_000))
        assertEquals("1:05", TileFormat.mmss(65_000))
        assertEquals("61:00", TileFormat.mmss(3_660_000))
    }

    @Test
    fun `mmss clamps negative input to zero`() {
        assertEquals("0:00", TileFormat.mmss(-5_000))
    }

    @Test
    fun `mmss output is locale-independent`() {
        val default = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale("ar", "SA"))
            assertEquals("12:03", TileFormat.mmss(723_000))
        } finally {
            java.util.Locale.setDefault(default)
        }
    }

    @Test
    fun `a frame with zero timestamp is stale - no frame yet`() {
        assertTrue(TileFormat.isStale(frame(0), nowMs = 1_000_000))
    }

    @Test
    fun `a fresh frame is live and an aged frame is stale`() {
        val now = System.currentTimeMillis()
        assertFalse(TileFormat.isStale(frame(now), nowMs = now))
        assertFalse(TileFormat.isStale(frame(now - TileFormat.STALE_MS), nowMs = now)) // exactly at the edge
        assertTrue(TileFormat.isStale(frame(now - TileFormat.STALE_MS - 1), nowMs = now))
    }

    @Test
    fun `a future timestamp is fresh, not negative-stale`() {
        val now = System.currentTimeMillis()
        assertFalse(TileFormat.isStale(frame(now + 60_000), nowMs = now))
    }
}
