package com.rohith161.musicplayer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class PlaylistRepository(context: Context) {
    private val prefs = context.getSharedPreferences("via_playlists", Context.MODE_PRIVATE)

    fun getAll(): List<ViaPlaylist> {
        val array = runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrElse { JSONArray() }
        return buildList {
            for (i in 0 until array.length()) {
                val p = array.optJSONObject(i) ?: continue
                val entries = mutableListOf<PlaylistEntry>()
                val ea = p.optJSONArray("entries") ?: JSONArray()
                for (j in 0 until ea.length()) {
                    val e = ea.optJSONObject(j) ?: continue
                    entries += PlaylistEntry(
                        e.optString("id"),
                        runCatching { PlaylistMediaType.valueOf(e.optString("type")) }.getOrDefault(PlaylistMediaType.AUDIO),
                        e.optString("uri"), e.optString("title"), e.optString("subtitle")
                    )
                }
                add(ViaPlaylist(p.optString("id"), p.optString("name"), entries))
            }
        }
    }

    fun create(name: String): ViaPlaylist? {
        val clean = name.trim()
        if (clean.isBlank() || getAll().any { it.name.equals(clean, true) }) return null
        val playlist = ViaPlaylist(UUID.randomUUID().toString(), clean)
        save(getAll() + playlist)
        return playlist
    }

    fun delete(id: String) = save(getAll().filterNot { it.id == id })

    fun addEntry(playlistId: String, entry: PlaylistEntry) {
        val updated = getAll().map { p ->
            if (p.id == playlistId && p.entries.none { it.id == entry.id && it.type == entry.type }) p.copy(entries = p.entries + entry) else p
        }
        save(updated)
    }

    fun removeEntry(playlistId: String, entryId: String, type: PlaylistMediaType) {
        save(getAll().map { p -> if (p.id == playlistId) p.copy(entries = p.entries.filterNot { it.id == entryId && it.type == type }) else p })
    }

    private fun save(playlists: List<ViaPlaylist>) {
        val array = JSONArray()
        playlists.forEach { p ->
            val entries = JSONArray()
            p.entries.forEach { e ->
                entries.put(JSONObject().apply {
                    put("id", e.id); put("type", e.type.name); put("uri", e.uri); put("title", e.title); put("subtitle", e.subtitle)
                })
            }
            array.put(JSONObject().apply { put("id", p.id); put("name", p.name); put("entries", entries) })
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    companion object { private const val KEY = "playlists" }
}
