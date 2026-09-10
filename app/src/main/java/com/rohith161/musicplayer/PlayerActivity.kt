package com.rohith161.musicplayer

import android.app.AlertDialog
import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

@UnstableApi
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
    private lateinit var speedButton: TextView
    private lateinit var sleepButton: TextView
    private val progressHandler = Handler(Looper.getMainLooper())
    private var userSeeking = false
    private var sleepEndElapsed = 0L

    private val progressUpdater = object : Runnable {
        override fun run() {
            updateProgress()
            updateSleepTimer()
            progressHandler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        applySystemInsets()
        title = findViewById(R.id.playerTitle)
        artist = findViewById(R.id.playerArtist)
        play = findViewById(R.id.playerPlay)
        shuffle = findViewById(R.id.playerShuffle)
        repeat = findViewById(R.id.playerRepeat)
        progress = findViewById(R.id.playerProgress)
        elapsed = findViewById(R.id.playerElapsed)
        durationText = findViewById(R.id.playerDuration)
        speedButton = findViewById(R.id.playerSpeed)
        sleepButton = findViewById(R.id.playerSleep)
        progress.max = 1000
        sleepEndElapsed = PlaybackPreferences.getSleepEnd(this)

        findViewById<ImageButton>(R.id.playerClose).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.playerPrevious).setOnClickListener { controller?.seekToPreviousMediaItem() }
        play.setOnClickListener { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
        findViewById<ImageButton>(R.id.playerNext).setOnClickListener { controller?.seekToNextMediaItem() }
        shuffle.setOnClickListener {
            controller?.let { player -> player.shuffleModeEnabled = !player.shuffleModeEnabled; updateModeButtons() }
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
        speedButton.setOnClickListener { chooseSpeed() }
        sleepButton.setOnClickListener { chooseSleepTimer() }
        progress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) { userSeeking = true }
            override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) {
                if (fromUser) elapsed.text = formatTime((controller?.duration ?: 0L) * value / 1000L)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                controller?.let { player ->
                    val duration = player.duration.coerceAtLeast(0L)
                    player.seekTo(duration * seekBar.progress / 1000L)
                }
                userSeeking = false
            }
        })
        connectController()
    }

    private fun applySystemInsets() {
        val root = findViewById<android.view.View>(R.id.playerRoot)
        val left = root.paddingLeft
        val top = root.paddingTop
        val right = root.paddingRight
        val bottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun connectController() {
        controllerFuture = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
        controllerFuture.addListener({
            controller = runCatching { controllerFuture.get() }.getOrNull()
            controller?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) = refresh()
                override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) = refresh()
                override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) = refresh()
            })
            refresh()
            progressHandler.post(progressUpdater)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun refresh() {
        val player = controller ?: return
        title.text = player.mediaMetadata.title ?: getString(R.string.unknown_title)
        artist.text = player.mediaMetadata.artist ?: getString(R.string.unknown_artist)
        play.setImageResource(if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        updateModeButtons()
        speedButton.text = "${String.format(java.util.Locale.US, "%.2f", player.playbackParameters.speed)}×"
    }

    private fun updateProgress() {
        val player = controller ?: return
        val duration = player.duration
        if (!userSeeking && duration > 0) progress.progress = ((player.currentPosition * 1000L) / duration).toInt().coerceIn(0, 1000)
        elapsed.text = formatTime(player.currentPosition)
        durationText.text = if (duration > 0) formatTime(duration) else "--:--"
    }

    private fun updateModeButtons() {
        val player = controller ?: return
        shuffle.setColorFilter(ContextCompat.getColor(this, if (player.shuffleModeEnabled) R.color.mp_accent else R.color.mp_foreground))
        repeat.setColorFilter(ContextCompat.getColor(this, if (player.repeatMode != Player.REPEAT_MODE_OFF) R.color.mp_accent else R.color.mp_foreground))
    }

    private fun chooseSpeed() {
        val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        val labels = speeds.map { "${String.format(java.util.Locale.US, "%.2f", it)}×" }.toTypedArray()
        val current = controller?.playbackParameters?.speed ?: 1f
        val selected = speeds.indices.minByOrNull { kotlin.math.abs(speeds[it] - current) } ?: 2
        AlertDialog.Builder(this).setTitle("Playback speed").setSingleChoiceItems(labels, selected) { dialog, which ->
            controller?.setPlaybackSpeed(speeds[which])
            PlaybackPreferences.setSpeed(this, speeds[which])
            dialog.dismiss()
            refresh()
        }.show()
    }

    private fun chooseSleepTimer() {
        val labels = arrayOf("Off", "15 minutes", "30 minutes", "45 minutes", "60 minutes", "End of song")
        AlertDialog.Builder(this).setTitle("Sleep timer").setItems(labels) { _, which ->
            when (which) {
                0 -> { sleepEndElapsed = 0L; PlaybackPreferences.clearSleep(this); sleepButton.text = "SLEEP" }
                1, 2, 3, 4 -> {
                    val minutes = intArrayOf(15, 30, 45, 60)[which - 1]
                    sleepEndElapsed = SystemClock.elapsedRealtime() + minutes * 60_000L
                    PlaybackPreferences.setSleepEnd(this, sleepEndElapsed)
                    sleepButton.text = "${minutes}M"
                }
                5 -> {
                    sleepEndElapsed = -1L
                    PlaybackPreferences.setSleepEnd(this, sleepEndElapsed)
                    sleepButton.text = "END"
                }
            }
        }.show()
    }

    private fun updateSleepTimer() {
        if (sleepEndElapsed == 0L) return
        val player = controller ?: return
        if (sleepEndElapsed == -1L) {
            if (player.currentMediaItem != null && player.currentPosition >= player.duration && player.duration > 0) player.pause()
            return
        }
        if (SystemClock.elapsedRealtime() >= sleepEndElapsed) {
            player.pause()
            sleepEndElapsed = 0L
            PlaybackPreferences.clearSleep(this)
            sleepButton.text = "SLEEP"
            Toast.makeText(this, "Sleep timer finished", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatTime(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        return "${total / 60}:${String.format(java.util.Locale.US, "%02d", total % 60)}"
    }

    override fun onDestroy() {
        progressHandler.removeCallbacks(progressUpdater)
        if (::controllerFuture.isInitialized) controllerFuture.cancel(true)
        controller?.release()
        controller = null
        super.onDestroy()
    }
}
