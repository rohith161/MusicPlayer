package com.rohith161.musicplayer

import android.content.ComponentName
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

@androidx.media3.common.util.UnstableApi
class PlayerActivity : AppCompatActivity() {
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var play: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        title = findViewById(R.id.playerTitle)
        artist = findViewById(R.id.playerArtist)
        play = findViewById(R.id.playerPlay)

        findViewById<ImageButton>(R.id.playerClose).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.playerPrevious).setOnClickListener { controller?.seekToPreviousMediaItem() }
        play.setOnClickListener { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
        findViewById<ImageButton>(R.id.playerNext).setOnClickListener { controller?.seekToNextMediaItem() }

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture.addListener({
            try {
                controller = controllerFuture.get()
                updateNowPlaying()
                controller?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) = updateNowPlaying()
                    override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) = updateNowPlaying()
                })
            } catch (_: Exception) {
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun updateNowPlaying() {
        val player = controller ?: return
        title.text = player.mediaMetadata.title ?: getString(R.string.unknown_title)
        artist.text = player.mediaMetadata.artist ?: getString(R.string.unknown_artist)
        play.setImageResource(if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
    }

    override fun onDestroy() {
        if (::controllerFuture.isInitialized) controllerFuture.cancel(true)
        controller?.release()
        controller = null
        super.onDestroy()
    }
}
