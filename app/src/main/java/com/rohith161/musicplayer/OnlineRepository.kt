package com.rohith161.musicplayer

import org.json.JSONArray
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
        val clean = query.trim()
        if (clean.isBlank()) return emptyList()
        val encodedQuery = URLEncoder.encode("mediatype:audio AND ($clean)", Charsets.UTF_8.name()).replace("+", "%20")
        val searchUrl = "https://archive.org/advancedsearch.php?q=$encodedQuery&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=creator&rows=$limit&page=1&output=json"
        val root = JSONObject(get(searchUrl))
        val docs = root.optJSONObject("response")?.optJSONArray("docs") ?: return emptyList()
        val results = mutableListOf<OnlineTrack>()
        for (i in 0 until docs.length()) {
            val doc = docs.optJSONObject(i) ?: continue
            val id = doc.optString("identifier").trim()
            if (id.isBlank()) continue
            results += OnlineTrack(id, firstValue(doc.opt("title")).ifBlank { "Untitled" }, firstValue(doc.opt("creator")).ifBlank { "Internet Archive audio" })
        }
        return results
    }

    fun resolveAudioUrl(identifier: String): String? {
        val safeId = URLEncoder.encode(identifier, Charsets.UTF_8.name()).replace("+", "%20")
        val root = JSONObject(get("https://archive.org/metadata/$safeId"))
        val files = root.optJSONArray("files") ?: return null
        val preferred = listOf(".mp3", ".m4a", ".ogg", ".opus", ".wav")
        var fallback: String? = null
        for (i in 0 until files.length()) {
            val file = files.optJSONObject(i) ?: continue
            val name = file.optString("name")
            if (name.isBlank() || name.endsWith("_files.xml") || name.endsWith("_meta.sqlite")) continue
            val lower = name.lowercase()
            val extension = preferred.firstOrNull { lower.endsWith(it) } ?: continue
            val encodedName = name.split('/').joinToString("/") { URLEncoder.encode(it, Charsets.UTF_8.name()).replace("+", "%20") }
            val url = "https://archive.org/download/$safeId/$encodedName"
            if (extension == ".mp3") return url
            fallback = fallback ?: url
        }
        return fallback
    }

    private fun firstValue(value: Any?): String = when (value) {
        is JSONArray -> if (value.length() > 0) value.optString(0) else ""
        else -> value?.toString() ?: ""
    }

    private fun get(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 20000
        connection.requestMethod = "GET"
        return try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("Internet Archive returned HTTP $code")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
