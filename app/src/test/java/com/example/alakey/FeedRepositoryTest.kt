package com.example.alakey

import com.example.alakey.data.FeedRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedRepositoryTest {

    @Test
    fun acceptsRssAndAtomFeeds() {
        assertTrue(FeedRepository.looksLikeFeed("""<?xml version="1.0"?><rss version="2.0"><channel></channel></rss>"""))
        assertTrue(FeedRepository.looksLikeFeed("<feed xmlns=\"http://www.w3.org/2005/Atom\"><title>t</title></feed>"))
        assertTrue(FeedRepository.looksLikeFeed("  \n<?xml version=\"1.0\"?><rss></rss>"))
    }

    @Test
    fun rejectsHtmlPagesIncludingDoctypeless() {
        // The old sniff let bare <html> pages through as "feeds" (parsed to 0 episodes)
        assertFalse(FeedRepository.looksLikeFeed("<!DOCTYPE html><html><body>404</body></html>"))
        assertFalse(FeedRepository.looksLikeFeed("<html><head><title>Access blocked</title></head></html>"))
        assertFalse(FeedRepository.looksLikeFeed("<HTML lang=\"en\"><body>cloudflare</body></HTML>"))
    }

    @Test
    fun rejectsEmptyAndNonMarkupContent() {
        assertFalse(FeedRepository.looksLikeFeed(null))
        assertFalse(FeedRepository.looksLikeFeed(""))
        assertFalse(FeedRepository.looksLikeFeed("{\"contents\": \"<rss></rss>\"}"))
        assertFalse(FeedRepository.looksLikeFeed("Moved Permanently. Redirecting to ..."))
    }
}
