package com.example.alakey.wear

import androidx.wear.tiles.TileService
import com.example.alakey.tile.TileContract
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/** Receives now-playing frames from the phone; refreshes the tile in place. */
class NowPlayingDataListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        try {
            dataEvents
                .filter { it.type == DataEvent.TYPE_CHANGED }
                .mapNotNull { event ->
                    val item = event.dataItem
                    if (item.uri.path != TileContract.DATA_PATH) return@mapNotNull null
                    val raw = DataMapItem.fromDataItem(item).dataMap.getString(TileContract.KEY)
                    // L8: malformed frames dropped silently — log the drop so
                    // wire-format drift is diagnosable from the watch.
                    TileContract.decode(raw) ?: run {
                        android.util.Log.w("NowPlayingData", "Dropped malformed tile frame (len=${raw?.length ?: 0})")
                        null
                    }
                }
                .forEach { np ->
                    TileStateStore.update(this, np)
                    TileService.getUpdater(this)
                        .requestUpdate(NowPlayingTileService::class.java)
                }
        } finally {
            dataEvents.release()
        }
    }
}
