package com.friendsvibe.autopilot.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
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
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, if (destructive) Clay else Amber.copy(alpha = 0.35f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (destructive) Wash else Panel,
            contentColor = if (destructive) Clay else Amber,
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
                Text("Close", color = Mist, style = MaterialTheme.typography.labelLarge)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Wash)
                    .border(1.dp, Amber.copy(alpha = 0.16f), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Amber),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (playing) "II" else "▶", color = Color.White, style = MaterialTheme.typography.titleLarge)
                }
            }
            Spacer(Modifier.height(8.dp))
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
