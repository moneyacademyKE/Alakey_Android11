package com.example.alakey.wear

import com.example.alakey.tile.TileContract

/**
 * Pure presentation logic of the now-playing tile — no Android types, so the
 * wear module's JVM tests can pin it. (M11: wear had zero test sources.)
 */
internal object TileFormat {
    fun glyph(np: TileContract.NowPlaying): String = when {
        np.isBuffering -> "◌"
        np.isPlaying -> "❚❚"
        else -> "▶"
    }

    /** mm:ss, pinned to Locale.US: default locale renders Eastern Arabic-Indic digits under ar/fa/ur. (L7) */
    fun mmss(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(java.util.Locale.US, totalSec / 60, totalSec % 60)
    }

    /** M9: no frame yet, or the last frame is too old to present as live. */
    fun isStale(np: TileContract.NowPlaying, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (np.timestampMs == 0L) return true
        return nowMs - np.timestampMs > STALE_MS
    }

    /** A frame with no refresh for this long stops presenting as live. */
    const val STALE_MS: Long = 10L * 60 * 1000
}
