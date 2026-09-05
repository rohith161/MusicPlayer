package com.rohith161.musicplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FolderAdapter(private val onClick: (FolderItem) -> Unit) :
    RecyclerView.Adapter<FolderAdapter.ViewHolder>() {

    private var folders: List<FolderItem> = emptyList()

    fun submitList(newFolders: List<FolderItem>) {
        folders = newFolders
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_folder, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(folders[position])

    override fun getItemCount(): Int = folders.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.folderName)
        private val count: TextView = itemView.findViewById(R.id.folderCount)

        fun bind(folder: FolderItem) {
            name.text = folder.name
            count.text = "${folder.count} ${if (folder.count == 1) "song" else "songs"}"
            itemView.setOnClickListener { onClick(folder) }
        }
    }
}

data class FolderItem(val name: String, val count: Int)
