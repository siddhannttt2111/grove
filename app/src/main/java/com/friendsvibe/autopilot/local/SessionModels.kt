package com.friendsvibe.autopilot.local

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.friendsvibe.autopilot.rank.LibrarySong
import com.friendsvibe.autopilot.rank.Names
import com.friendsvibe.autopilot.rank.TrackScorer
import com.friendsvibe.autopilot.rank.Vibe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LocalSessionUi(
    val current: LocalTrack? = null,
    val upcoming: List<LocalTrack> = emptyList(),
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val vibe: Vibe = Vibe.Neutral,
    val banned: List<String> = emptyList(),
    val status: String? = null,
)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val reader = LibraryReader(app)
    private val probe = TempoProbe(app)
    private val bpmCache = HashMap<Long, Int>()
    private var tracks: List<LocalTrack> = emptyList()

    private val _ui = MutableStateFlow<LibraryUi>(LibraryUi.Idle)
    val ui: StateFlow<LibraryUi> = _ui.asStateFlow()

    fun onPermission(granted: Boolean) {
        if (!granted) {
            _ui.value = LibraryUi.NeedsPermission
            return
        }
        if (_ui.value is LibraryUi.Loading || _ui.value is LibraryUi.Ready || _ui.value is LibraryUi.Empty) return
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = LibraryUi.Loading
            val loaded = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { reader.load() } }
            loaded.onSuccess { found ->
                tracks = found
                _ui.value = if (found.isEmpty()) LibraryUi.Empty else LibraryUi.Ready(found)
            }.onFailure {
                _ui.value = LibraryUi.Failed("Couldn't read the phone library.")
            }
        }
    }

    fun track(id: Long): LocalTrack? = tracks.find { it.id == id }

    fun tracks(): List<LocalTrack> = tracks

    suspend fun enrich(seed: LocalTrack, candidates: List<LocalTrack>): Pair<LocalTrack, List<LocalTrack>> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            var decodes = 0
            fun enrichOne(track: LocalTrack): LocalTrack {
                if (track.bpm != null) return track
                bpmCache[track.id]?.let { return track.copy(bpm = it) }
                val tagged = probe.readTag(track.uri)
                if (tagged != null) {
                    bpmCache[track.id] = tagged
                    return track.copy(bpm = tagged)
                }
                if (decodes >= 8) return track
                decodes += 1
                val estimated = probe.estimate(track.uri) ?: return track
                bpmCache[track.id] = estimated
                return track.copy(bpm = estimated)
            }
            val top = candidates.take(12).map(::enrichOne)
            enrichOne(seed) to (top + candidates.drop(12))
        }
    }
}

sealed interface LibraryUi {
    data object Idle : LibraryUi
    data object NeedsPermission : LibraryUi
    data object Loading : LibraryUi
    data object Empty : LibraryUi
    data class Ready(val tracks: List<LocalTrack>) : LibraryUi
    data class Failed(val message: String) : LibraryUi
}

class LocalSessionViewModel(app: Application) : AndroidViewModel(app) {
    private var player: CrossfadePlayer? = null
    private var bound = false
    private var closed = false
    private var observing = false
    private var startedFor: Long? = null
    private var pending: List<LocalTrack>? = null
    private var seed: LibrarySong? = null
    private var songs: List<LibrarySong> = emptyList()
    private var byId: Map<Long, LocalTrack> = emptyMap()
    private var vibe = Vibe.Neutral
    private val banned = LinkedHashSet<String>()
    private val bannedLabels = mutableListOf<String>()
    private val skipCounts = HashMap<Long, Int>()
    private val artistSkipCounts = HashMap<String, Int>()

