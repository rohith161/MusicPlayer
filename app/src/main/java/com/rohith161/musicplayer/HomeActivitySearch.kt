package com.rohith161.musicplayer

import android.view.View
import android.widget.TextView
import androidx.media3.common.util.UnstableApi

/**
 * Search-tab UI entry point kept separate so the main HomeActivity stays focused on playback/navigation.
 *
 * HomeActivity is explicitly marked with Media3's UnstableApi because it owns MediaController
 * integration. This extension invokes that Activity type, so it must opt into the same API
 * contract for Android lint to accept the declaration.
 */
@OptIn(UnstableApi::class)
fun HomeActivity.showSearch() {
    findViewById<View>(R.id.searchControls).visibility = View.VISIBLE
    findViewById<View>(R.id.folderHeader).visibility = View.GONE
    findViewById<TextView>(R.id.screenTitle).text = "SEARCH"
    findViewById<TextView>(R.id.screenSubtitle).text = "Search your local music and videos"
}
