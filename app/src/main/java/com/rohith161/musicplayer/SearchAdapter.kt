package com.rohith161.musicplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class SearchResult(val title: String, val subtitle: String, val uri: String, val isVideo: Boolean)

class SearchAdapter(private val onClick: (SearchResult) -> Unit) : RecyclerView.Adapter<SearchAdapter.VH>() {
    private var items = emptyList<SearchResult>()
    fun submitList(value: List<SearchResult>) { items = value; notifyDataSetChanged() }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(LayoutInflater.from(parent.context).inflate(R.layout.item_search, parent, false))
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size
    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val title = view.findViewById<TextView>(R.id.searchResultTitle)
        private val subtitle = view.findViewById<TextView>(R.id.searchResultSubtitle)
        fun bind(item: SearchResult) { title.text = item.title; subtitle.text = "${if (item.isVideo) "Video" else "Music"} • ${item.subtitle}"; itemView.setOnClickListener { onClick(item) } }
    }
}
