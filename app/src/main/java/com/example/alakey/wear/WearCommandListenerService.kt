package com.example.alakey.wear

import com.example.alakey.tile.TileContract
import com.example.alakey.ui.PlaybackClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Watch -> phone: the tile's toggle goes through the front door —
 * PlaybackClient, the single projection surface over the session, the same
 * togglePlay() the phone UI calls. No throwaway MediaController per tap, no
 * racing read of instantaneous isPlaying against the reconciler's desired
 * state. (M8)
 */
class WearCommandListenerService : WearableListenerService() {

    override fun onMessageReceived(message: MessageEvent) {
        if (message.path != TileContract.CMD_PATH) return
        if (String(message.data, Charsets.UTF_8) != TileContract.CMD_TOGGLE) return

        EntryPointAccessors.fromApplication(applicationContext, PlaybackEntryPoint::class.java)
            .playbackClient()
            .togglePlay()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlaybackEntryPoint {
    fun playbackClient(): PlaybackClient
}
