package com.rohith161.musicplayer

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import com.google.common.util.concurrent.ListenableFuture

@androidx.media3.common.util.UnstableApi
class MainActivity : AppCompatActivity() {
    private enum class Mode { MUSIC, VIDEO, ONLINE }

    private lateinit var repository: MusicRepository
    private lateinit var videoRepository: VideoRepository
    private lateinit var onlineRepository: OnlineRepository
    private lateinit var folderAdapter: FolderAdapter
    private lateinit var songAdapter: SongAdapter
    private lateinit var videoAdapter: VideoAdapter
    private lateinit var onlineAdapter: OnlineAdapter
    private lateinit var folderList: androidx.recyclerview.widget.RecyclerView
    private lateinit var songList: androidx.recyclerview.widget.RecyclerView
    private lateinit var videoList: androidx.recyclerview.widget.RecyclerView
    private lateinit var onlineList: androidx.recyclerview.widget.RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var onlineProgress: ProgressBar
    private lateinit var miniPlayer: LinearLayout
    private lateinit var folderHeader: LinearLayout
    private lateinit var screenTitle: TextView
    private lateinit var screenSubtitle: TextView
    private lateinit var emptyTitle: TextView
    private lateinit var emptySubtitle: TextView
    private lateinit var nowTitle: TextView
    private lateinit var nowArtist: TextView
    private lateinit var playButton: ImageButton
    private lateinit var searchInput: EditText
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private var allTracks: List<Track> = emptyList()
    private var allVideos: List<LocalVideo> = emptyList()
    private var currentFolder: String? = null
    private var mode = Mode.MUSIC

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { loadLibraries() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = MusicRepository(contentResolver)
        videoRepository = VideoRepository(contentResolver)
        onlineRepository = OnlineRepository()
        folderList = findViewById(R.id.folderList)
        songList = findViewById(R.id.songList)
        videoList = findViewById(R.id.videoList)
        onlineList = findViewById(R.id.onlineList)
        emptyState = findViewById(R.id.emptyState)
        onlineProgress = findViewById(R.id.onlineProgress)
        emptyTitle = findViewById(R.id.emptyTitle)
        emptySubtitle = findViewById(R.id.emptySubtitle)
        miniPlayer = findViewById(R.id.miniPlayer)
        folderHeader = findViewById(R.id.folderHeader)
        screenTitle = findViewById(R.id.screenTitle)
        screenSubtitle = findViewById(R.id.screenSubtitle)
        nowTitle = findViewById(R.id.nowTitle)
        nowArtist = findViewById(R.id.nowArtist)
        playButton = findViewById(R.id.playButton)
        searchInput = findViewById(R.id.searchInput)
        val grantButton: Button = findViewById(R.id.grantPermissionButton)

        folderAdapter = FolderAdapter { openFolder(it.name) }
        songAdapter = SongAdapter { playTrack(it) }
        videoAdapter = VideoAdapter { playVideo(it) }
        onlineAdapter = OnlineAdapter { playOnline(it) }
        setupList(folderList, folderAdapter)
        setupList(songList, songAdapter)
        setupList(videoList, videoAdapter)
        setupList(onlineList, onlineAdapter)

        findViewById<androidx.mediarouter.app.MediaRouteButton>(R.id.mediaOutputButton).routeSelector =
            MediaRouteSelector.Builder()
                .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
                .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                .build()

        findViewById<TextView>(R.id.musicTab).setOnClickListener { selectMode(Mode.MUSIC) }
        findViewById<TextView>(R.id.videoTab).setOnClickListener { selectMode(Mode.VIDEO) }
        findViewById<TextView>(R.id.onlineTab).setOnClickListener { selectMode(Mode.ONLINE) }
        findViewById<ImageButton>(R.id.backButton).setOnClickListener { showFolders() }
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (mode == Mode.ONLINE && (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE)) {
                searchOnline(searchInput.text.toString())
                true
            } else false
        }
        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (mode != Mode.ONLINE) filterLocal(s?.toString()?.trim().orEmpty())
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })

        grantButton.setOnClickListener { requestMediaPermissions() }
        findViewById<ImageButton>(R.id.prevButton).setOnClickListener { controller?.seekToPreviousMediaItem() }
        playButton.setOnClickListener { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
        findViewById<ImageButton>(R.id.nextButton).setOnClickListener { controller?.seekToNextMediaItem() }

        connectToPlaybackService()
        loadLibraries()
    }

    private fun setupList(list: androidx.recyclerview.widget.RecyclerView, adapter: androidx.recyclerview.widget.RecyclerView.Adapter<*>) {
        list.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        list.adapter = adapter
    }

    private fun connectToPlaybackService() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture.addListener({
            try {
                controller = controllerFuture.get()
                controller?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        playButton.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        Toast.makeText(this@MainActivity, "Can't play this media: ${error.errorCodeName}", Toast.LENGTH_LONG).show()
                    }
                })
            } catch (_: Exception) {
                Toast.makeText(this, "Unable to start playback service", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun loadLibraries() {
        if (!hasAudioPermission()) {
            allTracks = emptyList()
        }
        Thread {
            val tracks = if (hasAudioPermission()) repository.loadTracks() else emptyList()
            val videos = if (hasVideoPermission()) videoRepository.loadVideos() else emptyList()
            runOnUiThread {
                allTracks = tracks
                allVideos = videos
                if (mode == Mode.MUSIC) showFolders() else if (mode == Mode.VIDEO) showVideos()
            }
        }.start()
    }

    private fun selectMode(newMode: Mode) {
        mode = newMode
        currentFolder = null
        folderHeader.visibility = View.GONE
        searchInput.setText("")
        onlineProgress.visibility = View.GONE
        updateTabStyles()
        when (newMode) {
            Mode.MUSIC -> showFolders()
            Mode.VIDEO -> showVideos()
            Mode.ONLINE -> showOnlineHome()
        }
    }

    private fun updateTabStyles() {
        val tabs = listOf(R.id.musicTab, R.id.videoTab, R.id.onlineTab)
        tabs.forEach { id ->
            val tab = findViewById<TextView>(id)
            tab.background = if ((id == R.id.musicTab && mode == Mode.MUSIC) || (id == R.id.videoTab && mode == Mode.VIDEO) || (id == R.id.onlineTab && mode == Mode.ONLINE)) {
                getDrawable(R.drawable.bg_tab_selected)
            } else null
            tab.setTextColor(if (tab.background != null) getColor(R.color.mp_foreground) else getColor(R.color.mp_foreground_muted))
        }
    }

    private fun showFolders() {
        mode = Mode.MUSIC
        currentFolder = null
        folderHeader.visibility = View.GONE
        onlineProgress.visibility = View.GONE
        screenTitle.text = getString(R.string.your_folders)
        screenSubtitle.text = getString(R.string.folders_subtitle)
        showOnly(folderList)
        val folders = allTracks.groupBy { it.folder }.toSortedMap(String.CASE_INSENSITIVE_ORDER).map { FolderItem(it.key, it.value.size) }
        folderAdapter.submitList(folders)
        setEmpty(folders.isEmpty(), getString(R.string.no_music), getString(R.string.grant_audio_permission))
    }

    private fun openFolder(folder: String) {
        mode = Mode.MUSIC
        currentFolder = folder
        folderHeader.visibility = View.VISIBLE
        onlineProgress.visibility = View.GONE
        screenTitle.text = folder
        screenSubtitle.text = getString(R.string.folder_subtitle)
        showOnly(songList)
        val tracks = allTracks.filter { it.folder == folder }
        songAdapter.submitList(tracks)
        setEmpty(tracks.isEmpty(), "Folder is empty", "No playable music was found here.")
    }

    private fun showVideos() {
        folderHeader.visibility = View.GONE
        onlineProgress.visibility = View.GONE
        screenTitle.text = getString(R.string.local_videos)
        screenSubtitle.text = getString(R.string.videos_subtitle)
        showOnly(videoList)
        videoAdapter.submitList(allVideos)
        setEmpty(allVideos.isEmpty(), "No videos found", "Allow video access to browse videos stored on this device.")
    }

    private fun showOnlineHome() {
        folderHeader.visibility = View.GONE
        onlineProgress.visibility = View.GONE
        screenTitle.text = getString(R.string.online_music)
        screenSubtitle.text = getString(R.string.online_subtitle)
        showOnly(onlineList)
        onlineAdapter.submitList(emptyList())
        setEmpty(true, "Discover online music", "Search for open audio to stream from Internet Archive. Press Search on the keyboard.")
    }

    private fun searchOnline(query: String) {
        if (query.isBlank()) return
        showOnly(onlineList)
        onlineProgress.visibility = View.VISIBLE
        emptyTitle.text = "Searching online…"
        emptySubtitle.text = "Loading results from Internet Archive"
        emptyState.visibility = View.VISIBLE
        Thread {
            try {
                val results = onlineRepository.search(query)
                runOnUiThread {
                    onlineProgress.visibility = View.GONE
                    onlineAdapter.submitList(results)
                    setEmpty(results.isEmpty(), "No results", "Try another artist, title, or keyword.")
                }
            } catch (error: Exception) {
                runOnUiThread {
                    onlineProgress.visibility = View.GONE
                    setEmpty(true, "Online search failed", error.message ?: "Check your internet connection.")
                }
            }
        }.start()
    }

    private fun filterLocal(query: String) {
        onlineProgress.visibility = View.GONE
        when (mode) {
            Mode.MUSIC -> if (currentFolder == null) {
                val folders = allTracks.groupBy { it.folder }.filter { query.isBlank() || it.key.contains(query, true) }
                    .toSortedMap(String.CASE_INSENSITIVE_ORDER).map { FolderItem(it.key, it.value.size) }
                folderAdapter.submitList(folders)
                setEmpty(folders.isEmpty(), "No folders found", "Try a different search.")
            } else {
                val base = allTracks.filter { it.folder == currentFolder }
                val filtered = if (query.isBlank()) base else base.filter { it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) }
                songAdapter.submitList(filtered)
                setEmpty(filtered.isEmpty(), "No songs found", "Try a different search.")
            }
            Mode.VIDEO -> {
                val filtered = if (query.isBlank()) allVideos else allVideos.filter { it.title.contains(query, true) }
                videoAdapter.submitList(filtered)
                setEmpty(filtered.isEmpty(), "No videos found", "Try a different search.")
            }
            Mode.ONLINE -> Unit
        }
    }

    private fun showOnly(target: View) {
        listOf(folderList, songList, videoList, onlineList).forEach { it.visibility = if (it === target) View.VISIBLE else View.GONE }
        emptyState.visibility = View.GONE
    }

    private fun setEmpty(show: Boolean, title: String, subtitle: String) {
        if (!show) onlineProgress.visibility = View.GONE
        emptyTitle.text = title
        emptySubtitle.text = subtitle
        emptyState.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun playTrack(track: Track) {
        val player = controller ?: return
        val items = allTracks.map { item ->
            MediaItem.Builder().setMediaId(item.id.toString()).setUri(item.uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).setArtist(item.artist).setAlbumTitle(item.album).build()).build()
        }
        val index = allTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        try {
            player.setMediaItems(items, index, 0L)
            player.prepare()
            player.play()
            nowTitle.text = track.title
            nowArtist.text = track.artist.ifBlank { getString(R.string.unknown_artist) }
            miniPlayer.visibility = View.VISIBLE
            startActivity(Intent(this, PlayerActivity::class.java))
        } catch (error: Exception) {
            Toast.makeText(this, "Playback setup failed: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun playOnline(track: OnlineTrack) {
        val player = controller ?: return
        Thread {
            try {
                val url = onlineRepository.resolveAudioUrl(track.identifier)
                runOnUiThread {
                    if (url == null) {
                        Toast.makeText(this, "No playable audio found for this result", Toast.LENGTH_LONG).show()
                        return@runOnUiThread
                    }
                    player.setMediaItem(MediaItem.Builder().setUri(url).setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.creator).build()).build())
                    player.prepare()
                    player.play()
                    nowTitle.text = track.title
                    nowArtist.text = track.creator
                    miniPlayer.visibility = View.VISIBLE
                    startActivity(Intent(this, PlayerActivity::class.java))
                }
            } catch (error: Exception) {
                runOnUiThread { Toast.makeText(this, "Online playback failed: ${error.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun playVideo(video: LocalVideo) {
        startActivity(Intent(this, VideoActivity::class.java).apply {
            putExtra(VideoActivity.EXTRA_URI, video.uri.toString())
            putExtra(VideoActivity.EXTRA_TITLE, video.title)
        })
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

    override fun onBackPressed() {
        if (mode == Mode.MUSIC && currentFolder != null) showFolders() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::controllerFuture.isInitialized) controllerFuture.cancel(true)
        controller?.release()
        controller = null
        super.onDestroy()
    }
}
