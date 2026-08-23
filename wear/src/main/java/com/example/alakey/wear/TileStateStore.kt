package com.example.alakey.wear

import android.content.Context
import com.example.alakey.tile.TileContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the last now-playing frame the phone pushed. Memory first, prefs for
 * process death — the tile renders whatever is here, nothing more.
 */
object TileStateStore {
    private const val PREFS = "alakey_tile"
    private const val KEY_STATE = "now_playing"

    private val empty = TileContract.NowPlaying("", "", false, false, 0, 0, 0)
    private val _state = MutableStateFlow(empty)
    val state: StateFlow<TileContract.NowPlaying> = _state.asStateFlow()

    fun update(context: Context, np: TileContract.NowPlaying) {
        _state.value = np
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_STATE, TileContract.encode(np)).apply()
    }

    fun restore(context: Context) {
        if (_state.value.timestampMs != 0L) return
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STATE, null)
        TileContract.decode(raw)?.let { _state.value = it }
    }
}
