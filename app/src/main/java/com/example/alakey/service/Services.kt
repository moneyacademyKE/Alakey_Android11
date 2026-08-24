package com.example.alakey.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
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

    // FGS contract (#62): whatever arms the start window — media button, the
    // controller's play path, adb — must find startForeground() already called
    // before any player or stream work. onStartCommand only fires for started
    // (not bound) launches, so plain controller binds never show a placeholder.
    // Media3 posts the real media notification once the session is live.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, placeholderNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        return super.onStartCommand(intent, flags, startId)
    }

    private fun placeholderNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW))
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Alakey")
            .setContentText("Preparing player…")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOnlyAlertOnce(true)
            .build()
    }
    override fun onDestroy() {
        session?.release()
        audioSystem.stop()
        super.onDestroy()
    }

    private companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "playback_placeholder"
    }
}
