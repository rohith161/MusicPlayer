package com.rohith161.musicplayer

import android.content.Context

/** Remembers the last local media item so the library can offer quick resume. */
object PlaybackStateStore {
    private const val NAME = "playback_state"
    private const val ID = "last_media_id"
    private const val POSITION = "last_position"

    fun save(context: Context, mediaId: String?, position: Long) {
        if (mediaId.isNullOrBlank()) return
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString(ID, mediaId)
            .putLong(POSITION, position.coerceAtLeast(0L))
            .apply()
    }

    fun mediaId(context: Context): String? = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(ID, null)
    fun position(context: Context): Long = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getLong(POSITION, 0L)
    fun clear(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().clear().apply()
}
