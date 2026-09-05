package com.rohith161.musicplayer

import android.view.View
import android.widget.TextView

/** Search-tab UI entry point kept separate so the main HomeActivity stays focused on playback/navigation. */
fun HomeActivity.showSearch() {
    findViewById<View>(R.id.searchControls).visibility = View.VISIBLE
    findViewById<View>(R.id.folderHeader).visibility = View.GONE
    findViewById<TextView>(R.id.screenTitle).text = "SEARCH"
    findViewById<TextView>(R.id.screenSubtitle).text = "Search your local music and videos"
}
