package com.rohith161.musicplayer

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class SongAdapter(private val onClick: (Track) -> Unit, private val onAddToPlaylist: (Track) -> Unit) : RecyclerView.Adapter<SongAdapter.ViewHolder>() {
    private var tracks: List<Track> = emptyList()
    private val executor = Executors.newFixedThreadPool(2)
    fun submitList(newTracks: List<Track>) { tracks = newTracks; notifyDataSetChanged() }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_song_v2, parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(tracks[position])
    override fun getItemCount() = tracks.size
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val artwork = itemView.findViewById<ImageView>(R.id.songArtwork)
        private val title = itemView.findViewById<TextView>(R.id.songTitle)
        private val artist = itemView.findViewById<TextView>(R.id.songArtist)
        fun bind(track: Track) {
            title.text = track.title; artist.text = if (track.album.isBlank()) track.artist else "${track.artist} • ${track.album}"
            artwork.setImageDrawable(null)
            if (Build.VERSION.SDK_INT >= 29) executor.execute { runCatching { itemView.context.contentResolver.loadThumbnail(track.uri, Size(160, 160), null) }.getOrNull()?.let { bitmap: Bitmap -> itemView.post { artwork.setImageDrawable(BitmapDrawable(itemView.resources, bitmap)) } } }
            itemView.setOnClickListener { onClick(track) }
            itemView.findViewById<ImageButton>(R.id.songPlayButton).setOnClickListener { onClick(track) }
            itemView.findViewById<ImageButton>(R.id.songAddButton).setOnClickListener { onAddToPlaylist(track) }
        }
    }
}
