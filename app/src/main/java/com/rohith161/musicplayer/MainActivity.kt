package com.rohith161.musicplayer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

/** Compatibility entry point retained for older integrations. */
@OptIn(markerClass = UnstableApi::class)
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
