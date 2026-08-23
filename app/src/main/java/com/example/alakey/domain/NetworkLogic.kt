package com.example.alakey.domain

import com.example.alakey.data.ItunesSearchResult
import com.example.alakey.data.ItunesSearchResponse
import com.google.gson.Gson
import org.json.JSONObject

/**
 * Pure Logic: Network Transformation.
 * No side effects. String -> Data.
 */
object NetworkLogic {

    fun parseItunesResults(json: String): List<ItunesSearchResult> {
        if (json.isEmpty()) return emptyList()
        return try {
            Gson().fromJson(json, ItunesSearchResponse::class.java).results
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun extractProxyContent(json: String): String {
        return try {
            JSONObject(json).getString("contents")
        } catch (e: Exception) {
            ""
        }
    }

    /** True if the body is an HTML page (error page, CDN challenge, login wall)
     *  rather than an XML feed. Covers both `<!DOCTYPE html` and bare `<html>`
     *  openings, case-insensitive, leading whitespace tolerated. */
    fun looksLikeHtml(content: String): Boolean {
        val head = content.trim().take(200).lowercase()
        return head.startsWith("<!doctype html") || head.startsWith("<html")
    }
}
