package com.rohith161.musicplayer

import android.content.ContentResolver
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore

class MusicRepository(private val resolver: ContentResolver) {

    fun loadTracks(): List<Track> {
        val tracks = mutableListOf<Track>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )
        if (Build.VERSION.SDK_INT >= 29) {
            projection += MediaStore.Audio.Media.RELATIVE_PATH
        }

        resolver.query(
            collection,
            projection.toTypedArray(),
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val relativePathColumn = if (Build.VERSION.SDK_INT >= 29) {
                cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            } else -1

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val dataPath = cursor.getString(dataColumn).orEmpty()
                val relativePath = if (relativePathColumn >= 0) cursor.getString(relativePathColumn).orEmpty() else ""
                val folder = folderName(relativePath, dataPath)
                tracks += Track(
                    id = id,
                    title = cursor.getString(titleColumn).orEmpty().ifBlank { "Unknown title" },
                    artist = cursor.getString(artistColumn).orEmpty().ifBlank { "Unknown artist" },
                    album = cursor.getString(albumColumn).orEmpty(),
                    durationMs = cursor.getLong(durationColumn),
                    uri = ContentUris.withAppendedId(collection, id),
                    folder = folder
                )
            }
        }
        return tracks
    }

    private fun folderName(relativePath: String, dataPath: String): String {
        if (relativePath.isNotBlank()) {
            return relativePath.trimEnd('/').substringAfterLast('/').ifBlank { "Music" }
        }
        if (dataPath.isNotBlank()) {
            val normalized = dataPath.replace('\\', '/')
            val parent = normalized.substringBeforeLast('/', "")
            return parent.substringAfterLast('/').ifBlank { "Music" }
        }
        return "Music"
    }
}
