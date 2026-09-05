package com.rohith161.musicplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class OnlineAdapter(private val onClick: (OnlineTrack) -> Unit) : RecyclerView.Adapter<OnlineAdapter.ViewHolder>() {
    private var items: List<OnlineTrack> = emptyList()

    fun submitList(newItems: List<OnlineTrack>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_online, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.onlineTitle)
        private val creator: TextView = itemView.findViewById(R.id.onlineCreator)

        fun bind(item: OnlineTrack) {
            title.text = item.title
            creator.text = item.creator
            itemView.setOnClickListener { onClick(item) }
        }
    }
}
