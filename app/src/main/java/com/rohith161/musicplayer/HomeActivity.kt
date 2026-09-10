package com.rohith161.musicplayer

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.common.util.concurrent.ListenableFuture

@androidx.media3.common.util.UnstableApi
class HomeActivity : AppCompatActivity() {
    private enum class Mode { MUSIC, VIDEO, PLAYLIST, SEARCH }

    private var mode = Mode.MUSIC
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
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private var tracks: List<Track> = emptyList()
    private var videos: List<LocalVideo> = emptyList()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { loadLibraries() }

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

        folderAdapter = FolderAdapter { openFolder(it.name) }
        songAdapter = SongAdapter(::playTrack) { showAddToPlaylist(it) }
        videoAdapter = VideoAdapter(::playVideo) { showAddToPlaylist(it) }
        playlistAdapter = PlaylistAdapter(::openPlaylist)
        searchAdapter = SearchAdapter { result ->
            if (result.isVideo) {
                videos.firstOrNull { it.uri.toString() == result.uri }?.let(::playVideo)
            } else {
                tracks.firstOrNull { it.uri.toString() == result.uri }?.let(::playTrack)
            }
        }
        folderList.adapter = folderAdapter
        songList.adapter = songAdapter
        videoList.adapter = videoAdapter
        onlineList.adapter = playlistAdapter
        listOf(folderList, songList, videoList, onlineList).forEach {
            it.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        }

        findViewById<ImageButton>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<ImageButton>(R.id.backButton).setOnClickListener { goBack() }
        findViewById<TextView>(R.id.musicTab).setOnClickListener { selectMode(Mode.MUSIC) }
        findViewById<TextView>(R.id.videoTab).setOnClickListener { selectMode(Mode.VIDEO) }
        findViewById<TextView>(R.id.onlineTab).setOnClickListener { selectMode(Mode.PLAYLIST) }
        findViewById<TextView>(R.id.searchTab).setOnClickListener { selectMode(Mode.SEARCH) }
        findViewById<Button>(R.id.searchButton).setOnClickListener { performSearch() }
        searchInput.setOnEditorActionListener { _, _, _ -> performSearch(); true }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (mode == Mode.SEARCH) performSearch()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        miniPlayer.setOnClickListener { openPlayerScreen() }
        findViewById<ImageButton>(R.id.prevButton).setOnClickListener { controller?.seekToPreviousMediaItem() }
        miniPlayButton.setOnClickListener { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
        findViewById<ImageButton>(R.id.nextButton).setOnClickListener { controller?.seekToNextMediaItem() }

        selectMode(Mode.MUSIC)
        connectToPlaybackService()
        loadLibraries()
    }

