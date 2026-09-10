package com.rohith161.musicplayer

import android.content.Context

/** Small persistent playback preferences shared by player screens. */
object PlaybackPreferences {
    private const val NAME = "playback_preferences"
    private const val KEY_SLEEP_END = "sleep_end_elapsed"
    private const val KEY_SPEED = "playback_speed"

    private fun prefs(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun setSleepEnd(context: Context, elapsedRealtime: Long) {
        prefs(context).edit().putLong(KEY_SLEEP_END, elapsedRealtime).apply()
    }

    fun getSleepEnd(context: Context): Long = prefs(context).getLong(KEY_SLEEP_END, 0L)

    fun clearSleep(context: Context) = prefs(context).edit().remove(KEY_SLEEP_END).apply()

    fun setSpeed(context: Context, speed: Float) {
        prefs(context).edit().putFloat(KEY_SPEED, speed).apply()
    }

    fun getSpeed(context: Context): Float = prefs(context).getFloat(KEY_SPEED, 1f)
}
