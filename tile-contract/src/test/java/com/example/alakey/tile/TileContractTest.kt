package com.example.alakey.tile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TileContractTest {
    private val sample = TileContract.NowPlaying(
        show = "This Week in Tech",
        title = "TWiT 1005: Leo's μPodcast 🎧",
        isPlaying = true,
        isBuffering = false,
        positionMs = 68_249,
        durationMs = 9_679_000,
        timestampMs = 1_726_000_000_000,
    )

    @Test
    fun `round trips every field`() {
        assertEquals(sample, TileContract.decode(TileContract.encode(sample)))
    }

    @Test
    fun `round trips empty metadata`() {
        val empty = sample.copy(show = "", title = "")
        assertEquals(empty, TileContract.decode(TileContract.encode(empty)))
    }

    @Test
    fun `timestamp makes frames unique so the data layer never dedupes a push`() {
        val a = TileContract.encode(sample)
        val b = TileContract.encode(sample.copy(timestampMs = sample.timestampMs + 1))
        assertNotEquals(a, b)
    }

    @Test
    fun `rejects null empty and truncated frames`() {
        assertNull(TileContract.decode(null))
        assertNull(TileContract.decode(""))
        assertNull(TileContract.decode(TileContract.encode(sample).substringBeforeLast("\u001F")))
    }

    @Test
    fun `rejects unknown version`() {
        val encoded = TileContract.encode(sample)
        val raw = "99" + encoded.substring(1) // version "1" -> "99", frames intact
        assertNull(TileContract.decode(raw))
    }

    @Test
    fun `rejects corrupt numbers and booleans`() {
        val f = TileContract.encode(sample).split("\u001F").toMutableList()
        f[5] = "not-a-long"
        assertNull(TileContract.decode(f.joinToString("\u001F")))
        f[5] = "1"
        f[3] = "yes"
        assertNull(TileContract.decode(f.joinToString("\u001F")))
    }
}
