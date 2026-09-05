package com.rohith161.musicplayer

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore

class MusicRepository(private val resolver: ContentResolver) {

    fun loadTracks(): List<Track> {
        val tracks = mutableListOf<Track>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )

        resolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                tracks += Track(
                    id = id,
                    title = cursor.getString(titleColumn).orEmpty().ifBlank { "Unknown title" },
                    artist = cursor.getString(artistColumn).orEmpty().ifBlank { "Unknown artist" },
                    album = cursor.getString(albumColumn).orEmpty(),
                    durationMs = cursor.getLong(durationColumn),
                    uri = ContentUris.withAppendedId(collection, id)
                )
            }
        }
        return tracks
    }
}
