package com.rohith161.musicplayer

import android.Manifest
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

@androidx.media3.common.util.UnstableApi
class HomeActivity : AppCompatActivity() {
    private enum class Mode { VIDEO, MUSIC, PLAYLIST, SEARCH }
    private var mode = Mode.MUSIC
    private var searchOnline = false
    private var downY = 0f
    private var downX = 0f
    private lateinit var musicRepository: MusicRepository
    private lateinit var videoRepository: VideoRepository
    private lateinit var onlineRepository: OnlineRepository
    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var folderAdapter: FolderAdapter
    private lateinit var songAdapter: SongAdapter
    private lateinit var videoAdapter: VideoAdapter
    private lateinit var onlineAdapter: OnlineAdapter
    private lateinit var folderList: androidx.recyclerview.widget.RecyclerView
    private lateinit var songList: androidx.recyclerview.widget.RecyclerView
    private lateinit var videoList: androidx.recyclerview.widget.RecyclerView
    private lateinit var onlineList: androidx.recyclerview.widget.RecyclerView
    private lateinit var root: View
    private lateinit var emptyState: LinearLayout
    private lateinit var emptyTitle: TextView
    private lateinit var emptySubtitle: TextView
    private lateinit var searchInput: EditText
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private var tracks: List<Track> = emptyList()
    private var videos: List<LocalVideo> = emptyList()
    private var currentFolder: String? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { loadLibraries() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        root = findViewById(android.R.id.content)
        musicRepository = MusicRepository(contentResolver)
        videoRepository = VideoRepository(contentResolver)
        onlineRepository = OnlineRepository()
        playlistRepository = PlaylistRepository(this)
        folderList = findViewById(R.id.folderList); songList = findViewById(R.id.songList)
        videoList = findViewById(R.id.videoList); onlineList = findViewById(R.id.onlineList)
        emptyState = findViewById(R.id.emptyState); emptyTitle = findViewById(R.id.emptyTitle); emptySubtitle = findViewById(R.id.emptySubtitle)
        searchInput = findViewById(R.id.searchInput)
        findViewById<View>(R.id.grantPermissionButton).visibility = View.GONE
        folderAdapter = FolderAdapter { openFolder(it.name) }
        songAdapter = SongAdapter { playTrack(it) }
        videoAdapter = VideoAdapter { playVideo(it) }
        onlineAdapter = OnlineAdapter { showOnlineActions(it) }
        setupList(folderList, folderAdapter); setupList(songList, songAdapter); setupList(videoList, videoAdapter); setupList(onlineList, onlineAdapter)
        findViewById<androidx.mediarouter.app.MediaRouteButton>(R.id.mediaOutputButton).routeSelector = MediaRouteSelector.Builder().addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO).addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK).build()
        setupNavigation()
        setupPlayback()
        root.setOnTouchListener { _, event -> handleSwipe(event) }
        if (!getPreferences(MODE_PRIVATE).getBoolean("permission_prompted", false) && !hasAudioPermission()) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("permission_prompted", true).apply()
            requestMediaPermissions()
        } else loadLibraries()
    }

    private fun setupNavigation() {
        val music = findViewById<TextView>(R.id.musicTab)
        val video = findViewById<TextView>(R.id.videoTab)
        val online = findViewById<TextView>(R.id.onlineTab)
        music.text = "Music"; video.text = "Video"; online.text = "Playlist"
        music.setOnClickListener { selectMode(Mode.MUSIC) }; video.setOnClickListener { selectMode(Mode.VIDEO) }; online.setOnClickListener { selectMode(Mode.PLAYLIST) }
        val nav = findViewById<LinearLayout>(R.id.modeNav)
        val search = TextView(this).apply { text = "Search"; gravity = android.view.Gravity.CENTER; textSize = 13f; setTextColor(getColor(R.color.mp_foreground_muted)); layoutParams = LinearLayout.LayoutParams(0, -1, 1f) }
        nav.addView(search)
        search.setOnClickListener { selectMode(Mode.SEARCH) }
        updateTabs()
        searchInput.visibility = View.GONE
    }

    private fun selectMode(newMode: Mode) {
        mode = newMode; currentFolder = null; findViewById<View>(R.id.folderHeader).visibility = View.GONE
        searchInput.setText(""); updateTabs()
        when (mode) {
            Mode.VIDEO -> showVideos(); Mode.MUSIC -> showFolders(); Mode.PLAYLIST -> showPlaylist(); Mode.SEARCH -> showSearch()
        }
    }

    private fun updateTabs() {
        val ids = listOf(R.id.videoTab, R.id.musicTab, R.id.onlineTab)
        ids.forEach { id -> val v = findViewById<TextView>(id); v.background = if ((mode == Mode.VIDEO && id == R.id.videoTab) || (mode == Mode.MUSIC && id == R.id.musicTab) || (mode == Mode.PLAYLIST && id == R.id.onlineTab)) getDrawable(R.drawable.bg_tab_selected) else null }
    }

    private fun showFolders() { searchInput.visibility = View.GONE; switcher?.visibility = View.GONE; showOnly(folderList); findViewById<TextView>(R.id.screenTitle).text = "YOUR MUSIC"; findViewById<TextView>(R.id.screenSubtitle).text = getString(R.string.folders_subtitle); val list = tracks.groupBy { it.folder }.map { FolderItem(it.key, it.value.size) }; folderAdapter.submitList(list); setEmpty(list.isEmpty(), "No music found", "Your local music will appear here.") }
    private fun openFolder(folder: String) { currentFolder = folder; findViewById<View>(R.id.folderHeader).visibility = View.VISIBLE; showOnly(songList); val list = tracks.filter { it.folder == folder }; songAdapter.submitList(list); setEmpty(list.isEmpty(), "Folder is empty", "No playable music was found here.") }
    private fun showVideos() { searchInput.visibility = View.GONE; switcher?.visibility = View.GONE; showOnly(videoList); findViewById<TextView>(R.id.screenTitle).text = "VIDEO"; findViewById<TextView>(R.id.screenSubtitle).text = getString(R.string.videos_subtitle); videoAdapter.submitList(videos); setEmpty(videos.isEmpty(), "No videos found", "Your local videos will appear here.") }
    private fun showPlaylist() { searchInput.visibility = View.GONE; switcher?.visibility = View.GONE; showOnly(onlineList); findViewById<TextView>(R.id.screenTitle).text = "PLAYLIST"; findViewById<TextView>(R.id.screenSubtitle).text = "Saved online songs"; val list = playlistRepository.getAll(); onlineAdapter.submitList(list); setEmpty(list.isEmpty(), "Playlist is empty", "Save online songs from Search to keep them here.") }
    private fun showSearch() { searchInput.visibility = View.VISIBLE; showOnly(onlineList); findViewById<TextView>(R.id.screenTitle).text = "SEARCH"; findViewById<TextView>(R.id.screenSubtitle).text = if (searchOnline) "Online open audio" else "Local music"; ensureSearchSwitcher(); onlineAdapter.submitList(emptyList()); setEmpty(true, if (searchOnline) "Search online" else "Search local music", "Choose Local or Online, enter a query, then press Search.") }
    private var switcher: LinearLayout? = null
    private fun ensureSearchSwitcher() { if (switcher != null) { switcher?.visibility = View.VISIBLE; return }; val nav = findViewById<LinearLayout>(R.id.modeNav); switcher = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 0); layoutParams = LinearLayout.LayoutParams(-1, 44) }; val local = TextView(this).apply { text = "Local"; gravity = android.view.Gravity.CENTER; setOnClickListener { searchOnline = false; showSearch() } }; val online = TextView(this).apply { text = "Online"; gravity = android.view.Gravity.CENTER; setOnClickListener { searchOnline = true; showSearch() } }; switcher!!.addView(local, LinearLayout.LayoutParams(0, -1, 1f)); switcher!!.addView(online, LinearLayout.LayoutParams(0, -1, 1f)); (nav.parent as ViewGroup).addView(switcher, (nav.parent as ViewGroup).indexOfChild(nav) + 1, switcher!!.layoutParams) }

    private fun performSearch() { val q = searchInput.text.toString().trim(); if (q.isBlank()) return; if (!searchOnline) { val result = tracks.filter { it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true) }; showOnly(songList); songAdapter.submitList(result); setEmpty(result.isEmpty(), "No local matches", "Try another title, artist, or album.") } else { showOnly(onlineList); findViewById<ProgressBar>(R.id.onlineProgress).visibility = View.VISIBLE; Thread { runCatching { onlineRepository.search(q) }.onSuccess { result -> runOnUiThread { findViewById<ProgressBar>(R.id.onlineProgress).visibility = View.GONE; onlineAdapter.submitList(result); setEmpty(result.isEmpty(), "No online results", "Try another search.") } }.onFailure { e -> runOnUiThread { findViewById<ProgressBar>(R.id.onlineProgress).visibility = View.GONE; setEmpty(true, "Search failed", e.message ?: "Check your connection.") } } }.start() } }

    private fun showOnlineActions(track: OnlineTrack) { AlertDialog.Builder(this).setTitle(track.title).setMessage(track.creator).setItems(arrayOf("Play now", "Save to playlist", "Download")) { _, which -> when (which) { 0 -> playOnline(track); 1 -> { playlistRepository.add(track); Toast.makeText(this, "Saved to playlist", Toast.LENGTH_SHORT).show() }; 2 -> download(track) } }.show() }
    private fun playOnline(track: OnlineTrack) { Thread { runCatching { onlineRepository.resolveAudioUrl(track.identifier) }.onSuccess { url -> runOnUiThread { if (url == null) Toast.makeText(this, "No playable audio found", Toast.LENGTH_LONG).show() else playUri(url, track.title, track.creator) } }.onFailure { e -> runOnUiThread { Toast.makeText(this, "Online playback failed: ${e.message}", Toast.LENGTH_LONG).show() } } }.start() }
    private fun playUri(uri: String, title: String, artist: String) { controller?.setMediaItem(MediaItem.Builder().setUri(uri).setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).build()).build()); controller?.prepare(); controller?.play(); startActivity(Intent(this, PlayerActivity::class.java)) }
    private fun playTrack(track: Track) { val items = tracks.map { MediaItem.Builder().setUri(it.uri).setMediaId(it.id.toString()).setMediaMetadata(MediaMetadata.Builder().setTitle(it.title).setArtist(it.artist).setAlbumTitle(it.album).build()).build() }; val index = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0); controller?.setMediaItems(items, index, 0L); controller?.prepare(); controller?.play(); startActivity(Intent(this, PlayerActivity::class.java)) }
    private fun playVideo(video: LocalVideo) { startActivity(Intent(this, VideoActivity::class.java).apply { putExtra(VideoActivity.EXTRA_URI, video.uri.toString()); putExtra(VideoActivity.EXTRA_TITLE, video.title) }) }

    private fun download(track: OnlineTrack) { onlineAdapter.setDownloadProgress(track.identifier, 1); Thread { try { val url = onlineRepository.resolveAudioUrl(track.identifier) ?: throw IllegalStateException("No audio file"); val c = URL(url).openConnection() as HttpURLConnection; c.connectTimeout = 10000; c.readTimeout = 20000; c.connect(); val total = c.contentLengthLong; val file = File(File(filesDir, "online_downloads").apply { mkdirs() }, "${track.identifier.hashCode()}.audio"); c.inputStream.use { input -> file.outputStream().use { out -> val buffer = ByteArray(8192); var done = 0L; var n: Int; while (input.read(buffer).also { n = it } >= 0) { if (n == 0) continue; out.write(buffer, 0, n); done += n; if (total > 0) runOnUiThread { onlineAdapter.setDownloadProgress(track.identifier, (done * 100 / total).toInt().coerceIn(1, 99)) } } } }; c.disconnect(); runOnUiThread { onlineAdapter.setDownloadProgress(track.identifier, 100); Toast.makeText(this, "Downloaded for offline playback", Toast.LENGTH_SHORT).show() } } catch (e: Exception) { runOnUiThread { onlineAdapter.setDownloadProgress(track.identifier, 0); Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_LONG).show() } } }.start() }

    private fun setupPlayback() { val token = SessionToken(this, ComponentName(this, PlaybackService::class.java)); controllerFuture = MediaController.Builder(this, token).buildAsync(); controllerFuture.addListener({ controller = runCatching { controllerFuture.get() }.getOrNull() }, ContextCompat.getMainExecutor(this)) }
    private fun loadLibraries() { Thread { val a = if (hasAudioPermission()) musicRepository.loadTracks() else emptyList(); val v = if (hasVideoPermission()) videoRepository.loadVideos() else emptyList(); runOnUiThread { tracks = a; videos = v; when (mode) { Mode.MUSIC -> showFolders(); Mode.VIDEO -> showVideos(); Mode.PLAYLIST -> showPlaylist(); Mode.SEARCH -> Unit } } }.start() }
    private fun hasAudioPermission() = ContextCompat.checkSelfPermission(this, if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    private fun hasVideoPermission() = ContextCompat.checkSelfPermission(this, if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    private fun requestMediaPermissions() { val p = buildList { add(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE); if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.READ_MEDIA_VIDEO); if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS) }; permissionLauncher.launch(p.toTypedArray()) }
    private fun setupList(list: androidx.recyclerview.widget.RecyclerView, adapter: androidx.recyclerview.widget.RecyclerView.Adapter<*>) { list.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this); list.adapter = adapter }
    private fun showOnly(target: View) { listOf(folderList, songList, videoList, onlineList).forEach { it.visibility = if (it === target) View.VISIBLE else View.GONE }; emptyState.visibility = View.GONE }
    private fun setEmpty(show: Boolean, title: String, subtitle: String) { emptyTitle.text = title; emptySubtitle.text = subtitle; emptyState.visibility = if (show) View.VISIBLE else View.GONE }
    private fun handleSwipe(e: MotionEvent): Boolean { when (e.actionMasked) { MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; return true }; MotionEvent.ACTION_UP -> { val dx = e.x - downX; val dy = e.y - downY; if (abs(dx) > 100 && abs(dx) > abs(dy) * 1.3f) { val order = Mode.values(); val i = order.indexOf(mode); val next = if (dx < 0) order[(i + 1) % order.size] else order[(i - 1 + order.size) % order.size]; selectMode(next) }; return true } }; return true }

    override fun onBackPressed() { if (mode == Mode.MUSIC && currentFolder != null) { showFolders(); currentFolder = null } else super.onBackPressed() }
    override fun onDestroy() { if (::controllerFuture.isInitialized) controllerFuture.cancel(true); controller?.release(); super.onDestroy() }
}
