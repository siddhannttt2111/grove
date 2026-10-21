package com.friendsvibe.autopilot.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.friendsvibe.autopilot.rank.SpotifyTrack
import com.friendsvibe.autopilot.spotify.SpotifySessionViewModel
import com.friendsvibe.autopilot.spotify.SpotifyUi

@Composable
fun SpotifyPickScreen(
    spotifyVm: SpotifySessionViewModel,
    onBack: () -> Unit,
    onPick: () -> Unit,
) {
    val ui by spotifyVm.ui.collectAsStateWithLifecycle()
    val activity = LocalContext.current as Activity
    val login = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        spotifyVm.onAuthResult(result.resultCode, result.data)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
            Text("Back", color = Mist, style = MaterialTheme.typography.labelLarge)
        }
        Text("Spotify", style = MaterialTheme.typography.headlineMedium, color = Paper)
        Text(
            "A separate session. Spotify plays the audio. This app searches and queues, and it does not download or mix it.",
            color = Mist,
            style = MaterialTheme.typography.bodyMedium,
        )
        when (val state = ui) {
            SpotifyUi.NeedsSetup -> Text(
                "This build has no Spotify client id, so the Spotify session stays closed. The phone library still works. Add spotify.client.id and rebuild. The friend-share notes list the allowlist steps.",
                color = Paper,
            )
            SpotifyUi.SignedOut -> ActionButton("Connect Spotify", {
                spotifyVm.connect(activity) { login.launch(it) }
            }, Modifier.fillMaxWidth())
            is SpotifyUi.Working -> Text(state.text, color = Paper)
            is SpotifyUi.Blocked -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.text, color = Paper)
                ActionButton("Try again", {
                    spotifyVm.connect(activity) { login.launch(it) }
                }, Modifier.fillMaxWidth())
            }
            is SpotifyUi.Picker -> SpotifyResults(
                state = state,
                onSearch = spotifyVm::search,
                onPick = { track ->
                    spotifyVm.choose(track)
                    onPick()
                },
            )
            is SpotifyUi.Session -> Text("A Spotify set is already going.", color = Paper)
        }
    }
}

@Composable
private fun SpotifyResults(
    state: SpotifyUi.Picker,
    onSearch: (String) -> Unit,
    onPick: (SpotifyTrack) -> Unit,
) {
    var query by remember(state.query) { mutableStateOf(state.query) }
    Text(state.listenerName, color = Calm, style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Song or artist") },
            singleLine = true,
            colors = fieldColors(),
        )
        ActionButton("Search", { onSearch(query) }, enabled = !state.searching)
    }
    if (state.searching) Text("Searching Spotify…", color = Mist)
    state.message?.let { Text(it, color = Paper, style = MaterialTheme.typography.bodyMedium) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(state.results, key = { it.id }) { track ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(track) }
                    .padding(vertical = 4.dp),
            ) {
                Text(track.title, color = Paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = Mist, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
        }
    }
}

@Composable
fun SpotifySessionScreen(
    spotifyVm: SpotifySessionViewModel,
    onBack: () -> Unit,
) {
    val ui by spotifyVm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { spotifyVm.beginChosen() }
    BackHandler {
        spotifyVm.leaveSession()
        onBack()
    }
    val session = ui as? SpotifyUi.Session
    if (session == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val text = when (val state = ui) {
                is SpotifyUi.Working -> state.text
                is SpotifyUi.Blocked -> state.text
                else -> "Pick a Spotify song first."
            }
            Text(text, color = Paper)
            TextButton(onClick = {
                spotifyVm.leaveSession()
                onBack()
            }) { Text("Back", color = Mist) }
        }
        return
    }
    val progress = if (session.durationMs <= 0L) 0f else session.positionMs.toFloat() / session.durationMs.toFloat()
    SetScreen(
        sourceLabel = "Spotify",
        sourceNote = "Audio stays in Spotify. Turn crossfade on in Spotify’s playback settings if you want one.",
        title = session.title,
        artist = session.artist,
        detail = session.detail,
        vibeLabel = session.vibe.label,
        progress = progress,
        playing = session.playing,
        upcoming = session.upcoming,
        banned = session.banned,
        status = session.status,
        onBack = {
            spotifyVm.leaveSession()
            onBack()
        },
        onToggle = spotifyVm::toggle,
        onSkip = spotifyVm::skip,
        onCalmer = spotifyVm::calmer,
        onHarder = spotifyVm::harder,
        onBan = spotifyVm::banArtist,
    )
}
