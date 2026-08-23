package com.example.alakey.system

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioSystem @Inject constructor(
    @ApplicationContext private val context: Context
) : Component {
    var player: ExoPlayer? = null
        private set

    override fun start() {
        if (player != null) return
        val attributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .setUsage(C.USAGE_MEDIA)
            .build()
        player = ExoPlayer.Builder(context)
            .setAudioAttributes(attributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    override fun stop() {
        player?.release()
        player = null
    }
}
