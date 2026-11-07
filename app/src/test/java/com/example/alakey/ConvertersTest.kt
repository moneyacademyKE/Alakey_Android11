package com.example.alakey

import com.example.alakey.data.Chapter
import com.example.alakey.data.Converters
import com.example.alakey.data.PodcastPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** L12: corrupt rows previously crashed every getAllPodcasts emission. */
class ConvertersTest {
    private val converters = Converters()

    @Test
    fun `toChapters returns empty list for stored null string`() {
        assertEquals(emptyList<Chapter>(), converters.toChapters("null"))
    }

    @Test
    fun `toChapters returns empty list for blank string`() {
        assertEquals(emptyList<Chapter>(), converters.toChapters("   "))
    }

    @Test
    fun `toChapters returns empty list for malformed json`() {
        assertEquals(emptyList<Chapter>(), converters.toChapters("{not json"))
    }

    @Test
    fun `chapters roundtrip through the converters`() {
        val chapters = listOf(Chapter(start = 0L, title = "Intro"), Chapter(start = 90_000L, title = "Chapter 2", link = "https://example.com/2"))
        val encoded = converters.fromChapters(chapters)
        assertEquals(chapters, converters.toChapters(encoded))
    }

    @Test
    fun `toPalette returns null for blank and null strings`() {
        assertNull(converters.toPalette(""))
        assertNull(converters.toPalette("null"))
    }

    @Test
    fun `toPalette returns null for malformed json`() {
        assertNull(converters.toPalette("{not json"))
    }

    @Test
    fun `palette roundtrips through the converters`() {
        val palette = PodcastPalette(dominant = 1, vibrant = 2, muted = 3)
        assertEquals(palette, converters.toPalette(converters.fromPalette(palette)))
    }
}
