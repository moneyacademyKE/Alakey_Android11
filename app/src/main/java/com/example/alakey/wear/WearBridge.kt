package com.example.alakey.wear

import android.content.Context
import com.example.alakey.tile.TileContract
import com.example.alakey.ui.AppViewModel
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Phone -> watch: pushes the existing UiState projection onto the Wear data
 * layer whenever it meaningfully changes (plus a slow position tick while
 * playing). The phone stays the single source of truth; the tile only renders.
 */
object WearBridge {
    private const val POLL_MS = 2_000L
    private const val TICK_MS = 15_000L

    @Volatile
    private var started = false

    fun start(context: Context, scope: CoroutineScope, uiState: StateFlow<AppViewModel.UiState>) {
        if (started) return
        if (GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS
        ) {
            return
        }
        started = true
        val appContext = context.applicationContext
        scope.launch(Dispatchers.Default) {
            var lastKey = ""
            var lastPushMs = 0L
            while (true) {
                val s = uiState.value
                val np = TileContract.NowPlaying(
                    show = s.current?.title.orEmpty(),
                    title = s.current?.episodeTitle.orEmpty(),
                    isPlaying = s.isPlaying,
                    isBuffering = s.isBuffering,
                    positionMs = s.currentTime,
                    durationMs = s.duration,
                    timestampMs = System.currentTimeMillis(),
                )
                val key = "${np.show} ${np.title} ${np.isPlaying} ${np.isBuffering}"
                val dueTick = np.isPlaying && System.currentTimeMillis() - lastPushMs >= TICK_MS
                if (key != lastKey || dueTick) {
                    lastKey = key
                    lastPushMs = np.timestampMs
                    push(appContext, np)
                }
                delay(POLL_MS)
            }
        }
    }

    private fun push(context: Context, np: TileContract.NowPlaying) {
        try {
            val request = PutDataMapRequest.create(TileContract.DATA_PATH)
            request.dataMap.putString(TileContract.KEY, TileContract.encode(np))
            Wearable.getDataClient(context)
                .putDataItem(request.asPutDataRequest().setUrgent())
        } catch (_: Exception) {
            // No watch paired / GMS hiccup: the tile is best-effort by design.
        }
    }
}
