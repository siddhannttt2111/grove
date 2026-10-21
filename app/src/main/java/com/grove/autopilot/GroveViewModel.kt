package com.grove.autopilot

import android.app.Application
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.grove.autopilot.data.GrovePrefs
import com.grove.autopilot.data.LibraryRepository
import com.grove.autopilot.model.Energy
import com.grove.autopilot.model.Source
import com.grove.autopilot.model.Track
import com.grove.autopilot.player.Command
import com.grove.autopilot.player.PlaybackStore
import com.grove.autopilot.spotify.SpotifyClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GroveViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = GrovePrefs(app)
    private val spotify = SpotifyClient(app)

    val library = MutableStateFlow<List<Track>>(emptyList())
    val libraryMessage = MutableStateFlow("")
    val spotifyStatus = spotify.status

    init {
        PlaybackStore.crossfade = prefs.crossfade
        PlaybackStore.ui.value = PlaybackStore.ui.value.copy(
            energy = PlaybackStore.preferredEnergy,
            crossfade = prefs.crossfade,
        )
        viewModelScope.launch { spotify.restore() }
        viewModelScope.launch {
            while (isActive) {
                delay(5_000)
                spotify.refreshPlayer()
            }
        }
    }

    fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(getApplication(), permission) == PackageManager.PERMISSION_GRANTED
    }

    fun refreshLibrary() {
        if (!hasAudioPermission()) {
            libraryMessage.value = "Allow music access to build a set from this phone."
            return
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val tracks = LibraryRepository.load(getApplication())
                library.value = tracks
                PlaybackStore.phoneLibrary = tracks
                libraryMessage.value = if (tracks.isEmpty()) {
                    "No music files showed up. Add songs to this phone, then come back."
                } else {
                    ""
                }
            } catch (_: SecurityException) {
                libraryMessage.value = "Allow music access to build a set from this phone."
            }
        }
    }

    fun onSpotifyRedirect(uri: Uri?) {
        if (uri?.scheme != "grove") return
        val error = uri.getQueryParameter("error")
        if (error != null) {
            spotify.status.value = spotify.status.value.copy(message = "Spotify login was cancelled.")
            return
        }
        val code = uri.getQueryParameter("code") ?: return
        viewModelScope.launch { spotify.exchange(code) }
    }

    fun connectSpotify(context: Context) = spotify.beginLogin(context)

    fun savedClientId(): String = spotify.clientId()

    fun saveClientId(value: String) = spotify.setClientId(value)

    fun disconnectSpotify() {
        prefs.clearSession()
        spotify.refreshReady()
        spotify.status.value = spotify.status.value.copy(
            connected = false,
            name = "",
            message = "Spotify disconnected.",
            clientReady = spotify.clientId().isNotBlank(),
        )
    }

    fun searchSpotify(query: String) {
        viewModelScope.launch { spotify.search(query) }
    }

    fun playPhone(track: Track) {
        viewModelScope.launch {
            spotify.pauseQuietly()
            PlaybackStore.phoneLibrary = library.value
            PlaybackStore.send(getApplication(), Command.Play(track))
        }
    }

    fun playSpotify(track: Track) {
        viewModelScope.launch {
            if (PlaybackStore.ui.value.source == Source.Phone) {
                PlaybackStore.send(getApplication(), Command.Stop)
            }
            spotify.startSet(track)
        }
    }

    fun toggle() {
        if (PlaybackStore.ui.value.source == Source.Spotify) {
            viewModelScope.launch { spotify.toggle() }
        } else {
            PlaybackStore.send(getApplication(), Command.Toggle)
        }
    }

    fun next() {
        if (PlaybackStore.ui.value.source == Source.Spotify) {
            viewModelScope.launch { spotify.next() }
        } else {
            PlaybackStore.send(getApplication(), Command.Next)
        }
    }

    fun previous() {
        if (PlaybackStore.ui.value.source == Source.Spotify) {
            viewModelScope.launch { spotify.previous() }
        } else {
            PlaybackStore.send(getApplication(), Command.Previous)
        }
    }

    fun seek(positionMs: Long) {
        if (PlaybackStore.ui.value.source == Source.Phone) {
            PlaybackStore.send(getApplication(), Command.Seek(positionMs))
        }
    }

    fun setEnergy(energy: Energy) {
        PlaybackStore.preferredEnergy = energy
        when (PlaybackStore.ui.value.source) {
            Source.Phone -> PlaybackStore.send(getApplication(), Command.EnergyChange(energy))
            Source.Spotify -> viewModelScope.launch { spotify.replan(energy) }
            Source.None -> PlaybackStore.ui.value = PlaybackStore.ui.value.copy(energy = energy)
        }
    }

    fun ban() {
        when (PlaybackStore.ui.value.source) {
            Source.Phone -> PlaybackStore.send(getApplication(), Command.Ban)
            Source.Spotify -> viewModelScope.launch { spotify.banCurrent() }
            Source.None -> Unit
        }
    }

    fun stop() {
        if (PlaybackStore.ui.value.source == Source.Spotify) {
            viewModelScope.launch { spotify.stop() }
        } else {
            PlaybackStore.send(getApplication(), Command.Stop)
        }
    }

    fun setCrossfade(enabled: Boolean) {
        prefs.crossfade = enabled
        PlaybackStore.crossfade = enabled
        PlaybackStore.ui.value = PlaybackStore.ui.value.copy(crossfade = enabled)
    }
}
