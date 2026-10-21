package com.grove.autopilot.model

enum class Source { None, Phone, Spotify }

enum class Energy { Calm, Steady, Lift }

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val folder: String,
    val genre: String,
    val durationMs: Long,
    val uri: String,
    val artUri: String?,
    val source: Source,
    val artistId: String = "",
)

data class UpNext(
    val id: String,
    val title: String,
    val artist: String,
    val reason: String,
)

data class PlayUi(
    val source: Source = Source.None,
    val playing: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artUri: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val energy: Energy = Energy.Steady,
    val seedTitle: String = "",
    val seedArtist: String = "",
    val upcoming: List<UpNext> = emptyList(),
    val note: String = "",
    val crossfade: Boolean = true,
    val generation: Int = 0,
    val anchorRealtimeMs: Long = 0,
)

fun Track.prettyArtist(): String {
    val trimmed = artist.trim()
    if (trimmed.isEmpty() || trimmed.equals("<unknown>", true) || trimmed.equals("unknown", true)) {
        return "Unknown artist"
    }
    return trimmed
}

fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000).toInt()
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
