package com.friendsvibe.autopilot.local

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.friendsvibe.autopilot.rank.Names

class LibraryReader(private val context: Context) {
    fun load(): List<LocalTrack> {
        val genres = runCatching { genreMap() }.getOrDefault(emptyMap())
        val resolver = context.contentResolver
        val relative = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val pathColumn = if (relative) {
            MediaStore.Audio.Media.RELATIVE_PATH
        } else {
            MediaStore.Audio.Media.DATA
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            pathColumn,
        )
        val tracks = mutableListOf<LocalTrack>()
        resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?",
            arrayOf(MIN_DURATION_MS.toString()),
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val pathCol = cursor.getColumnIndexOrThrow(pathColumn)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol)?.trim().orEmpty().ifBlank { "Untitled" }
                tracks += LocalTrack(
                    id = id,
                    title = title,
                    artist = clean(cursor.getString(artistCol)),
                    album = clean(cursor.getString(albumCol)),
                    genre = clean(genres[id]),
                    folder = folderOf(cursor.getString(pathCol).orEmpty(), relative),
                    durationMs = cursor.getLong(durationCol),
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    bpm = null,
                )
            }
        }
        return tracks
    }

    private fun genreMap(): Map<Long, String> {
        val result = HashMap<Long, String>()
        context.contentResolver.query(
            MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)
            while (cursor.moveToNext()) {
                val genreId = cursor.getLong(idCol)
                val name = cursor.getString(nameCol).orEmpty()
                if (name.isBlank()) continue
                val members = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)
                context.contentResolver.query(
                    members,
                    arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
                    null,
                    null,
                    null,
                )?.use { memberCursor ->
                    val audioCol = memberCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.Members.AUDIO_ID)
                    while (memberCursor.moveToNext()) {
                        result.putIfAbsent(memberCursor.getLong(audioCol), name)
                    }
                }
            }
        }
        return result
    }

    private fun folderOf(path: String, relative: Boolean): String {
        val normalized = path.trim().replace('\\', '/').trim('/')
        if (normalized.isEmpty()) return ""
        if (relative) return normalized
        val slash = normalized.lastIndexOf('/')
        if (slash <= 0) return ""
        return normalized.substring(0, slash)
    }

    private fun clean(value: String?): String {
        val trimmed = value?.trim().orEmpty()
        return if (Names.key(trimmed).isEmpty()) "" else trimmed
    }

    private companion object {
        const val MIN_DURATION_MS = 15_000
    }
}
