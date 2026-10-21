package com.friendsvibe.autopilot.local

import android.net.Uri
import com.friendsvibe.autopilot.rank.LibrarySong

data class LocalTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val folder: String,
    val durationMs: Long,
    val uri: Uri,
    val bpm: Int?,
) {
    val displayArtist: String get() = artist.ifBlank { "Unknown artist" }

    fun toSong(): LibrarySong = LibrarySong(id, title, artist, album, genre, folder, bpm)

    fun detail(): String {
        val bits = mutableListOf<String>()
        if (album.isNotBlank()) bits += album
        if (genre.isNotBlank()) bits += genre
        bpm?.let { bits += "$it bpm" }
        return bits.joinToString(" · ")
    }
}