    private fun connectToPlaybackService() {
        controllerFuture = MediaController.Builder(
            this,
            SessionToken(this, ComponentName(this, PlaybackService::class.java))
        ).buildAsync()
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

    private fun updateMiniPlayer() {
        val player = controller ?: return
        val hasMedia = player.currentMediaItem != null
        miniPlayer.visibility = if (hasMedia) View.VISIBLE else View.GONE
        if (hasMedia) {
            nowTitle.text = player.mediaMetadata.title ?: getString(R.string.unknown_title)
            nowArtist.text = player.mediaMetadata.artist ?: getString(R.string.unknown_artist)
            miniPlayButton.setImageResource(
                if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
        }
    }

    private fun loadLibraries() {
        Thread {
            val loadedTracks = if (hasAudioPermission()) musicRepository.loadTracks() else emptyList()
            val loadedVideos = if (hasVideoPermission()) videoRepository.loadVideos() else emptyList()
            runOnUiThread {
                tracks = loadedTracks
                videos = loadedVideos
                when (mode) {
                    Mode.MUSIC -> showFolders()
                    Mode.VIDEO -> showVideos()
                    Mode.PLAYLIST -> showPlaylists()
                    Mode.SEARCH -> performSearch()
                }
            }
        }.start()
    }

    private fun selectMode(newMode: Mode) {
        mode = newMode
        currentFolder = null
        currentPlaylist = null
        findViewById<View>(R.id.folderHeader).visibility = View.GONE
        searchControls.visibility = if (newMode == Mode.SEARCH) View.VISIBLE else View.GONE
        updateTabs()
        when (newMode) {
            Mode.MUSIC -> showFolders()
            Mode.VIDEO -> showVideos()
            Mode.PLAYLIST -> showPlaylists()
            Mode.SEARCH -> showSearch()
        }
    }

    private fun updateTabs() {
        val active = when (mode) {
            Mode.MUSIC -> R.id.musicTab
            Mode.VIDEO -> R.id.videoTab
            Mode.PLAYLIST -> R.id.onlineTab
            Mode.SEARCH -> R.id.searchTab
        }
        listOf(R.id.videoTab, R.id.musicTab, R.id.onlineTab, R.id.searchTab).forEach { id ->
            val tab = findViewById<TextView>(id)
            tab.background = if (id == active) getDrawable(R.drawable.bg_tab_selected) else null
            tab.setTextColor(getColor(if (id == active) R.color.mp_foreground else R.color.mp_foreground_muted))
        }
    }

    private fun showFolders() {
        searchControls.visibility = View.GONE
        findViewById<View>(R.id.folderHeader).visibility = View.GONE
        showOnly(folderList)
        findViewById<TextView>(R.id.screenTitle).text = "YOUR MUSIC"
        findViewById<TextView>(R.id.screenSubtitle).text = getString(R.string.folders_subtitle)
        val folders = tracks.groupBy { it.folder }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .map { FolderItem(it.key, it.value.size) }
        folderAdapter.submitList(folders)
        setEmpty(folders.isEmpty(), "No music found", "Your local music will appear here.")
    }

    private fun openFolder(folder: String) {
        currentFolder = folder
        findViewById<View>(R.id.folderHeader).visibility = View.VISIBLE
        findViewById<TextView>(R.id.folderTitle).text = folder
        showOnly(songList)
        val list = tracks.filter { it.folder == folder }
        songAdapter.submitList(list)
        setEmpty(list.isEmpty(), "Folder is empty", "No playable music was found here.")
    }

    private fun showVideos() {
        searchControls.visibility = View.GONE
        findViewById<View>(R.id.folderHeader).visibility = View.GONE
        showOnly(folderList)
        findViewById<TextView>(R.id.screenTitle).text = "VIDEO"
        findViewById<TextView>(R.id.screenSubtitle).text = "Your local video folders"
        val folders = videos.groupBy { it.folder }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .map { FolderItem(it.key, it.value.size) }
        folderAdapter.submitList(folders)
        setEmpty(folders.isEmpty(), "No videos found", "Your local videos will appear here.")
    }

    private fun showPlaylists() {
        searchControls.visibility = View.GONE
        findViewById<View>(R.id.folderHeader).visibility = View.GONE
        showOnly(onlineList)
        onlineList.adapter = playlistAdapter
        findViewById<TextView>(R.id.screenTitle).text = "PLAYLIST"
        findViewById<TextView>(R.id.screenSubtitle).text = "Tap here to create a playlist"
        findViewById<TextView>(R.id.screenSubtitle).setOnClickListener { createPlaylist() }
        val list = playlistRepository.getAll()
        playlistAdapter.submitList(list)
        setEmpty(list.isEmpty(), "No playlists yet", "Tap the subtitle above to create one.")
    }

    private fun showSearch() {
        searchControls.visibility = View.VISIBLE
        findViewById<View>(R.id.folderHeader).visibility = View.GONE
        findViewById<TextView>(R.id.screenTitle).text = "SEARCH"
        findViewById<TextView>(R.id.screenSubtitle).text = "Search local music and video"
        showOnly(onlineList)
        onlineList.adapter = searchAdapter
        performSearch()
    }

    private fun performSearch() {
        if (mode != Mode.SEARCH) return
        val query = searchInput.text.toString().trim()
        if (query.isBlank()) {
            searchAdapter.submitList(emptyList())
            setEmpty(true, "Start typing", "Searches your local music and video library.")
            return
        }
        val results = mutableListOf<SearchResult>()
        tracks.filter {
            it.title.contains(query, true) || it.artist.contains(query, true) ||
                it.album.contains(query, true) || it.folder.contains(query, true)
        }.forEach {
            results += SearchResult(it.title, if (it.album.isBlank()) it.artist else "${it.artist} • ${it.album}", it.uri.toString(), false)
        }
        videos.filter { it.title.contains(query, true) || it.folder.contains(query, true) }
            .forEach { results += SearchResult(it.title, it.folder, it.uri.toString(), true) }
        searchAdapter.submitList(results)
        setEmpty(results.isEmpty(), "No local matches", "Try another title, artist, album, or folder.")
    }

    private fun createPlaylist() {
        val input = EditText(this).apply { hint = "Playlist name"; setSingleLine(true) }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Create playlist")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create") { _, _ ->
                if (input.text.toString().trim().isNotEmpty()) {
                    playlistRepository.create(input.text.toString().trim())
                    showPlaylists()
                }
            }.show()
    }

    private fun openPlaylist(playlist: ViaPlaylist) {
        currentPlaylist = playlist
        findViewById<View>(R.id.folderHeader).visibility = View.VISIBLE
        findViewById<TextView>(R.id.folderTitle).text = playlist.name
        showOnly(onlineList)
        onlineList.adapter = searchAdapter
        searchAdapter.submitList(playlist.entries.map {
            SearchResult(it.title, it.subtitle, it.uri, it.type == PlaylistMediaType.VIDEO)
        })
        setEmpty(playlist.entries.isEmpty(), "Playlist is empty", "Add local media from Music or Video.")
    }

    private fun showAddToPlaylist(track: Track) {
        showPlaylistSheet(PlaylistEntry(track.id.toString(), PlaylistMediaType.AUDIO, track.uri.toString(), track.title, track.artist))
    }

    private fun showAddToPlaylist(video: LocalVideo) {
        showPlaylistSheet(PlaylistEntry(video.id.toString(), PlaylistMediaType.VIDEO, video.uri.toString(), video.title, video.folder))
    }

    private fun showPlaylistSheet(entry: PlaylistEntry) {
        val dialog = BottomSheetDialog(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 32)
        }
        box.addView(TextView(this).apply { text = "Add to playlist"; textSize = 20f })
        playlistRepository.getAll().forEach { playlist ->
            box.addView(Button(this).apply {
                text = "Add to ${playlist.name}"
                setOnClickListener { playlistRepository.addEntry(playlist.id, entry); dialog.dismiss() }
            })
        }
        box.addView(Button(this).apply {
            text = "Create new playlist"
            setOnClickListener { dialog.dismiss(); createPlaylist() }
        })
        dialog.setContentView(box)
        dialog.show()
    }

