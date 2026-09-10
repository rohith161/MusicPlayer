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
        speedButton.setOnClickListener { showSpeedDialog() }
        sleepButton.setOnClickListener { showSleepDialog() }

        progress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                if (!fromUser) return
                val mediaController = controller ?: return
                val duration = mediaController.duration
                if (duration != C.TIME_UNSET && duration > 0) mediaController.seekTo(duration * value.toLong() / 1000L)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { userSeeking = true }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { userSeeking = false; updateProgress() }
        })

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture.addListener({
            try {
                controller = controllerFuture.get()
                updateNowPlaying(); updateProgress(); updateModeButtons(); updateSpeedButton(); updateSleepTimer()
                controller?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) = updateNowPlaying()
                    override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) = updateNowPlaying()
                    override fun onPlaybackStateChanged(playbackState: Int) = updateProgress()
                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = updateModeButtons()
                    override fun onRepeatModeChanged(repeatMode: Int) = updateModeButtons()
                    override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) = updateSpeedButton()
                })
            } catch (_: Exception) { finish() }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun showSpeedDialog() {
        val values = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        val labels = values.map { "${it}×" }.toTypedArray()
        val current = controller?.playbackParameters?.speed ?: 1f
        val selected = values.indices.minByOrNull { kotlin.math.abs(values[it] - current) } ?: 2
        AlertDialog.Builder(this).setTitle("Playback speed").setSingleChoiceItems(labels, selected) { dialog, which ->
            controller?.setPlaybackSpeed(values[which])
            PlaybackPreferences.setSpeed(this, values[which])
            dialog.dismiss()
        }.show()
    }

    private fun showSleepDialog() {
        val labels = arrayOf("Off", "15 minutes", "30 minutes", "45 minutes", "60 minutes", "End of current song")
        AlertDialog.Builder(this).setTitle("Sleep timer").setItems(labels) { _, which ->
            when (which) {
                0 -> { sleepEndElapsed = 0L; PlaybackPreferences.clearSleep(this) }
                1, 2, 3, 4 -> {
                    val minutes = which * 15L
                    sleepEndElapsed = SystemClock.elapsedRealtime() + minutes * 60_000L
                    PlaybackPreferences.setSleepEnd(this, sleepEndElapsed)
                }
                5 -> {
                    val p = controller
                    if (p != null && p.duration != C.TIME_UNSET) {
                        sleepEndElapsed = SystemClock.elapsedRealtime() + (p.duration - p.currentPosition).coerceAtLeast(0L)
                        PlaybackPreferences.setSleepEnd(this, sleepEndElapsed)
                    }
                }
            }
            updateSleepTimer()
        }.show()
    }

    private fun updateNowPlaying() {
        val p = controller ?: return
        title.text = p.mediaMetadata.title ?: getString(R.string.unknown_title)
        artist.text = p.mediaMetadata.artist ?: getString(R.string.unknown_artist)
        play.setImageResource(if (p.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
    }

    private fun updateModeButtons() {
        val p = controller ?: return
        shuffle.alpha = if (p.shuffleModeEnabled) 1f else 0.45f
        repeat.alpha = if (p.repeatMode == Player.REPEAT_MODE_OFF) 0.45f else 1f
        repeat.contentDescription = when (p.repeatMode) {
            Player.REPEAT_MODE_ONE -> "Repeat one"
            Player.REPEAT_MODE_ALL -> "Repeat all"
            else -> "Repeat off"
        }
    }

    private fun updateSpeedButton() {
        val speed = controller?.playbackParameters?.speed ?: PlaybackPreferences.getSpeed(this)
        speedButton.text = "${speed}×"
        speedButton.contentDescription = "Playback speed ${speed} times"
    }

    private fun updateSleepTimer() {
        if (sleepEndElapsed <= 0L) { sleepButton.text = "Sleep"; return }
        val remaining = sleepEndElapsed - SystemClock.elapsedRealtime()
        if (remaining <= 0L) {
            controller?.pause()
            sleepEndElapsed = 0L
            PlaybackPreferences.clearSleep(this)
            Toast.makeText(this, "Sleep timer ended", Toast.LENGTH_SHORT).show()
            sleepButton.text = "Sleep"
            return
        }
        val minutes = (remaining / 60_000L).coerceAtLeast(1L)
        sleepButton.text = "Sleep ${minutes}m"
    }

    private fun updateProgress() {
        val p = controller ?: return
        if (userSeeking) return
        val duration = p.duration
        val position = p.currentPosition.coerceAtLeast(0L)
        val valid = duration != C.TIME_UNSET && duration > 0
        progress.isEnabled = valid
        progress.progress = if (valid) ((position.coerceIn(0L, duration) * 1000L) / duration).toInt() else 0
        elapsed.text = formatTime(position)
        durationText.text = if (valid) formatTime(duration) else "--:--"
    }

    private fun formatTime(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        val h = total / 3600L; val m = (total % 3600L) / 60L; val s = total % 60L
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    override fun onStart() { super.onStart(); progressHandler.post(progressUpdater) }
    override fun onStop() { progressHandler.removeCallbacks(progressUpdater); super.onStop() }
    override fun onDestroy() {
        progressHandler.removeCallbacks(progressUpdater)
        if (::controllerFuture.isInitialized) controllerFuture.cancel(true)
        controller?.release(); controller = null
        super.onDestroy()
    }
}
