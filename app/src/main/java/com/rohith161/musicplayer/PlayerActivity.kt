package com.rohith161.musicplayer

import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.SeekBar
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
    private lateinit var progress: SeekBar
    private val progressHandler = Handler(Looper.getMainLooper())
    private var userSeeking = false

    private val progressUpdater = object : Runnable {
        override fun run() {
            updateProgress()
            progressHandler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        title = findViewById(R.id.playerTitle)
        artist = findViewById(R.id.playerArtist)
        play = findViewById(R.id.playerPlay)
        progress = findViewById(R.id.playerProgress)
        progress.max = 1000

        findViewById<ImageButton>(R.id.playerClose).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.playerPrevious).setOnClickListener { controller?.seekToPreviousMediaItem() }
        play.setOnClickListener { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
        findViewById<ImageButton>(R.id.playerNext).setOnClickListener { controller?.seekToNextMediaItem() }

        progress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                if (!fromUser) return
                val player = controller ?: return
                val duration = player.duration
                if (duration != Player.TIME_UNSET && duration > 0) {
                    val position = duration * value.toLong() / 1000L
                    // Update the player's actual position while dragging so online streams visibly seek.
                    player.seekTo(position)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                userSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                userSeeking = false
                updateProgress()
            }
        })

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture.addListener({
            try {
                controller = controllerFuture.get()
                updateNowPlaying()
                updateProgress()
                controller?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) = updateNowPlaying()
                    override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) = updateNowPlaying()
                    override fun onPlaybackStateChanged(playbackState: Int) = updateProgress()
                    override fun onPositionDiscontinuity(reason: Player.PositionInfo, oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo) = updateProgress()
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

    private fun updateProgress() {
        val player = controller ?: return
        if (userSeeking) return
        val duration = player.duration
        val position = player.currentPosition
        progress.isEnabled = duration != Player.TIME_UNSET && duration > 0
        progress.progress = if (duration != Player.TIME_UNSET && duration > 0) {
            ((position.coerceIn(0L, duration) * 1000L) / duration).toInt()
        } else {
            0
        }
    }

    override fun onStart() {
        super.onStart()
        progressHandler.post(progressUpdater)
    }

    override fun onStop() {
        progressHandler.removeCallbacks(progressUpdater)
        super.onStop()
    }

    override fun onDestroy() {
        progressHandler.removeCallbacks(progressUpdater)
        if (::controllerFuture.isInitialized) controllerFuture.cancel(true)
        controller?.release()
        controller = null
        super.onDestroy()
    }
}
