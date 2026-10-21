package com.friendsvibe.autopilot.spotify

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.friendsvibe.autopilot.rank.Names
import com.friendsvibe.autopilot.rank.SpotifyRanker
import com.friendsvibe.autopilot.rank.SpotifyTrack
import com.friendsvibe.autopilot.rank.Vibe
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed interface SpotifyUi {
    data object NeedsSetup : SpotifyUi
    data object SignedOut : SpotifyUi
    data class Working(val text: String) : SpotifyUi
    data class Blocked(val text: String) : SpotifyUi
    data class Picker(
        val listenerName: String,
        val query: String,
        val searching: Boolean,
        val results: List<SpotifyTrack>,
        val message: String?,
    ) : SpotifyUi

    data class Session(
        val title: String,
        val artist: String,
        val detail: String,
        val vibe: Vibe,
        val playing: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val upcoming: List<String>,
        val banned: List<String>,
        val status: String?,
    ) : SpotifyUi
}

class SpotifySessionViewModel(app: Application) : AndroidViewModel(app) {
    private val web = SpotifyWebApi()
    private val remote = SpotifyRemote(app)

    private var token: String? = null
    private var listenerName = ""
    private var query = ""
    private var results = emptyList<SpotifyTrack>()
    private var searching = false
    private var pickerMessage: String? = null
    private var chosen: SpotifyTrack? = null
    private var playingId: String? = null
    private var seed: SpotifyTrack? = null
    private var current: SpotifyTrack? = null
    private var pool = emptyList<SpotifyTrack>()
    private var savedIds = emptySet<String>()
    private var vibe = Vibe.Neutral
    private val banned = LinkedHashSet<String>()
    private val bannedLabels = mutableListOf<String>()
    private val skips = HashMap<String, Int>()
    private var committedNextId: String? = null
    private var inSession = false
    private var paused = true
    private var positionMs = 0L
    private var durationMs = 0L
    private var status: String? = null
    private var pollJob: Job? = null

    private val _ui = MutableStateFlow<SpotifyUi>(
        if (SpotifyConfig.isConfigured()) SpotifyUi.SignedOut else SpotifyUi.NeedsSetup,
    )
    val ui: StateFlow<SpotifyUi> = _ui.asStateFlow()

    fun loginIntent(activity: Activity): Intent? {
        if (!SpotifyConfig.isConfigured()) return null
        val request = AuthorizationRequest.Builder(
            SpotifyConfig.clientId(),
            AuthorizationResponse.Type.TOKEN,
            SpotifyConfig.REDIRECT_URI,
        )
            .setScopes(
                arrayOf(
                    "app-remote-control",
                    "user-read-private",
                    "user-library-read",
                ),
            )
            .build()
        return AuthorizationClient.createLoginActivityIntent(activity, request)
    }

    fun connect(activity: Activity, launchLogin: (Intent) -> Unit) {
        if (!remote.isInstalled()) {
            _ui.value = SpotifyUi.Blocked("Install the Spotify app, then come back. The phone library still works without it.")
            return
        }
        val existing = token
        if (existing.isNullOrBlank()) {
            val intent = loginIntent(activity)
            if (intent == null) {
                _ui.value = SpotifyUi.NeedsSetup
            } else {
                launchLogin(intent)
            }
        } else {
            viewModelScope.launch { openRemote(existing) }
        }
    }

    fun onAuthResult(resultCode: Int, data: Intent?) {
        val response = AuthorizationClient.getResponse(resultCode, data)
        when (response.type) {
            AuthorizationResponse.Type.TOKEN -> {
                val access = response.accessToken
                if (access.isNullOrBlank()) {
                    _ui.value = SpotifyUi.Blocked("Spotify didn't return a token.")
                } else {
                    token = access
                    viewModelScope.launch { openRemote(access) }
                }
            }
            AuthorizationResponse.Type.ERROR -> {
                _ui.value = SpotifyUi.Blocked(
                    response.error?.takeIf { it.isNotBlank() } ?: "Spotify login failed.",
                )
            }
            else -> {
                if (token == null) _ui.value = SpotifyUi.SignedOut
            }
        }
    }

