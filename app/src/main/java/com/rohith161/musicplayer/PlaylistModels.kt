package com.rohith161.musicplayer

enum class PlaylistMediaType { AUDIO, VIDEO }

data class ViaPlaylist(
    val id: String,
    val name: String,
    val entries: List<PlaylistEntry> = emptyList()
)

data class PlaylistEntry(
    val id: String,
    val type: PlaylistMediaType,
    val uri: String,
    val title: String,
    val subtitle: String
)
