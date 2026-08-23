package com.example.alakey.data

/**
 * Pure parser for the Podcasting 2.0 chapters JSON format.
 * Regex-based on purpose: no org.json dependency, fully unit-testable on the JVM.
 * Format: { "version": "...", "chapters": [ { "startTime": "1:23:45" | 5025 | "5025.5", "title": "..." }, ... ] }
 */
object ChapterParser {

    private val entryRegex = Regex(
        """\{\s*"startTime"\s*:\s*"?([0-9:.]+)"?\s*,\s*"title"\s*:\s*"((?:[^"\\]|\\.)*)""""
    )

    fun parse(json: String): List<Chapter> =
        entryRegex.findAll(json)
            .mapNotNull { match ->
                timeToMs(match.groupValues[1])?.let { ms ->
                    Chapter(ms, unescape(match.groupValues[2]))
                }
            }
            .sortedBy { it.start }
            .toList()

    /** "1:23:45" | "23:45" | "5025" | "5025.5" -> milliseconds, or null when unparseable. */
    fun timeToMs(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.contains(':')) {
            val parts = trimmed.split(':')
            if (parts.size !in 2..3) return null
            val seconds = parts.map { it.toLongOrNull() ?: return null }
            if (seconds.any { it < 0 }) return null
            return when (parts.size) {
                3 -> (seconds[0] * 3600 + seconds[1] * 60 + seconds[2]) * 1000
                else -> (seconds[0] * 60 + seconds[1]) * 1000
            }
        }
        val numeric = trimmed.toLongOrNull()?.times(1000)
            ?: trimmed.toDoubleOrNull()?.times(1000)?.toLong()
        return numeric?.takeIf { it >= 0 }
    }

    private fun unescape(text: String): String =
        text.replace("\\\"", "\"").replace("\\\\", "\\")
}
