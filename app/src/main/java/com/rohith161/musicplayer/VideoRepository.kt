package com.rohith161.musicplayer

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore

data class LocalVideo(
    val id: Long,
    val title: String,
    val durationMs: Long,
    val uri: android.net.Uri,
    val folder: String
)

class VideoRepository(private val resolver: ContentResolver) {
    fun loadVideos(): List<LocalVideo> {
        val result = mutableListOf<LocalVideo>()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.RELATIVE_PATH
        )
        resolver.query(collection, projection, null, null, "${MediaStore.Video.Media.TITLE} COLLATE NOCASE ASC")?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val title = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val duration = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val path = cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val videoId = cursor.getLong(id)
                val relative = if (path >= 0) cursor.getString(path).orEmpty().trimEnd('/') else ""
                val folder = relative.substringAfterLast('/', relative.ifBlank { "Videos" }).ifBlank { "Videos" }
                result += LocalVideo(videoId, cursor.getString(title).orEmpty().ifBlank { "Untitled video" }, cursor.getLong(duration), ContentUris.withAppendedId(collection, videoId), folder)
            }
        }
        return result
    }
}
