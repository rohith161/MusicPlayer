package com.rohith161.musicplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SongAdapter(private val onClick: (Track) -> Unit) :
    RecyclerView.Adapter<SongAdapter.ViewHolder>() {

    private var tracks: List<Track> = emptyList()

    fun submitList(newTracks: List<Track>) {
        tracks = newTracks
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(tracks[position])
    }

    override fun getItemCount(): Int = tracks.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.songTitle)
        private val artist: TextView = itemView.findViewById(R.id.songArtist)

        fun bind(track: Track) {
            title.text = track.title
            artist.text = if (track.album.isBlank()) track.artist else "${track.artist} • ${track.album}"
            itemView.setOnClickListener { onClick(track) }
        }
    }
}
