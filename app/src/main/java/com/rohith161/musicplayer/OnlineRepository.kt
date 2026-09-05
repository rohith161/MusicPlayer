package com.rohith161.musicplayer

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

/** Public/open audio discovery using Internet Archive's public search + metadata APIs. */
data class OnlineTrack(
    val identifier: String,
    val title: String,
    val creator: String,
    val audioUrl: String? = null
)

class OnlineRepository {
    fun search(query: String, limit: Int = 12): List<OnlineTrack> {
        if (query.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        val searchUrl = "https://archive.org/advancedsearch.php?q=mediatype%3Aaudio%20AND%20$encoded&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=creator&rows=$limit&page=1&output=json"
        val root = JSONObject(get(searchUrl))
        val docs = root.getJSONObject("response").getJSONArray("docs")
        val results = mutableListOf<OnlineTrack>()
        for (i in 0 until docs.length()) {
            val doc = docs.getJSONObject(i)
            results += OnlineTrack(
                identifier = doc.optString("identifier"),
                title = doc.optString("title").ifBlank { "Untitled" },
                creator = doc.optString("creator").ifBlank { "Internet Archive audio" }
            )
        }
        return results
    }

    fun resolveAudioUrl(identifier: String): String? {
        val root = JSONObject(get("https://archive.org/metadata/${URLEncoder.encode(identifier, Charsets.UTF_8.name())}"))
        val files = root.optJSONArray("files") ?: return null
        val preferred = listOf(".mp3", ".m4a", ".ogg", ".opus", ".wav")
        var fallback: String? = null
        for (i in 0 until files.length()) {
            val file = files.optJSONObject(i) ?: continue
            val name = file.optString("name")
            if (name.isBlank() || name.endsWith("_files.xml") || name.endsWith("_meta.sqlite")) continue
            val lower = name.lowercase()
            val match = preferred.firstOrNull { lower.endsWith(it) }
            if (match != null) {
                val url = "https://archive.org/download/$identifier/${name.split('/').joinToString("/") { java.net.URLEncoder.encode(it, Charsets.UTF_8.name()).replace("+", "%20") }}"
                if (match == ".mp3") return url
                fallback = fallback ?: url
            }
        }
        return fallback
    }

    private fun get(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 15000
        connection.requestMethod = "GET"
        return connection.inputStream.bufferedReader().use { it.readText() }.also { connection.disconnect() }
    }
}
