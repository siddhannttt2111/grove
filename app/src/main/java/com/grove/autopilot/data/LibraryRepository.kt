package com.grove.autopilot.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.grove.autopilot.model.Source
import com.grove.autopilot.model.Track

object LibraryRepository {
    fun load(context: Context): List<Track> {
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val columns = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
        )
        if (Build.VERSION.SDK_INT >= 29) {
            columns += MediaStore.Audio.Media.BUCKET_DISPLAY_NAME
        }
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 15000"
        val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        val found = LinkedHashMap<String, Track>()
        context.contentResolver.query(collection, columns.toTypedArray(), selection, null, sort)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val folderCol = cursor.getColumnIndex(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(idCol)
                val title = cursor.getString(titleCol)?.trim().orEmpty().ifBlank { "Untitled" }
                val artist = cursor.getString(artistCol)?.trim().orEmpty()
                val album = cursor.getString(albumCol)?.trim().orEmpty()
                val duration = cursor.getLong(durationCol)
                val albumId = cursor.getLong(albumIdCol)
                val folder = if (folderCol >= 0) cursor.getString(folderCol)?.trim().orEmpty() else ""
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId).toString()
                val art = if (albumId > 0) {
                    ContentUris.withAppendedId(
                        android.net.Uri.parse("content://media/external/audio/albumart"),
                        albumId,
                    ).toString()
                } else {
                    null
                }
                val id = "phone:$mediaId"
                found[id] = Track(
                    id = id,
                    title = title,
                    artist = artist,
                    album = album,
                    folder = folder,
                    genre = "",
                    durationMs = duration,
                    uri = uri,
                    artUri = art,
                    source = Source.Phone,
                )
            }
        }
        return found.values.toList()
    }
}
