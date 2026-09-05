package com.rohith161.musicplayer

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
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
    private lateinit var repository: MusicRepository
    private lateinit var folderAdapter: FolderAdapter
    private lateinit var songAdapter: SongAdapter
    private lateinit var folderList: androidx.recyclerview.widget.RecyclerView
    private lateinit var songList: androidx.recyclerview.widget.RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var miniPlayer: LinearLayout
    private lateinit var folderHeader: LinearLayout
    private lateinit var screenTitle: TextView
    private lateinit var screenSubtitle: TextView
    private lateinit var nowTitle: TextView
    private lateinit var nowArtist: TextView
    private lateinit var playButton: ImageButton
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private var allTracks: List<Track> = emptyList()
    private var currentFolder: String? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { loadLibraryIfPermitted() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = MusicRepository(contentResolver)
        folderList = findViewById(R.id.folderList)
        songList = findViewById(R.id.songList)
        emptyState = findViewById(R.id.emptyState)
        miniPlayer = findViewById(R.id.miniPlayer)
        folderHeader = findViewById(R.id.folderHeader)
        screenTitle = findViewById(R.id.screenTitle)
        screenSubtitle = findViewById(R.id.screenSubtitle)
        nowTitle = findViewById(R.id.nowTitle)
        nowArtist = findViewById(R.id.nowArtist)
        playButton = findViewById(R.id.playButton)
        val searchInput: EditText = findViewById(R.id.searchInput)
        val grantButton: Button = findViewById(R.id.grantPermissionButton)

        folderAdapter = FolderAdapter { openFolder(it.name) }
        songAdapter = SongAdapter { playTrack(it) }
        folderList.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        folderList.adapter = folderAdapter
        songList.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        songList.adapter = songAdapter

        findViewById<androidx.mediarouter.app.MediaRouteButton>(R.id.mediaOutputButton).routeSelector =
            MediaRouteSelector.Builder()
                .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
                .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                .build()

        findViewById<ImageButton>(R.id.backButton).setOnClickListener { showFolders() }
        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterContent(s?.toString()?.trim().orEmpty())
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })

        grantButton.setOnClickListener { requestAudioPermissions() }
        findViewById<ImageButton>(R.id.prevButton).setOnClickListener { controller?.seekToPreviousMediaItem() }
        playButton.setOnClickListener { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
        findViewById<ImageButton>(R.id.nextButton).setOnClickListener { controller?.seekToNextMediaItem() }

        connectToPlaybackService()
        loadLibraryIfPermitted()
    }

    private fun connectToPlaybackService() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture.addListener({
            try {
                controller = controllerFuture.get()
                controller?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        playButton.setImageResource(
                            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                        )
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        Toast.makeText(this@MainActivity, "Can't play this file: ${error.errorCodeName}", Toast.LENGTH_LONG).show()
                    }
                })
            } catch (_: Exception) {
                Toast.makeText(this, "Unable to start playback service", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun loadLibraryIfPermitted() {
        if (!hasAudioPermission()) {
            emptyState.visibility = View.VISIBLE
            folderList.visibility = View.GONE
            songList.visibility = View.GONE
            return
        }
        Thread {
            val tracks = repository.loadTracks()
            runOnUiThread {
                allTracks = tracks
                showFolders()
            }
        }.start()
    }

    private fun showFolders() {
        currentFolder = null
        folderHeader.visibility = View.GONE
        screenTitle.text = getString(R.string.your_folders)
        screenSubtitle.text = getString(R.string.folders_subtitle)
        folderList.visibility = if (allTracks.isEmpty()) View.GONE else View.VISIBLE
        songList.visibility = View.GONE
        emptyState.visibility = if (allTracks.isEmpty()) View.VISIBLE else View.GONE
        val folders = allTracks.groupBy { it.folder }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .map { FolderItem(it.key, it.value.size) }
        folderAdapter.submitList(folders)
        findViewById<EditText>(R.id.searchInput).setText("")
    }

    private fun openFolder(folder: String) {
        currentFolder = folder
        folderHeader.visibility = View.VISIBLE
        screenTitle.text = folder
        screenSubtitle.text = getString(R.string.folder_subtitle)
        folderList.visibility = View.GONE
        songList.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
        val tracks = allTracks.filter { it.folder == folder }
        songAdapter.submitList(tracks)
        findViewById<EditText>(R.id.searchInput).setText("")
    }

    private fun filterContent(query: String) {
        if (currentFolder == null) {
            val folders = allTracks.groupBy { it.folder }
                .filter { query.isBlank() || it.key.contains(query, true) }
                .toSortedMap(String.CASE_INSENSITIVE_ORDER)
                .map { FolderItem(it.key, it.value.size) }
            folderAdapter.submitList(folders)
            emptyState.visibility = if (folders.isEmpty()) View.VISIBLE else View.GONE
        } else {
            val base = allTracks.filter { it.folder == currentFolder }
            val filtered = if (query.isBlank()) base else base.filter {
                it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)
            }
            songAdapter.submitList(filtered)
            emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermissions() {
        val permissions = buildList {
            add(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun playTrack(track: Track) {
        val player = controller ?: run {
            Toast.makeText(this, "Player is still starting", Toast.LENGTH_SHORT).show()
            return
        }
        val items = allTracks.map { item ->
            MediaItem.Builder()
                .setMediaId(item.id.toString())
                .setUri(item.uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).setArtist(item.artist).setAlbumTitle(item.album).build())
                .build()
        }
        val index = allTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        try {
            player.setMediaItems(items, index, 0L)
            player.prepare()
            player.play()
            nowTitle.text = track.title
            nowArtist.text = if (track.artist.isBlank()) getString(R.string.unknown_artist) else track.artist
            miniPlayer.visibility = View.VISIBLE
            startActivity(Intent(this, PlayerActivity::class.java))
        } catch (error: Exception) {
            Toast.makeText(this, "Playback setup failed: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onBackPressed() {
        if (currentFolder != null) showFolders() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::controllerFuture.isInitialized) controllerFuture.cancel(true)
        controller?.release()
        controller = null
        super.onDestroy()
    }
}