    fun search(text: String) {
        query = text
        val access = token
        if (access.isNullOrBlank()) {
            _ui.value = SpotifyUi.SignedOut
            return
        }
        if (text.isBlank()) {
            results = emptyList()
            pickerMessage = "Type a song, then search."
            showPicker()
            return
        }
        searching = true
        pickerMessage = null
        showPicker()
        viewModelScope.launch {
            try {
                results = web.search(access, text)
                pickerMessage = if (results.isEmpty()) "No tracks came back for that search." else null
            } catch (error: SpotifyApiException) {
                results = emptyList()
                pickerMessage = explainApi(error)
            } finally {
                searching = false
                if (!inSession) showPicker()
            }
        }
    }

    fun choose(track: SpotifyTrack) {
        chosen = track
    }

    fun beginChosen() {
        val track = chosen ?: return
        if (playingId == track.id && _ui.value is SpotifyUi.Session) return
        play(track)
    }

    fun toggle() {
        viewModelScope.launch {
            val result = if (paused) remote.resume() else remote.pause()
            result.onFailure { error -> status = explainRemote(error) }
            showSession()
        }
    }

    fun skip() {
        val now = current ?: return
        skips[now.id] = (skips[now.id] ?: 0) + 1
        viewModelScope.launch {
            if (!reloadPool()) return@launch
            val next = ranked(setOf(now.id)).firstOrNull()
            if (next == null) {
                status = "Nothing else to queue."
                showSession()
                return@launch
            }
            committedNextId = null
            remote.play(next.uri).onFailure { error -> status = explainRemote(error) }
            current = next
            showSession()
        }
    }

    fun calmer() {
        vibe = vibe.calmer()
        steer()
    }

    fun harder() {
        vibe = vibe.harder()
        steer()
    }

    fun banArtist() {
        val artist = current?.artists?.firstOrNull { Names.key(it).isNotEmpty() }
        if (artist == null) {
            status = "This track has no artist name to ban."
            showSession()
            return
        }
        banned += Names.key(artist)
        if (artist !in bannedLabels) bannedLabels += artist
        skip()
    }

    fun leaveSession() {
        inSession = false
        pollJob?.cancel()
        viewModelScope.launch { remote.pause() }
        status = null
        showPicker()
    }

