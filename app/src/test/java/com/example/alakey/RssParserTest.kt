package com.example.alakey

import com.example.alakey.data.RssParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RssParserTest {

    @Test
    fun `parse reads rss item with channel artwork fallback and duration`() {
        val episodes = RssParser.parse(
            """
            <rss xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
                <channel>
                    <title>Example Show</title>
                    <itunes:image href="https://example.com/show.jpg" />
                    <item>
                        <title>Episode One</title>
                        <description><![CDATA[<p>Hello world</p>]]></description>
                        <guid>episode-1</guid>
                        <pubDate>Mon, 01 Jan 2024 10:00:00 +0000</pubDate>
                        <itunes:duration>01:02:03</itunes:duration>
                        <enclosure url="https://example.com/episode.mp3" type="audio/mpeg" />
                    </item>
                </channel>
            </rss>
            """.trimIndent(),
            FEED_URL
        )

        assertEquals(1, episodes.size)
        assertEquals("Example Show", episodes[0].title)
        assertEquals("Episode One", episodes[0].episodeTitle)
        assertEquals("https://example.com/show.jpg", episodes[0].imageUrl)
        assertEquals("https://example.com/episode.mp3", episodes[0].audioUrl)
        assertEquals(3_723_000L, episodes[0].duration)
        assertEquals("$FEED_URL/episode-1", episodes[0].id)
    }

    @Test
    fun `parse reads atom entry with summary and published date`() {
        val episodes = RssParser.parse(
            """
            <feed>
                <title>Atom Show</title>
                <entry>
                    <title>Atom Episode</title>
                    <summary>Short summary</summary>
                    <published>2024-01-01T10:00:00Z</published>
                    <link rel="enclosure" href="https://example.com/atom.mp3" />
                </entry>
            </feed>
            """.trimIndent(),
            FEED_URL
        )

        assertEquals(1, episodes.size)
        assertEquals("Atom Show", episodes[0].title)
        assertEquals("Atom Episode", episodes[0].episodeTitle)
        assertEquals("Short summary", episodes[0].description)
        assertEquals("2024-01-01T10:00:00Z", episodes[0].pubDate)
    }

    @Test
    fun `parse skips entries without audio url`() {
        val episodes = RssParser.parse(
            """
            <rss>
                <channel>
                    <title>Example Show</title>
                    <item><title>Text Only</title></item>
                </channel>
            </rss>
            """.trimIndent(),
            FEED_URL
        )

        assertTrue(episodes.isEmpty())
    }

    @Test
    fun `parse returns empty list for malformed xml`() {
        assertTrue(RssParser.parse("<rss><channel>", FEED_URL).isEmpty())
    }

    private companion object {
        const val FEED_URL = "https://example.com/feed.xml"
    }
}
