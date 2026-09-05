package com.rohith161.musicplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class OnlineAdapter(private val onClick: (OnlineTrack) -> Unit) : RecyclerView.Adapter<OnlineAdapter.ViewHolder>() {
    private var items: List<OnlineTrack> = emptyList()
    private val progress = mutableMapOf<String, Int>()

    fun submitList(newItems: List<OnlineTrack>) { items = newItems; notifyDataSetChanged() }
    fun setDownloadProgress(identifier: String, value: Int) { progress[identifier] = value; val i = items.indexOfFirst { it.identifier == identifier }; if (i >= 0) notifyItemChanged(i) }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_online, parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.onlineTitle)
        private val creator: TextView = itemView.findViewById(R.id.onlineCreator)
        private val action: ImageButton = itemView.findViewById(R.id.onlineAction)
        private val downloadProgress: ProgressBar = itemView.findViewById(R.id.onlineDownloadProgress)

        fun bind(item: OnlineTrack) {
            title.text = item.title
            creator.text = item.creator
            val value = progress[item.identifier]
            downloadProgress.visibility = if (value != null && value in 1..99) View.VISIBLE else View.GONE
            if (value != null && value in 1..99) downloadProgress.progress = value
            action.setImageResource(if (value == 100) android.R.drawable.ic_menu_save else android.R.drawable.ic_menu_more)
            itemView.setOnClickListener { onClick(item) }
            action.setOnClickListener { onClick(item) }
        }
    }
}
