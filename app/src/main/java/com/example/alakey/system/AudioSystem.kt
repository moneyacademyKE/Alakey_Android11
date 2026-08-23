package com.example.alakey.system

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the process-wide ExoPlayer instance.
 * Reads persisted playback preferences (skip-silence, boost) written by PlaybackClient.
 */
@Singleton
class AudioSystem @Inject constructor(
    @ApplicationContext private val context: Context
) : Component {
    var player: ExoPlayer? = null
        private set

    private var enhancer: LoudnessEnhancer? = null

    override fun start() {
        if (player != null) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val attributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .setUsage(C.USAGE_MEDIA)
            .build()
        val built = ExoPlayer.Builder(context)
            .setAudioAttributes(attributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(SKIP_MS)
            .setSeekForwardIncrementMs(SKIP_MS)
            .build()
        built.setSkipSilenceEnabled(prefs.getBoolean(KEY_SKIP_SILENCE, true))
        built.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    // Re-read prefs at every READY: toggles apply live on the next transition.
                    built.setSkipSilenceEnabled(prefs.getBoolean(KEY_SKIP_SILENCE, true))
                    attachBoost(built, prefs.getBoolean(KEY_BOOST, false))
                }
            }
        })
        player = built
    }

    private fun attachBoost(player: ExoPlayer, enabled: Boolean) {
        releaseBoost()
        if (!enabled) return
        val sessionId = player.audioSessionId
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
        runCatching {
            LoudnessEnhancer(sessionId).apply {
                setTargetGain(BOOST_DB)
                setEnabled(true)
            }.also { enhancer = it }
        }
    }

    private fun releaseBoost() {
        runCatching { enhancer?.release() }
        enhancer = null
    }

    override fun stop() {
        releaseBoost()
        player?.release()
        player = null
    }

    private companion object {
        const val PREFS = "playback_prefs"
        const val KEY_SKIP_SILENCE = "skip_silence"
        const val KEY_BOOST = "boost"
        const val SKIP_MS = 30_000L
        const val BOOST_DB = 600 // milli-dB == +6 dB
    }
}
