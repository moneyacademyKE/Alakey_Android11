package com.example.alakey.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterParserTest {

    @Test
    fun `parses standard podcast namespace chapters json`() {
        val json = """
            {"version": "1.2.0", "chapters": [
              {"startTime": "0:00:00", "title": "Intro"},
              {"startTime": "0:15:30", "title": "Main topic"},
              {"startTime": 5025, "title": "Numeric seconds"}
            ]}
        """.trimIndent()
        val chapters = ChapterParser.parse(json)
        assertEquals(3, chapters.size)
        assertEquals("Intro", chapters[0].title)
        assertEquals(0L, chapters[0].start)
        assertEquals(930_000L, chapters[1].start)
        assertEquals(5_025_000L, chapters[2].start)
    }

    @Test
    fun `sorts out-of-order chapters by start time`() {
        val json = """
            {"chapters": [
              {"startTime": "0:10:00", "title": "Later"},
              {"startTime": "0:01:00", "title": "Earlier"}
            ]}
        """.trimIndent()
        val chapters = ChapterParser.parse(json)
        assertEquals(listOf("Earlier", "Later"), chapters.map { it.title })
    }

    @Test
    fun `fractional seconds parse to millisecond precision`() {
        assertEquals(5025_500L, ChapterParser.timeToMs("5025.5"))
    }

    @Test
    fun `time formats`() {
        assertEquals(3_723_000L, ChapterParser.timeToMs("1:02:03"))
        assertEquals(153_000L, ChapterParser.timeToMs("2:33"))
        assertEquals(42_000L, ChapterParser.timeToMs("42"))
    }

    @Test
    fun `invalid time values return null`() {
        assertNull(ChapterParser.timeToMs(""))
        assertNull(ChapterParser.timeToMs("1:2:3:4"))
        assertNull(ChapterParser.timeToMs("abc"))
        assertNull(ChapterParser.timeToMs("-5"))
    }

    @Test
    fun `empty and malformed json yields no chapters`() {
        assertEquals(emptyList<Chapter>(), ChapterParser.parse(""))
        assertEquals(emptyList<Chapter>(), ChapterParser.parse("<html>not json</html>"))
    }

    @Test
    fun `escaped quotes in titles survive`() {
        val json = """{"chapters": [{"startTime": 10, "title": "He said \"hello\""}]}"""
        val chapters = ChapterParser.parse(json)
        assertEquals("He said \"hello\"", chapters.single().title)
    }
}
