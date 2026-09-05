package com.rohith161.musicplayer

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.common.util.concurrent.ListenableFuture
import kotlin.math.abs

@androidx.media3.common.util.UnstableApi
class HomeActivity : AppCompatActivity() {
    private enum class Mode { VIDEO, MUSIC, PLAYLIST, SEARCH }
    private var mode = Mode.MUSIC
    private var downX = 0f
    private var downY = 0f
    private var swipeTracking = false
    private var currentFolder: String? = null
    private var currentPlaylist: ViaPlaylist? = null

    private lateinit var musicRepository: MusicRepository
    private lateinit var videoRepository: VideoRepository
    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var folderAdapter: FolderAdapter
    private lateinit var songAdapter: SongAdapter
    private lateinit var videoAdapter: VideoAdapter
    private lateinit var playlistAdapter: PlaylistAdapter
    private lateinit var searchAdapter: SearchAdapter
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
    private lateinit var contentContainer: View
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private var tracks: List<Track> = emptyList()
    private var videos: List<LocalVideo> = emptyList()

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { loadLibraries() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val root = findViewById<View>(R.id.homeRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, top + 12, v.paddingRight, v.paddingBottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)

        musicRepository = MusicRepository(contentResolver)
        videoRepository = VideoRepository(contentResolver)
        playlistRepository = PlaylistRepository(this)
        folderList = findViewById(R.id.folderList); songList = findViewById(R.id.songList); videoList = findViewById(R.id.videoList); onlineList = findViewById(R.id.onlineList)
        emptyState = findViewById(R.id.emptyState); emptyTitle = findViewById(R.id.emptyTitle); emptySubtitle = findViewById(R.id.emptySubtitle)
        searchInput = findViewById(R.id.searchInput); searchControls = findViewById(R.id.searchControls); miniPlayer = findViewById(R.id.miniPlayer)
        nowTitle = findViewById(R.id.nowTitle); nowArtist = findViewById(R.id.nowArtist); miniPlayButton = findViewById(R.id.playButton); contentContainer = findViewById(R.id.contentContainer)
        findViewById<View>(R.id.grantPermissionButton).visibility = View.GONE

        folderAdapter = FolderAdapter { openFolder(it.name) }
        songAdapter = SongAdapter(::playTrack) { showAddToPlaylist(it) }
        videoAdapter = VideoAdapter(::playVideo) { showAddToPlaylist(it) }
        playlistAdapter = PlaylistAdapter { openPlaylist(it) }
        searchAdapter = SearchAdapter { result ->
            if (result.isVideo) videos.firstOrNull { it.uri.toString() == result.uri }?.let(::playVideo)
            else tracks.firstOrNull { it.uri.toString() == result.uri }?.let(::playTrack)
        }
        setupList(folderList, folderAdapter); setupList(songList, songAdapter); setupList(videoList, videoAdapter); setupList(onlineList, playlistAdapter)
        findViewById<androidx.mediarouter.app.MediaRouteButton>(R.id.mediaOutputButton).routeSelector = MediaRouteSelector.Builder().addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO).addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK).build()
        findViewById<ImageButton>(R.id.settingsButton).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<ImageButton>(R.id.backButton).setOnClickListener { goBackFromFolderOrPlaylist() }
        setupNavigation(); setupSearch(); setupPlayback(); setupMiniPlayer(); setupSwipe()

        if (!getPreferences(MODE_PRIVATE).getBoolean("permission_prompted", false) && !hasAudioPermission()) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("permission_prompted", true).apply(); requestMediaPermissions()
        } else loadLibraries()
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
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { if (mode == Mode.SEARCH) performLocalSearch() }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun selectMode(newMode: Mode) {
        mode = newMode; currentFolder = null; currentPlaylist = null
        findViewById<View>(R.id.folderHeader).visibility = View.GONE
        searchInput.setText(""); updateTabs()
        when (mode) { Mode.VIDEO -> showVideos(); Mode.MUSIC -> showFolders(); Mode.PLAYLIST -> showPlaylists(); Mode.SEARCH -> showSearch() }
    }

    private fun updateTabs() {
        listOf(Mode.VIDEO to R.id.videoTab, Mode.MUSIC to R.id.musicTab, Mode.PLAYLIST to R.id.onlineTab, Mode.SEARCH to R.id.searchTab).forEach { (m, id) ->
            val tab = findViewById<TextView>(id); tab.background = if (mode == m) getDrawable(R.drawable.bg_tab_selected) else null; tab.setTextColor(getColor(if (mode == m) R.color.mp_foreground else R.color.mp_foreground_muted))
        }
    }

    private fun showFolders() {
        searchControls.visibility = View.GONE; showOnly(folderList); findViewById<TextView>(R.id.screenTitle).text = "YOUR MUSIC"; findViewById<TextView>(R.id.screenSubtitle).text = getString(R.string.folders_subtitle)
        val list = tracks.groupBy { it.folder }.map { FolderItem(it.key, it.value.size) }; folderAdapter.submitList(list); setEmpty(list.isEmpty(), "No music found", "Your local music will appear here.")
    }

    private fun showVideos() {
        searchControls.visibility = View.GONE; showOnly(if (currentFolder == null) folderList else videoList); findViewById<TextView>(R.id.screenTitle).text = "VIDEO"; findViewById<TextView>(R.id.screenSubtitle).text = if (currentFolder == null) "Your local video folders" else currentFolder
        if (currentFolder == null) { val list = videos.groupBy { it.folder }.map { FolderItem(it.key, it.value.size) }; folderAdapter.submitList(list); setEmpty(list.isEmpty(), "No videos found", "Your local videos will appear here.") }
        else { val list = videos.filter { it.folder == currentFolder }; videoAdapter.submitList(list); setEmpty(list.isEmpty(), "Folder is empty", "No videos were found here.") }
    }

    private fun openFolder(folder: String) {
        currentFolder = folder; findViewById<View>(R.id.folderHeader).visibility = View.VISIBLE; findViewById<TextView>(R.id.folderTitle).text = folder
        if (mode == Mode.VIDEO) showVideos() else { searchControls.visibility = View.GONE; showOnly(songList); val list = tracks.filter { it.folder == folder }; songAdapter.submitList(list); setEmpty(list.isEmpty(), "Folder is empty", "No playable music was found here.") }
    }

    private fun showPlaylists() {
        searchControls.visibility = View.GONE; showOnly(onlineList); onlineList.adapter = playlistAdapter; findViewById<TextView>(R.id.screenTitle).text = "PLAYLIST"; findViewById<TextView>(R.id.screenSubtitle).text = "Your playlists"
        val list = playlistRepository.getAll(); playlistAdapter.submitList(list); setEmpty(list.isEmpty(), "No playlists yet", "Create a playlist and add local music or video.")
        findViewById<TextView>(R.id.screenSubtitle).setOnClickListener { createPlaylist() }
    }

    private fun openPlaylist(playlist: ViaPlaylist) {
        currentPlaylist = playlist; findViewById<View>(R.id.folderHeader).visibility = View.VISIBLE; findViewById<TextView>(R.id.folderTitle).text = playlist.name
        showOnly(onlineList); onlineList.adapter = searchAdapter
        searchAdapter.submitList(playlist.entries.map { SearchResult(it.title, it.subtitle, it.uri, it.type == PlaylistMediaType.VIDEO) })
        setEmpty(playlist.entries.isEmpty(), "Playlist is empty", "Add local music or video from their tabs.")
    }

    private fun createPlaylist() {
        val input = EditText(this); input.hint = "Playlist name"; input.setSingleLine(true)
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Create playlist").setView(input).setNegativeButton("Cancel", null).setPositiveButton("Create") { _, _ -> playlistRepository.create(input.text.toString()); showPlaylists() }.show()
    }

    private fun showAddToPlaylist(track: Track) = showPlaylistSheet(PlaylistEntry(track.id.toString(), PlaylistMediaType.AUDIO, track.uri.toString(), track.title, track.artist))
    private fun showAddToPlaylist(video: LocalVideo) = showPlaylistSheet(PlaylistEntry(video.id.toString(), PlaylistMediaType.VIDEO, video.uri.toString(), video.title, video.folder))

    private fun showPlaylistSheet(entry: PlaylistEntry) {
        val dialog = BottomSheetDialog(this); val box = LinearLayout(this); box.orientation = LinearLayout.VERTICAL; box.setPadding(24, 24, 24, 32)
        val title = TextView(this); title.text = "Add to playlist"; title.textSize = 20f; title.setTextColor(getColor(R.color.mp_foreground)); box.addView(title)
        val create = Button(this); create.text = "＋ Create new playlist"; box.addView(create); create.setOnClickListener { dialog.dismiss(); val input = EditText(this); input.hint = "Playlist name"; androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Create playlist").setView(input).setNegativeButton("Cancel", null).setPositiveButton("Create and add") { _, _ -> playlistRepository.create(input.text.toString())?.let { playlistRepository.addEntry(it.id, entry) } }.show() }
        playlistRepository.getAll().forEach { playlist ->
            val b = Button(this); b.text = "Add to ${playlist.name}"; box.addView(b); b.setOnClickListener { playlistRepository.addEntry(playlist.id, entry); dialog.dismiss() }
        }
        dialog.setContentView(box); dialog.show()
    }

    private fun performLocalSearch() {
        val q = searchInput.text.toString().trim(); showOnly(onlineList); onlineList.adapter = searchAdapter
        if (q.isBlank()) { searchAdapter.submitList(emptyList()); setEmpty(true, "Start typing", "Searches local music and video as you type."); return }
        val results = mutableListOf<SearchResult>()
        tracks.filter { it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true) || it.folder.contains(q, true) }.forEach { results += SearchResult(it.title, if (it.album.isBlank()) it.artist else "${it.artist} • ${it.album}", it.uri.toString(), false) }
        videos.filter { it.title.contains(q, true) || it.folder.contains(q, true) }.forEach { results += SearchResult(it.title, it.folder, it.uri.toString(), true) }
        searchAdapter.submitList(results); setEmpty(results.isEmpty(), "No local matches", "Try another part of the file name, title, artist, album, or folder.")
    }

    private fun playTrack(track: Track) {
        val items = tracks.map { MediaItem.Builder().setUri(it.uri).setMediaId(it.id.toString()).setMediaMetadata(MediaMetadata.Builder().setTitle(it.title).setArtist(it.artist).setAlbumTitle(it.album).build()).build() }
        controller?.setMediaItems(items, tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0), 0L); controller?.prepare(); controller?.play(); startActivity(Intent(this, PlayerActivity::class.java))
    }

    private fun playVideo(video: LocalVideo) = startActivity(Intent(this, VideoActivity::class.java).apply { putExtra(VideoActivity.EXTRA_URI, video.uri.toString()); putExtra(VideoActivity.EXTRA_TITLE, video.title) })

    private fun setupPlayback() {
        controllerFuture = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
        controllerFuture.addListener({ controller = runCatching { controllerFuture.get() }.getOrNull(); controller?.addListener(object : Player.Listener { override fun onIsPlayingChanged(isPlaying: Boolean) = updateMiniPlayer(); override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = updateMiniPlayer(); override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = updateMiniPlayer() }); updateMiniPlayer() }, ContextCompat.getMainExecutor(this))
    }

    private fun setupMiniPlayer() {
        findViewById<View>(R.id.miniPlayerInfo).setOnClickListener { startActivity(Intent(this, PlayerActivity::class.java)) }
        miniPlayButton.setOnClickListener { controller?.let { if (it.isPlaying) it.pause() else it.play() } }; findViewById<ImageButton>(R.id.prevButton).setOnClickListener { controller?.seekToPreviousMediaItem() }; findViewById<ImageButton>(R.id.nextButton).setOnClickListener { controller?.seekToNextMediaItem() }
    }

    private fun updateMiniPlayer() { val c = controller ?: return; val hasMedia = c.mediaItemCount > 0 && c.currentMediaItem != null; miniPlayer.visibility = if (hasMedia) View.VISIBLE else View.GONE; if (hasMedia) { nowTitle.text = c.mediaMetadata.title ?: getString(R.string.unknown_title); nowArtist.text = c.mediaMetadata.artist ?: getString(R.string.unknown_artist); miniPlayButton.setImageResource(if (c.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play) } }

    private fun setupSwipe() { }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val content = contentContainer
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; swipeTracking = isInside(content, event.rawX, event.rawY) }
            MotionEvent.ACTION_MOVE -> if (swipeTracking) { val dx = event.rawX - downX; val dy = event.rawY - downY; if (abs(dx) > abs(dy) * 1.25f) content.translationX = dx * 0.7f }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (swipeTracking) { val dx = event.rawX - downX; val dy = event.rawY - downY; swipeTracking = false; animateSwipe(dx, dy) }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun animateSwipe(dx: Float, dy: Float) {
        val order = listOf(Mode.VIDEO, Mode.MUSIC, Mode.PLAYLIST, Mode.SEARCH); val i = order.indexOf(mode)
        if (abs(dx) <= 90f || abs(dx) <= abs(dy) * 1.25f) { contentContainer.animate().translationX(0f).setDuration(120).start(); return }
        val direction = if (dx < 0) 1 else -1; val targetIndex = i + direction
        if (targetIndex !in order.indices) { contentContainer.animate().translationX(0f).setDuration(160).start(); return }
        val target = if (dx < 0) -contentContainer.width.toFloat() else contentContainer.width.toFloat()
        val duration = (260L - abs(dx).toLong()).coerceIn(90L, 260L)
        contentContainer.animate().translationX(target).setDuration(duration).withEndAction { contentContainer.translationX = -target; selectMode(order[targetIndex]); contentContainer.animate().translationX(0f).setDuration(170).start() }.start()
    }

    private fun isInside(v: View, x: Float, y: Float): Boolean { val loc = IntArray(2); v.getLocationOnScreen(loc); return x >= loc[0] && x <= loc[0] + v.width && y >= loc[1] && y <= loc[1] + v.height }

    private fun goBackFromFolderOrPlaylist() { if (currentPlaylist != null) { currentPlaylist = null; findViewById<View>(R.id.folderHeader).visibility = View.GONE; showPlaylists() } else if (currentFolder != null) { currentFolder = null; findViewById<View>(R.id.folderHeader).visibility = View.GONE; if (mode == Mode.VIDEO) showVideos() else showFolders() } }

    private fun loadLibraries() { Thread { val a = if (hasAudioPermission()) musicRepository.loadTracks() else emptyList(); val v = if (hasVideoPermission()) videoRepository.loadVideos() else emptyList(); runOnUiThread { tracks = a; videos = v; when (mode) { Mode.MUSIC -> showFolders(); Mode.VIDEO -> showVideos(); Mode.PLAYLIST -> showPlaylists(); Mode.SEARCH -> if (searchInput.text.isNotBlank()) performLocalSearch() }; updateMiniPlayer() } }.start() }
    private fun setupList(list: androidx.recyclerview.widget.RecyclerView, adapter: androidx.recyclerview.widget.RecyclerView.Adapter<*>) { list.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this); list.adapter = adapter }
    private fun showOnly(target: View) { listOf(folderList, songList, videoList, onlineList).forEach { it.visibility = if (it === target) View.VISIBLE else View.GONE }; emptyState.visibility = View.GONE }
    private fun setEmpty(empty: Boolean, title: String, subtitle: String) { if (empty) { emptyTitle.text = title; emptySubtitle.text = subtitle; emptyState.visibility = View.VISIBLE } else emptyState.visibility = View.GONE }
    private fun hasAudioPermission() = ContextCompat.checkSelfPermission(this, if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    private fun hasVideoPermission() = ContextCompat.checkSelfPermission(this, if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    private fun requestMediaPermissions() { permissionLauncher.launch(buildList { add(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE); if (Build.VERSION.SDK_INT >= 33) { add(Manifest.permission.READ_MEDIA_VIDEO); add(Manifest.permission.POST_NOTIFICATIONS) } }.toTypedArray()) }
    override fun onResume() { super.onResume(); updateMiniPlayer() }
    override fun onDestroy() { if (::controllerFuture.isInitialized) controllerFuture.cancel(true); controller?.release(); controller = null; super.onDestroy() }
}
