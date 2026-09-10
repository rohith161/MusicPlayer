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
import androidx.media3.common.C
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
    private lateinit var shuffle: ImageButton
    private lateinit var repeat: ImageButton
    private lateinit var progress: SeekBar
    private lateinit var elapsed: TextView
    private lateinit var durationText: TextView
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
        shuffle = findViewById(R.id.playerShuffle)
        repeat = findViewById(R.id.playerRepeat)
        progress = findViewById(R.id.playerProgress)
        elapsed = findViewById(R.id.playerElapsed)
        durationText = findViewById(R.id.playerDuration)
        progress.max = 1000

        findViewById<ImageButton>(R.id.playerClose).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.playerPrevious).setOnClickListener { controller?.seekToPreviousMediaItem() }
        play.setOnClickListener { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
        findViewById<ImageButton>(R.id.playerNext).setOnClickListener { controller?.seekToNextMediaItem() }
        shuffle.setOnClickListener {
            controller?.let { player ->
                player.shuffleModeEnabled = !player.shuffleModeEnabled
                updateModeButtons()
            }
        }
        repeat.setOnClickListener {
            controller?.let { player ->
                player.repeatMode = when (player.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                updateModeButtons()
            }
        }

        progress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                if (!fromUser) return
                val mediaController = controller ?: return
                val duration = mediaController.duration
                if (duration != C.TIME_UNSET && duration > 0) {
                    mediaController.seekTo(duration * value.toLong() / 1000L)
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
                updateModeButtons()
                controller?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) = updateNowPlaying()
                    override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) = updateNowPlaying()
                    override fun onPlaybackStateChanged(playbackState: Int) = updateProgress()
                    override fun onIsLoadingChanged(isLoading: Boolean) = updateProgress()
                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = updateModeButtons()
                    override fun onRepeatModeChanged(repeatMode: Int) = updateModeButtons()
                })
            } catch (_: Exception) {
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun updateNowPlaying() {
        val mediaController = controller ?: return
        title.text = mediaController.mediaMetadata.title ?: getString(R.string.unknown_title)
        artist.text = mediaController.mediaMetadata.artist ?: getString(R.string.unknown_artist)
        play.setImageResource(if (mediaController.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
    }

    private fun updateModeButtons() {
        val player = controller ?: return
        shuffle.alpha = if (player.shuffleModeEnabled) 1f else 0.45f
        repeat.alpha = if (player.repeatMode == Player.REPEAT_MODE_OFF) 0.45f else 1f
        repeat.contentDescription = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> "Repeat one"
            Player.REPEAT_MODE_ALL -> "Repeat all"
            else -> "Repeat off"
        }
    }

    private fun updateProgress() {
        val mediaController = controller ?: return
        if (userSeeking) return
        val duration = mediaController.duration
        val position = mediaController.currentPosition.coerceAtLeast(0L)
        val validDuration = duration != C.TIME_UNSET && duration > 0
        progress.isEnabled = validDuration
        progress.progress = if (validDuration) ((position.coerceIn(0L, duration) * 1000L) / duration).toInt() else 0
        elapsed.text = formatTime(position)
        durationText.text = if (validDuration) formatTime(duration) else "--:--"
    }

    private fun formatTime(milliseconds: Long): String {
        val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
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
