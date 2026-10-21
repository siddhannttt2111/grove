package com.grove.autopilot.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.grove.autopilot.MainActivity
import com.grove.autopilot.R
import com.grove.autopilot.model.Energy
import com.grove.autopilot.model.PlayUi
import com.grove.autopilot.model.Source
import com.grove.autopilot.model.Track
import com.grove.autopilot.model.UpNext
import com.grove.autopilot.vibe.Pick
import com.grove.autopilot.vibe.VibeScorer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlaybackService : android.app.Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())
    private lateinit var first: ExoPlayer
    private lateinit var second: ExoPlayer
    private lateinit var session: MediaSessionCompat
    private lateinit var notifications: NotificationManager

    private var tickJob: Job? = null
    private var active = 0
    private var myGen = 0
    private var fading = false
    private var fadeStart = 0L
    private var fadeMs = 4_000L
    private var holding = false
    private var errorStreak = 0
    private var armedAt = 0L
    private var foreground = false
    private var alive = true
    private var note = ""
    private var energy = Energy.Steady
    private var seedTrack: Track? = null
    private var now: Pick? = null
    private var fadingIn: Pick? = null
    private val queue = ArrayDeque<Pick>()
    private val history = ArrayDeque<Track>()
    private val played = mutableSetOf<String>()
    private val banned = mutableSetOf<String>()
    private var lastNotifyKey = ""
    private var lastStateAt = 0L

    override fun onCreate() {
        super.onCreate()
        notifications = getSystemService(NotificationManager::class.java)
        createChannel()
        first = buildPlayer()
        second = buildPlayer()
        session = MediaSessionCompat(this, "Grove").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = onMain { toggle() }
                override fun onPause() = onMain { pause() }
                override fun onSkipToNext() = onMain { skipInternal(fast = true) }
                override fun onSkipToPrevious() = onMain { previous() }
                override fun onSeekTo(pos: Long) = onMain { seek(pos) }
                override fun onStop() = onMain { windDown() }
            })
            isActive = true
        }
        tickJob = scope.launch {
            while (isActive) {
                tick()
                delay(
                    when {
                        fading -> 32
                        ::first.isInitialized && audible().isPlaying -> 200
                        else -> 800
                    },
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        onMain { handleIntent(intent) }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        alive = false
        tickJob?.cancel()
        scope.coroutineContext[Job]?.cancel()
        if (::first.isInitialized) first.release()
        if (::second.isInitialized) second.release()
        if (::session.isInitialized) {
            session.isActive = false
            session.release()
        }
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent?) {
        if (!alive) return
        ensureForeground()
        when (intent?.action) {
            ACTION_TOGGLE -> toggle()
            ACTION_NEXT -> skipInternal(fast = true)
            ACTION_PREV -> previous()
            ACTION_STOP -> windDown()
            else -> {
                var stop = false
                while (true) {
                    when (val command = PlaybackStore.commands.poll() ?: break) {
                        Command.Stop -> stop = true
                        is Command.Play -> {
                            stop = false
                            startPhone(command.track)
                        }
                        Command.Toggle -> toggle()
                        Command.Next -> skipInternal(fast = true)
                        Command.Previous -> previous()
                        is Command.EnergyChange -> applyEnergy(command.energy)
                        Command.Ban -> ban()
                        is Command.Seek -> seek(command.positionMs)
                    }
                }
                if (stop) windDown()
            }
        }
    }

    private fun startPhone(seed: Track) {
        myGen = PlaybackStore.bump()
        holding = false
        errorStreak = 0
        fading = false
        fadingIn = null
        note = ""
        queue.clear()
        history.clear()
        played.clear()
        banned.clear()
        energy = PlaybackStore.preferredEnergy
        seedTrack = seed
        armedAt = SystemClock.elapsedRealtime()
        val opening = Pick(seed, "You picked this", 0)
        played += seed.id
        now = opening
        audible().volume = 1f
        quiet().pause()
        playOn(audible(), opening, 1f)
        refill()
        publish()
    }

    private fun tick() {
        if (!alive || now == null) return
        if (myGen != PlaybackStore.generation) return
        if (fading) {
            val t = fadeProgress()
            audible().volume = 1f - t
            quiet().volume = t
            if (t >= 1f) finishFade()
        } else {
            maybeStartFade()
        }
        publish()
    }

    private fun maybeStartFade() {
        if (holding || fading) return
        if (SystemClock.elapsedRealtime() - armedAt < 1_500) return
        val player = audible()
        val duration = player.duration
        if (duration <= 0L || duration < 8_000L) return
        val left = duration - player.currentPosition
        val threshold = if (PlaybackStore.crossfade) 4_000L else 350L
        if (left in 1 until threshold) skipInternal(fast = false)
    }

    private fun skipInternal(fast: Boolean) {
        if (!alive || fading) return
        refill()
        val next = if (queue.isEmpty()) null else queue.removeFirst()
        if (next == null) {
            holding = true
            audible().pause()
            note = "No other song on this phone fits this set."
            publish()
            return
        }
        holding = false
        armedAt = SystemClock.elapsedRealtime()
        transitionTo(next, overlap = PlaybackStore.crossfade, manual = fast)
    }

    private fun transitionTo(next: Pick, overlap: Boolean, manual: Boolean) {
        remember(now)
        played += next.track.id
        errorStreak = 0
        if (!overlap) {
            fading = false
            fadingIn = null
            quiet().pause()
            now = next
            playOn(audible(), next, 1f)
            refill()
            publish()
            return
        }
        fadingIn = next
        fadeStart = SystemClock.elapsedRealtime()
        fadeMs = if (manual) 700L else 4_000L
        fading = true
        playOn(quiet(), next, 0f)
        refill()
        publish()
    }

    private fun finishFade() {
        val incoming = fadingIn ?: return
        val outgoing = audible()
        val incomingPlayer = quiet()
        outgoing.pause()
        outgoing.volume = 1f
        incomingPlayer.volume = 1f
        active = 1 - active
        now = incoming
        fadingIn = null
        fading = false
        refill()
    }

    private fun previous() {
        if (now == null) return
        val player = audible()
        if (!fading && player.currentPosition > 3_000) {
            player.seekTo(0)
            publish()
            return
        }
        fading = false
        quiet().pause()
        val prior = if (history.isEmpty()) null else history.removeLast()
        if (prior == null) {
            player.seekTo(0)
            publish()
            return
        }
        now?.let { queue.addFirst(it) }
        val pick = Pick(prior, "Back", 0)
        now = pick
        playOn(audible(), pick, 1f)
        publish()
    }

    private fun toggle() {
        if (now == null) return
        if (audible().isPlaying || fading) pause() else {
            audible().play()
            publish()
        }
    }

    private fun pause() {
        if (now == null) return
        audible().pause()
        quiet().pause()
        fading = false
        fadingIn?.let { queue.addFirst(it) }
        fadingIn = null
        publish()
    }

    private fun seek(positionMs: Long) {
        if (now == null || fading) return
        val duration = audible().duration.takeIf { it > 0 } ?: now?.track?.durationMs ?: return
        audible().seekTo(positionMs.coerceIn(0, duration))
        publish()
    }

    private fun applyEnergy(next: Energy) {
        energy = next
        PlaybackStore.preferredEnergy = next
        if (now == null) return
        queue.clear()
        note = ""
        holding = false
        refill()
        publish()
    }

    private fun ban() {
        val artist = now?.track?.artist?.trim().orEmpty()
        if (artist.isEmpty()) return
        banned += artist.lowercase()
        queue.removeAll { VibeScorer.excludesArtist(it.track.artist, banned) }
        note = "Banned $artist for this set"
        holding = false
        refill()
        if (fading) return
        skipInternal(fast = true)
    }

    private fun refill() {
        val seed = seedTrack ?: return
        if (queue.size >= 10) return
        val exclude = buildSet {
            add(seed.id)
            now?.let { add(it.track.id) }
            fadingIn?.let { add(it.track.id) }
            queue.forEach { add(it.track.id) }
            addAll(played)
        }
        var more = VibeScorer.plan(seed, PlaybackStore.phoneLibrary, energy, banned, exclude, 12)
        if (more.isEmpty()) {
            val soft = buildSet {
                add(seed.id)
                now?.let { add(it.track.id) }
                fadingIn?.let { add(it.track.id) }
                queue.forEach { add(it.track.id) }
            }
            more = VibeScorer.plan(seed, PlaybackStore.phoneLibrary, energy, banned, soft, 12)
            if (more.isNotEmpty()) note = "The set is looping through the closest songs."
        }
        queue.addAll(more)
    }

    private fun playOn(player: ExoPlayer, item: Pick, volume: Float) {
        player.setMediaItem(MediaItem.fromUri(item.track.uri))
        player.prepare()
        player.volume = volume
        player.playWhenReady = true
        player.play()
    }

    private fun publish() {
        if (!alive || myGen != PlaybackStore.generation) return
        val shown = if (fading && fadeProgress() > 0.5f) fadingIn else now
        val player = if (shown != null && shown == fadingIn) quiet() else audible()
        val duration = player.duration.takeIf { it > 0 } ?: shown?.track?.durationMs ?: 0L
        val position = player.currentPosition.coerceAtLeast(0L)
        PlaybackStore.ui.value = PlayUi(
            source = if (shown == null) Source.None else Source.Phone,
            playing = player.isPlaying,
            title = shown?.track?.title.orEmpty(),
            artist = shown?.track?.artist.orEmpty(),
            album = shown?.track?.album.orEmpty(),
            artUri = shown?.track?.artUri,
            positionMs = position,
            durationMs = duration,
            energy = energy,
            seedTitle = seedTrack?.title.orEmpty(),
            seedArtist = seedTrack?.artist.orEmpty(),
            upcoming = queue.take(24).map { UpNext(it.track.id, it.track.title, it.track.artist, it.reason) },
            note = note,
            crossfade = PlaybackStore.crossfade,
            generation = myGen,
            anchorRealtimeMs = SystemClock.elapsedRealtime(),
        )
        updateSession(shown, player.isPlaying, duration, position)
    }

    private fun windDown() {
        fading = false
        fadingIn = null
        now = null
        queue.clear()
        if (::first.isInitialized) {
            first.pause()
            second.pause()
        }
        if (myGen == PlaybackStore.generation) {
            PlaybackStore.ui.value = PlayUi(energy = PlaybackStore.preferredEnergy, crossfade = PlaybackStore.crossfade)
        }
        foreground = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun fadeProgress(): Float {
        if (!fading || fadeMs <= 0L) return 0f
        return ((SystemClock.elapsedRealtime() - fadeStart).toFloat() / fadeMs).coerceIn(0f, 1f)
    }

    private fun remember(item: Pick?) {
        val track = item?.track ?: return
        history.addLast(track)
        while (history.size > 20) history.removeFirst()
    }

    private fun audible(): ExoPlayer = if (active == 0) first else second

    private fun quiet(): ExoPlayer = if (active == 0) second else first

    private fun buildPlayer(): ExoPlayer {
        return ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .also { player ->
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) errorStreak = 0
                        if (state == Player.STATE_ENDED && player == audible() && !fading) {
                            onMain { skipInternal(fast = false) }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        onMain {
                            errorStreak += 1
                            note = "Skipped a file that would not play."
                            if (errorStreak < 4) skipInternal(fast = true) else pause()
                        }
                    }
                })
            }
    }

    private fun ensureForeground() {
        val notification = buildNotification(now)
        if (!foreground) {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            foreground = true
        }
    }

    private fun updateSession(shown: Pick?, playing: Boolean, duration: Long, position: Long) {
        val nowRt = SystemClock.elapsedRealtime()
        if (nowRt - lastStateAt > 900) {
            val state = if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
            session.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_PLAY_PAUSE or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackStateCompat.ACTION_SEEK_TO or
                            PlaybackStateCompat.ACTION_STOP,
                    )
                    .setState(state, position, if (playing) 1f else 0f)
                    .build(),
            )
            lastStateAt = nowRt
        }
        val key = (shown?.track?.id ?: "") + playing
        if (key == lastNotifyKey && nowRt - lastStateAt < 4_000) return
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, shown?.track?.title ?: "Grove")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, shown?.track?.artist.orEmpty())
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, shown?.track?.album.orEmpty())
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                .build(),
        )
        notifications.notify(NOTIFICATION_ID, buildNotification(shown))
        lastNotifyKey = key
    }

    private fun buildNotification(shown: Pick?): Notification {
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val playing = ::first.isInitialized && audible().isPlaying
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_note)
            .setContentTitle(shown?.track?.title ?: "Grove")
            .setContentText(shown?.track?.artist ?: "Phone set")
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .addAction(android.R.drawable.ic_media_previous, "Previous", pending(ACTION_PREV))
            .addAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "Pause" else "Play",
                pending(ACTION_TOGGLE),
            )
            .addAction(android.R.drawable.ic_media_next, "Next", pending(ACTION_NEXT))
            .setStyle(
                MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .build()
    }

    private fun pending(action: String): PendingIntent {
        return PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, javaClass).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW)
        channel.description = "The phone set Grove is playing"
        notifications.createNotificationChannel(channel)
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    companion object {
        const val ACTION_COMMAND = "com.grove.autopilot.COMMAND"
        const val ACTION_TOGGLE = "com.grove.autopilot.TOGGLE"
        const val ACTION_NEXT = "com.grove.autopilot.NEXT"
        const val ACTION_PREV = "com.grove.autopilot.PREV"
        const val ACTION_STOP = "com.grove.autopilot.STOP"
        private const val CHANNEL = "grove_playback"
        private const val NOTIFICATION_ID = 41

    }
}
