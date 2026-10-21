package com.friendsvibe.autopilot.spotify

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.PlayerState
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class RemoteNow(
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val positionMs: Long,
    val paused: Boolean,
)

/**
 * Tells the installed Spotify app which track URI to play or queue.
 * Playback, crossfade, and the audio itself stay inside Spotify.
 */
class SpotifyRemote(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var appRemote: SpotifyAppRemote? = null
    private var subscription: Subscription<PlayerState>? = null

    fun isInstalled(): Boolean = SpotifyAppRemote.isSpotifyInstalled(context)

    fun connect(onReady: () -> Unit, onError: (String) -> Unit) {
        val existing = appRemote
        if (existing != null && existing.isConnected) {
            onReady()
            return
        }
        val params = ConnectionParams.Builder(SpotifyConfig.clientId())
            .setRedirectUri(SpotifyConfig.REDIRECT_URI)
            .showAuthView(true)
            .build()
        SpotifyAppRemote.connect(
            context,
            params,
            object : Connector.ConnectionListener {
                override fun onConnected(remote: SpotifyAppRemote) {
                    appRemote = remote
                    main.post(onReady)
                }

                override fun onFailure(error: Throwable) {
                    main.post { onError(explain(error)) }
                }
            },
        )
    }

    fun disconnect() {
        subscription?.cancel()
        subscription = null
        appRemote?.let { SpotifyAppRemote.disconnect(it) }
        appRemote = null
    }

    suspend fun canPlayOnDemand(): Result<Boolean> = suspendCancellableCoroutine { continuation ->
        val remote = connectedOrNull()
        if (remote == null) {
            continuation.resume(Result.failure(IllegalStateException("Spotify isn't connected.")))
            return@suspendCancellableCoroutine
        }
        val call = remote.userApi.capabilities
        call.setResultCallback { capabilities ->
            if (continuation.isActive) continuation.resume(Result.success(capabilities.canPlayOnDemand))
        }
        call.setErrorCallback { error ->
            if (continuation.isActive) continuation.resume(Result.failure(error))
        }
    }

    suspend fun play(uri: String): Result<Unit> = command { it.playerApi.play(uri) }

    suspend fun queue(uri: String): Result<Unit> = command { it.playerApi.queue(uri) }

    suspend fun pause(): Result<Unit> = command { it.playerApi.pause() }

    suspend fun resume(): Result<Unit> = command { it.playerApi.resume() }

    suspend fun playerState(): RemoteNow? = suspendCancellableCoroutine { continuation ->
        val remote = connectedOrNull()
        if (remote == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        val call = remote.playerApi.playerState
        call.setResultCallback { state ->
            if (continuation.isActive) continuation.resume(state.toNow())
        }
        call.setErrorCallback {
            if (continuation.isActive) continuation.resume(null)
        }
    }

    fun subscribe(onState: (RemoteNow) -> Unit) {
        val remote = connectedOrNull() ?: return
        subscription?.cancel()
        subscription = remote.playerApi.subscribeToPlayerState()
            .setEventCallback { state ->
                val now = state.toNow()
                main.post { onState(now) }
            }
    }

    private suspend fun command(
        call: (SpotifyAppRemote) -> com.spotify.protocol.client.CallResult<*>,
    ): Result<Unit> = suspendCancellableCoroutine { continuation ->
        val remote = connectedOrNull()
        if (remote == null) {
            continuation.resume(Result.failure(IllegalStateException("Spotify isn't connected.")))
            return@suspendCancellableCoroutine
        }
        val pending = call(remote)
        pending.setResultCallback {
            if (continuation.isActive) continuation.resume(Result.success(Unit))
        }
        pending.setErrorCallback { error ->
            if (continuation.isActive) continuation.resume(Result.failure(error))
        }
    }

    private fun connectedOrNull(): SpotifyAppRemote? {
        val remote = appRemote
        return if (remote != null && remote.isConnected) remote else null
    }

    private fun PlayerState.toNow(): RemoteNow {
        val artists = track?.artists?.mapNotNull { it?.name }?.filter { it.isNotBlank() }.orEmpty()
        val name = artists.joinToString(", ").ifBlank { track?.artist?.name.orEmpty() }
        return RemoteNow(
            uri = track?.uri.orEmpty(),
            title = track?.name.orEmpty(),
            artist = name.ifBlank { "Unknown artist" },
            album = track?.album?.name.orEmpty(),
            durationMs = track?.duration ?: 0L,
            positionMs = playbackPosition.coerceAtLeast(0L),
            paused = isPaused,
        )
    }

    private fun explain(error: Throwable): String {
        return when (error.javaClass.simpleName) {
            "CouldNotFindSpotifyApp" -> "Install the Spotify app, then try again."
            "NotLoggedInException" -> "Open Spotify and log in on this phone, then try again."
            "UserNotAuthorizedException" ->
                "Spotify didn't authorize this app. Add this account's email under Users Management."
            "OfflineModeException" -> "Spotify is in offline mode. Turn that off in Spotify and try again."
            "SpotifyDisconnectedException",
            "SpotifyConnectionTerminatedException",
            -> "Spotify disconnected."
            "AuthenticationFailedException" ->
                "Spotify rejected the login. Check the client id, redirect URI, and the debug SHA-1."
            else -> error.message?.takeIf { it.isNotBlank() } ?: "Couldn't reach Spotify."
        }
    }
}
