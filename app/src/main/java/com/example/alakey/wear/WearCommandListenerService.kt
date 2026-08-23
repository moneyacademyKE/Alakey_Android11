package com.example.alakey.wear

import android.content.ComponentName
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.alakey.tile.TileContract
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * Watch -> phone: the tile's toggle arrives here and goes through the front
 * door — a MediaController onto the existing AudioService session, the same
 * path the lockscreen and headset buttons use. No new playback state owners.
 */
class WearCommandListenerService : WearableListenerService() {

    override fun onMessageReceived(message: MessageEvent) {
        if (message.path != TileContract.CMD_PATH) return
        if (String(message.data, Charsets.UTF_8) != TileContract.CMD_TOGGLE) return

        val context = applicationContext
        val token = SessionToken(context, ComponentName(context, AUDIO_SERVICE_CLASS))
        val future = MediaController.Builder(context, token).buildAsync()
        val main = Handler(Looper.getMainLooper())

        future.addListener({
            try {
                val controller = future.get()
                if (controller.isPlaying) controller.pause() else controller.play()
                main.postDelayed({ controller.release() }, RELEASE_DELAY_MS)
            } catch (_: Exception) {
                // Session not running (app process killed): toggle is best-effort.
            }
        }, Executor { runnable -> main.post(runnable) })
    }

    private companion object {
        const val AUDIO_SERVICE_CLASS = "com.example.alakey.service.AudioService"
        const val RELEASE_DELAY_MS = 1_000L
    }
}
