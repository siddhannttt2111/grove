package com.grove.autopilot.spotify

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import com.grove.autopilot.BuildConfig
import com.grove.autopilot.data.GrovePrefs
import com.grove.autopilot.model.Energy
import com.grove.autopilot.model.PlayUi
import com.grove.autopilot.model.Source
import com.grove.autopilot.model.Track
import com.grove.autopilot.model.UpNext
import com.grove.autopilot.player.PlaybackStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

data class SpotifyStatus(
    val connected: Boolean = false,
    val name: String = "",
    val message: String = "",
    val results: List<Track> = emptyList(),
    val busy: Boolean = false,
    val clientReady: Boolean = false,
)

class SpotifyClient(context: Context) {
    private val prefs = GrovePrefs(context.applicationContext)
    private val http = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build()
    private val gate = Mutex()
    val status = MutableStateFlow(SpotifyStatus())
    private var activeGen = 0
    private var seed: Track? = null
    private var queued = listOf<Track>()
    private var energy = Energy.Steady
    private val banned = mutableSetOf<String>()
    private var note = "Playback stays inside Spotify."
    private var market = "US"

    init {
        refreshReady()
    }

    fun refreshReady() {
        status.value = status.value.copy(clientReady = clientId().isNotBlank())
    }

    fun clientId(): String = prefs.clientId.ifBlank { BuildConfig.SPOTIFY_CLIENT_ID }

    fun setClientId(value: String) {
        val trimmed = value.trim()
        if (trimmed != prefs.clientId) prefs.clearSession()
        prefs.clientId = trimmed
        status.value = SpotifyStatus(
            clientReady = trimmed.isNotBlank(),
            message = if (trimmed.isBlank()) {
                "Paste the Spotify client id from the developer dashboard."
            } else {
                "Client id saved. Connect Spotify."
            },
        )
    }

    fun beginLogin(context: Context) {
        val id = clientId()
        if (id.isBlank()) {
            status.value = status.value.copy(message = "Add a Spotify client id in Settings first.")
            return
        }
        val verifier = randomUrl(64)
        prefs.verifier = verifier
        val scope = "user-read-private user-read-email user-read-playback-state user-modify-playback-state user-read-currently-playing"
        val uri = Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", id)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", BuildConfig.SPOTIFY_REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", sha256(verifier))
            .appendQueryParameter("scope", scope)
            .appendQueryParameter("show_dialog", "true")
            .build()
        CustomTabsIntent.Builder().build().launchUrl(context, uri)
    }

    fun yieldSession() {
        activeGen = 0
    }

    suspend fun restore() {
        if (prefs.refreshToken.isBlank()) {
            refreshReady()
            return
        }
        val access = token() ?: run {
            status.value = status.value.copy(connected = false, message = "Spotify login expired. Connect again.")
            return
        }
        loadProfile(access)
    }

