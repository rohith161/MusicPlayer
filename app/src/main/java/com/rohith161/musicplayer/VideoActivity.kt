package com.rohith161.musicplayer

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.mediarouter.app.MediaRouteButton
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.PlayerView
import kotlin.math.abs
import kotlin.math.roundToInt

@androidx.media3.common.util.UnstableApi
class VideoActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var audioManager: AudioManager? = null
    private lateinit var prefs: SharedPreferences
    private lateinit var playerView: PlayerView
    private lateinit var controlsOverlay: View
    private lateinit var gestureHint: TextView
    private lateinit var speedButton: Button
    private lateinit var fullscreenButton: Button
    private lateinit var lockButton: Button
    private lateinit var videoUri: Uri
    private var uriKey = ""
    private var startX = 0f
    private var startY = 0f
    private var gestureStartVolume = 0
    private var gestureStartBrightness = 0.5f
    private var gestureSide = 0
    private var gestureActive = false
    private var locked = false
    private var speedActive = false
    private var speedBase = 1f

    private val handler = Handler(Looper.getMainLooper())
    private val hideControls = Runnable { controlsOverlay.visibility = View.GONE }
    private val startSpeedGesture = Runnable {
        speedActive = true
        speedBase = player?.playbackParameters?.speed ?: 1f
        showSpeed(speedBase)
    }

    private val subtitlePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, IntentFlags.READ) }
            applySubtitleUri(uri)
        }
    }

    private val audioPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) applyAudioUri(uri)
    }

    private object IntentFlags { const val READ = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video)
        playerView = findViewById(R.id.videoPlayerView)
        controlsOverlay = findViewById(R.id.videoControlsOverlay)
        gestureHint = findViewById(R.id.videoGestureHint)
        speedButton = findViewById(R.id.videoSpeedButton)
        fullscreenButton = findViewById(R.id.videoFullscreenButton)
        lockButton = findViewById(R.id.videoLockButton)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        prefs = getSharedPreferences("video_resume_positions", MODE_PRIVATE)

        videoUri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse) ?: run { finish(); return }
        uriKey = videoUri.toString()
        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer
        playerView.player = exoPlayer
        playerView.controllerShowTimeoutMs = 3000
        playerView.controllerHideOnTouch = true
        playerView.controllerAutoShow = true
        exoPlayer.setMediaItem(MediaItem.fromUri(videoUri))
        exoPlayer.prepare()
        exoPlayer.seekTo(prefs.getLong(uriKey, 0L).coerceAtLeast(0L))
        exoPlayer.play()

        exoPlayer.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                loudnessEnhancer?.release()
                loudnessEnhancer = if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) runCatching { LoudnessEnhancer(audioSessionId).apply { enabled = true } }.getOrNull() else null
            }
        })

        speedButton.setOnClickListener { cycleSpeed() }
        fullscreenButton.setOnClickListener { toggleFullscreen() }
        lockButton.setOnClickListener { setLocked(!locked) }
        findViewById<Button>(R.id.videoSubtitleButton).setOnClickListener { showSubtitleMenu() }
        findViewById<Button>(R.id.videoAudioButton).setOnClickListener { showAudioMenu() }
        findViewById<MediaRouteButton>(R.id.videoOutputButton).routeSelector = MediaRouteSelector.Builder()
            .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
            .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
            .build()
        playerView.setOnTouchListener { _, event -> handleGesture(event) }
        showControlsTemporarily()
    }

    private fun handleGesture(event: MotionEvent): Boolean {
        if (locked) return true
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x; startY = event.y
                gestureStartVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                gestureStartBrightness = window.attributes.screenBrightness.takeIf { it >= 0f } ?: 0.5f
                gestureSide = if (event.x >= playerView.width / 2f) 1 else -1
                gestureActive = false
                handler.removeCallbacks(startSpeedGesture)
                handler.postDelayed(startSpeedGesture, 350L)
                showControlsTemporarily()
                false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - startX
                val dy = event.y - startY
                if (speedActive) {
                    val speed = (speedBase + dx / playerView.width.coerceAtLeast(1) * 3f).coerceIn(0.25f, 4f)
                    player?.setPlaybackSpeed(speed)
                    showSpeed(speed)
                    true
                } else {
                    if (abs(dy) > 18f && abs(dy) > abs(dx) * 1.15f) gestureActive = true
                    if (!gestureActive) false else {
                        if (gestureSide > 0) adjustVolume(startY - event.y, playerView.height.toFloat())
                        else adjustBrightness(startY - event.y, playerView.height.toFloat())
                        true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(startSpeedGesture)
                if (speedActive) {
                    speedActive = false
                    gestureHint.postDelayed({ if (!speedActive) gestureHint.visibility = View.GONE }, 700L)
                    true
                } else if (gestureActive) {
                    gestureHint.visibility = View.GONE
                    gestureActive = false
                    true
                } else {
                    showControlsTemporarily()
                    false
                }
            }
            else -> speedActive || gestureActive
        }
    }

    private fun cycleSpeed() {
        val speeds = floatArrayOf(1f, 1.25f, 1.5f, 2f, 3f, 4f)
        val current = player?.playbackParameters?.speed ?: 1f
        val index = speeds.indexOfFirst { abs(it - current) < 0.01f }.let { if (it < 0) 0 else it }
        val next = speeds[(index + 1) % speeds.size]
        player?.setPlaybackSpeed(next)
        speedButton.text = "${next}×"
        showSpeed(next)
    }

    private fun showSpeed(speed: Float) {
        gestureHint.text = "${speed}×"
        gestureHint.visibility = View.VISIBLE
        gestureHint.alpha = 0.82f
    }

    private fun adjustVolume(delta: Float, height: Float) {
        val manager = audioManager ?: return
        val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val step = ((delta / height.coerceAtLeast(1f) * 0.55f) * max).roundToInt()
        val target = (gestureStartVolume + step).coerceIn(0, max)
        manager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        val percent = (target * 100f / max).roundToInt()
        loudnessEnhancer?.setTargetGain(if (percent >= 100) 6000 else 0)
        gestureHint.text = if (percent >= 100) "Volume 100% • +6 dB" else "Volume $percent%"
        gestureHint.visibility = View.VISIBLE
        gestureHint.alpha = 0.82f
    }

    private fun adjustBrightness(delta: Float, height: Float) {
        val brightness = (gestureStartBrightness + delta / height.coerceAtLeast(1f) * 0.9f).coerceIn(0.02f, 1f)
        window.attributes = window.attributes.apply { screenBrightness = brightness }
        gestureHint.text = "Brightness ${(brightness * 100).roundToInt()}%"
        gestureHint.visibility = View.VISIBLE
        gestureHint.alpha = 0.82f
    }

    private fun showControlsTemporarily() {
        controlsOverlay.visibility = View.VISIBLE
        handler.removeCallbacks(hideControls)
        handler.postDelayed(hideControls, 3000L)
    }

    private fun showSubtitleMenu() {
        val labels = mutableListOf("Off", "Embedded subtitles", "External subtitle file", "Online subtitle URL")
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Subtitles").setItems(labels.toTypedArray()) { _, which ->
            when (which) {
                0 -> clearExternalSubtitle()
                1 -> selectEmbeddedSubtitle()
                2 -> subtitlePicker.launch(arrayOf("text/vtt", "application/x-subrip", "text/x-ssa", "application/octet-stream"))
                3 -> promptForUrl("Online subtitle URL") { applySubtitleUrl(it) }
            }
        }.show()
    }

    private fun selectEmbeddedSubtitle() {
        val p = player ?: return
        val groups = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        if (groups.isEmpty()) {
            gestureHint.text = "No embedded subtitles"
            gestureHint.visibility = View.VISIBLE
            handler.postDelayed({ gestureHint.visibility = View.GONE }, 1200L)
            return
        }
        val choices = groups.flatMap { group -> (0 until group.length).map { i ->
            val f = group.getTrackFormat(i)
            "${f.language ?: "Unknown"}${if (f.label != null) " • ${f.label}" else ""}"
        } }
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Embedded subtitles").setItems(choices.toTypedArray()) { _, which ->
            var n = which
            groups.forEach { group ->
                if (n < group.length) {
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .addOverride(TrackSelectionOverride(group.mediaTrackGroup, n)).build()
                    return@setItems
                }
                n -= group.length
            }
        }.show()
    }

    private fun clearExternalSubtitle() {
        val p = player ?: return
        val position = p.currentPosition
        val wasPlaying = p.isPlaying
        p.setMediaItem(MediaItem.fromUri(videoUri), position)
        p.prepare()
        if (wasPlaying) p.play()
    }

    private fun applySubtitleUri(uri: Uri) {
        val mime = when (uri.toString().lowercase()) {
            else -> when {
                uri.path?.endsWith(".srt", true) == true -> MimeTypes.APPLICATION_SUBRIP
                uri.path?.endsWith(".ass", true) == true || uri.path?.endsWith(".ssa", true) == true -> MimeTypes.TEXT_SSA
                else -> MimeTypes.TEXT_VTT
            }
        }
        val config = MediaItem.SubtitleConfiguration.Builder(uri).setMimeType(mime).setLanguage("und").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
        applySubtitleConfig(config)
    }

    private fun applySubtitleUrl(url: String) {
        val config = MediaItem.SubtitleConfiguration.Builder(Uri.parse(url)).setMimeType(MimeTypes.TEXT_VTT).setLanguage("und").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
        applySubtitleConfig(config)
    }

    private fun applySubtitleConfig(config: MediaItem.SubtitleConfiguration) {
        val p = player ?: return
        val position = p.currentPosition
        val wasPlaying = p.isPlaying
        val item = MediaItem.Builder().setUri(videoUri).setSubtitleConfigurations(listOf(config)).build()
        p.setMediaItem(item, position)
        p.prepare()
        if (wasPlaying) p.play()
    }

    private fun showAudioMenu() {
        val items = arrayOf("Embedded audio", "External audio file", "Online audio URL")
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Audio track").setItems(items) { _, which ->
            when (which) {
                0 -> selectEmbeddedAudio()
                1 -> audioPicker.launch(arrayOf("audio/*"))
                2 -> promptForUrl("Online audio URL") { applyAudioUrl(it) }
            }
        }.show()
    }

    private fun selectEmbeddedAudio() {
        val p = player ?: return
        val groups = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (groups.isEmpty()) return
        val choices = groups.flatMap { group -> (0 until group.length).map { i ->
            val f = group.getTrackFormat(i)
            "${f.language ?: "Unknown"}${if (f.label != null) " • ${f.label}" else ""}"
        } }
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Embedded audio").setItems(choices.toTypedArray()) { _, which ->
            var n = which
            groups.forEach { group ->
                if (n < group.length) {
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        .addOverride(TrackSelectionOverride(group.mediaTrackGroup, n)).build()
                    return@setItems
                }
                n -= group.length
            }
        }.show()
    }

    private fun applyAudioUri(uri: Uri) = applyMergedAudio(uri)

    private fun applyAudioUrl(url: String) = applyMergedAudio(Uri.parse(url))

    private fun applyMergedAudio(audioUri: Uri) {
        val p = player ?: return
        val position = p.currentPosition
        val wasPlaying = p.isPlaying
        val factory = DefaultMediaSourceFactory(DefaultDataSource.Factory(this))
        val videoSource = factory.createMediaSource(MediaItem.fromUri(videoUri))
        val audioSource = factory.createMediaSource(MediaItem.fromUri(audioUri))
        p.setMediaSource(MergingMediaSource(videoSource, audioSource), position)
        p.prepare()
        if (wasPlaying) p.play()
    }

    private fun promptForUrl(title: String, onSubmit: (String) -> Unit) {
        val input = EditText(this).apply { hint = "https://..."; singleLine = true }
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("Cancel", null).setPositiveButton("Use") { _, _ ->
            val value = input.text.toString().trim()
            if (value.startsWith("https://") || value.startsWith("http://")) onSubmit(value)
        }.show()
    }

    private fun toggleFullscreen() {
        if (Build.VERSION.SDK_INT >= 30) {
            val controller = window.insetsController ?: return
            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            val visible = window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.systemBars()) ?: true
            if (visible) controller.hide(WindowInsets.Type.systemBars()) else controller.show(WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (window.decorView.systemUiVisibility == 0) View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY else 0
        }
        showControlsTemporarily()
    }

    private fun setLocked(value: Boolean) {
        locked = value
        playerView.useController = !locked
        lockButton.text = if (locked) "🔒" else "🔓"
        gestureHint.text = if (locked) "Controls locked" else "Controls unlocked"
        gestureHint.visibility = View.VISIBLE
        handler.postDelayed({ gestureHint.visibility = View.GONE }, 900L)
    }

    private fun savePosition() {
        val p = player ?: return
        if (p.currentPosition > 0L && p.duration != C.TIME_UNSET) prefs.edit().putLong(uriKey, p.currentPosition).apply()
    }

    override fun onPause() {
        savePosition()
        super.onPause()
    }

    override fun onStop() {
        savePosition()
        handler.removeCallbacksAndMessages(null)
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
