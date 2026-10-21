package com.grove.autopilot.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.grove.autopilot.model.PlayUi
import com.grove.autopilot.model.prettyArtist
import com.grove.autopilot.theme.GroveColor
import kotlinx.coroutines.delay

@Composable
fun GroveBackdrop() {
    Box(Modifier.fillMaxSize().background(GroveColor.Paper)) {
        Box(
            Modifier
                .size(220.dp)
                .offset(x = 230.dp, y = (-40).dp)
                .clip(CircleShape)
                .background(GroveColor.Mist),
        )
        Box(
            Modifier
                .size(140.dp)
                .offset(x = (-60).dp, y = 520.dp)
                .clip(CircleShape)
                .background(GroveColor.Moss),
        )
        Box(
            Modifier
                .size(72.dp)
                .offset(x = 300.dp, y = 680.dp)
                .clip(CircleShape)
                .background(GroveColor.Leaf),
        )
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = 28.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .clip(shape)
            .background(GroveColor.Glass)
            .border(1.dp, GroveColor.Moss.copy(alpha = 0.16f), shape)
            .padding(20.dp),
        content = content,
    )
}

@Composable
fun ArtThumb(
    artUri: String?,
    modifier: Modifier = Modifier.size(54.dp),
    radius: Dp = 16.dp,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(GroveColor.Mist),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = GroveColor.Moss)
        if (!artUri.isNullOrBlank()) {
            AsyncImage(
                model = artUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
fun TrackRow(
    title: String,
    subtitle: String,
    artUri: String?,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtThumb(artUri, Modifier.size(54.dp), 14.dp)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(title, color = GroveColor.Ink, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = GroveColor.Mute, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun livePosition(ui: PlayUi): Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(ui.playing, ui.anchorRealtimeMs, ui.positionMs) {
        while (ui.playing) {
            now = SystemClock.elapsedRealtime()
            delay(200)
        }
    }
    if (!ui.playing || ui.anchorRealtimeMs == 0L) return ui.positionMs
    val estimated = ui.positionMs + (now - ui.anchorRealtimeMs)
    return if (ui.durationMs > 0) estimated.coerceIn(0, ui.durationMs) else estimated.coerceAtLeast(0)
}

fun PlayUi.artistLine(): String {
    val shown = artist.ifBlank { "Unknown artist" }
    return if (shown.equals("<unknown>", true)) "Unknown artist" else shown
}

fun com.grove.autopilot.model.Track.rowSubtitle(): String = prettyArtist()
