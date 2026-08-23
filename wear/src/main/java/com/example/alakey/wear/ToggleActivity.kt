package com.example.alakey.wear

import android.app.Activity
import android.os.Bundle
import com.example.alakey.tile.TileContract
import com.google.android.gms.wearable.Wearable

/**
 * Headless trampoline: the tile's tap target. Sends the toggle command to the
 * phone over the message channel, then vanishes. The watch never owns
 * playback state — the phone's media session stays the single truth.
 */
class ToggleActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContext = applicationContext
        val nodes = Wearable.getNodeClient(appContext).connectedNodes
        nodes.addOnSuccessListener { list ->
            val messages = Wearable.getMessageClient(appContext)
            list.filter { it.isNearby }.forEach { node ->
                messages.sendMessage(
                    node.id,
                    TileContract.CMD_PATH,
                    TileContract.CMD_TOGGLE.toByteArray(Charsets.UTF_8),
                )
            }
        }
        finish() // Theme.NoDisplay requires finishing before the first frame
    }
}
