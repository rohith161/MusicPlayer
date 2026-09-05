package com.rohith161.musicplayer

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

class VideoAdapter(private val onClick: (LocalVideo) -> Unit, private val onAddToPlaylist: (LocalVideo) -> Unit) : RecyclerView.Adapter<VideoAdapter.ViewHolder>() {
    private var items: List<LocalVideo> = emptyList()
    private val executor = Executors.newFixedThreadPool(2)
    fun submitList(newItems: List<LocalVideo>) { items = newItems; notifyDataSetChanged() }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_video_v2, parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val artwork = itemView.findViewById<ImageView>(R.id.videoArtwork)
        private val title = itemView.findViewById<TextView>(R.id.videoTitle)
        private val duration = itemView.findViewById<TextView>(R.id.videoDuration)
        fun bind(item: LocalVideo) {
            title.text = item.title; duration.text = "${item.folder} • ${formatDuration(item.durationMs)}"; artwork.setImageDrawable(null)
            if (Build.VERSION.SDK_INT >= 29) executor.execute { runCatching { itemView.context.contentResolver.loadThumbnail(item.uri, Size(240, 160), null) }.getOrNull()?.let { bitmap -> itemView.post { artwork.setImageDrawable(BitmapDrawable(itemView.resources, bitmap)) } } }
            itemView.setOnClickListener { onClick(item) }
            itemView.findViewById<ImageButton>(R.id.videoAddButton).setOnClickListener { onAddToPlaylist(item) }
        }
        private fun formatDuration(ms: Long): String { val s = ms.coerceAtLeast(0) / 1000; return "%d:%02d".format(s / 60, s % 60) }
    }
}
