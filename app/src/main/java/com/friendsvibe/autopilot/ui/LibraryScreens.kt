package com.friendsvibe.autopilot.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.friendsvibe.autopilot.local.LibraryUi
import com.friendsvibe.autopilot.local.LibraryViewModel
import com.friendsvibe.autopilot.local.LocalSessionViewModel
import com.friendsvibe.autopilot.local.LocalTrack
import com.friendsvibe.autopilot.spotify.SpotifySessionViewModel

@Composable
fun LibraryPickScreen(
    libraryVm: LibraryViewModel,
    onBack: () -> Unit,
    onPick: (Long) -> Unit,
) {
    val context = LocalContext.current
    val ui by libraryVm.ui.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        libraryVm.onPermission(granted)
    }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, audioPermission()) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) libraryVm.onPermission(true) else permission.launch(audioPermission())
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
            Text("Back", color = Mist, style = MaterialTheme.typography.labelLarge)
        }
        Text("Phone library", style = MaterialTheme.typography.headlineMedium, color = Paper)
        Text(
            "Pick the song the set should start from.",
            color = Mist,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        when (val state = ui) {
            LibraryUi.Idle, LibraryUi.Loading -> Text("Reading songs on this phone…", color = Paper)
            LibraryUi.NeedsPermission -> PermissionCopy(
                onAsk = { permission.launch(audioPermission()) },
                onSettings = {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    )
                    context.startActivity(intent)
                },
            )
            LibraryUi.Empty -> Text("No music files turned up. Add some songs to this phone and come back.", color = Paper)
            is LibraryUi.Failed -> Text(state.message, color = Clay)
            is LibraryUi.Ready -> TrackPicker(state.tracks, onPick)
        }
    }
}

@Composable
private fun PermissionCopy(onAsk: () -> Unit, onSettings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("The phone library needs permission to read audio files on this device.", color = Paper)
        ActionButton("Allow audio access", onAsk, Modifier.fillMaxWidth())
        TextButton(onClick = onSettings) { Text("Open app settings", color = Mist) }
    }
}

@Composable
private fun TrackPicker(tracks: List<LocalTrack>, onPick: (Long) -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(query, tracks) {
        val needle = query.trim()
        tracks.filter { track ->
            needle.isEmpty() ||
                track.title.contains(needle, ignoreCase = true) ||
                track.artist.contains(needle, ignoreCase = true) ||
                track.album.contains(needle, ignoreCase = true)
        }.take(200)
    }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search title, artist, album") },
        singleLine = true,
        colors = fieldColors(),
    )
    Text(
        "${tracks.size} songs",
        color = Mist,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 8.dp),
    )
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(shown, key = { it.id }) { track ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(track.id) }
                    .padding(vertical = 4.dp),
            ) {
                Text(track.title, color = Paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.displayArtist, color = Mist, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
        }
    }
}

@Composable
fun LocalSessionScreen(
    trackId: Long,
    libraryVm: LibraryViewModel,
    spotifyVm: SpotifySessionViewModel,
    onBack: () -> Unit,
) {
    val sessionVm: LocalSessionViewModel = viewModel()
    val ui by sessionVm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var missing by remember { mutableStateOf(false) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(trackId) {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        spotifyVm.stopForOtherSession()
        val track = libraryVm.track(trackId)
        if (track == null) {
            missing = true
            return@LaunchedEffect
        }
        sessionVm.start(track, libraryVm.tracks()) { seed, candidates ->
            libraryVm.enrich(seed, candidates)
        }
    }
    BackHandler {
        sessionVm.close()
        onBack()
    }
    val track = ui.current
    if (missing && track == null) {
        Column(Modifier.padding(24.dp).statusBarsPadding()) {
            Text("That song isn't in the library anymore.", color = Paper)
            TextButton(onClick = onBack) { Text("Back", color = Mist) }
        }
        return
    }
    val progress = if (ui.durationMs <= 0L) 0f else ui.positionMs.toFloat() / ui.durationMs.toFloat()
    SetScreen(
        sourceLabel = "Phone library",
        sourceNote = "Crossfade runs on this phone, between your files.",
        title = track?.title ?: "Scoring the library…",
        artist = track?.displayArtist ?: "",
        detail = track?.detail().orEmpty(),
        vibeLabel = ui.vibe.label,
        progress = progress,
        playing = ui.playing,
        upcoming = ui.upcoming.map { "${it.title} — ${it.displayArtist}" },
        banned = ui.banned,
        status = ui.status,
        onBack = {
            sessionVm.close()
            onBack()
        },
        onToggle = sessionVm::toggle,
        onSkip = sessionVm::skip,
        onCalmer = sessionVm::calmer,
        onHarder = sessionVm::harder,
        onBan = sessionVm::banArtist,
    )
}

private fun audioPermission(): String {
    return if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
}

@Composable
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Paper,
    unfocusedTextColor = Paper,
    focusedBorderColor = Amber,
    unfocusedBorderColor = Line,
    cursorColor = Amber,
    focusedPlaceholderColor = Mist,
    unfocusedPlaceholderColor = Mist,
)
