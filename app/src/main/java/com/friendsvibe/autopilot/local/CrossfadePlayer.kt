package com.friendsvibe.autopilot.local

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.friendsvibe.autopilot.rank.CrossfadeTiming
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Two players and a volume ramp. Audio focus is held once so the incoming player
 * does not pause the outgoing one. This is only used for files on the phone.
 */
class CrossfadePlayer(private val context: Context) {
    data class Snapshot(
        val current: LocalTrack?,
        val upcoming: List<LocalTrack>,
        val playing: Boolean,
        val positionMs: Long,
        val durationMs: Long,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val playerA = buildPlayer()
    private val playerB = buildPlayer()
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false
    private var resumeAfterFocus = false
    private var activeIsA = true
    private var tracks: List<LocalTrack> = emptyList()
    private var index = 0
    private var playing = false
    private var fading = false
    private var fadeTargetId: Long? = null
    private var fadeJob: Job? = null
    private var watchJob: Job? = null

    var onUserSkip: (() -> Unit)? = null

    private val _snapshot = MutableStateFlow(Snapshot(null, emptyList(), false, 0L, 0L))
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause()
        }
    }

    init {
        playerA.addListener(listener(playerA))
        playerB.addListener(listener(playerB))
        runCatching {
            ContextCompat.registerReceiver(
                context,
                noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    fun playQueue(queue: List<LocalTrack>) {
        if (queue.isEmpty()) {
            stopPlayback()
            return
        }
        tracks = queue
        index = 0
        cancelFade()
        playing = true
        requestFocus()
        prepare(active(), queue.first(), 1f)
        active().play()
        inactive().pause()
        publish()
        arm()
    }

    fun replaceUpcoming(upcoming: List<LocalTrack>) {
        val current = tracks.getOrNull(index)
        val next = upcoming.filter { it.id != current?.id }
        tracks = if (current == null) next else listOf(current) + next
        if (fading && fadeTargetId != tracks.getOrNull(index + 1)?.id) {
            cancelFade()
        }
        publish()
    }

    fun toggle() {
        if (playing) pause() else resume()
    }

    fun pause() {
        resumeAfterFocus = false
        pausePlayers()
    }

    fun requestSkip() {
        val handler = onUserSkip
        if (handler != null) handler() else advance()
    }

    fun stopPlayback() {
        watchJob?.cancel()
        cancelFade()
        playing = false
        resumeAfterFocus = false
        playerA.stop()
        playerB.stop()
        tracks = emptyList()
        index = 0
        abandonFocus()
        publish()
    }

    fun release() {
        scope.cancel()
        runCatching { context.unregisterReceiver(noisyReceiver) }
        abandonFocus()
        playerA.release()
        playerB.release()
    }

    private fun advance() {
        val rest = tracks.drop(index + 1)
        if (rest.isEmpty()) {
            stopPlayback()
        } else {
            playQueue(rest)
        }
    }

    private fun arm() {
        if (watchJob?.isActive == true) return
        watchJob = scope.launch {
            while (isActive) {
                val player = active()
                val duration = player.duration
                val position = player.currentPosition
                if (!fading && playing && duration > 0 && index + 1 < tracks.size) {
                    val fade = CrossfadeTiming.crossfadeMs(duration)
                    val remaining = duration - position
                    if (fade > 0 && remaining in 1..fade) {
                        beginCrossfade(fade)
                    }
                }
                publish()
                delay(200)
            }
        }
    }

    private fun beginCrossfade(fadeMs: Long) {
        val next = tracks.getOrNull(index + 1) ?: return
        fading = true
        fadeTargetId = next.id
        val outgoing = active()
        val incoming = inactive()
        fadeJob = scope.launch {
            try {
                prepare(incoming, next, 0f)
                incoming.play()
                awaitReady(incoming)
                val steps = 16
                val stepDelay = (fadeMs / steps).coerceAtLeast(16L)
                for (step in 1..steps) {
                    if (!fading || fadeTargetId != next.id) return@launch
                    val t = step / steps.toFloat()
                    outgoing.volume = cos(t * (PI / 2).toFloat())
                    incoming.volume = sin(t * (PI / 2).toFloat())
                    delay(stepDelay)
                }
                if (!fading || fadeTargetId != next.id) return@launch
                outgoing.pause()
                outgoing.volume = 1f
                incoming.volume = 1f
                activeIsA = !activeIsA
                index += 1
            } finally {
                if (fadeTargetId == next.id) {
                    fading = false
                    fadeTargetId = null
                }
                publish()
            }
        }
    }

    private suspend fun awaitReady(player: ExoPlayer) {
        if (player.playbackState == Player.STATE_READY) return
        withTimeoutOrNull(4_000) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : Player.Listener {
                    fun finish() {
                        player.removeListener(this)
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) finish()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        finish()
                    }
                }
                player.addListener(listener)
                continuation.invokeOnCancellation { player.removeListener(listener) }
            }
        }
    }

    private fun prepare(player: ExoPlayer, track: LocalTrack, volume: Float) {
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(
            MediaItem.Builder().setUri(track.uri).setMediaId(track.id.toString()).build(),
        )
        player.volume = volume
        player.prepare()
        player.playWhenReady = playing
    }

    private fun listener(owner: ExoPlayer) = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED && owner == active() && !fading) {
                advance()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "Local playback failed", error)
            if (fading && owner == inactive()) {
                cancelFade()
                val current = tracks.getOrNull(index)
                tracks = listOfNotNull(current) + tracks.drop(index + 2)
                publish()
                return
            }
            if (owner == active()) advance()
        }
    }

    private fun pauseFromFocus() {
        if (playing) resumeAfterFocus = true
        pausePlayers()
    }

    private fun pausePlayers() {
        if (fading) cancelFade()
        playing = false
        active().pause()
        inactive().pause()
        publish()
    }

    private fun resume() {
        playing = true
        requestFocus()
        active().play()
        publish()
    }

    private fun cancelFade() {
        fadeJob?.cancel()
        fadeJob = null
        fading = false
        fadeTargetId = null
        inactive().pause()
        inactive().volume = 0f
        active().volume = 1f
    }

    private fun requestFocus() {
        if (hasFocus) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    -> pauseFromFocus()
                    AudioManager.AUDIOFOCUS_GAIN -> if (resumeAfterFocus) {
                        resumeAfterFocus = false
                        resume()
                    }
                }
            }
            .build()
        focusRequest = request
        hasFocus = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
        hasFocus = false
    }

    private fun publish() {
        val player = active()
        val duration = player.duration
        _snapshot.value = Snapshot(
            current = tracks.getOrNull(index),
            upcoming = tracks.drop(index + 1),
            playing = playing,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = if (duration > 0) duration else tracks.getOrNull(index)?.durationMs ?: 0L,
        )
    }

    private fun buildPlayer(): ExoPlayer {
        return ExoPlayer.Builder(context).build().apply {
            val attributes = androidx.media3.common.AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()
            setAudioAttributes(attributes, false)
        }
    }

    private fun active(): ExoPlayer = if (activeIsA) playerA else playerB
    private fun inactive(): ExoPlayer = if (activeIsA) playerB else playerA

    private companion object {
        const val TAG = "FriendsVibe"
    }
}
