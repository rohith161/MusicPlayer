package com.rohith161.musicplayer

import android.content.Context
import android.graphics.Color
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var gestureHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video)

        val playerView = findViewById<PlayerView>(R.id.videoPlayerView)
        gestureHint = findViewById(R.id.videoGestureHint)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val uri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse) ?: run {
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
                loudnessEnhancer = if (audioSessionId != androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) {
                    runCatching {
                        LoudnessEnhancer(audioSessionId).apply {
                            // Positive gain provides a real volume-boost path above the device max.
                            setTargetGain(6000)
                            enabled = true
                        }
                    }.getOrNull()
                } else {
                    null
                }
            }
        })

        findViewById<View>(R.id.videoGestureOverlay).setOnTouchListener { _, event ->
            handleGesture(event)
            true
        }
    }

    private fun handleGesture(event: MotionEvent) {
        val width = findViewById<View>(R.id.videoGestureOverlay).width
        if (width <= 0) return

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startY = event.y
                gestureStartVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                gestureStartBrightness = window.attributes.screenBrightness.takeIf { it >= 0f } ?: 0.5f
                gestureSide = if (event.x >= width / 2f) 1 else -1
                gestureActive = false
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val delta = startY - event.y
                if (!gestureActive && abs(delta) > 12f) gestureActive = true
                if (!gestureActive) return

                if (gestureSide > 0) {
                    adjustVolume(delta, width.toFloat())
                } else {
                    adjustBrightness(delta, width.toFloat())
                }
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (gestureActive) showGestureHint("")
                gestureActive = false
                true
            }
            else -> true
        }
    }

    private fun adjustVolume(delta: Float, heightReference: Float) {
        val manager = audioManager ?: return
        val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val step = ((delta / (heightReference * 0.55f)) * max).roundToInt()
        val target = (gestureStartVolume + step).coerceIn(0, max)
        manager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)

        val percent = (target * 100f / max).roundToInt()
        if (percent >= 100) {
            showGestureHint("Volume $percent% • Boost +6 dB")
        } else {
            showGestureHint("Volume $percent%")
        }
    }

    private fun adjustBrightness(delta: Float, heightReference: Float) {
        val change = delta / (heightReference * 0.9f)
        val brightness = (gestureStartBrightness + change).coerceIn(0.02f, 1f)
        val params = window.attributes
        params.screenBrightness = brightness
        window.attributes = params
        showGestureHint("Brightness ${(brightness * 100).roundToInt()}%")
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
