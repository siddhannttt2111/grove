package com.grove.autopilot.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.grove.autopilot.GroveViewModel
import com.grove.autopilot.model.Energy
import com.grove.autopilot.model.PlayUi
import com.grove.autopilot.model.Source
import com.grove.autopilot.model.Track
import com.grove.autopilot.model.formatTime
import com.grove.autopilot.model.prettyArtist
import com.grove.autopilot.player.PlaybackStore
import com.grove.autopilot.theme.GroveColor
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1100)
        onDone()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(GroveColor.Paper)
            .clickable(onClick = onDone),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(GroveColor.Moss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text("GROVE", color = GroveColor.Ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 6.sp)
            Spacer(Modifier.height(8.dp))
            Text("One song. The set continues.", color = GroveColor.Mute, fontSize = 14.sp)
        }
    }
}

@Composable
fun HomeScreen(model: GroveViewModel, nav: NavHostController) {
    val library by model.library.collectAsState()
    val message by model.libraryMessage.collectAsState()
    val play by PlaybackStore.ui.collectAsState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        model.refreshLibrary()
    }
    LaunchedEffect(Unit) {
        if (model.hasAudioPermission()) model.refreshLibrary()
    }
    val starters = remember(library) { library.distinctBy { it.prettyArtist().lowercase() }.take(8) }
    Box(Modifier.fillMaxSize()) {
        GroveBackdrop()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Spacer(Modifier.height(18.dp))
            Text("Discover", color = GroveColor.Ink, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(18.dp))
            GlassCard {
                Text(
                    if (play.title.isBlank()) "ONE SONG" else "NOW PLAYING",
                    color = GroveColor.Moss,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.4.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (play.title.isBlank()) "Keep the same vibe" else play.title,
                    color = GroveColor.Ink,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (play.title.isBlank()) "Pick one song. Grove continues the set from your phone, or inside Spotify."
                    else play.artistLine(),
                    color = GroveColor.Mute,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(18.dp))
                if (!model.hasAudioPermission()) {
                    GroveButton("Allow music") {
                        launcher.launch(audioPermissions())
                    }
                } else if (play.title.isNotBlank()) {
                    GroveButton("Open the set") { nav.navigate("player") { launchSingleTop = true } }
                } else {
                    GroveButton("Pick a song") { nav.navigate("search?source=phone") }
                }
            }
            if (message.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(message, color = GroveColor.Mute, fontSize = 14.sp)
            }
            Spacer(Modifier.height(26.dp))
            Text("Daily mix", color = GroveColor.Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (starters.isEmpty()) {
                Text("Songs on this phone show up here.", color = GroveColor.Mute, fontSize = 14.sp)
            } else {
                starters.forEach { track ->
                    TrackRow(track.title, track.prettyArtist(), track.artUri) { model.playPhone(track) }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Spotify",
                color = GroveColor.Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            TrackRow("Continue in Spotify", "A separate session. Audio stays in Spotify.", null) {
                nav.navigate("search?source=spotify")
            }
        }
    }
}

@Composable
fun LibraryScreen(model: GroveViewModel, nav: NavHostController) {
    val library by model.library.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    val albums = remember(library) {
        library.filter { it.album.isNotBlank() }.groupBy { it.album }.map { (name, tracks) ->
            GroupCard(name, tracks.size, tracks.first().artUri, tracks.first().prettyArtist(), "album")
        }.sortedBy { it.title.lowercase() }
    }
    val artists = remember(library) {
        library.groupBy { it.prettyArtist() }.map { (name, tracks) ->
            GroupCard(name, tracks.size, tracks.first().artUri, "${tracks.size} songs", "artist")
        }.sortedBy { it.title.lowercase() }
    }
    val folders = remember(library) {
        library.filter { it.folder.isNotBlank() }.groupBy { it.folder }.map { (name, tracks) ->
            GroupCard(name, tracks.size, tracks.first().artUri, "Folder", "folder")
        }.sortedBy { it.title.lowercase() }
    }
    val cards = when (tab) {
        0 -> if (folders.isEmpty()) albums else folders
        1 -> albums
        else -> artists
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            "Library",
            modifier = Modifier.padding(start = 20.dp, top = 18.dp),
            color = GroveColor.Ink,
            fontSize = 34.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "${library.size} songs on this phone",
            modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 12.dp),
            color = GroveColor.Mute,
        )
        Row(Modifier.padding(horizontal = 12.dp)) {
            listOf("Playlists", "Albums", "Artists").forEachIndexed { index, label ->
                Column(
                    Modifier
                        .clickable { tab = index }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(label, color = if (tab == index) GroveColor.Ink else GroveColor.Mute, fontWeight = FontWeight.SemiBold)
                    Box(
                        Modifier
                            .padding(top = 6.dp)
                            .height(2.dp)
                            .width(if (tab == index) 28.dp else 0.dp)
                            .background(GroveColor.Moss),
                    )
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(cards, key = { it.kind + it.title }) { card ->
                Column(Modifier.clickable { openGroup(nav, card.kind, card.title) }) {
                    ArtThumb(card.art, Modifier.fillMaxWidth().aspectRatio(1f), 22.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(card.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = GroveColor.Ink, fontWeight = FontWeight.Medium)
                    Text("${card.count} songs", color = GroveColor.Mute, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun SearchScreen(model: GroveViewModel, nav: NavHostController, initialSource: String) {
    val library by model.library.collectAsState()
    val spotify by model.spotifyStatus.collectAsState()
    var query by remember { mutableStateOf("") }
    var source by remember { mutableStateOf(if (initialSource == "spotify") "spotify" else "phone") }
    LaunchedEffect(query, source) {
        if (source == "spotify") {
            delay(280)
            model.searchSpotify(query)
        }
    }
    val phoneHits = remember(library, query) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) emptyList()
        else library.filter {
            it.title.lowercase().contains(needle) ||
                it.artist.lowercase().contains(needle) ||
                it.album.lowercase().contains(needle) ||
                it.folder.lowercase().contains(needle)
        }.take(40)
    }
    val tiles = remember(library) {
        val folders = library.map { it.folder }.filter { it.isNotBlank() }.distinct().take(4).map { it to "folder" }
        val artists = library.map { it.prettyArtist() }.distinct().take(6).map { it to "artist" }
        (folders + artists).distinctBy { it.second + it.first }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(18.dp))
        Text("Search", color = GroveColor.Ink, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        GlassCard(Modifier.fillMaxWidth(), radius = 22.dp) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Artists, songs, albums") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = GroveColor.Moss) },
                singleLine = true,
                colors = fieldColors(),
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceChip("Phone", source == "phone") { source = "phone" }
            SourceChip("Spotify", source == "spotify") { source = "spotify" }
        }
        Spacer(Modifier.height(16.dp))
        if (source == "phone" && query.isBlank()) {
            Text("Browse", color = GroveColor.Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                itemsIndexed(tiles) { index, tile ->
                    val (name, kind) = tile
                    val filled = index % 2 == 1
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (filled) GroveColor.Moss else GroveColor.Paper)
                            .border(1.dp, GroveColor.Line, RoundedCornerShape(22.dp))
                            .clickable { openGroup(nav, kind, name) }
                            .padding(14.dp),
                    ) {
                        Text(name, color = if (filled) Color.White else GroveColor.Ink, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    }
                }
            }
        } else if (source == "spotify" && !spotify.connected) {
            Text(spotify.message.ifBlank { "Connect Spotify in Settings to search that library." }, color = GroveColor.Mute)
        } else {
            val rows = if (source == "spotify") spotify.results else phoneHits
            LazyColumn(Modifier.weight(1f)) {
                items(rows, key = { it.source.name + it.id }) { track ->
                    TrackRow(track.title, track.prettyArtist(), track.artUri) {
                        if (track.source == Source.Spotify) model.playSpotify(track) else model.playPhone(track)
                        nav.navigate("player") { launchSingleTop = true }
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerScreen(model: GroveViewModel, nav: NavHostController) {
    val play by PlaybackStore.ui.collectAsState()
    val position = livePosition(play)
    var dragging by remember { mutableStateOf(false) }
    var drag by remember { mutableFloatStateOf(0f) }
    val fraction = if (dragging) drag else if (play.durationMs > 0) position / play.durationMs.toFloat() else 0f
    Column(
        Modifier
            .fillMaxSize()
            .background(GroveColor.Paper)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        IconButton(onClick = { nav.popBackStack() }) {
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Close", tint = GroveColor.Ink)
        }
        ArtThumb(play.artUri, Modifier.fillMaxWidth().aspectRatio(1f), 28.dp)
        Spacer(Modifier.height(22.dp))
        Text(
            if (play.seedTitle.isBlank()) "FIRST SONG" else "FROM ${play.seedTitle.uppercase()}",
            color = GroveColor.Moss,
            fontSize = 11.sp,
            letterSpacing = 1.2.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(play.title.ifBlank { "Nothing playing" }, color = GroveColor.Ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Text(if (play.title.isBlank()) "Pick a song from Discover" else play.artistLine(), color = GroveColor.Mute, fontSize = 16.sp)
        Spacer(Modifier.height(12.dp))
        Slider(
            value = fraction.coerceIn(0f, 1f),
            onValueChange = {
                dragging = true
                drag = it
            },
            onValueChangeFinished = {
                if (play.source == Source.Phone && play.durationMs > 0) model.seek((drag * play.durationMs).toLong())
                dragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = GroveColor.Moss,
                activeTrackColor = GroveColor.Moss,
                inactiveTrackColor = GroveColor.Mist,
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(if (dragging) (drag * play.durationMs).toLong() else position), color = GroveColor.Mute, fontSize = 12.sp)
            Text(formatTime(play.durationMs), color = GroveColor.Mute, fontSize = 12.sp)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = model::previous) {
                Icon(Icons.Outlined.SkipPrevious, contentDescription = "Previous", tint = GroveColor.Ink, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.width(18.dp))
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(GroveColor.Moss)
                    .clickable(onClick = model::toggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (play.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = if (play.playing) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.width(18.dp))
            IconButton(onClick = model::next) {
                Icon(Icons.Outlined.SkipNext, contentDescription = "Next", tint = GroveColor.Ink, modifier = Modifier.size(36.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Energy.entries.forEach { energy ->
                SourceChip(energy.name, play.energy == energy) { model.setEnergy(energy) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Ban artist", color = GroveColor.Moss, modifier = Modifier.clickable(onClick = model::ban), fontWeight = FontWeight.SemiBold)
            Text("Up next", color = GroveColor.Moss, modifier = Modifier.clickable { nav.navigate("queue") }, fontWeight = FontWeight.SemiBold)
            Text("The set", color = GroveColor.Moss, modifier = Modifier.clickable { nav.navigate("set") }, fontWeight = FontWeight.SemiBold)
        }
        if (play.note.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(play.note, color = GroveColor.Mute, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text("Stop", color = GroveColor.Mute, modifier = Modifier.clickable(onClick = model::stop))
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun QueueScreen(nav: NavHostController) {
    val play by PlaybackStore.ui.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .background(GroveColor.Paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        IconButton(onClick = { nav.popBackStack() }) {
            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = GroveColor.Ink)
        }
        GlassCard(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            Text(play.title.ifBlank { "Waiting" }, color = GroveColor.Moss, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            play.upcoming.take(8).forEach { item ->
                Text(item.title, color = GroveColor.Mute, fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text(play.title.ifBlank { "Grove" }, color = GroveColor.Ink, fontWeight = FontWeight.SemiBold)
            Text(play.artistLine(), color = GroveColor.Mute, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun VibeScreen(model: GroveViewModel, nav: NavHostController) {
    val play by PlaybackStore.ui.collectAsState()
    val heights = when (play.energy) {
        Energy.Calm -> listOf(0.28f, 0.36f, 0.22f, 0.4f, 0.3f, 0.24f, 0.34f)
        Energy.Steady -> listOf(0.5f, 0.62f, 0.48f, 0.7f, 0.55f, 0.6f, 0.52f)
        Energy.Lift -> listOf(0.72f, 0.84f, 0.9f, 0.78f, 0.88f, 0.7f, 0.92f)
    }
    Column(Modifier.fillMaxSize().background(GroveColor.Paper).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        IconButton(onClick = { nav.popBackStack() }) {
            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = GroveColor.Ink)
        }
        Text("Vibe", color = GroveColor.Ink, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Calmer stays soft. Harder pulls remixes and club titles. The first song stays the seed.", color = GroveColor.Mute)
        Spacer(Modifier.height(22.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().height(180.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
            ) {
                heights.forEach { height ->
                    Box(
                        Modifier
                            .width(18.dp)
                            .height(160.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(GroveColor.Mist),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height((160 * height).dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(GroveColor.Moss),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Energy.entries.forEach { energy ->
                SourceChip(energy.name, play.energy == energy) { model.setEnergy(energy) }
            }
        }
    }
}

@Composable
fun SetScreen(nav: NavHostController) {
    val play by PlaybackStore.ui.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .background(GroveColor.Paper)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        IconButton(onClick = { nav.popBackStack() }) {
            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = GroveColor.Ink)
        }
        Text("The set", color = GroveColor.Ink, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        GlassCard {
            Text("STARTED FROM", color = GroveColor.Moss, fontSize = 11.sp, letterSpacing = 1.3.sp)
            Spacer(Modifier.height(6.dp))
            Text(play.seedTitle.ifBlank { play.title.ifBlank { "No seed yet" } }, color = GroveColor.Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(play.seedArtist.ifBlank { play.artistLine() }, color = GroveColor.Mute)
        }
        Spacer(Modifier.height(16.dp))
        play.upcoming.forEach { item ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(GroveColor.Moss), contentAlignment = Alignment.Center) {
                    Text(item.title.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(item.title, color = GroveColor.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.reason, color = GroveColor.Mute, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun GroupScreen(model: GroveViewModel, nav: NavHostController, kind: String, name: String) {
    val library by model.library.collectAsState()
    val tracks = remember(library, kind, name) {
        when (kind) {
            "album" -> library.filter { it.album == name }
            "folder" -> library.filter { it.folder == name }
            else -> library.filter { it.prettyArtist() == name || it.artist == name }
        }
    }
    Column(Modifier.fillMaxSize().background(GroveColor.Paper).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        if (kind == "artist") {
            Box(Modifier.fillMaxWidth().height(260.dp).background(GroveColor.Moss)) {
                IconButton(onClick = { nav.popBackStack() }, modifier = Modifier.statusBarsPadding()) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                    Text(name, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
                    Text("${tracks.size} songs", color = Color.White.copy(alpha = 0.85f))
                }
            }
        } else {
            Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp)) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = GroveColor.Ink)
                }
                Text(name, color = GroveColor.Ink, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
                Text("${tracks.size} songs", color = GroveColor.Mute)
            }
        }
        Column(Modifier.padding(horizontal = 20.dp).padding(top = if (kind == "artist") 0.dp else 12.dp)) {
            if (kind == "artist") {
                GlassCard(Modifier.fillMaxWidth().padding(top = 0.dp)) {
                    Text("Popular on this phone", color = GroveColor.Ink, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    if (tracks.isNotEmpty()) GroveButton("Start a set") { model.playPhone(tracks.first()) }
                }
                Spacer(Modifier.height(8.dp))
            }
            tracks.forEachIndexed { index, track ->
                Row(
                    Modifier.fillMaxWidth().clickable { model.playPhone(track) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${index + 1}", color = GroveColor.Mute, modifier = Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, color = GroveColor.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.prettyArtist(), color = GroveColor.Mute, fontSize = 13.sp, maxLines = 1)
                    }
                    Text(formatTime(track.durationMs), color = GroveColor.Mute, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(model: GroveViewModel, nav: NavHostController) {
    val library by model.library.collectAsState()
    val play by PlaybackStore.ui.collectAsState()
    val spotify by model.spotifyStatus.collectAsState()
    var clientId by remember { mutableStateOf(model.savedClientId()) }
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Text("Settings", color = GroveColor.Ink, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(GroveColor.Moss), contentAlignment = Alignment.Center) {
                Text(spotify.name.take(1).ifBlank { "G" }.uppercase(), color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text(spotify.name.ifBlank { "Grove on this phone" }, color = GroveColor.Ink, fontWeight = FontWeight.SemiBold)
                Text("${library.size} local songs", color = GroveColor.Mute, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Phone fade", color = GroveColor.Ink, fontWeight = FontWeight.Medium)
                Text(if (play.crossfade) "4 seconds" else "Off", color = GroveColor.Mute, fontSize = 13.sp)
            }
            Switch(
                checked = play.crossfade,
                onCheckedChange = model::setCrossfade,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = GroveColor.Moss,
                    checkedThumbColor = Color.White,
                    uncheckedTrackColor = GroveColor.Mist,
                    uncheckedThumbColor = Color.White,
                    uncheckedBorderColor = GroveColor.Line,
                ),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("Vibe", color = GroveColor.Moss, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { nav.navigate("vibe") })
        Spacer(Modifier.height(18.dp))
        GlassCard {
            Text("SPOTIFY", color = GroveColor.Moss, fontSize = 11.sp, letterSpacing = 1.3.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Spotify plays in the Spotify app. Grove only queues a similar session. Five allowlisted Premium accounts.",
                color = GroveColor.Mute,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = clientId,
                onValueChange = { clientId = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Client id") },
                singleLine = true,
                colors = fieldColors(),
            )
            Spacer(Modifier.height(8.dp))
            Text("Redirect URI  grove://callback", color = GroveColor.Mute, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            GroveButton("Save client id") { model.saveClientId(clientId) }
            Spacer(Modifier.height(8.dp))
            if (spotify.connected) {
                GroveButton("Disconnect") { model.disconnectSpotify() }
            } else {
                GroveButton("Connect Spotify") { model.connectSpotify(context) }
            }
            if (spotify.message.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(spotify.message, color = GroveColor.Mute, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Grove 0.1.0", color = GroveColor.Mute, fontSize = 12.sp)
    }
}

@Composable
private fun GroveButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = GroveColor.Moss, contentColor = Color.White),
        shape = RoundedCornerShape(999.dp),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(label)
    }
}

@Composable
private fun SourceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) GroveColor.Moss else GroveColor.Paper)
            .border(1.dp, if (selected) GroveColor.Moss else GroveColor.Line, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, color = if (selected) Color.White else GroveColor.Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = GroveColor.Moss,
    unfocusedBorderColor = GroveColor.Line,
    cursorColor = GroveColor.Moss,
    focusedTextColor = GroveColor.Ink,
    unfocusedTextColor = GroveColor.Ink,
    focusedLabelColor = GroveColor.Moss,
    unfocusedLabelColor = GroveColor.Mute,
)

private fun openGroup(nav: NavHostController, kind: String, name: String) {
    nav.navigate("group/$kind/${Uri.encode(name)}")
}

private fun audioPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private data class GroupCard(
    val title: String,
    val count: Int,
    val art: String?,
    val detail: String,
    val kind: String,
)
