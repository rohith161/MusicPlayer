package com.rohith161.musicplayer

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import com.google.common.util.concurrent.ListenableFuture
import kotlin.math.abs

@androidx.media3.common.util.UnstableApi
class HomeActivity : AppCompatActivity() {
    private enum class Mode { VIDEO, MUSIC, PLAYLIST, SEARCH }
    private var mode = Mode.MUSIC
    private var downX = 0f
    private var downY = 0f
    private var swipeTracking = false

    private lateinit var musicRepository: MusicRepository
    private lateinit var videoRepository: VideoRepository
    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var folderAdapter: FolderAdapter
    private lateinit var songAdapter: SongAdapter
    private lateinit var videoAdapter: VideoAdapter
    private lateinit var onlineAdapter: OnlineAdapter
    private lateinit var folderList: androidx.recyclerview.widget.RecyclerView
    private lateinit var songList: androidx.recyclerview.widget.RecyclerView
    private lateinit var videoList: androidx.recyclerview.widget.RecyclerView
    private lateinit var onlineList: androidx.recyclerview.widget.RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var emptyTitle: TextView
    private lateinit var emptySubtitle: TextView
    private lateinit var searchInput: EditText
    private lateinit var searchControls: LinearLayout
    private lateinit var miniPlayer: LinearLayout
    private lateinit var nowTitle: TextView
    private lateinit var nowArtist: TextView
    private lateinit var miniPlayButton: ImageButton
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private var tracks: List<Track> = emptyList()
    private var videos: List<LocalVideo> = emptyList()
    private var currentFolder: String? = null

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { loadLibraries() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        musicRepository = MusicRepository(contentResolver)
        videoRepository = VideoRepository(contentResolver)
        playlistRepository = PlaylistRepository(this)

        folderList = findViewById(R.id.folderList)
        songList = findViewById(R.id.songList)
        videoList = findViewById(R.id.videoList)
        onlineList = findViewById(R.id.onlineList)
        emptyState = findViewById(R.id.emptyState)
        emptyTitle = findViewById(R.id.emptyTitle)
        emptySubtitle = findViewById(R.id.emptySubtitle)
        searchInput = findViewById(R.id.searchInput)
        searchControls = findViewById(R.id.searchControls)
        miniPlayer = findViewById(R.id.miniPlayer)
        nowTitle = findViewById(R.id.nowTitle)
        nowArtist = findViewById(R.id.nowArtist)
        miniPlayButton = findViewById(R.id.playButton)
        findViewById<View>(R.id.grantPermissionButton).visibility = View.GONE

        folderAdapter = FolderAdapter { openFolder(it.name) }
        songAdapter = SongAdapter { playTrack(it) }
        videoAdapter = VideoAdapter { playVideo(it) }
        onlineAdapter = OnlineAdapter { track ->
            if (mode == Mode.PLAYLIST) showPlaylistActions(track)
        }
        setupList(folderList, folderAdapter)
        setupList(songList, songAdapter)
        setupList(videoList, videoAdapter)
        setupList(onlineList, onlineAdapter)

        findViewById<androidx.mediarouter.app.MediaRouteButton>(R.id.mediaOutputButton).routeSelector =
            MediaRouteSelector.Builder()
                .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
                .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                .build()

        setupNavigation()
        setupSearch()
        setupPlayback()
        setupMiniPlayer()
        setupSwipe()

        if (!getPreferences(MODE_PRIVATE).getBoolean("permission_prompted", false) && !hasAudioPermission()) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("permission_prompted", true).apply()
            requestMediaPermissions()
        } else {
            loadLibraries()
        }
    }

    private fun setupNavigation() {
        findViewById<TextView>(R.id.musicTab).setOnClickListener { selectMode(Mode.MUSIC) }
        findViewById<TextView>(R.id.videoTab).setOnClickListener { selectMode(Mode.VIDEO) }
        findViewById<TextView>(R.id.onlineTab).setOnClickListener { selectMode(Mode.PLAYLIST) }
        findViewById<TextView>(R.id.searchTab).setOnClickListener { selectMode(Mode.SEARCH) }
        selectMode(Mode.MUSIC)
    }

    private fun setupSearch() {
        findViewById<Button>(R.id.searchButton).setOnClickListener { performLocalSearch() }
        searchInput.setOnEditorActionListener { _, _, _ -> performLocalSearch(); true }
    }

    private fun selectMode(newMode: Mode) {
        mode = newMode
        currentFolder = null
        findViewById<View>(R.id.folderHeader).visibility = View.GONE
        searchInput.setText("")
        updateTabs()
        when (mode) {
            Mode.VIDEO -> showVideos()
            Mode.MUSIC -> showFolders()
            Mode.PLAYLIST -> showPlaylist()
            Mode.SEARCH -> showSearch()
        }
    }

    private fun updateTabs() {
        val pairs = listOf(Mode.VIDEO to R.id.videoTab, Mode.MUSIC to R.id.musicTab, Mode.PLAYLIST to R.id.onlineTab, Mode.SEARCH to R.id.searchTab)
        pairs.forEach { (tabMode, id) ->
            val tab = findViewById<TextView>(id)
            tab.background = if (mode == tabMode) getDrawable(R.drawable.bg_tab_selected) else null
            tab.setTextColor(getColor(if (mode == tabMode) R.color.mp_foreground else R.color.mp_foreground_muted))
        }
    }

    private fun showFolders() {
        searchControls.visibility = View.GONE
        showOnly(folderList)
        findViewById<TextView>(R.id.screenTitle).text = "YOUR MUSIC"
        findViewById<TextView>(R.id.screenSubtitle).text = getString(R.string.folders_subtitle)
        val list = tracks.groupBy { it.folder }.map { FolderItem(it.key, it.value.size) }
        folderAdapter.submitList(list)
        setEmpty(list.isEmpty(), "No music found", "Your local music will appear here.")
    }

    private fun openFolder(folder: String) {
        currentFolder = folder
        findViewById<View>(R.id.folderHeader).visibility = View.VISIBLE
        showOnly(songList)
        val list = tracks.filter { it.folder == folder }
        songAdapter.submitList(list)
        setEmpty(list.isEmpty(), "Folder is empty", "No playable music was found here.")
    }

    private fun showVideos() {
        searchControls.visibility = View.GONE
        showOnly(videoList)
        findViewById<TextView>(R.id.screenTitle).text = "VIDEO"
        findViewById<TextView>(R.id.screenSubtitle).text = getString(R.string.videos_subtitle)
        videoAdapter.submitList(videos)
        setEmpty(videos.isEmpty(), "No videos found", "Your local videos will appear here.")
    }

    private fun showPlaylist() {
        searchControls.visibility = View.GONE
        showOnly(onlineList)
        findViewById<TextView>(R.id.screenTitle).text = "PLAYLIST"
        findViewById<TextView>(R.id.screenSubtitle).text = "Saved songs"
        onlineAdapter.setPlaylistMode(true)
        val list = playlistRepository.getAll()
        onlineAdapter.submitList(list)
        setEmpty(list.isEmpty(), "Playlist is empty", "Saved songs will appear here.")
    }

    private fun showSearch() {
        searchControls.visibility = View.VISIBLE
        showOnly(songList)
        findViewById<TextView>(R.id.screenTitle).text = "SEARCH"
        findViewById<TextView>(R.id.screenSubtitle).text = "Local music only"
        onlineAdapter.setPlaylistMode(false)
        setEmpty(false, "", "")
    }

    private fun performLocalSearch() {
        val q = searchInput.text.toString().trim()
        if (q.isBlank()) {
            setEmpty(true, "Enter a search", "Type a title, artist, or album.")
            return
        }
        showOnly(songList)
        val result = tracks.filter {
            it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true) || it.folder.contains(q, true)
        }
        songAdapter.submitList(result)
        setEmpty(result.isEmpty(), "No local matches", "Try another title, artist, album, or folder.")
    }

    private fun showPlaylistActions(track: OnlineTrack) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(track.title)
            .setMessage(track.creator)
            .setItems(arrayOf("Play now", "Remove from playlist")) { _, which ->
                if (which == 0) {
                    track.audioUrl?.let { playUri(it, track.title, track.creator) }
                } else {
                    playlistRepository.remove(track.identifier)
                    showPlaylist()
                }
            }.show()
    }

    private fun playUri(uri: String, title: String, artist: String) {
        controller?.setMediaItem(MediaItem.Builder().setUri(uri).setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).build()).build())
        controller?.prepare()
        controller?.play()
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    private fun playTrack(track: Track) {
        val items = tracks.map { MediaItem.Builder().setUri(it.uri).setMediaId(it.id.toString()).setMediaMetadata(MediaMetadata.Builder().setTitle(it.title).setArtist(it.artist).setAlbumTitle(it.album).build()).build() }
        val index = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        controller?.setMediaItems(items, index, 0L)
        controller?.prepare()
        controller?.play()
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    private fun playVideo(video: LocalVideo) {
        startActivity(Intent(this, VideoActivity::class.java).apply {
            putExtra(VideoActivity.EXTRA_URI, video.uri.toString())
            putExtra(VideoActivity.EXTRA_TITLE, video.title)
        })
    }

    private fun setupPlayback() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture.addListener({
            controller = runCatching { controllerFuture.get() }.getOrNull()
            controller?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) = updateMiniPlayer()
                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = updateMiniPlayer()
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = updateMiniPlayer()
            })
            updateMiniPlayer()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupMiniPlayer() {
        findViewById<View>(R.id.miniPlayerInfo).setOnClickListener { startActivity(Intent(this, PlayerActivity::class.java)) }
        miniPlayButton.setOnClickListener { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
        findViewById<ImageButton>(R.id.prevButton).setOnClickListener { controller?.seekToPreviousMediaItem() }
        findViewById<ImageButton>(R.id.nextButton).setOnClickListener { controller?.seekToNextMediaItem() }
    }

    private fun updateMiniPlayer() {
        val c = controller ?: return
        val hasMedia = c.mediaItemCount > 0 && c.currentMediaItem != null
        miniPlayer.visibility = if (hasMedia) View.VISIBLE else View.GONE
        if (!hasMedia) return
        nowTitle.text = c.mediaMetadata.title ?: getString(R.string.unknown_title)
        nowArtist.text = c.mediaMetadata.artist ?: getString(R.string.unknown_artist)
        miniPlayButton.setImageResource(if (c.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
    }

    private fun setupSwipe() {
        // Touch is observed at Activity level so RecyclerViews and other child views cannot swallow the tab swipe.
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                swipeTracking = true
            }
            MotionEvent.ACTION_UP -> {
                if (swipeTracking) handleSwipe(event.rawX - downX, event.rawY - downY)
                swipeTracking = false
            }
            MotionEvent.ACTION_CANCEL -> swipeTracking = false
        }
        return super.dispatchTouchEvent(event)
    }

    private fun handleSwipe(dx: Float, dy: Float) {
        if (abs(dx) <= 100f || abs(dx) <= abs(dy) * 1.3f) return
        val order = listOf(Mode.VIDEO, Mode.MUSIC, Mode.PLAYLIST, Mode.SEARCH)
        val i = order.indexOf(mode)
        val next = if (dx < 0f) order[(i + 1) % order.size] else order[(i - 1 + order.size) % order.size]
        selectMode(next)
    }

    private fun loadLibraries() {
        Thread {
            val a = if (hasAudioPermission()) musicRepository.loadTracks() else emptyList()
            val v = if (hasVideoPermission()) videoRepository.loadVideos() else emptyList()
            runOnUiThread {
                tracks = a
                videos = v
                when (mode) {
                    Mode.MUSIC -> showFolders()
                    Mode.VIDEO -> showVideos()
                    Mode.PLAYLIST -> showPlaylist()
                    Mode.SEARCH -> Unit
                }
                updateMiniPlayer()
            }
        }.start()
    }

    private fun setupList(list: androidx.recyclerview.widget.RecyclerView, adapter: androidx.recyclerview.widget.RecyclerView.Adapter<*>) {
        list.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        list.adapter = adapter
    }

    private fun showOnly(target: View) {
        listOf(folderList, songList, videoList, onlineList).forEach { it.visibility = if (it === target) View.VISIBLE else View.GONE }
        emptyState.visibility = View.GONE
    }

    private fun setEmpty(empty: Boolean, title: String, subtitle: String) {
        if (empty) {
            emptyTitle.text = title
            emptySubtitle.text = subtitle
            emptyState.visibility = View.VISIBLE
        } else {
            emptyState.visibility = View.GONE
        }
    }

    private fun hasAudioPermission() = ContextCompat.checkSelfPermission(this, if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    private fun hasVideoPermission() = ContextCompat.checkSelfPermission(this, if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun requestMediaPermissions() {
        val p = buildList {
            add(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.READ_MEDIA_VIDEO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(p.toTypedArray())
    }

    override fun onResume() {
        super.onResume()
        updateMiniPlayer()
    }

    override fun onDestroy() {
        if (::controllerFuture.isInitialized) controllerFuture.cancel(true)
        controller?.release()
        controller = null
        super.onDestroy()
    }
}
