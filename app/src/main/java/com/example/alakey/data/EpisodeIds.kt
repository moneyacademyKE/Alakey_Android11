package com.example.alakey.data

import java.security.MessageDigest

/**
 * Episode identity. GUID episodes are identified by feedUrl/guid — stable,
 * feed-authored. GUID-less episodes have no feed-authored identity, so we mint
 * one: a 64-bit truncation of SHA-256 over feedUrl|audioUrl — the same inputs
 * the v13 hashCode scheme used, so every legacy id is re-derivable in the
 * v14 migration.
 *
 * Why not hashCode: a 32-bit space hits a 50% birthday collision at ~77k
 * episodes; PodcastDao's INSERT IGNORE drops collisions silently, so
 * episodes never entered the library. 64 bits moves the bound to ~5e9. (M14)
 */
object EpisodeIds {
    /** Stable guid-less id: 16 lowercase hex chars, unsigned. */
    fun derive(feedUrl: String, audioUrl: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$feedUrl|$audioUrl".toByteArray())
        var value = 0L
        for (i in 0 until 8) value = (value shl 8) or (digest[i].toLong() and 0xFF)
        return "%016x".format(value)
    }

    /**
     * Legacy v13 shape: an Int hashCode stringified (optional sign, at most
     * 10 digits). v14 hex ids are always 16 chars, and guid ids always
     * contain the feedUrl — neither can match.
     */
    fun isLegacyHashId(id: String): Boolean =
        id.isNotEmpty() && id.length <= 11 && id.all { it.isDigit() || (it == '-' && id.indexOf('-') == 0) }
}
