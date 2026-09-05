package com.rohith161.musicplayer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class PlaylistRepository(context: Context) {
    private val prefs = context.getSharedPreferences("via_playlist", Context.MODE_PRIVATE)

    fun getAll(): List<OnlineTrack> {
        val array = runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrElse { JSONArray() }
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                add(OnlineTrack(o.optString("id"), o.optString("title"), o.optString("creator"), o.optString("url").ifBlank { null }))
            }
        }
    }

    fun add(track: OnlineTrack) {
        if (getAll().any { it.identifier == track.identifier }) return
        val array = JSONArray()
        (getAll() + track).forEach { t ->
            array.put(JSONObject().apply {
                put("id", t.identifier)
                put("title", t.title)
                put("creator", t.creator)
                t.audioUrl?.let { put("url", it) }
            })
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    fun remove(identifier: String) {
        val array = JSONArray()
        getAll().filterNot { it.identifier == identifier }.forEach { t ->
            array.put(JSONObject().apply {
                put("id", t.identifier)
                put("title", t.title)
                put("creator", t.creator)
                t.audioUrl?.let { put("url", it) }
            })
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    companion object { private const val KEY = "tracks" }
}
