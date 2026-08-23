package com.example.alakey.tile

/**
 * Single source of truth for the phone <-> watch wire format.
 * Pure data, pure functions: no Android, no GMS — JVM-testable on both sides.
 */
object TileContract {
    const val DATA_PATH = "/alakey/now_playing"
    const val CMD_PATH = "/alakey/cmd"
    const val CMD_TOGGLE = "toggle"
    const val KEY = "v"
    private const val FRAME_VERSION = 1
    private const val SEP = "\u001F" // unit separator: never appears in podcast metadata

    data class NowPlaying(
        val show: String,
        val title: String,
        val isPlaying: Boolean,
        val isBuffering: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val timestampMs: Long,
    )

    fun encode(state: NowPlaying): String = listOf(
        FRAME_VERSION.toString(),
        state.show,
        state.title,
        state.isPlaying.toString(),
        state.isBuffering.toString(),
        state.positionMs.toString(),
        state.durationMs.toString(),
        state.timestampMs.toString(),
    ).joinToString(SEP)

    fun decode(raw: String?): NowPlaying? {
        if (raw.isNullOrEmpty()) return null
        val f = raw.split(SEP)
        if (f.size != 8 || f[0].toIntOrNull() != FRAME_VERSION) return null
        return NowPlaying(
            show = f[1],
            title = f[2],
            isPlaying = f[3].toBooleanStrictOrNull() ?: return null,
            isBuffering = f[4].toBooleanStrictOrNull() ?: return null,
            positionMs = f[5].toLongOrNull() ?: return null,
            durationMs = f[6].toLongOrNull() ?: return null,
            timestampMs = f[7].toLongOrNull() ?: return null,
        )
    }
}
