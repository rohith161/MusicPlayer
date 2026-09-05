package com.rohith161.musicplayer

import android.view.View
import android.widget.TextView
import androidx.media3.common.util.UnstableApi

/**
 * Search-tab UI entry point kept separate so the main HomeActivity stays focused on playback/navigation.
 *
 * HomeActivity is explicitly marked with Media3's UnstableApi because it owns MediaController
 * integration. Media3's UnstableApi in this dependency is a declaration annotation rather than
 * a Kotlin RequiresOptIn marker, so the extension must carry the annotation directly.
 */
@UnstableApi
fun HomeActivity.showSearch() {
    findViewById<View>(R.id.searchControls).visibility = View.VISIBLE
    findViewById<View>(R.id.folderHeader).visibility = View.GONE
    findViewById<TextView>(R.id.screenTitle).text = "SEARCH"
    findViewById<TextView>(R.id.screenSubtitle).text = "Search your local music and videos"
}
