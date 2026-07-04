package com.example.alakey.data

import android.util.Log
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

/** Pure parser: feed XML plus source URL becomes immutable episode values. */
object RssParser {
    fun parse(xml: String, feedUrl: String): List<PodcastEntity> {
        return try {
            val document = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isExpandEntityReferences = false
            }.newDocumentBuilder().parse(InputSource(StringReader(xml)))

            val root = document.documentElement ?: return emptyList()
            val channel = root.directElements("channel").firstOrNull() ?: root
            val channelTitle = channel.directText("title").ifEmpty { "Podcast" }
            val channelImage = channel.directElements("itunes:image").firstOrNull()?.attr("href")
                ?: channel.directElements("image").firstOrNull()?.directText("url")
                ?: ""

            channel.directElements("item", "entry").mapNotNull { entry ->
                readEntry(entry, feedUrl, channelTitle, channelImage)
            }
        } catch (e: Exception) {
            Log.e("RssParser", "Parse failure", e)
            emptyList()
        }
    }

    private fun readEntry(
        entry: Element,
        feedUrl: String,
        channelTitle: String,
        channelImage: String
    ): PodcastEntity? {
        val title = entry.directText("title")
        val audioUrl = entry.directElements("enclosure").firstOrNull()?.attr("url")
            ?: entry.directElements("link").firstOrNull { it.attr("rel") == "enclosure" }?.attr("href")
            ?: ""
        if (title.isEmpty() || audioUrl.isEmpty()) return null

        val guid = entry.directText("guid")
        val imageUrl = entry.directElements("itunes:image").firstOrNull()?.attr("href") ?: channelImage
        val description = entry.directText("description", "summary", "content")
            .replace(Regex("<.*?>"), " ")
            .trim()
            .take(500)

        val attrs = mutableMapOf<String, String>()
        entry.directText("itunes:season").takeIf { it.isNotEmpty() }?.let { attrs["season"] = it }
        entry.directText("itunes:episodeType").takeIf { it.isNotEmpty() }?.let { attrs["episodeType"] = it }
        attrs["downloadPolicy"] = "latest"

        return PodcastEntity(
            id = if (guid.isNotEmpty()) "$feedUrl/$guid" else (feedUrl + audioUrl).hashCode().toString(),
            title = channelTitle,
            episodeTitle = title,
            description = description,
            imageUrl = imageUrl,
            audioUrl = audioUrl,
            feedUrl = feedUrl,
            duration = parseDuration(entry.directText("itunes:duration")),
            pubDate = entry.directText("pubDate", "published"),
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
            } * 1000
        } catch (e: Exception) {
            0L
        }
    }

    private fun Element.directText(vararg names: String): String {
        return directElements(*names).firstOrNull()?.textContent?.trim().orEmpty()
    }

    private fun Element.directElements(vararg names: String): List<Element> {
        val accepted = names.toSet()
        val matches = mutableListOf<Element>()
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE && node is Element && node.tagName in accepted) {
                matches.add(node)
            }
        }
        return matches
    }

    private fun Element.attr(name: String): String = getAttribute(name).orEmpty()
}
