package com.example.alakey.wear

import android.content.Context
import android.util.Log
import com.example.alakey.system.Monotonic
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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Phone -> watch: pushes the existing UiState projection onto the Wear data
 * layer whenever it meaningfully changes (plus a slow position tick while
 * playing). The phone stays the single source of truth; the tile only renders.
 */
object WearBridge {
    private const val POLL_MS = 2_000L
    private const val TICK_MS = 15_000L

    // M10: no paired watch -> poll slowly instead of burning a 2s loop forever.
    private const val IDLE_POLL_MS = 30_000L
    private const val NODE_CHECK_MS = 30_000L
    private const val TAG = "WearBridge"

    private val started = AtomicBoolean(false)

    /** Last known watch presence; refreshed every NODE_CHECK_MS. */
    @Volatile
    private var watchPresent = false

    fun start(context: Context, scope: CoroutineScope, uiState: StateFlow<AppViewModel.UiState>) {
        // M10: @Volatile check-then-act let two start() calls spawn two loops.
        if (!started.compareAndSet(false, true)) return
        if (GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS
        ) {
            started.set(false)
            return
        }
        val appContext = context.applicationContext
        val job = scope.launch(Dispatchers.Default) {
            var lastKey = ""
            var lastPushMs = 0L
            var lastNodeCheckMs = 0L
            while (true) {
                // M68: interval math on the monotonic clock — a wall-clock step
                // made now-lastX negative and froze the tick/presence checks;
                // timestampMs stays wall-clock (crosses to the watch tile).
                val now = System.currentTimeMillis()
                val mono = Monotonic.now()
                if (mono - lastNodeCheckMs >= NODE_CHECK_MS) {
                    lastNodeCheckMs = mono
                    refreshWatchPresence(appContext)
                }
                if (watchPresent) {
                    val s = uiState.value
                    val np = TileContract.NowPlaying(
                        show = s.current?.title.orEmpty(),
                        title = s.current?.episodeTitle.orEmpty(),
                        isPlaying = s.isPlaying,
                        isBuffering = s.isBuffering,
                        positionMs = s.currentTime,
                        durationMs = s.duration,
                        timestampMs = now,
                    )
                    val key = "${np.show} ${np.title} ${np.isPlaying} ${np.isBuffering}"
                    val dueTick = np.isPlaying && mono - lastPushMs >= TICK_MS
                    if (key != lastKey || dueTick) {
                        lastKey = key
                        lastPushMs = mono
                        push(appContext, np)
                    }
                }
                delay(if (watchPresent) POLL_MS else IDLE_POLL_MS)
            }
        }
        // H10: the latch is process-global but the loop dies with the caller's
        // scope (viewModelScope on Activity finish). Without releasing it here,
        // the next start() returns at the CAS guard and the watch tile never
        // receives another frame for the rest of the process lifetime.
        job.invokeOnCompletion { started.set(false) }
    }

    private fun refreshWatchPresence(context: Context) {
        try {
            Wearable.getNodeClient(context).connectedNodes
                .addOnSuccessListener { nodes -> watchPresent = nodes.any { it.isNearby } }
                .addOnFailureListener { watchPresent = false }
        } catch (_: Exception) {
            watchPresent = false
        }
    }

    private fun push(context: Context, np: TileContract.NowPlaying) {
        try {
            val request = PutDataMapRequest.create(TileContract.DATA_PATH)
            request.dataMap.putString(TileContract.KEY, TileContract.encode(np))
            Wearable.getDataClient(context)
                .putDataItem(request.asPutDataRequest().setUrgent())
                // L8: wear-path failures were fully silent — observe the Task.
                .addOnFailureListener { e -> Log.w(TAG, "Tile frame push failed", e) }
        } catch (e: Exception) {
            // No watch paired / GMS hiccup: the tile is best-effort by design.
            Log.w(TAG, "Tile frame push threw", e)
        }
    }
}
