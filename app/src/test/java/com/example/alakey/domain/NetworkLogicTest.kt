package com.example.alakey.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkLogicTest {

    @Test
    fun `doctype html is detected`() {
        assertTrue(NetworkLogic.looksLikeHtml("<!DOCTYPE html>\n<html><body>404</body></html>"))
    }

    @Test
    fun `bare html tag is detected`() {
        assertTrue(NetworkLogic.looksLikeHtml("  <html lang=\"en\"><head></head></html>"))
    }

    @Test
    fun `lowercase doctype with leading whitespace is detected`() {
        assertTrue(NetworkLogic.looksLikeHtml("\n\n   <!doctype HTML><html></html>"))
    }

    @Test
    fun `xml feed is not html`() {
        assertFalse(NetworkLogic.looksLikeHtml("<?xml version=\"1.0\"?><rss version=\"2.0\"><channel></channel></rss>"))
    }

    @Test
    fun `empty string is not html`() {
        assertFalse(NetworkLogic.looksLikeHtml(""))
    }
}
