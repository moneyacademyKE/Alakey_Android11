package com.example.alakey.data

import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/**
 * Pure functional parser.
 * No state. No side effects. Just String -> ListView.
 */
object RssParser {
    fun parse(xml: String, feedUrl: String): List<PodcastEntity> {
        return try {
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(StringReader(xml))
            }
            readFeed(parser, feedUrl)
        } catch (e: Exception) {
            Log.e("RssParser", "Parse failure", e)
            emptyList()
        }
    }

    private fun readFeed(parser: XmlPullParser, feedUrl: String): List<PodcastEntity> {
        val entries = mutableListOf<PodcastEntity>()
        var eventType = parser.eventType
        // Store channel title
        var channelTitle = "Podcast" 
        var channelImage = ""

        // Fast-forward to root
        while (eventType != XmlPullParser.START_TAG && eventType != XmlPullParser.END_DOCUMENT) {
            eventType = parser.next()
        }
        
        if (eventType == XmlPullParser.END_DOCUMENT) return emptyList()

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                if (parser.name == "title" && channelTitle == "Podcast") {
                    channelTitle = readText(parser)
                } else if (parser.name == "itunes:image" || parser.name == "image") {
                    // Capture channel-level artwork
                    val href = parser.getAttributeValue(null, "href")
                    val url = if (!href.isNullOrEmpty()) href else ""
                    if (url.isNotEmpty()) channelImage = url
                } else if (parser.name == "item" || parser.name == "entry") {
                    readEntry(parser, feedUrl, channelTitle, channelImage)?.let { entries.add(it) }
                }
            }
        }
        return entries
    }

    private fun readEntry(parser: XmlPullParser, feedUrl: String, channelTitle: String, channelImage: String): PodcastEntity? {
        var title = ""
        var description = ""
        var audioUrl = ""
        var imageUrl = ""
        var pubDate = ""
        var guid = ""
        var duration = 0L
        val attrs = mutableMapOf<String, String>()

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "title" -> title = readText(parser)
                "description", "summary", "content" -> description = readText(parser)
                "enclosure" -> {
                    audioUrl = parser.getAttributeValue(null, "url") ?: ""
                    // Consume the tag itself, else its END_TAG terminates the item loop early.
                    if (parser.isEmptyElementTag()) parser.next() else skip(parser)
                }
                "itunes:image" -> {
                    imageUrl = parser.getAttributeValue(null, "href") ?: ""
                    // Same as enclosure: an unconsumed open tag's END_TAG ends the loop prematurely.
                    if (parser.isEmptyElementTag()) parser.next() else skip(parser)
                }
                "pubDate", "published" -> pubDate = readText(parser)
                "guid" -> guid = readText(parser)
                "itunes:duration" -> {
                    val raw = readText(parser)
                    duration = parseDuration(raw)
                }
                "itunes:season" -> attrs["season"] = readText(parser)
                "itunes:episodeType" -> attrs["episodeType"] = readText(parser)
                else -> skip(parser)
            }
        }
        
        // Validate required fields
        if (audioUrl.isEmpty() || title.isEmpty()) return null
        
        // Default download policy for the registry
        attrs["downloadPolicy"] = "latest"

        // Generate deterministic ID
        val finalId = if (guid.isNotEmpty()) "$feedUrl/$guid" else (feedUrl + audioUrl).hashCode().toString()

        return PodcastEntity(
            id = finalId,
            title = channelTitle,
            episodeTitle = title,
            description = description.replace(Regex("<.*?>"), " ").trim().take(500),
            imageUrl = if (imageUrl.isNotEmpty()) imageUrl else channelImage,
            audioUrl = audioUrl,
            feedUrl = feedUrl,
            duration = duration,
            pubDate = pubDate,
            attributes = attrs
        )
    }

    private fun parseDuration(raw: String): Long {
        return try {
            val parts = raw.split(":").map { it.trim().toLong() }
            when (parts.size) {
                3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                2 -> parts[0] * 60 + parts[1]
                1 -> parts[0]
                else -> 0L
            } * 1000 // Convert to MS
        } catch (e: Exception) {
            0L
        }
    }

    private fun readText(parser: XmlPullParser): String {
        // KXmlParser may deliver long/CDATA text as MULTIPLE TEXT events; gather them all.
        val sb = StringBuilder()
        var ev = parser.next()
        while (ev == XmlPullParser.TEXT) {
            sb.append(parser.text)
            ev = parser.next()
        }
        // A nested element inside the text holder (rare) must be consumed too.
        if (ev == XmlPullParser.START_TAG) skip(parser)
        return sb.toString()
    }

    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) return
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }
}
