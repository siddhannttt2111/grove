package com.friendsvibe.autopilot.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, if (destructive) Clay else Line),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Panel,
            contentColor = if (destructive) Clay else Paper,
            disabledContentColor = Mist,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SetScreen(
    sourceLabel: String,
    sourceNote: String,
    title: String,
    artist: String,
    detail: String,
    vibeLabel: String,
    progress: Float,
    playing: Boolean,
    upcoming: List<String>,
    banned: List<String>,
    status: String?,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onSkip: () -> Unit,
    onCalmer: () -> Unit,
    onHarder: () -> Unit,
    onBan: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                Text("Back", color = Mist, style = MaterialTheme.typography.labelLarge)
            }
            Text(sourceLabel.uppercase(), color = Amber, style = MaterialTheme.typography.labelLarge)
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Paper, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(artist, style = MaterialTheme.typography.bodyLarge, color = Mist)
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = Mist)
            }
            Text(vibeLabel, style = MaterialTheme.typography.bodyMedium, color = Calm)
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp)),
                color = Amber,
                trackColor = Line,
            )
            Text(
                sourceNote,
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Mist,
            )
            if (!status.isNullOrBlank()) {
                Text(status, modifier = Modifier.padding(top = 8.dp), color = Paper, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(if (playing) "Pause" else "Play", onToggle, Modifier.weight(1f))
                    ActionButton("Skip", onSkip, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("Calmer", onCalmer, Modifier.weight(1f))
                    ActionButton("Harder", onHarder, Modifier.weight(1f))
                }
                ActionButton("Ban artist", onBan, Modifier.fillMaxWidth(), destructive = true, enabled = artist.isNotBlank())
            }
        }
        item {
            Text("Up next", color = Amber, style = MaterialTheme.typography.labelLarge)
        }
        if (upcoming.isEmpty()) {
            item { Text("Nothing else is queued.", color = Mist) }
        } else {
            items(upcoming.take(8)) { line ->
                Text(line, color = Paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (banned.isNotEmpty()) {
            item {
                Text("Banned: ${banned.joinToString(", ")}", color = Clay, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
