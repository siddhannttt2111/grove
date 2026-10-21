package com.friendsvibe.autopilot.local

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.friendsvibe.autopilot.MainActivity
import com.friendsvibe.autopilot.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LocalPlaybackService : Service() {
    inner class LocalBinder : Binder() {
        val player: CrossfadePlayer get() = this@LocalPlaybackService.player
    }

    private val binder = LocalBinder()
    private lateinit var player: CrossfadePlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        player = CrossfadePlayer(this)
        createChannel()
        val notification = notification("Phone library", "Starting the set", false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        scope.launch {
            var lastKey = ""
            player.snapshot.collect { snap ->
                val key = "${snap.current?.id}:${snap.playing}"
                if (key == lastKey) return@collect
                lastKey = key
                val title = snap.current?.title ?: "Phone library"
                val text = when {
                    snap.current == null -> "Starting the set"
                    snap.playing -> snap.current.displayArtist
                    else -> "Paused"
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, notification(title, text, snap.playing))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> player.toggle()
            ACTION_SKIP -> player.requestSkip()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        scope.cancel()
        player.release()
        super.onDestroy()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Phone library",
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.description = "The set playing from files on this phone"
        manager.createNotificationChannel(channel)
    }

    private fun notification(title: String, text: String, playing: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val toggle = serviceIntent(ACTION_TOGGLE, 1)
        val skip = serviceIntent(ACTION_SKIP, 2)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_disc)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, if (playing) "Pause" else "Play", toggle)
            .addAction(0, "Skip", skip)
            .build()
    }

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent {
        return PendingIntent.getService(
            this,
            requestCode,
            Intent(this, LocalPlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_TOGGLE = "com.friendsvibe.autopilot.TOGGLE"
        const val ACTION_SKIP = "com.friendsvibe.autopilot.SKIP"
        private const val CHANNEL_ID = "friends_vibe_playback"
        private const val NOTIFICATION_ID = 41
    }
}
