package com.example.alakey.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.*
import com.example.alakey.MainActivity
import com.example.alakey.system.AudioSystem
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AudioService : MediaLibraryService() {
    @Inject lateinit var audioSystem: AudioSystem

    private var session: MediaLibrarySession? = null

    // De-complected: playback configuration lives in AudioSystem; intent state
    // lives in PlaybackClient. The service is a humble shell for the Player.

    override fun onCreate() {
        super.onCreate()
        audioSystem.start()
        val player = audioSystem.player ?: throw IllegalStateException("AudioSystem failed to start")
        session = MediaLibrarySession.Builder(this, player, object : MediaLibrarySession.Callback {} )
            .setSessionActivity(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .build()
    }

    override fun onGetSession(info: MediaSession.ControllerInfo) = session
    override fun onDestroy() {
        session?.release()
        audioSystem.stop()
        super.onDestroy()
    }
}
