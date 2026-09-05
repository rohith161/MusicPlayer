package com.rohith161.musicplayer

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.mediarouter.app.MediaRouteButton
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlin.math.abs
import kotlin.math.roundToInt

@androidx.media3.common.util.UnstableApi
class VideoActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var audioManager: AudioManager? = null
    private var startY = 0f
    private var gestureStartVolume = 0
    private var gestureStartBrightness = 0.5f
    private var gestureActive = false
    private var gestureSide = 0
    private var locked = false
    private var speedIndex = 0
    private lateinit var playerView: PlayerView
    private lateinit var gestureHint: TextView
    private lateinit var speedButton: Button
    private lateinit var fullscreenButton: Button
    private lateinit var lockButton: Button

    private val speeds = floatArrayOf(1f, 1.25f, 1.5f, 2f, 3f, 4f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video)

        playerView = findViewById(R.id.videoPlayerView)
        gestureHint = findViewById(R.id.videoGestureHint)
        speedButton = findViewById(R.id.videoSpeedButton)
        fullscreenButton = findViewById(R.id.videoFullscreenButton)
        lockButton = findViewById(R.id.videoLockButton)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val uri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse)
        if (uri == null) {
            finish()
            return
        }

        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer
        playerView.player = exoPlayer
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
        exoPlayer.play()

        exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                loudnessEnhancer?.release()
                loudnessEnhancer = if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    runCatching { LoudnessEnhancer(audioSessionId).apply { enabled = true } }.getOrNull()
                } else null
            }
        })

        speedButton.setOnClickListener {
            speedIndex = (speedIndex + 1) % speeds.size
            val speed = speeds[speedIndex]
            player?.setPlaybackSpeed(speed)
            speedButton.text = "${speed}×"
        }

        val selector = MediaRouteSelector.Builder()
            .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
            .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
            .build()
        findViewById<MediaRouteButton>(R.id.videoOutputButton).routeSelector = selector

        fullscreenButton.setOnClickListener { toggleFullscreen() }
        lockButton.setOnClickListener { setLocked(!locked) }
        playerView.setOnTouchListener { _, event -> handleGesture(event) }
    }

    private fun handleGesture(event: MotionEvent): Boolean {
        if (locked) return true
        val width = playerView.width
        if (width <= 0) return false

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startY = event.y
                gestureStartVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                gestureStartBrightness = window.attributes.screenBrightness.takeIf { it >= 0f } ?: 0.5f
                gestureSide = if (event.x >= width / 2f) 1 else -1
                gestureActive = false
                false
            }
            MotionEvent.ACTION_MOVE -> {
                val delta = startY - event.y
                if (!gestureActive && abs(delta) > 12f) gestureActive = true
                if (!gestureActive) false
                else {
                    if (gestureSide > 0) adjustVolume(delta, playerView.height.toFloat())
                    else adjustBrightness(delta, playerView.height.toFloat())
                    true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (gestureActive) {
                    showGestureHint("")
                    gestureActive = false
                    true
                } else false
            }
            else -> gestureActive
        }
    }

    private fun adjustVolume(delta: Float, heightReference: Float) {
        val manager = audioManager ?: return
        val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val step = ((delta / (heightReference.coerceAtLeast(1f) * 0.55f)) * max).roundToInt()
        val target = (gestureStartVolume + step).coerceIn(0, max)
        manager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        val percent = (target * 100f / max).roundToInt()
        if (percent >= 100) {
            loudnessEnhancer?.setTargetGain(6000)
            showGestureHint("Volume 100% • Boost +6 dB")
        } else {
            loudnessEnhancer?.setTargetGain(0)
            showGestureHint("Volume $percent%")
        }
    }

    private fun adjustBrightness(delta: Float, heightReference: Float) {
        val change = delta / (heightReference.coerceAtLeast(1f) * 0.9f)
        val brightness = (gestureStartBrightness + change).coerceIn(0.02f, 1f)
        val params = window.attributes
        params.screenBrightness = brightness
        window.attributes = params
        showGestureHint("Brightness ${(brightness * 100).roundToInt()}%")
    }

    private fun toggleFullscreen() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val controller = window.insetsController ?: return
            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            val visible = window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.systemBars()) ?: true
            if (visible) controller.hide(WindowInsets.Type.systemBars()) else controller.show(WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (window.decorView.systemUiVisibility == 0) {
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            } else 0
        }
    }

    private fun setLocked(value: Boolean) {
        locked = value
        playerView.useController = !locked
        lockButton.text = if (locked) "🔒" else "🔓"
        showGestureHint(if (locked) "Controls locked" else "Controls unlocked")
        lockButton.postDelayed({ showGestureHint("") }, 900L)
    }

    private fun showGestureHint(text: String) {
        gestureHint.text = text
        gestureHint.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onStop() {
        player?.release()
        player = null
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        super.onStop()
    }

    companion object {
        const val EXTRA_URI = "video_uri"
        const val EXTRA_TITLE = "video_title"
    }
}