    private fun playTrack(track: Track) {
        val player = controller ?: return
        val items = tracks.map {
            MediaItem.Builder()
                .setMediaId(it.id.toString())
                .setUri(it.uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(it.title).setArtist(it.artist).setAlbumTitle(it.album).build())
                .build()
        }
        val index = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        val savedId = PlaybackStateStore.mediaId(this)
        val position = if (savedId == track.id.toString()) PlaybackStateStore.position(this) else 0L
        player.setMediaItems(items, index, position)
        player.prepare()
        player.play()
        openPlayerScreen()
    }

    private fun playVideo(video: LocalVideo) {
        startActivity(Intent(this, VideoActivity::class.java).apply {
            putExtra(VideoActivity.EXTRA_URI, video.uri.toString())
            putExtra(VideoActivity.EXTRA_TITLE, video.title)
        })
    }

    private fun openPlayerScreen() {
        if (controller?.currentMediaItem != null) startActivity(Intent(this, PlayerActivity::class.java))
    }

    private fun showOnly(target: View) {
        listOf(folderList, songList, videoList, onlineList).forEach { it.visibility = if (it === target) View.VISIBLE else View.GONE }
        emptyState.visibility = View.GONE
    }

    private fun setEmpty(show: Boolean, title: String, subtitle: String) {
        emptyTitle.text = title
        emptySubtitle.text = subtitle
        emptyState.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun goBack() {
        when {
            currentPlaylist != null -> { currentPlaylist = null; showPlaylists() }
            currentFolder != null -> { currentFolder = null; if (mode == Mode.VIDEO) showVideos() else showFolders() }
            else -> finish()
        }
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasVideoPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestMediaPermissions() {
        val permissions = buildList {
            add(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.READ_MEDIA_VIDEO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    override fun onDestroy() {
        if (::controllerFuture.isInitialized) controllerFuture.cancel(true)
        controller?.release()
        controller = null
        super.onDestroy()
    }
}