    fun stopForOtherSession() {
        inSession = false
        pollJob?.cancel()
        viewModelScope.launch {
            remote.pause()
            remote.disconnect()
        }
        if (token != null) {
            pickerMessage = "Spotify is paused. Connect again when you want that session."
            showPicker()
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        remote.disconnect()
        super.onCleared()
    }

    private fun play(track: SpotifyTrack) {
        playingId = track.id
        seed = track
        current = track
        vibe = Vibe.Neutral
        banned.clear()
        bannedLabels.clear()
        skips.clear()
        committedNextId = null
        status = null
        inSession = true
        viewModelScope.launch {
            _ui.value = SpotifyUi.Working("Starting that song in Spotify…")
            if (!reloadPool()) return@launch
            val started = remote.play(track.uri)
            if (started.isFailure) {
                inSession = false
                _ui.value = SpotifyUi.Blocked(explainRemote(started.exceptionOrNull() ?: IllegalStateException("Spotify didn't start.")))
                return@launch
            }
            watch()
            showSession()
        }
    }

    private fun steer() {
        committedNextId = null
        status = null
        viewModelScope.launch {
            if (!reloadPool()) return@launch
            if (inSession) showSession()
        }
    }

    private suspend fun reloadPool(): Boolean {
        return try {
            refreshPool()
            true
        } catch (error: SpotifyApiException) {
            inSession = false
            _ui.value = SpotifyUi.Blocked(explainApi(error))
            false
        }
    }

    private fun watch() {
        remote.subscribe { state -> onRemote(state) }
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive && inSession) {
                delay(2_000)
                val state = remote.playerState() ?: continue
                onRemote(state)
            }
        }
    }

    private fun onRemote(state: RemoteNow) {
        if (!inSession || state.uri.isBlank()) return
        paused = state.paused
        positionMs = state.positionMs
        durationMs = state.durationMs
        val known = pool.firstOrNull { it.uri == state.uri }
        current = known ?: SpotifyTrack(
            id = state.uri.substringAfterLast(':'),
            uri = state.uri,
            title = state.title.ifBlank { "Spotify" },
            artists = listOf(state.artist),
            album = state.album,
            durationMs = state.durationMs,
        )
        if (committedNextId != null && committedNextId == current?.id) {
            committedNextId = null
        }
        val remaining = state.durationMs - state.positionMs
        val next = ranked(setOfNotNull(current?.id)).firstOrNull()
        val closeEnough = state.durationMs <= 0L || remaining in 1..1_500
        if (next != null && closeEnough && committedNextId != next.id) {
            committedNextId = next.id
            viewModelScope.launch { remote.queue(next.uri) }
        }
        showSession()
    }

    private suspend fun refreshPool() {
        val access = token ?: return
        val seedTrack = seed ?: return
        val found = mutableListOf<SpotifyTrack>()
        for (search in SpotifyRanker.searchQueries(seedTrack, vibe)) {
            try {
                found += web.search(access, search)
            } catch (error: SpotifyApiException) {
                if (error.status == 403 || error.status == 401) throw error
            }
        }
        try {
            val saved = web.savedTracks(access)
            savedIds = saved.map { it.id }.toSet()
            val seedArtists = seedTrack.artists.map { Names.key(it) }.filter { it.isNotEmpty() }.toSet()
            found += saved.filter { track ->
                track.artists.any { Names.key(it) in seedArtists }
            }
        } catch (_: SpotifyApiException) {
            savedIds = emptySet()
        }
        pool = (found + pool).distinctBy { it.id }.take(80)
    }

    private fun ranked(excluding: Set<String>): List<SpotifyTrack> {
        val seedTrack = seed ?: return emptyList()
        return SpotifyRanker.rank(seedTrack, pool, vibe, banned, savedIds, skips, excluding)
    }

    private suspend fun openRemote(access: String) {
        _ui.value = SpotifyUi.Working("Checking this Spotify account…")
        try {
            listenerName = web.profile(access).name
        } catch (error: SpotifyApiException) {
            _ui.value = SpotifyUi.Blocked(explainApi(error))
            return
        }
        if (!remote.isInstalled()) {
            _ui.value = SpotifyUi.Blocked("Install the Spotify app. This session plays there, and it never downloads the audio.")
            return
        }
        val connected = connectRemote()
        if (connected.isFailure) {
            _ui.value = SpotifyUi.Blocked(connected.exceptionOrNull()?.message ?: "Couldn't connect to Spotify.")
            return
        }
        val premium = remote.canPlayOnDemand()
        if (premium.isFailure) {
            remote.disconnect()
            _ui.value = SpotifyUi.Blocked(explainRemote(premium.exceptionOrNull() ?: IllegalStateException("Couldn't read Spotify playback.")))
            return
        }
        if (premium.getOrDefault(false).not()) {
            remote.disconnect()
            _ui.value = SpotifyUi.Blocked(
                "Spotify Premium is required. This account can't play a chosen song on demand, so the set stays in the phone library.",
            )
            return
        }
        pickerMessage = null
        showPicker()
    }

    private suspend fun connectRemote(): Result<Unit> = suspendCancellableCoroutine { continuation ->
        remote.connect(
            onReady = {
                if (continuation.isActive) continuation.resume(Result.success(Unit))
            },
            onError = { message ->
                if (continuation.isActive) continuation.resume(Result.failure(IllegalStateException(message)))
            },
        )
    }

    private fun showPicker() {
        _ui.value = SpotifyUi.Picker(listenerName, query, searching, results, pickerMessage)
    }

    private fun showSession() {
        val now = current ?: return
        if (!inSession) return
        val upcoming = ranked(setOf(now.id)).map { "${it.title} — ${it.artist}" }
        _ui.value = SpotifyUi.Session(
            title = now.title,
            artist = now.artist,
            detail = now.album,
            vibe = vibe,
            playing = !paused,
            positionMs = positionMs,
            durationMs = durationMs,
            upcoming = upcoming,
            banned = bannedLabels.toList(),
            status = status,
        )
    }

    private fun explainApi(error: SpotifyApiException): String {
        return when (error.status) {
            401 -> "Spotify sign-in expired. Connect again."
            403 -> "This Spotify account isn't on the allowlist. Development Mode allows 5 people. Add their email under Users Management."
            429 -> "Spotify asked the app to slow down. Wait a moment, then search again."
            else -> error.message ?: "Spotify couldn't answer."
        }
    }

    private fun explainRemote(error: Throwable): String {
        return error.message?.takeIf { it.isNotBlank() } ?: "Spotify didn't take that command."
    }
}
