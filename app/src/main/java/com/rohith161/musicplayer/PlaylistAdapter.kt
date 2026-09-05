package com.rohith161.musicplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PlaylistAdapter(private val onClick: (ViaPlaylist) -> Unit) : RecyclerView.Adapter<PlaylistAdapter.VH>() {
    private var items = emptyList<ViaPlaylist>()
    fun submitList(value: List<ViaPlaylist>) { items = value; notifyDataSetChanged() }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(LayoutInflater.from(parent.context).inflate(R.layout.item_playlist, parent, false))
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size
    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val name = view.findViewById<TextView>(R.id.playlistName)
        private val count = view.findViewById<TextView>(R.id.playlistCount)
        fun bind(item: ViaPlaylist) { name.text = item.name; count.text = "${item.entries.size} items"; itemView.setOnClickListener { onClick(item) } }
    }
}