    private val _ui = MutableStateFlow(LocalSessionUi(status = "Opening the phone library…"))
    val ui: StateFlow<LocalSessionUi> = _ui.asStateFlow()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val local = binder as? LocalPlaybackService.LocalBinder ?: return
            player = local.player
            local.player.onUserSkip = { skip() }
            observe()
            pending?.let { queue ->
                pending = null
                local.player.playQueue(queue)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            player = null
            bound = false
        }
    }

    init {
        val context = getApplication<Application>()
        val intent = Intent(context, LocalPlaybackService::class.java)
        ContextCompat.startForegroundService(context, intent)
        bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun start(
        seedTrack: LocalTrack,
        library: List<LocalTrack>,
        enrich: suspend (LocalTrack, List<LocalTrack>) -> Pair<LocalTrack, List<LocalTrack>>,
    ) {
        if (startedFor == seedTrack.id) return
        startedFor = seedTrack.id
        viewModelScope.launch {
            _ui.update { it.copy(status = "Scoring the library…") }
            val others = library.filter { it.id != seedTrack.id }
            val (enrichedSeed, enriched) = enrich(seedTrack, others)
            byId = (enriched + enrichedSeed).associateBy { it.id }
            songs = byId.values.map { it.toSong() }
            seed = enrichedSeed.toSong()
            vibe = Vibe.Neutral
            banned.clear()
            bannedLabels.clear()
            skipCounts.clear()
            artistSkipCounts.clear()
            val queue = listOf(enrichedSeed) + upcoming(enrichedSeed.id).take(30)
            val engine = player
            if (engine == null) {
                pending = queue
            } else {
                engine.onUserSkip = { skip() }
                engine.playQueue(queue)
            }
            _ui.update {
                it.copy(
                    vibe = vibe,
                    banned = bannedLabels.toList(),
                    status = if (queue.size == 1) "This is the only song the library could score." else null,
                )
            }
        }
    }

    fun toggle() {
        player?.toggle()
    }

    fun skip() {
        val engine = player ?: return
        val current = engine.snapshot.value.current ?: return
        recordSkip(current)
        val next = upcoming(current.id)
        if (next.isEmpty()) {
            engine.stopPlayback()
            _ui.update { it.copy(status = "Nothing left to play.", banned = bannedLabels.toList(), vibe = vibe) }
            return
        }
        engine.onUserSkip = { skip() }
        engine.playQueue(next.take(31))
        _ui.update { it.copy(status = null, banned = bannedLabels.toList(), vibe = vibe) }
    }

    fun calmer() {
        vibe = vibe.calmer()
        restage()
    }

    fun harder() {
        vibe = vibe.harder()
        restage()
    }

    fun banArtist() {
        val current = player?.snapshot?.value?.current ?: return
        val key = Names.key(current.artist)
        if (key.isEmpty()) {
            _ui.update { it.copy(status = "This track has no artist name to ban.") }
            return
        }
        banned += key
        if (current.artist !in bannedLabels) bannedLabels += current.artist
        skip()
    }

    fun close() {
        if (closed) return
        closed = true
        player?.onUserSkip = null
        player?.stopPlayback()
        val context = getApplication<Application>()
        if (bound) {
            runCatching { context.unbindService(connection) }
            bound = false
        }
        context.stopService(Intent(context, LocalPlaybackService::class.java))
    }

    override fun onCleared() {
        close()
        super.onCleared()
    }

    private fun restage() {
        val engine = player ?: return
        val current = engine.snapshot.value.current ?: return
        engine.replaceUpcoming(upcoming(current.id).take(30))
        _ui.update { it.copy(vibe = vibe, status = null, banned = bannedLabels.toList()) }
    }

    private fun upcoming(currentId: Long?): List<LocalTrack> {
        val seedSong = seed ?: return emptyList()
        return TrackScorer.rank(seedSong, songs, vibe, banned, skipCounts, artistSkipCounts)
            .filter { it.id != currentId }
            .mapNotNull { byId[it.id] }
    }

    private fun recordSkip(track: LocalTrack) {
        skipCounts[track.id] = (skipCounts[track.id] ?: 0) + 1
        val artist = Names.key(track.artist)
        if (artist.isNotEmpty()) {
            artistSkipCounts[artist] = (artistSkipCounts[artist] ?: 0) + 1
        }
    }

    private fun observe() {
        if (observing) return
        val engine = player ?: return
        observing = true
        viewModelScope.launch {
            engine.snapshot.collect { snap ->
                _ui.update {
                    it.copy(
                        current = snap.current,
                        upcoming = snap.upcoming,
                        playing = snap.playing,
                        positionMs = snap.positionMs,
                        durationMs = snap.durationMs,
                        vibe = vibe,
                        banned = bannedLabels.toList(),
                    )
                }
            }
        }
    }
}
