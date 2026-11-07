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
        // Invariant, not a handled failure (L63): start() either publishes a
        // player or throws inside onCreate — there is no reachable null here.
        // checkNotNull states the invariant without advertising recovery.
        val player = checkNotNull(audioSystem.player) { "AudioSystem.start() must publish a player" }
        session = MediaLibrarySession.Builder(this, player, object : MediaLibrarySession.Callback {} )
            .setSessionActivity(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .build()
    }

    override fun onGetSession(info: MediaSession.ControllerInfo): MediaLibrarySession? = when {
        // H4: platform bridges (SystemUI lockscreen, BT AVRCP, Auto) connect as
        // legacy controllers — media3 1.2.0 reports them as controllerVersion 0.
        // The compat package name this gate previously allowlisted exists
        // nowhere in the pinned AAR (verified via javap), so every platform
        // controller was rejected and lockscreen/headset control died.
        // Third-party apps on modern media3 report a real version and are
        // still refused. isTrusted() becomes public at the H2 media3 bump.
        acceptsController(info.packageName, info.controllerVersion, packageName) -> session
        else -> {
            android.util.Log.w(TAG, "Rejecting media controller: ${info.packageName}")
            null
        }
    }

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
        const val TAG = "AudioService"
    }
}

/**
 * M3/H4 gate: own package always passes; platform media bridges
 * (lockscreen, Bluetooth AVRCP, Android Auto) connect as legacy
 * controllers (controllerVersion 0) and must pass; everything else —
 * modern media3 third-party clients — is refused.
 */
internal fun acceptsController(packageName: String, controllerVersion: Int, ownPackage: String): Boolean =
    packageName == ownPackage ||
        controllerVersion == MediaSession.ControllerInfo.LEGACY_CONTROLLER_VERSION