    suspend fun exchange(code: String) {
        val verifier = prefs.verifier
        if (code.isBlank() || verifier.isBlank()) return
        status.value = status.value.copy(busy = true, message = "Connecting Spotify…")
        val json = withContext(Dispatchers.IO) {
            val body = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", BuildConfig.SPOTIFY_REDIRECT_URI)
                .add("client_id", clientId())
                .add("code_verifier", verifier)
                .build()
            val response = http.newCall(Request.Builder().url(TOKEN).post(body).build()).execute()
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) null else JSONObject(text)
        }
        if (json == null) {
            status.value = status.value.copy(busy = false, message = "Spotify did not accept that login.")
            return
        }
        storeToken(json)
        loadProfile(prefs.accessToken)
    }

    suspend fun search(query: String) {
        if (query.isBlank()) {
            status.value = status.value.copy(results = emptyList())
            return
        }
        if (token() == null) {
            status.value = status.value.copy(message = "Connect Spotify in Settings.")
            return
        }
        val text = get("/search?type=track&limit=20&q=${Uri.encode(query)}") ?: return
        val tracks = parseTracks(JSONObject(text).optJSONObject("tracks")?.optJSONArray("items"))
        status.value = status.value.copy(results = tracks, message = "")
    }

    suspend fun startSet(track: Track) {
        activeGen = PlaybackStore.bump()
        val gen = activeGen
        seed = track
        banned.clear()
        energy = PlaybackStore.preferredEnergy
        note = "Playback stays inside Spotify. Turn on crossfade in the Spotify app if you want a fade."
        status.value = status.value.copy(busy = true)
        publish(track, playing = false, position = 0)
        if (!prepareDevice(gen)) {
            fail(gen, "Open Spotify once so this phone shows up as a device, then start the set again.")
            return
        }
        val list = collect(track, energy)
        if (gen != PlaybackStore.generation) return
        queued = list.drop(1)
        if (!playUri(track.uri, gen)) return
        queued.take(6).forEach { item ->
            if (gen != PlaybackStore.generation) return
            post("/me/player/queue?uri=${Uri.encode(item.uri)}")
        }
        status.value = status.value.copy(busy = false)
        publish(track, playing = true, position = 0)
    }

    suspend fun replan(next: Energy) {
        val current = seed ?: return
        energy = next
        PlaybackStore.preferredEnergy = next
        val gen = activeGen
        if (gen == 0 || gen != PlaybackStore.generation) return
        val list = collect(current, next)
        if (gen != PlaybackStore.generation) return
        val start = list.firstOrNull() ?: return
        queued = list.drop(1)
        if (!playUri(start.uri, gen)) return
        queued.take(6).forEach { post("/me/player/queue?uri=${Uri.encode(it.uri)}") }
        publish(start, playing = true, position = 0)
    }

    suspend fun toggle() {
        if (activeGen == 0 || activeGen != PlaybackStore.generation) return
        if (PlaybackStore.ui.value.playing) put("/me/player/pause", JSONObject()) else put("/me/player/play", JSONObject())
        refreshPlayer()
    }

    suspend fun next() {
        if (activeGen == 0) return
        post("/me/player/next")
        refreshPlayer()
    }

    suspend fun previous() {
        if (activeGen == 0) return
        post("/me/player/previous")
        refreshPlayer()
    }

    suspend fun banCurrent() {
        val artist = PlaybackStore.ui.value.artist
        if (artist.isBlank()) return
        banned += artist.lowercase()
        note = "Banned $artist for this set"
        next()
    }

    suspend fun pauseQuietly() {
        if (prefs.accessToken.isBlank() && prefs.refreshToken.isBlank()) return
        yieldSession()
        runCatching { put("/me/player/pause", JSONObject()) }
    }

    suspend fun stop() {
        activeGen = 0
        runCatching { put("/me/player/pause", JSONObject()) }
        if (PlaybackStore.ui.value.source == Source.Spotify) {
            PlaybackStore.ui.value = PlayUi(
                energy = PlaybackStore.preferredEnergy,
                crossfade = PlaybackStore.crossfade,
            )
        }
    }

    suspend fun refreshPlayer() {
        val gen = activeGen
        if (gen == 0 || gen != PlaybackStore.generation) return
        val text = get("/me/player") ?: return
        if (text.isBlank() || gen != PlaybackStore.generation) return
        val item = JSONObject(text).optJSONObject("item") ?: return
        val track = parseTrack(item) ?: return
        publish(track, JSONObject(text).optBoolean("is_playing"), JSONObject(text).optLong("progress_ms"))
    }

    private suspend fun collect(track: Track, mood: Energy): List<Track> {
        val found = LinkedHashMap<String, Track>()
        found[track.id] = track
        if (track.artistId.isNotBlank()) {
            val top = get("/artists/${track.artistId}/top-tracks?market=$market")
            if (top != null) {
                parseTracks(JSONObject(top).optJSONArray("tracks")).forEach { found.putIfAbsent(it.id, it) }
            }
        }
        val artistQuery = if (track.artist.isBlank()) track.title else "artist:\"${track.artist}\""
        searchInto(found, artistQuery)
        when (mood) {
            Energy.Calm -> searchInto(found, "${track.title} acoustic")
            Energy.Lift -> searchInto(found, "${track.title} remix")
            Energy.Steady -> Unit
        }
        return found.values.filter { item ->
            banned.none { ban -> item.artist.lowercase().contains(ban) }
        }.take(16)
    }

    private suspend fun searchInto(into: LinkedHashMap<String, Track>, query: String) {
        val text = get("/search?type=track&limit=10&q=${Uri.encode(query)}") ?: return
        parseTracks(JSONObject(text).optJSONObject("tracks")?.optJSONArray("items"))
            .forEach { into.putIfAbsent(it.id, it) }
    }

    private suspend fun prepareDevice(gen: Int): Boolean {
        val text = get("/me/player/devices") ?: return false
        if (gen != PlaybackStore.generation) return false
        val devices = JSONObject(text).optJSONArray("devices") ?: return false
        if (devices.length() == 0) return false
        var chosen: String? = null
        for (index in 0 until devices.length()) {
            val device = devices.getJSONObject(index)
            if (device.optBoolean("is_active")) {
                chosen = device.getString("id")
                break
            }
        }
        if (chosen == null) {
            for (index in 0 until devices.length()) {
                val device = devices.getJSONObject(index)
                if (device.optString("type") == "Smartphone") {
                    chosen = device.getString("id")
                    break
                }
            }
        }
        if (chosen == null) chosen = devices.getJSONObject(0).getString("id")
        put("/me/player", JSONObject().put("device_ids", JSONArray().put(chosen)).put("play", false))
        delay(450)
        return gen == PlaybackStore.generation
    }

    private suspend fun playUri(uri: String, gen: Int): Boolean {
        if (gen != PlaybackStore.generation) return false
        val code = put("/me/player/play", JSONObject().put("uris", JSONArray().put(uri)))
        when (code) {
            404 -> fail(gen, "Open Spotify on this phone, play anything once, then start the set again.")
            403 -> fail(gen, "This Spotify account needs Premium and a place on the app allowlist.")
            in 200..299 -> return true
            else -> fail(gen, "Spotify did not start playback ($code).")
        }
        return false
    }

    private fun publish(track: Track, playing: Boolean, position: Long) {
        if (activeGen != PlaybackStore.generation) return
        PlaybackStore.ui.value = PlayUi(
            source = Source.Spotify,
            playing = playing,
            title = track.title,
            artist = track.artist,
            album = track.album,
            artUri = track.artUri,
            positionMs = position,
            durationMs = track.durationMs,
            energy = energy,
            seedTitle = seed?.title ?: track.title,
            seedArtist = seed?.artist ?: track.artist,
            upcoming = queued.take(20).map { UpNext(it.id, it.title, it.artist, "Same session") },
            note = note,
            crossfade = false,
            generation = activeGen,
            anchorRealtimeMs = SystemClock.elapsedRealtime(),
        )
    }

    private fun fail(gen: Int, message: String) {
        activeGen = 0
        status.value = status.value.copy(busy = false, message = message)
        if (gen == PlaybackStore.generation) {
            PlaybackStore.ui.value = PlayUi(
                energy = PlaybackStore.preferredEnergy,
                note = message,
                crossfade = PlaybackStore.crossfade,
            )
        }
    }

    private suspend fun loadProfile(access: String) {
        val text = withContext(Dispatchers.IO) {
            http.newCall(
                Request.Builder()
                    .url("https://api.spotify.com/v1/me")
                    .header("Authorization", "Bearer $access")
                    .build(),
            ).execute().body?.string().orEmpty()
        }
        if (text.isBlank()) return
        val json = JSONObject(text)
        prefs.displayName = json.optString("display_name").ifBlank { json.optString("id") }
        market = json.optString("country").ifBlank { "US" }
        status.value = status.value.copy(
            connected = true,
            name = prefs.displayName,
            busy = false,
            clientReady = true,
            message = "Connected as ${prefs.displayName}.",
        )
    }

    private suspend fun token(): String? = gate.withLock {
        val current = prefs.accessToken
        if (current.isNotBlank() && System.currentTimeMillis() < prefs.expiryMs - 60_000) return@withLock current
        val refresh = prefs.refreshToken
        if (refresh.isBlank()) return@withLock null
        val json = withContext(Dispatchers.IO) {
            val body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refresh)
                .add("client_id", clientId())
                .build()
            val response = http.newCall(Request.Builder().url(TOKEN).post(body).build()).execute()
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) null else JSONObject(text)
        } ?: return@withLock null
        storeToken(json)
        prefs.accessToken
    }

    private fun storeToken(json: JSONObject) {
        prefs.accessToken = json.getString("access_token")
        if (json.has("refresh_token")) prefs.refreshToken = json.getString("refresh_token")
        prefs.expiryMs = System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000
    }

    private suspend fun get(path: String): String? {
        val access = token() ?: return null
        return withContext(Dispatchers.IO) {
            val response = http.newCall(
                Request.Builder().url(API + path).header("Authorization", "Bearer $access").build(),
            ).execute()
            if (response.code == 204) return@withContext ""
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) null else text
        }
    }

    private suspend fun put(path: String, json: JSONObject): Int = send("PUT", path, json)

    private suspend fun post(path: String): Int = send("POST", path, JSONObject())

    private suspend fun send(method: String, path: String, json: JSONObject): Int {
        val access = token() ?: return 401
        val body = json.toString().toRequestBody("application/json".toMediaType())
        return withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(API + path).header("Authorization", "Bearer $access")
            val request = if (method == "PUT") builder.put(body).build() else builder.post(body).build()
            http.newCall(request).execute().use { it.code }
        }
    }

    private fun parseTracks(array: JSONArray?): List<Track> {
        if (array == null) return emptyList()
        val tracks = ArrayList<Track>(array.length())
        for (index in 0 until array.length()) {
            parseTrack(array.optJSONObject(index) ?: continue)?.let(tracks::add)
        }
        return tracks
    }

    private fun parseTrack(item: JSONObject): Track? {
        val id = item.optString("id")
        val uri = item.optString("uri")
        if (id.isBlank() || uri.isBlank()) return null
        val artists = item.optJSONArray("artists")
        val artist = buildString {
            if (artists != null) {
                for (index in 0 until artists.length()) {
                    val name = artists.optJSONObject(index)?.optString("name").orEmpty()
                    if (name.isBlank()) continue
                    if (isNotEmpty()) append(", ")
                    append(name)
                }
            }
        }
        val album = item.optJSONObject("album")
        return Track(
            id = id,
            title = item.optString("name").ifBlank { "Untitled" },
            artist = artist,
            album = album?.optString("name").orEmpty(),
            folder = "",
            genre = "",
            durationMs = item.optLong("duration_ms"),
            uri = uri,
            artUri = album?.optJSONArray("images")?.optJSONObject(0)?.optString("url"),
            source = Source.Spotify,
            artistId = artists?.optJSONObject(0)?.optString("id").orEmpty(),
        )
    }

    private fun randomUrl(size: Int): String {
        val bytes = ByteArray(size)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    companion object {
        private const val TOKEN = "https://accounts.spotify.com/api/token"
        private const val API = "https://api.spotify.com/v1"
    }
}
