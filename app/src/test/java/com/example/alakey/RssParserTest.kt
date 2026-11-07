package com.example.alakey

import com.example.alakey.data.EpisodeIds
import com.example.alakey.data.RssParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

    // M14: guid-less identity is a 64-bit SHA-256 of feedUrl|audioUrl —
    // deterministic across processes and collision-safe against INSERT IGNORE.
    @Test
    fun `parse derives stable 64-bit fallback id when guid missing`() {
        val audioUrl = "https://example.com/audio/episode-2.mp3"
        val episodes = RssParser.parse(
            """
            <rss>
                <channel>
                    <title>Example Show</title>
                    <item>
                        <title>Episode Two</title>
                        <enclosure url="$audioUrl" type="audio/mpeg" />
                    </item>
                </channel>
            </rss>
            """.trimIndent(),
            FEED_URL
        )

        assertEquals(1, episodes.size)
        // M51: a PERSISTED identity function must be pinned to a golden value,
        // not recomputed from itself — both sides of the old assertion came
        // from the same oracle, so changing the hash kept every test green
        // while every guid-less episode in every install got a new id
        // (INSERT IGNORE stops matching → re-sync duplicates everything).
        // Golden: SHA-256("https://example.com/feed.xml|https://example.com/audio/episode-2.mp3"), first 8 bytes, %016x.
        assertEquals("d3aa7f6019787d9b", episodes[0].id)
        assertEquals(16, episodes[0].id.length)
        assertEquals("Episode Two", episodes[0].episodeTitle)
    }

    @Test
    fun `guid-less ids do not collide across feeds sharing audio urls`() {
        // The v13 failure mode: two feeds, same audio path — 32-bit hashCode
        // collided often enough to silently drop episodes on INSERT IGNORE.
        val audioUrl = "https://cdn.example.com/shared/episode.mp3"
        val a = RssParser.parse(itemXml(audioUrl), "https://a.example.com/feed.xml")
        val b = RssParser.parse(itemXml(audioUrl), "https://b.example.com/feed.xml")
        assertNotEquals(a[0].id, b[0].id)
    }

    private fun itemXml(audioUrl: String) = """
        <rss><channel><title>Show</title>
            <item><title>Ep</title><enclosure url="$audioUrl" type="audio/mpeg" /></item>
        </channel></rss>
    """.trimIndent()

    @Test
    fun `legacy hash id detection discriminates old and new id shapes`() {
        // v13 rows the migration must rewrite (provable legacy shapes)…
        assertTrue(EpisodeIds.isLegacyHashId("123456789"))
        assertTrue(EpisodeIds.isLegacyHashId("-987654321"))
        // …and the shapes it must never touch.
        assertFalse(EpisodeIds.isLegacyHashId("https://feed.example.com/x/episode-1"))
        assertFalse(EpisodeIds.isLegacyHashId("0123456789abcdef"))
        assertFalse(EpisodeIds.isLegacyHashId(""))
    }

    @Test
    fun `derive is deterministic and moves with both inputs`() {
        val a = EpisodeIds.derive("https://feed.example.com", "https://a.example.com/x.mp3")
        assertEquals(a, EpisodeIds.derive("https://feed.example.com", "https://a.example.com/x.mp3"))
        assertNotEquals(a, EpisodeIds.derive("https://other.example.com", "https://a.example.com/x.mp3"))
        assertNotEquals(a, EpisodeIds.derive("https://feed.example.com", "https://b.example.com/x.mp3"))
    }

    private companion object {
        const val FEED_URL = "https://example.com/feed.xml"
    }
}
