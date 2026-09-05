package com.rohith161.musicplayer

import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        val title = TextView(this).apply { text = "Settings"; textSize = 28f; setTextColor(getColor(R.color.mp_foreground)); setPadding(0, 0, 0, 24) }
        root.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addSection(root, "General", "Playback, appearance, notifications and library behavior")
        addSection(root, "Help", "Playback gestures, folders, playlists and troubleshooting")
        addSection(root, "About", "VIA Player • Versatile Integrated Audio-Video Player")
        setContentView(root)
    }
    private fun addSection(root: LinearLayout, heading: String, text: String) {
        val h = TextView(this).apply { this.text = heading; textSize = 18f; setTextColor(getColor(R.color.mp_foreground)); setPadding(0, 18, 0, 4) }
        val d = TextView(this).apply { this.text = text; textSize = 14f; setTextColor(getColor(R.color.mp_foreground_muted)); setPadding(0, 0, 0, 8) }
        root.addView(h); root.addView(d)
    }
}
