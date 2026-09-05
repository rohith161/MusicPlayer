package com.rohith161.musicplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class VideoAdapter(private val onClick: (LocalVideo) -> Unit) : RecyclerView.Adapter<VideoAdapter.ViewHolder>() {
    private var items: List<LocalVideo> = emptyList()

    fun submitList(newItems: List<LocalVideo>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.videoTitle)
        private val duration: TextView = itemView.findViewById(R.id.videoDuration)

        fun bind(item: LocalVideo) {
            title.text = item.title
            duration.text = formatDuration(item.durationMs)
            itemView.setOnClickListener { onClick(item) }
        }

        private fun formatDuration(ms: Long): String {
            val totalSeconds = ms.coerceAtLeast(0) / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
    }
}
